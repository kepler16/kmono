(ns k16.kmono.test.helpers.cmd
  (:require
   [babashka.process :as proc]
   [clojure.string :as string]))

(set! *warn-on-reflection* true)

(defn- out->strings
  [{:keys [out err exit] :as result}]
  (when-not (= 0 exit)
    (throw (ex-info err (select-keys result [:out :err :exit]))))

  (let [out (string/trim out)]
    (when (seq out)
      (-> out
          (string/split-lines)
          (vec)))))

(defn run-cmd! [dir & cmd]
  (-> (proc/shell {:dir (str dir)
                   :out :string
                   :err :string}
                  (string/join " " (filterv identity cmd)))
      (out->strings)))
