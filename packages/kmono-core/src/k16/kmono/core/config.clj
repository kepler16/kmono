(ns k16.kmono.core.config
  (:require
   [babashka.fs :as fs]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.schema :as core.schema]
   [malli.core :as malli]
   [malli.error :as malli.error]
   [malli.transform :as malli.transform]
   [meta-merge.core :as metamerge]))

(set! *warn-on-reflection* true)

(defn- validate!
  ([?schema data message]
   (validate! ?schema data message {}))
  ([?schema data message ex-data]
   (when-not (malli/validate ?schema data)
     (throw (ex-info message (merge {:type :kmono/validation-error
                                     :errors (->> data
                                                  (malli/explain ?schema)
                                                  malli.error/humanize)}
                                    ex-data))))

   data))

(defn- read-kmono-config [deps-file-path key]
  (some-> (when (fs/exists? deps-file-path)
            (core.fs/read-edn-file! deps-file-path))
          (get key)))

(defn resolve-workspace-config
  "Read in and validate kmono workspace config at a given `root` dir.

  This will attempt to load the key `:kmono/workspace` from both a `deps.edn`
  file and a `deps.local.edn` file at the given `root`. These will be merged
  together with the `deps.local.edn` taking precedence."
  [root]
  (let [root-workspace-config (read-kmono-config (fs/file root "deps.edn") :kmono/workspace)
        local-workspace-config (read-kmono-config (fs/file root "deps.local.edn") :kmono/workspace)
        workspace-config (metamerge/meta-merge {} root-workspace-config local-workspace-config)]

    (when workspace-config
      (validate! core.schema/?WorkspaceConfig workspace-config "Workspace config is invalid")

      (malli/encode core.schema/?WorkspaceConfig
                    workspace-config
                    (malli.transform/default-value-transformer
                     {::malli.transform/add-optional-keys true})))))

(defn resolve-package-config
  "Read in and validate kmono package config from a package at the given
  `package-path`.

  This accepts a `workspace-config` from which certain keys like `:group` will
  be derived and used as defaults for the package config."
  [workspace-config package-path]
  (let [deps-file-path (fs/file package-path "deps.edn")

        deps-edn (when (fs/exists? deps-file-path)
                   (core.fs/read-edn-file! deps-file-path))

        package-config (:kmono/package deps-edn)
        package-config (merge (select-keys workspace-config [:group])
                              {:deps-edn deps-edn}
                              package-config)]

    (when (:kmono/package deps-edn)
      (validate! core.schema/?PackageConfig
                 package-config
                 "Package config is invalid"
                 {:package-path (str package-path)})

      package-config)))
