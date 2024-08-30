(ns k16.kmono.log
  (:require
   [jansi-clj.core :as color]))

(def ^:private lock
  (Object.))

(defn log-raw [data]
  (locking lock
    (.println System/out data)))

(defn log [& msg]
  (log-raw (apply color/render msg)))

(defn info [msg]
  (log (str "@|blue [I] " "|@") (color/render msg)))

(defn error [msg]
  (log (str "@|red [E] " "|@") (color/render msg)))

(defn debug [msg]
  (log (str "@|white [D] " (color/render msg) "|@")))
