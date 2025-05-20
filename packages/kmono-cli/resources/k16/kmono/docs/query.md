Query and inspect information about the workspace package graph in various formats.

Provides information about workspace packages and their relationships. Outputs package
metadata, version information, change tracking, and dependency relationships in JSON or
EDN format.

Examples:

```bash
# List all packages in JSON format
kmono query

# List all packages in EDN format
kmono query -o edn

# Query specific packages
kmono query -F :com.example/api,:com.example/web

# Include version information from git tags
kmono query --with-versions

# Only show packages that have changed since last version
kmono query --with-changes --filter-unchanged

# Show packages changed since main branch
kmono query --with-changes-since origin/main --filter-unchanged

# Only include specific fields
kmono query --include-keys name,version,path
```

Useful for debugging workspace configuration, building custom tooling, understanding
package relationships, and identifying changed packages for CI/CD pipelines.
