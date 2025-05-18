(ns k16.kmono.core.graph
  (:require
   [k16.kmono.core.schema :as core.schema]))

(set! *warn-on-reflection* true)

(def ^:no-doc ?ExecOrder
  [:vector [:vector :symbol]])

(defn find-cycles
  "Try find cyclic dependencies between packages in a given `packages` map"
  [packages]
  (loop [nodes (keys packages)
         node (first nodes)
         path #{}]
    (when node
      (let [package (get packages node)]
        (if (seq (:depends-on package))
          (if (contains? path node)
            (conj (set path) node)
            (recur (:depends-on package)
                   (first (:depends-on package))
                   (conj path node)))
          (recur (rest nodes)
                 (first (rest nodes))
                 (disj path node)))))))

(defn ensure-no-cycles!
  "Same as [[find-cycles]] but will throw an exception if any cycles are found."
  [packages]
  (let [cycles (find-cycles packages)]
    (when (seq cycles)
      (throw (ex-info "Cicrlar dependencies found"
                      {:type :kmono/circular-dependencies
                       :cycles cycles}))))
  packages)

(defn parallel-topo-sort
  "Sort a given `packages` map by the order in which the packages therein depend
  on each other.

  As an example, if I have 3 packages `a`, `b`, `c` and `b` depends on `a`
  then:

  ```clojure
  (parallel-topo-sort {a {} b {} c {}})
  ;; => [[a c] [b]]
  ```

  This is generally used to calculate the execution order of packages when
  trying to run commands in subpackages or build/release packages in the
  correct order."
  {:malli/schema [:=> [:cat core.schema/?PackageMap] [:maybe ?ExecOrder]]}
  [packages]
  (let [stage
        (into #{}
              (comp
               (filter (fn [[_ pkg]]
                         (empty? (:depends-on pkg))))
               (map first))
              packages)

        remaining
        (reduce
         (fn [packages [pkg-name pkg]]
           (if-not (contains? stage pkg-name)
             (let [deps (apply disj (:depends-on pkg) stage)
                   pkg (assoc pkg :depends-on deps)]
               (assoc packages pkg-name pkg))
             packages))
         {}
         packages)]

    (when (seq stage)
      (into [(-> stage sort vec)] (parallel-topo-sort remaining)))))

(defn query-dependents
  "Find all dependent packages of `pkg-name` within the give `packages` map.

  This includes all transitive dependencies."
  {:malli/schema [:=> [:cat core.schema/?PackageMap :symbol] [:set :symbol]]}
  [packages pkg-name]

  (let [pkg (get packages pkg-name)
        dependents
        (mapcat
         (fn [dependent-pkg-name]
           (query-dependents packages dependent-pkg-name))
         (:dependents pkg))]

    (into (set (:dependents pkg)) dependents)))

(defn- update-graph-edges
  [packages filtered]
  (into
   {}
   (map (fn [pkg-name]
          (let [pkg (get packages pkg-name)

                depends-on
                (into #{}
                      (filter (fn [dep]
                                (contains? filtered dep)))
                      (:depends-on pkg))

                dependents
                (into #{}
                      (filter (fn [dep]
                                (contains? filtered dep)))
                      (:dependents pkg))

                pkg (assoc pkg
                           :depends-on depends-on
                           :dependents dependents)]

            [pkg-name pkg])))

   filtered))

(defn filter-by
  "Filter a given `packages` map by those that match the given `predicate-fn`.

  If the `:include-dependents` property is `true` then all dependent packages
  of the retained packages will also be kept.

  This function will update the `:depends-on` and `:dependent` keys of each
  retained package to include only other packages that still remain in the map.

  It's generally recommended to use this function instead of writing your own
  package filtering. If you need to write your own then you should also make
  sure to keep the `:depends-on` and `:dependents` updated."
  ([predicate-fn packages] (filter-by predicate-fn {} packages))
  ([predicate-fn {:keys [include-dependents]} packages]
   (let [filtered
         (->> packages
              (mapv (fn [[pkg-name pkg]]
                      (future
                        [pkg-name (predicate-fn pkg)])))
              (mapv deref))

         filtered
         (into #{}
               (comp
                (filter second)
                (map first))
               filtered)

         filtered
         (if include-dependents
           (into #{}
                 (mapcat
                  (fn [pkg-name]
                    (into [pkg-name] (query-dependents packages pkg-name))))
                 filtered)
           filtered)]

     (update-graph-edges packages filtered))))
