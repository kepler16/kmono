(ns k16.kmono.build
  (:require
   [babashka.fs :as fs]
   [clojure.tools.build.api :as b]
   [clojure.tools.deps.extensions :as deps.ext]
   [clojure.tools.deps.extensions.maven]
   [clojure.tools.deps.util.maven :as deps.util.maven]
   [clojure.tools.deps.util.session :as deps.util.session]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.log :as log]
   [k16.kmono.log.render :as log.render])
  (:import
   java.util.concurrent.Semaphore))

(defn published?
  "A filter function designed to be used with `k16.kmono.core.graph/filter-by`.

  Checks whether a given package has been published to it's maven repository.
  The repositories from the packages' `:mvn/deps` map will be used to run this
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
  "The inverse of `k16.kmono.build/published?`."
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
  "Constructs a basis using `clojure.tools.build.api/create-basis` with a
  modified set of `:libs`.

  Any `:local/root` kmono dependencies within the basis are replaced with their
  respective `:mvn/version` coordinate derived from the given `packages` map.

  These `:libs` are then used to generate a correctly referenced `pom.xml` when
  using `clojure.tools.build.api/write-pom`."
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

(defn for-each-package
  "Execute a given `build-fn` for each package in the given `packages` map.

  The `build-fn` will be called with the *project-root* var bound to the
  subdirectory of the relevant package. This allows using the various API's from
  `tools.build` under the assumption that all specified paths will be relative
  to the package subdirectory.

  The `build-fn` will be called concurrently (up to max of `:concurrency` or
  `4`) but packages with dependencies will only be executed after each of their
  respective dependencies have run."
  ([build-fn packages] (for-each-package build-fn {:title "Build"} packages))
  ([build-fn {:keys [title concurrency ordered]} packages]
   ;; This is a hack as calling tools.build API's concurrently is not
   ;; thread-safe due to them dynamically loading the internal namespaces
   ;; listed below.
   (requiring-resolve 'clojure.tools.build.tasks.copy/copy)
   (requiring-resolve 'clojure.tools.build.tasks.write-pom/write-pom)
   (requiring-resolve 'clojure.tools.build.tasks.jar/jar)
   (requiring-resolve 'clojure.tools.build.tasks.create-basis/create-basis)

   (let [exec-order (if (or (not (boolean? ordered))
                            ordered)
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
                        (log/info (str title " "
                                       (log.render/render-package-name pkg-name)
                                       "@|magenta  " (:version pkg) "|@"))
                        (b/with-project-root (:relative-path pkg)
                          (build-fn pkg)))
                      (finally
                        (.release semaphore)))))
                stage)]

           (mapv deref op-procs)
           (recur (rest stages))))))))
