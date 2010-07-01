(ns docjure.core
  (:use compojure.core
        ring.util.servlet
        [ring.util.codec :only [url-encode]]
        [hiccup.core :only [html escape-html]]
        [clojure.contrib.repl-utils :only [get-source]])
  (:require [compojure.route :as route])
  (:gen-class
     :extends javax.servlet.http.HttpServlet))

(def *assets-addr* "http://gamma.mini.pw.edu.pl/~stepienj/smietnik/sh")

(def *root-addr* "/stepienj")

(defn include-sh-css
  [name]
  [:link
   {:href (str *assets-addr* "/styles/" name)
    :type "text/css" :rel "stylesheet"}])

(defn include-css
  [addr]
  [:link {:href addr :type "text/css" :rel "stylesheet"}])

(defn include-sh-js
  [name]
  [:script
   {:src (str *assets-addr* "/scripts/" name)
    :type "text/javascript"}])

(defn home-link
  []
  (html [:a {:href *root-addr*} "docjure" ]))

(defn ns-addr
  [ns]
  (str *root-addr* "/doc/" (url-encode ns)))

(defn ns-link
  [ns]
  (html [:a {:href (ns-addr ns)} ns]))

(defn var-link
  [ns df]
  (html [:a {:href (str (ns-addr ns) "/" (url-encode df))} df]))

(defn title
  ([] "docjure")
  ([ns] (str (title) " &raquo; " ns))
  ([ns var] (str (title ns) "/" var)))

(defn title-with-links
  ([] (home-link))
  ([ns] (str (title-with-links) " &raquo; " (ns-link ns)))
  ([ns var] (str (title-with-links ns) "/" (var-link ns var))))

(defn clojure-info
  []
  (str "Running Clojure " (clojure-version)))

(defn copyrights
  []
  (str "(C) 2010 Jan Stępień"))

(defn layout
  [title-coll & body]
  (html
    [:html
     [:head
      [:title (apply title title-coll)]
      (include-sh-css "shCoreJankowy.css")
      (include-sh-css "shThemeRDark.css")
      (include-sh-js "shCore.js")
      (include-sh-js "shBrushClojure.js")
      (include-css "http://fonts.googleapis.com/css?family=Inconsolata")
      ; TODO: abstraction!
      [:style {:type "text/css"}
       "body, pre, code { background: #1B2426; color: silver;"
       "font-family: \"Inconsolata\", monospace; font-size: 16px;"
       "width: 960px; margin: 10px auto;}"
       "a { color: #FFAA3E; }"]]
     [:body 
      [:h1 (apply title-with-links title-coll)]
      body
      [:small {:style "text-align: center"}
       [:div (clojure-info)]
       [:div (copyrights)]]]]))

(defn not-found []
  (html [:h1 "Page not found"]))

(defn var-name
  [ns var]
  (symbol (str ns "/" var)))

(defn doc-string
  [ns var]
  (apply str
         (drop-while
           #(or (= % \-) (= % \newline))
           (with-out-str (print-doc (find-var (var-name ns var)))))))

(defn source-string
  [ns var]
  (get-source (var-name ns var)))

(defn highlight!
  []
  [:script {:type "text/javascript"}
   "SyntaxHighlighter.defaults['gutter'] = false;"
   "SyntaxHighlighter.defaults['toolbar'] = false;"
   "SyntaxHighlighter.all();"])

(defn var-page [ns-str var-str]
  (layout
    [ns-str var-str]
    [:pre (escape-html (doc-string ns-str var-str))]
    [:pre {:class "brush: clojure;"}
     (escape-html (source-string ns-str var-str))]
    (highlight!)))

(defn sorted-publics [ns-str]
  (sort (keys (ns-publics (symbol ns-str)))))

(defn ns-contents [ns-str]
  (layout
    [ns-str]
    [:ul
     (for [x (map #(var-link ns-str (str %)) (sorted-publics ns-str))]
       [:li x])]))

(defn sorted-namespaces
  []
  (sort (filter #(not (= "user" %)) (map #(str (.name %)) (all-ns)))))

(defn main-page
  []
  (layout
    []
    [:ul
     (for [x (map ns-link (sorted-namespaces))]
       [:li x])]))

(defroutes our-routes
  (GET "/stepienj" [] (main-page))
  (GET ["/stepienj/doc/:ns", :ns #"[\w\-\.]+"] [ns] (ns-contents ns))
  (GET ["/stepienj/doc/:ns/:var", :ns #"[\w\-\.]+", :var #".*"] [ns var]
       (var-page ns var))
  (route/not-found (not-found)))

(defservice our-routes)
