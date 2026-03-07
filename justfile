default:
    @just --choose

build *args:
    clojure -T:build build {{ args }}

build-cli:
    clojure -T:build build-cli

build-native-cli *args: build-cli
    #!/usr/bin/env bash
    set -eo pipefail

    mkdir -p target/bin/

    NATIVE_IMAGE=$(which $JAVA_HOME/bin/native-image || which $GRAALVM_HOME/bin/native-image)
    $NATIVE_IMAGE {{ args }} -jar target/kmono-cli/cli.jar target/bin/kmono

reflect *args: build-cli
    #!/usr/bin/env bash
    set -eo pipefail

    JAVA=$(which $JAVA_HOME/bin/java || which $GRAALVM_HOME/bin/java)
    $JAVA -agentlib:native-image-agent=config-output-dir=./reflection-output/ -jar target/kmono-cli/cli.jar {{ args }}

release *args:
    clojure -T:build release {{ args }}

cli *args:
    cd packages/kmono-cli && clojure -M -m k16.kmono.cli.main {{ args }}

test *args:
    just cli {{ args }} run -M :test

format dry='false':
    pruner format '**/*.clj' \
      --config pruner.toml \
      --lang clojure \
      --check={{ dry }}

lint:
    just format true
