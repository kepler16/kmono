(ns k16.kmono.workspace
  (:require
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.packages :as core.packages]))

(defn resolve-workspace-context!
  "A helper function for resolving the workspace context. This combines:

   1.  Finding the project root
   2.  Resolving the kmono workspace config for the project
   3.  Resolving the initial package graph

   Returns a map containing:

   - `:root` - The project root
   - `:config` - The workspace config
   - `:packages` - The packages graph

   Will throw an exception if the project root cannot be resolved, if anything
   is incorrectly configured, or fails to resolve."
  ([] (resolve-workspace-context! nil))
  ([dir]
   (let [project-root (core.fs/find-project-root! dir)
         workspace-config (core.config/resolve-workspace-config project-root)
         packages (core.packages/resolve-packages project-root workspace-config)]
     {:root project-root
      :config workspace-config
      :packages packages})))
