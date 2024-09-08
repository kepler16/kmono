default:
    @just --choose

build *args:
    clojure -T:build build {{args}}

build-cli:
    clojure -T:build build-cli

build-native-cli *args: build-cli
    mkdir -p target/bin/
    $GRAALVM_HOME/bin/native-image -jar target/kmono-cli/cli.jar target/bin/kmono

release *args:
    clojure -T:build release {{args}}

test *args:
    kmono run --ordered false --skip-unchanged true -M ':*/test'

cli *args:
  cd packages/kmono-cli && clojure -M -m k16.kmono.cli.main {{args}}
