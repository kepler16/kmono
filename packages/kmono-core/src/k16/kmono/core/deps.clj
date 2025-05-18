(ns k16.kmono.core.deps
  (:require
   [babashka.fs :as fs]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.packages :as core.packages]))

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

(defn- generate-package-aliases [project-root package]
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

(defn filter-package-aliases
  "Filter the given `packages` map by those that contain aliases described by at
  least one of the given given `globs`.

  Returns a map with pkg-name as the key and the set of aliases from that
  package that matched the globs.

  ```clojure
  (filter-package-aliases {:package-a {}} [:*/test])
  ;; => {:package-a #{:test}}
  ```"
  [globs packages]
  (reduce
   (fn [packages [pkg-name pkg]]
     (let [aliases (get-in pkg [:deps-edn :aliases] {})

           aliases
           (into #{}
                 (comp
                  (map first)
                  (filter
                   (fn [pkg-alias]
                     (let [scoped-alias (scope-package-alias pkg pkg-alias)]
                       (some
                        #(core.packages/glob-matches? % scoped-alias)
                        globs)))))
                 aliases)]

       (if (seq aliases)
         (assoc packages pkg-name aliases)
         packages)))
   {}
   packages))

(defn generate-sdeps-aliases
  "Generate an `-Sdeps` compatible map containing aliases generated from various
  workspace sources.

  This is the primary way of augmenting clojure with new classpath information.

  Aliases are generated from:

  1. The set of packages in the workspace. These are added into an alias called
     `:kmono/packages` containing `:extra-deps`.
  2. All the aliases from all packages in the workspace are raised up and
     combined, scoping their alias names to the package name.
  3. The aliases from `deps.local.edn` in the project root."
  [project-root packages]
  (let [local-deps
        (fs/file project-root "deps.local.edn")

        local-aliases
        (when (fs/exists? local-deps)
          (:aliases (core.fs/read-edn-file! local-deps)))

        extra-deps
        {:extra-deps (generate-extra-deps packages)}

        package-aliases
        (generate-all-package-aliases project-root packages)]

    (into (sorted-map)
          (merge local-aliases
                 {:kmono/packages extra-deps}
                 package-aliases))))
