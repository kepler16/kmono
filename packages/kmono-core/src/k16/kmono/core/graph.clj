(ns k16.kmono.core.graph
  (:require
   [k16.kmono.core.schema :as core.schema]))

(set! *warn-on-reflection* true)

(def ^:no-doc ?ExecOrder
  [:vector [:vector :symbol]])

(defn- cycle-path
  [parent-map from-node to-node]
  (loop [node from-node
         acc [from-node]]
    (if (= node to-node)
      (conj (vec (reverse acc)) to-node)
      (recur (get parent-map node)
             (conj acc (get parent-map node))))))

(defn- find-cycle-path-from
  [packages node visiting visited parent-map]
  (let [visiting (conj visiting node)
        neighbors (sort (get-in packages [node :depends-on]))]
    (loop [neighbors neighbors
           visited visited
           parent-map parent-map]
      (if-let [neighbor (first neighbors)]
        (cond
          (contains? visiting neighbor)
          [(cycle-path parent-map node neighbor) visited parent-map]

          (contains? visited neighbor)
          (recur (rest neighbors) visited parent-map)

          :else
          (let [[cycle visited parent-map]
                (find-cycle-path-from packages
                                      neighbor
                                      visiting
                                      visited
                                      (assoc parent-map neighbor node))]
            (if cycle
              [cycle visited parent-map]
              (recur (rest neighbors) visited parent-map))))
        [nil (conj visited node) parent-map]))))

(defn find-cycle
  "Find a dependency cycle in `packages` and returns it as a closed path.

   Returns a vector like `[a b c a]` for a cycle `a -> b -> c -> a`, or nil if
   no cycle is found."
  [packages]
  (loop [nodes (sort (keys packages))
         visited #{}
         parent-map {}]
    (when-let [node (first nodes)]
      (if (contains? visited node)
        (recur (rest nodes) visited parent-map)
        (let [[cycle visited parent-map]
              (find-cycle-path-from packages node #{} visited parent-map)]
          (if cycle
            cycle
            (recur (rest nodes) visited parent-map)))))))

(defn ensure-no-cycles!
  "Same as [[find-cycles]] but will throw an exception if any cycles are found."
  [packages]
  (let [cycle (find-cycle packages)]
    (when cycle
      (throw (ex-info "Circular dependencies found"
                      {:type :kmono/circular-dependencies
                       :cycles [cycle]}))))
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

(defn query-dependencies
  "Find all dependency packages of `pkg-name` within the given `packages` map.

  This includes all transitive dependencies."
  {:malli/schema [:=> [:cat core.schema/?PackageMap :symbol] [:set :symbol]]}
  [packages pkg-name]

  (let [pkg (get packages pkg-name)
        dependencies
        (mapcat
         (fn [dep-pkg-name]
           (query-dependencies packages dep-pkg-name))
         (:depends-on pkg))]

    (into (set (:depends-on pkg)) dependencies)))

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
