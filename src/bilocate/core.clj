(ns bilocate.core
  {:doc "Utility for interaction with remote nREPLs"
   :author "vermilionsands"}
  (:require [clojure.tools.nrepl :as repl]))

(defonce ^:dynamic *nrepl-spec* nil)

(defn set-nrepl-spec! [& opts]
  (alter-var-root (var *nrepl-spec*) (fn [_] (apply hash-map opts))))

(defn remote-eval
  "Connects to nREPL using specification stored at *nrepl-spec* and sends
   form (Cons or String) for evaluation. Returns evaluation result or throws
   an exception.

   Connection specification should be a map and supports the same keys as
   clojure.tools.nrepl/connect (:port, :host (defaults to localhost),
   :transport-fn) and additionaly :timeout (defaults to 10000)."
  [form]
  (with-open [conn (apply repl/connect (flatten (seq *nrepl-spec*)))]
    (let [response (->
                    (repl/client conn (:timeout *nrepl-spec* 10000))
                    (repl/message {:op "eval" :code (str form)}))
          response-map (repl/combine-responses response)]
      (when-let [out (:out response-map)]
        (println out))
      (when ((:status response-map) "eval-error")
        (throw (java.lang.RuntimeException. (str "Remote execution failed with " (:root-ex response-map)))))
      (last (repl/response-values response)))))

(defn remote-fn-call
  "Calls remote-eval passing (sym & args) as an argument."
  [sym & args]
  (remote-eval (cons sym (apply list args))))

(defn- parse-namespace-arg [x]
  (if (vector? x)
    [(first x) (apply hash-map (rest x))]
    [x {}]))

(defn- require-single-namespace [ns-sym as referred overwrite]
  (create-ns ns-sym)
  (when as
    (alias as ns-sym))
  (doseq [var-sym (remote-eval `(map first (ns-publics '~ns-sym)))
          :let [ns+var (symbol (str ns-sym) (str var-sym))]
          :when (or overwrite (not (find-var ns+var)))]
    (intern ns-sym var-sym (partial remote-fn-call ns+var))
    (when (or (= referred :all) (some (partial = var-sym) referred))
      (intern *ns* var-sym ns+var))))

(defn require-remote-ns [& args]
  "Looks for a namespaces at a remote repl, and, if found, tries to create
   them locally. In each namespace it will try to create vars corresponding
   to public vars from remote namespace. Each var would be bound to remote-fn-call
   pointing to an appropriate remote var.

   Each arg can be one of the following:

   Spec:
   symbol - namespace name
   vector - containing namespace name and options (in a form of pairs of keyword and value)

   Supported options:
   :as - takes a symbol as an argument. If present, an alias would be created in the
   current namespace.
   :refer - takes a vector of symbols to refer from the namespace, or the :all keyword
   to refer all public vars.

   Flag:
   :overwrite - if the namespace already exists cause an overwrite of already defined vars.

   Example:
   (require-remote-ns '[remote-datasource-ns :as data] :overwrite)

   For remote nREPL connection description see remote-eval."
  (let [overwrite (some (partial = :overwrite) args)]
    (doseq [[ns-sym {as :as referred :refer}] (map parse-namespace-arg (remove (partial = :overwrite) args))]
      (when-not (remote-eval `(when-let [~'x (find-ns '~ns-sym)] (ns-name ~'x)))
        (throw (IllegalArgumentException. (str "Remote namespace " ns-sym " not found."))))
      (require-single-namespace ns-sym as referred overwrite))))