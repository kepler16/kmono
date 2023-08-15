(ns k16.kbuild.api
  (:require
   [clojure.string :as string]
   [k16.kbuild.build :as build]
   [k16.kbuild.config :as config]
   [k16.kbuild.git :as git]))

(defn- print-stage-result
  [stage-result]
  (doseq [[pkg-name {:keys [success? output]}] stage-result]
    (println (str (if success? "[OK]" "[ERROR]") pkg-name))
    (println output)))

(defn- run-build
  [config changes]
  (let [[success? results] (build/build config changes)]
    (doseq [stage-result results]
      (print-stage-result stage-result))
    success?))

(defn- run-release
  [config changes]
  (println "Releasing...")
  (let [[success? stage-results] (build/release config changes)
        all-results (apply merge stage-results)
        failed-releases (into {}
                              (filter (fn [[_ result]]
                                        (not (:success? result))))
                              all-results)
        success-releases (into []
                               (comp
                                (filter (fn [[_ result]]
                                          (:success? result)))
                                (map first))
                               all-results)
        tags-to-create (->> success-releases
                            (map (fn [pkg-name]
                                   (get-in changes [pkg-name :tag])))
                            (remove nil?))]
    (doseq [[pkg-name result] failed-releases]
      (println pkg-name "failed to release:")
      (println (str (:output result) "\n")))
    (println)
    (if (seq tags-to-create)
      (try
        (println "Creating tags for successful results")
        (git/create-tags! config tags-to-create)
        (git/push-tags! config)
        (println (str "Tags created and pushed: \n\t "
                      (string/join "\n\t " tags-to-create)))
        (catch Throwable ex
          (println "Error creating and pushing git tags:")
          (println (ex-message ex))
          (println (:body (ex-data ex)))))
      (println "No tags has been created"))
    success?))

(defn run
  [{:keys [mode repo-root glob dry-run? snapshot?]
    :or {repo-root "."
         glob "packages/*"
         dry-run? false}}]
  (if dry-run?
    (println "Starting kbuild in dry mode...")
    (println "Starting kbuild..."))
  (let [config (-> (config/load-config repo-root glob)
                   (merge {:snapshot? snapshot?
                           :dry-run? dry-run?})
                   (config/validate-config!))
        changes (git/scan-for-changes config)
        success? (case mode
                   :build (run-build config changes)
                   :release (run-release config changes))]
    (if success?
      (System/exit 0)
      (System/exit 1))))

(comment
  (def config (-> (config/load-config "../../transit/micro"
                                      "packages/*")
                  (merge {:snapshot? true
                          :dry-run? false})))
  (def changes (git/scan-for-changes config))
  (run-build config changes)
  (run-release config changes)
  (run {:mode :build
        :repo-root "../../transit/micro"})
  nil)
