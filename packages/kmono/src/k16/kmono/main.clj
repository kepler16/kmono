(ns k16.kmono.main
  (:require
   [clojure.pprint :as pprint]
   [clojure.string :as string]
   [k16.kmono.api :as api])
  (:gen-class))

(defn -main [& args]
  (let [params (->> args
                    (partition-all 2)
                    (map (fn [[k v]]
                           [(keyword (string/replace k #"^:" "")) v]))
                    (into {}))]
    (pprint/pprint params)
    (api/run params)))

