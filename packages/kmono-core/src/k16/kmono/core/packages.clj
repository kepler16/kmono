(ns k16.kmono.core.packages
  (:require
   [babashka.fs :as fs]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.schema :as core.schema]))

(set! *warn-on-reflection* true)

(defn- create-package [project-root workspace-config package-path]
  (let [config (core.config/resolve-package-config package-path)]
    (when (and config
               (not (:excluded config))
               (not (fs/same-file? project-root package-path)))
      (let [relative-path (str (fs/relativize project-root package-path))
            package (merge {:name (symbol (fs/file-name package-path))}
                           (select-keys workspace-config [:group])
                           (select-keys config [:group :name :deps-edn])
                           {:absolute-path (str package-path)
                            :relative-path relative-path
                            :depends-on #{}})
            fqn (symbol (str (:group package))
                        (str (:name package)))]

        (when-not (:group package)
          (throw
           (ex-info (str "Missing :group config for package "
                         (:name package) ". "
                         "This either needs to be set in "
                         "the `:kmono/package` config or "
                         "in the `:kmono/workspace` config")
                    {:type :kmono/validation-error
                     :errors {:group ["required key"]}})))

        (assoc package :fqn fqn)))))

(defn- filter-dependencies [packages package]
  (into #{}
        (comp
         (map #(-> % second :local/root))
         (remove nil?)

         (map (fn [path]
                (-> (fs/file (:absolute-path package) path)
                    fs/normalize
                    str)))

         (map
          (fn [path]
            (some
             (fn [[fqn package]]
               (when (= (:absolute-path package) path)
                 fqn))
             packages)))
         (remove nil?))
        (get-in package [:deps-edn :deps])))

(defn- find-dependencies
  {:malli/schema [:-> core.schema/?PackageMap core.schema/?PackageMap]}
  [packages]
  (reduce (fn [acc [fqn package]]
            (assoc acc
                   fqn (assoc package
                              :depends-on (filter-dependencies
                                           packages package))))
          {}
          packages))

(defn- package-dependents [pkg-name packages]
  (into #{}
        (comp
         (filter (fn [[_ pkg]]
                   (contains? (:depends-on pkg)
                              pkg-name)))
         (map first))
        packages))

(defn- find-dependents
  {:malli/schema [:-> core.schema/?PackageMap core.schema/?PackageMap]}
  [packages]
  (reduce
   (fn [packages [pkg-name pkg]]
     (let [dependents (package-dependents pkg-name packages)]
       (assoc packages pkg-name (assoc pkg :dependents dependents))))
   packages
   packages))

(defn glob-matches?
  "Compare a namespaced keyword or symbol against a keyword `glob`.

  The keyword `glob` should be provided as a keyword where either the namespace
  or name component can be substituted with a `*`. For example the below are all
  valid globs

  ```clojure
  (glob-matches? :*/* :a/b) ;; true
  (glob-matches? :a/* :a/b) ;; true
  (glob-matches? :*/b :a/b) ;; true
  (glob-matches? :*/b 'a/b) ;; true

  (glob-matches? :a/a :a/b) ;; false
  ```"
  {:malli/schema [:-> :keyword [:or :keyword :symbol] :boolean]}
  [glob kw|sym]
  (let [ns-matches
        (or (= "*" (namespace glob))
            (= (namespace glob)
               (namespace kw|sym)))
        name-matches
        (or (= "*" (name glob))
            (= (name glob)
               (name kw|sym)))]
    (and ns-matches name-matches)))

(defn name-matches?
  "A `predicate-fn` constructor (returns a `predicate-fn`) designed to be used
  with [[k16.kmono.core.graph/filter-by]].

  Compares a give `pkg` :fqn against a given set of `globs` in the format
  described by [[glob-matches?]]. Returns true if a match is found.

  ```clojure
  (core.graph/filter-by (name-matches? [:*/*]))
  ```"
  [globs]
  (fn name-matches-filter-fn [pkg]
    (boolean (some #(glob-matches? % (:fqn pkg)) globs))))

(defn resolve-packages
  "Resolve the packages graph for a clojure project.

  This will find all packages as described by the given `workspace-config` and
  will use them to build a graph of all workspace packages and their
  dependencies.

  See [[k16.kmono.core.schema/?PackageMap]] for a schema of the returned package
  map."
  {:malli/schema [:=> [:cat :string core.schema/?WorkspaceConfig] core.schema/?PackageMap]}
  [project-root workspace-config]
  (let [globs (:packages workspace-config)
        globs (if (string? globs) #{globs} globs)

        dirs (mapcat #(core.fs/find-package-directories project-root %)
                     globs)

        packages
        (into {}
              (comp
               (map (partial create-package project-root workspace-config))
               (remove nil?)
               (map (juxt :fqn identity)))

              dirs)]

    (->> packages
         find-dependencies
         find-dependents
         core.graph/ensure-no-cycles!)))
