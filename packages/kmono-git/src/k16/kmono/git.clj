(ns k16.kmono.git
  (:import
   [java.io File]
   [org.eclipse.jgit.errors RepositoryNotFoundException]
   [org.eclipse.jgit.lib RepositoryBuilder]))

(set! *warn-on-reflection* true)

(def ^:dynamic ^:private *repo* nil)

(defn- with-repo* [repo-path f]
  (if *repo*
    (f *repo*)
    (try (with-open [repo (-> (RepositoryBuilder.)
                              (RepositoryBuilder/.findGitDir (File. repo-path))
                              RepositoryBuilder/.readEnvironment
                              RepositoryBuilder/.build)]
           (binding [*repo* repo]
             (f repo)))
         (catch RepositoryNotFoundException ex
           (throw (ex-info "Project is not in a git repository"
                           {:type :kmono/no-git-repository
                            :path (str repo-path)}
                           ex))))))

(defmacro with-repo [[var repo-path] & body]
  (let [fn-name '-with-git]
    `(let [f# (fn ~fn-name [git#]
                (let [~var git#]
                  ~@body))]
       (#'k16.kmono.git/with-repo* ~repo-path f#))))
