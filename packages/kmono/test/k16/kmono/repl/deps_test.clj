(ns k16.kmono.repl.deps-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [k16.kmono.config :as config]
   [k16.kmono.repl.deps :as repl.deps]))

(deftest some-test
  (let [config (config/load-config "test/fixtures/example_repo")]
    (testing "Should extract aliases and relativize paths"
      (is (= '{:aliases
               {:bar/test {:extra-deps {some/dependency {:mvn/version "1.0.0"}}
                           :extra-paths ["packages/bar/test"]}
                :foo/test {:extra-deps {kepler16/bar {:local/root "packages/bar"}}
                           :extra-paths ["packages/foo/test"]}}}
             (repl.deps/construct-sdeps-overrides! config [:bar/test
                                                           :foo/test]))))))
