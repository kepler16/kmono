(ns k16.kmono.cli.commands.repl
  (:require
   [clojure.string :as str]
   [k16.kmono.cli.commands.clojure :as commands.clojure]
   [k16.kmono.cli.common.context :as common.context]
   [k16.kmono.cli.common.opts :as opts]
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
  (let [{:keys [config]} (common.context/load-context props)
        repl-aliases (:repl-aliases config)]
    (binding [log/*log-out* System/err]
      (log/info (str "Aliases: " (render-aliases (:aliases config))))
      (log/info (str "Package Aliases: " (render-aliases (:package-aliases config)))))

    (commands.clojure/run-clojure (assoc props
                                         :M true
                                         :aliases (concat (:aliases props repl-aliases))))))

(def command
  {:command "repl"
   :description "Start a clojure repl"
   :opts [opts/packages-opt
          opts/package-aliases-opt
          opts/aliases-opt
          opts/verbose-opt]
   :runs repl-command})
