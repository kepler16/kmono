(ns k16.kmono.test.helpers.commit
  (:require
   [k16.kmono.test.helpers.cmd :as cmd]))

(defn- get-current-commit [repo]
  (first
   (cmd/run-cmd! repo "git rev-parse HEAD")))

(defn commit
  ([repo] (commit repo "commit"))
  ([repo message]
   (cmd/run-cmd! repo "git add .")
   (cmd/run-cmd! repo "git -c commit.gpgsign=false commit --allow-empty" "-m" (str "'" message "'"))
   (get-current-commit repo)))
