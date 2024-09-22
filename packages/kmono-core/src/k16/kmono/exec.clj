(ns k16.kmono.exec
  (:require
   [babashka.process :as proc]
   [clojure.string :as str]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.schema :as core.schema])
  (:import
   java.util.concurrent.Semaphore))

(def ^:no-doc ?ProcsResult
  [:vector
   [:map-of
    :string
    [:map
     [:success? :boolean]
     [:output :string]]]])

(def ^:no-doc ?JobResult
  [:tuple :boolean ?ProcsResult])

(defn- run-external-cmd
  [{:keys [package command on-event]}]
  (on-event {:type :proc-start
             :package package
             :command command})

  (let [result (proc/sh {:dir (:absolute-path package)}
                        (str/join " " command))]

    (on-event {:type :proc-finish
               :success (= 0 (:exit result))
               :exit (:exit result)
               :out (:out result)
               :err (:err result)
               :package package
               :command command})

    (assoc result :package package)))

(defn- await-procs [procs]
  (let [results (mapv deref procs)
        failed? (some
                 (fn [result]
                   (not= 0 (:exit result)))
                 results)]

    [(not failed?) results]))

(def ^:no-doc ?RunOpts
  [:map
   [:packages core.schema/?PackageMap]
   [:command [:or [:sequential :string] ifn?]]
   [:concurrency {:optional true} :int]
   [:run-in-order {:optional true} :boolean]
   [:on-event ifn?]])

(defn run-external-cmds
  {:malli/schema [:-> ?RunOpts [:sequential [:map [:success :boolean]]]]}
  [{:keys [packages command on-event
           concurrency run-in-order]}]
  (let [exec-order (if (or (not (boolean? run-in-order))
                           run-in-order)
                     (core.graph/parallel-topo-sort packages)
                     [(keys packages)])

        cores (.availableProcessors (Runtime/getRuntime))
        semaphore (Semaphore. (or concurrency cores))

        total-stages (count exec-order)]

    (loop [stages exec-order
           idx 1
           stage-results []]
      (if (seq stages)
        (let [stage (first stages)

              _ (on-event {:type :stage-start
                           :id idx
                           :total total-stages
                           :stage stage})

              op-procs
              (mapv
               (fn [pkg-name]
                 (future
                   (.acquire semaphore)

                   (try
                     (let [pkg (get packages pkg-name)]
                       (run-external-cmd
                        {:package pkg
                         :command (if (fn? command)
                                    (command pkg)
                                    command)
                         :on-event on-event}))
                     (finally
                       (.release semaphore)))))
               stage)

              [success? results] (await-procs op-procs)]

          (on-event {:type :stage-finish
                     :stage stage
                     :success success?
                     :results results})

          (recur (rest stages)
                 (inc idx)
                 (conj stage-results {:success success?
                                      :results results})))
        stage-results))))
