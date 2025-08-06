(ns k16.kmono.cli.commands.query
  (:require
   [clojure.java.io :as io]
   [jsonista.core :as json]
   [k16.kmono.cli.common.context :as common.context]
   [k16.kmono.cli.common.opts :as opts]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.log :as log]
   [k16.kmono.version :as kmono.version]))

(defn- run [opts _]
  (let [{:keys [with-versions with-changes with-changes-since
                include-dependents filter-unchanged
                output include-keys exclude-keys]} opts
        filter' (:filter opts)

        with-versions (if with-changes
                        true
                        with-versions)

        _ (when (and filter-unchanged
                     (not with-changes)
                     (not with-changes-since))
            (log/error "Either --with-changes or --with-changes-since is required when setting --filter-unchanged")
            (System/exit 1))

        {:keys [root packages]} (common.context/load-context opts)
        packages (cond->> packages
                   filter'
                   (core.graph/filter-by (core.packages/name-matches? filter'))

                   with-versions
                   (kmono.version/resolve-package-versions root)

                   with-changes
                   (kmono.version/resolve-package-changes root)

                   with-changes-since
                   (kmono.version/resolve-package-changes-since root with-changes-since)

                   filter-unchanged
                   (core.graph/filter-by kmono.version/package-changed?
                                         {:include-dependents include-dependents}))
        packages (into {}
                       (map (fn [[key pkg]]
                              (let [pkg (cond-> pkg
                                          (seq include-keys) (select-keys include-keys))]
                                [key (apply (partial dissoc pkg) exclude-keys)])))
                       packages)]

    (case output
      :json (println (json/write-value-as-string packages (json/object-mapper {:pretty true})))
      :edn (println (prn-str packages)))))

(def with-changes-since
  {:desc "Include commits which modified each package since the given <rev>"
   :coerce :string
   :ref "<rev>"})

(def with-changes
  {:desc "Include commits which modified each package since its last tagged version. Setting this will enable --with-versions"
   :coerce :boolean
   :default false})

(def with-versions
  {:desc "Whether to load package versions from git tags"
   :coerce :boolean
   :default false})

(def filter-unchanged
  {:desc "Exclude packages which have not changed. Requires one of --with-changes or --with-changes-since"
   :coerce :boolean
   :default false})

(def include-dependents
  {:desc "Include the dependents of filtered packages even if they don't match any of the filters"
   :coerce :boolean
   :default false})

(def output-format
  {:desc "What format should the query result be presented in. Can be one of [json, edn]"
   :alias :o
   :parse-fn keyword
   :validate {:pred (fn [value]
                      (contains? #{"edn" "json"} value))
              :ex-msg (fn [{:keys [value]}]
                        (str "--output (-o) format must be one of [json, edn]. Given '" value "'"))}
   :default "json"})

(def include-keys
  {:desc "Include only the specified pkg keys in the output"
   :coerce :string
   :parse-fn opts/parse-keywords})

(def exclude-keys
  {:desc "Include the specified pkg keys from the output"
   :coerce :string
   :parse-fn opts/parse-keywords
   :default "deps-edn"})

(def command
  {:command "query"
   :summary "Query information about the package graph"
   :desc (delay (io/resource "k16/kmono/docs/query.md"))
   :options {:with-versions with-versions
             :with-changes-since with-changes-since
             :with-changes with-changes
             :filter-unchanged filter-unchanged
             :include-dependents include-dependents
             :filter opts/package-filter-opt
             :output output-format
             :include-keys include-keys
             :exclude-keys exclude-keys}
   :run-fn run})
