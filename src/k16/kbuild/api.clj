(ns k16.kbuild.api
  (:require
   [clojure.string :as string]
   [k16.kbuild.ansi :as ansi]
   [k16.kbuild.config :as config]
   [k16.kbuild.exec :as exec]
   [k16.kbuild.git :as git]
   [malli.core :as m]
   [malli.transform :as mt]))

(defn- print-stage-result
  [stage-result]
  (map (fn [[pkg-name {:keys [success? output]}]]
         (println (str (if success?
                         (ansi/green "**** [OK] ")
                         (ansi/red "**** [ERROR] ")) pkg-name))
         (println output)
         success?)
       stage-result))

(defn- run-build
  [config changes]
  (let [[success? results] (exec/build config changes)]
    (doseq [stage-result results]
      (print-stage-result stage-result))
    success?))

(defn- run-custom-cmd
  [config changes]
  (assert (:custom-cmd config) "Custom command not specified")
  (let [[_ results] (exec/custom-command config changes)
        success? (->> results
                      (map (fn [stage-result]
                             (print-stage-result stage-result)))
                      (filter not)
                      (seq)
                      (nil?))]
    success?))

(defn- run-release
  [config changes]
  (println "Releasing...")
  (let [[success? stage-results] (exec/release config changes)
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
      (println (ansi/red "**** [ERROR] ") pkg-name "failed to release:")
      (let [shifted-output (->> result
                             :output
                             (string/split-lines)
                             (map #(str "\t" %))
                             (string/join))]
        (println (str shifted-output "\n"))))
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

(def ?RunParams
  [:map
   [:mode [:enum :build :release :exec]]
   [:repo-root {:default "."}
    :string]
   [:glob {:default "packages/*"}
    :string]
   [:dry-run? {:default false}
    :boolean]
   [:snapshot? {:default true}
    :boolean]
   [:custom-cmd {:optional true}
    [:maybe :string]]
   [:build-cmd {:optional true}
    [:maybe :string]]
   [:release-cmd {:optional true}
    [:maybe :string]]])

(defn run
  [{:keys [dry-run?] :as args}]
  (if dry-run?
    (println "Starting kbuild in dry mode...")
    (println "Starting kbuild..."))
  (let [{:keys [repo-root glob mode]
         :as run-params} (m/decode ?RunParams args mt/default-value-transformer)
        config (-> run-params
                   (merge (config/load-config repo-root glob))
                   (config/validate-config!))
        changes (git/scan-for-changes config)
        _ (assert (seq (:build-order config)) "No packages to build found")
        success? (case mode
                   :build (run-build config changes)
                   :release (run-release config changes)
                   :exec (run-custom-cmd config changes))]
    #_(if success?
        (System/exit 0)
        (System/exit 1))))

(comment
  (def config (-> (config/load-config "../../transit/micro"
                                      "packages/*")
                  (merge {:snapshot? true
                          :dry-run? false})))
  (-> (config/load-config "../../transit/micro"
                          "packages/*")
      (merge {:custom-cmd "just test"})
      (config/validate-config!))
  (def changes (git/scan-for-changes config))
  (run-build config changes)
  (run-release config changes)

  (run {:mode :exec
        :repo-root "../../transit/micro"
        :custom-cmd "just test"})

  nil)
