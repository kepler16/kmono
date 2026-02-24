(ns k16.kmono.git.tags
  (:require
   [k16.kmono.git :as git])
  (:import
   org.eclipse.jgit.api.Git
   org.eclipse.jgit.api.ListTagCommand
   org.eclipse.jgit.api.TagCommand
   org.eclipse.jgit.lib.Constants
   org.eclipse.jgit.lib.Ref
   org.eclipse.jgit.lib.Repository
   org.eclipse.jgit.revwalk.RevCommit
   org.eclipse.jgit.revwalk.RevTag
   org.eclipse.jgit.revwalk.RevWalk))

(set! *warn-on-reflection* true)

(defn- shorten-tag-name [^String refname]
  (if (.startsWith refname "refs/tags/")
    (subs refname (count "refs/tags/"))
    refname))

(defn get-sorted-tags
  "Returns all tags that are present in the current or any ancestor of the
   given `rev`.

   The returned tags are sorted by commit date (descending, most recent commit
   first)"
  ([^String repo-path] (get-sorted-tags repo-path Constants/HEAD))
  ([^String repo-path rev]
   (git/with-repo [repo repo-path]
     (with-open [walk (RevWalk. repo)]
       (let [rev (.resolve repo rev)
             git (Git. repo)]
         (when (nil? rev)
           (throw (ex-info (str "Cannot resolve rev: " rev) {:rev rev})))

         (let [^RevCommit rev-commit (RevWalk/.parseCommit walk rev)
               tags (into []
                          (keep (fn [^Ref ref]
                                  (let [obj-id (Ref/.getObjectId ref)
                                        any-object (RevWalk/.parseAny walk obj-id)
                                        ;; If annotated tag, unwrap to commit
                                        tag-commit (cond
                                                     (instance? RevTag any-object)
                                                     (->> ^RevTag any-object
                                                          (.getObject)
                                                          (RevWalk/.parseCommit walk))

                                                     (instance? RevCommit any-object)
                                                     any-object

                                                     :else nil)]
                                    (when (and tag-commit
                                               (RevWalk/.isMergedInto walk
                                                                      tag-commit
                                                                      rev-commit))
                                      [(shorten-tag-name (.getName ref))
                                       (.getCommitTime ^RevCommit tag-commit)]))))
                          (-> git Git/.tagList ListTagCommand/.call))]

           (into []
                 (map first)
                 (sort-by second #(compare %2 %1) tags))))))))

(defn- resolve-object
  [^Repository repo ref]
  (with-open [walk (RevWalk. repo)]
    (let [oid (Repository/.resolve repo (or ref "HEAD"))]
      (when (nil? oid)
        (throw (ex-info (str "Cannot resolve ref: " ref) {:ref ref})))
      (RevWalk/.parseAny walk oid))))

(defn create-tags
  "Create a set of `tags` in the git repo found at `repo-root` that point to
   the specified `ref` (default: HEAD).

   This will create annotated tags unless `annotated` is set to `false`."
  {:malli/schema [:-> :string [:map
                               [:ref {:optional true} :string]
                               [:annotated {:optional true
                                            :default true} :boolean]
                               [:tags [:sequential :string]]]
                  :nil]}
  [^String repo-root {:keys [ref annotated tags]}]
  (git/with-repo [repo repo-root]
    (let [git (Git. repo)
          obj (resolve-object repo ref)]
      (doseq [tag tags]
        (let [cmd (Git/.tag git)]
          (TagCommand/.setName cmd tag)
          (TagCommand/.setObjectId cmd obj)
          (TagCommand/.setAnnotated cmd (if (boolean? annotated)
                                          annotated
                                          true))
          (TagCommand/.call cmd))))))
