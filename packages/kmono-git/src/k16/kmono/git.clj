(ns k16.kmono.git
  (:require
   [babashka.fs :as fs]
   [babashka.process :as proc]
   [clojure.string :as string]))

(set! *warn-on-reflection* true)

(defn- -is-git-repo? [repo-root]
  (let [git-dir (fs/file repo-root ".git")]
    (and (fs/exists? git-dir)
         (fs/directory? git-dir))))

(def is-git-repo?
  (memoize -is-git-repo?))

(defn- out->strings
  [{:keys [out err exit] :as result}]
  (when-not (= 0 exit)
    (throw (ex-info err (select-keys result [:out :err :exit]))))

  (let [out (string/trim out)]
    (when (seq out)
      (-> out
          (string/split-lines)
          (vec)))))

(defn run-cmd! [dir & cmd]
  (-> (proc/shell {:dir (str dir)
                   :out :string
                   :err :string}
                  (string/join " " (filter identity cmd)))
      (out->strings)))
