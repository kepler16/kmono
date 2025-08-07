(ns k16.kmono.git
  (:import
   [java.io File]
   [org.eclipse.jgit.api Git]
   [org.eclipse.jgit.errors RepositoryNotFoundException]))

(set! *warn-on-reflection* true)

(def ^:dynamic ^:private *repo* nil)

(defmacro with-open-repo [repo-path f]
  `(if-let [[git# repo#] (var-get #'k16.kmono.git/*repo*)]
     (~f git# repo#)
     (try (with-open [git# (Git/open (File. (str ~repo-path)))]
            (let [repo# (Git/.getRepository git#)]
              (binding [k16.kmono.git/*repo* [git# repo#]]
                (~f git# repo#))))
          (catch RepositoryNotFoundException ex#
            (throw (ex-info "Project is not in a git repository"
                            {:type :kmono/no-git-repository
                             :path (str ~repo-path)}
                            ex#))))))

(defmacro with-pre-open-repo [repo-path & body]
  `(with-open-repo ~repo-path
     (fn [_# _#] ~@body)))
