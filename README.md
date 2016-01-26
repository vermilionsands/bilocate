# bilocate

A small utility to help with interaction between two different nREPLs. Internally it's a wrapper 
around `clojure.tools.nrepl/message` function from [nREPL](http://github.com/clojure/tools.nrepl).

## Usage

Add the following to your `:dependencies`:

`[vermilionsands/bilocate "0.1.0"]`

There are two main functions:

`remote-eval` - to invoke a code in remote nREPL and get the response
`require-remote-ns` - to automatically create a namespace and vars that will call the code from remote nREPL

```clojure
(require 'bilocate.core :as b)

; provide connection configuration, here we will use another localhost nREPL
(binding [*nrepl-spec* {:port 47568}]
 ;require namespace and functions from remote nREPL
 (b/require-remote-ns '[some-remote-ns :as remote :include [foo] :refer :all])
 
 ; call a var pointing to some-remote-ns/foo
 (foo))
```

Calls to remote nREPL require a valid connection configuration stored in `*nrepl-spec*`. For available options see doc for `remote-eval`.

## License

Copyright © 2016 vermilionsands

Distributed under the Eclipse Public License.
