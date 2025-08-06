(ns k16.kmono.version
  (:require
   [clojure.string :as str]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.schema :as core.schema]
   [k16.kmono.core.thread :as core.thread]
   [k16.kmono.git.commit :as git.commit]
   [k16.kmono.git.tags :as git.tags]
   [k16.kmono.version.semver :as semver]))

(def ^:no-doc ?VersionOp
  [:enum nil :patch :minor :major])

(def ^:no-doc ?VersionFn
  [:function
   [:-> core.schema/?Package ?VersionOp]])

(defn match-package-version-tag
  "This matches whether a given string is a correctly formatted git version tag
  for a package with the given `pkg-name`.

  Returns the version substring of the tag or `nil` if there was no match.

  ```clojure
  (match-package-version-tag \"com.kepler16/kmono-core@1.0.0\", 'com.kepler16/kmono-core)
  ;; => \"1.0.0\"
  ```"
  [tag pkg-name]
  (let [escaped-name (str/replace (str pkg-name) #"\." "\\.")
        pattern (re-pattern (str escaped-name "@(.*)"))

        [_ version] (re-matches pattern tag)]

    version))

(defn create-package-version-tag
  "Construct a valid package version tag from a given `package`"
  [pkg]
  (str (:fqn pkg) "@" (:version pkg)))

(defn resolve-package-versions
  "Try resolve the last known version for each of the given `packages`.

  This works by fiding the latest git tag for each package which follows the
  structure of `<package-group>/<package-name>@<package-version>`. As an
  example a package with the name `com.kepler16/kmono-core` might have a tag
  `com.kepler16/kmono-core@1.0.0`

  The version component of the tag is matched and used to set the package
  `:version` key.

  This is default implementation for resolving package versions. If your
  use-case requires alternative strategies then you might be interested in
  writing your own version of this function.

  Other kmono-* API's only care about there being a `:version` set on a
  package therefore how that field is set is up to you."
  {:malli/schema [:-> :string core.schema/?PackageMap core.schema/?PackageMap]}
  [project-root packages]
  (let [tags (git.tags/get-sorted-tags project-root)]
    (->> packages
         (reduce
          (fn [packages [pkg-name pkg]]
            (let [latest (some #(match-package-version-tag % pkg-name) tags)
                  pkg (assoc pkg :version latest)]
              (assoc! packages pkg-name pkg)))
          (transient {}))
         persistent!)))

(defn- -resolve-package-changes-since
  [project-root packages rev-fn]
  (into {}
        (core.thread/batch
         (fn find-commits [[pkg-name pkg]]
           (let [commits (git.commit/find-commits-since
                          project-root {:ref (rev-fn pkg)
                                        :subdir (:relative-path pkg)})

                 commits (pmap (fn [commit-sha]
                                 (git.commit/get-commit-details project-root
                                                                commit-sha))
                               commits)

                 pkg (assoc pkg :commits (vec commits))]
             [pkg-name pkg]))
         32)
        packages))

(defn resolve-package-changes
  "For each package try find all commits that modified files in the package
  subdirectory since the last known version of the package.

  This works by finding commits since a tag constructed from the package name
  and version. See `k16.kmono.version/resolve-package-versions` for a
  description on how this tag is expected to be formatted.

  Any commits found will be appended to the packages `:commits` key."
  {:malli/schema [:-> :string core.schema/?PackageMap core.schema/?PackageMap]}
  [project-root packages]
  (-resolve-package-changes-since project-root
                                  packages
                                  (fn [pkg]
                                    (when (:version pkg)
                                      (create-package-version-tag pkg)))))

(defn resolve-package-changes-since
  "For each package try find all commits that modified files in the package
  subdirectory since the given rev.

  Any commits found will be appended to the packages `:commits` key."
  {:malli/schema [:-> :string :string core.schema/?PackageMap core.schema/?PackageMap]}
  [project-root rev packages]
  (-resolve-package-changes-since project-root
                                  packages
                                  (constantly rev)))

(defn package-changed?
  "A filter function designed to be used with `k16.kmono.core.graph/filter-by`.

  Filters a package based on whether it has any file changes since it's last
  version.

  This is determined by whether there are any commits associated with the
  package and is generally used in conjunction with
  `k16.kmono.version/resolve-package-changes`."
  [pkg]
  (seq (:commits pkg)))

(defn- version-changed? [packages-a packages-b pkg-name]
  (not= (get-in packages-a [pkg-name :version])
        (get-in packages-b [pkg-name :version])))

(defn- inc-dependent-packages [original-packages suffix packages]
  (reduce
   (fn [bumped [pkg-name]]
     (if (version-changed? original-packages bumped pkg-name)
       (let [dependents (core.graph/query-dependents original-packages pkg-name)]
         (reduce
          (fn [bumped dependent]
            (if-not (version-changed? original-packages bumped dependent)
              (update-in bumped
                         [dependent :version]
                         (fn [version]
                           (semver/inc-version version :patch suffix)))
              bumped))

          bumped
          dependents))
       bumped))

   packages
   packages))

(defn inc-package-versions
  "Increment each packages' semver version in the given `packages` map.

  This func will call the given `version-fn` with each respective package. The
  `version-fn` is responsible for determining how the version should be
  incremented by returning one of `[nil, :patch, :minor, :major]`.

  Generally this should be called after first associating a `:version` to each
  package using `resolve-package-versions` and a set of commits using
  `resolve-package-changes`. This information can be used to correctly
  determine the next version.

  Note that this fn will also inc dependent package versions if those dependent
  packages weren't themselves incremented. This is to ensure that changes made
  to a parent package result in dependent packages being incremented and
  published

  Example usage:

  ```clojure
  (require '[k16.kmono.version.alg.semantic :as semantic])

  (->> packages
       (resolve-package-versions project-root)
       (resolve-package-changes project-root)
       (inc-package-versions semantic/version-fn))
  ```

  This will determine the next version for each package according to
  semantic-commits."
  {:malli/schema
   [:function
    [:-> ?VersionFn core.schema/?PackageMap core.schema/?PackageMap]
    [:-> ?VersionFn [:maybe :string] core.schema/?PackageMap core.schema/?PackageMap]]}
  ([version-fn packages] (inc-package-versions version-fn nil packages))
  ([version-fn suffix packages]
   (->> packages
        (reduce
         (fn [packages [pkg-name pkg]]
           (let [inc-type (version-fn pkg)
                 current-version (or (:version pkg) "0.0.0")
                 version (if inc-type
                           (semver/inc-version
                            current-version
                            inc-type
                            suffix)
                           current-version)
                 pkg (assoc pkg :version version)]
             (assoc! packages pkg-name pkg)))
         (transient {}))
        persistent!

        (inc-dependent-packages packages suffix))))
