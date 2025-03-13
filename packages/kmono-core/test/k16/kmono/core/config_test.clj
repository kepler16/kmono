(ns k16.kmono.core.config-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]
   [matcher-combinators.test]))

(use-fixtures :each with-test-repo)

(deftest resolves-default-workpace-config-test
  (is (= {:packages "packages/**"
          :group 'com.kepler16}
         (core.config/resolve-workspace-config *repo*))))

(deftest merge-with-local-test
  (fs/write-bytes (fs/file *repo* "deps.local.edn")
                  (.getBytes (prn-str {:kmono/workspace {:group 'a.b.c}})))
  (is (= {:packages "packages/**"
          :group 'a.b.c}
         (core.config/resolve-workspace-config *repo*))))

(deftest workspace-config-validation-test
  (fs/write-bytes (fs/file *repo* "deps.local.edn")
                  (.getBytes (prn-str {:kmono/workspace {:group 'a.b.c
                                                         :aliases "invalid"}})))
  (is (thrown-match? clojure.lang.ExceptionInfo
                     {:type :kmono/validation-error
                      :errors {:aliases ["invalid type"]}}
                     (core.config/resolve-workspace-config *repo*))))

(deftest package-config-test
  (is (match? {:deps-edn {}}
              (core.config/resolve-package-config (fs/file *repo* "packages/a")))))

(deftest package-validation-test
  (fs/write-bytes (fs/file *repo* "packages/a/deps.edn")
                  (.getBytes (prn-str {:kmono/package {:group "invalid"}})))
  (is (thrown-match? clojure.lang.ExceptionInfo
                     {:type :kmono/validation-error
                      :errors {:group ["should be a symbol"]}}
                     (core.config/resolve-package-config
                      (fs/file *repo* "packages/a")))))
