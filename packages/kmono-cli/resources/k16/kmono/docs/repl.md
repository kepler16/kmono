Start a Clojure REPL with workspace packages and aliases automatically configured.

Enhanced REPL with workspace awareness that automatically includes workspace packages in
the classpath and applies configured aliases from both the root project and workspace
packages.

Configuration example:

```clojure
;; deps.local.edn
{:kmono/workspace {;; Always include these aliases when running `kmono repl`
                   :repl-aliases [:nrepl :cider]
                   :aliases [:dev]
                   :package-aliases [:*/dev]}}
```

Examples:

```bash
# Start basic REPL with workspace packages
kmono repl

# Start REPL with additional root aliases
kmono repl -A :dev,:test

# Start REPL with specific package aliases
kmono repl -P ':*/test,:a/dev'

# Start REPL with both root and package aliases
kmono repl -A :cider -P ':*/dev'
```

Use `deps.local.edn` for personal REPL preferences without affecting the shared project
configuration.
