(ns k16.kmono.cli.commands.cp
  (:require
   [k16.kmono.cli.commands.clojure :as commands.clojure]
   [k16.kmono.cli.common.opts :as opts]))

(set! *warn-on-reflection* true)

(defn- cp-command [props]
  (commands.clojure/run-clojure
   (merge props {:A true
                 :_arguments ["-Spath"]})))

(def command
  {:command "cp"
   :description "Produce a classpath string from a clojure project"
   :opts [opts/packages-opt
          opts/aliases-opt
          opts/package-aliases-opt
          opts/verbose-opt]
   :runs cp-command})
