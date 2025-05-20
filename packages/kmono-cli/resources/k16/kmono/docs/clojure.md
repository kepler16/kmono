Run Clojure CLI commands with workspace packages and aliases automatically integrated.

Drop-in replacement for the standard `clojure` CLI enhanced with workspace awareness.
Automatically includes workspace packages and their aliases, allowing you to work with
your entire monorepo as a single project.

Examples:

```bash
# Start a REPL with workspace packages available
kmono clojure

# Run with specific aliases
kmono clojure -A :dev

# Use package aliases (e.g., all :test aliases from packages)
kmono clojure -P ':*/test' -M :test

# Run a main function with workspace awareness
kmono clojure -M :my-main-alias

# Execute a tool
kmono clojure -T :build

# Run with exec
kmono clojure -X :my-exec-alias

# Pass through standard clojure arguments
kmono clojure -M:test -- -m kaocha.runner

# Combine workspace and package aliases
kmono clojure -A :dev -P ':*/dev,:*/test'
```

The command automatically creates aliases for workspace packages and lifts
package-specific aliases to the root level with namespaced names.

Use the global `-v` flag to see the actual `clojure` command being executed for debugging
classpath issues.
