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

(defn run-clojure [{:keys [A M T X] :as props} args]
  (let [aliases (or A M T X)
        props (assoc props :aliases aliases)
        {:keys [root config packages]} (common.context/load-context props)

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
        command (into command args)

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

    (when-not (= 0 (:exit @proc))
      (System/exit 1))))

(def command
  {:command "clojure"
   :desc "Run an augmented clojure command"

   :options {:package-aliases opts/package-aliases-opt

             :A {:desc "Aliases"
                 :parse-fn opts/parse-bool-or-aliases}
             :M {:desc "Main aliases"
                 :parse-fn opts/parse-bool-or-aliases}
             :T {:desc "Tool aliases"
                 :parse-fn opts/parse-bool-or-aliases}
             :X {:desc "Exec aliases"
                 :parse-fn opts/parse-bool-or-aliases}}

   :run-fn run-clojure})
