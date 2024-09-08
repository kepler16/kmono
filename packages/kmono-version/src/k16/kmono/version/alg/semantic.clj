(ns k16.kmono.version.alg.semantic
  (:require
   [clojure.string :as str]))

(def ^:private commit-pattern
  #"^(?<type>\w+)(?:\([^\)]+\))?(?<breaking>!?):\s*(?<message>.*)$")

(defn match-commit [commit]
  (let [[_ type breaking] (re-matches commit-pattern (:message commit))
        contains-breaking (str/includes? (:body commit) "BREAKING CHANGE:")]
    {:type type
     :breaking (or contains-breaking
                   (= "!" breaking))}))

(def ^:private commit-type->version-type
  {:fix :patch
   :feat :minor})

(def version-type->weight
  {:patch 1
   :minor 2
   :major 3})

(defn version-fn [package]
  (reduce
   (fn [current-version-type commit]
     (let [match (match-commit commit)
           version-type (if (:breaking match)
                          :major
                          (when (:type match)
                            ((keyword (:type match)) commit-type->version-type)))]

       (cond
         (not version-type)
         current-version-type

         (not current-version-type)
         version-type

         (> (version-type version-type->weight) (current-version-type version-type->weight))
         version-type

         :else
         current-version-type)))

   nil
   (:commits package)))
