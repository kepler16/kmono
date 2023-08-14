(ns k16.kbuild.adapters.clojure-deps
  (:require
   [promesa.core :as p]
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.tools.deps.extensions :as deps.ext]
   [clojure.tools.deps.extensions.maven]
   [clojure.tools.deps.util.maven :as deps.util.maven]
   [clojure.tools.deps.util.session :as deps.util.session]
   [k16.kbuild.adapter :refer [Adapter]]))

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

(defn- ensure-artifact
  [config package-path]
  (if-not (:artifact config)
    (assoc config :artifact (symbol (fs/file-name package-path)))
    config))

(defn ->adapter
  ([package-path]
   (->adapter package-path 10000))
  ([package-path timeout-ms]
   (let [deps-edn (-> (fs/file package-path "deps.edn")
                      (slurp)
                      (edn/read-string))
         {:keys [group artifact] :as config} (-> (:kbuild/config deps-edn)
                                                 (ensure-artifact package-path))
         coord (str group "/" artifact)
         managed-deps (get-local-deps config deps-edn)]
     (reify Adapter

       (prepare-deps-env [_ changes]
         (binding [*print-namespace-maps* false]
           (str "'"
                {:deps
                 (into {} (map
                           (fn [dep]
                             [(symbol dep)
                              {:mvn/version
                               (get-in changes [dep :version])}]))
                       managed-deps)}
                "'")))

       (get-managed-deps [_] managed-deps)

       (get-kbuild-config [_] config)

       (release-published? [_ version]
         (-> (p/future
              (let [;; ignore user's local repository cache
                    local-repo (str package-path "/.m2")]
                (try
                  (deps.util.session/with-session
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
                     ;; cleanup
                    (try (fs/delete-tree local-repo)
                         (catch Throwable _))))))
             (p/timeout timeout-ms (str "Timeout resolving mvn version for package " coord))))))))

(comment
  (def timeout-ms 3000)
  (def result (-> (p/future
                   (let [;; ignore user's local repository cache
                         local-repo "./.m2"
                         version "1.1.1"]
                     (try
                       (deps.util.session/with-session
                         (let [versions (->> (deps.ext/find-versions
                                              'transit-engineering/telemetry.clj
                                              nil
                                              :mvn {:mvn/local-repo local-repo
                                                    :mvn/repos
                                                    (merge deps.util.maven/standard-repos
                                                           {"github-transit" {:url "https://maven.pkg.github.com/transit-engineering/micro"}
                                                            "github-kepler" {:url "https://maven.pkg.github.com/kepler16/*"}})})
                                             (map :mvn/version)
                                             (set))]
                           versions
                           #_(contains? versions version)))
                       (finally
                     ;; cleanup
                         (try (fs/delete-tree local-repo)
                              (catch Throwable _))))))
                  (p/timeout timeout-ms "Timeout resolving mvn version for package")))
  @result

  (def p (-> (p/do (p/delay 3000))
             (p/timeout 200)
             (p/catch #(throw
                        (ex-info
                         "Timeout !!!"
                         {:msg (ex-message %)
                          :cause %})))))
  @p
  (deps.util.session/with-session
    (def vers (->> (deps.ext/find-versions 'transit-engineering/telemetry.clj
                                           nil
                                           :mvn
                                           {:mvn/local-repo "./m2"
                                            :mvn/repos
                                            (merge deps.util.maven/standard-repos
                                                   {"github-transit" {:url "https://maven.pkg.github.com/transit-engineering/micro"}
                                                    "github-kepler" {:url "https://maven.pkg.github.com/kepler16/*"}})})
                   (map :mvn/version)
                   (set))))
  (fs/delete-tree "./m2")
  (contains? vers "1.79.1.1-8adecdc")
  nil)
