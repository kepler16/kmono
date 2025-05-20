Execute Clojure commands with aliases across workspace packages that contain the specified
aliases.

Automatically filters packages based on whether they have the requested alias defined in
their `deps.edn` file. Useful for running tests, builds, or other Clojure-based tasks
across multiple packages.

Examples:

```bash
# Run tests in all packages that have a :test alias
#
# This will result in `clojure -M:test` being run in all workspace packages containing
# a `:test` alias.
kmono run -M :test

# Run build tools in packages with :build alias
kmono run -T :build

# Run exec function in packages with :my-exec alias
kmono run -X :my-exec :fn some-fn

# Run only in changed packages
kmono run --changed -M :test

# Run in packages changed since main branch
kmono run --changed-since origin/main -M :test

# Run with package filtering
kmono run -F :com.example/api -M :test

# Run with limited concurrency
kmono run -c 2 -M :test

# Pass additional arguments to the Clojure command
kmono run -M :test --focus my-test-ns
```

Only executes in packages that actually contain the specified alias, automatically
skipping packages without the alias. Each command runs with the package directory as the
working directory.

By default, commands run in dependency order to ensure proper build sequences, but this
can be disabled for independent operations.
