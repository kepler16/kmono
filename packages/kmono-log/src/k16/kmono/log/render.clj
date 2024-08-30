(ns k16.kmono.log.render)

(defn render-package-name [pkg]
  (str "@|white " (namespace pkg) "|@"
       "@|bold /|@"
       "@|yellow " (name pkg) "|@"))
