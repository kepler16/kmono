(ns k16.kmono.main
  (:require
   [clojure.tools.cli :as tools.cli]
   [k16.kmono.api :as api])
  (:gen-class))

(def run-cli-spec
  [["-h" "--help"
    "Show this help"
    :id :show-help?]
   ["-x" "--exec CMD"
    "Command to exec [build, release, <custom cmd>]"
    :default :build
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

(defn- parse-aliases
  [aliases-arg]
  (->> aliases-arg (re-seq #"[/a-zA-Z0-9]+") (mapv keyword)))

(defn- cp-option
  [default-value description]
  ["-f" "--cp-file FILENAME"
   description
   :default default-value])

(def repl-cli-spec
  [["-h" "--help"
    "Show this help"
    :id :show-help?]
   ["-A" "--aliases :alias1:alias2:namespace/alias3"
    "List of aliases from root deps.edn"
    :parse-fn parse-aliases]
   ["-P" "--package-aliases :package/alias1:package/alias2"
    "List of aliases from packages"
    :parse-fn parse-aliases]
   ["-r" "--repo-root PATH"
    "Repository root (default: '.')"
    :default "."]
   ["-g" "--glob GLOB"
    "A glob describing where to search for packages, (default: 'packages/*')"
    :default "packages/*"]
   (cp-option nil "Classpath file name (default: do nothing)")])

(def cp-cli-spec
  (conj (butlast repl-cli-spec)
        (cp-option nil "Classpath file name (default: print to output)")))

(defn print-help
  [title summary]
  (println title)
  (println summary)
  (println))

(def run-title "=== run - execute command for monorepo ===")
(def repl-title "=== repl - start a REPL for monorepo ===")
(def cp-title "=== cp - save/print a classpath ===")

(defn make-handler
  [cli-spec help-title run-fn]
  (fn [opts]
    (let [{:keys [options summary errors]}
          (tools.cli/parse-opts opts cli-spec)]
      (cond
        errors (do
                 (print-help help-title summary)
                 (println errors))

        (:show-help? options)
        (print-help help-title summary)

        :else (run-fn options)))))

(def modes
  {"run" (make-handler run-cli-spec run-title api/run)
   "repl" (make-handler repl-cli-spec repl-title api/repl)
   "cp" (make-handler cp-cli-spec cp-title api/generate-classpath!)
   "help" (fn [_]
            (let [run-summary (-> (tools.cli/parse-opts [] run-cli-spec)
                                  :summary)
                  repl-summary (-> (tools.cli/parse-opts [] repl-cli-spec)
                                   :summary)
                  cp-summary (-> (tools.cli/parse-opts [] cp-cli-spec)
                                 :summary)]
              (println "kmono <mode> opts...\n")
              (println "Modes:")
              (print-help run-title run-summary)
              (print-help repl-title repl-summary)
              (print-help cp-title cp-summary)))})

(defn -main [& args]
  (let [mode (first args)
        opts (rest args)
        mode-handler (or (get modes mode)
                         (get modes "help"))]
    (mode-handler opts)
    (System/exit 0)))

(comment
  (-main "run" "-x" "build")
  (-main "repl" "-A" ":foo:bar" "-P" "kmono/test")
  nil)
