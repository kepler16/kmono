(ns k16.kmono.changes-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.git.commit :refer [get-current-commit]]
   [k16.kmono.git.tags :as git.tags]
   [k16.kmono.test.helpers.commit :refer [commit]]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]
   [k16.kmono.version :as kmono.version]
   [matcher-combinators.test]))

(use-fixtures :each with-test-repo)

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

(deftest load-package-changes-since-rev-test
  (let [start-commit (get-current-commit *repo*)

        _ (fs/create-file (fs/file *repo* "packages/b/change-1"))

        next-commit (commit *repo* "Change package b")

        config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-changes-since *repo* start-commit))]

    (is (match? {'com.kepler16/a {:commits []}
                 'com.kepler16/b {:commits [{:sha next-commit
                                             :message "Change package b"
                                             :body ""}]}}
                packages))))
