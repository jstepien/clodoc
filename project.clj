(defproject docjure "0.0.1-SNAPSHOT"
  :description "The description"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [compojure "0.6.5"]
                 [hiccup "0.3.6"]
                 [ring/ring-servlet "0.3.11"]
                 [net.sf.jsr107cache/jsr107cache "1.1"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.4.3"]
                 [com.google.appengine/appengine-jsr107cache "1.4.3"]]
  :dev-dependencies [[ring/ring-jetty-adapter "0.3.11"]]
  :aot [docjure.core])
