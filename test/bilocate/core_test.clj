(ns bilocate.core-test
  (:require [clojure.test :refer :all]
            [bilocate.core :refer :all]))

(defn with-remote-repl [f]
  (let [proc (.exec (Runtime/getRuntime) "lein repl")
        port (with-open [rdr (clojure.java.io/reader (.getInputStream proc))]
               (-> rdr
                   line-seq
                   first
                   (.split ":")
                   last
                   (Integer/parseInt)))]
    (binding [*nrepl-spec* {:port port}]
      (try
        (f)
        (finally (.destroy proc))))))

(defn with-remote-ns [f]
  (remote-eval '(in-ns 'remote-bilocate-1) :skip-return)
  (remote-eval '(do (ns remote-bilocate-1) (defn foo [x] (* 2 x))))
  (remote-eval '(do (ns remote-bilocate-1) (defn bar [x] (* 3 x))))
  (remote-eval '(in-ns 'remote-bilocate-2) :skip-return)
  (remote-eval '(do (ns remote-bilocate-2) (defn zoo [x] (* 4 x))))
  (f)
  (remote-eval '(remove-ns 'remote-bilocate-1) :skip-return)
  (remote-eval '(remove-ns 'remote-bilocate-2) :skip-return)
  (remove-ns 'remove-bilocate-1)
  (remove-ns 'remove-bilocate-2))

(use-fixtures :once with-remote-repl)
(use-fixtures :each with-remote-ns)

(deftest remote-eval-test
  (testing "Testing remote eval"
    (is (= 3 (remote-eval '(+ 1 2))))
    (is (= 3 (remote-eval "(+ 1 2)")))
    (is (symbol? (last (remote-eval '(defn foo [x] x)))))))

(deftest remote-eval-out-test
  (testing "Testing remote eval output"
    (is (= "Test" (.trim (with-out-str (remote-eval '(println "Test"))))))
    (is (empty? (with-out-str (remote-eval '(+ 1 2)))))))

(deftest remote-eval-opts-test
  (testing "Testing remote eval opts"
    (is (nil? (remote-eval '(+ 1 2) :skip-return)))
    (is (empty? (with-out-str (remote-eval '(println "Test") :skip-out))))))

(deftest remote-eval-error-test
  (testing "Testing remote eval exceptions"
    (is (thrown? RuntimeException (remote-eval '(/ 1 0)))))
  (testing "Testing remote eval exception message"
    (let [s (try (remote-eval '(/ 1 0)) (catch Exception e (.getMessage e)))]
      (is (= "Remote execution failed with class java.lang.ArithmeticException" s)))))

(deftest remote-fn-call-test
  (testing "Testing remote-fn-call"
    (is (= 3 (remote-fn-call '+ 1 2)))
    (is (= "Test" (.trim (with-out-str (remote-fn-call 'println "Test")))))
    (let [sym (last (remote-eval '(defn foo [x y] (* x y))))]
      (is (= 6 (remote-fn-call sym 2 3))))))

(deftest require-remote-ns-test
  (testing "Testing require of remote ns"
    (require-remote-ns '[remote-bilocate-1 :include [foo bar] :refer [bar]])
    (is (some? (find-ns 'remote-bilocate-1)))
    (is (= 4 (remote-bilocate-1/foo 2)))
    (is (= 6 (bar 2)))
    (.unbindRoot #'bar))
  (testing "Testing overwriting already bound vars and aliases"
    (create-ns 'remote-bilocate-2)
    (intern 'remote-bilocate-2 'zoo (fn [x] x))
    (is (= 3 ((find-var 'remote-bilocate-2/zoo) 3)))
    (require-remote-ns '[remote-bilocate-2 :include [zoo] :as r])
    (is (= 12 (r/zoo 3)))))

(deftest require-multiple-ns-test
  (testing "Testing requiring multiple ns"
    (is (not (bound? (find-var 'bilocate.core-test/foo))))
    (is (not (bound? (find-var 'bilocate.core-test/bar))))
    (is (not (bound? (find-var 'bilocate.core-test/zoo))))
    (require-remote-ns '[remote-bilocate-1 :include [foo bar] :refer [foo bar]]
                       '[remote-bilocate-2 :include [zoo] :refer [zoo]])
    (is (bound? (find-var 'bilocate.core-test/foo)))
    (is (bound? (find-var 'bilocate.core-test/bar)))
    (is (bound? (find-var 'bilocate.core-test/zoo)))))