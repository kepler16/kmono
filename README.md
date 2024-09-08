<div align="center">
  <p>
    <img 
      src="https://github.com/user-attachments/assets/4f98b4d2-adcf-412f-bba5-1dbba43604a8"
      align="center"
      alt="Logo"
      height="200px"
    />
  </p>

  <h1>Kmono</h1>

  <p>
    The missing workspace tool for clojure tools.deps projects
  </p>

  [![Clojars Project](https://img.shields.io/clojars/v/com.kepler16/kmono-core.svg)](https://clojars.org/com.kepler16/kmono-core)
</div>

Kmono is a suite of tools and API's for working in Clojure (mono)repos. It aims to meet Clojure where it's at by
providing a better understanding of deps.edn projects.

This project was built with a focus on improving the experience of working in Clojure monorepos but works great in
standalone projects too.

## Overview

- **Workspace features**: Discovers packages and understands relationships between dependencies
- **Aliases**: Allows dynamically interacting with workspace package aliases in a 'Clojure native' way
- **Build Tools**: Exposes a suite of libs intended to be used from tools.build programs to build monorepos
- **Command Runner**: Allows executing Clojure or external commands in workspace packages
- **Local Deps Overrides**: Allow overriding properties and `deps.edn` config during local development

## Documentation

+ **[kmono-core](https://cljdoc.org/d/com.kepler16/kmono-core)** - The core suite of API's for working with kmono
packages.
+ **[kmono-build](https://cljdoc.org/d/com.kepler16/kmono-build)** - A companion lib to `tools.build` which contains
API's for building jar artifacts or simplifying the use of `tools.build` in a kmono workspace.
+ **[kmono-version](https://cljdoc.org/d/com.kepler16/kmono-version)** - A set of API's for versioning kmono packages.

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

### Editor Integration / ClojureLSP

One of the things that kmono enables is integration into your editor by acting as a drop-in replacement for `clojure
-Spath` which is used by default by clojure-lsp.

If we instead use `kmono cp` to generate the classpath then your clojure-lsp server will be able to provide better
analysis.

##### Project local Clojure-LSP config

```clojure
;; .lsp/config.edn
{:project-specs [{:project-path "deps.edn"
                  :classpath-cmd ["kmono" "cp"]}]}
```

##### Neovim / nvim-lspconfig

```lua
local lspconfig = require('lspconfig')
lspconfig.clojure_lsp.setup {
  init_options = {
    ["project-specs"] = {
      {
        ["project-path"] = "deps.edn",
        ["classpath-cmd"] = { "kmono", "cp" },
      },
    },
  },
}
```

### Package configuration

Package-specific configurations are done within each `deps.edn` file under the `:kmono/package` key:

```clj
:kmono/package {;; maven artifact's group
                :group com.example
                ;; the package name which is also used as maven's artifactId
                ;; this is optional and inferred from a package's dir name
                :name my-lib}
```
