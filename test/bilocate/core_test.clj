(ns bilocate.core-test
  (:require [clojure.test :refer :all]
            [bilocate.core :refer :all]
            [clojure.tools.nrepl.server :as server]))

(def ^:dynamic *server* nil)

(defn with-remote-repl [f]
  (with-open [server (server/start-server)]
    (binding [*server* server
              *nrepl-spec* {:port (:port server)}]
      (f))))

(use-fixtures :each with-remote-repl)

(deftest remote-eval-test
  (testing "Testing remote eval"
    (is (= 3 (remote-eval '(+ 1 2))))
    (is (= 3 (remote-eval "(+ 1 2)")))))

(deftest remote-eval-out-test
  (testing "Testing remote eval output"
    (is (= "Test" (.trim (with-out-str (remote-eval '(println "Test"))))))
    (is (empty? (with-out-str (remote-eval '(+ 1 2)))))))

(deftest remote-eval-error-test
  (testing "Testing remote eval exceptions"
    (is (thrown? RuntimeException (remote-eval '(/ 1 0)))))
  (testing "Testing remote eval exception message"
    (let [s (try (remote-eval '(/ 1 0)) (catch Exception e (.getMessage e)))]
      (is (= "Remote execution failed with class java.lang.ArithmeticException" s)))))