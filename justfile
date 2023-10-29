test *ARGS:
    clojure -T:kmono run :exec "\"just test\"" {{ ARGS }}

build *ARGS:
    clojure -T:kmono run :exec :build {{ ARGS }}

release *ARGS:
    clojure -T:kmono run :exec :release {{ ARGS }}
