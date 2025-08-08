(ns k16.kmono.log
  (:require
   [bling.core :as bling]))

(set! *warn-on-reflection* true)

(def ^:private lock
  (Object.))

(def ^:dynamic *log-out*
  System/out)

(defn log-raw [data]
  (locking lock
    (let [result (apply bling/bling data)]
      (.println ^java.io.PrintStream *log-out* result))))

(defn log [& msgs]
  (log-raw msgs))

(defn info [& msgs]
  (log-raw (into [[:system-blue "[I] "]] msgs)))

(defn error [& msgs]
  (log-raw (into [[:system-red "[E] "]] msgs)))

(defn debug [& msgs]
  (log-raw (into [[:system-silver "[D] "]] msgs)))
