(ns k16.kmono.config-schema
  (:require
   [malli.util :as mu]))

(def ?KmonoPackageConfig
  [:map
   [:group [:or :string :symbol]]
   [:artifact {:optional true}
    [:maybe [:or :string :symbol]]]
   [:aliases {:optional true}
    [:vector :keyword]]
   [:release-cmd {:optional true}
    :string]
   [:local-deps {:optional true}
    [:or
     [:vector :symbol]
     [:vector :string]]]
   [:build-cmd {:optional true}
    :string]])

(def ?Package
  (-> ?KmonoPackageConfig
      (mu/required-keys [:artifact])
      (mu/assoc :depends-on [:vector :string])
      (mu/assoc :commit-sha :string)
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
   [:exec [:or :string [:enum :build :release]]]
   [:glob :string]
   [:dry-run? :boolean]
   [:snapshot? :boolean]
   [:include-unchanged? :boolean]
   [:create-tags? :boolean]
   [:repo-root :string]
   [:packages ?Packages]
   [:package-map ?PackageMap]
   [:graph ?Graph]
   [:build-order [:maybe ?BuildOrder]]])


