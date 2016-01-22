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
  (let [overwrite (some (partial = :overwrite) args)]
    (doseq [[ns-sym {as :as referred :refer}] (map parse-namespace-arg (remove (partial = :overwrite) args))]
      (when-not (remote-eval `(when-let [~'x (find-ns '~ns-sym)] (ns-name ~'x)))
        (throw (IllegalArgumentException. (str "Remote namespace " ns-sym " not found."))))
      (require-single-namespace ns-sym as referred overwrite))))