(defproject clodoc "0.0.1-SNAPSHOT"
  :description "The description"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [compojure "0.6.5"]
                 [hiccup "0.3.6"]
                 [ring/ring-servlet "0.3.11"]
                 [net.sf.jsr107cache/jsr107cache "1.1"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.4.3"]
                 [com.google.appengine/appengine-jsr107cache "1.4.3"]
                 [clojure-http-client "1.1.0"]
                 [am.ik/clj-gae-ds "0.3.0"]
                 [commons-codec/commons-codec "1.5"]
                 [com.google.javascript/closure-compiler "r1352"]]
  :aot [clodoc.core])
