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
   [k16.kmono.git.tags :as git.tags]
   [k16.kmono.version :as kmono.version]
   [k16.kmono.version.alg.semantic :as semantic-commits]))

(defn build [{:keys [snapshot release]}]
  (b/delete {:path "target"})

  (let [project-root (core.fs/find-project-root)
        workspace-config (core.config/resolve-workspace-config project-root)

        sha (git.commit/get-current-commit-short project-root)

        packages
        (->> (core.packages/resolve-packages project-root workspace-config)
             (core.graph/filter-by #(not (get-in % [:deps-edn :kmono/private])))
             (kmono.version/resolve-package-versions project-root)
             (kmono.version/resolve-package-changes project-root))

        suffix (when snapshot (str sha "-SNAPSHOT"))

        versioned-packages
        (kmono.version/inc-package-versions
         semantic-commits/version-type
         suffix
         packages)

        changed-packages
        (->> versioned-packages
             (core.graph/filter-by #(seq (:commits %)))
             (core.graph/filter-by kmono.build/not-published?))]

    (kmono.build/exec
     "Building" changed-packages
     (fn [pkg]
       (let [pkg-name (:fqn pkg)
             {:keys [basis paths]}
             (kmono.build/create-build-context
              versioned-packages pkg)

             class-dir (str "target/" (kmono.build/relativize pkg "classes"))
             jar-file (str "target/" (kmono.build/relativize pkg "lib.jar"))]

         (b/copy-dir {:src-dirs paths
                      :target-dir class-dir})

         (b/write-pom {:class-dir class-dir
                       :lib pkg-name
                       :version (:version pkg)
                       :basis basis})

         (b/jar {:class-dir class-dir
                 :jar-file jar-file})

         (when-let [build (get-in pkg [:deps-edn :kmono/build])]
           (let [basis (b/create-basis
                        {:dir (:relative-path pkg)
                         :aliases (:aliases build)})]
             (b/compile-clj
              {:basis basis
               :compile-opts {:direct-linking true}
               :ns-compile [(:main build)]
               :class-dir class-dir})

             (b/uber
              {:class-dir class-dir
               :uber-file (str "target/" (kmono.build/relativize pkg (:file build)))
               :basis basis
               :main (:main build)}))))))

    (when release
      (kmono.build/exec
       "Releasing" changed-packages
       (fn [pkg]
         (deps-deploy/deploy
          {:installer :remote
           :artifact (b/resolve-path (str "target/" (kmono.build/relativize pkg "lib.jar")))
           :pom-file (b/pom-path {:lib (:fqn pkg)
                                  :class-dir (str "target/" (kmono.build/relativize pkg "classes"))})})))

      (git.tags/create-tags
       project-root
       (map
        (fn [pkg]
          (str (:fqn pkg) "@" (:version pkg)))
        changed-packages)))))
