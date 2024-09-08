(ns build
  (:require
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deps-deploy]
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
          ;; since their last version
          skip-unchanged (core.graph/filter-by kmono.version/package-changed?))]

    ;; Use semantic commits to increment package versions based on commits which
    ;; modified them since their last version.
    (kmono.version/inc-package-versions conventional-commits/version-fn packages)))

(defn build [opts]
  (b/delete {:path "target"})

  (let [packages (load-packages opts)]
    (kmono.build/for-each-package
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
                 :jar-file jar-file})))
     packages)))

(defn release [{:keys [repository] :as opts}]
  (let [project-root (core.fs/find-project-root)
        packages (load-packages opts)
        changed-packages (core.graph/filter-by kmono.build/not-published? packages)]
    (kmono.build/for-each-package
     (fn [pkg]
       ;; Release the package using deps-deploy
       (deps-deploy/deploy
        {:installer :remote
         :artifact (b/resolve-path "target/lib.jar")
         :pom-file (b/pom-path {:lib (:fqn pkg)
                                :class-dir (b/resolve-path "target/classes")})
         :repository repository})

       ;; Create tags after successfully releasing the package
       ;; This will require calling `git push --tags` separately but you could
       ;; just as easilly call it here too.
       (git.tags/create-tags
        project-root {:tags [(kmono.version/create-package-version-tag pkg)]}))

     {:title "Release"
      :concurrency 1}
     changed-packages)))
