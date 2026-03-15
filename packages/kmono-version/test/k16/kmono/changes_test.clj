(ns k16.kmono.changes-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is use-fixtures]]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.git.commit :refer [get-current-commit]]
   [k16.kmono.git.tags :as git.tags]
   [k16.kmono.test.helpers.commit :refer [commit]]
   [k16.kmono.test.helpers.repo :refer [*repo* with-test-repo] :as helpers.repo]
   [k16.kmono.version :as kmono.version]
   [matcher-combinators.test]))

(use-fixtures :each with-test-repo)

(deftest load-package-changes-test
  (git.tags/create-tags *repo* {:tags ["com.kepler16/a@1.0.0"
                                       "com.kepler16/b@1.1.0"]})

  (fs/create-file (fs/file *repo* "packages/a/change-1"))
  (commit *repo* "fix: changed package a")

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-versions *repo*)
                      (kmono.version/resolve-package-changes *repo*))]
    (is (match? {'com.kepler16/a {:version "1.0.0"
                                  :commits [{:message "fix: changed package a"
                                             :body ""}]}
                 'com.kepler16/b {:version "1.1.0"
                                  :commits []}}
                packages))))

(deftest load-package-changes-no-version-test
  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-changes *repo*))]
    (is (match? {'com.kepler16/a {:commits [{:message "init"
                                             :body ""}]}
                 'com.kepler16/b {:commits [{:message "init"
                                             :body ""}]}}
                packages))))

(deftest load-package-changes-since-rev-test
  (let [start-commit (get-current-commit *repo*)

        _ (fs/create-file (fs/file *repo* "packages/b/change-1"))

        next-commit (commit *repo* "Change package b")

        config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-changes-since *repo* start-commit))]

    (is (match? {'com.kepler16/a {:commits []}
                 'com.kepler16/b {:commits [{:sha next-commit
                                             :message "Change package b"
                                             :body ""}]}}
                packages))))

(deftest ignore-changes-all-files-match-test
  (git.tags/create-tags *repo* {:tags ["com.kepler16/a@1.0.0"
                                       "com.kepler16/b@1.1.0"]})

  (fs/create-file (fs/file *repo* "packages/a/README.md"))
  (commit *repo* "docs: add readme to package a")

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-versions *repo*)
                      (kmono.version/resolve-package-changes *repo* {:ignore-changes [".*\\.md$"]}))]
    (is (match? {'com.kepler16/a {:version "1.0.0"
                                  :commits []}
                 'com.kepler16/b {:version "1.1.0"
                                  :commits []}}
                packages))))

(deftest ignore-changes-partial-match-test
  (git.tags/create-tags *repo* {:tags ["com.kepler16/a@1.0.0"
                                       "com.kepler16/b@1.1.0"]})

  (fs/create-file (fs/file *repo* "packages/a/README.md"))
  (fs/create-dirs (fs/file *repo* "packages/a/src"))
  (fs/create-file (fs/file *repo* "packages/a/src/core.clj"))
  (commit *repo* "feat: add code and docs to package a")

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-versions *repo*)
                      (kmono.version/resolve-package-changes *repo* {:ignore-changes [".*\\.md$"]}))]
    (is (match? {'com.kepler16/a {:version "1.0.0"
                                  :commits [{:message "feat: add code and docs to package a"
                                             :body ""}]}
                 'com.kepler16/b {:version "1.1.0"
                                  :commits []}}
                packages))))

(deftest ignore-changes-multiple-patterns-test
  (git.tags/create-tags *repo* {:tags ["com.kepler16/a@1.0.0"
                                       "com.kepler16/b@1.1.0"]})

  (fs/create-file (fs/file *repo* "packages/a/README.md"))
  (fs/create-file (fs/file *repo* "packages/a/LICENSE"))
  (commit *repo* "docs: add readme and license")

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-versions *repo*)
                      (kmono.version/resolve-package-changes *repo* {:ignore-changes [".*\\.md$" "LICENSE"]}))]
    (is (match? {'com.kepler16/a {:version "1.0.0"
                                  :commits []}
                 'com.kepler16/b {:version "1.1.0"
                                  :commits []}}
                packages))))

(deftest ignore-changes-since-rev-test
  (let [start-commit (get-current-commit *repo*)

        _ (fs/create-file (fs/file *repo* "packages/b/CHANGELOG.md"))

        _ (commit *repo* "docs: add changelog to package b")

        config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-changes-since *repo* start-commit {:ignore-changes [".*\\.md$"]}))]

    (is (match? {'com.kepler16/a {:commits []}
                 'com.kepler16/b {:commits []}}
                packages))))

(deftest ignore-changes-package-override-test
  ;; Package a declares its own :ignore-changes that overrides workspace-level
  (spit (str (fs/file *repo* "packages/a/deps.edn"))
        (pr-str {:kmono/package {:ignore-changes [".*\\.txt$"]}
                 :aliases {:test {:extra-paths ["test"]
                                  :extra-deps {'local/excluded {:local/root "../excluded"}}}}}))
  (commit *repo* "configure package a ignore-changes")

  (git.tags/create-tags *repo* {:tags ["com.kepler16/a@1.0.0"
                                       "com.kepler16/b@1.1.0"]})

  ;; Add .md files to both packages
  (fs/create-file (fs/file *repo* "packages/a/README.md"))
  (fs/create-file (fs/file *repo* "packages/b/README.md"))
  (commit *repo* "docs: add readmes")

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-versions *repo*)
                      ;; Workspace-level ignores .md files
                      (kmono.version/resolve-package-changes *repo* {:ignore-changes [".*\\.md$"]}))]
    ;; Package a uses its own patterns (*.txt), so .md is NOT ignored → changed
    (is (match? {'com.kepler16/a {:version "1.0.0"
                                  :commits [{:message "docs: add readmes"
                                             :body ""}]}}
                packages))
    ;; Package b has no override, uses workspace patterns (*.md) → unchanged
    (is (match? {'com.kepler16/b {:version "1.1.0"
                                  :commits []}}
                packages))))

(deftest ignore-changes-per-commit-filtering-test
  (git.tags/create-tags *repo* {:tags ["com.kepler16/a@1.0.0"
                                       "com.kepler16/b@1.1.0"]})

  ;; Commit 1: only a .md file (should be filtered out)
  (fs/create-file (fs/file *repo* "packages/a/README.md"))
  (commit *repo* "docs: add readme")

  ;; Commit 2: a .clj file (should be kept)
  (fs/create-dirs (fs/file *repo* "packages/a/src"))
  (fs/create-file (fs/file *repo* "packages/a/src/core.clj"))
  (commit *repo* "feat: add core module")

  (let [config (core.config/resolve-workspace-config *repo*)
        packages (->> (core.packages/resolve-packages *repo* config)
                      (kmono.version/resolve-package-versions *repo*)
                      (kmono.version/resolve-package-changes *repo* {:ignore-changes [".*\\.md$"]}))]
    ;; Only the second commit should remain
    (is (match? {'com.kepler16/a {:version "1.0.0"
                                  :commits [{:message "feat: add core module"
                                             :body ""}]}}
                packages))))
