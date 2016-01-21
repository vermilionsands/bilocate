(ns bilocate.core
  (:require [clojure.tools.nrepl :as repl]))

(defonce ^:dynamic *nrepl-spec* nil)
(defonce ^:dynamic *nrepl-timeout* 10000)

(defn set-nrepl-spec! [& opts]
  (alter-var-root (var *nrepl-spec*) (fn [_] opts)))

(defn set-nrepl-timeout! [x]
  (alter-var-root (var *nrepl-spec*) (fn [_] x)))

(defn remote-eval [form]
  (with-open [conn (apply repl/connect *nrepl-spec*)]
    (let [response (->
                    (repl/client conn *nrepl-timeout*)
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
          (doseq [name remote-vars]
            (when (or reload
                      (not (find-var (symbol (str sym) (str name)))))
              (intern sym name (partial remote-fn-call (symbol (str sym) (str name)))))
            (when (or (= referred :all)
                      (some (partial = name) referred))
              (intern *ns* name (find-var (symbol (str sym) (str name)))))))))))

