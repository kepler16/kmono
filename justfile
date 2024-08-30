default:
    @just --choose

build *args:
    clojure -T:build build {{args}}

build-cli *args:
    mkdir -p target/bin/
    $GRAALVM_HOME/bin/native-image -jar target/packages/kmono-cli/cli.jar target/bin/kmono

release *args:
    clojure -T:build release {{args}}

test:
    kmono run --ordered false -M ':*/test'

cli *args:
  cd packages/kmono-cli && clojure -M -m k16.kmono.cli.main {{args}}
