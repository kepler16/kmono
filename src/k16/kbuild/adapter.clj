(ns k16.kbuild.adapter
  (:require
   [malli.core :as m]))

(def ?VersionMap
  [:vector
   [:map-of :string :string]])

(defprotocol Adapter
  (update-deps! [this deps])
  (get-deps [this]))

(defn bump-local-deps!
  [adapter version-map]
  (assert (m/validate ?VersionMap version-map)
          (m/explain ?VersionMap version-map))
  (update-deps! adapter version-map))

(defn get-local-deps
  [adapter]
  (get-deps adapter))



