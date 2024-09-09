(ns k16.kmono.cli.main
  (:require
   [babashka.process :as proc]
   [cli-matic.core :as cli]
   [clojure.string :as str]
   [k16.kmono.cli.commands.cp :as commands.cp]
   [k16.kmono.cli.commands.exec :as commands.exec]
   [k16.kmono.cli.commands.repl :as commands.repl]
   [k16.kmono.cli.commands.run :as commands.run]
   [k16.kmono.cli.commands.clojure :as commands.clojure]
   [k16.kmono.log :as log])
  (:gen-class))

(set! *warn-on-reflection* true)

(defmacro version []
  (let [res (proc/sh (str/split "git describe --abbrev=0 --tags" #" "))]
    (str/replace (str/trim (:out res)) #"v" "")))

(defn make-error-handler [command]
  (fn [props]
    (try
      (command props)
      (catch Exception ex
        (let [data (ex-data ex)]
          (if (= "kmono" (namespace (:type data)))
            (do (log/error (ex-message ex))
                (println data))
            (println ex)))
        (System/exit 1)))))

(defn with-error-handling [commands]
  (mapv
   (fn [command]
     (update command :runs make-error-handler))
   commands))

(def cli-configuration
  {:command "kmono"
   :description "A cli for managing clojure (mono)repos"
   :version (version)
   :opts [{:as "Run commands as if in this directory"
           :option "dir"
           :short "d"
           :type :string}]
   :subcommands (with-error-handling
                  [commands.cp/command
                   commands.repl/command
                   commands.exec/command
                   commands.run/command
                   commands.clojure/command])})

(defn- -main [& args]
  (cli/run-cmd args cli-configuration))
