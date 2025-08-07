(ns k16.kmono.core.fs
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.string :as str])
  (:import
   java.nio.file.FileSystems
   java.nio.file.Path
   java.nio.file.PathMatcher))

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

(defn- escape-glob-chars
  "Escapes special glob characters in the input string."
  [s]
  (let [special-chars #{\\ \* \? \[ \] \{ \}}
        escape-char (fn [c]
                      (if (contains? special-chars c)
                        (str "\\" c)
                        (str c)))]
    (apply str (mapv escape-char s))))

(defn- glob->matcher ^PathMatcher [root glob]
  (let [base-path (-> root fs/absolutize fs/normalize str)
        escaped-base-path (escape-glob-chars base-path)
        pattern (let [separator (when-not (str/ends-with? base-path fs/file-separator)
                                  (str fs/file-separator))]
                  (str escaped-base-path separator glob))
        pattern (str "glob:" pattern)]
    (.getPathMatcher
     (FileSystems/getDefault)
     pattern)))

(defn- globs->matcher ^PathMatcher [root globs]
  (let [matchers (mapv #(glob->matcher root %) globs)]
    (proxy [PathMatcher] []
      (matches [^Path path]
        (reduce
         (fn [acc matcher]
           (if (PathMatcher/.matches matcher path)
             (reduced true)
             acc))
         false
         matchers)))))

(def ^:no-doc ?Path
  [:fn {:error/message "Should be an instance of java.nio.file.Path"}
   (partial instance? Path)])

;; TODO: It would be nice if this could skip/not traverse into directories that
;; are included in gitignored files
;; 
;; This would require either a way to convert `.gitignore` syntax to Java
;; PathMatcher compatible globs or using something like JGit.
(defn find-package-directories
  "Find packages in a given `root` that are described by the given set of
   `package-globs`."
  {:malli/schema [:-> :string [:or :string [:set :string]] [:vector ?Path]]}
  [root package-globs]
  (let [root (-> (fs/path root)
                 fs/normalize
                 fs/absolutize)

        matcher (if (string? package-globs)
                  (glob->matcher root package-globs)
                  (globs->matcher root package-globs))

        base-path (-> root fs/absolutize fs/normalize str)
        results (atom (transient #{root}))
        past-root? (volatile! nil)

        match (fn match-path [^Path path]
                (when (and (PathMatcher/.matches matcher path)
                           (= "deps.edn" (fs/file-name path)))
                  (swap! results conj! (fs/parent path)))
                nil)]

    (fs/walk-file-tree
     base-path
     {:follow-links false
      :pre-visit-dir (fn [dir _attrs]
                       (cond
                         (and @past-root?
                              (fs/hidden? dir))
                         :skip-subtree

                         (not @past-root?)
                         (do (vreset! past-root? true)
                             :continue)

                         :else :continue))

      :visit-file (fn [path _attrs]
                    (when-not (fs/hidden? path)
                      (match path))
                    :continue)})

    (persistent! @results)))
