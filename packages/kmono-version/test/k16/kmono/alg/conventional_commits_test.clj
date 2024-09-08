(ns k16.kmono.alg.conventional-commits-test
  (:require
   [clojure.test :refer [deftest is]]
   [k16.kmono.version.alg.conventional-commits :as conventional-commits]))

(deftest empty-commits-test
  (let [pkg {:commits []}]
    (is (= nil (conventional-commits/version-fn pkg)))))

(deftest patch-test
  (let [pkg {:commits [{:message "fix: message"
                        :body ""}]}]
    (is (= :patch (conventional-commits/version-fn pkg)))))

(deftest minor-test
  (let [pkg {:commits [{:message "feat: message"
                        :body ""}]}]
    (is (= :minor (conventional-commits/version-fn pkg)))))

(deftest major-bang-test
  (let [pkg {:commits [{:message "feat!: message"
                        :body ""}]}]
    (is (= :major (conventional-commits/version-fn pkg)))))

(deftest major-bang-with-scope-test
  (let [pkg {:commits [{:message "feat(api)!: message"
                        :body ""}]}]
    (is (= :major (conventional-commits/version-fn pkg)))))

(deftest major-body-test
  (let [pkg {:commits [{:message "feat: message"
                        :body "The body\n\nBREAKING CHANGE: Changed API"}]}]
    (is (= :major (conventional-commits/version-fn pkg)))))

(deftest largest-type-test
  (let [pkg {:commits [{:message "fix: message"
                        :body ""}
                       {:message "feat: message"
                        :body ""}]}]

    (is (= :minor (conventional-commits/version-fn pkg)))))

(deftest non-semantic-commits-test
  (let [pkg {:commits [{:message "message"
                        :body ""}]}]
    (is (= nil (conventional-commits/version-fn pkg)))))
