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

(deftest workspace-test
  (testing "Packages should derive opts from workspace if not set"
    (let [p1-dir "packages/p1"
          p2-dir "packages/p2"]
      (spit (fs/file repo-root "deps.edn")
            (str {:kmono/workspace {:group "kmono-wp-test"
                                    :package-aliases [:*/test]
                                    :build-cmd "echo 'build root'"
                                    :release-cmd "echo 'release root'"}
                  :deps {}
                  :paths ["src"]}))
      (spit (fs/file repo-root p1-dir "deps.edn")
            (str {:kmono/package {:build-cmd "echo 'build p1'"
                                 :release-cmd "echo 'release p1'"}
                  :deps {}
                  :paths ["src"]}))

      (spit (fs/file repo-root p2-dir "deps.edn")
            (str {:kmono/package {:group "my-own-group"
                                 :build-cmd "echo 'build p2'"
                                 :release-cmd "echo 'release p2'"}
                  :deps {}
                  :paths ["src"]}))
      (let [config (config/load-config repo-root)]
        (is (= 2 (count (:packages config))))
        (is (= #{"my-own-group/p2" "kmono-wp-test/p1"}
               (->> config
                    :packages
                    (map :name)
                    (set))))))))

