(ns k16.kmono.core.deps
  (:require
   [babashka.fs :as fs]
   [k16.kmono.core.fs :as core.fs]))

(set! *warn-on-reflection* true)

(defn- generate-extra-deps [packages]
  (reduce
   (fn [deps [fqn pkg]]
     (assoc deps fqn {:local/root (:relative-path pkg)}))
   (sorted-map)
   packages))

(defn- scope-package-alias [package alias-name]
  (let [ns' (namespace alias-name)
        alias (str
               (when ns' (str ns' "."))
               (name alias-name))]
    (-> (str (:name package) "/" alias)
        keyword)))

(defn- alter-path [project-root package path]
  (let [resolved-path
        (fs/normalize
         (fs/file (:absolute-path package) path))

        relative-path
        (fs/relativize project-root resolved-path)]

    (str relative-path)))

(defn- alter-paths [project-root package paths]
  (mapv
   (partial alter-path project-root package)
   paths))

(defn- alter-deps-paths [project-root package deps]
  (reduce
   (fn [deps [fqn coord]]
     (if-let [path (:local/root coord)]
       (assoc deps fqn {:local/root (alter-path project-root package path)})
       (assoc deps fqn coord)))
   {}
   deps))

(defn- alter-alias-paths
  [project-root package {:keys [extra-deps replace-deps
                                extra-paths replace-paths] :as alias}]
  (cond-> alias
    extra-deps (assoc :extra-deps (alter-deps-paths
                                   project-root package extra-deps))

    replace-deps (assoc :replace-deps (alter-deps-paths
                                       project-root package replace-deps))

    extra-paths (assoc :extra-paths (alter-paths
                                     project-root package extra-paths))

    replace-paths (assoc :replace-paths (alter-paths
                                         project-root package replace-paths))))

(defn generate-package-aliases [project-root package]
  (reduce
   (fn [aliases [alias-name alias]]
     (assoc aliases
            (scope-package-alias package alias-name)
            (alter-alias-paths project-root package alias)))
   {}
   (get-in package [:deps-edn :aliases])))

(defn- generate-all-package-aliases [project-root packages]
  (reduce
   (fn [aliases [_ package]]
     (merge aliases (generate-package-aliases project-root package)))
   {}
   packages))

(defn resolve-aliases [project-root packages]
  (let [local-deps (fs/file project-root "deps.local.edn")

        aliases (if (fs/exists? local-deps)
                  (:aliases (core.fs/read-edn-file! local-deps))
                  {})

        extra-deps {:extra-deps (generate-extra-deps packages)}
        package-aliases (generate-all-package-aliases project-root packages)]

    {:aliases aliases
     :packages extra-deps
     :package-aliases package-aliases

     :combined (into (sorted-map)
                     (merge aliases
                            {:kmono/packages extra-deps}
                            package-aliases))}))

(defn- glob-to-pattern [glob]
  (if (= "*" glob)
    ".*"
    glob))

(defn- alias-matches-glob [alias globs]
  (some
   (fn [glob]
     (let [pkg-glob (namespace glob)
           alias-glob (name glob)

           pkg-match (glob-to-pattern pkg-glob)
           alias-match (glob-to-pattern alias-glob)
           pattern (re-pattern (str pkg-match "/" alias-match))]

       (re-matches pattern (str (namespace alias) "/" (name alias)))))

   globs))

(defn filter-package-aliases [aliases globs]
  (into {}
        (filter
         (fn [[alias-name]]
           (alias-matches-glob alias-name globs)))
        aliases))
