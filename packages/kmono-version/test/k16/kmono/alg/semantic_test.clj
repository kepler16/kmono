(ns k16.kmono.alg.semantic-test
  (:require
   [clojure.test :refer [deftest is]]
   [k16.kmono.version.alg.semantic :as semantic]))

(deftest empty-commits-test
  (let [pkg {:commits []}]
    (is (= nil (semantic/version-type pkg)))))

(deftest patch-test
  (let [pkg {:commits [{:message "fix: message"
                        :body ""}]}]
    (is (= :patch (semantic/version-type pkg)))))

(deftest minor-test
  (let [pkg {:commits [{:message "feat: message"
                        :body ""}]}]
    (is (= :minor (semantic/version-type pkg)))))

(deftest major-bang-test
  (let [pkg {:commits [{:message "feat!: message"
                        :body ""}]}]
    (is (= :major (semantic/version-type pkg)))))

(deftest major-bang-with-scope-test
  (let [pkg {:commits [{:message "feat(api)!: message"
                        :body ""}]}]
    (is (= :major (semantic/version-type pkg)))))

(deftest major-body-test
  (let [pkg {:commits [{:message "feat: message"
                        :body "The body\n\nBREAKING CHANGE: Changed API"}]}]
    (is (= :major (semantic/version-type pkg)))))

(deftest largest-type-test
  (let [pkg {:commits [{:message "fix: message"
                        :body ""}
                       {:message "feat: message"
                        :body ""}]}]

    (is (= :minor (semantic/version-type pkg)))))

(deftest non-semantic-commits-test
  (let [pkg {:commits [{:message "message"
                        :body ""}]}]
    (is (= nil (semantic/version-type pkg)))))