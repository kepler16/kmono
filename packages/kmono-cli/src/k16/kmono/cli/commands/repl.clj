(ns k16.kmono.cli.commands.repl
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [k16.kmono.cli.commands.clojure :as commands.clojure]
   [k16.kmono.cli.common.context :as common.context]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.log :as log]))

(set! *warn-on-reflection* true)

(defn- render-aliases [aliases]
  (str "@|black,bold [|@"

       (->> aliases
            (mapv (fn [alias]
                    (str "@|yellow " alias "|@")))
            (str/join "@|black,bold , |@"))

       "@|black,bold ]|@"))

(defn- repl-command [opts _]
  (let [{:keys [config]} (common.context/load-context opts)
        repl-aliases (:repl-aliases config)]
    (binding [log/*log-out* System/err]
      (log/info (str "Aliases: " (render-aliases (:aliases config))))
      (log/info (str "Repl Aliases: " (render-aliases repl-aliases)))
      (log/info (str "Package Aliases: " (render-aliases (:package-aliases config)))))

    (commands.clojure/run-clojure (assoc opts
                                         :M (into (:aliases opts repl-aliases)))
                                  [])))

(def command
  {:command "repl"
   :summary "Start a clojure repl"
   :desc (io/resource "k16/kmono/docs/repl.md")
   :options {:aliases opts/aliases-opt
             :package-aliases opts/package-aliases-opt}
   :run-fn repl-command})
