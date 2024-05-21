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
   [k16.kmono.util :as util]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]))

(defn- read-workspace-config
  [repo-root deps-file]
  (let [wp-deps-file (fs/file repo-root deps-file)]
    (some-> (when (fs/exists? wp-deps-file)
              (util/read-deps-edn! wp-deps-file))
            (select-keys [:kmono/workspace :kmono/config]))))

(defn get-workspace-config
  [repo-root]
  (let [kmono-props (read-workspace-config repo-root "deps.edn")
        kmono-props-local (read-workspace-config repo-root "deps.local.edn")
        kmono-merged-props (merge-with merge kmono-props kmono-props-local)
        workspace-config (:kmono/workspace kmono-merged-props)]
    (when (seq workspace-config)
      (ansi/assert-err!
       (not (seq (:kmono/config kmono-merged-props)))
       "Both `:kmono/config` and `:kmono/workspace can't be set")
      (schema/assert-schema!
       schema/?KmonoWorkspaceConfig "Workspace config error" workspace-config))
    (m/encode schema/?KmonoWorkspaceConfig (or workspace-config {})
              (mt/default-value-transformer
               {::mt/add-optional-keys true}))))

(defn get-adapter
  [pkg-dir]
  (or (kmono.edn/->adapter (fs/file pkg-dir))
      (clj.deps/->adapter (fs/file pkg-dir))))

(defn validate-config!
  [config]
  (schema/assert-schema! schema/?Config config))

(defn- read-package-config
  [workspace-config package-dir]
  (when-let [adapter (get-adapter package-dir)]
    (let [derived-fields (cond-> (select-keys workspace-config
                                              [:group :build-cmd :release-cmd])
                           :always (assoc :dir (str package-dir)
                                          :adapter adapter))]
      (some->> (adapter/get-kmono-config adapter)
               (merge derived-fields)
               (schema/assert-schema! schema/?KmonoPackageConfig
                                      "Package config init error")))))

(defn- create-package-config
  [repo-root glob {:keys [adapter] :as pkg-config}]
  (when pkg-config
    (let [package-dir (:dir pkg-config)
          git-repo? (git/git-initialzied? repo-root)
          artifact (or (:artifact pkg-config)
                       (symbol (fs/file-name package-dir)))
          pkg-name (str (:group pkg-config) "/" artifact)
          root-package? (fs/same-file? repo-root package-dir)
          exclusions (when root-package?
                       (str ":!:" glob))
          pkg-commit-sha (or (when git-repo?
                               (git/subdir-commit-sha exclusions package-dir))
                             "untracked")
          pkg-config (merge pkg-config
                            {:artifact (or (:artifact pkg-config)
                                           (symbol (fs/file-name package-dir)))
                             :name pkg-name
                             :commit-sha pkg-commit-sha
                             :root-package? root-package?})]
      (->> (assoc pkg-config :depends-on (adapter/get-managed-deps adapter))
           (schema/assert-schema! schema/?Package "Package config init error")))))

(defn- create-config
  [repo-root glob]
  (let [workspace-config (get-workspace-config repo-root)
        glob' (or glob (:glob workspace-config))
        package-dirs (conj (fs/glob repo-root glob')
                           (-> (fs/path repo-root)
                               (fs/normalize)
                               (fs/absolutize)))]
    (merge
     workspace-config
     {:glob glob'
      :repo-root repo-root
      :packages (into []
                      (comp
                       (map (partial read-package-config workspace-config))
                       (map (partial create-package-config repo-root glob'))
                       (remove nil?))
                      package-dirs)})))

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
         (merge config {:package-map (->pkg-map packages)
                        :graph graph
                        :build-order (parallel-topo-sort graph)}))))))

(comment
  (get-workspace-config ".")
  (load-config "." nil)
  nil)
