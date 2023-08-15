(ns k16.kbuild.proc
  (:require
   [babashka.process :as bp]
   [k16.kbuild.ansi :as ansi]))

(def ?PackageName :string)

(def ?BabashkaProcess :map)

(def ?ProcsResult
  [:vector
   [:map-of
    :string
    [:map
     [:success? :boolean]
     [:output :string]]]])

(def ?ProcResult
  [:map
   [:success? :boolean]
   [:output :string]])

(def ?ProcAwaitResults
  [:map-of ?PackageName ?ProcResult])

(def ?CmdProc
  [:tuple ?PackageName ?BabashkaProcess])

(defn- failed?
  [proc]
  (not (-> proc (deref) :exit (zero?))))

(defn- rotate [v]
  (into (vec (drop 1 v)) (take 1 v)))

(defn- ->success
  {:malli/schema [:=> [:cat ?BabashkaProcess] ?ProcResult]}
  [proc]
  {:success? true
   :output @(:out proc)})

(defn- ->failure
  {:malli/schema [:=> [:cat ?BabashkaProcess] ?ProcResult]}
  [proc]
  {:success? false
   :output @(:err proc)})

(defn await-procs
  {:malli/schema [:=>
                  [:cat [:sequential ?CmdProc] :boolean]
                  [:tuple :boolean ?ProcAwaitResults]]}
  [package-procs terminate-on-failure?]
  (loop [package-procs package-procs
         results {}]
    (let [[pkg-name proc] (first package-procs)]
      (if proc
        (if (bp/alive? proc)
          (do (Thread/sleep 200)
              (recur (rotate package-procs) results))
          (if (failed? proc)
            (if terminate-on-failure?
              [false (assoc results pkg-name (->failure proc))]
              (do (println "\t" (ansi/red "failed") pkg-name)
                  (recur (rest package-procs) (assoc results pkg-name (->failure proc)))))
            (do (println "\t" (ansi/green "success") pkg-name)
                (recur (rest package-procs) (assoc results pkg-name (->success proc))))))
        [true results]))))

