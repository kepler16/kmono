(ns k16.kbuild.adapters.clojure-deps
  (:require
   [k16.kbuild.adapter :refer [Adapter]]
   [clojure.edn :as edn]
   [clojure.walk :as walk]))

(defn bump-deps
  [edn-string deps]
  (let [depsfile (edn/read-string edn-string)
        bump-map (into {} (map (juxt (comp symbol :coordinate) :version)) deps)]
    (walk/postwalk
     (fn [step-entity]
       (or (when (map-entry? step-entity)
             (let [[koord v] step-entity]
               ;; replace only local/root deps
               (when (:local/root v)
                 (when-let [version (get bump-map koord)]
                   [koord {:mvn/version version}]))))
           step-entity))
     depsfile)))

(def adapter
  (reify
    Adapter
    (update-deps! [_ path deps]
      (let [edn-string (slurp path)]
        (bump-deps edn-string deps)))))

(comment
  (def deps-str (slurp "/Users/armed/Developer/k16/transit/micro/packages/http/deps.edn"))
  (def bumps [{:coordinate "transit-engineering/telemetry.clj"
               :version "1.89.2"}
              {:coordinate "transit-engineering/test.clj"
               :version "1.77.2"}])
  (binding [*print-namespace-maps* false]
    (let [contents (with-out-str (clojure.pprint/pprint
                                  (bump-deps deps-str bumps)))]
      (spit "deps.test.edn" contents)))

  nil)
