(ns docjure.core
  (:use compojure.core
        [clojure.contrib.find-namespaces :only [find-namespaces-in-jarfile]]
        ring.util.servlet
        [ring.util.codec :only [url-encode]]
        [hiccup.core :only [html escape-html]]
        hiccup.page-helpers
        [clojure.contrib.repl-utils :only [get-source]])
  (:require [compojure.route :as route]
            [clojure.contrib.str-utils2 :as str2]
            [hiccup.form-helpers :as form]
            [docjure.cache :as cache])
  (:import java.util.jar.JarFile)
  (:gen-class
     :extends javax.servlet.http.HttpServlet))

(def *assets-addr* "")

(def *root-addr* "")

(def
  #^{:doc "A collection of JAR files to be searched for namespaces."}
  *documented-jar-files*
  (let [files ["WEB-INF/lib/clojure-1.2.1.jar"
               "WEB-INF/lib/clojure-contrib-1.2.0.jar"]]
    (if (.isDirectory (java.io.File. "war"))
      (map #(str "war/" %) files)
      files)))

(defn include-sh-css
  [name]
  (include-css (str *assets-addr* "/styles/" name)))

(defn include-sh-js
  [name]
  (include-js (str *assets-addr* "/scripts/" name)))

(defn home-link
  []
  (link-to (str *root-addr* "/") "docjure"))

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

(def fork-me-ribbon
  (link-to
    "http://github.com/jstepien/docjure"
    (html [:img
     {:style "position: absolute; top: 0; right: 0; border: 0;"
      :src "http://s3.amazonaws.com/github/ribbons/forkme_right_orange_ff7600.png"
      :alt "Fork me on GitHub"}])))

(defn clojure-info
  []
  (str "Running Clojure " (clojure-version)))

(defn copyrights
  []
  (str "(C) 2010-2011 Jan Stępień"))

(def search-form
  (form/form-to [:post (str *root-addr* "/search")]
                (form/text-field "what")
                (form/submit-button "Search")))

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
      search-form
      body
      [:div {:style "text-align: center; font-size: small"}
       (clojure-info) [:br] (copyrights)]
      fork-me-ribbon]]))

(defn not-found []
  (layout [] [:p "Page not found."]))

(defn var-name
  [ns var]
  (symbol (str ns "/" var)))

(defn doc-string
  [ns var]
  (cache/get!
    (str "doc-string:" ns "/" var)
    #(do
       (require (symbol ns))
       (apply str
              (drop-while
                (fn [c] (or (= c \-) (= c \newline)))
                (with-out-str (print-doc (find-var (var-name ns var)))))))))

(defn source-string
  [ns var]
  (cache/get!
    (str "source-string:" ns "/" var)
    #(do
       (require (symbol ns))
       (get-source (var-name ns var)))))

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
    (try
      (do
        (require (symbol ns-str))
        (unordered-list
          (map #(var-link ns-str (str %)) (sorted-publics ns-str))))
      (catch Exception e
        (list [:p "Cannot load namespace " ns-str]
              [:pre {:class :error} e])))))

(defn jar-files
  [files]
  (map #(java.util.jar.JarFile. %) files))

(defn interesting-namespaces
  []
  (cache/get!
    "interesting-namespaces"
    #(try
       (reduce
         (fn [coll jar] (concat coll (find-namespaces-in-jarfile jar)))
         [] (jar-files *documented-jar-files*))
       (catch Exception e []))))

(defn main-page
  []
  (layout
    []
    (unordered-list (map #(ns-link (str %)) (interesting-namespaces)))))

(defn ns-publics-hash
  []
  (cache/get!
    "ns-publics-hash"
    #(reduce
      (fn [hash ns]
        (try
          (do
            (require ns)
            (let
              [vars (map first (ns-publics ns))]
              (if (empty? vars)
                hash
                (assoc hash ns vars))))
          (catch Exception e hash)
          (catch NoClassDefFoundError e hash)))
      {} (interesting-namespaces))))

(defn find-vars-containing
  "Returns a map with vars containing a given string assigned to their
  namespaces."
  [x]
  (reduce
    (fn [hash ns]
      (try
        (let
          [vars (filter
                  #(str2/contains? (str %) x)
                  (get (ns-publics-hash) ns))]
          (if (empty? vars)
            hash
            (assoc hash ns vars)))))
    {} (interesting-namespaces)))

(defn search-results
  [what]
  (layout
    []
    [:p [:strong what] ", right? Let's see..."]
    (let
      [vars-hash (find-vars-containing what)]
      (if (empty? vars-hash)
        [:p "No results."]
        (unordered-list
          (map
            (fn [hash]
              (list (str (first hash))
                    (unordered-list
                      (map
                        #(var-link (str (first hash)) (str %))
                        (second hash)))))
            vars-hash))))))

(defroutes our-routes
  (GET "/" [] (main-page))
  (GET ["/doc/:ns", :ns #"[\w\-\.]+"] [ns] (ns-contents ns))
  (GET ["/doc/:ns/:var", :ns #"[\w\-\.]+", :var #".*"] [ns var]
       (var-page ns var))
  (POST "/search" [what] (search-results what))
  (route/not-found (not-found)))

(defservice our-routes)
