(ns k16.kbuild.build
  (:require
   [babashka.process :as bp]
   [clojure.string :as string]
   [k16.kbuild.config :as config]
   [k16.kbuild.adapter :as adapter]
   [k16.kbuild.git :as git]))

(def ?Builds
  [:map-of
   :string
   [:map
    [:version :string]
    [:tag :string]
    [:package-name :string]
    [:build? :boolean]]])

(def version-pattern
  (re-pattern #"(?:(?:[^\d]*))(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?(?:[^\d].*)?"))

(defn version?
  [v]
  (boolean (re-matches version-pattern v)))

(defn bump
  "Bumps the version depending of type of bump [major, minor, patch, build]
  strips out any characters not related to version, e.g. v1.1.1-foo becomes 1.1.2"
  [version bump-type]
  (if-let [[_ major minor patch build] (re-matches version-pattern version)]
    (let [major (parse-long major)
          minor (parse-long minor)
          patch (parse-long patch)
          build' (if build (parse-long build) 0)]
      (case bump-type
        :major (string/join "." [(inc major) 0 0 0])
        :minor (string/join "." [major (inc minor) 0 0])
        :patch (string/join "." [major minor (inc patch) 0])
        :build (string/join "." [major minor patch (inc build')])
        version))
    (throw (ex-info "Version does not match pattern `major.minor.patch[.build]`"
                    {:body (str "version: " version)}))))

(comment
  (def version "1.77.2.3")

  (= "2.0.0.0" (bump version :major))
  (= "1.78.0.0" (bump version :minor))
  (= "1.77.3.0" (bump version :patch))
  (= "1.77.2.4" (bump version :chore))
  (= "1.77.2.3" (bump version :none))

  (bump "lol1.3.2.4" :major)
  (bump "lol1.3.2" :none)
  (re-matches version-pattern "1.2.3")

  nil)

(defn package-changes
  [repo-path {:keys [name dir]}]
  (let [tags (git/get-sorted-tags repo-path)]
    (when-let [latest-tag (->> tags
                               (filter #(string/starts-with? % name))
                               (first))]
      (let [[_ current-version] (string/split latest-tag #"@")
            bump-type (-> (git/subdir-changes dir latest-tag)
                          (git/bump-type))]
        (when (version? current-version)
          (let [version (bump current-version bump-type)
                tag (str name "@" version)]
            {:version version
             :tag tag
             :package-name name
             :build? (not= :none bump-type)}))))))

(defn scan-for-changes
  "Takes a repo path and kbuild config map. Scans all packages and determines
  version, tag, and should it be built. Build is always true in case of
  fallback_version"
  {:malli/schema [:=> [:cat :string config/?Packages] ?Builds]}
  [repo-path packages]
  (into {}
        (map (fn [pkg] [(:name pkg) (package-changes repo-path pkg)]))
        packages))

(defn build-package
  [pkg-map changes pkg-name]
  (println "\t" pkg-name "building")
  (let [pkg (get pkg-map pkg-name)
        deps-env (-> pkg :adapter (adapter/prepare-deps-env changes))
        version (get-in changes [pkg-name :version])
        build-cmd (:build-cmd pkg)
        build-result (bp/process {:extra-env
                                  {"KBUILD_DEPS_ENV" deps-env
                                   "VERSION" version}
                                  :out :string
                                  :err :string
                                  :dir (:dir pkg)}
                                 build-cmd)]
    [pkg-name build-result]))

(defn get-milis
  []
  (.getTime (java.util.Date.)))

(defn build
  [repo-path config]
  (let [package-map (into {} (map (juxt :name identity)) (:packages config))
        build-order (:build-order config)
        changes (scan-for-changes repo-path (:packages config))
        stages-to-run (map (fn [build-stage]
                             (filter (fn [pkg-name]
                                       (get-in changes [pkg-name :build?]))
                                     build-stage))
                           build-order)
        global-start (get-milis)]
    (doseq [stage stages-to-run]
      (println "Stage started")
      (let [start-time (get-milis)
            build-procs (mapv (partial build-package package-map changes) stage)]
        (println)
        (doseq [[pkg-name proc] build-procs]
          {pkg-name
           (if (zero? (-> (deref proc) :exit))
             (do (println "\t" pkg-name "complete")
                 @(:out proc))
             (do (println "\t" pkg-name "failed")
                 @(:err proc)))})
        (println "Stage finished in"
                 (- (get-milis) start-time)
                 "ms\n")))
    (println "Total time:" (- (get-milis) global-start) "ms")))

(comment
  (def repo-path "/Users/armed/Developer/k16/transit/micro")
  (def config (config/load-config repo-path))
  (build repo-path config)
  (:build-order config)
  (into {} (map (juxt :name identity)) (:packages config))
  (def changes (scan-for-changes repo-path (:packages config)))
  (adapter/prepare-deps-env (:adapter (second (:packages config)))
                            changes)
  (git/get-sorted-tags repo-path)
  nil)
