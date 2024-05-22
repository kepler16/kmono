(ns k16.kmono.main
  (:require
   [clojure.tools.cli :as tools.cli]
   [k16.kmono.api :as api])
  (:gen-class))

(defmacro package-version []
  `(let [v# ~(or (System/getenv "KMONO_PKG_VERSION") "unset")]
     v#))

(defn get-version [] (package-version))

(def run-cli-spec
  [["-h" "--help"
    "Show this help"
    :id :show-help?]
   ["-x" "--exec CMD"
    "Command to exec [build, release, <custom cmd>]"
    :parse-fn (fn [cmd]
                (if (or (= cmd "release") (= cmd "build"))
                  (keyword cmd)
                  cmd))]
   ["-r" "--repo-root PATH"
    "Repository root (default: '.')"
    :default "."]
   ["-g" "--glob GLOB"
    "A glob describing where to search for packages, (default: 'packages/*')"
    :default "packages/*"]
   ["-s" "--snapshot FLAG"
    "A snapshot flag (default: false)"
    :id :snapshot?
    :default false
    :parse-fn #(Boolean/parseBoolean %)]
   ["-t" "--create-tags FLAG"
    "Should create tags flag (default: false)"
    :id :create-tags?
    :default false
    :parse-fn #(Boolean/parseBoolean %)]
   ["-i" "--include-unchanged FLAG"
    "Should include unchanged packages flag (default: true)"
    :id :include-unchanged?
    :default true
    :parse-fn #(Boolean/parseBoolean %)]])

(defn parse-aliases
  [aliases-arg]
  (->> aliases-arg (re-seq #"[^:]+") (mapv keyword)))

(defn- cp-option
  [default-value description]
  ["-f" "--cp-file FILENAME"
   description
   :default default-value])

(defn- deps-option
  [default-value description]
  ["-d" "--deps-file FILENAME"
   description
   :default default-value])

(def repl-cli-spec
  [["-h" "--help"
    "Show this help"
    :id :show-help?]
   ["-v" "--verbose"
    "Print additional info"
    :id :verbose?]
   ["-A" "--aliases :alias1:alias2:namespace/alias3"
    "List of aliases from root deps.edn"
    :parse-fn parse-aliases]
   ["-M" "--main-aliases :alias1:alias2"
    "List of main aliases from root deps.edn"
    :parse-fn parse-aliases]
   ["-P" "--package-aliases :package/alias1:package/alias2:*/alias3"
    "List of aliases from packages (asterisk means all packages)"
    :parse-fn parse-aliases]
   ["-r" "--repo-root PATH"
    "Repository root (default: '.')"
    :default "."]
   ["-g" "--glob GLOB"
    "A glob describing where to search for packages, (default: 'packages/*')"
    :default nil]
   ["-l" "--configure-lsp"
    (str "Set repl specific `:project-specs` in `.lsp/config.edn`, "
         "requires cp-file to be set (default: true)")
    :id :configure-lsp?]
   (cp-option nil "Save classpath file for LSP use (default: do nothing)")])

(def cp-cli-spec
  (conj (drop-last 2 repl-cli-spec)
        (cp-option nil "Classpath file name (default: print to output)")))

(def deps-cli-spec
  (conj (drop-last 2 repl-cli-spec)
        (deps-option nil "Creates a deps file")))

(defn print-help
  [title summary]
  (println title)
  (println summary)
  (println))

(def run-title
  (str "=== run - execute command for monorepo ===\n"
       " run <exec> [opts...]\n"
       " run -x <exec> [opts...]"))
(def repl-title "=== repl - start a REPL for monorepo ===")
(def cp-title "=== cp - save/print a classpath ===")
(def deps-title "=== deps - save/print a combined deps file ===")

(defn make-handler
  [cli-spec help-title run-fn]
  (fn [opts]
    (let [{:keys [options summary errors arguments]}
          (tools.cli/parse-opts opts cli-spec)]
      (cond
        errors (do
                 (print-help help-title summary)
                 (println errors))

        (:show-help? options)
        (print-help help-title summary)

        :else (run-fn options arguments)))))

(def modes
  {"--version" (fn [_]
                 (println (str "kmono v" (get-version))))
   "run" (make-handler run-cli-spec run-title api/run)
   "repl" (make-handler repl-cli-spec repl-title api/repl)
   "cp" (make-handler cp-cli-spec cp-title api/generate-classpath!)
   "deps" (make-handler deps-cli-spec deps-title api/generate-deps!)
   "help" (fn [_]
            (let [run-summary (-> (tools.cli/parse-opts [] run-cli-spec)
                                  :summary)
                  repl-summary (-> (tools.cli/parse-opts [] repl-cli-spec)
                                   :summary)
                  cp-summary (-> (tools.cli/parse-opts [] cp-cli-spec)
                                 :summary)
                  deps-summary (-> (tools.cli/parse-opts [] deps-cli-spec)
                                   :summary)]
              (println (str "kmono v" (get-version) "\n"))
              (println "USAGE: kmono <mode> opts...\n")
              (println "MODES:")
              (print-help run-title run-summary)
              (print-help repl-title repl-summary)
              (print-help cp-title cp-summary)
              (print-help deps-title deps-summary)))})

(defn -main [& args]
  (let [mode (first args)
        mode-handler (or (get modes mode)
                         (get modes "help"))]
    (mode-handler args)
    (System/exit 0)))

