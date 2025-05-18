(ns k16.kmono.git.tag-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.git :as git]
   [k16.kmono.git.commit :as git.commit]
   [k16.kmono.git.tags :as git.tags]
   [k16.kmono.test.helpers.commit :refer [commit]]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]))

(use-fixtures :each with-test-repo)

(deftest query-ordered-tags
  (let [initial-commit (git.commit/get-current-commit *repo*)]
    ;; Git commit timestamps have a 1s resolution. To get stable sorting we need
    ;; to wait at least 1s.
    (Thread/sleep 1000)

    (git.tags/create-tags *repo* {:ref initial-commit
                                  :tags ["a"]})

    (commit *repo* "change-1")

    (git.tags/create-tags *repo* {:ref (git.commit/get-current-commit *repo*)
                                  :tags ["b-1"]})

    (git/run-cmd! *repo* "git" "checkout" initial-commit)

    (commit *repo* "change-2")

    (git.tags/create-tags *repo* {:ref (git.commit/get-current-commit *repo*)
                                  :tags ["b-2"]})

    (let [tags (git.tags/get-sorted-tags *repo* (git.commit/get-current-commit *repo*))]

      (is (= ["b-2" "a"] tags)))))
