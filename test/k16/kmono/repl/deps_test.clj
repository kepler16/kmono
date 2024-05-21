(ns k16.kmono.repl.deps-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [k16.kmono.config :as config]
   [k16.kmono.main :as main]
   [k16.kmono.repl.deps :as repl.deps]))

(deftest parse-aliases-test
  (testing "Aliases should be parsed correctly"
    (is (= [:foo :kmono.pkg/bar]
           (main/parse-aliases ":foo:kmono.pkg/bar")))))

(deftest some-test
  (let [config (config/load-config "test/fixtures/example_repo")]
    (testing "Should extract aliases and relativize paths"
      (is (= '{:aliases
              {:kmono/package-deps
               {:extra-deps {kepler16/bar-lib
                             {:local/root
                              "test/fixtures/example_repo/packages/bar"},
                             kepler16/foo-lib
                             {:local/root
                              "test/fixtures/example_repo/packages/foo"}}}
               :kmono.pkg/bar.test
               {:extra-deps {some/dependency {:mvn/version "1.0.0"}}
                :extra-paths ["packages/bar/test"]}
               :kmono.pkg/foo.test
               {:extra-deps {kepler16/bar {:local/root "packages/bar"}}
                :extra-paths ["packages/foo/test"]}}}
             (repl.deps/construct-sdeps-overrides! config [:bar/test
                                                           :foo/test])))
      (is (= '{:aliases
              {:kmono/package-deps
               {:extra-deps {kepler16/bar-lib
                             {:local/root
                              "test/fixtures/example_repo/packages/bar"},
                             kepler16/foo-lib
                             {:local/root
                              "test/fixtures/example_repo/packages/foo"}}}
               :kmono.pkg/bar.test
               {:extra-deps {some/dependency {:mvn/version "1.0.0"}}
                :extra-paths ["packages/bar/test"]}
               :kmono.pkg/foo.test
               {:extra-deps {kepler16/bar {:local/root "packages/bar"}}
                :extra-paths ["packages/foo/test"]}}}
             (repl.deps/construct-sdeps-overrides! config [:*/test]))))))
