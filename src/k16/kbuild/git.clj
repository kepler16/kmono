(ns k16.kbuild.git
  (:require
   [babashka.process :as p]
   [clojure.string :as string]))

(defn- out->strings
  [{:keys [out err] :as result}]
  (if (seq err)
    (throw (ex-info err {:body result}))
    (-> (string/trim out)
        (string/split-lines)
        (vec))))

(defn git [dir & cmd]
  (-> (p/shell {:dir dir
                :out :string
                :err :string}
               (string/join cmd))
      (out->strings)))

(defn get-sorted-tags
  "Returns all tags sorted by creation date (descending)"
  [repo-path]
  (git repo-path "git tag --sort=-creatordate"))

(defn subdir-changes
  [sub-dir tag]
  (let [out (git sub-dir
                 "git log --pretty=format:\"%s\" "
                 tag "..HEAD -- .")]
    (if (coll? out)
      (vec out)
      [out])))

(def change-type
  {:patch #{:fix :patch :release}
   ;; happens on any change to package
   :build (constantly true)
   :minor #{:minor :feat}
   :major #{:major :breaking}})

(defn bump-type
  [changes]
  (if-let [prefixes (some->> changes
                             (seq)
                             (map (fn [c]
                                    (-> (string/split c #":")
                                        (first)
                                        (string/trim)
                                        (keyword)))))]
    (condp some (set prefixes)
      (change-type :major) :major
      (change-type :minor) :minor
      (change-type :patch) :patch
      :build)
    :none))

