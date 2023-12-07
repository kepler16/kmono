test *ARGS:
    clojure -T:kmono run :exec "\"just test\"" {{ ARGS }}

build *ARGS:
    clojure -T:kmono run :exec :build {{ ARGS }}

build-uber *ARGS:
    clojure -T:kmono run :exec '"just build-uber-native"' {{ ARGS }}

build-native *ARGS:
    rm -rf ./bin && mkdir bin && \
    clojure -T:kmono run :exec '"just build-native"' {{ ARGS }} && \
    cp packages/kmono/target/kmono ./bin/kmono

release *ARGS:
    clojure -T:kmono run :exec :release {{ ARGS }}
