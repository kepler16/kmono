(ns k16.a-test
  (:require
   [clojure.test :refer [deftest is]]
   [k16.a :as a]))

(deftest should-return-one
  (is 1 (a/return-one)))
