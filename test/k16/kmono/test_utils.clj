(ns k16.kmono.test-utils
  (:require
   [babashka.fs :as fs]
   [babashka.process :as bp]
   [clojure.string :as string]))

(def repo-root ".kmono_test")

(defn delete-repo []
  (fs/delete-tree repo-root))

(defn init-repo []
  (let [p1-dir "packages/p1"
        p2-dir "packages/p2"
        dirs ["src"
              (fs/path p1-dir "src")
              (fs/path p2-dir "src")]]
    (doseq [d dirs]
      (fs/create-dirs (fs/path repo-root d)))

    (spit (fs/file repo-root "deps.edn")
          (str {:kmono/config {:group "kmono-test"
                               :artifact "root-module"
                               :build-cmd "echo 'build root'"
                               :release-cmd "echo 'release root'"}
                :deps {}
                :paths ["src"]}))

    (spit (fs/file repo-root p1-dir "deps.edn")
          (str {:kmono/config {:group "kmono-test"
                               :build-cmd "echo 'build p1'"
                               :release-cmd "echo 'release p1'"}
                :deps {}
                :paths ["src"]}))

    (spit (fs/file repo-root p2-dir "deps.edn")
          (str {:kmono/config {:group "kmono-test"
                               :build-cmd "echo 'build p2'"
                               :release-cmd "echo 'release p2'"}
                :deps {}
                :paths ["src"]}))))

(defn repo-fixture [t]
  (init-repo)
  (t)
  (delete-repo))

(defn shell-commands! [cmds]
  (into {}
        (map (fn [cmd]
               [cmd (bp/shell {:dir repo-root
                               :out :string} cmd)]))
        cmds))

(defn initialize-git! []
  (shell-commands! ["git init -q --initial-branch=main"
                    "git config user.email \"kmono@test.com\""
                    "git config user.name \"kmono\""
                    "git add ."
                    "git commit -m 'initial commit'"]))

(defn get-tags []
  (let [tags-cmd "git tag --list"]
    (-> (shell-commands! [tags-cmd])
        (get tags-cmd)
        :out
        (string/split-lines))))
