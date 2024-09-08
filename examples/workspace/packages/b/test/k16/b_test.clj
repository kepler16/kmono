(ns k16.b-test
  (:require
   [clojure.test :refer [deftest is]]
   [k16.b :as b]))

(deftest should-return-one
  (is 1 (b/also-return-one)))
