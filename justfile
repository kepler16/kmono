library := "kepler16/kbuild"
version := "$VERSION"
maven_server := "github-kepler"

build:
    clojure -T:meta run :alias lib :lib {{ library }} :version \"{{ version }}\"

release: build
    clojure -T:meta deploy :repository \"{{ maven_server }}\" :lib {{ library }} :version \"{{ version }}\"

test:
    clojure -M:test -m "kaocha.runner"
