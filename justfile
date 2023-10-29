test *ARGS:
    clojure -T:kmono run :exec "\"just test\"" {{ ARGS }}

build-packages *ARGS:
    clojure -T:kmono run :exec :build {{ ARGS }}

release-packages *ARGS:
    clojure -T:kmono run :exec :release {{ ARGS }}
