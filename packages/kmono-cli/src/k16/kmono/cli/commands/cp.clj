(ns k16.kmono.cli.commands.cp
  (:require
   [k16.kmono.cli.common.config :as common.config]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.cp :as kmono.cp]
   [k16.kmono.log :as log]))

(set! *warn-on-reflection* true)

(defn- cp-command [props]
  (let [project-root (core.fs/find-project-root (:dir props))
        workspace-config (-> (core.config/resolve-workspace-config project-root)
                             (common.config/merge-workspace-config props))
        packages (core.packages/resolve-packages project-root workspace-config)]

    (when (:verbose props)
      (log/debug "Running clojure command")
      (log/log-raw (kmono.cp/generate-classpath-command project-root workspace-config packages))
      (log/log-raw ""))

    (log/log-raw
     (kmono.cp/resolve-classpath project-root workspace-config packages))))

(def command
  {:command "cp"
   :description "Produce a classpath string from a clojure project"
   :opts [opts/packages-opt
          opts/main-aliases-opt
          opts/aliases-opt
          opts/package-aliases-opt
          opts/verbose-opt]
   :runs cp-command})
