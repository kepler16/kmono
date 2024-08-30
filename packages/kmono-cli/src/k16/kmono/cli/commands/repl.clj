(ns k16.kmono.cli.commands.repl
  (:require
   [babashka.process :as proc]
   [clojure.string :as str]
   [k16.kmono.cli.common.config :as common.config]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.cp :as kmono.cp]
   [k16.kmono.log :as log]))

(set! *warn-on-reflection* true)

(defn- render-aliases [aliases]
  (str "@|black,bold [|@"

       (->> aliases
            (map
             (fn [alias]
               (str "@|yellow " alias "|@")))
            (str/join "@|black,bold , |@"))

       "@|black,bold ]|@"))

(defn- repl-command [props]
  (let [project-root (core.fs/find-project-root (:dir props))
        workspace-config (-> (core.config/resolve-workspace-config project-root)
                             (common.config/merge-workspace-config props))
        packages (core.packages/resolve-packages project-root workspace-config)

        {:keys [sdeps aliases]}
        (kmono.cp/generate-aliases
         project-root workspace-config packages)

        aliases (concat aliases (:main-aliases workspace-config))

        command
        ["clojure"
         "-Sdeps" (str "'" (str/trim (prn-str sdeps)) "'")
         (str "-M" (kmono.cp/serialize-aliases aliases))]

        _ (log/info (str "Main aliases: " (render-aliases (:main-aliases workspace-config))))
        _ (log/info (str "Aliases: " (render-aliases (:aliases workspace-config))))
        _ (log/info (str "Package Aliases: " (render-aliases (:package-aliases workspace-config))))

        command (str/join " " command)

        _ (when (:verbose props)
            (log/debug "Running repl command")
            (log/log-raw command)
            (log/log-raw ""))

        proc (proc/process
              {:dir project-root
               :inherit true}
              command)]

    (doto (Runtime/getRuntime)
      (.addShutdownHook
       (Thread.
        (fn []
          (try
            (proc/destroy proc)
            (proc/check proc)
            (catch Throwable _ nil))))))

    (proc/check proc)))

(def command
  {:command "repl"
   :description "Start a clojure repl"
   :opts [opts/packages-opt
          opts/main-aliases-opt
          opts/package-aliases-opt
          opts/aliases-opt
          opts/verbose-opt]
   :runs repl-command})
