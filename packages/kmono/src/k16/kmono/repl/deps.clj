(ns k16.kmono.repl.deps
  (:require
   [babashka.fs :as fs]
   [babashka.process :as bp]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [k16.kmono.adapters.clojure-deps :as clj.deps]
   [k16.kmono.ansi :as ansi]
   [k16.kmono.config :as config]
   [malli.core :as m]))

(def path-aliases #{:paths :extra-paths :replace-paths :local/root})

(defn relativize-path
  [root package-dir path]
  (assert (and root package-dir path)
          {:root root
           :package-dir package-dir
           :path path})
  (fs/relativize root (fs/path package-dir path)))

(defn relativize-paths
  "Relativise paths from package alias opts to work in repo root context"
  [repo-root package-dir aliases-map]
  (letfn [(relativize [path]
            (str (relativize-path repo-root package-dir path)))]
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
     aliases-map)))

(defn- namespaced?
  [kw]
  (and (keyword? kw) (not (nil? (namespace kw)))))

(defn package-dependency-alias
  [package-name package-dir]
  (let [full-name (str "packages/" package-name)]
    {(keyword full-name)
     {:extra-deps
      {(symbol full-name) {:local/root package-dir}}}}))

(defn get-package-alias-map!
  "Returns alias map from a given package/alias pair.
  Accepts kmono config (`config.schema/?Config`) and a package/alias pair as
  a namespaced keyord where namespace is a package name and a name is an alias,
  e.g. `:my-package/test`.
  Returns an extracted alias map from package's deps.edn"
  [{:keys [package-dirs repo-root]} package-alias]
  (let [pkg-alias (keyword package-alias)
        package-name (or (namespace pkg-alias) (name pkg-alias))
        package-dir (get package-dirs package-name)
        base-alias (package-dependency-alias package-name package-dir)]
    (assert package-name (str "Could not get package from package-alias ["
                              package-alias "]"))
    (if (namespaced? pkg-alias)
      (let [alias-key (-> pkg-alias (name) (keyword))
            _ (assert alias-key (str "Could not get alias from package-alias ["
                                     package-alias "]"))
            deps-edn (clj.deps/read-deps-edn! package-dir)
            alias-name (keyword (str "packages/" package-name "." (name alias-key)))]
        (merge
         base-alias
         {alias-name (relativize-paths
                      repo-root
                      package-dir
                      (get-in deps-edn [:aliases alias-key]))}))
      base-alias)))

(defn construct-sdeps-overrides!
  "Accepts kmono config and a collection pairs of package/alias and
  returns a string for -Sdeps argument"
  [config package-alias-pairs]
  (let [package-dirs (->> config
                          :packages
                          (map (fn [pkg]
                                 [(fs/file-name (fs/path (:dir pkg)))
                                  (:dir pkg)]))
                          (into {}))
        aliases (into {}
                      (comp
                       (map (partial get-package-alias-map!
                                     {:package-dirs package-dirs
                                      :repo-root (:repo-root config)})))
                      package-alias-pairs)]
    {:aliases aliases}))

(def ?ReplParams
  [:map
   [:aliases {:optional true}
    [:vector :keyword]]
   [:packages-aliases {:optional true}
    [:vector :keyword]]])

(def nrepl-alias
  {:kmono-nrepl {:extra-deps {'cider/cider-nrepl {:mvn/version "0.44.0"}}
                 :main-opts ["-m"
                             "nrepl.cmdline"
                             "--middleware"
                             "[cider.nrepl/cider-middleware]"]}})

(defn- cp!
  [{:keys [package-aliases aliases cp-file]} sdeps]
  (let [cp-opts (if (or (seq package-aliases) (seq aliases))
                  (str "-A"
                       (string/join aliases)
                       (string/join package-aliases))
                  "-A")
        clojure-cmd (string/join " " ["clojure" sdeps cp-opts "-Spath"])]
    (if (seq cp-file)
      (do
        (ansi/print-info "Saving classpath to a file:" cp-file)
        (println (ansi/green clojure-cmd))
        (bp/shell {:out cp-file} clojure-cmd))
      (bp/shell clojure-cmd))))

(defn generate-classpath!
  [{:keys [package-aliases repo-root glob] :as params}]
  (ansi/print-info "Generating kmono REPL classpath...")
  (assert (m/validate ?ReplParams params) (m/explain ?ReplParams params))
  (let [config (config/load-config repo-root glob)
        package-overrides (construct-sdeps-overrides!
                           config package-aliases)
        sdeps-overrides (update package-overrides :aliases merge nrepl-alias)
        sdeps (str "-Sdeps '" (pr-str sdeps-overrides) "'")]
    (cp! (assoc params :package-aliases (-> package-overrides :aliases (keys)))
         sdeps)))

(defn run-repl
  [{:keys [aliases package-aliases repo-root glob cp-file] :as params}]
  (ansi/print-info "Starting kmono REPL...")
  (assert (m/validate ?ReplParams params) (m/explain ?ReplParams params))
  (binding [*print-namespace-maps* false]
    (let [config (config/load-config repo-root glob)
          package-overrides (construct-sdeps-overrides!
                             config package-aliases)
          sdeps-overrides (update package-overrides :aliases merge nrepl-alias)
          sdeps (str "-Sdeps '" (pr-str sdeps-overrides) "'")
          main-opts (if (or (seq package-aliases) (seq aliases))
                      (str "-M"
                           (string/join aliases)
                           (string/join (-> package-overrides :aliases (keys)))
                           ":kmono-nrepl")
                      "-M")
          clojure-cmd (string/join " " ["clojure" sdeps main-opts])]
      (when cp-file
        (cp! params sdeps))
      (ansi/print-info "Running clojure...")
      (println (ansi/green clojure-cmd))
      (bp/shell clojure-cmd))))

