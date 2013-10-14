(defproject clj-uu-assignment "0.1.0-SNAPSHOT"
  :description "Clojure port of `uu-assignment` template."
  :url "http://github.com/llasram/clj-uu-assignment"
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.6" "-source" "1.6"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.codehaus.jsr166-mirror/jsr166y "1.7.0"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.grouplens.lenskit/lenskit-core "2.0.2"]
                 [org.platypope/parenskit "0.1.0"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 [log4j "1.2.17"]]
  :main ^:skip-aot recsys.uu
  :profiles {:uberjar {:aot :all}})
