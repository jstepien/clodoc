(ns docjure.core
  (:use compojure.core
        ring.util.servlet
        [ring.util.codec :only [url-encode]]
        [hiccup.core :only [html escape-html]]
        hiccup.page-helpers
        [clojure.contrib.repl-utils :only [get-source]])
  (:require [compojure.route :as route])
  (:gen-class
     :extends javax.servlet.http.HttpServlet))

(def *assets-addr* "http://gamma.mini.pw.edu.pl/~stepienj/smietnik/sh")

(def *root-addr* "/stepienj")

(defn include-sh-css
  [name]
  (include-css (str *assets-addr* "/styles/" name)))

(defn include-sh-js
  [name]
  (include-js (str *assets-addr* "/scripts/" name)))

(defn home-link
  []
  (link-to *root-addr* "docjure"))

(defn ns-addr
  [ns]
  (str *root-addr* "/doc/" (url-encode ns)))

(defn ns-link
  [ns]
  (link-to (ns-addr ns) (escape-html ns)))

(defn var-link
  [ns df]
  (link-to (str (ns-addr ns) "/" (url-encode df)) (escape-html df)))

(defn title
  ([] "docjure")
  ([ns] (str (title) " &raquo; " (escape-html ns)))
  ([ns var] (str (title ns) "/" (escape-html var))))

(defn title-with-links
  ([] (home-link))
  ([ns] (list (title-with-links) " &raquo; " (ns-link ns)))
  ([ns var] (list (title-with-links ns) "/" (var-link ns var))))

(defn clojure-info
  []
  (str "Running Clojure " (clojure-version)))

(defn copyrights
  []
  (str "(C) 2010 Jan Stępień"))

(defn layout
  [title-coll & body]
  (html
    (doctype :html5)
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
      [:div {:style "text-align: center; font-size: small"}
       (clojure-info) [:br] (copyrights)]]]))

(defn not-found []
  (layout [] [:p "Page not found."]))

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
  (javascript-tag
    (str
      "SyntaxHighlighter.defaults['gutter'] = false;"
      "SyntaxHighlighter.defaults['toolbar'] = false;"
      "SyntaxHighlighter.all();")))

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
    (unordered-list (map #(var-link ns-str (str %)) (sorted-publics ns-str)))))

(defn sorted-namespaces
  []
  (sort (filter #(not (= "user" %)) (map #(str (.name %)) (all-ns)))))

(defn main-page
  []
  (layout
    []
    (unordered-list (map ns-link (sorted-namespaces)))))

(defroutes our-routes
  (GET "/stepienj" [] (main-page))
  (GET ["/stepienj/doc/:ns", :ns #"[\w\-\.]+"] [ns] (ns-contents ns))
  (GET ["/stepienj/doc/:ns/:var", :ns #"[\w\-\.]+", :var #".*"] [ns var]
       (var-page ns var))
  (route/not-found (not-found)))

(defservice our-routes)
