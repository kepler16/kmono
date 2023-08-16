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
         (if success?
           (ansi/print-success pkg-name)
           (ansi/print-error pkg-name))
         (ansi/print-shifted output)
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
  (ansi/assert-err! (:custom-cmd config) "custom command not specified")
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
  (ansi/print-info "releasing...")
  (let [[_ stage-results] (exec/release config changes)
        all-results (apply merge stage-results)
        failed-releases (into {}
                              (filter (fn [[_ result]]
                                        (not (:success? result))))
                              all-results)
        released-packages (into []
                                (comp
                                 (filter (fn [[_ result]]
                                           (:success? result)))
                                 (map first))
                                all-results)
        tags-to-create (->> released-packages
                            (map (fn [pkg-name]
                                   (get-in changes [pkg-name :tag])))
                            (remove nil?))]
    (doseq [[pkg-name result] failed-releases]
      (ansi/print-error pkg-name "failed to release")
      (ansi/print-shifted (:output result)))
    (if (seq tags-to-create)
      (try
        (ansi/print-info "creating tags for successful results")
        (git/create-tags! config tags-to-create)
        (git/push-tags! config)
        (ansi/print-success "tags created and pushed")
        (ansi/print-shifted (string/join "\n" tags-to-create))
        (catch Throwable ex
          (ansi/print-error "creating and pushing git tags")
          (ansi/print-shifted (ex-message ex))
          (ansi/print-shifted (:body (ex-data ex)))))
      (println "No tags has been created"))
    (empty? failed-releases)))

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
    (ansi/print-info "Starting kbuild in dry mode...")
    (ansi/print-info "Starting kbuild..."))
  (let [{:keys [repo-root glob mode]
         :as run-params} (m/decode ?RunParams args mt/default-value-transformer)
        config (-> run-params
                   (merge (config/load-config repo-root glob))
                   (config/validate-config!))
        changes (git/scan-for-changes config)
        _ (ansi/assert-err! (seq (:build-order config)) "no packages to execute found")
        success? (case mode
                   :build (run-build config changes)
                   :release (run-release config changes)
                   :exec (run-custom-cmd config changes))]
    (if success?
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
  (update-vals changes (fn [v] (update v :published? deref)))
  (run-build config changes)
  (run-release config changes)

  (run {:mode :exec
        :repo-root "../../transit/micro"
        :custom-cmd "just test"})

  nil)
