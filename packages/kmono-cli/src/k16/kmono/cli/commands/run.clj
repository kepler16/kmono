(ns k16.kmono.cli.commands.run
  (:require
   [clojure.string :as str]
   [k16.kmono.cli.common.context :as common.context]
   [k16.kmono.cli.common.log :as common.log]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.core.deps :as core.deps]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.exec :as kmono.exec]
   [k16.kmono.log :as log]
   [k16.kmono.version :as kmono.version]))

(set! *warn-on-reflection* true)

(defn- run-command [{:keys [M T X skip-unchanged] :as props}]
  (let [{:keys [root packages]} (common.context/load-context props)
        packages (cond-> packages
                   skip-unchanged (->>
                                   (kmono.version/resolve-package-versions root)
                                   (kmono.version/resolve-package-changes root)
                                   (core.graph/filter-by kmono.version/package-changed?)))

        globs (or M T X)
        flag (cond
               M "-M"
               T "-T"
               X "-X")

        package-aliases (core.deps/filter-package-aliases globs packages)
        packages (core.graph/filter-by
                  (fn [pkg]
                    (get package-aliases (:fqn pkg)))
                  packages)

        results
        (kmono.exec/run-external-cmds
         {:packages packages
          :ordered (:ordered props)
          :command (fn [pkg]
                     (let [aliases (get package-aliases (:fqn pkg))

                           names (->> aliases
                                      (map (fn [alias]
                                             (name alias)))
                                      (str/join ":"))

                           cmd (concat ["clojure" (str flag ":" names)]
                                       (:_arguments props))]

                       (when (:verbose props)
                         (binding [log/*log-out* System/err]
                           (log/debug "Running command:")
                           (log/debug (str/join " " cmd))))

                       cmd))

          :concurrency (:concurrency props)
          :on-event common.log/handle-event})

        failed? (some
                 (fn [{:keys [success]}]
                   (not success))
                 results)]

    (when failed?
      (log/error "Command failed in one or more packages")
      (System/exit 1))))

(defn- aliases-opt [name]
  {:as (str "The clojure command mode -" name)
   :option name
   :short name
   :multiple true
   :type :keyword})

(def command
  {:command "run"
   :description "Run an alias in project packages"
   :opts [opts/packages-opt
          opts/verbose-opt
          opts/order-opt
          opts/skip-unchanged-opt

          {:as "Maximum number of commands to run in parallel. Defaults to number of cores"
           :option "concurrency"
           :short "c"
           :type :int}

          (aliases-opt "M")
          (aliases-opt "T")
          (aliases-opt "X")]

   :runs run-command})
