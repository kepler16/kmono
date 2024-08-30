(ns k16.kmono.cli.common.config)

(defn merge-workspace-config
  [workspace-config {:keys [package-aliases main-aliases aliases]}]
  (cond-> workspace-config
    package-aliases (assoc :package-aliases package-aliases)
    main-aliases (assoc :main-aliases main-aliases)
    aliases (assoc :aliases aliases)))
