(ns k16.kmono.core.packages-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]
   [matcher-combinators.test]))

(use-fixtures :each with-test-repo)

(deftest load-packages-test
  (let [config (core.config/resolve-workspace-config *repo*)]
    (is (match? {'com.kepler16/a {:group 'com.kepler16
                                  :name 'a
                                  :fqn 'com.kepler16/a

                                  :deps-edn {}
                                  :depends-on #{}
                                  :dependents #{'com.kepler16/b}

                                  :absolute-path (str (fs/file *repo* "packages/a"))
                                  :relative-path "packages/a"}
                 'com.kepler16/b {:group 'com.kepler16
                                  :name 'b
                                  :fqn 'com.kepler16/b

                                  :deps-edn {:deps {'a/b {:mvn/version "RELEASE"}
                                                    'local/excluded {:local/root "../excluded"}
                                                    'com.kepler16/a {:local/root "../a"}}}
                                  :depends-on #{'com.kepler16/a}
                                  :dependents #{}

                                  :absolute-path (str (fs/file *repo* "packages/b"))
                                  :relative-path "packages/b"}}
                (core.packages/resolve-packages *repo* config)))))
