(ns k16.kmono.log.render)

(defn render-package-name [pkg]
  [[:system-silver (namespace pkg)]
   [:bold "/"]
   [:system-yellow (name pkg)]])
