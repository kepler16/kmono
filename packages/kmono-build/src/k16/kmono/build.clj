(ns k16.kmono.build
  (:require
   [babashka.fs :as fs]
   [clojure.tools.build.api :as b]
   [clojure.tools.deps.extensions :as deps.ext]
   [clojure.tools.deps.extensions.maven]
   [clojure.tools.deps.util.maven :as deps.util.maven]
   [clojure.tools.deps.util.session :as deps.util.session]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.schema :as core.schema]
   [k16.kmono.log :as log]
   [k16.kmono.log.render :as log.render])
  (:import
   java.util.concurrent.Semaphore))

(defn published?
  "A `predicate-fn` designed to be used with [[k16.kmono.core.graph/filter-by]].

  Checks whether a given `package` has been published to it's maven repository.
  The repositories from the packages `:mvn/deps` map will be used to run this
  check.

  Requires that the package has a `:version` set in order to perform a maven
  query.

  This fn will just return `false` if the package `:version` is not set."
  [package]
  (let [local-repo-override (str (fs/create-temp-dir))]
    (try
      (deps.util.session/with-session
        (let [versions (->> (deps.ext/find-versions
                             (:fqn package)
                             nil
                             :mvn {:mvn/local-repo local-repo-override
                                   :mvn/repos
                                   (merge deps.util.maven/standard-repos
                                          (get-in package [:deps-edn :mvn/repos]))})
                            (map :mvn/version)
                            (set))]
          (contains? versions (:version package))))
      (finally
        (fs/delete-tree local-repo-override)))))

(defn not-published?
  "The inverse of [[k16.kmono.build/published?]]."
  [package]
  (not (published? package)))

(defn join
  "Attempt to join together a given a series of paths.

  For example - given `(join \"/a/b/c\" \"../d\")` return `\"/a/b/d\"`"
  [& paths]
  (->> (apply fs/file paths)
       fs/normalize
       str))

(defn create-basis
  "Constructs a basis using [[clojure.tools.build.api/create-basis]] with a
  modified set of `:libs`.

  Any `:local/root` kmono dependencies within the basis are replaced with their
  respective `:mvn/version` coordinate derived from the given `packages` map.

  These `:libs` are then used to generate a correctly referenced `pom.xml` when
  using [[clojure.tools.build.api/write-pom]].

  Additional `opts` can be optionally provided and these will be given directly
  to `create-basis`."
  ([packages package] (create-basis packages package {}))
  ([packages package opts]
   (let [dependencies
         (into {}
               (comp
                (map (fn [pkg-name]
                       (let [version (get-in packages [pkg-name :version])
                             dep {:deps/manifest :mvn
                                  :mvn/version version
                                  :parents #{[]}
                                  :paths []}]
                         (when version
                           [pkg-name dep]))))
                (remove nil?))
               (:depends-on package))

         basis (b/create-basis opts)]

     (update basis :libs merge dependencies))))

(def ^:no-doc ?BuildOpts
  [:map
   [:concurrency {:optional true} :int]
   [:run-in-order {:optional true} :boolean]
   [:silent {:optional true} :boolean]])

(defn for-each-package
  "Execute a given `build-fn` for each package in the given `packages` map.

  Accepts an optional `opts` map containing:

  - **`:concurrency`** :int (default 4) - The maximum number of packages that
  can be executing at a time.
  - **`:run-in-order`** :boolean (default `true`) - Set this to false to run all
  packages concurrently ignoring their dependency order.
  - **`silent`** :boolean (default `false`) - Set this to true to disable
  logging the package name and version.

  The `build-fn` will be called with the `*project-root*` var (from
  `clojure.tools.build.api`) bound to the subdirectory of the relevant package.
  This allows using the various API's from `tools.build` under the assumption
  that all specified paths will be relative to the package subdirectory.

  TIP: Use the [[clojure.tools.build.api/resolve-path]] API to resolve a path
  relative to the current package dir.

  The `build-fn` will be called concurrently (up to max of `:concurrency` or
  `4`) but packages with dependencies will only be executed after each of their
  respective dependencies have run unless `:run-in-order` is `false`."
  {:style/indent :defn
   :malli/schema
   [:function
    [:-> core.schema/?PackageMap ifn? :any]
    [:-> core.schema/?PackageMap ?BuildOpts ifn? :any]]}
  ([packages build-fn] (for-each-package packages {} build-fn))
  ([packages {:keys [concurrency run-in-order silent]} build-fn]
   ;; This is a hack as calling tools.build API's concurrently is not
   ;; thread-safe due to them dynamically loading the internal namespaces
   ;; listed below.
   (requiring-resolve 'clojure.tools.build.tasks.copy/copy)
   (requiring-resolve 'clojure.tools.build.tasks.write-pom/write-pom)
   (requiring-resolve 'clojure.tools.build.tasks.jar/jar)
   (requiring-resolve 'clojure.tools.build.tasks.create-basis/create-basis)

   (let [exec-order (if (or (not (boolean? run-in-order))
                            run-in-order)
                      (core.graph/parallel-topo-sort packages)
                      [(keys packages)])

         semaphore (Semaphore. (or concurrency 4))]

     (loop [stages exec-order]
       (when (seq stages)
         (let [stage (first stages)

               op-procs
               (mapv
                (fn [pkg-name]
                  (future
                    (.acquire semaphore)

                    (try
                      (let [pkg (get packages pkg-name)]
                        (when-not silent
                          (log/info (str (log.render/render-package-name pkg-name)
                                         "@|magenta  " (:version pkg) "|@")))

                        (b/with-project-root (:relative-path pkg)
                          (build-fn pkg)))
                      (finally
                        (.release semaphore)))))
                stage)]

           ;; First deref everything, handling exceptions. This ensures that all
           ;; procs finish before any exceptions are thrown.
           ;;
           ;; Once all procs are complete then throw if any were exceptional.
           (->> op-procs
                (mapv (fn [proc]
                        (try
                          (deref proc)
                          (catch Exception ex ex))))
                (run! (fn [val]
                        (when (instance? Exception val)
                          (throw val)))))

           (recur (rest stages))))))))
