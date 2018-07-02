(ns dev
  (:require [bilocate.core :as b]))

(def load-time (System/currentTimeMillis))

(defn dev-ns []
  (b/remote-ns dev-ns

    ;not-ok, needs binding/symbol resolution
    ;(def time load-time)
    
    (def  defined-at (System/currentTimeMillis))
    (defn remote-time [] (System/currentTimeMillis))
    (defn remote-add [x y] (+ x y))))
  

(def defined-at (fn [] (b/remote-var 'dev-ns/defined-at)))
(def remote-add (partial b/remote-fn-call 'dev-ns/remote-add))
