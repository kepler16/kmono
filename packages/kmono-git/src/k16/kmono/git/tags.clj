(ns k16.kmono.git.tags
  (:require
   [k16.kmono.git :as git.cmd]
   [k16.kmono.git.commit :as git.commit]))

(set! *warn-on-reflection* true)

(defn get-sorted-tags
  "Returns all tags that are present in the current or any ancestor of
  the given `sha`.
  
  The returned tags are sorted by commit date (descending, most recent commit first)"
  ([repo-root]
   (get-sorted-tags repo-root (git.commit/get-current-commit repo-root)))
  ([repo-root ref]
   (git.cmd/run-cmd! repo-root "git" "tag" "--merged" ref "--sort=-committerdate")))

(defn get-all-tags-for-ref [repo-root ref]
  (git.cmd/run-cmd! repo-root "git" "tag" "--points-at" ref))

(defn create-tags
  [repo-root {:keys [ref tags]}]
  (doseq [tag tags]
    (git.cmd/run-cmd! repo-root "git" "tag" tag ref)))
