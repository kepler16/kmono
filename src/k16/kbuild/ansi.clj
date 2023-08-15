(ns k16.kbuild.ansi)

(defn red
  [& chunks]
  (str "\033[31m"
       (apply str chunks)
       "\033[0m"))

(defn green
  [& chunks]
  (str "\033[32m"
       (apply str chunks)
       "\033[0m"))
