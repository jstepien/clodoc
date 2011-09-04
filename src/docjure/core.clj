(ns docjure.core
  (:use compojure.core
        ring.util.servlet
        [ring.util.codec :only [url-encode]]
        [hiccup.core :only [html escape-html]]
        hiccup.page-helpers
        [clojure.contrib.repl-utils :only [get-source]]
        docjure.common)
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            clojure.pprint
            [clojure.contrib.str-utils2 :as str2]
            [hiccup.form-helpers :as form]
            [docjure.cache :as cache]
            [docjure.persistent :as persistent]
            [docjure.background :as background]
            [docjure.jars-handler :as jars-handler]
            [clojure-http.resourcefully :as res])
  (:import java.util.jar.JarFile)
  (:gen-class
     :extends javax.servlet.http.HttpServlet))

(def *assets-addr* "")

(def *root-addr* "")

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

(defn version-info
  []
  (str "Running Docjure "
       (html
         [:a {:href (str "https://github.com/jstepien/docjure/commit/" version)}
          version])
       " on Clojure " (clojure-version)))

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
       (version-info) [:br] (copyrights)]
      fork-me-ribbon]]))

(defn not-found []
  (layout [] [:p "Page not found."]))

(defn var-name
  [ns var]
  (symbol (str ns "/" var)))

(defn highlight!
  []
  (javascript-tag
    (str
      "SyntaxHighlighter.defaults['gutter'] = false;"
      "SyntaxHighlighter.defaults['toolbar'] = false;"
      "SyntaxHighlighter.all();")))

(defn var-page [ns-str var-str]
  (cache/get!
    (str "var-rendered:" ns-str "/" var-str)
    #(let [{src :src doc :doc} (persistent/get!
                                 (str "var:" ns-str "/" var-str))]
       (layout
         [ns-str var-str]
         (if (not (empty? doc))
           [:pre (escape-html doc)])
         [:pre {:class "brush: clojure;"}
          (escape-html src)]
         (highlight!)))))

(defn sorted-publics [ns-str]
  (sort (persistent/get! (str "ns:" ns-str))))

(defn ns-contents [ns-str]
  (layout
    [ns-str]
    (try
      (cache/get!
        (str "ns-rendered:" ns-str)
        (fn []
          (unordered-list
            (map #(var-link ns-str (str %)) (sorted-publics ns-str)))))
      (catch Exception e
        (list [:p "Cannot load namespace " ns-str]
              [:pre {:class :error} e])))))

(defn all-namespaces
  []
  (persistent/get! "all-ns"))

(defn main-page
  []
  (layout
    []
    (unordered-list (map #(ns-link (str %)) (all-namespaces)))))

(defn find-vars-containing
  "Returns a map with vars containing a given string assigned to their
  namespaces."
  [x]
  (reduce
    (fn [hash ns]
      (try
        (let [vars (filter #(str2/contains? (str %) x)
                           (persistent/get! (str "ns:" ns)))]
          (if (empty? vars)
            hash
            (assoc hash ns vars)))))
    {} (all-namespaces)))


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

(defn add-cache-control
  [html]
  {:headers {"Cache-Control" (str "public, max-age: " (* 60 60))}
   :body html})

(defroutes our-routes
  (GET "/" [] (add-cache-control (main-page)))
  (GET ["/doc/:ns", :ns #"[\w\-\.]+"] [ns] (add-cache-control (ns-contents ns)))
  (GET ["/doc/:ns/:var", :ns #"[\w\-\.]+", :var #".*"] [ns var]
       (add-cache-control (var-page ns var)))
  (POST "/search" {params :params} (search-results (params :what)))
  (POST "/scan" [name] (do
                         (background/add-task "/background_scan" {:name name})
                         (str "scheduled " name)))
  (POST "/background_scan" {{name :name} :params} (do
                                                    (jars-handler/scan name)
                                                    (str "scanned " name)))
  (route/not-found (not-found)))

(defservice
  (handler/api our-routes))
