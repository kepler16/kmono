(ns k16.kmono.cli.common.opts)

(set! *warn-on-reflection* true)

(def packages-opt
  {:as "A glob string describing where to search for packages (default: 'packages/*')"
   :option "packages"
   :short "p"
   :type :string})

(def aliases-opt
  {:as "List of aliases from the root deps.edn"
   :option "aliases"
   :short "A"
   :multiple true
   :type :keyword})

(def main-aliases-opt
  {:as "List of main aliases from the root deps.edn"
   :option "main-aliases"
   :short "M"
   :multiple true
   :type :keyword})

(def package-aliases-opt
  {:as "List of aliases from packages"
   :option "package-aliases"
   :short "P"
   :multiple true
   :type :keyword})

(def order-opt
  {:as "Run tests in dependency order"
   :option "ordered"
   :type :flag
   :default true})

(def verbose-opt
  {:as "Verbose output"
   :option "verbose"
   :short "v"
   :type :with-flag})
