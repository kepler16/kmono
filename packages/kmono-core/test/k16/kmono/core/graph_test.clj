(ns k16.kmono.core.graph-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]
   [matcher-combinators.test]))

(use-fixtures :each with-test-repo)

(deftest no-cycles-test
  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)]

    (is (not (core.graph/find-cycles packages)))))

(deftest finds-cycles-test
  (fs/write-bytes (fs/file *repo* "packages/a/deps.edn")
                  (.getBytes (prn-str {:kmono/package {}
                                       :deps {'com.keperl16/b {:local/root "../b"}}})))

  (let [config (core.config/resolve-workspace-config *repo*)]

    (is (thrown-match?
         clojure.lang.ExceptionInfo
         {:type :kmono/circular-dependencies
          :cycles #{'com.kepler16/a 'com.kepler16/b}}
         (core.packages/resolve-packages *repo* config)))))

(deftest topological-sort-test
  (fs/create-dirs (fs/file *repo* "packages/c"))
  (fs/write-bytes (fs/file *repo* "packages/c/deps.edn")
                  (.getBytes (prn-str {:kmono/package {}
                                       :deps {}})))

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)]

    (is (= [['com.kepler16/a 'com.kepler16/c] ['com.kepler16/b]]
           (core.graph/parallel-topo-sort packages)))))

(deftest topological-sort-missing-dep-test
  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)]

    (is (= [['com.kepler16/b]]
           (core.graph/parallel-topo-sort (dissoc packages 'com.kepler16/a))))))

(deftest query-dependents-test
  (fs/create-dirs (fs/file *repo* "packages/c"))
  (fs/write-bytes (fs/file *repo* "packages/c/deps.edn")
                  (.getBytes (prn-str {:kmono/package {}
                                       :deps {'com.kepler16/b {:local/root "../b"}}})))

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)]

    (is (match? #{'com.kepler16/b 'com.kepler16/c}
                (core.graph/query-dependents packages 'com.kepler16/a)))

    (is (match? #{'com.kepler16/c}
                (core.graph/query-dependents packages 'com.kepler16/b)))

    (is (match? #{}
                (core.graph/query-dependents packages 'com.kepler16/c)))))

(deftest filter-packages-test
  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)]

    (is (match? {'com.kepler16/a {:dependents #{}}}
                (core.graph/filter-by #(= 'com.kepler16/a (:fqn %)) packages)))

(is (match? {'com.kepler16/b {:depends-on #{}}}
                (core.graph/filter-by #(= 'com.kepler16/b (:fqn %)) packages)))

    (is (= ['com.kepler16/b 'com.kepler16/a]
           (keys (core.graph/filter-by
                  #(= 'com.kepler16/a (:fqn %))
                  {:include-dependents true}
                  packages))))))
