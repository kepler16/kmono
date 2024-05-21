default:
  @just --choose

clean:
    clojure -T:build clean

build-uber-native: clean
    clojure -T:build uber-for-native

native-image:
    $GRAALVM_HOME/bin/native-image \
      -jar target/kmono-uber.jar \
      --no-fallback \
      --features=clj_easy.graal_build_time.InitClojureClasses \
      --report-unsupported-elements-at-runtime \
      -o target/kmono \
      -H:+UnlockExperimentalVMOptions \
      -H:+ReportExceptionStackTraces \
      --initialize-at-build-time=org.eclipse.aether.transport.http.HttpTransporterFactory

build-native *ARGS: build-uber-native native-image
    rm -rf ./bin && mkdir bin && \
    cp target/kmono ./bin/kmono

build *ARGS:
    clojure -T:kmono run :exec :build {{ ARGS }}

release *ARGS:
    clojure -T:kmono run :exec :release {{ ARGS }}

test:
    clojure -M:test -m "kaocha.runner"

repl *ARGS:
    clojure -M:kmono{{ ARGS }} repl -P ':*/test' -l -v
