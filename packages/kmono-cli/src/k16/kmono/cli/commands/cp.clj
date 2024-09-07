(ns k16.kmono.cli.commands.cp
  (:require
   [k16.kmono.cli.common.context :as common.context]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.cp :as kmono.cp]
   [k16.kmono.log :as log]))

(set! *warn-on-reflection* true)

(defn- cp-command [props]
  (let [{:keys [root config packages]} (common.context/load-context props)]

    (when (:verbose props)
      (log/debug "Running clojure command")
      (log/log-raw (kmono.cp/generate-classpath-command root config packages))
      (log/log-raw ""))

    (log/log-raw
     (kmono.cp/resolve-classpath root config packages))))

(def command
  {:command "cp"
   :description "Produce a classpath string from a clojure project"
   :opts [opts/packages-opt
          opts/main-aliases-opt
          opts/aliases-opt
          opts/package-aliases-opt
          opts/verbose-opt]
   :runs cp-command})
