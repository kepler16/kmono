(ns k16.kmono.cli.commands.run
  (:require
   [k16.kmono.cli.common.context :as common.context]
   [k16.kmono.cli.common.log :as common.log]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.cp :as kmono.cp]
   [k16.kmono.exec :as kmono.exec]
   [k16.kmono.log :as log]
   [k16.kmono.version :as kmono.version]))

(set! *warn-on-reflection* true)

(defn- run-command [{:keys [M T X skip-unchanged changed changed-since] :as opts} args]
  (let [{:keys [root packages]} (common.context/load-context opts)
        filter' (:filter opts)
        packages (cond-> packages
                   filter'
                   (->> (core.graph/filter-by (core.packages/name-matches? filter')))

                   (or changed skip-unchanged)
                   (->> (kmono.version/resolve-package-versions root)
                        (kmono.version/resolve-package-changes root)
                        (core.graph/filter-by kmono.version/package-changed?
                                              {:include-dependents true}))

                   changed-since
                   (->> (kmono.version/resolve-package-changes-since root changed-since)
                        (core.graph/filter-by kmono.version/package-changed?
                                              {:include-dependents true})))

        aliases (or M T X)
        flag (cond
               M "-M"
               T "-T"
               X "-X")

        packages (core.graph/filter-by
                  (fn [pkg]
                    (boolean (get-in pkg [:deps-edn :aliases (last aliases)])))
                  packages)

        results
        (kmono.exec/run-external-cmds
         {:packages packages
          :run-in-order (:run-in-order opts)
          :command (into ["clojure" (str flag (kmono.cp/serialize-aliases aliases))] args)

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
  {:command "run"
   :desc "Run aliases in workspace packages"
   :options {:run-in-order opts/run-in-order-opt
             :skip-unchanged opts/skip-unchanged-opt
             :changed opts/changed-opt
             :changed-since opts/changed-since-opt
             :concurrency opts/concurrency-opt
             :filter opts/package-filter-opt

             :M {:desc "Run clojure -M <aliases>"
                 :parse-fn opts/parse-bool-or-aliases}
             :T {:desc "Run clojure -T <aliases>"
                 :parse-fn opts/parse-bool-or-aliases}
             :X {:desc "Run clojure -X <aliases>"
                 :parse-fn opts/parse-bool-or-aliases}}

   :run-fn run-command})
