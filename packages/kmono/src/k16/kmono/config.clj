(ns k16.kmono.config
  (:require
   [babashka.fs :as fs]
   [clojure.set :as set]
   [clojure.string :as string]
   [flatland.ordered.set :as oset]
   [k16.kmono.adapter :as adapter]
   [k16.kmono.adapters.clojure-deps :as clj.deps]
   [k16.kmono.adapters.kmono-edn :as kmono.edn]
   [k16.kmono.ansi :as ansi]
   [k16.kmono.config-schema :as schema]
   [k16.kmono.git :as git]
   [malli.core :as m]
   [malli.error :as me]))

(defn get-adapter
  [pkg-dir]
  (if (fs/exists? (fs/file pkg-dir "kmono.edn"))
    (kmono.edn/->adapter (fs/file pkg-dir))
    (clj.deps/->adapter (fs/file pkg-dir))))

(defn- assert-schema!
  [?schema value]
  (assert (m/validate ?schema value)
          (me/humanize (m/explain ?schema value)))
  value)

(defn validate-config!
  [config]
  (assert-schema! schema/?Config config))

(defn- create-package-config [package-dir]
  (let [adapter (get-adapter package-dir)
        kb-pkg-config (->> (adapter/get-kmono-config adapter)
                           (assert-schema! schema/?KmonoPackageConfig))
        artifact (or (:artifact kb-pkg-config)
                     (symbol (fs/file-name package-dir)))
        pkg-name (str (:group kb-pkg-config) "/" artifact)
        pkg-commit-sha (git/subdir-commit-sha package-dir)]

    (let [pkg-config (merge kb-pkg-config
                            {:artifact (or (:artifact kb-pkg-config)
                                           (symbol (fs/file-name package-dir)))
                             :name pkg-name
                             :commit-sha pkg-commit-sha
                             :adapter adapter
                             :dir (str package-dir)})]
      (->> (assoc pkg-config :depends-on (adapter/get-managed-deps adapter))
           (assert-schema! schema/?Package)))))

(defn- create-config
  [repo-root glob]
  (let [package-dirs (fs/glob repo-root glob)]
    {:packages (mapv create-package-config package-dirs)}))

(defn create-graph
  {:malli/schema [:=> [:cat schema/?Packages] schema/?Graph]}
  [packages]
  (ansi/print-info "creating graph...")
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
  {:malli/schema [:=> [:cat schema/?Graph] [:maybe schema/?BuildOrder]]}
  [graph]
  (when (seq graph)
    (when-let [ks (seq (keep (fn [[k v]] (when (empty? v) k)) graph))]
      (vec (into [(vec ks)]
                 (parallel-topo-sort
                  (into {}
                        (map (fn [[k v]] [k (apply disj v ks)]))
                        (apply dissoc graph ks))))))))

(defn- ->pkg-map
  {:malli/schema [:=> [:cat schema/?Config] schema/?PackageMap]}
  [packages]
  (into {} (map (juxt :name identity)) packages))

(defn load-config
  "Loads config from a file, accepts a directory where config is located,
  defaults to current dir. Returns a map with parsed config and build order.
  Build order is a list of parallel builds, where parallel build
  is a list of package names which can run simultaneously"
  {:malli/schema [:function
                  [:=> :cat schema/?Config]
                  [:=> [:cat :string] schema/?Config]
                  [:=> [:cat :string :string] schema/?Config]]}
  ([]
   (load-config "." "packages/*"))
  ([repo-root]
   (load-config repo-root "packages/*"))
  ([repo-root glob]
   (ansi/assert-err! repo-root "config dir is not specified")
   (ansi/print-info "loading config...")
   (let [config (create-config repo-root glob)
         packages (:packages config)]
     (ansi/print-info (count packages) "packages found: ")
     (doseq [pkg packages]
       (ansi/print-info "\t" (:name pkg)))
     (if-let [err (m/explain schema/?Packages packages)]
       (throw (ex-info "Config validation error" {:body (me/humanize err)}))
       (let [graph (create-graph packages)]
         (assert-missing-deps! graph)
         (assert-cycles! graph)
         {:repo-root repo-root
          :packages packages
          :package-map (->pkg-map packages)
          :graph graph
          :build-order (parallel-topo-sort graph)})))))

