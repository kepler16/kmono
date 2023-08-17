(ns k16.kbuild.git
  (:require
   [babashka.process :as bp]
   [clojure.string :as string]
   [k16.kbuild.config-schema :as config.schema]
   [k16.kbuild.dry :as dry]
   [promesa.core :as p]))

(defn- out->strings
  [{:keys [out err] :as result}]
  (if (seq err)
    (throw (ex-info err {:body result}))
    (let [out (string/trim out)]
      (when (seq out)
        (-> out
            (string/split-lines)
            (vec))))))

(defn run-cmd! [dir & cmd]
  (-> (bp/shell {:dir (str dir)
                 :out :string
                 :err :string}
                (string/join cmd))
      (out->strings)))

(defn get-sorted-tags
  "Returns all tags sorted by creation date (descending)"
  [repo-root]
  (run-cmd! repo-root "git tag --sort=-creatordate"))

(defn subdir-commit-sha
  [sub-dir]
  (first (run-cmd! (str sub-dir) "git log -n 1 --pretty=format:\"%h\" -- .")))

(defn subdir-changes
  [sub-dir tag]
  (if-let [out (run-cmd! sub-dir
                         "git log --pretty=format:\"%s\" "
                         tag "..HEAD -- .")]
    (if (coll? out)
      (vec out)
      [out])))

