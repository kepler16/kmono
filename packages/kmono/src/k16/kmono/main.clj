(ns k16.kmono.main
  (:require
   [clojure.pprint :as pprint]
   [clojure.tools.cli :as tools.cli]
   [k16.kmono.repl.deps :as repl.deps]
   [k16.kmono.api :as api])
  (:gen-class))

(def run-cli-opts
  [["-x" "--exec CMD"
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
    :parse-fn #(Boolean/parseBoolean %)]
   ["-h" "--help"
    "Show this help"
    :id :show-help?]])

(defn- parse-aliases
  [aliases-arg]
  (->> aliases-arg (re-seq #"[/a-zA-Z0-9]+") (mapv keyword)))

(def repl-cli-opts
  [["-A" "--aliases :alias1:alias2:namespace/alias3"
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
   ["-h" "--help"
    "Show this help"
    :id :show-help?]])

(defn print-help
  [title summary]
  (println title)
  (println summary)
  (println))

(def run-title "=== run - execute command for monorepo ===")
(def repl-title "=== repl - start a REPL for monorepo ===")

(defn -main [& args]
  (let [mode (first args)
        opts (rest args)
        repl? (= mode "repl")
        run? (= mode "run")
        help? (and (not repl?) (not run?))]
    (cond
      run?
      (let [{:keys [options summary errors]}
            (tools.cli/parse-opts opts run-cli-opts)]
        (cond
          errors (do
                   (print-help run-title summary)
                   (println errors))

          (:show-help? options)
          (print-help run-title summary)

          :else (api/run options)))
      repl?
      (let [{:keys [options summary errors]}
            (tools.cli/parse-opts opts repl-cli-opts)]
        (cond
          errors (do
                   (print-help repl-title summary)
                   (println errors))

          (:show-help? options) (print-help repl-title summary)

          :else (api/repl options)))

      help?
      (let [run-summary (-> (tools.cli/parse-opts [] run-cli-opts)
                            :summary)
            repl-summary (-> (tools.cli/parse-opts [] repl-cli-opts)
                             :summary)]
        (println "kmono <mode> opts...\n")
        (println "Modes:")
        (print-help run-title run-summary)
        (print-help repl-title repl-summary)))))

(comment
  (-main "run" "-x" "build")
  (-main "repl" "-A" ":foo:bar" "-P" "kmono/test")
  nil)
