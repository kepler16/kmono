(ns k16.kmono.git.commit
  (:require
   [clojure.string :as str]
   [k16.kmono.git :as git])
  (:import
   [java.io File]
   [org.eclipse.jgit.api Git LogCommand]
   [org.eclipse.jgit.lib Constants ObjectId Repository]
   [org.eclipse.jgit.revwalk RevCommit]))

(set! *warn-on-reflection* true)

(def ^:private resolve-cache
  (atom {}))

(defn resolve-commit-id
  "Resolve a rev (e.g. HEAD, tag, branch, SHA) to a commit ObjectId. Throws if
   it cannot be resolved to a commit."
  ^ObjectId [^Repository repo ^String rev]
  (let [index [(str (Repository/.getDirectory repo)) rev]
        result (get @resolve-cache index)]
    (or result
        (let [oid (Repository/.resolve repo rev)]
          (when (nil? oid)
            (throw (ex-info (str "Cannot resolve rev: " rev) {:rev rev})))
          (swap! resolve-cache assoc index oid)
          oid))))

(defn get-current-commit [^String repo]
  (with-open [git (Git/open (File. repo))]
    (let [repository (Git/.getRepository git)]
      (-> (Repository/.resolve repository Constants/HEAD)
          (ObjectId/.getName)))))

(defn- commit->map [^RevCommit commit]
  (let [full (RevCommit/.getFullMessage commit)
        idx (when full (str/index-of full "\n"))
        has-body? (and (not (nil? idx))
                       (not (neg? idx)))
        message (if has-body?
                  (subs full 0 idx)
                  full)
        body (if has-body?
               (subs full (inc idx))
               "")]
    {:sha (RevCommit/.getName commit)
     :message (or message "")
     :body (str/trim body)}))

(defn find-commits-since [^String repo {:keys [ref subdir]}]
  (git/with-open-repo repo
    (fn with-open-repo [git repo]
      (let [log (Git/.log git)]
        (when ref
          (let [since (resolve-commit-id repo ref)
                head (resolve-commit-id repo Constants/HEAD)]
            (LogCommand/.addRange log since head)))

        (when subdir
          (LogCommand/.addPath log subdir))

        (mapv commit->map (LogCommand/.call log))))))
