(ns k16.kbuild.ansi
  (:require
   [clojure.string :as string]))

(defn red
  [& chunks]
  (str "\033[31m"
       (apply str chunks)
       "\033[0m"))

(defn green
  [& chunks]
  (str "\033[32m"
       (apply str chunks)
       "\033[0m"))

(defn cyan
  [& chunks]
  (str "\033[36m"
       (apply str chunks)
       "\033[0m"))

(defn blue
  [& chunks]
  (str "\033[34m"
       (apply str chunks)
       "\033[0m"))

(def ERROR_PREFIX (red "[ERROR]"))
(def SUCCESS_PREFIX (green "[OK]"))
(def INFO_PREFIX (blue "[INFO]"))

(defn print-success
  [& v]
  (apply println SUCCESS_PREFIX v))

(defn print-error
  [& v]
  (apply println ERROR_PREFIX v))

(defn print-info
  [& v]
  (apply println INFO_PREFIX v))

(defn assert-err!
  [v msg]
  (when-not v
    (println ERROR_PREFIX msg)
    (System/exit 1)))

(defn print-shifted
  [output]
  (->> output
       (string/split-lines)
       (map #(str "\t" %))
       (string/join "\n")
       (print))
  (println))

