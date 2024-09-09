(ns k16.kmono.cli.commands.clojure
  (:require
   [babashka.process :as proc]
   [clojure.string :as str]
   [k16.kmono.cli.common.context :as common.context]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.core.deps :as core.deps]
   [k16.kmono.cp :as kmono.cp]
   [k16.kmono.log :as log]))

(set! *warn-on-reflection* true)

(defn run-clojure [{:keys [A M T X _arguments] :as props}]
  (let [{:keys [root config packages]} (common.context/load-context props)

        mode (cond
               A "A"
               M "M"
               T "T"
               X "X")

        sdeps-aliases (core.deps/generate-sdeps-aliases root packages)
        sdeps {:aliases sdeps-aliases}

        aliases (kmono.cp/collect-aliases config packages)

        arg (when mode
              (str "-" mode (kmono.cp/serialize-aliases aliases)))
        sdeps (str "'" (str/trim (prn-str sdeps)) "'")

        command ["clojure" "-Sdeps" sdeps arg]
        command (concat command _arguments)

        command (str/join " " command)

        _ (when (:verbose props)
            (binding [log/*log-out* System/err]
              (log/debug "Running clojure command:")
              (log/debug command)))

        opts {:dir root :inherit true}
        proc (proc/process opts command)]

    (doto (Runtime/getRuntime)
      (.addShutdownHook
       (Thread.
        (fn []
          (try
            (proc/destroy proc)
            @proc
            (catch Throwable _ nil))))))

    @proc))

(defn- aliases-opt [name]
  {:as (str "The clojure command mode -" name)
   :option name
   :short name
   :type :with-flag})

(def command
  {:command "clojure"
   :description "Run an augmented clojure command"
   :opts [opts/packages-opt
          opts/verbose-opt

          (dissoc opts/aliases-opt :short)
          opts/package-aliases-opt

          (aliases-opt "A")
          (aliases-opt "M")
          (aliases-opt "T")
          (aliases-opt "X")]

   :runs run-clojure})
