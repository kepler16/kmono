(ns k16.kmono.test.helpers.repo
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [k16.kmono.test.helpers.cmd :as cmd]))

(defn setup-multi-package-repo []
  (let [uuid (str (random-uuid))

        repo (fs/file (str ".test-repos/" uuid))]
    (fs/create-dirs repo)
    (fs/copy-tree (io/resource "kmono/templates/monorepo")
                  repo)

    (cmd/run-cmd! repo "git" "init")
    (cmd/run-cmd! repo "git" "add" ".")
    (cmd/run-cmd! repo "git" "commit" "-m" "init")

    (cmd/run-cmd! repo "git config advice.detachedHead false")

    (.getAbsolutePath repo)))

(def ^:dynamic *repo* nil)

(defn with-test-repo [test]
  (let [repo (setup-multi-package-repo)]
    (try
      (binding [*repo* repo]
        (test))
      (finally
        (fs/delete-tree repo)))))
