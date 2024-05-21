(ns k16.kmono.api-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [k16.kmono.api :as api]
   [k16.kmono.repl.deps :as repl.deps]
   [k16.kmono.test-utils :as test-utils :refer [repo-root]]))

(use-fixtures :each test-utils/repo-fixture)

(deftest run-build-test
  (test-utils/initialize-git!)
  (testing "Build should be successful"
    (is (= [true
            [{"kmono-test/p2" {:success? true,
                               :output "build p2\n"},
              "kmono-test/p1" {:success? true,
                               :output "build p1\n"},
              "kmono-test/root-module" {:success? true,
                                        :output "build root\n"}}]]
           (api/-run {:repo-root repo-root
                      :exec :build}
                     nil)))))

(deftest run-release-test
  (test-utils/initialize-git!)
  (let [release-opts {:repo-root repo-root
                      :create-tags? true
                      :include-unchanged? false
                      :snapshot? false
                      :exec :release}]
    (testing "Release + create initial tags"
      (is (= [true
              [{"kmono-test/p2" {:success? true,
                                 :output "release p2\n"},
                "kmono-test/p1" {:success? true,
                                 :output "release p1\n"},
                "kmono-test/root-module" {:success? true,
                                          :output "release root\n"}}]]
             (api/-run release-opts nil)))
      (is (= #{"kmono-test/p1@0.0.0.0"
               "kmono-test/p2@0.0.0.0"
               "kmono-test/root-module@0.0.0.0"}
             (set (test-utils/get-tags)))))

    (testing "Make feat changes to p1 and release again"
      (spit (fs/file repo-root "packages/p1/src/foo.clj")
            "(ns foo)\n(println :hello)")
      (Thread/sleep 100)
      (test-utils/shell-commands! ["git add ."
                                   "git commit -m 'feat: p1 foo added'"])
      (is (= [true
              [{"kmono-test/p2" {:success? true, :output "no changes"},
                "kmono-test/root-module" {:success? true, :output "no changes"},
                "kmono-test/p1" {:success? true, :output "release p1\n"}}]]
             (api/-run release-opts nil)))
      (is (= #{"kmono-test/p1@0.1.0.0"
               "kmono-test/p1@0.0.0.0"
               "kmono-test/p2@0.0.0.0"
               "kmono-test/root-module@0.0.0.0"}
             (set (test-utils/get-tags)))))

    (testing "Make fix changes to root, p2 and release"
      (spit (fs/file repo-root "packages/p2/src/bar.clj")
            "(ns bar)\n(println :hello_bar)")
      (spit (fs/file repo-root "src/lol.clj") "(ns lol)")
      (Thread/sleep 100)
      (test-utils/shell-commands! ["git add ."
                                   "git commit -m 'fix: root and p2 bugs'"])
      (is (= [true
              [{"kmono-test/p2" {:success? true,
                                 :output "release p2\n"},
                "kmono-test/root-module" {:success? true,
                                          :output "release root\n"},
                "kmono-test/p1" {:success? true,
                                 :output "no changes"}}]]
             (api/-run release-opts nil)))
      (is (= #{"kmono-test/p2@0.0.1.0"
               "kmono-test/root-module@0.0.0.0"
               "kmono-test/p1@0.0.0.0"
               "kmono-test/p1@0.1.0.0"
               "kmono-test/p2@0.0.0.0"
               "kmono-test/root-module@0.0.1.0"}
             (set (test-utils/get-tags)))))))

(deftest root-only-test
  (fs/delete-tree (fs/file repo-root "packages"))
  (test-utils/initialize-git!)
  (let [release-opts {:repo-root repo-root
                      :create-tags? true
                      :include-unchanged? false
                      :snapshot? false
                      :exec :release}]
    (testing "Release + create initial tags"
      (is (= [true
              [{"kmono-test/root-module" {:success? true,
                                          :output "release root\n"}}]]
             (api/-run release-opts nil)))
      (is (= #{"kmono-test/root-module@0.0.0.0"}
             (set (test-utils/get-tags)))))))

(deftest cp-workplace-test
  (let [release-opts {:repo-root repo-root}]
    (spit (fs/file repo-root "deps.edn")
          (str {:kmono/workspace {:group "kmono-wp-test"
                                  :glob "packages/*"
                                  :aliases [:dev]
                                  :package-aliases [:*/test]
                                  :build-cmd "echo 'build root'"
                                  :release-cmd "echo 'release root'"}
                :deps {}
                :paths ["src"]}))
    (testing "Derive params from workspace"
      (with-redefs [repl.deps/cp! (fn [{:keys [package-aliases
                                               aliases]}
                                       sdeps-overrides]
                                    (is (= [:kmono/package-deps
                                            :kmono.pkg/p2.test
                                            :kmono.pkg/p1.test]
                                           package-aliases))
                                    (is (= [:dev] aliases)))]
        (api/generate-classpath! release-opts nil)))))
