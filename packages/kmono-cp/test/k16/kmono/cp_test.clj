(ns k16.kmono.cp-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.cp :as kmono.cp]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]
   [matcher-combinators.test]))

(use-fixtures :once with-test-repo)

(deftest generate-cp-command-test
  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)

        cmd (kmono.cp/generate-classpath-command *repo* config packages)]

    (is (= "clojure -Sdeps '{:aliases {:a/test {:extra-paths [\"packages/a/test\"], :extra-deps #:local{excluded #:local{:root \"packages/excluded\"}}}, :kmono/packages {:extra-deps #:com.kepler16{a #:local{:root \"packages/a\"}, b #:local{:root \"packages/b\"}}}}}' -A:kmono/packages -Spath"
           cmd))))

(deftest generate-cp-command-with-aliases-test
  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)

        cmd (kmono.cp/generate-classpath-command
             *repo*
             (merge config
                    {:aliases [:local]})
             packages)]

    (is (= "clojure -Sdeps '{:aliases {:a/test {:extra-paths [\"packages/a/test\"], :extra-deps #:local{excluded #:local{:root \"packages/excluded\"}}}, :kmono/packages {:extra-deps #:com.kepler16{a #:local{:root \"packages/a\"}, b #:local{:root \"packages/b\"}}}}}' -A:kmono/packages:local -Spath"
           cmd))))
