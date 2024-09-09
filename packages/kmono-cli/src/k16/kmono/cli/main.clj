(ns k16.kmono.cli.main
  (:require
   [babashka.process :as proc]
   [clojure.string :as str]
   [k16.kmono.cli.commands.clojure :as commands.clojure]
   [k16.kmono.cli.commands.cp :as commands.cp]
   [k16.kmono.cli.commands.exec :as commands.exec]
   [k16.kmono.cli.commands.repl :as commands.repl]
   [k16.kmono.cli.commands.run :as commands.run]
   [k16.kmono.cli.parser :as cli.parser]
   [k16.kmono.log :as log])
  (:gen-class))

(set! *warn-on-reflection* true)

(defmacro version []
  (let [res (proc/sh (str/split "git describe --abbrev=0 --tags" #" "))]
    (str/replace (str/trim (:out res)) #"v" "")))

(def cli-configuration
  {:command "kmono"
   :desc "A cli for managing clojure (mono)repos"
   :version (version)
   :global-options {:dir {:desc "Run commands as if in this directory"
                          :alias :d
                          :coerce :string}
                    :packages {:desc "A glob string describing where to search for packages (default: 'packages/*')"
                               :alias :p
                               :coerce :string}
                    :verbose {:desc "Enable verbose output"
                              :alias :v
                              :coerce :boolean}
                    :help {:alias :h
                           :coerce :boolean}}
   :commands [commands.cp/command
              commands.repl/command
              commands.exec/command
              commands.run/command
              commands.clojure/command
              {:command "version"
               :desc "Print the current version of kmono"
               :run-fn (fn [_ _]
                         (println (version)))}]})

(defn -main [& args]
  (try
    (cli.parser/run-cli cli-configuration args)
    (System/exit 0)
    (catch Exception ex
      (let [data (ex-data ex)]
        (if (and (:type data)
                 (= "kmono" (namespace (:type data))))
          (do (log/error (ex-message ex))
              (println data))
          (println ex)))
      (System/exit 1))))
