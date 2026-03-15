(ns k16.kmono.git.files
  (:require
   [clojure.string :as str]
   [k16.kmono.git :as git])
  (:import
   [java.io ByteArrayOutputStream]
   [org.eclipse.jgit.diff DiffEntry DiffFormatter]
   [org.eclipse.jgit.lib Constants Repository]
   [org.eclipse.jgit.revwalk RevCommit RevWalk]))

(set! *warn-on-reflection* true)

(defn find-changed-files-since
  "Return a seq of changed file paths since `ref` up to HEAD. If `subdir` is
   provided, only include files within that subdir."
  [^String repo-path {:keys [ref subdir]}]
  (git/with-repo [repo repo-path]
    (let [start-commit (Repository/.resolve repo ref)
          end-commit (Repository/.resolve repo Constants/HEAD)

          subdir (when subdir
                   (str subdir "/"))]

      (when-not start-commit
        (throw (ex-info (str "Unknown rev " ref) {})))

      (when-not end-commit
        (throw (ex-info "Failed to resolve HEAD" {})))

      (with-open [out (ByteArrayOutputStream.)
                  diff-formatter (DiffFormatter. out)]
        (DiffFormatter/.setRepository diff-formatter repo)
        (let [results (DiffFormatter/.scan diff-formatter
                                           start-commit
                                           end-commit)]

          (into []
                (comp
                 (map #(DiffEntry/.getNewPath %))
                 (remove #(= % DiffEntry/DEV_NULL))
                 (filter (fn [entry]
                           (or (nil? subdir)
                               (str/starts-with? entry subdir)))))
                results))))))

(defn find-commit-changed-files
  "Return file paths changed by a specific commit. If `subdir` is provided, only
   include files within that subdir."
  [^String repo-path {:keys [sha subdir]}]
  (git/with-repo [repo repo-path]
    (let [commit-id (Repository/.resolve repo sha)
          subdir-prefix (when subdir (str subdir "/"))]
      (with-open [walk (RevWalk. repo)]
        (let [commit (RevWalk/.parseCommit walk commit-id)
              parent-tree (when (pos? (RevCommit/.getParentCount commit))
                            (let [parent (RevWalk/.parseCommit walk
                                                               (aget (RevCommit/.getParents commit) 0))]
                              (RevCommit/.getTree parent)))
              commit-tree (RevCommit/.getTree commit)]
          (with-open [out (ByteArrayOutputStream.)
                      df (DiffFormatter. out)]
            (DiffFormatter/.setRepository df repo)
            (let [diffs (DiffFormatter/.scan df parent-tree commit-tree)]
              (into []
                    (comp
                     (map #(DiffEntry/.getNewPath %))
                     (remove #(= % DiffEntry/DEV_NULL))
                     (filter (fn [path]
                               (or (nil? subdir-prefix)
                                   (str/starts-with? path subdir-prefix)))))
                    diffs))))))))
