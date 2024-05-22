(ns k16.kmono.api
  (:require
   [babashka.fs :as fs]
   [clojure.string :as string]
   [k16.kmono.ansi :as ansi]
   [k16.kmono.config :as config]
   [k16.kmono.exec :as exec]
   [k16.kmono.git :as git]
   [k16.kmono.repl.deps :as repl.deps]
   [malli.core :as m]
   [malli.transform :as mt]))

(defmacro with-assertion-error
  [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo ex#
       (let [data# (ex-data ex#)]
         (when-not (= :errors/assertion (:type data#))
           (ansi/print-error (ex-message ex#)))
         (when-let [body# (:body data#)]
           (ansi/print-shifted (str body#)))
         (System/exit 1)))))

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
    [success? stage-results]))

(defn- run-custom-cmd
  [config changes]
  (ansi/assert-err! (:exec config) "custom command not specified")
  (let [[_ stage-results] (exec/custom-command config changes)
        success? (->> stage-results
                      (apply merge)
                      (vals)
                      (map :success?)
                      (filter not)
                      (seq)
                      (nil?))]
    (print-stage-results stage-results)
    [success? stage-results]))

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
    [(empty? failed-releases) stage-results]))

(def ?RunOpts
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

(defn arg->exec
  [[_ exec-cmd]]
  (if (or (= "build" exec-cmd)
          (= "release" exec-cmd))
    (keyword exec-cmd)
    exec-cmd))

(defn -run [{:keys [exec dry-run?] :as opts} arguments]
  (if dry-run?
    (ansi/print-info "Starting kmono in dry mode...")
    (ansi/print-info "Starting kmono..."))
  (let [opts' (if (and (not exec) (seq arguments))
                (assoc opts :exec (arg->exec arguments))
                opts)
        {:keys [repo-root glob exec]
         :as run-params} (m/decode ?RunOpts opts' mt/default-value-transformer)
        config (->> run-params
                    (merge (config/load-config repo-root glob))
                    (config/validate-config!))
        changes (git/scan-for-changes config)
        _ (ansi/assert-err! (seq (:build-order config)) "no packages to execute found")]
    (case exec
      :build (run-build config changes)
      :release (run-release config changes)
      (run-custom-cmd config changes))))

(defn run
  ([opts] (run opts nil))
  ([opts arguments]
   (with-assertion-error
     (let [[success?] (-run opts arguments)]
       (if success?
         (System/exit 0)
         (System/exit 1))))))

(defn- relativize-to-repo-root
  [repo-root path]
  (when path
    (if (fs/absolute? path)
      (fs/path path)
      (fs/path repo-root path))))

(defn repl
  ([opts]
   (repl opts nil))
  ([{:keys [repo-root cp-file configure-lsp?] :as opts} _]
   (with-assertion-error
     (let [cp-file' (when configure-lsp?
                      (or (relativize-to-repo-root repo-root cp-file)
                          (relativize-to-repo-root repo-root ".lsp/.kmonocp")))
           opts' (assoc opts :cp-file (str cp-file'))]
       (repl.deps/run-repl opts')))))

(defn generate-classpath!
  ([opts]
   (generate-classpath! opts nil))
  ([opts _]
   (binding [ansi/*logs-enabled* (:cp-file opts)]
     (with-assertion-error
       (repl.deps/generate-classpath! opts)))))

(defn generate-deps!
  ([opts]
   (generate-deps! opts nil))
  ([opts _]
   (binding [ansi/*logs-enabled* (:deps-file opts)]
     (with-assertion-error
       (repl.deps/generate-deps! opts)))))

