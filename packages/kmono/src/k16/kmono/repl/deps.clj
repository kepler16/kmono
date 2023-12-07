(ns k16.kmono.repl.deps
  (:require
   [babashka.fs :as fs]
   [clojure.walk :as walk]
   [k16.kmono.adapters.clojure-deps :as clj.deps]))

(def path-aliases #{:paths :extra-paths :replace-paths :local/root})

(defn relativize-path
  [root package-dir path]
  (assert (and root package-dir path)
          {:root root
           :package-dir package-dir
           :path path})
  (fs/relativize root (fs/path package-dir path)))

(defn relativize-paths
  "Relativise paths from package alias opts to work in repo root context"
  [repo-root package-dir aliases-map]
  (letfn [(relativize [path]
            (str (relativize-path repo-root package-dir path)))]
    (walk/postwalk
     (fn [form]
       (if (map-entry? form)
         (let [[k v] form]
           (if (contains? path-aliases k)
             [k (if (string? v)
                  (relativize v)
                  (mapv relativize v))]
             form))
         form))
     aliases-map)))

(defn get-package-alias-map!
  "Returns alias map from a given package/alias pair.
  Accepts kmono config (`config.schema/?Config`) and a package/alias pair as
  a namespaced keyord where namespace is a package name and a name is an alias,
  e.g. `:my-package/test`.
  Returns an extracted alias map from package's deps.edn"
  [{:keys [package-dirs repo-root]} package-alias]
  (let [pkg-alias (keyword package-alias)
        package-name (namespace pkg-alias)
        alias-key (-> pkg-alias (name) (keyword))
        _ (assert package-name (str "Could not get package from package-alias ["
                                    package-alias "]"))
        _ (assert alias-key (str "Could not get alias from package-alias ["
                                 package-alias "]"))
        package-dir (get package-dirs package-name)
        deps-edn (clj.deps/read-deps-edn! package-dir)]
    {package-alias (relativize-paths
                    repo-root
                    package-dir
                    (get-in deps-edn [:aliases alias-key]))}))

(defn construct-sdeps-overrides!
  "Accepts kmono config and a collection pairs of package/alias and
  returns a string for -Sdeps argument"
  [config package-alias-pairs]
  (let [package-dirs (->> config
                          :packages
                          (map (fn [pkg]
                                 [(fs/file-name (fs/path (:dir pkg)))
                                  (:dir pkg)]))
                          (into {}))
        aliases (into {}
                      (map (partial get-package-alias-map!
                                    {:package-dirs package-dirs
                                     :repo-root (:repo-root config)}))
                      package-alias-pairs)]
    {:aliases aliases}))

