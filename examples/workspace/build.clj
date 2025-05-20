(ns build
  (:require
   [clojure.tools.build.api :as b]
   [k16.kaven.deploy :as kaven.deploy]
   [k16.kmono.build :as kmono.build]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.git.tags :as git.tags]
   [k16.kmono.version :as kmono.version]
   [k16.kmono.version.alg.conventional-commits :as conventional-commits]))

(defn load-packages [{:keys [skip-unchanged]}]
  (let [project-root (core.fs/find-project-root)
        workspace-config (core.config/resolve-workspace-config project-root)

        packages
        (cond->> (->> (core.packages/resolve-packages project-root workspace-config)
                      (kmono.version/resolve-package-versions project-root)
                      (kmono.version/resolve-package-changes project-root))

          ;; Only perform a build and/or release for packages which have changed
          ;; since their last version; or packages whose dependencies have
          ;; changed.
          skip-unchanged (core.graph/filter-by kmono.version/package-changed?
                                               {:include-dependents true}))]

    ;; Use semantic commits to increment package versions based on commits which
    ;; modified them since their last version.
    (kmono.version/inc-package-versions conventional-commits/version-fn packages)))

(defn build [opts]
  (b/delete {:path "target"})

  (let [packages (load-packages opts)]
    (kmono.build/for-each-package packages
      (fn [pkg]
        (let [;; Create a basis with all local kmono libs replaced with their
              ;; respective :mvn/version coordinate
              {:keys [basis paths]} (kmono.build/create-basis packages pkg)

              pkg-name (:fqn pkg)
              class-dir "target/classes"
              jar-file "target/lib.jar"]

          (b/copy-dir {:src-dirs paths
                       :target-dir class-dir})

          (b/write-pom
           {:class-dir class-dir
            :lib pkg-name
            :version (:version pkg)
            :basis basis})

          (b/jar {:class-dir class-dir
                  :jar-file jar-file}))))))

(defn release [opts]
  (let [project-root (core.fs/find-project-root)
        packages (load-packages opts)
        changed-packages (core.graph/filter-by kmono.build/not-published? packages)]
    (kmono.build/for-each-package changed-packages
      (fn [pkg]
        ;; Release the package using com.kepler16/kaven
        (kaven.deploy/deploy
         {:jar-path (b/resolve-path "target/lib.jar")
          :repository "clojars"})

        ;; Create tags after successfully releasing the package.
        ;; 
        ;; This will require calling `git push --tags` separately but you could
        ;; just as easily call it here too.
        (git.tags/create-tags
         project-root {:tags [(kmono.version/create-package-version-tag pkg)]})))))
