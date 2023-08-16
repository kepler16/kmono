(ns k16.kbuild.util
  (:require
   [k16.kbuild.adapter :as adapter]
   [k16.kbuild.git :as git]))

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
           :published? (adapter/release-published?
                        (:adapter dpkg) new-version))))

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

(comment
  (def graph {"lib-1" #{}
              "lib-2" #{"lib-1"}
              "lib-3" #{"lib-2"}
              "lib-4" #{}})

  (def changes {"lib-1"
                {:version "1.79.1.1-8adecdc.dev",
                 :published? false,
                 :tag nil,
                 :package-name "lib-1"},
                "lib-2"
                {:version "1.79.1",
                 :published? false,
                 :tag nil,
                 :package-name "lib-2"},
                "lib-3"
                {:version "1.79.1",
                 :published? false,
                 :tag nil,
                 :package-name "lib-3"},
                "lib-4"
                {:version "1.79.1",
                 :published? false,
                 :tag nil,
                 :package-name "lib-4"}})
  (def expected-changes {"lib-1"
                         {:version "1.79.1.1-8adecdc.dev",
                          :published? false,
                          :tag nil,
                          :package-name "lib-1",
                          :build? true},
                         "lib-2"
                         {:version "1.79.1.1-8adecdc.dev",
                          :published? false,
                          :tag nil,
                          :package-name "lib-2",
                          :build? true},
                         "lib-3"
                         {:version "1.79.1.1-8adecdc.dev",
                          :published? false,
                          :tag nil,
                          :package-name "lib-3",
                          :build? true},
                         "lib-4"
                         {:version "1.79.1.1-8adecdc.dev",
                          :published? false,
                          :tag nil,
                          :package-name "lib-4",
                          :build? false}})
  (= expected-changes (ensure-dependent-builds changes graph))
  nil)
