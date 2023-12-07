(ns k16.kmono.api
  (:require
   [clojure.string :as string]
   [k16.kmono.ansi :as ansi]
   [k16.kmono.config :as config]
   [k16.kmono.exec :as exec]
   [k16.kmono.git :as git]
   [k16.kmono.repl.deps :as repl.deps]
   [malli.core :as m]
   [malli.transform :as mt]))

(defn- print-stage-results
  [stage-results]
  (doseq [stage-result stage-results]
    (doseq [[pkg-name {:keys [success? output]}] stage-result]
      (if success?
        (ansi/print-success pkg-name)
        (ansi/print-error pkg-name))
      (ansi/print-shifted output))))

(defn- run-build
  [config changes]
  (let [[success? stage-results] (exec/build config changes)]
    (print-stage-results stage-results)
    success?))

(defn- run-custom-cmd
  [config changes]
  (ansi/assert-err! (:exec config) "custom command not specified")
  (let [[_ stage-results] (exec/custom-command config changes)]
    (print-stage-results stage-results)
    (->> stage-results
         (apply merge)
         (vals)
         (map :success?)
         (filter not)
         (seq)
         (nil?))))

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
        create-tags? (:create-tags? config)
        snapshot? (:snapshot? config)
        tags-to-create (->> released-packages
                            (map (fn [pkg-name]
                                   (let [{:keys [changed? version]} (get changes pkg-name)]
                                     (when (and create-tags? changed? (not snapshot?))
                                       (str pkg-name "@" version)))))
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
      (ansi/print-info "no tags has been created"))
    (empty? failed-releases)))

(def ?RunParams
  [:map
   [:exec [:or :string [:enum :build :release]]]
   [:repo-root {:default "."}
    :string]
   [:glob {:default "packages/*"}
    :string]
   [:dry-run? {:default false}
    :boolean]
   [:snapshot? {:default true}
    :boolean]
   [:create-tags? {:default false}
    :boolean]
   [:include-unchanged? {:default true}
    :boolean]
   [:build-cmd {:optional true}
    [:maybe :string]]
   [:release-cmd {:optional true}
    [:maybe :string]]])

(defn run
  [{:keys [dry-run?] :as args}]
  (if dry-run?
    (ansi/print-info "Starting kmono in dry mode...")
    (ansi/print-info "Starting kmono..."))
  (let [{:keys [repo-root glob exec]
         :as run-params} (m/decode ?RunParams args mt/default-value-transformer)
        config (-> run-params
                   (merge (config/load-config repo-root glob))
                   (config/validate-config!))
        changes (git/scan-for-changes config)
        _ (ansi/assert-err! (seq (:build-order config)) "no packages to execute found")
        success? (case exec
                   :build (run-build config changes)
                   :release (run-release config changes)
                   (run-custom-cmd config changes))]
    (if success?
      (System/exit 0)
      (System/exit 1))))

(def ?ReplParams
  [:map
   [:aliases {:optional true}
    [:vector :keyword]]
   [:packages-aliases {:optional true}
    [:vector :keyword]]])

(def nrepl-overrides
  {:kmono-nrepl {:extra-deps {'cider/cider-nrepl {:mvn/version "0.44.0"}}
                 :main-opts ["-m"
                             "nrepl.cmdline"
                             "--middleware"
                             "[cider.nrepl/cider-middleware]"]}})

(defn repl
  [{:keys [aliases package-aliases repo-root glob]}]
  (ansi/print-info "Starting kmono REPL...")
  (let [config (config/load-config repo-root glob)
        package-overrides (repl.deps/construct-sdeps-overrides!
                           config package-aliases)
        sdeps-overrides (merge package-overrides nrepl-overrides)
        sdeps ["-Sdeps" (str "\"" (pr-str sdeps-overrides) "\"")]
        main-opts (if (or (seq package-aliases) (seq aliases))
                    [(str "-M"
                          (string/join aliases)
                          (string/join package-aliases)
                          ":kmono-nrepl")]
                    ["-M"])]
    (concat ["clojure"] sdeps main-opts)))

(comment
  (def args {:snapshot? true
             :create-tags? false
             :exec :release
             :dry-run? false})
  (config/load-config "../../k42/agent")
  (def config (as-> (config/load-config "." "packages/*") x
                (merge args x)
                (m/decode ?RunParams x mt/default-value-transformer)
                (config/validate-config! x)))
  (def changes (git/scan-for-changes config))

  (def create-tags? true)
  (def snapshot? false)
  (let [pkg-name "transit-engineering/http"]
    (let [{:keys [changed? version]} (get changes pkg-name)]
      (when (and create-tags? changed? (not snapshot?))
        (str pkg-name "@" version))))

  (run-build config changes)
  (run-release config changes)

  (run {:snapshot? false
        :repo-root "../../k42/agent"
        :create-tags? false
        :exec :release
        :dry-run? false})

  nil)
