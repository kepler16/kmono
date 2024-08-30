(ns k16.kmono.core.config-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]
   [matcher-combinators.test]))

(use-fixtures :each with-test-repo)

(deftest resolves-default-workpace-config-test
  (is (= {:packages "packages/*"
          :group 'com.kepler16}
         (core.config/resolve-workspace-config *repo*))))

(deftest merge-with-local-test
  (fs/write-bytes (fs/file *repo* "deps.local.edn")
                  (.getBytes (prn-str {:kmono/workspace {:group 'a.b.c}})))
  (is (= {:packages "packages/*"
          :group 'a.b.c}
         (core.config/resolve-workspace-config *repo*))))

(deftest workspace-config-validation-test
  (fs/write-bytes (fs/file *repo* "deps.local.edn")
                  (.getBytes (prn-str {:kmono/workspace {:group 'a.b.c
                                                         :main-aliases "invalid"}})))
  (is (thrown-match? clojure.lang.ExceptionInfo
                     {:type :kmono/validation-error
                      :errors {:main-aliases ["invalid type"]}}
                     (core.config/resolve-workspace-config *repo*))))

(deftest package-config-test
  (is (match? {:group 'a}
              (core.config/resolve-package-config
               {:group 'a} (fs/file *repo* "packages/a")))))

(deftest package-validation-test
  (is (thrown-match? clojure.lang.ExceptionInfo
                     {:type :kmono/validation-error
                      :errors {:group ["missing required key"]}}
                     (core.config/resolve-package-config
                      {} (fs/file *repo* "packages/a")))))
