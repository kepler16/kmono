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

(defn published? [package]
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

(defn not-published? [package]
  (not (published? package)))

(defn filter-by-published [packages]
  (core.graph/filter-by published? packages))

(defn relativize [package path]
  (str (fs/file (:relative-path package) path)))

(defn create-build-context [versioned-packages package]
  (let [dependencies
        (into
         {}
         (map (fn [pkg-name]
                (let [version (get-in versioned-packages [pkg-name :version])
                      dep
                      {:deps/manifest :mvn
                       :mvn/version version
                       :parents #{[]}
                       :paths []}]

                  [pkg-name dep])))
         (:depends-on package))

        basis
        (b/create-basis
         {:dir (:relative-path package)})

        libs
        (merge (:libs basis) dependencies)

        basis
        (assoc basis :libs libs)

        paths
        (map (partial relativize package) (:paths basis))]

    {:basis basis
     :paths paths
     :dependencies dependencies}))

(defn exec [title packages build-fn]
  (requiring-resolve 'clojure.tools.build.tasks.copy/copy)
  (requiring-resolve 'clojure.tools.build.tasks.write-pom/write-pom)
  (requiring-resolve 'clojure.tools.build.tasks.jar/jar)
  (requiring-resolve 'clojure.tools.build.tasks.create-basis/create-basis)

  (let [semaphore (Semaphore. 10)]
    (->> packages
         (mapv (fn [[pkg-name pkg]]
                 (future
                   (.acquire semaphore)
                   (log/info (str title " "
                                  (log.render/render-package-name pkg-name)
                                  "@|magenta  " (:version pkg) "|@"))

                   (try
                     (build-fn pkg)
                     (finally
                       (.release semaphore))))))
         (mapv deref))))
