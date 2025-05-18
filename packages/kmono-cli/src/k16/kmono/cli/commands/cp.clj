(ns k16.kmono.cli.commands.cp
  (:require
   [k16.kmono.cli.commands.clojure :as commands.clojure]
   [k16.kmono.cli.common.opts :as opts]))

(set! *warn-on-reflection* true)

(defn- cp-command [opts _]
  (commands.clojure/run-clojure (merge opts {:A []})
                                ["-Spath"]))

(def command
  {:command "cp"
   :desc "Produce a classpath string from a clojure project"
   :options {:aliases opts/aliases-opt
             :package-aliases opts/package-aliases-opt}
   :run-fn cp-command})
