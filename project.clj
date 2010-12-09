(defproject docjure "0.0.1-SNAPSHOT"
  :description "The description"
  :dependencies [[org.clojure/clojure "1.3.0-SNAPSHOT"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [compojure "0.5.3"]
                 [hiccup "0.3.1"]
                 [ring/ring-servlet "0.3.5"]
                 [net.sf.jsr107cache/jsr107cache "1.1"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.4.0"]
                 [com.google.appengine/appengine-jsr107cache "1.4.0"]]
  :dev-dependencies [[uk.org.alienscience/leiningen-war "0.0.11"]
                     [ring/ring-jetty-adapter "0.3.5"]]
  :namespaces [docjure.core])
