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
         (filter (fn [[ns|name]]
                   (not= "" ns|name)))
         (map (fn [[ns|name maybe-name]]
                (if (and maybe-name
                         (not= "" maybe-name))
                  (keyword ns|name maybe-name)
                  (keyword ns|name)))))
        (str/split value #"(,|:)")))

(defn parse-bool-or-aliases [value]
  (cond
    (= value true) []
    (keyword? value) (parse-aliases (name value))
    :else (parse-aliases value)))

(def aliases-opt
  {:desc "Include aliases from the root deps.edn or deps.local.edn"
   :short :A
   :coerce :string
   :parse-fn parse-aliases})

(def package-aliases-opt
  {:desc "Use aliases from sub packages in the workspace"
   :alias :P
   :coerce :string
   :parse-fn parse-aliases})

(def package-filter-opt
  {:desc "Only operate on packages that match the given :<group>/<artifact> filter"
   :alias :F
   :coerce :string
   :parse-fn parse-aliases})

(def run-in-order-opt
  {:desc "Run commands against packages in the order in which they depend on each other"
   :coerce :boolean
   :default true})

(def concurrency-opt
  {:desc "Maximum number of commands to run in parallel. Defaults to number of cores"
   :alias :c
   :coerce :int})

(def skip-unchanged-opt
  {:desc "Skip packages that have not been changed since the last known version"
   :coerce :boolean
   :default false})
