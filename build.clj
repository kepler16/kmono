(ns build
  (:require
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deps-deploy]
   [k16.kmono.build :as kmono.build]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.git.tags :as git.tags]))

(defn- load-packages []
  (let [project-root (core.fs/find-project-root)
        workspace-config (core.config/resolve-workspace-config project-root)

        version (->> (git.tags/get-sorted-tags project-root)
                     (map (fn [tag]
                            (second (re-matches #"v(.*)" tag))))
                     (remove nil?)
                     last)

        version (or version "0.0.0")]

    (->> (core.packages/resolve-packages project-root workspace-config)
         (reduce
          (fn [packages [pkg-name pkg]]
            (assoc packages pkg-name (assoc pkg :version version)))
          {}))))

(defn build [_]
  (b/delete {:path "target"})

  (let [packages (load-packages)]
    (kmono.build/exec
     "Building" packages
     (fn [pkg]
       (let [pkg-name (:fqn pkg)
             {:keys [basis paths]}
             (kmono.build/create-build-context packages pkg)

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
               :main (:main build)}))))))))

(defn release [_]
  (let [packages (load-packages)]
    (when release
      (kmono.build/exec
       "Releasing" packages
       (fn [pkg]
         (deps-deploy/deploy
          {:installer :remote
           :artifact (b/resolve-path (str "target/" (kmono.build/relativize pkg "lib.jar")))
           :pom-file (b/pom-path {:lib (:fqn pkg)
                                  :class-dir (str "target/" (kmono.build/relativize pkg "classes"))})}))))))
