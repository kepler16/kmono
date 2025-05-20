Execute external commands (non-Clojure) across multiple workspace packages.

Runs external commands in each workspace package directory, useful for shell scripts,
build tools, tests, or any external commands across your monorepo.

Examples:

```bash
# Run npm test in all packages
kmono exec npm test

# Run a shell script in all packages
kmono exec ./scripts/build.sh

# Run tests only in changed packages
kmono exec --changed npm test

# Run commands in packages that changed since main branch
kmono exec --changed-since origin/main pytest

# Run in specific packages only
kmono exec -F :com.example/api,:com.example/web npm run build

# Run with limited concurrency
kmono exec -c 2 ./slow-command.sh

# Run commands without dependency ordering (faster but potentially unsafe)
kmono exec --run-in-order=false npm install
```

Each command is executed with the package directory as the working directory. If any
command fails, `kmono exec` continues running in other packages and reports failures at
the end.

By default, commands run in dependency order to respect package relationships, but this
can be disabled for independent operations like tests or linting.
