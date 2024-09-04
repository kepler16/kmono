(ns k16.kmono.core.graph
  (:require
   [k16.kmono.core.schema :as core.schema]))

(set! *warn-on-reflection* true)

(def ?ExecOrder
  [:vector [:vector :symbol]])

(defn find-cycles [packages]
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

(defn ensure-no-cycles! [packages]
  (let [cycles (find-cycles packages)]
    (when (seq cycles)
      (throw (ex-info "Cicrlar dependencies found"
                      {:type :kmono/circular-dependencies
                       :cycles cycles}))))
  packages)

(defn parallel-topo-sort
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
