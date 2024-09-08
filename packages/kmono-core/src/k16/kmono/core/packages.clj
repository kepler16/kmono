(ns k16.kmono.core.packages
  (:require
   [babashka.fs :as fs]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.schema :as core.schema]))

(set! *warn-on-reflection* true)

(defn- create-package [project-root workspace-config package-path]
  (when-let [config (core.config/resolve-package-config workspace-config package-path)]
    (let [package
          (merge {:name (symbol (fs/file-name package-path))}
                 (select-keys config [:group :name :deps-edn])
                 {:absolute-path (str package-path)
                  :relative-path (str (fs/relativize project-root package-path))
                  :depends-on #{}})]

      (assoc package :fqn (symbol (str (:group package) "/" (:name package)))))))

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
  {:malli/schema [:=> [:cat core.schema/?PackageMap] core.schema/?PackageMap]}
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
  {:malli/schema [:=> [:cat core.schema/?PackageMap] core.schema/?PackageMap]}
  [packages]
  (reduce
   (fn [packages [pkg-name pkg]]
     (let [dependents (package-dependents pkg-name packages)]
       (assoc packages pkg-name (assoc pkg :dependents dependents))))
   packages
   packages))

(defn resolve-packages
  "Resolve the packages graph for a clojure project.

  This will find all packages as described by the given `workspace-config` and
  will use them to build a graph of all workspace packages and their
  dependencies.

  See `k16.kmono.core.schema/?PackageMap` for a schema of the returned package
  map."
  {:malli/schema [:=> [:cat :string core.schema/?WorkspaceConfig] core.schema/?PackageMap]}
  [project-root workspace-config]
  (let [dirs (core.fs/find-package-directories
              project-root (:packages workspace-config))

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
