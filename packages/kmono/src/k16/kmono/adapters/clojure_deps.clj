(ns k16.kmono.adapters.clojure-deps
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.tools.deps.extensions :as deps.ext]
   [clojure.tools.deps.extensions.maven]
   [clojure.tools.deps.util.maven :as deps.util.maven]
   [clojure.tools.deps.util.session :as deps.util.session]
   [k16.kmono.adapter :as adapter :refer [Adapter]]))

(defn- local?
  [[_ coord]]
  (boolean (:local/root coord)))

(defn- get-local-deps
  [config deps-edn]
  (into
   (->> deps-edn
        :deps
        (filter local?)
        (mapv (comp str first)))
   (when-let [manage-aliases (seq (:aliases config))]
     (->> (map #(vals (select-keys % [:deps :extra-deps :replace-deps]))
               (-> deps-edn
                   :aliases
                   (select-keys manage-aliases)
                   (vals)))
          (flatten)
          (apply merge)
          (filter local?)
          (map first)))))

(defn read-deps-edn!
  [package-path]
  (try
    (-> (fs/file package-path "deps.edn")
        (slurp)
        (edn/read-string))
    (catch Throwable e
      (throw (ex-info "Could not read deps.edn file"
                      {:package-path package-path
                       :event "read-deps-edn"}
                      e)))))

(defn ->adapter
  ([package-path]
   (->adapter package-path 10000))
  ([package-path timeout-ms]
   (let [deps-edn (read-deps-edn! package-path)
         kmono-config (:kmono/config deps-edn)]
     (when kmono-config
       (let [{:keys [group artifact] :as config}
             (-> kmono-config
                 (adapter/ensure-artifact package-path))
             coord (str group "/" artifact)
             managed-deps (get-local-deps config deps-edn)]
         (reify Adapter

           (prepare-deps-env [_ changes]
             (binding [*print-namespace-maps* false]
               (pr-str {:deps
                        (into {} (map
                                  (fn [dep]
                                    [(symbol dep)
                                     {:mvn/version
                                      (get-in changes [dep :version])}]))
                              managed-deps)})))

           (get-managed-deps [_] managed-deps)

           (get-kmono-config [_] config)

           (release-published? [_ version]
             (let [fut (future
                         (let [;; ignore user's local repository cache
                               local-repo (str package-path "/.kmono/" artifact "/.m2")]
                           (try (deps.util.session/with-session
                                  (let [;; ignoring user's machine local m2 repo
                                        versions (->> (deps.ext/find-versions
                                                       (symbol coord)
                                                       nil
                                                       :mvn {:mvn/local-repo local-repo
                                                             :mvn/repos
                                                             (merge deps.util.maven/standard-repos
                                                                    (:mvn/repos deps-edn))})
                                                      (map :mvn/version)
                                                      (set))]
                                    (contains? versions version)))
                                (finally
                                  (try (fs/delete-tree local-repo)
                                       (catch Throwable _))))))
                   res (deref fut timeout-ms ::timed-out)]
               (if (= ::timed-out res)
                 (throw (ex-info "Timed out requesting remote repository for version check"
                                 {:lib coord
                                  :version version}))
                 res)))))))))
