(ns k16.kmono.cli.commands.exec
  (:require
   [clojure.java.io :as io]
   [k16.kmono.cli.common.context :as common.context]
   [k16.kmono.cli.common.log :as common.log]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.exec :as kmono.exec]
   [k16.kmono.log :as log]
   [k16.kmono.version :as kmono.version]))

(set! *warn-on-reflection* true)

(defn- run-command
  [{:keys [filter skip-unchanged changed changed-since
           include-dependents run-in-order concurrency]
    :as opts}
   args]
  (let [{:keys [root packages config]} (common.context/load-context opts)
        ignore-changes (:ignore-changes config)
        change-opts (when ignore-changes
                      {:ignore-changes ignore-changes})

        packages
        (cond-> packages
          (or changed skip-unchanged)
          (->> (kmono.version/resolve-package-versions root)
               (kmono.version/resolve-package-changes root change-opts)
               (core.graph/filter-by kmono.version/package-changed?
                                     {:include-dependents include-dependents}))

          changed-since
          (->> (kmono.version/resolve-package-changes-since root changed-since change-opts)
               (core.graph/filter-by kmono.version/package-changed?
                                     {:include-dependents include-dependents}))

          filter
          (->> (core.graph/filter-by (core.packages/name-matches? filter))))

        results
        (kmono.exec/run-external-cmds
         {:packages packages
          :run-in-order run-in-order
          :command args
          :concurrency concurrency
          :on-event common.log/handle-event})

        failed? (some
                 (fn [{:keys [success]}]
                   (not success))
                 results)]

    (when failed?
      (log/error "Command failed in one or more packages")
      (System/exit 1))))

(def include-dependents
  {:desc "Include the dependents of filtered packages even if they don't match any of the filters"
   :coerce :boolean
   :default true})

(def command
  {:command "exec"
   :summary "Run a given command in workspace packages"
   :desc (delay (io/resource "k16/kmono/docs/exec.md"))
   :options {:run-in-order opts/run-in-order-opt
             :skip-unchanged opts/skip-unchanged-opt
             :changed opts/changed-opt
             :changed-since opts/changed-since-opt
             :ignore-changes opts/ignore-changes-opt
             :filter opts/package-filter-opt
             :include-dependents include-dependents
             :concurrency opts/concurrency-opt}
   :run-fn run-command})
