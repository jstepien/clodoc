(defproject docjure "0.0.1-SNAPSHOT"
  :description "The description"
  :dependencies [[org.clojure/clojure "1.1.0"]
                 [org.clojure/clojure-contrib "1.1.0"]
                 [compojure "0.4.1"]
                 [hiccup "0.2.6"]
                 [ring/ring-servlet "0.2.6"]
                 [net.sf.jsr107cache/jsr107cache "1.1"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.4.0"]
                 [com.google.appengine/appengine-jsr107cache "1.4.0"]]
  :dev-dependencies [[uk.org.alienscience/leiningen-war "0.0.3"]
                     [ring/ring-jetty-adapter "0.2.3"]]
  :namespaces [docjure.core])
