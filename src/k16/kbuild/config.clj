(ns k16.kbuild.config
  (:require
   [babashka.fs :as fs]
   [clojure.set :as set]
   [clojure.string :as string]
   [flatland.ordered.set :as oset]
   [k16.kbuild.adapter :as adapter]
   [k16.kbuild.adapters.clojure-deps :as clj.deps]
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]))

(def ?KbuldPackageConfig
  [:map
   [:group [:or :string :symbol]]
   [:artifact {:optional true}
    [:maybe [:or :string :symbol]]]
   [:aliases {:optional true}
    [:vector :keyword]]
   [:release-cmd {:optional true}
    :string]
   [:build-cmd :string]])

(def ?Package
  (-> ?KbuldPackageConfig
      (mu/required-keys [:artifact])
      (mu/assoc :depends-on [:vector :string])
      (mu/assoc :name :string)))

(def ?Packages
  [:vector ?Package])

(def ?PackageMap
  [:map-of :string ?Package])

(def ?Graph
  [:map-of :string [:set :string]])

(def ?BuildOrder
  [:vector [:vector :string]])

(def ?Config
  [:map {:closed true}
   [:dry-run? {:optional true} :boolean]
   [:snapshot? {:optional true} :boolean]
   [:repo-root :string]
   [:packages ?Packages]
   [:package-map ?PackageMap]
   [:graph ?Graph]
   [:build-order ?BuildOrder]])

(defn get-adapter
  [pkg-dir]
  (clj.deps/->adapter (fs/file pkg-dir)))

(defn- assert-schema!
  [?schema value]
  (assert (m/validate ?schema value)
          (me/humanize (m/explain ?schema value)))
  value)

(defn validate-config!
  [config]
  (assert-schema! ?Config config))

(defn- create-package-config [package-dir]
  (let [adapter (get-adapter package-dir)
        kb-pkg-config (->> (adapter/get-kbuild-config adapter)
                           (assert-schema! ?KbuldPackageConfig))
        artifact (or (:artifact kb-pkg-config)
                     (symbol (fs/file-name package-dir)))
        pkg-name (str (:group kb-pkg-config) "/" artifact)]

    (let [pkg-config (merge kb-pkg-config
                            {:artifact (or (:artifact kb-pkg-config)
                                           (symbol (fs/file-name package-dir)))
                             :name pkg-name
                             :adapter adapter
                             :dir (str package-dir)})]
      (->> (assoc pkg-config :depends-on (adapter/get-managed-deps adapter))
           (assert-schema! ?Package)))))

(defn- create-config
  [root-dir glob]
  (let [package-dirs (fs/glob root-dir glob)]
    {:packages (mapv create-package-config package-dirs)}))

(defn create-graph
  {:malli/schema [:=> [:cat ?Packages] ?Graph]}
  [packages]
  (println "Creating graph...")
  (reduce (fn [acc {:keys [name depends-on]}]
            (assoc acc name (or (set depends-on) #{})))
          {}
          packages))

(defn find-cycles
  [graph]
  (loop [nodes (keys graph)
         node (first nodes)
         path (oset/ordered-set)]
    (when node
      (let [links (get graph node)]
        (if (seq links)
          (if (contains? path node)
            (conj (vec path) node)
            (recur links
                   (first links)
                   (conj path node)))
          (recur (rest nodes)
                 (first (rest nodes))
                 (disj path node)))))))

(defn assert-cycles!
  [graph]
  (let [cycle-path (find-cycles graph)]
    (when (seq cycle-path)
      (throw (ex-info (str "Cicrlar dependency error")
                      {:body cycle-path})))))

(defn find-missing-deps
  [graph]
  (let [defined (set (keys graph))
        all-deps (->> graph
                      (vals)
                      (reduce into [])
                      (set))]
    (set/difference all-deps defined)))

(defn assert-missing-deps!
  [graph]
  (let [diff (find-missing-deps graph)]
    (when (seq diff)
      (throw (ex-info "Unknown dependencies"
                      {:body (str "- " (string/join "\n- " diff))})))))

(defn parallel-topo-sort
  {:malli/schema [:=> [:cat ?Graph] [:maybe ?BuildOrder]]}
  [graph]
  (when (seq graph)
    (when-let [ks (seq (keep (fn [[k v]] (when (empty? v) k)) graph))]
      (vec (into [(vec ks)]
                 (parallel-topo-sort
                  (into {}
                        (map (fn [[k v]] [k (apply disj v ks)]))
                        (apply dissoc graph ks))))))))

(defn- ->pkg-map
  {:malli/schema [:=> [:cat ?Config] ?PackageMap]}
  [packages]
  (into {} (map (juxt :name identity)) packages))

(defn load-config
  "Loads config from a file, accepts a directory where config is located,
  defaults to current dir. Returns a map with parsed config and build order.
  Build order is a list of parallel builds, where parallel build
  is a list of package names which can run simultaneously"
  {:malli/schema [:function
                  [:=> :cat ?Config]
                  [:=> [:cat :string] ?Config]
                  [:=> [:cat :string :string] ?Config]]}
  ([]
   (load-config "." "packages/*"))
  ([repo-root]
   (load-config repo-root "packages/*"))
  ([repo-root glob]
   (assert repo-root "Config dir is not specified")
   (println "Loading config...")
   (let [config (create-config repo-root glob)
         packages (:packages config)]
     (println (count packages) "packages found: ")
     (doseq [pkg packages]
       (println "\t" (:name pkg)))
     (if-let [err (m/explain ?Packages packages)]
       (throw (ex-info "Config validation error" {:body (me/humanize err)}))
       (let [graph (create-graph packages)]
         (assert-missing-deps! graph)
         (assert-cycles! graph)
         {:repo-root repo-root
          :packages packages
          :package-map (->pkg-map packages)
          :graph graph
          :build-order (parallel-topo-sort graph)})))))

(comment

  (def config (load-config "/Users/armed/Developer/k16/transit/micro"))
  (def graph (create-graph (:packages config)))
  (def topo (parallel-topo-sort graph))

  (let [p (second packages)]
    (-> p :adapter (adapter/get-managed-deps p)))

  nil)

