(ns k16.kmono.repl.deps
  (:require
   [babashka.fs :as fs]
   [babashka.process :as bp]
   [clojure.edn :as edn]
   [clojure.pprint :as pprint]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [k16.kmono.adapters.clojure-deps :as clj.deps]
   [k16.kmono.ansi :as ansi]
   [k16.kmono.config :as config]
   [k16.kmono.util :as util]
   [malli.core :as m]))

(def path-aliases #{:paths :extra-paths :replace-paths :local/root})

(defn relativize-path
  [root package-dir path]
  (assert (and root package-dir path)
          {:root root
           :package-dir package-dir
           :path path})
  (if (fs/same-file? root package-dir)
    (fs/path package-dir path)
    (fs/relativize root (fs/path package-dir path))))

(defn- strip-extra-parent
  "JRE 8 workaround"
  [str-path]
  (if (string/starts-with? str-path "../")
    (subs str-path 3)
    str-path))

(defn relativize-paths
  "Relativise paths from package alias opts to work in repo root context"
  [repo-root package-dir aliases-map]
  (when (seq aliases-map)
    (letfn [(relativize [path]
              (-> (relativize-path repo-root package-dir path)
                  (str)
                  (strip-extra-parent)))]
      (walk/postwalk
       (fn [form]
         (if (map-entry? form)
           (let [[k v] form]
             (if (contains? path-aliases k)
               [k (if (string? v)
                    (relativize v)
                    (mapv relativize v))]
               form))
           form))
       aliases-map))))

(defn all-packages-deps-alias
  [packages]
  (let [pkg-deps (reduce (fn [deps {:keys [name dir]}]
                           (assoc deps (symbol name) {:local/root dir}))
                         {}
                         packages)]
    (when (seq pkg-deps)
      {:kmono/package-deps {:extra-deps pkg-deps}})))

(defn get-package-alias-map
  "Returns alias map from a given package/alias pair.
  Accepts kmono config (`config.schema/?Config`) and a package/alias pair as
  a namespaced keyord where namespace is a package name and a name is an alias,
  e.g. `:my-package/test`.
  Returns an extracted alias map from package's deps.edn"
  [{:keys [package-dirs repo-root]} package-alias]
  (let [package-name (or (namespace package-alias) (name package-alias))
        package-dir (get package-dirs package-name)
        alias-key (-> package-alias (name) (keyword))
        _ (assert package-name (str "Could not get package from package-alias ["
                                    package-alias "]"))
        _ (assert alias-key (str "Could not get alias from package-alias ["
                                 package-alias "]"))
        deps-edn (clj.deps/read-pkg-deps! package-dir)
        alias-name (keyword (str "kmono.pkg/" package-name "." (name alias-key)))]
    {alias-name (or (relativize-paths
                     repo-root
                     package-dir
                     (get-in deps-edn [:aliases alias-key]))
                    {})}))

(defn- expand-package-alias-pairs
  [packages package-alias-pairs]
  (->> package-alias-pairs
       (map (fn [pair]
              (let [pair' (keyword pair)]
                (if (= "*" (namespace pair'))
                  (map (fn [p]
                         (keyword p (name pair')))
                       packages)
                  pair'))))
       (flatten)
       (vec)))

(defn construct-sdeps-overrides!
  "Accepts kmono config and a collection pairs of package/alias and
  returns a string for -Sdeps argument"
  [config package-alias-pairs]
  (let [package-dirs (->> config
                          :packages
                          (map (fn [pkg]
                                 [(fs/file-name (fs/normalize
                                                 (fs/path (:dir pkg))))
                                  (-> (:dir pkg)
                                      (fs/normalize)
                                      (str))]))
                          (into {}))
        package-deps (all-packages-deps-alias (:packages config))
        package-alias-pairs' (expand-package-alias-pairs
                              (keys package-dirs)
                              package-alias-pairs)
        package-aliases (into {}
                              (comp
                               (map (partial get-package-alias-map
                                             {:package-dirs package-dirs
                                              :repo-root (:repo-root config)})))
                              package-alias-pairs')
        override {:aliases (merge package-deps package-aliases)}]
    override))

(def ?ReplParams
  [:map
   [:main-aliases {:optional true}
    [:vector :keyword]]
   [:aliases {:optional true}
    [:vector :keyword]]
   [:packages-aliases {:optional true}
    [:vector :keyword]]])

(def nrepl-alias
  {:kmono/nrepl {:extra-deps {'cider/cider-nrepl {:mvn/version "0.44.0"}}
                 :main-opts ["-m"
                             "nrepl.cmdline"
                             "--middleware"
                             "[cider.nrepl/cider-middleware]"]}})

(defn- print-clojure-cmd
  [sdeps-overrides args-str]
  (ansi/print-shifted (ansi/green "\nclojure -Sdeps"))
  (ansi/print-shifted (ansi/green (str (with-out-str
                                         (pprint/pprint sdeps-overrides))
                                       "\n" args-str))))

(defn- cp!
  [{:keys [verbose? package-aliases aliases repo-root cp-file]} sdeps-overrides]
  (let [all-aliases (str (string/join package-aliases)
                         (string/join aliases))
        cp-aliases-opt (when (seq all-aliases)
                         (str "-A" all-aliases))
        ;; to avoid "use -M instead of -A" deprecation warning
        sdeps-overrides' (update sdeps-overrides
                                 :aliases
                                 update-vals
                                 #(dissoc % :main-opts))
        sdeps (str "-Sdeps '" (pr-str sdeps-overrides') "'")
        clojure-cmd (string/join " " ["clojure" sdeps cp-aliases-opt "-Spath"])]
    (if (seq cp-file)
      (do
        (ansi/print-info "Saving classpath to a file:" cp-file)
        (when verbose?
          (print-clojure-cmd sdeps-overrides' (str cp-aliases-opt " -Spath")))
        (bp/shell {:dir repo-root :out cp-file} clojure-cmd))
      (bp/shell {:dir repo-root} clojure-cmd))))

(defn- make-cp-params
  [config {:keys [package-aliases] :as params}]
  (let [package-overrides (construct-sdeps-overrides! config package-aliases)
        local-deps-file (fs/file (:repo-root config) "deps.local.edn")
        deps-local-overrides (when (fs/exists? local-deps-file)
                               (util/read-deps-edn! local-deps-file))]
    {:package-overrides package-overrides
     :cp-params (assoc params
                       :package-aliases
                       (-> package-overrides :aliases (keys)))
     :sdeps-overrides (update package-overrides
                              :aliases
                              merge
                              deps-local-overrides)}))

(defn generate-classpath!
  [{:keys [repo-root glob] :as params}]
  (ansi/print-info "Generating kmono REPL classpath...")
  (assert (m/validate ?ReplParams params) (m/explain ?ReplParams params))
  (let [config (config/load-config repo-root glob)
        {:keys [cp-params sdeps-overrides]}
        (make-cp-params config params)]
    (cp! cp-params sdeps-overrides)))

(defn generate-deps!
  [{:keys [repo-root glob deps-file] :as params}]
  (ansi/print-info "Generating kmono deps.edn...")
  (assert (m/validate ?ReplParams params) (m/explain ?ReplParams params))
  (let [config (config/load-config repo-root glob)
        {:keys [sdeps-overrides]} (make-cp-params config params)
        project-deps-file (fs/file repo-root "deps.edn")
        project-deps (when (fs/exists? project-deps-file)
                       (util/read-deps-edn! project-deps-file))
        kmono-deps (binding [*print-namespace-maps* false]
                     (with-out-str
                       (pprint/pprint
                        (merge project-deps sdeps-overrides))))]
    (if deps-file
      (spit deps-file kmono-deps)
      (do
        (println "kmono deps.edn")
        (println kmono-deps)))))

(defn create-project-specs
  [cp-file]
  [{:project-path "deps.edn"
    :classpath-cmd ["cat" cp-file]}])

(defn configure-lsp!
  [{:keys [repo-root cp-file]}]
  (let [lsp-config-file (fs/file repo-root ".lsp/config.edn")
        lsp-config (when (fs/exists? lsp-config-file)
                     (try
                       (-> lsp-config-file
                           (slurp)
                           (edn/read-string))
                       (catch Throwable _ {})))
        project-specs (create-project-specs cp-file)
        with-project-specs
        (assoc lsp-config :project-specs project-specs)]
    (ansi/print-info "Setting project-specs for lsp config")
    (spit lsp-config-file
          (with-out-str
            (binding [*print-namespace-maps* false]
              (pprint/pprint with-project-specs))))))

(defn run-repl
  [{:keys [main-aliases aliases repo-root glob cp-file configure-lsp? verbose?]
    :as params}]
  (ansi/print-info "Starting kmono REPL...")
  (assert (m/validate ?ReplParams params) (m/explain ?ReplParams params))
  (binding [*print-namespace-maps* false]
    (let [config (config/load-config repo-root glob)
          {:keys [cp-params package-overrides sdeps-overrides]}
          (make-cp-params config params)
          main-opts (str "-M"
                         (string/join (-> package-overrides :aliases (keys)))
                         (string/join aliases)
                         (if (seq main-aliases)
                           (do (ansi/print-info
                                "Using custom main aliases" main-aliases)
                               (string/join main-aliases))
                           (do (ansi/print-info
                                "Using default main alias :kmono/repl")
                               ":kmono/nrepl")))
          with-nrepl-alias (if-not (seq main-aliases)
                             (update sdeps-overrides :aliases merge nrepl-alias)
                             sdeps-overrides)
          sdeps (str "-Sdeps '" (pr-str with-nrepl-alias) "'")
          clojure-cmd (string/join " " ["clojure" sdeps main-opts])]
      (when cp-file
        (cp! cp-params sdeps-overrides))
      (when configure-lsp?
        (configure-lsp! params))
      (ansi/print-info "Running clojure...")
      (when verbose?
        (print-clojure-cmd sdeps-overrides main-opts))
      (bp/shell clojure-cmd))))