(def change-type
  {:patch #{:fix :patch :release}
   ;; happens on any change to package
   :build (constantly true)
   :minor #{:minor :feat}
   :major #{:major :breaking}})

(defn bump-type
  [changes]
  (if-let [prefixes (some->> changes
                             (seq)
                             (map (fn [c]
                                    (-> (string/split c #":")
                                        (first)
                                        (string/trim)
                                        (keyword)))))]
    (condp some (set prefixes)
      (change-type :major) :major
      (change-type :minor) :minor
      (change-type :patch) :patch
      :build)
    :none))

(def version-pattern
  (re-pattern #"(?:(?:[^\d]*))(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?(?:[^\d].*)?"))

(defn version?
  [v]
  (boolean (re-matches version-pattern v)))

(def ?Changes
  [:map-of
   :string
   [:map
    [:version :string]
    [:tag {:optional true}
     [:maybe :string]]
    [:package-name :string]
    [:published? :boolean]]])

(defn bump
  "Bumps the version depending of type of bump [major, minor, patch, build]
  strips out any characters not related to version, e.g. v1.1.1-foo becomes 1.1.2"
  [{:keys [version bump-type commit-sha snapshot?]}]
  (if-let [[_ major minor patch build] (re-matches version-pattern version)]
    (let [major (parse-long major)
          minor (parse-long minor)
          patch (parse-long patch)
          build' (if build (parse-long build) 0)
          new-version (case bump-type
                        :major (string/join "." [(inc major) 0 0 0])
                        :minor (string/join "." [major (inc minor) 0 0])
                        :patch (string/join "." [major minor (inc patch) 0])
                        :build (string/join "." [major minor patch (inc build')])
                        version)]
      (if snapshot?
        (str new-version "-" commit-sha "-SNAPSHOT")
        new-version))
    (throw (ex-info "Version does not match pattern `major.minor.patch[.build]`"
                    {:body (str "version: " version)}))))

(comment
  (def version "1.77.2.3")

  (= "2.0.0.0" (bump {:version version
                      :bump-type :major
                      :commit-sha "deadbee"
                      :snapshot? false}))
  (= "1.78.0.0" (bump {:version version
                       :bump-type :minor
                       :commit-sha "deadbee"
                       :snapshot? false}))
  (= "1.77.3.0" (bump {:version version
                       :bump-type :patch
                       :commit-sha "deadbee"
                       :snapshot? false}))
  (= "1.77.3.0-deadbee.dev" (bump {:version version
                                   :bump-type :patch
                                   :commit-sha "deadbee"
                                   :snapshot? true}))
  (= "1.77.2.4" (bump {:version version
                       :bump-type :build
                       :commit-sha "deadbee"
                       :snapshot? false}))
  (= "1.77.2.3" (bump {:version version
                       :bump-type :none
                       :commit-sha "deadbee"
                       :snapshot? false}))

  nil)

(defn package-changes
  [{:keys [repo-root snapshot? create-tags?]} {:keys [name commit-sha dir]}]
  (if-let [tags (get-sorted-tags repo-root)]
    (if-let [latest-tag (->> tags
                             (filter #(string/starts-with? % name))
                             (first))]
      (let [[_ current-version] (string/split latest-tag #"@")
            bump-type (-> (subdir-changes dir latest-tag)
                          (bump-type))]
        (when (version? current-version)
          (let [version (bump {:version current-version
                               :bump-type bump-type
                               :commit-sha commit-sha
                               :snapshot? snapshot?})
                changed? (not= current-version version)]
            {:version version
             :changed? changed?
             ;; create a new tag only if version is changed
             :tag (when (and create-tags? changed? (not snapshot?))
                    (str name "@" version))
             :package-name name})))
      (throw (ex-info (str "ERROR: latest tag for [" name "] not found")
                      {:body name})))))

(defn- bump-dependant
  [dependant config dependant-name]
  (let [{:keys [version]} dependant
        {:keys [snapshot? package-map repo-root]} config
        dpkg (get package-map dependant-name)
        new-version (bump {:version version
                           :bump-type :build
                           :commit-sha (:commit-sha dpkg)
                           :snapshot? snapshot?})]
    (assoc dependant
           :version new-version
           :changed? (not= new-version version))))

(defn ensure-dependent-builds
  [config changes]
  (loop [changes' changes
         cursor (keys changes)]
    (if-let [{:keys [changed? package-name]} (get changes' (first cursor))]
      (if-not changed?
        (let [dependants-to-bump (->> (:graph config)
                                      (map (fn [[pkg-name deps]]
                                             (when (and (contains? deps package-name)
                                                        (-> changes
                                                            (get pkg-name)
                                                            :changed?))
                                               pkg-name)))
                                      (remove nil?))]
          (recur (reduce (fn [chgs dpn-name]
                           (update chgs dpn-name bump-dependant config dpn-name))
                         changes'
                         dependants-to-bump)
                 (rest cursor)))
        (recur changes' (rest cursor)))
      changes')))

(defn scan-for-changes
  "Takes a repo path and kbuild config map. Scans all packages and determines
  version, tag, and should it be built. Build is always true in case of
  fallback_version. Returns a promise containing changes"
  {:malli/schema [:=> [:cat config.schema/?Config] ?Changes]}
  [{:keys [packages] :as config}]
  (->> packages
       (into {} (map (fn [pkg] [(:name pkg) (package-changes config pkg)])))
       (ensure-dependent-builds config)))

(defn create-tags!
  {:malli/schema [:=> [:cat config.schema/?Config [:sequential :string]] :boolean]}
  [{:keys [dry-run? repo-root]} tags]
  (try
    (loop [tags tags]
      (if (seq tags)
        (let [tag (first tags)]
          (run-cmd! repo-root (if dry-run?
                                dry/fake-git-tag-cmd
                                (str "git tag " tag)))
          (recur (rest tags)))
        true))
    (catch Throwable ex
      (do (println (ex-message ex))
          false))))

(defn push-tags!
  {:malli/schema [:=> [:cat config.schema/?Config] :boolean]}
  [{:keys [dry-run? repo-root]}]
  (try
    (run-cmd! repo-root (if dry-run?
                          dry/fake-git-push-cmd
                          "git push origin --tags"))
    true
    (catch Throwable ex
      (do (println (ex-message ex))
          false))))

(comment
  (def graph {"lib-1" #{}
              "lib-2" #{"lib-1"}
              "lib-3" #{"lib-2"}
              "lib-4" #{}})

  (def changes {"lib-1"
                {:version "1.79.1.1-8adecdc.dev",
                 :published? (p/resolved false)
                 :tag nil,
                 :package-name "lib-1"},
                "lib-2"
                {:version "1.79.1",
                 :published? (p/resolved false),
                 :tag nil,
                 :package-name "lib-2"},
                "lib-3"
                {:version "1.79.1",
                 :published? (p/resolved false),
                 :tag nil,
                 :package-name "lib-3"},
                "lib-4"
                {:version "1.79.1",
                 :published? (p/resolved false),
                 :tag nil,
                 :package-name "lib-4"}})

  (= expected-changes @(ensure-dependent-builds {:graph graph} changes))
  nil)
