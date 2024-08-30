(ns k16.kmono.git.files
  (:require
   [k16.kmono.git :as git]))

(set! *warn-on-reflection* true)

(defn find-changed-files-since [repo {:keys [ref subdir]}]
  (let [subdir (when subdir
                 (str "-- " subdir))]
    (git/run-cmd! repo "git diff --name-only" (str ref "..HEAD") subdir)))
