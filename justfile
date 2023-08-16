library := "kepler16/kbuild"
version := "$VERSION"
maven_server := "github-kepler"

build:
    clojure -T:meta run :alias lib :lib {{ library }} :version \"{{ version }}\"

release:
    clojure -T:meta deploy :repository \"{{ maven_server }}\" :lib {{ library }} :version \"{{ version }}\"

