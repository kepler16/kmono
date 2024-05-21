(ns k16.kmono.config-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [k16.kmono.config :as config]
   [k16.kmono.test-utils :as test-utils :refer [repo-root]]))

(use-fixtures :each test-utils/repo-fixture)

(deftest load-config-test
  (let [config (config/load-config repo-root)]
    (testing "Should be 3 packages including root which :root-package? = true"
      (is (= {"kmono-test/p1" false
              "kmono-test/p2" false
              "kmono-test/root-module" true}
             (-> config
                 :package-map
                 (update-vals :root-package?)))))
    (testing "All packages are untracked"
      (is (= {"kmono-test/p1" "untracked"
              "kmono-test/p2" "untracked"
              "kmono-test/root-module" "untracked"}
             (-> config
                 :package-map
                 (update-vals :commit-sha)))))))

(deftest tracked-packages-test
  (test-utils/initialize-git!)
  (let [config (config/load-config repo-root)]
    (testing "All packages are tracked and has same sha"
      (is (not= #{"untracked"}
                (->> config
                     :package-map
                     (map :commit-sha)
                     (set)))))))

(deftest no-root-config-test
  (testing "Should ignore root package if there is no kmono config found"
    (spit (fs/file repo-root "deps.edn")
          (str {:deps {}
                :paths ["src"]}))
    (let [config (config/load-config repo-root)]
      (is (= {"kmono-test/p1" false
              "kmono-test/p2" false}
             (-> config
                 :package-map
                 (update-vals :root-package?)))))))
