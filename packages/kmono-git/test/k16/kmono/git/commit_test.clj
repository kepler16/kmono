(ns k16.kmono.git.commit-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.git.commit :as git.commit]
   [k16.kmono.test.helpers.commit :refer [commit]]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]
   [matcher-combinators.test]))

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

(deftest query-commits-since-ref
  (let [[root-commit commit-1] (prepare-repo)

        files-since-root
        (git.commit/find-commits-since
         *repo* {:ref root-commit})

        files-since-change-1
        (git.commit/find-commits-since
         *repo* {:ref commit-1})]

    (is (match? [#"[a-f0-9]{40}"
                 #"[a-f0-9]{40}"]
                files-since-root))

    (is (match? [#"[a-f0-9]{40}"]
                files-since-change-1))))

(deftest query-commits-since-ref-in-subdir
  (let [[root-commit commit-1] (prepare-repo)

        files-since-root-a
        (git.commit/find-commits-since
         *repo* {:ref root-commit
                 :subdir "packages/a"})

        files-since-change-1-b
        (git.commit/find-commits-since
         *repo* {:ref commit-1
                 :subdir "packages/b"})]

    (is (match? [#"[a-f0-9]{40}"
                 #"[a-f0-9]{40}"]
                files-since-root-a))

    (is (match? [#"[a-f0-9]{40}"]
                files-since-change-1-b))))

(deftest query-commit-message
  (let [commit-sha (commit *repo* "this is the message\nthis is\nthe\nbody")
        commit (git.commit/get-commit-details *repo* commit-sha)]

    (is (match? {:message "this is the message"
                 :body "this is\nthe\nbody"}
                commit))))
