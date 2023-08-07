(ns k16.kbuild.main
  (:require
   [k16.kbuild.adapters.clojure-deps :as clojure-deps]))

(def adapders
  {:clojure-deps clojure-deps/adapter})

(defn -main []
  (println "Hello"))

