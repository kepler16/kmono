(ns k16.kmono.cli.common.log
  (:require
   [clojure.string :as str]
   [k16.kmono.log :as log]
   [k16.kmono.log.render :as log.render]))

(defn- stage-started [{:keys [id total]}]
  (log/info (str "Stage " id " of " total " started")))

(defn- render-command [command]
  (let [command (str/join " " command)
        shortened (subs command 0 (min (count command) 20))
        with-elide (if (not= (count shortened) (count command))
                     (str shortened "...")
                     shortened)]
    [[:system-grey with-elide]]))

(defn- proc-started [{:keys [package command]}]
  (log/log-raw (-> ["    "]
                   (into (log.render/render-package-name (:fqn package)))
                   (into [[:system-aqua " [running] "]])
                   (into (render-command command)))))

(defn- proc-finished [{:keys [package success out err]}]
  (let [message (if success
                  [:system-green " [succes] "]
                  [:system-red " [failed] "])]

    (log/log-raw (-> ["    "]
                     (into (log.render/render-package-name (:fqn package)))
                     (into [message])))

    (when (seq out)
      (log/log out))
    (when (seq err)
      (log/log err))))

(defn handle-event [event]
  (case (:type event)
    :stage-start (stage-started event)
    :stage-finish (log/info "Stage finished")

    :proc-start (proc-started event)
    :proc-finish (proc-finished event)))
