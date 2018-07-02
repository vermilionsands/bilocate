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
  
(b/refer-var defined-at dev-ns/defined-at)
(b/refer-fn  remote-add dev-ns/remote-add)