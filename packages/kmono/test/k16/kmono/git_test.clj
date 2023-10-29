(ns k16.kmono.git-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [k16.kmono.git :as git]))

(def config
  {:packages [{:name "Z"}
              {:name "B"}
              {:name "C"}
              {:name "D"}]
   :graph {"Z" #{}
           "D" #{"Z"}
           "B" #{"Z"}
           "C" #{"B"}}})

(deftest dependands-build-test
  (testing "It should bump and rebuild all dependands"
    (let [changes {"Z" {:changed? false
                        :package-name "Z"
                        :version "1.0.0.0"}
                   "B" {:changed? true
                        :package-name "B"
                        :version "1.0.0.0"}
                   "C" {:changed? false
                        :package-name "C"
                        :version "1.0.0.0"}
                   "D" {:changed? false
                        :package-name "D"
                        :version "1.0.0.0"}}]
      (is (= {"Z" {:changed? false, :package-name "Z", :version "1.0.0.0"},
              "B" {:changed? true, :package-name "B", :version "1.0.0.0"},
              "C" {:changed? true, :package-name "C", :version "1.0.0.1"}
              "D" {:changed? false, :package-name "D", :version "1.0.0.0"}}
             (git/ensure-dependend-builds config changes))
          "C should be marked as changed"))

    (let [changes {"Z" {:changed? true
                        :package-name "Z"
                        :version "1.0.0.0"}
                   "B" {:changed? false
                        :package-name "B"
                        :version "1.0.0.0"}
                   "C" {:changed? false
                        :package-name "C"
                        :version "1.0.0.0"}
                   "D" {:changed? false
                        :package-name "D"
                        :version "1.0.0.0"}}]
      (is (= {"Z" {:changed? true, :package-name "Z", :version "1.0.0.0"},
              "B" {:changed? true, :package-name "B", :version "1.0.0.1"},
              "C" {:changed? true, :package-name "C", :version "1.0.0.1"}
              "D" {:changed? true, :package-name "D", :version "1.0.0.1"}}
             (git/ensure-dependend-builds config changes))
          "B, C and D should be marked as changed"))))

(deftest bump-test
  (let [version "1.77.2.3"]

    (is (= "2.0.0.0" (git/bump {:version version
                                :bump-type :major
                                :commit-sha "deadbee"
                                :snapshot? false})))
    (is (= "1.78.0.0" (git/bump {:version version
                                 :bump-type :minor
                                 :commit-sha "deadbee"
                                 :snapshot? false})))
    (is (= "1.77.3.0" (git/bump {:version version
                                 :bump-type :patch
                                 :commit-sha "deadbee"
                                 :snapshot? false})))
    (is (= "1.77.3.0-deadbee-SNAPSHOT" (git/bump {:version version
                                                   :bump-type :patch
                                                   :commit-sha "deadbee"
                                                   :snapshot? true})))
    (is (= "1.77.2.4" (git/bump {:version version
                                 :bump-type :build
                                 :commit-sha "deadbee"
                                 :snapshot? false})))
    (is (= "1.77.2.3" (git/bump {:version version
                                 :bump-type :none
                                 :commit-sha "deadbee"
                                 :snapshot? false})))))
