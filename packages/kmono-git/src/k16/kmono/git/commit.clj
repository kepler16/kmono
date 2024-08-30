(ns k16.kmono.git.commit
  (:require
   [clojure.string :as str]
   [k16.kmono.git :as git]))

(set! *warn-on-reflection* true)

(defn get-current-commit [repo]
  (first
   (git/run-cmd! repo "git rev-parse HEAD")))

(defn get-current-commit-short [repo]
  (subs (get-current-commit repo) 0 7))

(defn find-commits-since [repo {:keys [ref subdir]}]
  (let [subdir (when subdir
                 (str "-- " subdir))]
    (git/run-cmd! repo
                  "git log --pretty=format:\"%H\""
                  (when ref (str ref "..HEAD"))
                  subdir)))

(defn get-commit-message [repo sha]
  (let [res (git/run-cmd! repo "git show -s --format=%B" sha)]
    {:message (first res)
     :body (str/join \newline (rest res))}))
