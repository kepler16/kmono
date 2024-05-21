(ns k16.kmono.git-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
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

(deftest dependent-single-bump-test
  (let [config (edn/read-string
                (slurp (io/resource "fixtures/dependents_config.edn")))
        changes {"transit-engineering/runtime-api"
                 {:version "0.15.1.0",
                  :changed? true,
                  :package-name "transit-engineering/runtime-api"},
                 "transit-engineering/jollibee.ext"
                 {:version "0.0.0.9",
                  :changed? false,
                  :package-name "transit-engineering/jollibee.ext"},
                 "transit-engineering/session-notification.ext"
                 {:version "0.6.0.3",
                  :changed? false,
                  :package-name "transit-engineering/session-notification.ext"},
                 "transit-engineering/runtime"
                 {:version "2.10.0.30",
                  :changed? false,
                  :package-name "transit-engineering/runtime"},
                 "transit-engineering/robinsons-loyalty.ext"
                 {:version "0.1.1.0",
                  :changed? false,
                  :package-name "transit-engineering/robinsons-loyalty.ext"},
                 "transit-engineering/swing.ext"
                 {:version "0.1.0.5",
                  :changed? false,
                  :package-name "transit-engineering/swing.ext"},
                 "transit-engineering/telemetry.ext"
                 {:version "0.0.1.0",
                  :changed? true,
                  :package-name "transit-engineering/telemetry.ext"},
                 "transit-engineering/healthchecker.ext"
                 {:version "0.0.2.9",
                  :changed? false,
                  :package-name "transit-engineering/healthchecker.ext"},
                 "transit-engineering/scan-hook.ext"
                 {:version "0.4.0.0",
                  :changed? true,
                  :package-name "transit-engineering/scan-hook.ext"},
                 "transit-engineering/agent.ext"
                 {:version "0.12.0.6",
                  :changed? false,
                  :package-name "transit-engineering/agent.ext"},
                 "transit-engineering/scan-training.ext"
                 {:version "0.0.0.19",
                  :changed? false,
                  :package-name "transit-engineering/scan-training.ext"}}]
    (is (= "2.10.0.31" (-> (git/ensure-dependend-builds config changes)
                           (get "transit-engineering/runtime")
                           :version)))))

