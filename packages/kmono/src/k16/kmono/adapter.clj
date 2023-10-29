(ns k16.kmono.adapter)

(defprotocol Adapter
  (get-managed-deps [this] "Returns a list (package names) of repo-local dependencies for package")
  (prepare-deps-env [this changes] "Returns an env string representing deps overrides for build/release")
  (release-published? [this version] "Returns a promise containing boolean value")
  (get-kmono-config [this] "Returns a kmono config for package"))

