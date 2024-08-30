(ns k16.kmono.version
  (:require
   [clojure.string :as str]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.schema :as core.schema]
   [k16.kmono.git.commit :as git.commit]
   [k16.kmono.git.tags :as git.tags]
   [k16.kmono.version.semver :as semver]))

(def ?VersionOp
  [:enum nil :patch :minor :major])

(defn resolve-package-versions
  {:malli/schema [:=> [:cat :string core.schema/?PackageMap] core.schema/?PackageMap]}
  [project-root packages]
  (let [tags (git.tags/get-sorted-tags project-root)]
    (->> packages
         (reduce
          (fn [packages [pkg-name pkg]]
            (let [escaped-name (str/replace (str pkg-name) #"\." "\\.")
                  pattern (re-pattern (str escaped-name "@(.*)"))
                  latest (some
                          (fn [tag]
                            (when-let [[_ version] (re-matches pattern tag)]
                              version))
                          tags)
                  pkg (assoc pkg :version latest)]
              (assoc! packages pkg-name pkg)))
          (transient {}))
         persistent!)))

(defn resolve-package-changes
  {:malli/schema [:=> [:cat :string core.schema/?PackageMap] core.schema/?PackageMap]}
  [project-root packages]
  (->> packages
       (reduce
        (fn [packages [pkg-name pkg]]
          (let [commits (git.commit/find-commits-since
                         project-root {:ref (when (:version pkg)
                                              (str pkg-name "@" (:version pkg)))
                                       :subdir (:relative-path pkg)})

                commits (pmap
                         (fn [commit-sha]
                           (git.commit/get-commit-message project-root commit-sha))
                         commits)

                changed? (or (not (:version pkg))
                             (seq commits))

                pkg (cond-> pkg
                      changed? (assoc :dirty true
                                      :commits commits))]

            (assoc! packages pkg-name pkg)))
        (transient {}))
       persistent!))

(defn- version-changed? [packages-a packages-b pkg-name]
  (not= (get-in packages-a [pkg-name :version])
        (get-in packages-b [pkg-name :version])))

(defn inc-package-versions
  {:malli/schema
   [:function
    [:=> [:cat ifn? core.schema/?PackageMap] core.schema/?PackageMap]
    [:=> [:cat ifn? [:maybe :string] core.schema/?PackageMap] core.schema/?PackageMap]]}
  ([version-fn packages] (inc-package-versions version-fn nil packages))
  ([version-fn suffix packages]
   (let [bumped
         (->> packages
              (reduce
               (fn [packages [pkg-name pkg]]
                 (let [inc-type (version-fn pkg)
                       current-version (or (:version pkg) "0.0.0")
                       version (semver/inc-version
                                current-version
                                inc-type
                                suffix)
                       pkg (assoc pkg :version version)]
                   (assoc! packages pkg-name pkg)))
               (transient {}))
              persistent!)]

     (reduce
      (fn [bumped [pkg-name]]
        (if (version-changed? packages bumped pkg-name)
          (let [dependents (core.graph/query-dependents packages pkg-name)]
            (reduce
             (fn [bumped dependent]
               (if-not (version-changed? packages bumped dependent)
                 (update-in bumped
                            [dependent :version]
                            (fn [version]
                              (semver/inc-version version :patch suffix)))
                 bumped))

             bumped
             dependents))
          bumped))

      bumped
      bumped))))
