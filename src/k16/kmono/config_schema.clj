(ns k16.kmono.config-schema
  (:require
   [clojure.pprint :as pp]
   [clojure.set :as set]
   [k16.kmono.ansi :as ansi]
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu]))

(def ?CommandConfig
  [:map
   [:glob {:optional true
           :default "packages/*"}
    :string]
   [:snapshot? {:optional true
                :default true}
    :boolean]
   [:include-unchanged? {:optional true
                         :default true}
    :boolean]
   [:main-aliases {:optional true}
    [:vector :keyword]]
   [:aliases {:optional true}
    [:vector :keyword]]
   [:package-aliases {:optional true}
    [:vector :keyword]]])

(def ?KmonoWorkspaceConfig
  (mu/merge ?CommandConfig
            [:map
             [:group {:optional true}
              [:or :string :symbol]]]))

(def ?KmonoWorkspaceUserConfig
  (mu/rename-keys ?KmonoWorkspaceConfig {:glob :packages}))

(def ?KmonoPackageConfig
  [:map
   [:group [:or :string :symbol]]
   [:artifact {:optional true}
    [:maybe [:or :string :symbol]]]
   [:aliases {:optional true}
    [:vector :keyword]]
   [:release-cmd {:optional true}
    [:or [:= :skip] :string]]
   [:local-deps {:optional true}
    [:or
     [:vector :symbol]
     [:vector :string]]]
   [:build-cmd {:optional true}
    :string]])

(def ?Package
  (-> ?KmonoPackageConfig
      (mu/required-keys [:artifact])
      (mu/merge [:map
                 [:depends-on [:vector :string]]
                 [:commit-sha :string]
                 [:name :string]
                 [:dir :string]])))

(def ?Packages
  [:vector ?Package])

(def ?PackageMap
  [:map-of :string ?Package])

(def ?Graph
  [:map-of :string [:set :string]])

(def ?BuildOrder
  [:vector [:vector :string]])

(def ?Config
  (mu/merge
   (mu/required-keys
    ?KmonoWorkspaceConfig
    [:glob :snapshot? :include-unchanged?])
   [:map {:closed true}
    [:exec [:or :string [:enum :build :release]]]
    [:workspace-config {:optional true}
     ?KmonoWorkspaceConfig]
    [:dry-run? :boolean]
    [:create-tags? :boolean]
    [:repo-root :string]
    [:packages ?Packages]
    [:package-map ?PackageMap]
    [:graph ?Graph]
    [:build-order [:maybe ?BuildOrder]]]))

(defn assert-schema!
  ([?schema value]
   (assert-schema! ?schema "Schema error" value))
  ([?schema title value]
   (binding [ansi/*logs-enabled* true]
     (if-not (m/validate ?schema value)
       (do (ansi/print-error title)
           (ansi/print-shifted
            (with-out-str
              (pp/pprint (me/humanize (m/explain ?schema value)))))
           (throw (ex-info title {:type :errors/assertion})))
       value))))

(defn ->internal-config
  "We don't want packages key internally, because we already have it, so rename it to glob"
  [config]
  (set/rename-keys config {:packages :glob}))

