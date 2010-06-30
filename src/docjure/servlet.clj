(ns docjure.servlet
  (:use compojure.core
        [hiccup.core :only [html]])
  (:gen-class
     :extends javax.servlet.http.HttpServlet))

(defroutes greeter
    (GET "/*" [] (html [:h1 "Hello World"])))

;(defservice greeter)
