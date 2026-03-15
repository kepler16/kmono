(ns k16.kmono.cli.common.config)

(defn merge-workspace-config
  [workspace-config {:keys [package-aliases aliases ignore-changes]}]
  (cond-> workspace-config
    package-aliases (assoc :package-aliases package-aliases)
    aliases (update :aliases into aliases)
    ignore-changes (assoc :ignore-changes ignore-changes)))
