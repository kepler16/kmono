(ns k16.kmono.git.files
  (:require
   [clojure.string :as str]
   [k16.kmono.git :as git])
  (:import
   [java.io ByteArrayOutputStream]
   [org.eclipse.jgit.diff DiffEntry DiffFormatter]
   [org.eclipse.jgit.lib Constants Repository]))

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
