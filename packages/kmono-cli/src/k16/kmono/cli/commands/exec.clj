(ns k16.kmono.cli.commands.exec
  (:require
   [k16.kmono.cli.common.log :as common.log]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.exec :as kmono.exec]
   [k16.kmono.log :as log]
   [k16.kmono.version :as kmono.version]))

(set! *warn-on-reflection* true)

(defn- run-command [props]
  (let [project-root (core.fs/find-project-root (:dir props))
        workspace-config (core.config/resolve-workspace-config project-root)
        packages (cond-> (core.packages/resolve-packages project-root workspace-config)
                   (:skip-unchanged props) (->>
                                            (kmono.version/resolve-package-versions project-root)
                                            (kmono.version/resolve-package-changes project-root)
                                            (core.graph/filter-by kmono.version/package-changed?)))

        results
        (kmono.exec/run-external-cmds
         {:packages packages
          :ordered (:ordered props)
          :command (:_arguments props)
          :concurrency (:concurrency props)
          :on-event common.log/handle-event})

        failed? (some
                 (fn [{:keys [success]}]
                   (not success))
                 results)]

    (when failed?
      (log/error "Command failed in one or more packages")
      (System/exit 1))))

(def command
  {:command "exec"
   :description "Run a command in each package"
   :opts [opts/packages-opt
          opts/verbose-opt
          opts/order-opt
          opts/skip-unchanged-opt

          {:as "Maximum number of commands to run in parallel. Defaults to number of cores"
           :option "concurrency"
           :short "c"
           :type :int}]
   :runs run-command})
