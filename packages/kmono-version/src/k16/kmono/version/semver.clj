(ns k16.kmono.version.semver
  (:require
   [clojure.string :as str]))

(def ^:private version-pattern
  (re-pattern #"(?:(?:[^\d]*))(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?(?:[^\d].*)?"))

(defn parse-version-string [version]
  (when-let [[_ major minor patch build]
             (re-matches version-pattern version)]
    {:major (parse-long major)
     :minor (parse-long minor)
     :patch (parse-long patch)
     :build (when build (parse-long build))}))

(defn inc-version
  ([version inc-type] (inc-version version inc-type nil))
  ([version inc-type suffix]
   (if-let [{:keys [major minor patch build]}
            (parse-version-string version)]
     (let [new-version (case inc-type
                         :major [(inc major) 0 0 0]
                         :minor [major (inc minor) 0 0]
                         :patch [major minor (inc patch) 0]
                         :build [major minor patch (inc (or build 0))]
                         [major minor patch build])

           new-version (if (or build
                               (= :build inc-type))
                         new-version
                         (drop-last new-version))

           version-string (str/join "." new-version)]

       (if suffix
         (str version-string "-" suffix)
         version-string))

     (throw (ex-info "Version does not match pattern `major.minor.patch[.build]`"
                     {:body (str "version: " version)})))))
