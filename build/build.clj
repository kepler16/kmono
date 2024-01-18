(ns build
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deps-deploy]))

(def basis (delay (b/create-basis {})))

(def kmono-config
  (-> (io/file "deps.edn")
      slurp
      edn/read-string
      :kmono/config))

(def lib (symbol (or (System/getenv "KMONO_PKG_NAME")
                     (str (:group kmono-config) "/" (:artifact kmono-config)))))
(def version (or (System/getenv "KMONO_PKG_VERSION") "0.0.0"))
(def class-dir "target/classes")
(def jar-file "target/lib.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn build [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :pom-data [[:description "Clojure monorepo tools"]
                           [:url "https://github.com/kepler16/kmono"]
                           [:licenses
                            [:license
                             [:name "MIT"]
                             [:url "https://opensource.org/license/mit"]]]]})

  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})

  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn release [_]
  (deps-deploy/deploy {:installer :remote
                       :artifact (b/resolve-path jar-file)
                       :pom-file (b/pom-path {:lib lib
                                              :class-dir class-dir})}))
