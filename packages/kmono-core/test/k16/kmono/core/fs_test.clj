(ns k16.kmono.core.fs-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]))

(use-fixtures :once with-test-repo)

(deftest stops-at-workspace-root-test
  (is (= *repo* (core.fs/find-project-root *repo*))))

(deftest stops-at-workspace-root-from-subdir-test
  (is (= *repo* (core.fs/find-project-root (fs/file *repo* "packages/a")))))

(deftest stops-at-homedir-test
  (is (= nil (core.fs/find-project-root (fs/file (fs/home) "some-subdir")))))
