(ns k16.kmono.cli.main
  (:require
   [babashka.process :as proc]
   [cli-matic.core :as cli]
   [clojure.string :as str]
   [k16.kmono.cli.commands.cp :as commands.cp]
   [k16.kmono.cli.commands.exec :as commands.run]
   [k16.kmono.cli.commands.repl :as commands.repl]
   [k16.kmono.cli.commands.run :as commands.tool])
  (:gen-class))

(set! *warn-on-reflection* true)

(defmacro version []
  (let [res (proc/sh (str/split "git describe --abbrev=0 --tags" #" "))]
    (str/replace (str/trim (:out res)) #"v" "")))

(def cli-configuration
  {:command "kmono"
   :description "A cli for managing clojure (mono)repos"
   :version (version)
   :opts [{:as "Run commands as if in this directory"
           :option "dir"
           :short "d"
           :type :string}]
   :subcommands [commands.cp/command
                 commands.repl/command
                 commands.run/command
                 commands.tool/command]})

(defn- -main [& args]
  (cli/run-cmd args cli-configuration))
