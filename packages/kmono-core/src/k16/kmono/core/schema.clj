(ns k16.kmono.core.schema)

(set! *warn-on-reflection* true)

(def ?WorkspaceConfig
  [:map
   [:packages {:optional true
               :default "packages/*"}
    :string]

   [:group {:optional true}
    :symbol]

   [:main-aliases {:optional true}
    [:vector :keyword]]
   [:aliases {:optional true}
    [:vector :keyword]]
   [:package-aliases {:optional true}
    [:vector :keyword]]])

(def ?PackageConfig
  [:map
   [:group :symbol]
   [:name {:optional true}
    [:maybe [:or :string :symbol]]]])

(def ?Coordinate
  [:map
   [[:= :local/root] {:optional true} :string]])

(def ?Commit
  [:map
   [:sha :string]
   [:message :string]
   [:body :string]])

(def ?Package
  [:map
   [:group :symbol]
   [:name :symbol]
   [:fqn :symbol]

   [:version {:optional true} [:maybe :string]]
   [:commits {:optional true} [:sequential ?Commit]]

   [:deps-edn
    [:map
     [:deps {:optional true} [:map-of :symbol ?Coordinate]]
     [:aliases {:optional true} [:map-of :keyword :map]]]]

   [:depends-on [:set :symbol]]
   [:dependents [:set :symbol]]

   [:absolute-path :string]
   [:relative-path :string]])

(def ?PackageMap
  [:map-of :symbol ?Package])
