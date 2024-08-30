(ns k16.kmono.cli.commands.run
  (:require
   [clojure.string :as str]
   [k16.kmono.cli.common.log :as common.log]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.deps :as core.deps]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.exec :as kmono.exec]
   [k16.kmono.log :as log]))

(set! *warn-on-reflection* true)

(defn- run-command [props]
  (let [project-root (core.fs/find-project-root (:dir props))
        workspace-config (core.config/resolve-workspace-config project-root)
        packages (core.packages/resolve-packages project-root workspace-config)

        globs (or (:M props)
                  (:T props)
                  (:X props))

        packages (core.graph/filter-by
                  (fn [pkg]
                    (-> (core.deps/filter-package-aliases
                         (core.deps/generate-package-aliases
                          project-root pkg)
                         globs)
                        seq))

                  packages)

        results
        (kmono.exec/run-external-cmds
         {:packages packages
          :ordered (:ordered props)
          :command (fn [pkg]
                     (let [aliases (core.deps/filter-package-aliases
                                    (core.deps/generate-package-aliases
                                     project-root pkg)
                                    globs)
                           names (->> (keys aliases)
                                      (map (fn [alias]
                                             (name alias)))
                                      (str/join ":"))

                           flag (cond
                                  (:M props) "-M"
                                  (:T props) "-T"
                                  (:X props) "-X")]

                       (concat ["clojure" (str flag ":" names)] (:_arguments props))))
          :concurrency (:concurrency props)
          :on-event common.log/handle-event})

        failed? (some
                 (fn [{:keys [success]}]
                   (not success))
                 results)]

    (when failed?
      (log/error "Command failed in one or more packages")
      (System/exit 1))))

(def aliases-opt
  {:as "List of aliases from packages"
   :multiple true
   :type :keyword})

(def command
  {:command "run"
   :description "Run an alias in project packages"
   :opts [opts/packages-opt
          opts/verbose-opt
          opts/order-opt

          {:as "Maximum number of commands to run in parallel. Defaults to number of cores"
           :option "concurrency"
           :short "c"
           :type :int}

          (assoc aliases-opt :option "M" :short "M")
          (assoc aliases-opt :option "T" :short "T")
          (assoc aliases-opt :option "X" :short "X")]

   :runs run-command})
