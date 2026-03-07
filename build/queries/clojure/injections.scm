((list_lit
  ((sym_lit) @def-type
   (sym_lit) @def-name
   (str_lit) @docstring @injection.content)
   (map_lit)?

   (_)+)

  (#match? @def-type "^(def|defprotocol)$")
  (#offset! @injection.content 0 1 0 -1)
  (#escape! @injection.content "\"")
  (#set! injection.language "markdown"))

((list_lit
  ((sym_lit) @def-type
   (sym_lit) @def-name
   (str_lit)? @docstring @injection.content)
   (map_lit)?

   [
    (vec_lit)
    (list_lit (vec_lit))+
   ])

  (#match? @def-type "^(defn-?|defmacro)$")
  (#offset! @injection.content 0 1 0 -1)
  (#escape! @injection.content "\"")
  (#set! injection.language "markdown"))

;; Match ns
((list_lit
  ((sym_lit) @fn-type
   (sym_lit) @ns-name
   (#eq? @fn-type "ns")

   (str_lit)? @docstring @injection.content)
   (map_lit)?)

  (_)*

  (#offset! @injection.content 0 1 0 -1)
  (#escape! @injection.content "\"")
  (#set! injection.language "markdown"))

(list_lit
  ((sym_lit) @fn-name
   (#eq? @fn-name "defprotocol")
   (sym_lit) @protocol-name

   (str_lit)?)

  (list_lit
    (sym_lit)
    (vec_lit)+
    (str_lit) @docstring @injection.content)

  (#offset! @injection.content 0 1 0 -1)
  (#escape! @injection.content "\"")
  (#set! injection.language "markdown"))

((str_lit)  @injection.content
  (#match? @injection.content "^\"(SET|TRUNCATE|SELECT|CREATE|DELETE|ALTER|UPDATE|DROP|INSERT|WITH)")
  (#offset! @injection.content 0 1 0 -1)
  (#escape! @injection.content "\"")
  (#set! injection.language "sql"))
