(ns k16.kbuild.git
  (:require
   [babashka.process :as bp]
   [clojure.string :as string]
   [k16.kbuild.config :as config]
   [k16.kbuild.dry :as dry]))

(defn- out->strings
  [{:keys [out err] :as result}]
  (if (seq err)
    (throw (ex-info err {:body result}))
    (-> (string/trim out)
        (string/split-lines)
        (vec))))

(defn run-cmd! [dir & cmd]
  (-> (bp/shell {:dir dir
                 :out :string
                 :err :string}
                (string/join cmd))
      (out->strings)))

(defn get-sorted-tags
  "Returns all tags sorted by creation date (descending)"
  [repo-root]
  (run-cmd! repo-root "git tag --sort=-creatordate"))

(defn subdir-changes
  [sub-dir tag]
  (let [out (run-cmd! sub-dir
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
    [:tag :string]
    [:package-name :string]
    [:build? :boolean]]])

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
  [{:keys [repo-root snapshot?]} {:keys [name dir]}]
  (let [tags (get-sorted-tags repo-root)]
    (when-let [latest-tag (->> tags
                               (filter #(string/starts-with? % name))
                               (first))]
      (let [[_ current-version] (string/split latest-tag #"@")
            bump-type (-> (subdir-changes dir latest-tag)
                          (bump-type))]
        (when (version? current-version)
          (let [version (bump current-version bump-type)
                version' (if snapshot?
                          (str version "-SNAPSHOT") 
                          version)
                tag (str name "@" version')]
            {:version version'
             :tag tag
             :package-name name
             :build? (not= :none bump-type)}))))))

(defn scan-for-changes
  "Takes a repo path and kbuild config map. Scans all packages and determines
  version, tag, and should it be built. Build is always true in case of
  fallback_version"
  {:malli/schema [:=> [:cat config/?Config] ?Changes]}
  [{:keys [packages] :as config}]
  (into {}
        (map (fn [pkg] [(:name pkg) (package-changes config pkg)]))
        packages))

(defn create-tags!
  {:malli/schema [:=> [:cat config/?Config [:sequential :string]] :boolean]}
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
  {:malli/schema [:=> [:cat config/?Config] :boolean]}
  [{:keys [dry-run? repo-root]}]
  (try
    (run-cmd! repo-root (if dry-run?
                          dry/fake-git-push-cmd
                          "git push origin --tags"))
    true
    (catch Throwable ex
      (do (println (ex-message ex))
          false))))
