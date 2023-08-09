(ns k16.kbuild.config
  (:require
   [k16.kbuild.adapters.clojure-deps :as clj.deps]
   [k16.kbuild.adapter :as adapter]
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.string :as string]
   [flatland.ordered.set :as oset]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [malli.util :as mu]))

(def ?Package
  [:map
   [:name :string]
   [:dir :string]
   [:depends_on {:optional true}
    [:vector :string]]
   [:adapter {:default :clojure-deps}
    :keyword]
   [:fallback_version {:optional true}
    :string]
   [:release_cmd {:optional true} :string]
   [:build_cmd :string]])

(def ?Config
  [:map {:closed false}
   [:defaults
    (-> (mu/optional-keys ?Package)
        (mu/dissoc :name)
        (mu/dissoc :dir)
        (mu/dissoc :depends_on))]
   [:packages [:vector ?Package]]])

(def ^:dynamic *config-dir* ".")

(defn -get-adapder
  [{:keys [adapter dir]}]
  (let [pkg-dir (fs/file *config-dir* dir)]
    (case adapter
      :clojure-deps (clj.deps/adapter (fs/file pkg-dir "deps.edn"))
      (clj.deps/adapter (fs/file pkg-dir "deps.edn")))))

(def get-adapter (memoize -get-adapder))

(defn- read-config
  [config-dir]
  (let [config-file (fs/file config-dir "kbuild.edn")]
    (if (fs/exists? config-file)
      (let [config-edn (->> config-file
                            (slurp)
                            (edn/read-string))]
        (m/decode ?Config
                  config-edn
                  mt/default-value-transformer))
      (throw (ex-info "Config file not found (json/edn)"
                      {:config-dir config-dir})))))

(defn create-graph [packages]
  (reduce (fn [acc {:keys [name depends_on]}]
            (assoc acc name (or (set depends_on) #{})))
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
      (throw (ex-info (str "Cicrlar dependency error:\n "
                           (string/join " ->\n " cycle-path))
                      {:cycle cycle-path})))))

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
      (throw (ex-info (str "Unknown dependencies:\n "
                           (string/join "\n-" diff))
                      {:unknowns diff})))))

(defn parallel-topo-sort
  [graph]
  (when (seq graph)
    (when-let [ks (seq (keep (fn [[k v]] (when (empty? v) k)) graph))]
      (into [ks]
            (parallel-topo-sort
             (into {}
                   (map (fn [[k v]] [k (apply disj v ks)]))
                   (apply dissoc graph ks)))))))

(defn gather-deps
  [packages]
  (mapv (fn [pkg]
          (assoc pkg :depends_on (-> pkg (get-adapter) (adapter/get-local-deps))))
        packages))

(defn load-config
  "Loads config from a file, accepts a directory where config is located,
  defaults to current dir. Returns a map with parsed config and build order.
  Build order is a list of parallel builds, where parallel build
  is a list of package names which can run simultaneously"
  ([]
   (load-config "."))
  ([config-dir]
   (assert config-dir "Config dir is not specified")
   (binding [*config-dir* config-dir]
     (let [config-map (read-config config-dir)
           packages (mapv (fn [pkg]
                            (m/decode ?Package
                                      (merge (:defaults config-map) pkg)
                                      mt/default-value-transformer))
                          (:packages config-map))
           with-defaults (assoc config-map :packages packages)]
       (if-let [err (m/explain ?Config with-defaults)]
         (throw (ex-info "Config validation error" {:explanation (me/humanize err)}))
         (let [graph (-> packages gather-deps create-graph)]
           (assert-missing-deps! graph)
           (assert-cycles! graph)
           {:config with-defaults
            :graph graph
            :build-order (parallel-topo-sort graph)}))))))

(comment
  (def graph {"transit-engineering/gx" #{},
              "transit-engineering/util.clj" #{"transit-engineering/bar"},
              "transit-engineering/auth.clj" #{"transit-engineering/util.clj"},
              "transit-engineering/telemetry.clj" #{"transit-engineering/util.clj"},
              "transit-engineering/bar"
              #{"transit-engineering/telemetry.clj" "transit-engineering/auth.clj"}})

  (set (reduce into [] (vals graph)))

  (find-missing-deps graph)
  (assert-missing-deps! graph)

  (assert-cycles! graph)

  (slurp "/Users/armed/Developer/k16/transit/micro/packages/auth.clj/deps.edn")
  (def config-dir "/Users/armed/Developer/k16/transit/micro")

  (binding [*config-dir* config-dir]
    (-> {:dir "packages/mongo.clj"
         :adapter :clojure-deps}
        (get-adapter)
        (adapter/get-local-deps)))
  (def config (load-config "/Users/armed/Developer/k16/transit/micro"))

  (find-cycles graph)

  (def graph (create-graph (:packages config))) ; false (in this case)

  (parallel-topo-sort graph)

  (empty-deps-node graph))

(comment

  nil)
