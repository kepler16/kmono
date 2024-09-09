(ns k16.kmono.cli.parser
  (:require
   [babashka.cli :as cli]
   [clojure.string :as str]))

(defn- merge-globals [command-config globals]
  (update command-config
          :global-options
          (fn [current-globals]
            (merge globals current-globals))))

(defn show-summary [{:keys [options global-options commands
                            command desc version summary-path]}]
  (println (str command (when desc (str " - " desc)) "\n"))

  (println "Usage:")
  (let [path (conj summary-path command)]
    (println (str (str/join " " path) " [opts] <args>")))

  (when version
    (println)
    (println "Version:")
    (println version))

  (when (seq commands)
    (println)
    (println
     (cli/format-table
      {:rows (mapv (fn [{:keys [command desc]}]
                     [command desc])
                   commands)
       :indent 0})))

  (when (seq options)
    (println "\nOptions")
    (println (cli/format-opts {:spec options})))

  (when (seq global-options)
    (println "\nGlobal Options")
    (println (cli/format-opts {:spec global-options}))))

(defn parse-opts [opts spec]
  (into {}
        (map (fn [[key value]]
               (let [{:keys [parse-fn]} (get spec key)]
                 (if parse-fn
                   [key (parse-fn value)]
                   [key value]))))
        opts))

(defn make-commands-vector
  ([command-config] (make-commands-vector command-config nil))
  ([command-config commands]
   (let [{:keys [command path run-fn
                 options global-options]}
         command-config

         spec (merge global-options options)

         cmd {:cmds (if (nil? commands)
                      []
                      (vec (conj path command)))
              :fn (fn run-command-fn [{:keys [opts args]}]
                    {:opts (parse-opts opts spec)
                     :args args
                     :run-fn run-fn
                     :config command-config})

              :spec spec}]
     (reduce
      (fn [commands subcommand]
        (make-commands-vector
         (-> subcommand
             (merge-globals (:global-options command-config))
             (assoc :path (:cmds cmd))
             (assoc :summary-path (conj (or (:summary-path command-config) []) command)))
         commands))

      (conj (vec commands) cmd)
      (:commands command-config)))))

(defn run-cli [config args]
  (let [commands (make-commands-vector config)

        {:keys [opts args run-fn config]}
        (cli/dispatch commands args)]

    (cond
      (:help opts) (show-summary config)
      (nil? run-fn) (show-summary config)

      run-fn (run-fn opts args))))
