(ns k16.kbuild.adapter
  (:require
   [malli.core :as m]))

(def ?Deps
  [:vector
   [:map
    [:coordinate :string]
    [:version :string]]])

(defprotocol Adapter
  (update-deps! [this path deps]))

(defn bump-deps!
  [adapter path deps]
  (assert (m/validate ?Deps deps)
          (m/explain ?Deps deps))
  (update-deps! adapter path deps))

