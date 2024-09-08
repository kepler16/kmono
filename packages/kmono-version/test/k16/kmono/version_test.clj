(ns k16.kmono.version-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.git.tags :as git.tags]
   [k16.kmono.test.helpers.commit :refer [commit]]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]
   [k16.kmono.version :as kmono.version]
   [k16.kmono.version.alg.semantic :as semantic]
   [matcher-combinators.test]))

(use-fixtures :each with-test-repo)

(deftest no-package-versions-test
  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)]
    (is (match? {'com.kepler16/a {:version nil}
                 'com.kepler16/b {:version nil}}
                (kmono.version/resolve-package-versions *repo* packages)))))

(deftest load-package-versions-test
  (git.tags/create-tags *repo* {:tags ["com.kepler16/a@1.0.0"
                                       "com.kepler16/b@1.1.0"]})

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)]
    (is (match? {'com.kepler16/a {:version "1.0.0"}
                 'com.kepler16/b {:version "1.1.0"}}
                (kmono.version/resolve-package-versions *repo* packages)))))

(deftest load-package-changes-test
  (git.tags/create-tags *repo* {:tags ["com.kepler16/a@1.0.0"
                                       "com.kepler16/b@1.1.0"]})

  (fs/create-file (fs/file *repo* "packages/a/change-1"))
  (commit *repo* "fix: changed package a")

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-versions *repo*)
                      (kmono.version/resolve-package-changes *repo*))]
    (is (match? {'com.kepler16/a {:version "1.0.0"
                                  :commits [{:message "fix: changed package a"
                                             :body ""}]}
                 'com.kepler16/b {:version "1.1.0"
                                  :commits []}}
                packages))))

(deftest load-package-changes-no-version-test

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-changes *repo*))]
    (is (match? {'com.kepler16/a {:commits [{:message "init"
                                             :body ""}]}
                 'com.kepler16/b {:commits [{:message "init"
                                             :body ""}]}}
                packages))))

(deftest inc-package-versions-test
  (git.tags/create-tags *repo* {:tags ["com.kepler16/a@1.0.0"
                                       "com.kepler16/b@1.1.0"]})

  (fs/create-file (fs/file *repo* "packages/b/change-1"))
  (commit *repo* "fix: changed package b")

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-versions *repo*)
                      (kmono.version/resolve-package-changes *repo*)
                      (kmono.version/inc-package-versions semantic/version-fn))]
    (is (match? {'com.kepler16/a {:version "1.0.0"}
                 'com.kepler16/b {:version "1.1.1"}}
                packages))))

(deftest inc-dependent-package-versions-test
  (git.tags/create-tags *repo* {:tags ["com.kepler16/a@1.0.0"
                                       "com.kepler16/b@1.1.0"]})

  (fs/create-file (fs/file *repo* "packages/a/change-1"))
  (commit *repo* "fix: changed package a")

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-versions *repo*)
                      (kmono.version/resolve-package-changes *repo*)
                      (kmono.version/inc-package-versions semantic/version-fn))]
    (is (match? {'com.kepler16/a {:version "1.0.1"}
                 'com.kepler16/b {:version "1.1.1"}}
                packages))))
