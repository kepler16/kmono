(ns k16.kmono.core.fs
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn- is-workspace-root [file]
  (let [lines (fs/read-all-lines file)]
    (some (fn [line]
            (str/includes? line ":kmono/workspace"))
          lines)))

(defn- normalize-dir [dir]
  (if (fs/absolute? dir)
    dir
    (-> (fs/file (fs/cwd) dir)
        fs/normalize
        str)))

(defn find-project-root
  "Given a directory (or the cwd if non is supplied) try to find root of the
  clojure project.

  This is either:

  1. The first directory containing a `deps.edn` file with a `:kmono/workspace`
     key present
  2. The furthest directory containing a `deps.edn` file."
  ([] (find-project-root nil nil))
  ([dir] (find-project-root dir nil))
  ([dir current-root]
   (let [dir (normalize-dir (or dir (fs/cwd)))
         deps-file (fs/file dir "deps.edn")]
     (cond
       (not (fs/starts-with? dir (fs/home)))
       (when current-root
         (str (fs/absolutize current-root)))

       (and (fs/exists? deps-file)
            (is-workspace-root deps-file))
       (str (fs/absolutize dir))

       (fs/exists? deps-file)
       (find-project-root (fs/parent dir) dir)

       :else
       (find-project-root (fs/parent dir) current-root)))))

(defn find-project-root!
  "This is the same as [[k16.kmono.core.fs/find-project-root]] but will throw an
  exception if no project root can be found."
  ([] (find-project-root! nil))
  ([dir]
   (let [root (find-project-root dir)]
     (when-not root
       (throw (ex-info "Not a Clojure project" {:type :kmono/root-not-found
                                                :dir (str (normalize-dir (or dir (fs/cwd))))})))
     root)))

(defn read-edn-file! [file-path]
  (try
    (-> (fs/file file-path)
        (slurp)
        (edn/read-string))
    (catch Exception ex
      (throw (ex-info (str "Could not read " file-path)
                      {:file-path file-path}
                      ex)))))

(defn find-package-directories [root packages-glob]
  (let [root (-> (fs/path root)
                 fs/normalize
                 fs/absolutize)]
    (into [root]
          (comp
           (filter (fn [path]
                     (= "deps.edn" (fs/file-name path))))
           (map fs/parent))
          (fs/glob root packages-glob))))
