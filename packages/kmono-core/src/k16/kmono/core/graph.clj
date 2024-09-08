(ns k16.kmono.core.graph
  (:require
   [k16.kmono.core.schema :as core.schema]))

(set! *warn-on-reflection* true)

(def ?ExecOrder
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
  "Same as `k16.kmono.core.graph/find-cycles` but will throw an exception if any
  cycles are found."
  [packages]
  (let [cycles (find-cycles packages)]
    (when (seq cycles)
      (throw (ex-info "Cicrlar dependencies found"
                      {:type :kmono/circular-dependencies
                       :cycles cycles}))))
  packages)

(defn parallel-topo-sort
  "Sort a give `packages` map by the order in which the packages therein depend
  on each other.

  As an example, if I have 3 packages `a`, `b`, `c` and `b` depends on `a` then:
  
  ```clojure
  (parallel-topo-sort {a {} b {} c {}})
  ;; => [[a c] [b]]
  ```"
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
  "Find all transitive dependent packages of `pkg-name` within the give
  `packages` map."
  {:malli/schema [:=> [:cat core.schema/?PackageMap :symbol] [:set :symbol]]}
  [packages pkg-name]

  (let [pkg (get packages pkg-name)
        dependents
        (mapcat
         (fn [dependent-pkg-name]
           (query-dependents packages dependent-pkg-name))
         (:dependents pkg))]

    (set (concat (:dependents pkg) dependents))))

(defn filter-by
  "Filter a given map of `packages` by those that the given `filter-fn`
  predicate returns `true`.

  If the `:include-dependents` property is `true` then all dependent packages of
  the retained packages will also be kept.

  This function will update the `:depends-on` and `:dependent` keys of each
  retained package to include only other packages that still remain."
  ([filter-fn packages] (filter-by filter-fn {} packages))
  ([filter-fn {:keys [include-dependents]} packages]
   (let [filtered
         (->> packages
              (mapv (fn [[pkg-name pkg]]
                      (future
                        [pkg-name (filter-fn pkg)])))
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
                    (concat [pkg-name] (query-dependents packages pkg-name))))
                 filtered)
           filtered)]

     (into {}
           (map
            (fn [pkg-name]
              (let [pkg (-> (get packages pkg-name)
                            (update :depends-on
                                    (fn [deps]
                                      (->> deps
                                           (filter (fn [dep]
                                                     (contains? filtered dep)))

                                           set)))
                            (update :dependents
                                    (fn [deps]
                                      (->> deps
                                           (filter (fn [dep]
                                                     (contains? filtered dep)))

                                           set))))]

                [pkg-name pkg])))

           filtered))))
