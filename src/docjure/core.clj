(ns docjure.core
  (:use compojure.core
        ring.util.servlet
        [ring.util.codec :only [url-encode]]
        [hiccup.core :only [html escape-html]]
        [clojure.contrib.repl-utils :only [get-source]])
  (:require [compojure.route :as route])
  (:gen-class
     :extends javax.servlet.http.HttpServlet))

(def *assets-addr* "http://stepien.cc/~jan/sh")

(defn include-css
  [name]
  [:link
   {:href (str *assets-addr* "/styles/" name)
    :type "text/css" :rel "stylesheet"}])

(defn include-js
  [name]
  [:script
   {:src (str *assets-addr* "/scripts/" name)
    :type "text/javascript"}])

(defn home-link
  []
  (html [:a {:href "/"} "docjure" ]))

(defn ns-link
  [ns]
  (html [:a {:href (str "/doc/" (url-encode ns))} ns]))

(defn def-link
  [ns df]
  (html [:a {:href (str "/doc/" (url-encode ns) "/" (url-encode df))} df]))

(defn title
  ([] (home-link))
  ([ns] (str (title) " &raquo; " (ns-link ns)))
  ([ns var] (str (title ns) "/" (def-link ns var))))

(defn layout
  [title-coll body]
  (html
    [:html
     [:head
      [:title (apply title title-coll)]
      (include-css "shCore.css")
      (include-css "shThemeDefault.css")
      (include-js "shCore.js")
      (include-js "shBrushClojure.js")]
     [:body 
      [:h1 (apply title title-coll)]
      (for [x body] x)]]))

(defn not-found []
  (html [:h1 "Page not found"]))

(defn var-name
  [ns var]
  (symbol (str ns "/" var)))

(defn doc-for
  [ns var]
  (with-out-str (print-doc (find-var (var-name ns var)))))

(defn source-for
  [ns var]
  (get-source (var-name ns var)))

(defn highlight!
  []
  [:script {:type "text/javascript"} "SyntaxHighlighter.all()"])

(defn def-page [ns-str df-str]
  (layout
    [ns-str df-str]
    [[:pre (escape-html (doc-for ns-str df-str))]
     [:pre {:class "brush: clojure"} (escape-html (source-for ns-str df-str))]
     (highlight!)]))

(defn sorted-publics [ns-str]
  (sort (keys (ns-publics (symbol ns-str)))))

(defn ns-contents [ns-str]
  (layout
    [ns-str]
    [[:ul
      (for [x (map #(def-link ns-str (str %)) (sorted-publics ns-str))]
        [:li x])]]))

(defn sorted-namespaces
  []
  (sort (filter #(not (= "user" %)) (map #(str (.name %)) (all-ns)))))

(defn main-page
  []
  (layout
    []
    [[:ul
      (for [x (map ns-link (sorted-namespaces))]
        [:li x])]]))

(defroutes our-routes
  (GET "/" [] (main-page))
  (GET ["/doc/:ns", :ns #"[\w\-\.]+"] [ns] (ns-contents ns))
  (GET ["/doc/:ns/:df", :ns #"[\w\-\.]+", :df #".*"] [ns df] (def-page ns df))
  (route/not-found (not-found)))

(defservice our-routes)
