(ns k16.kbuild.adapters.clojure-deps
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
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

(defn ->adapter
  [package-path]
  (let [deps-edn (-> (fs/file package-path "deps.edn")
                     (slurp)
                     (edn/read-string))
        config (:kbuild/config deps-edn)
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

      (get-kbuild-config [_] config))))

