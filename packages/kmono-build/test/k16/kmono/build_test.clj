(ns k16.kmono.build-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.tools.build.api :as b]
   [k16.kmono.build :as kmono.build]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo]]
   [matcher-combinators.test]))

(use-fixtures :once with-test-repo)

(defn- load-packages []
  (let [config (core.config/resolve-workspace-config *repo*)
        packages (core.packages/resolve-packages *repo* config)]
    packages))

(deftest create-basis-replaces-local-deps-with-mvn-version-test
  (testing "replaces :local/root workspace libs with :mvn/version"
    (let [packages (load-packages)
          packages (assoc-in packages ['com.kepler16/a :version] "1.2.3")
          pkg-b (get packages 'com.kepler16/b)]
      (b/with-project-root (:absolute-path pkg-b)
        (let [basis (kmono.build/create-basis packages pkg-b)]
          (is (match? {'com.kepler16/a {:deps/manifest :mvn
                                        :mvn/version "1.2.3"}}
                      (:libs basis))))))))

(deftest create-basis-after-filter-by-test
  (testing "works when :depends-on has been cleared by filter-by"
    (let [packages (load-packages)
          packages (assoc-in packages ['com.kepler16/a :version] "1.2.3")
          pkg-b (assoc (get packages 'com.kepler16/b) :depends-on #{})]
      (b/with-project-root (:absolute-path pkg-b)
        (let [basis (kmono.build/create-basis packages pkg-b)]
          (is (match? {'com.kepler16/a {:deps/manifest :mvn
                                        :mvn/version "1.2.3"}}
                      (:libs basis)))))))

  (testing "leaves non-workspace libs unchanged"
    (let [packages (load-packages)
          packages (assoc-in packages ['com.kepler16/a :version] "1.2.3")
          pkg-b (get packages 'com.kepler16/b)]
      (b/with-project-root (:absolute-path pkg-b)
        (let [basis (kmono.build/create-basis packages pkg-b)]
          (is (match? {'org.clojure/clojure {:deps/manifest :mvn
                                             :mvn/version "1.12.0"}}
                      (:libs basis)))))))

  (testing "leaves workspace libs with no version unchanged"
    (let [packages (load-packages)
          pkg-b (get packages 'com.kepler16/b)]
      (b/with-project-root (:absolute-path pkg-b)
        (let [basis (kmono.build/create-basis packages pkg-b)]
          (is (match? {'com.kepler16/a {:deps/manifest :deps}}
                      (:libs basis)))
          (is (nil? (get-in basis [:libs 'com.kepler16/a :mvn/version]))))))))
