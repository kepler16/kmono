snapshot := env_var_or_default('SNAPSHOT', 'true')
create_tags := env_var_or_default('CREATE_TAGS', 'false')
include_unchanged := env_var_or_default('INCLUDE_UNCHANGED', 'true')

test:
    clojure -T:kbuild run :exec "\"just test\"" \
      ":include-unchanged?" {{ include_unchanged }}

build:
    clojure -T:kbuild run :glob \".\" :exec :build ":snapshot?" {{ snapshot }} \
      ":include-unchanged?" {{ include_unchanged }}

release:
    clojure -T:kbuild run :exec :release ":snapshot?" {{ snapshot }} \
      ":create-tags?" {{ create_tags }} ":include-unchanged?" {{ include_unchanged }}

