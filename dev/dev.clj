(ns dev
  (:require [bilocate.core :as b]))

(def load-time (System/currentTimeMillis))

(defn dev-ns []
  (let [load-name "test"]
    (b/remote-ns dev-ns
      (def source-time ^:local! load-time)
      (def source-name ^:local! load-name)
      
      (def  defined-at (System/currentTimeMillis))
      (defn remote-time [] (System/currentTimeMillis))
      (defn remote-add [x y] (+ x y)))))
  
(b/refer-var defined-at dev-ns/defined-at)
(b/refer-fn  remote-add dev-ns/remote-add)