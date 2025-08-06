(ns ^:no-doc k16.kmono.core.thread
  (:import
   java.util.concurrent.ExecutionException
   java.util.concurrent.ExecutorService
   java.util.concurrent.Executors))

(set! *warn-on-reflection* true)

(def ^:dynamic ^:private *executor*
  (Executors/newVirtualThreadPerTaskExecutor))

(defmacro vthread [& body]
  `(let [^Callable fn# (bound-fn [] ~@body)]
     (ExecutorService/.submit *executor* fn#)))

(defn batch
  "Execute the given `coll` in batches of `batch-size`.

   Each batch will be executed in parallel and a new batch will only be
   executed when all items in the previous batch have completed.

   If any items fail the entire operation fails."
  ([f batch-size]
   (comp
    (partition-all batch-size)
    (map (fn execute-batch! [batch]
           (->> batch
                (mapv (fn execute-item! [value]
                        (vthread (f value))))
                (mapv (fn [task]
                        (try (deref task)
                             (catch ExecutionException ex
                               (throw (ex-cause ex)))))))))

    cat))
  ([f batch-size coll]
   (into []
         (batch f batch-size)
         coll)))
