(ns k16.kbuild.adapter)

(defprotocol Adapter
  (get-managed-deps [this])
  (prepare-deps-env [this changes])
  (get-kbuild-config [this]))

