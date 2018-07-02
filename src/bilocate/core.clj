(ns bilocate.core
  {:doc "Utility for interaction with remote nREPLs"
   :author "vermilionsands"}
  (:require [clojure.tools.nrepl :as repl]))

(defonce ^:dynamic *nrepl-spec* nil)

(defn set-nrepl-spec! [& opts]
  (alter-var-root (var *nrepl-spec*) (fn [_] (apply hash-map opts))))

(defn remote-eval
  "Connects to nREPL using specification stored at *nrepl-spec* and sends
   form or string for evaluation. Returns evaluation result or throws
   an exception.

   Connection specification should be a map and supports the same keys as
   clojure.tools.nrepl/connect (:port, :host (defaults to localhost),
   :transport-fn) and additionaly :timeout (defaults to 10000).

   Supported opts:
   :skip-return - will return nil, instead of eval result,
                  useful for evals that return objects for which no
                  reader tag exists
   :skip-out    - won't print to *out*

   Example:
   (binding [*nrepl-spec* {:timeout 5000 :port 47319}]
     (remote-eval '(defn add [x y] (+ x y)))
     (remote-eval \"(add 2 3)\"))"
  [form & opts]
  (with-open [conn (apply repl/connect (flatten (seq *nrepl-spec*)))]
    (let [opts-set (into #{} opts)
          response (->
                    (repl/client conn (:timeout *nrepl-spec* 10000))
                    (repl/message {:op "eval" :code (str form)}))
          response-map (repl/combine-responses response)]
      (when (and (not (opts-set :skip-out)) (:out response-map))
        (println (:out response-map)))
      (when ((:status response-map) "eval-error")
        (throw (java.lang.RuntimeException. (str "Remote execution failed with " (:root-ex response-map)))))
      (when-not (opts-set :skip-return)
        (last (repl/response-values response))))))

(defn remote-var
  "Alias for `remote-eval` to retrieve a val from remote val under `sym`."
  [sym]
  (remote-eval sym))

(defn remote-fn-call
  "Calls `remote-eval` passing `(sym & args)` as an argument."
  [sym & args]
  (remote-eval (cons sym (apply list args))))

(defn- parse-namespace-arg [x]
  (if (vector? x)
    [(first x) (apply hash-map (rest x))]
    [x {}]))

(defn- require-single-namespace [ns-sym as included referred]
  (create-ns ns-sym)
  (when as (alias as ns-sym))
  (doseq [var-sym (remote-eval `(map first (ns-publics '~ns-sym)))
          :let [ns+var-sym (symbol (str ns-sym) (str var-sym))]
          :when (or (empty? included) (some (partial = var-sym) included))]
    (intern ns-sym var-sym (partial remote-fn-call ns+var-sym))
    (when (or (= referred :all) (some (partial = var-sym) referred))
      (intern (ns-name *ns*) var-sym (find-var ns+var-sym)))))

(defn require-remote-ns* [& args]
  (doseq [[ns-sym {as :as included :include referred :refer}] (map parse-namespace-arg args)]
    (when-not (remote-eval `(when-let [~'x (find-ns '~ns-sym)] (ns-name ~'x)))
      (throw (IllegalArgumentException. (str "Remote namespace " ns-sym " not found."))))
    (require-single-namespace ns-sym as included referred)))

(defmacro require-remote-ns
  "Macro that looks for a namespaces at a remote repl, and, if found, tries to create
   them locally. In each namespace it will try to create vars corresponding
   to symbols passed with :include option. Each var would be bound to remote-fn-call
   pointing to an appropriate remote var.

   Each arg can be one of the following:

   symbol - namespace name
   vector - containing namespace name and options (in a form of pairs of keyword and value)

   Supported options:
   :as - takes a symbol as an argument. If present, an alias would be created in the
   current namespace.
   :include - takes a vector of symbols to require from the namespace
   :refer - takes a vector of symbols to refer from the namespace, or the :all keyword
   to refer all included vars.

   Example:
   (require-remote-ns '[remote-datasource-ns :as data :include [foo bar]])

   For remote nREPL connection description see remote-eval."
  [& args]
  (doseq [[ns-sym {as :as included :include referred :refer}] (map (comp parse-namespace-arg second) args)]
    (create-ns ns-sym)
    (when as (alias as ns-sym))
    (when (vector? included)
      (doseq [var-sym included]
        (intern ns-sym var-sym)))
    (when (vector? referred)
      (doseq [var-sym referred]
        (intern *ns* var-sym))))
  `(binding [*ns* ~*ns*]
     (require-remote-ns* ~@args)))

(defmacro remote-ns 
  "Helper to define a remote namespace.
  
   ```
   (remote-ns some-ns
     (require '[clojure.string :as string])

     (defn foo [s] (string/upper-case s)))
   ```
  "
  [name & forms]
  `(remote-eval 
     '(do 
        (ns ~name)
        (in-ns '~name)
        ~@forms)
     :skip-return))      

(defmacro using-nrepl [host port & body]
  `(binding [*nrepl-spec* {:host ~host :port ~port}]
     ~@body))
    