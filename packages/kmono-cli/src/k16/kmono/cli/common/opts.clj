(ns k16.kmono.cli.common.opts
  (:require
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn parse-aliases [value]
  (into []
        (comp
         (map (fn [value]
                (-> value
                    (str/replace ":" "")
                    (str/split #"/"))))
         (map (fn [[ns|name maybe-name]]
                (if maybe-name
                  (keyword ns|name maybe-name)
                  (keyword ns|name)))))
        (str/split value #",")))

(defn parse-bool-or-aliases [value]
  (cond
    (= value true) []
    (keyword? value) (parse-aliases (name value))
    :else (parse-aliases value)))

(def aliases-opt
  {:desc "Use aliases from the root deps.edn or deps.local.edn"
   :short :A
   :coerce :string
   :parse-fn parse-aliases})

(def package-aliases-opt
  {:desc "Use aliases from sub packages in the workspace"
   :alias :P
   :coerce :string
   :parse-fn parse-aliases})

(def run-in-order-opt
  {:desc "Run tests in dependency order"
   :coerce :boolean
   :default true})

(def skip-unchanged-opt
  {:desc "Skip packages that have not been changed since the last known version"
   :coerce :boolean
   :default false})
