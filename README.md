<div align="center">
  <p>
    <picture>
      <source srcset="https://i.postimg.cc/M6TbTcnr/kmono-black.png" media="(prefers-color-scheme: dark)">
      <source srcset="https://i.postimg.cc/hD587Sx4/kmono-white.png" media="(prefers-color-scheme: light)">
      <img src="https://i.postimg.cc/hD587Sx4/kmono-white.png" alt="Logo">
    </picture>
  </p>

  <p>
    A monorepo/workspace tool for clojure tools.deps projects
  </p>

[![Clojars Project](https://img.shields.io/clojars/v/com.kepler16/kmono-core.svg)](https://clojars.org/com.kepler16/kmono-core)

</div>

Kmono is a suite of tools and API's for working in Clojure (mono)repos. It aims to meet Clojure where it's at by
providing a better understanding of deps.edn projects.

While Kmono was designed with a focus on improving the experience of working in Clojure monorepos, care has been taken
to ensure it works great in standalone (non-workspace) projects too.

## Index

- **[Features](#features)**
- **[Rationale](#rationale)**
- **[How It Works](#how-it-works)**
  - **[Workspace Package Graph](#workspace-package-graph)**
  - **[The Classpath and Package Aliases](#The-Classpath-and-Package-Aliases)**
  - **[Clojure APIs](#clojure-apis)**
  - **[Versioning / Change Detection](#Versioning--Change-Detection)**
- **[Installation](#installation)**
- **[Documentation](#API-Documentation)**
  - **[Kmono CLI](#kmono-cli)**
  - **[Example Project](#example-project)**
- **[Configuration](#configuration)**
  - **[Workspace Configuration](#workspace-configuration)**
  - **[Package Configuration](#package-configuration)**
  - **[Local-Only Configuration Overrides](#Local-Only-Configuration-Overrides)**
- **[Clojure-lsp / Editor Integration](#clojure-lsp--editor-integration)**

## Features

- **Workspace features**: Discovers packages and understands relationships between dependencies
- **Aliases**: Allows working with packages aliases defined in `deps.edn` in a 'Clojure native' way without having to
  pull all alias definitions into a root `deps.edn`
- **Build Tools**: Exposes a suite of tools and APIs intended to be used from `tools.build` programs to build and
  release monorepos
- **Command Runner**: Allows executing Clojure and/or external commands in workspace packages
- **Local Deps Overrides**: Allow overriding kmono config and `deps.edn` dependencies during local development. Useful
  for providing local paths to in-development libs without committing.
- **Editor/Clojure-lsp**: Integrates with clojure-lsp to provide better classpath information and improve the
  developer/editing experience in monorepos

## Rationale

It's a bit of a pain to work with `deps.edn` based Clojure monorepos. There is a lack of good workspace features that
one might find in other languages and that is needed in order to work effectively in a large monorepo.

Here is a list of some of the functionality I find lacking:

- Start a repl with some subset of package aliases (such as `:test`) active (deps included, paths on classpath)
- Have your editor / clojure-lsp know about these subpackage aliases and include their `:extra-paths` on the classpath
- Run arbitrary commands across packages in a monorepo
- Track and increment package versions.
- Release package jars with their referenced workspace dependencies/dependent package versions correctly set.
- Run CI build/test/release workflows against only the subset of packages that have changed since the previous version
  or some revision.
- Be able to build arbitrary custom workspace configurations or build pipelines that make use of the workspace graph /
  metadata.
- Script against the workspace package graph (run queries against the package graph)

Kmono aims to solve all of these problems.

There are some existing projects out there that are trying to solve for this problem too - but I find that they are
either too opinionated, too rigid, or stray too far from the 'official' tooling too much.

Kmono tries to build on top of `tools.deps` and the clojure cli in order to add additional functionality / capabilities
in a way that feels native to `tools.deps` based projects. It tries to do this in a way that does not require
configuring the project in a non-standard way, or configuring it in a manner which would render it incompatible with
other tools.deps based tooling.

I see the functionality added here as a kind of proposal for how I might want workspace features to work in the core
tools.deps and broader clojure ecosystem.

## Installation

#### Homebrew

```bash
brew install kepler16/tap/kmono
```

#### Curl

You can use the below script to automatically install the latest release

```bash
bash < <(curl -s https://raw.githubusercontent.com/kepler16/kmono/master/install.sh)
```

Or alternatively binaries for various platforms can be pulled directly from the
[Releases](https://github.com/kepler16/kmono/releases) page.

## How It Works

### Workspace Package Graph

At the core kmono is a tool which understands how packages in a Clojure workspace relate and depend on each other. This
is done by analysing the `:deps` of packages to find `:local/root` coordinates to other packages in the same workspace.
Using this information kmono can build up a graph of packages and their dependencies.

All of kmono's features and capabilities are built on top of this package graph.

### The ClassPath and Package Aliases

Using the package graph kmono can augment the clojure cli with additional aliases constructed from the package graph.
This allows 'telling' clojure about the aliases and paths of subpackages in the workspace. This is built on top of the
`clojure -Sdeps` subcommand.

When you run a command such as `kmono cp -P ':*/test'` kmono will look for packages in the workspace containing a
`:test` alias, and it will 'lift' these aliases into the root deps.edn config by generating a deps map to merge in via
`-Sdeps`.

If we were to run this command in the [example workspace project](./examples/workspace/) it would result in a call to
the clojure CLI that looks something like:

```bash
clojure -Sdeps '{:aliases {:a/test {:extra-paths ["packages/a/test"]
                                    :extra-deps {lambdaisland/kaocha {:mvn/version "1.91.1392"}}
                                    :main-opts ["-m" "kaocha.runner" "-c" "../../tests.edn"]}
                           :b/test {:extra-paths ["packages/b/test"]
                                    :extra-deps {lambdaisland/kaocha {:mvn/version "1.91.1392"}}
                                    :main-opts ["-m" "kaocha.runner" "-c" "../../tests.edn"]}
                           :kmono/packages {:extra-deps {com.kepler16/a {:local/root "packages/a"}
                                                         com.kepler16/b {:local/root "packages/b"}}}}}' \
  -A:kmono/packages:a/test:b/test -Spath
```

From this you can see kmono has extended the `deps.edn` project configuration using the clojure cli `-Sdeps` flag,
passing it a set of aliases dynamically constructed from the package graph.

You should notice that any relative paths defined by package aliases in the workspace have been adjusted to be relative
to the project root instead of to the package.

Using `-A` kmono can selectively pick which aliases to apply!

Most kmono cli commands can be run with `-v` to print out debug information about what clojure command kmono is
constructing.

### Programmatic APIs

Kmono exposes a large set of clojure APIs which can be used to work with the analyzed workspace package graph. These
APIs are primarily intended to be used within `tools.build` pipelines to define custom build + release workflows that
fit the projects requirements, but could be used for building custom tools/clis/scripts as well.

Most API's are for querying, filtering, or versioning packages in the graph. Two key APIs exposed are
`k16.kmono.build/for-each-package` and `k16.kmono.build/create-basis`.

#### `k16.kmono.build/for-each-package`

This allows iterating over the packages in a given package graph and executing a given function in the context of that
package. It allows using the `tools.build` API's as if they were executed from the directory of the workspace package.

#### `k16.kmono.build/create-basis`

This allows constructing a `tools.build` basis wherein any references to workspace packages have been replaced with
their maven coordinates. This is key to simplifying the process of incrementing package versions, building and
releasing.

Please see the [example workspace](./examples/workspace/) or kmono itself for some examples on how to use the API's
exposed by kmono.

### Versioning / Change Detection

Packages in the graph can optionally have a set of `:commits` and a `:version` associated with them. Various kmono API's
work by populating these fields for packages in the graph, or use this information to filter and/or apply package
version updates.

For example, there is `k16.kmono.version/inc-package-versions` which will increment the version of packages in a given
package graph.

There is `k16.kmono.version/package-changed?` which can be used to filter out packages which have not changed since some
previous revision / version.

This information can be populated into the graph however you want, but there are also some built-in mechanisms for
associating package versions and commits.

For versions the built-in mechanism is to track package versions as git tags. The
`k16.kmono.version/resolve-package-versions` will look for git tags following the pattern
`<group>/<package-name>@<version>` and will use the `<version>` component to populate package version in the graph.

The `k16.kmono.version/resolve-package-changes` can be used to find commits that have changed the package since it's
last tagged version.

The `k16.kmono.version/resolve-package-changes-since` can be used to find commits that have changed the package since a
given git revision.

These tools and others can be used to build sophisticated build and release pipelines for kmono workspaces.

## API Documentation

- **[kmono](https://cljdoc.org/d/com.kepler16/kmono)** - A BOM package containing all the submodules of kmono. This is
  the best one to look at for docs.

For each individual modules' docs:

- **[kmono-core](https://cljdoc.org/d/com.kepler16/kmono-core)** - The core suite of API's for working with kmono
  packages.
- **[kmono-build](https://cljdoc.org/d/com.kepler16/kmono-build)** - A companion lib to `tools.build` which contains
  API's for building jar artifacts or simplifying the use of `tools.build` in a kmono workspace.
- **[kmono-version](https://cljdoc.org/d/com.kepler16/kmono-version)** - A set of API's for versioning kmono packages.

#### Kmono CLI

```bash
❯ kmono
kmono - A cli for managing clojure (mono)repos

Usage:
  kmono [opts] <args>

Version:
  4.9.0

Commands:
  cp      Produce a classpath string from a clojure project
  repl    Start a clojure repl
  exec    Run a given command in workspace packages
  run     Run aliases in workspace packages
  clojure Run an augmented clojure command
  query   Query information about the package graph
  version Print the current version of kmono

Global Options
  -d, --dir      Run commands as if in this directory
  -p, --packages A glob string describing where to search for packages (default: 'packages/*')
  -v, --verbose  Enable verbose output
  -h, --help
```

#### Example project

Have a look at **[the example project](./examples/workspace/)** to get a better idea of the type of project structures
kmono is built to support. This should provide a good reference on how to correctly use the kmono API's and integrate it
into your own project.

## Configuration

Kmono will work in any `deps.edn` based project, but needs explicit configuration in order to treat a project as a
workspace (or monorepo).

### Workspace Configuration

To mark a project as a kmono workspace add a `:kmono/workspace {}` field to the root `deps.edn` config file. This
configuration accepts the following properties:

| Field              | Type                    | Default           | Description                                                                                                                            |
| ------------------ | ----------------------- | ----------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `:packages`        | `string? \| #{string?}` | "./packages/\*\*" | A glob or set of file globs that describe the set of packages included in the workspace                                                |
| `:group`           | `symbol?`               | `nil`             | The mvn group to apply to any packages in the workspace that have not specified a group                                                |
| `:repl-aliases`    | `[keyword?]`            | `nil`             | A set of `deps` aliases to include when running `kmono repl`                                                                           |
| `:aliases`         | `[keyword?]`            | `nil`             | A set of `deps` aliases to include for all kmono workspace commands by default                                                         |
| `:package-aliases` | `[keyword?]`            | `nil`             | A set of namespaced [alias globs](#alias-globs) that describe the aliases of packages within the workspace to include in the classpath |

Example:

```clojure
;; deps.edn
{:kmono/workspace {:group com.example
                   :packages #{"./(packages|modules)/**"}
                   ;; Include any `:test` aliases from all (`*`) packages in the workspace
                   :package-aliases [:*/test]}

 :paths ["src" "resources"]

 :deps {...}}
```

### Package Configuration

Packages in the workspace can optionally provide their own configuration metadata. This is done by setting a
`:kmono/package {}` config field in the packages `deps.edn` file. This config accepts the following properties:

| Field       | Type                 | Default | Description                                                                                                                                    |
| ----------- | -------------------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `:group`    | `symbol?`            | `nil`   | The mvn group to use for this package. If not specified, the `:group` specified in the root `:kmono/workspace` configuration will be used.     |
| `:name`     | `string? \| symbol?` | `$dir`  | The name of the package. If not set the name of the parent directory containing the packages' `deps.edn` file will be used as the package name |
| `:excluded` | `boolean?`           | `false` | Whether or not this package is excluded from the project workspace                                                                             |

Example

```clojure
;; packages/example/deps.edn
{:kmono/package {;; Maven artifacts group
                 :group com.example
                 ;; Override the default package name
                 :name example-lib}

 :paths ["src"]
 :deps {...}
 :aliases {:test {...}}}
```

### Local-Only Configuration Overrides

Kmono will automatically read in a `deps.local.edn` file from the project root and deep-merge it with the root
`deps.edn` file if present.

This is very useful for adding additional configuration such as default-enabled project aliases, new project-specific
dev-only deps aliases or any other metadata in a way that is local to the developers environment and won't be committed
to the project.

This `deps.local.edn` file should typically be added to `.gitignore`.

The below is a good example of how this `deps.local.edn` config might be used in someones environment, on their personal
machine:

```clojure
{:kmono/workspace {;; Add the :nrepl alias from ~/.clojure/deps.edn when running
                   ;; `kmono repl`
                   :repl-aliases [:nrepl]
                   ;; - Include the :dev alias from ~/.clojure/deps.edn
                   ;; - Include the :local alias defined below
                   :aliases [:dev :local]}

 :aliases {;; Define a custom local-only alias for this project
           :local {;; Override some dependency with a locally checked out copy
                   ;; for development
                   :extra-deps {com.example/some-lib {:local/root "/some/local/lib/path"}}}
           :extra-paths ["local"]}}
```

This config will be loaded and included for this project when running commands such as

```bash
kmono cp
kmono repl
kmono clojure
...
```

This is especially useful in an editor that has been configured to use
[kmono as the project-specs source](#clojure-lsp--editor-integration), or when using kmono to start a repl for the
project.

## Clojure-lsp / Editor Integration

One of the things that kmono enables is integration into your editor by acting as a drop-in replacement for
`clojure -Spath` which is used by clojure-lsp by default.

If we instead use `kmono cp` to generate the classpath then your clojure-lsp server will be able to provide better
analysis that includes information about subpackages of your workspace, as well as package aliases.

#### Project local Clojure-LSP config

```clojure
;; .lsp/config.edn
{:project-specs [{:project-path "deps.edn"
                  :classpath-cmd ["kmono" "cp"]}]}
```

#### Neovim / nvim-lspconfig

```lua
local lspconfig = require("lspconfig")
lspconfig.clojure_lsp.setup({
  init_options = {
    ["project-specs"] = {
      {
        ["project-path"] = "deps.edn",
        ["classpath-cmd"] = { "kmono", "cp" },
      },
    },
  },
})
```
