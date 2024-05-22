(ns k16.kmono.util
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]))

(defn read-deps-edn!
  [file-path]
  (try
    (-> (fs/file file-path)
        (slurp)
        (edn/read-string))
    (catch Throwable e
      (throw (ex-info "Could not read deps.edn file"
                      {:file-path file-path
                       :event "read-deps-edn"}
                      e)))))
