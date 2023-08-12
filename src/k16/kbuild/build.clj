(ns k16.kbuild.build
  (:require
   [babashka.process :as bp]
   [k16.kbuild.adapter :as adapter]
   [k16.kbuild.config :as config]
   [k16.kbuild.dry :as dry]
   [k16.kbuild.git :as git]))

(def ?ProcsResult
  [:vector
   [:map-of
    :string
    [:map
     [:success? :boolean]
     [:output :string]]]])

(def ?ProcResult
  [:map
   [:success? :boolean]
   [:output :string]])

(def ?ProcAwaitResults
  [:map-of :string ?ProcResult])

(def ?JobResult
  [:tuple :boolean ?ProcsResult])

(def ?BuildProc
  [:tuple :string :map])

(defn- run-external-cmd
  [config changes pkg-name cmd-type]
  (let [pkg-map (:package-map config)
        pkg (get pkg-map pkg-name)
        deps-env (-> pkg :adapter (adapter/prepare-deps-env changes))
        version (get-in changes [pkg-name :version])
        _ (println "\t" cmd-type (str pkg-name "@" version))
        ext-cmd (if (:dry-run? config)
                  (if (= :build-cmd cmd-type)
                    dry/fake-build-cmd
                    dry/fake-release-cmd)
                  (get pkg cmd-type))
        build-result (bp/process {:extra-env
                                  {"KBUILD_DEPS_ENV" deps-env
                                   "KBUILD_PKG_VERSION" version
                                   "KBUILD_PKG_NAME" pkg-name}
                                  :out :string
                                  :err :string
                                  :dir (:dir pkg)}
                                 ext-cmd)]
    [pkg-name build-result]))

(defn build-package
  [config changes pkg-name]
  (run-external-cmd config changes pkg-name :build-cmd))

(defn release-package
  [config changes pkg-name]
  (run-external-cmd config changes pkg-name :release-cmd))

(defn get-milis
  []
  (.getTime (java.util.Date.)))

(defn- failed?
  [proc]
  (not (-> proc (deref) :exit (zero?))))

(defn- rotate [v]
  (into (vec (drop 1 v)) (take 1 v)))

(defn- ->success
  {:malli/schema [:=> [:cat :map] ?ProcResult]}
  [proc]
  {:success? true
   :output @(:out proc)})

(defn- ->failure
  {:malli/schema [:=> [:cat :map] ?ProcResult]}
  [proc]
  {:success? false
   :output @(:err proc)})

(defn await-procs
  {:malli/schema [:=>
                  [:cat [:sequential ?BuildProc] :boolean]
                  [:tuple :boolean ?ProcAwaitResults]]}
  [build-procs terminate-on-failure?]
  (loop [build-procs build-procs
         results {}]
    (let [[pkg-name proc] (first build-procs)]
      (if proc
        (if (bp/alive? proc)
          (do (Thread/sleep 200)
              (recur (rotate build-procs) results))
          (if (failed? proc)
            (if terminate-on-failure?
              [false (assoc results pkg-name (->failure proc))]
              (do (println "\t" pkg-name "failed")
                  (recur (rest build-procs) (assoc results pkg-name (->failure proc)))))
            (do (println "\t" pkg-name "complete")
                (recur (rest build-procs) (assoc results pkg-name (->success proc))))))
        [true results]))))

(defn build
  {:malli/schema [:=> [:cat config/?Config git/?Changes] ?JobResult]}
  [config changes]
  (let [build-order (:build-order config)
        stages-to-run (map (fn [build-stage]
                             (filter (fn [pkg-name]
                                       (get-in changes [pkg-name :build?]))
                                     build-stage))
                           build-order)
        global-start (get-milis)]
    (println (count stages-to-run) "parallel stages to run...")
    (loop [stages stages-to-run
           idx 1
           stage-results []]
      (if (seq stages)
        (let [stage (first stages)
              prefix (str "#" idx)
              start-time (get-milis)
              _ (println prefix "stage started" (str "(" (count stage) " packages)"))
              build-procs (mapv (partial build-package config changes) stage)
              _ (println)
              [success? stage-result] (await-procs build-procs true)]
          (if success?
            (do (println prefix "stage finished in"
                         (- (get-milis) start-time)
                         "ms\n")
                (recur (rest stages) (inc idx) (conj stage-results stage-result)))
            (do (println prefix "stage failed for package:" (first stage-result))
                (println "Terminating")
                (doseq [[_ proc] build-procs]
                  (bp/destroy-tree proc))
                [false (conj stage-results stage-result)])))
        (do (println "Total time:" (- (get-milis) global-start) "ms")
            (println "Results:")
            [true stage-results])))))

(defn release
  {:malli/schema [:=> [:cat config/?Config git/?Changes]
                  [:map-of :string ?ProcResult]]}
  [config changes]
  (let [pkgs-to-release (into []
                              (comp
                               (filter (fn [[_ change]]
                                         (:build? change)))
                               (map second)
                               (map :package-name))
                              changes)]
    (println (count pkgs-to-release) "packages will be released")
    (let [release-procs (mapv (partial release-package config changes)
                              pkgs-to-release)
          _ (println)
          [_ result] (await-procs release-procs false)]
      result)))

(comment
  (def repo-path "/Users/armed/Developer/k16/transit/micro")
  (def config (config/load-config repo-path))
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
