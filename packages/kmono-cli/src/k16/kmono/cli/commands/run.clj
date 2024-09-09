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

(defn- run-command [{:keys [M T X skip-unchanged] :as props} args]
  (let [{:keys [root packages]} (common.context/load-context props)
        packages (cond-> packages
                   skip-unchanged (->>
                                   (kmono.version/resolve-package-versions root)
                                   (kmono.version/resolve-package-changes root)
                                   (core.graph/filter-by kmono.version/package-changed?
                                                         {:include-dependents true})))

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
                                       args)]

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

(def command
  {:command "run"
   :desc "Run aliases in workspace packages"
   :options {:run-in-order opts/run-in-order-opt
             :skip-unchanged opts/skip-unchanged-opt

             :concurrency {:desc "Maximum number of commands to run in parallel. Defaults to number of cores"
                           :alias :c
                           :coerce :int}

             :M {:desc "Run clojure -M <alias globs>"
                 :parse-fn opts/parse-bool-or-aliases}
             :T {:desc "Run clojure -T <alias globs>"
                 :parse-fn opts/parse-bool-or-aliases}
             :X {:desc "Run clojure -X <alias globs>"
                 :parse-fn opts/parse-bool-or-aliases}}

   :run-fn run-command})
