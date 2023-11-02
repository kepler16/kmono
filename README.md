# 👘 kmono - monorepo build/release tool

## Overview

kmono is a command runner tool designed specifically for managing Clojure monorepos. With features like automatic change detection via git, semantic versioning through commit prefixes, and parallel excution, kmono aims to simplify and automate your monorepo experience.

## Features

- **Change Detection**: Calculates changes per package by parsing git history for each package directory.
- **Semantic Versioning**: Uses Angular-style commit prefixes to determine the type of version bump.
- **Dependency Graph**: Creates a graph of interdependent packages and builds them in parallel groups.
- **Local Dependency Management**: Replaces `local/root` dependencies with real version numbers during the build.

## What kmono is not

- Not a build tool - it doesn't know how to build a package, it only runs a provided build command
- Not a release tool - see above

## Which means that

- Each package must know how to build itself and provide a command
- Each package must know how to release itself and provide a command
- Each package can provide additional custom commands e.g. `just test`

## Limitations

- Relies on git tags for versioning.
- Requires a baseline tag; otherwise, the version defaults to `0.0.0.0`.
- Currently only Clojure deps.edn specific.

## Quick Start (deps.edn projects)

**Directory Structure**

Ensure your monorepo has the following directory structure:

```
repo-root
  ├── packages
  │   ├── package1
  │   │   └── deps.edn
  │   ├── package2
  │   │   └── deps.edn
  │   └── package3
  │       └── deps.edn
  └── deps.edn (for starting monorepo repl and kmono alias)
```

**Basic Usage**

Root `deps.edn`:
```clojure
{:deps {;; add local/root deps for all packages to start repo level REPL (optional)
        {kepler16/package1 {:local/root "packages/package1"}}}
 :aliases {:kmono {:deps {kepler16/kmono {:mvn/version "1.0.0.0"}}
                   :ns-default k16.kmono.api}}}
```

All commands below runs against all changed packages.

Build a package:

```sh
clojure -T:kmono run :exec :build
```

Deploy a package:

```sh
clojure -T:kmono run :exec :release
```

Run a custom command:

```sh
clojure -T:kmono run :exec \"just test\"
```

During each command following environment variables are available for each package:
- `KMONO_PKG_VERSION` - version of a package
- `KMONO_PKG_DEPS` - dependency overrides for a package
- `KMONO_PKG_NAME` - full name of a package artifact, e.g. `com.example/my-lib`

### Additional CLI arguments

One can pass this CLI args to `-T:kmono` command
- `:include-unchanged?` -jk `boolean`, should kmono include unchanged packages into build process (default `false`)
- `:snapshot?` - `boolean`, whether release is a snapshot, useful when releasing from PR branch (default `true`)
- `:create-tags?` - `boolean`, should kmono create new baseline tags after release. Usually baseline tags are created on master releases, not snapshots (default `false`)
- `:glob` - `string`, where to search for packages, defaults to `"packages/*"`
- `:build-cmd` - `string`, overrides `build-cmd` option from package kmono config
- `:release-cmd` - `string`, overrides `release-cmd` option from package kmono config
- `:repo-root` - `string`, root of the monorepo, defaults to `"."`
- `:dry-run?` - `boolean`, dry run to check command output (default `false`)

### Package configuration

Package-specific configurations are done within each `deps.edn` file under the `:kmono/config` key:

```clj
:kmono/config {;; maven artifact's group
               :group com.example
               ;; maven's artifactId
               ;; this is optional and inferred from a package's dir name
               :artifact my-lib
               ;; below are two builtin commands, each package must provide this commands
               ;; run :exec :build
               :build-cmd "just build"
               ;; run :exec :release
               :release-cmd "just release"}
```

## Quick start (kmono.edn) - experimental 

If a package directory has `kmono.edn` file it will be preferred. Configuration schema is the same, but without `:kmono/config` top level key:

```clj
{;; maven artifact's group
 :group com.example
 ;; maven's artifactId
 ;; this is optional and inferred from a package's dir name
 :artifact my-lib
 ;; below are two builtin commands, each package must provide this commands
 ;; run :exec :build
 :build-cmd "just build"
 ;; run :exec :release
 :release-cmd "just release"
 ;; since we are not relying on deps.edn's local/root anymore
 ;; list of local packages this package depends on
 :local-deps [group1/package1 group1/package2]}
```
### Differences from deps.edn

- Package can be any non deps.edn project and even non clojure
- In deps.edn projects kmono overrides local deps by storing extra deps map in `KMONO_PKG_DEPS`, for generic packages
`KMONO_PKG_DEPS` env variable contains semicolon separated package name/version: `group1/package1@1.0.0.0;group2/package2@1.0.0.0`.
Package's build tool must know how to handle that.

