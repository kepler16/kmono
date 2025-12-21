(ns k16.kmono.git-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.git.commit :as git.commit]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]))

(use-fixtures :each with-test-repo)

(deftest open-repo-subdir-test
  (let [dir (fs/file *repo* "subdir")]
    (fs/create-dir dir)
    (is (string? (git.commit/get-current-commit (str dir))))))
