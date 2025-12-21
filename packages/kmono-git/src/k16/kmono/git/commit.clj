(ns k16.kmono.git.commit
  (:require
   [clojure.string :as str]
   [k16.kmono.git :as git])
  (:import
   [org.eclipse.jgit.api Git LogCommand]
   [org.eclipse.jgit.lib
    AbbreviatedObjectId
    AnyObjectId
    Constants
    ObjectId
    ObjectReader
    Repository]
   [org.eclipse.jgit.revwalk RevCommit RevTag RevWalk]))

(set! *warn-on-reflection* true)

(def ^:private resolve-cache
  (atom {}))

(defn- peel-to-commit
  "Resolve rev to a _commit_ ObjectId, peeling annotated tags."
  ^ObjectId [^Repository repo ^AnyObjectId oid]
  (with-open [walk (RevWalk. repo)]
    (loop [obj (RevWalk/.parseAny walk oid)]
      (cond
        (instance? RevCommit obj)
        (RevCommit/.getId ^RevCommit obj)

        (instance? RevTag obj)
        (->> (RevTag/.getObject ^RevTag obj)
             (RevWalk/.parseAny walk)
             recur)

        :else
        (throw (ex-info (str "Rev does not resolve to a commit: " (AnyObjectId/.getName oid))
                        {:rev (AnyObjectId/.getName oid)}))))))

(defn resolve-commit-id
  "Resolve a rev (e.g. HEAD, tag, branch, SHA) to a commit ObjectId.

   Throws if it cannot be resolved to a commit."
  ^ObjectId [^Repository repo ^String rev]
  (let [index [(str (Repository/.getDirectory repo)) rev]
        result (get @resolve-cache index)]
    (or result
        (let [oid (some->> (Repository/.resolve repo rev)
                           (peel-to-commit repo))]
          (when (nil? oid)
            (throw (ex-info (str "Cannot resolve rev: " rev) {:rev rev})))
          (swap! resolve-cache assoc index oid)
          oid))))

(defn get-current-commit
  "Returns the current commit (HEAD) of the repository at `repo`.

   Returns `nil` if the commit cannot be resolved."
  [^String repo-path]
  (git/with-repo [repo repo-path]
    (some-> (Repository/.resolve repo Constants/HEAD)
            (ObjectId/.getName))))

(defn get-current-commit-short
  "Like [[get-current-commit]] but returns the abbreviated commit sha instead"
  [^String repo-path]
  (git/with-repo [repo repo-path]
    (with-open [reader (Repository/.newObjectReader repo)]
      (some->> (Repository/.resolve repo Constants/HEAD)
               (ObjectReader/.abbreviate reader)
               (AbbreviatedObjectId/.name)))))

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

(defn find-commits-since
  "Find all commits since a given `ref` (or all, if excluded) and optionally
   filter by the commits which affect the provided `subdir`."
  [^String repo-path {:keys [ref subdir]}]
  (git/with-repo [repo repo-path]
    (let [git (Git. repo)
          log (Git/.log git)]
      (when ref
        (let [since (resolve-commit-id repo ref)
              head (resolve-commit-id repo Constants/HEAD)]
          (LogCommand/.addRange log since head)))

      (when subdir
        (LogCommand/.addPath log subdir))

      (mapv commit->map (LogCommand/.call log)))))
