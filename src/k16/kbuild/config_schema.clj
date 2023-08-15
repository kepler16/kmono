(ns k16.kbuild.config-schema
  (:require
   [malli.util :as mu]))

(def ?KbuldPackageConfig
  [:map
   [:group [:or :string :symbol]]
   [:artifact {:optional true}
    [:maybe [:or :string :symbol]]]
   [:aliases {:optional true}
    [:vector :keyword]]
   [:release-cmd {:optional true}
    :string]
   [:build-cmd :string]])

(def ?Package
  (-> ?KbuldPackageConfig
      (mu/required-keys [:artifact])
      (mu/assoc :depends-on [:vector :string])
      (mu/assoc :name :string)))

(def ?Packages
  [:vector ?Package])

(def ?PackageMap
  [:map-of :string ?Package])

(def ?Graph
  [:map-of :string [:set :string]])

(def ?BuildOrder
  [:vector [:vector :string]])

(def ?Config
  [:map {:closed true}
   [:mode [:enum :build :exec :release]]
   [:glob :string]
   [:dry-run? :boolean]
   [:snapshot? :boolean]
   [:repo-root :string]
   [:commit-sha :string]
   [:packages ?Packages]
   [:package-map ?PackageMap]
   [:graph ?Graph]
   [:build-order [:maybe ?BuildOrder]]
   [:build-cmd {:optional true} [:maybe :string]]
   [:release-cmd {:optional true} [:maybe :string]]
   [:custom-cmd {:optional true} [:maybe :string]]])

