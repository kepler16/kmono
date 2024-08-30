(ns build
  (:require
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deps-deploy]
   [k16.kmono.build :as kmono.build]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.git.commit :as git.commit]
   [k16.kmono.version :as kmono.version]
   [k16.kmono.version.alg.semantic :as version.semantic-commits]
   [k16.kmono.git.tags :as git.tags]))

(defn build-unreleased [_]
  (b/delete {:path "target"})

  (let [project-root (core.fs/find-project-root)
        workspace-config (core.config/resolve-workspace-config project-root)

        packages
        (->> (core.packages/resolve-packages project-root workspace-config)
             (kmono.version/with-package-versions project-root))

        changed-packages
        (core.graph/filter-by kmono.build/package-version-unpublished? packages)]

    (kmono.build/with-each-package changed-packages
      (fn [pkg]
        (let [pkg-name (:fqn pkg)
              {:keys [basis paths]}
              (kmono.build/create-build-context packages pkg)

              class-dir (str "target/" (kmono.build/relativize pkg "classes"))
              jar-file (str "target/" (kmono.build/relativize pkg "lib.jar"))]

          (b/copy-dir {:src-dirs paths
                       :target-dir class-dir})

          (b/write-pom
           {:class-dir class-dir
            :lib pkg-name
            :version (:version pkg)
            :basis basis})

          (b/jar {:class-dir class-dir
                  :jar-file jar-file}))))))

(defn build-normal [{:keys [snapshot]}]
  (b/delete {:path "target"})

  (let [project-root (core.fs/find-project-root)
        workspace-config (core.config/resolve-workspace-config project-root)

        packages
        (time
         (->> (core.packages/resolve-packages project-root workspace-config)
              (kmono.version/with-package-versions project-root)
              (kmono.version/with-package-changes project-root)
              (kmono.version/calculate-new-versions version.semantic-commits/semantic-commits)))

        changed-packages
        (time
         (->> packages
              (core.graph/filter-by (fn [package] (seq (:commits package))))
              (core.graph/filter-by kmono.build/package-version-unpublished?)))]

    (kmono.build/with-each-package changed-packages
      (fn [pkg]
        (let [pkg-name (:fqn pkg)
              {:keys [basis paths]}
              (kmono.build/create-build-context packages pkg)

              class-dir (str "target/" (kmono.build/relativize pkg "classes"))
              jar-file (str "target/" (kmono.build/relativize pkg "lib.jar"))]

          (b/copy-dir {:src-dirs paths
                       :target-dir class-dir})

          (b/write-pom
           {:class-dir class-dir
            :lib pkg-name
            :version (:version pkg)
            :basis basis})

          (b/jar {:class-dir class-dir
                  :jar-file jar-file}))))
    
    (git.tags/create-tags project-root [])
    ))

(defn build-snapshot [_]
  (b/delete {:path "target"})

  (let [project-root (core.fs/find-project-root)
        workspace-config (core.config/resolve-workspace-config project-root)
        sha (subs (git.commit/get-current-commit project-root) 0 7)

        packages
        (->> (core.packages/resolve-packages project-root workspace-config)
             (kmono.version/with-package-versions project-root)
             (kmono.version/with-package-changes project-root)
             (kmono.version/calculate-new-versions (fn [_pkg] :patch) (str sha "-SNAPSHOT")))

        changed-packages
        (->> packages
             (core.graph/filter-by (fn [package] (seq (:commits package))))
             (core.graph/filter-by kmono.build/package-version-unpublished?))]

    (kmono.build/with-each-package changed-packages
      (fn [pkg]
        (let [pkg-name (:fqn pkg)
              {:keys [basis paths]}
              (kmono.build/create-build-context packages pkg)

              class-dir (str "target/" (kmono.build/relativize pkg "classes"))
              jar-file (str "target/" (kmono.build/relativize pkg "lib.jar"))]

          (b/copy-dir {:src-dirs paths
                       :target-dir class-dir})

          (b/write-pom
           {:class-dir class-dir
            :lib pkg-name
            :version (:version pkg)
            :basis basis})

          (b/jar {:class-dir class-dir
                  :jar-file jar-file}))))))



(comment
  (build-snapshot nil))
