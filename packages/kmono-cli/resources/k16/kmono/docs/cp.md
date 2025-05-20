Generate a classpath string from a Clojure project, augmented with workspace package
information and aliases.

Similar to `clojure -Spath` but enhanced with kmono's workspace awareness. Particularly
useful for editor integration (clojure-lsp), custom tooling, and debugging classpath
issues.

Examples:

```bash
# Basic classpath generation
kmono cp

# Include specific root aliases
kmono cp -A :dev,:test

# Include package aliases (e.g., all :test aliases from workspace packages)
kmono cp -P ':*/test'

# Combine root and package aliases
kmono cp -A :dev -P ':*/test,:*/dev'
```

For clojure-lsp integration, use as a drop-in replacement for `clojure -Spath`:

```clojure
;; .lsp/config.edn
{:project-specs [{:project-path "deps.edn"
                  :classpath-cmd ["kmono" "cp"]}]}
```
