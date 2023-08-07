(ns k16.kbuild.util
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.string :as string]))

(defn- out->strings
  [{:keys [out err] :as result}]
  (if (seq err)
    (throw (ex-info err {:output result}))
    (-> (string/trim out)
        (string/split-lines)
        (vec))))

(defn git [dir & cmd]
  (-> (apply p/shell {:dir dir
                      :out :string
                      :err :string}
             cmd)
      (out->strings)))

(defn get-latest-tags
  [repo-path]
  (git repo-path "git tag --sort=-creatordate"))

(defn subdir-changes
  [repo-path sub-dir]
  (let [tag (get-latest-tags repo-path)
        out (git repo-path
                 "git log --pretty=format:\"%s\""
                 tag
                 "..HEAD -- ./"
                 sub-dir)]
    (if (coll? out)
      (vec out)
      [out])))

(def change-types
  {:patch #{:fix :patch :release}
   :none #{:chore :refactor}
   :minor #{:minor :feat}
   :major #{:major :breaking}})

(defn bump-type
  [changes]
  (let [prefixes (map (fn [c]
                        (-> (string/split c #":")
                            (first)
                            (string/trim)
                            (keyword)))
                      changes)]
    (condp some (set prefixes)
      (:major change-types) :major
      (:minor change-types) :minor
      (:patch change-types) :patch
      :none)))

(comment
  (get-latest-tags (fs/file "/Users/armed/Developer/myself/nvim-paredit"))
  nil)
