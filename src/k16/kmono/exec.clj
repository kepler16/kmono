(ns k16.kmono.exec
  (:require
   [babashka.process :as bp]
   [k16.kmono.adapter :as adapter]
   [k16.kmono.ansi :as ansi]
   [k16.kmono.config-schema :as config.schema]
   [k16.kmono.dry :as dry]
   [k16.kmono.git :as git]
   [k16.kmono.proc :as kmono.proc]))

(def ?JobResult
  [:tuple :boolean kmono.proc/?ProcsResult])

(defn- run-external-cmd
  [config changes pkg-name cmd-type]
  (let [pkg-map (:package-map config)
        pkg (get pkg-map pkg-name)
        pkg-deps-env (-> pkg :adapter (adapter/prepare-deps-env changes))
        version (get-in changes [pkg-name :version])
        ext-cmd (if (:dry-run? config)
                  (case cmd-type
                    :build-cmd dry/fake-build-cmd
                    :release-cmd dry/fake-release-cmd
                    dry/fake-custom-cmd)
                  (or (get pkg cmd-type)
                      (get config cmd-type)))
        _ (ansi/assert-err! ext-cmd (str "Command of type [" cmd-type "] could not be found"))
        _ (ansi/print-info "\t" (str pkg-name "@" version " => " ext-cmd))
        build-result (if (or (nil? ext-cmd) (= :skip ext-cmd))
                       {:skipped? true}
                       (bp/process {:extra-env
                                    {"KMONO_PKG_DEPS" pkg-deps-env
                                     "KMONO_PKG_VERSION" version
                                     "KMONO_PKG_NAME" pkg-name}
                                    :out :string
                                    :err :string
                                    :dir (:dir pkg)}
                                   ext-cmd))]
    [pkg-name build-result]))

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
              [success? stage-result] (kmono.proc/await-procs
                                       op-procs terminate-on-fail?)]
          (if success?
            (do (ansi/print-info prefix "stage finished in"
                                 (- (get-milis) start-time)
                                 "ms\n")
                (recur (rest stages) (inc idx) (conj stage-results stage-result)))
            (do (ansi/print-error prefix "stage failed")
                (ansi/print-error "terminating")
                (doseq [[_ proc] op-procs]
                  (when-not (:skipped? proc)
                    (bp/destroy-tree proc)))
                [false (conj stage-results stage-result)])))
        (do (ansi/print-info "Total time:" (- (get-milis) global-start) "ms")
            [true stage-results])))))

(defn build-package
  [config changes pkg-name]
  (let [change (get changes pkg-name)]
    (if (or (:changed? change) (:include-unchanged? config))
      (run-external-cmd config changes pkg-name :build-cmd)
      [pkg-name {:skipped? true}])))

(defn release-package
  [config changes pkg-name]
  (let [pkg-map (:package-map config)
        pkg (get pkg-map pkg-name)
        version (get-in changes [pkg-name :version])
        changed? (get-in changes [pkg-name :changed?])]
    (if (and changed?
             (-> pkg :adapter (adapter/release-published? version) (not)))
      (run-external-cmd config changes pkg-name :release-cmd)
      [pkg-name {:skipped? true}])))

(defn package-custom-command
  [config changes pkg-name]
  (let [change (get changes pkg-name)]
    (if (or (:changed? change) (:include-unchanged? config))
      (run-external-cmd config changes pkg-name :exec)
      [pkg-name {:skipped? true}])))

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

