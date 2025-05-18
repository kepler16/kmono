(ns build
  (:require
   [babashka.fs :as fs]
   [clojure.tools.build.api :as b]
   [k16.kaven.deploy :as kaven.deploy]
   [k16.kmono.build :as kmono.build]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.git.tags :as git.tags]))

(defn- get-latest-version [dir]
  (some (fn [tag]
          (when-let [version (second (re-matches #"v(.*)" tag))]
            version))
        (git.tags/get-sorted-tags dir)))

(defn- load-packages []
  (let [project-root (core.fs/find-project-root!)
        workspace-config (core.config/resolve-workspace-config project-root)
        packages (->> (core.packages/resolve-packages project-root workspace-config)
                      (core.graph/filter-by #(not (get-in % [:deps-edn :kmono/private]))))

        version (get-latest-version project-root)]

    (reduce
     (fn [packages [pkg-name pkg]]
       (assoc packages pkg-name (assoc pkg :version version)))
     {}
     packages)))

(defn build [_]
  (b/delete {:path "target"})

  (let [packages (load-packages)]
    (kmono.build/for-each-package packages
      (fn [pkg]
        (let [pkg-name (:fqn pkg)
              relative-path (:relative-path pkg)
              basis (kmono.build/create-basis packages pkg)

              class-dir (kmono.build/join (fs/cwd) "target/" relative-path "classes")
              jar-file (kmono.build/join (fs/cwd) "target/" relative-path "lib.jar")]

          (b/copy-dir {:src-dirs (:paths basis)
                       :target-dir class-dir})

          (b/write-pom {:class-dir class-dir
                        :lib pkg-name
                        :version (:version pkg)
                        :basis basis
                        :src-dirs (:paths basis)
                        :pom-data [[:description (get-in pkg [:deps-edn :kmono/description])]
                                   [:url "https://github.com/kepler16/kmono"]
                                   [:licenses
                                    [:license
                                     [:name "MIT"]
                                     [:url "https://opensource.org/license/mit"]]]]})

          (b/jar {:class-dir class-dir
                  :jar-file jar-file}))))))

(def ^:private clojars-credentials
  {:username (System/getenv "CLOJARS_USERNAME")
   :password (System/getenv "CLOJARS_PASSWORD")})

(defn release [_]
  (let [packages (core.graph/filter-by kmono.build/not-published? (load-packages))]
    (kmono.build/for-each-package packages
      (fn [{:keys [relative-path]}]
        (kaven.deploy/deploy
         {:jar-path (kmono.build/join (fs/cwd) "target/" relative-path "lib.jar")
          :repository {:id "clojars"
                       :credentials clojars-credentials}})))))

(defn build-cli [_]
  (b/delete {:path "target"})

  (b/with-project-root "packages/kmono-cli"
    (let [basis (b/create-basis {:aliases [:native]})
          class-dir (kmono.build/join (fs/cwd) "target/kmono-cli/classes")
          uber-dir (kmono.build/join (fs/cwd) "target/kmono-cli/cli.jar")]

      (b/copy-dir {:src-dirs (:paths basis)
                   :target-dir class-dir})

      (b/compile-clj
       {:basis basis
        :compile-opts {:direct-linking true}
        :ns-compile ['k16.kmono.cli.main]
        :class-dir class-dir})

      (b/uber
       {:class-dir class-dir
        :uber-file uber-dir
        :basis basis
        :main 'k16.kmono.cli.main}))))
