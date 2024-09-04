(ns k16.kmono.cp
  (:require
   [babashka.process :as proc]
   [clojure.string :as str]
   [k16.kmono.core.deps :as core.deps]))

(set! *warn-on-reflection* true)

(defn serialize-aliases [aliases]
  (reduce
   (fn [acc alias]

     (if (namespace alias)
       (str acc ":" (namespace alias) "/" (name alias))
       (str acc ":" (name alias))))

   ""
   aliases))

(defn generate-aliases
  [project-root workspace-config packages]

  (let [{:keys [combined package-aliases]}
        (core.deps/resolve-aliases project-root packages)

        root-aliases (:aliases workspace-config)
        package-alias-globs (:package-aliases workspace-config)
        package-aliases (core.deps/filter-package-aliases
                         package-aliases package-alias-globs)

        aliases (concat [:kmono/packages]
                        root-aliases
                        (keys package-aliases))]

    {:sdeps {:aliases combined}
     :aliases aliases}))

(defn generate-classpath-command
  [project-root workspace-config packages]

  (let [{:keys [sdeps aliases]}
        (generate-aliases
         project-root workspace-config packages)

        command
        ["clojure"
         "-Sdeps" (str "'" (str/trim (prn-str (into (sorted-map) sdeps))) "'")
         (str "-A" (serialize-aliases aliases))
         "-Spath"]]

    (str/join " " command)))

(defn resolve-classpath [project-root workspace-config packages]
  (:out
   (proc/shell
    {:dir (str project-root)
     :out :string
     :err :string}
    (generate-classpath-command project-root workspace-config packages))))