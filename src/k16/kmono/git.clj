(ns k16.kmono.git
  (:require
   [babashka.fs :as fs]
   [babashka.process :as bp]
   [clojure.set :as set]
   [clojure.string :as string]
   [k16.kmono.config-schema :as config.schema]
   [k16.kmono.dry :as dry]))

(defn git-initialzied?
  [repo-root]
  (let [git-dir (fs/file repo-root ".git")]
    (and (fs/exists? git-dir) (fs/directory? git-dir))))

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
                (string/join " " cmd))
      (out->strings)))

(defn get-sorted-tags
  "Returns all tags sorted by creation date (descending)"
  [repo-root]
  (run-cmd! repo-root "git tag --sort=-creatordate"))

(defn subdir-commit-sha
  [exclusions sub-dir]
  (first (run-cmd!
          (str sub-dir)
          "git log -n 1 --pretty=format:\"%h\" -- ."
          exclusions)))

(defn subdir-changes
  [sub-dir tag exclusions]
  (when-let [out (run-cmd! sub-dir
                           "git log --pretty=format:\"%s\" "
                           (str tag "..HEAD -- .")
                           exclusions)]
    (if (coll? out)
      (vec out)
      [out])))

(def change-type
  {:patch #{"fix" "patch" "release"}
   :minor #{"minor" "feat"}
   :major #{"major" "breaking"}})

(def prefixes (apply set/union (vals change-type)))

(defn find-prefix
  [message]
  (when-let [msg-prefix (second (re-find #"(:?.+):.+" message))]
    (some (fn [p] (when (string/starts-with? msg-prefix p) p))
          prefixes)))

(defn bump-type
  [changes]
  (if-let [chahge-prefixes (some->> changes (seq) (map find-prefix))]
    (condp some (set chahge-prefixes)
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

(defn non-git-pkg-changes [snapshot? pkg]
  {:version (if snapshot?
              (str "0.0.0.0-" (:commit-sha pkg) "-SNAPSHOT")
              "0.0.0.0")
   :changed? true
   :package-name (:name pkg)})

(defn package-changes
  [{:keys [repo-root snapshot? glob include-unchanged?]}
   {:keys [commit-sha dir] :as pkg}]
  (def repo-root repo-root)
  (def snapshot? snapshot?)
  (def glob glob)
  (def include-unchanged? include-unchanged?)
  (def pkg pkg)
  (def commit-sha commit-sha)
  (def dir dir)
  (or (and (git-initialzied? repo-root)
           (when-let [latest-tag (some->>
                                  repo-root
                                  (get-sorted-tags)
                                  (filter #(string/starts-with?
                                            % (str (:name pkg) "@")))
                                  (first))]
             (let [[_ current-version] (string/split latest-tag #"@")
                   exclusions (when (fs/same-file? repo-root dir)
                                (str ":!:" glob))
                   bump-type (-> (subdir-changes dir latest-tag exclusions)
                                 (bump-type))]
               (when (version? current-version)
                 (let [changed? (or (not= :none bump-type)
                                    include-unchanged?)
                       version (if changed?
                                 (bump {:version current-version
                                        :bump-type bump-type
                                        :commit-sha commit-sha
                                        :snapshot? snapshot?})
                                 current-version)]
                   {:version version
                    :changed? changed?
                    :package-name (:name pkg)})))))
      (non-git-pkg-changes snapshot? pkg)))

(defn- bump-dependand
  [dependant config dependant-name]
  (let [{:keys [version]} dependant
        {:keys [snapshot? package-map]} config
        dpkg (get package-map dependant-name)
        new-version (bump {:version version
                           :bump-type :build
                           :commit-sha (:commit-sha dpkg)
                           :snapshot? snapshot?})]
    (assoc dependant
           :version new-version
           :changed? (not= new-version version))))

(defn ensure-dependend-builds
  [config changes]
  (loop [changes' changes
         cursor (keys changes)]
    (if-let [{:keys [changed? package-name]} (get changes' (first cursor))]
      (if changed?
        (let [dependands-to-bump (->> (:graph config)
                                      (map (fn [[pkg-name deps]]
                                             (when (and (contains? deps package-name)
                                                        (-> changes
                                                            (get pkg-name)
                                                            :changed?
                                                            (not)))
                                               pkg-name)))
                                      (remove nil?))]
          (recur (reduce (fn [chgs dpn-name]
                           ;; NOTE: we want to bump only once
                           (if (= (get-in chgs [dpn-name :version])
                                  (get-in changes [dpn-name :version]))
                             (update chgs dpn-name bump-dependand config dpn-name)
                             chgs))
                         changes'
                         dependands-to-bump)
                 (rest cursor)))
        (recur changes' (rest cursor)))
      changes')))

(defn scan-for-changes
  "Takes a repo path and kmono config map. Scans all packages and determines
  version, tag, and should it be built. Build is always true in case of
  fallback_version. Returns a promise containing changes"
  {:malli/schema [:=> [:cat config.schema/?Config] ?Changes]}
  [{:keys [packages] :as config}]
  (package-changes config (second packages))
  (->> packages
       (into {} (map (fn [pkg] [(:name pkg) (package-changes config pkg)])))
       (ensure-dependend-builds config)))

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

