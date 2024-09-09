(ns k16.kmono.log
  (:require
   [jansi-clj.core :as color]))

(set! *warn-on-reflection* true)

(def ^:private lock
  (Object.))

(def ^:dynamic *log-out*
  System/out)

(defn log-raw [data]
  (locking lock
    (.println ^java.io.PrintStream *log-out* data)))

(defn log [& msg]
  (log-raw (apply color/render msg)))

(defn info [msg]
  (log (str "@|blue [I] " "|@") (color/render msg)))

(defn error [msg]
  (log (str "@|red [E] " "|@") (color/render msg)))

(defn debug [msg]
  (log (str "@|white [D] " (color/render msg) "|@")))
