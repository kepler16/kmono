(ns k16.kbuild.adapters.clojure-deps
  (:require
   [k16.kbuild.adapter :refer [Adapter]]
   [clojure.edn :as edn]
   [clojure.walk :as walk]))

(defn update-locals
  [deps-edn version-map]
  (walk/postwalk
   (fn [step-entity]
     (or (when (map-entry? step-entity)
           (let [[pname v] step-entity]
             ;; replace only local/root deps
             (when (:local/root v)
               (when-let [version (get version-map (str pname))]
                 [pname {:mvn/version version}]))))
         step-entity))
   deps-edn))

(defn- local?
  [[_ coord]]
  (boolean (:local/root coord)))

(defn get-locals
  "Gather all dependencies that are local/root and return a list of symbols"
  [deps-edn]
  (into
   (->> deps-edn
        :deps
        (filter local?)
        (mapv (comp str first)))
   (->> deps-edn
        :aliases
        (vals)
        (map #(vals (select-keys % [:deps :extra-deps :replace-deps])))
        (flatten)
        (apply merge)
        (filter local?)
        (map (comp str first)))))

(defn adapter
  [path]
  (reify Adapter
    (update-deps! [_ dep-versions]
      (let [deps-edn (-> path (slurp) (edn/read-string))
            with-updated-locals (update-locals deps-edn dep-versions)]
        (binding [*print-namespace-maps* false]
          (spit path with-updated-locals))))

    (get-deps [_]
      (let [deps-edn (-> path (slurp) (edn/read-string))]
        (get-locals deps-edn)))))

(comment
  (def deps-str (slurp "/Users/armed/Developer/k16/transit/micro/packages/http/deps.edn"))
  (def deps-edn (edn/read-string deps-str))
  (get-locals (edn/read-string deps-str))
  (->> deps-edn
       :aliases
       (vals)
       (map #(vals (select-keys % [:deps :extra-deps :replace-deps])))
       (flatten)
       (apply merge)
       (filter local?))
  (def bumps {"transit-engineering/telemetry.clj" "1.89.2"
              "transit-engineering/test.clj" "1.77.2"})
  (binding [*print-namespace-maps* false]
    (update-locals deps-edn bumps))

  nil)
