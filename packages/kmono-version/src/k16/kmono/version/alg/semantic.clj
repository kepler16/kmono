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

(def ^:private version-type->weight
  {:patch 1
   :minor 2
   :major 3})

(defn version-fn
  "A `version-fn` for `k16.kmono.version/inc-package-versions` which produces a
  version-type of `[:patch, :minor, :major]` according to the convensions of
  semantic commits.

  - A commit message with `fix:` in the title would produce a version-type of
  `:patch`. - A commit message with `feat:` in the title would produce a
  version-type of `:minor`. - The presence of a bang (!) such as `fix!:` would
  produce a version-type of `:major`.

  And finally if the commit message body contained the text `BREAKING CHANGE:`
  then this would also result in a version-type of `:major`."
  [package]
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
