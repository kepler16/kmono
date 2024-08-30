(ns k16.kmono.git.files-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.git.commit :as git.commit]
   [k16.kmono.git.files :as git.files]
   [k16.kmono.test.helpers.commit :refer [commit]]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]))

(use-fixtures :each with-test-repo)

(defn prepare-repo []
  (let [root-commit (git.commit/get-current-commit *repo*)

        _ (fs/create-file (fs/file *repo* "packages/a/change-1"))
        _ (fs/create-file (fs/file *repo* "packages/b/change-1"))

        commit-1 (commit *repo* "change-1")

        _ (fs/create-file (fs/file *repo* "packages/a/change-2"))
        _ (fs/create-file (fs/file *repo* "packages/b/change-2"))

        _ (commit *repo* "change-2")]

    [root-commit commit-1]))

(deftest query-files-since-ref
  (let [[root-commit commit-1] (prepare-repo)

        files-since-root
        (git.files/find-changed-files-since
         *repo* {:ref root-commit})

        files-since-change-1
        (git.files/find-changed-files-since
         *repo* {:ref commit-1})]

    (is (= ["packages/a/change-1"
            "packages/a/change-2"
            "packages/b/change-1"
            "packages/b/change-2"]
           files-since-root))

    (is (= ["packages/a/change-2"
            "packages/b/change-2"]
           files-since-change-1))))

(deftest query-files-since-ref-in-subdir
  (let [[root-commit commit-1] (prepare-repo)

        files-since-root-a
        (git.files/find-changed-files-since
         *repo* {:ref root-commit
                 :subdir "packages/a"})

        files-since-change-1-b
        (git.files/find-changed-files-since
         *repo* {:ref commit-1
                 :subdir "packages/b"})]

    (is (= ["packages/a/change-1"
            "packages/a/change-2"]
           files-since-root-a))

    (is (= ["packages/b/change-2"]
           files-since-change-1-b))))
