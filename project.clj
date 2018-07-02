(defproject vermilionsands/bilocate "0.1.1"
  :description "Utility for interaction with remote nREPLs"
  :url "https://github.com/vermilionsands/bilocate"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.nrepl "0.2.12"]]
  :profiles {:dev {:source-paths ["dev" "src"]}})