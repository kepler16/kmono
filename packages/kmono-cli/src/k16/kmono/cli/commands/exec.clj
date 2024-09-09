(ns k16.kmono.cli.commands.exec
  (:require
   [k16.kmono.cli.common.context :as common.context]
   [k16.kmono.cli.common.log :as common.log]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.exec :as kmono.exec]
   [k16.kmono.log :as log]
   [k16.kmono.version :as kmono.version]))

(set! *warn-on-reflection* true)

(defn- run-command [opts _]
  (let [{:keys [root packages]} (common.context/load-context opts)
        packages (cond-> packages
                   (:skip-unchanged opts) (->>
                                           (kmono.version/resolve-package-versions root)
                                           (kmono.version/resolve-package-changes root)
                                           (core.graph/filter-by kmono.version/package-changed?
                                                                 {:include-dependents true})))

        results
        (kmono.exec/run-external-cmds
         {:packages packages
          :ordered (:ordered opts)
          :command (:_arguments opts)
          :concurrency (:concurrency opts)
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
   :desc "Run a given command in workspace packages"
   :options {:run-in-order opts/run-in-order-opt
             :skip-unchanged opts/skip-unchanged-opt
             :concurrency {:desc "Maximum number of commands to run in parallel. Defaults to number of cores"
                           :alias :c
                           :coerce :int}}
   :run-fn run-command})
