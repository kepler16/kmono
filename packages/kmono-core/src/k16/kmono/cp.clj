(ns k16.kmono.cp
  (:require
   [babashka.process :as proc]
   [clojure.string :as str]
   [k16.kmono.core.deps :as core.deps]))

(set! *warn-on-reflection* true)

(defn serialize-aliases
  "Serialize a given sequence of `aliases` to a colon delimited string."
  [aliases]
  (reduce
   (fn [acc alias]
     (if (namespace alias)
       (str acc ":" (namespace alias) "/" (name alias))
       (str acc ":" (name alias))))
   ""
   aliases))

(defn collect-aliases
  "Collect a set of 'active' aliases from the workspace.

  This function works by:

  1) Discovering all available package aliases in the workspace and filtering
  them based on the `:package-aliases` config from the provided
  `workspace-config`.
  2) Merging this set with the aliases defined in the `:aliases` key on the
  `workspace-config`.
  3) Adding the special `:kmono/packages` alias which contains the base package
  classpaths.

  This function is designed to be used for collect the relevant set of aliases
  to provide to the clojure flags `-A`, `-X`, `-M`."
  [workspace-config packages]
  (let [root-aliases (:aliases workspace-config)
        package-alias-globs (:package-aliases workspace-config)
        package-aliases (->> packages
                             (core.deps/filter-package-aliases package-alias-globs)
                             (mapcat second))

        aliases (concat [:kmono/packages]
                        root-aliases
                        package-aliases)]

    (set aliases)))

(defn generate-classpath-command
  "Generate an augmented `clojure -Spath` command string."
  [project-root workspace-config packages]
  (let [sdeps-aliases (core.deps/generate-sdeps-aliases project-root packages)
        sdeps {:aliases sdeps-aliases}

        aliases (collect-aliases workspace-config packages)

        command ["clojure"
                 "-Sdeps" (str "'" (str/trim (prn-str (into (sorted-map) sdeps))) "'")
                 (str "-A" (serialize-aliases aliases))
                 "-Spath"]]

    (str/join " " command)))

(defn resolve-classpath
  "Resolve a classpath string for a workspace.

  This works by shelling out to `clojure -Spath` with additional flags generated
  from analysing the workspace.

  See [[generate-classpath-command]] for the command construction logic."
  [project-root workspace-config packages]
  (:out
   (proc/shell
    {:dir (str project-root)
     :out :string
     :err :string}
    (generate-classpath-command project-root workspace-config packages))))
