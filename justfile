default:
    @just --choose

clean:
    clojure -T:build clean

build-uber-native *ARGS: clean
    clojure -T:kmono run :exec '"clojure -T:build uber-for-native"' {{ ARGS }}

native-image:
    $GRAALVM_HOME/bin/native-image \
      -jar target/kmono-uber.jar \
      --no-fallback \
      --features=clj_easy.graal_build_time.InitClojureClasses \
      --report-unsupported-elements-at-runtime \
      --install-exit-handlers \
      -o target/kmono \
      -H:+UnlockExperimentalVMOptions \
      -H:+ReportExceptionStackTraces \
      --initialize-at-build-time=org.eclipse.aether.transport.http.HttpTransporterFactory

build-native *ARGS:
    just build-uber-native {{ ARGS }} && \
    just native-image && \
    rm -rf ./bin && mkdir bin && \
    cp target/kmono ./bin/kmono

build *ARGS:
    clojure -T:kmono run :exec :build {{ ARGS }}

release *ARGS:
    clojure -T:kmono run :exec :release {{ ARGS }}

test:
    clojure -M:test -m "kaocha.runner"

repl *ARGS:
    clojure -M:kmono:test{{ ARGS }} repl
