(ns bilocate.core
  (:require [clojure.tools.nrepl :as repl]))

(defonce ^:dynamic *nrepl-spec* nil)

(defn set-nrepl-spec! [& opts]
  (alter-var-root (var *nrepl-spec*) (fn [_] (apply hash-map opts))))

(defn remote-eval [form]
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

(defn remote-fn-call [sym & args]
  (remote-eval (cons sym (apply list args))))

(defn- parse-namespace-arg [x]
  (if (vector? x)
    [(first x) (apply hash-map (rest x))]
    [x {}]))

(defn require-remote-ns [& args]
  (let [[sym {as :as referred :refer}] (parse-namespace-arg (first args))
        reload (= (second args) :reload)]
    (when (remote-eval `(ns-name (find-ns '~sym)))
      (do
        (create-ns sym)
        (when as (alias as sym))
        (let [remote-vars (remote-eval `(map first (ns-publics '~sym)))]
          (doseq [name remote-vars
                  :let [var-sym (symbol (str sym) (str name))]
                  :when (or reload (not (find-var var-sym)))]
              (intern sym name (partial remote-fn-call var-sym))
              (when (or (= referred :all)
                        (some (partial = name) referred))
                (intern *ns* name var-sym))))))))

