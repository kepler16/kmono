(ns k16.kmono.util
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [k16.kmono.git :as git]))

(defn- update-dependant
  [{:keys [snapshot? package-map]} changes dependant-name]
  (let [dpkg (get package-map dependant-name)
        {:keys [version] :as dependant} (get changes dependant-name)
        new-version (git/bump {:version version
                               :bump-type :build
                               :commit-sha (:commit-sha dpkg)
                               :snapshot? snapshot?})]
    (assoc dependant
           :version new-version
           :changed? (not= version new-version))))

(defn ensure-dependent-builds
  [config changes graph]
  (loop [changes' changes
         cursor (keys changes)]
    (if-let [{:keys [published? package-name]} (get changes' (first cursor))]
      (do
        (println package-name)

        (if-not @published?
          (let [dependants (->> graph
                                (map (fn [[pkg-name deps]]
                                       (when (contains? deps package-name)
                                         pkg-name)))
                                (remove nil?))]
            (recur (reduce (fn [chgs dpn-name]
                             (update-dependant config chgs dpn-name))
                           changes'
                           dependants)
                   (rest cursor)))
          (recur changes' (rest cursor))))
      changes')))

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
