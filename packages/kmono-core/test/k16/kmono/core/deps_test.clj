(ns k16.kmono.core.deps-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.deps :as core.deps]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]
   [matcher-combinators.test]
   [babashka.fs :as fs]))

(use-fixtures :each with-test-repo)

(deftest resolve-package-aliases-test
  (fs/write-bytes (fs/file *repo* "deps.local.edn")
                  (.getBytes (prn-str {:aliases {:local {:extra-paths ["local"]}}})))

  (let [config (core.config/resolve-workspace-config *repo*)

        packages (core.packages/resolve-packages *repo* config)

        aliases (core.deps/resolve-aliases *repo* packages)]

    (is (match? {:aliases {:local {:extra-paths ["local"]}}

                 :packages {:extra-deps {'com.kepler16/a {:local/root "packages/a"}
                                         'com.kepler16/b {:local/root "packages/b"}}}

                 :package-aliases {:a/test
                                   {:extra-paths ["packages/a/test"]
                                    :extra-deps {'local/excluded {:local/root "packages/excluded"}}}}}
                aliases))))

(deftest filter-package-aliases-test
  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)
        aliases (core.deps/resolve-aliases *repo* packages)]

    (is (= [:a/test]
           (keys (core.deps/filter-package-aliases (:package-aliases aliases) [:*/*]))))

    (is (= [:a/test]
           (keys (core.deps/filter-package-aliases (:package-aliases aliases) [:a/*]))))

    (is (= [:a/test]
           (keys (core.deps/filter-package-aliases (:package-aliases aliases) [:*/test]))))

    (is (= [:a/test]
           (keys (core.deps/filter-package-aliases (:package-aliases aliases) [:a/test]))))

    (is (not
         (keys (core.deps/filter-package-aliases (:package-aliases aliases) [:*/unknown]))))))
