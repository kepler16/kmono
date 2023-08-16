(ns k16.kbuild.exec
  (:require
   [babashka.process :as bp]
   [k16.kbuild.adapter :as adapter]
   [k16.kbuild.ansi :as ansi]
   [k16.kbuild.config-schema :as config.schema]
   [k16.kbuild.dry :as dry]
   [k16.kbuild.git :as git]
   [k16.kbuild.proc :as kbuild.proc]))

(def ?JobResult
  [:tuple :boolean kbuild.proc/?ProcsResult])

(defn- run-external-cmd
  [config changes pkg-name cmd-type]
  (let [change (get changes pkg-name)]
    (if (not @(:published? change))
      (let [pkg-map (:package-map config)
            pkg (get pkg-map pkg-name)
            deps-env (-> pkg :adapter (adapter/prepare-deps-env changes))
            version (get-in changes [pkg-name :version])
            ext-cmd (if (:dry-run? config)
                      (case cmd-type
                        :build-cmd dry/fake-build-cmd
                        :release-cmd dry/fake-release-cmd
                        dry/fake-cusrom-cmd)
                      (or (get pkg cmd-type)
                          (get config cmd-type)))
            _ (ansi/assert-err! ext-cmd (str "Command of type [" cmd-type "] could not be found"))
            _ (ansi/print-info "\t" (str pkg-name "@" version " => " ext-cmd))
            build-result (bp/process {:extra-env
                                      {"KBUILD_DEPS_ENV" deps-env
                                       "KBUILD_PKG_VERSION" version
                                       "KBUILD_PKG_NAME" pkg-name}
                                      :out :string
                                      :err :string
                                      :dir (:dir pkg)}
                                     ext-cmd)]
        [pkg-name build-result])
      [pkg-name {:skipped? true}])))

(defn build-package
  [config changes pkg-name]
  (run-external-cmd config changes pkg-name :build-cmd))

(defn release-package
  [config changes pkg-name]
  (run-external-cmd config changes pkg-name :release-cmd))

(defn package-custom-command
  [config changes pkg-name]
  (run-external-cmd config changes pkg-name :custom-cmd))

(defn get-milis
  []
  (.getTime (java.util.Date.)))

(defn run-external-cmds
  {:malli/schema [:=> [:cat config.schema/?Config git/?Changes] ?JobResult]}
  [config changes operation-fn terminate-on-fail?]
  (let [exec-order (:build-order config)
        global-start (get-milis)]
    (ansi/print-info (count exec-order) "parallel stages to run...")
    (loop [stages exec-order
           idx 1
           stage-results []]
      (if (seq stages)
        (let [stage (first stages)
              prefix (str "#" idx)
              start-time (get-milis)
              _ (println)
              _ (ansi/print-info prefix "stage started" (str "(" (count stage) " packages)"))
              op-procs (->> stage
                            (map (partial operation-fn config changes))
                            (remove nil?))
              [success? stage-result] (kbuild.proc/await-procs
                                       op-procs terminate-on-fail?)]
          (if success?
            (do (ansi/print-info prefix "stage finished in"
                                 (- (get-milis) start-time)
                                 "ms\n")
                (recur (rest stages) (inc idx) (conj stage-results stage-result)))
            (do (ansi/print-error prefix "stage failed")
                (ansi/print-error "terminating")
                (doseq [[_ proc] op-procs]
                  (bp/destroy-tree proc))
                [false (conj stage-results stage-result)])))
        (do (ansi/print-info "Total time:" (- (get-milis) global-start) "ms")
            [true stage-results])))))

(defn build
  {:malli/schema [:=> [:cat config.schema/?Config git/?Changes] ?JobResult]}
  [config changes]
  (run-external-cmds config changes build-package true))

(defn release
  {:malli/schema [:=> [:cat config.schema/?Config git/?Changes] ?JobResult]}
  [config changes]
  (run-external-cmds config changes release-package true))

(defn custom-command
  {:malli/schema [:=> [:cat config.schema/?Config git/?Changes] ?JobResult]}
  [config changes]
  (run-external-cmds config changes package-custom-command false))

(comment
  (def repo-path "/Users/armed/Developer/k16/transit/micro")
  (def config (config.schema/load-config repo-path))
  (def changes (git/scan-for-changes repo-path (:packages config)))
  (into []
        (comp
         (filter (fn [[_ change]]
                   (:build? change)))
         (map second)
         (map :package-name))
        changes)
  (:package-map config)
  (build repo-path config)
  (def release-result (release config changes))
  (:build-order config)
  (into {} (map (juxt :name identity)) (:packages config))
  (adapter/prepare-deps-env (:adapter (second (:packages config)))
                            changes)
  (git/get-sorted-tags repo-path)
  nil)
