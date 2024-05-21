(ns k16.kmono.adapters.kmono-edn
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.string :as string]
   [clojure.tools.deps.extensions.maven]
   [k16.kmono.adapter :as adapter :refer [Adapter]]))

(defn ->adapter
  ([package-path]
   (->adapter package-path 10000))
  ([package-path _]
   (let [kmono-file (fs/file package-path "kmono.edn")]
     (when (fs/exists? kmono-file)
      (let [kmono-config (-> kmono-file
                             (slurp)
                             (edn/read-string))
            config (adapter/ensure-artifact kmono-config package-path)
            managed-deps (get config :local-deps [])]
        (reify Adapter
 
          (prepare-deps-env [_ changes]
            (string/join
             ";"
             (map
              (fn [dep]
                (str (symbol dep)
                     "@"
                     (get-in changes [dep :version])))
              managed-deps)))
 
          (get-managed-deps [_] managed-deps)
 
          (get-kmono-config [_] config)
 
          (release-published? [_ _] false)))))))
