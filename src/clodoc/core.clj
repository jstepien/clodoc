(ns clodoc.core
  (:use compojure.core
        ring.util.servlet
        [ring.util.codec :only [url-encode]]
        [hiccup.core :only [html escape-html]]
        hiccup.page-helpers
        [clojure.contrib.repl-utils :only [get-source]]
        clodoc.common)
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            clojure.pprint
            [clojure.string :as string]
            [clojure.contrib.str-utils2 :as str2]
            [hiccup.form-helpers :as form]
            [clodoc.cache :as cache]
            [clodoc.persistent :as persistent]
            [clodoc.background :as background]
            [clodoc.jars-handler :as jars-handler]
            [clodoc.javascript :as js]
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
  (link-to (str *root-addr* "/") "clodoc"))

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
  ([] "clodoc")
  ([ns] (str (title) " &raquo; " (escape-html ns)))
  ([ns var] (str (title ns) "/" (escape-html var))))

(defn title-with-links
  ([] (home-link))
  ([ns] (list (title-with-links) " &raquo; " (ns-link ns)))
  ([ns var] (list (title-with-links ns) "/" (var-link ns var))))

(def fork-me-ribbon
  (link-to
    "http://github.com/jstepien/clodoc"
    (html [:img
     {:style "position: absolute; top: 0; right: 0; border: 0;"
      :src "http://s3.amazonaws.com/github/ribbons/forkme_right_orange_ff7600.png"
      :alt "Fork me on GitHub"}])))

(defn version-info
  []
  (str "Running Clodoc "
       (html
         [:a {:href (str "https://github.com/jstepien/clodoc/commits/" version)}
          version])
       " on Clojure " (clojure-version)))

(defn copyrights
  []
  (str "(C) 2010-2011 Jan Stępień"))

(def search-form
  (form/form-to [:post (str *root-addr* "/search")]
                (form/text-field "what")
                (form/submit-button "Search")))

(defn google-analytics
  []
  (javascript-tag
   (js/compress
     "var _gaq = _gaq || [];
     _gaq.push(['_setAccount', 'UA-1018917-12']);
     _gaq.push(['_trackPageview']);

     (function() {
     var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
     ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
     var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
     })();")))

(defn layout
  [title-coll & body]
  (html
    (doctype :html5)
    [:html
     [:head
      [:meta {:http-equiv "Content-Type" :content "text/html;charset=utf-8"}]
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
       "a { color: #FFAA3E; }"]
      (google-analytics)]
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
    (js/compress
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
        [:p "No results. "
         (link-to
           (str "https://www.google.com/search?q=site:www.clodoc.org+"
                (string/replace what #" +" "+"))
           "Try Google instead") "."]
        (unordered-list
          (map
            (fn [hash]
              (list (str (first hash))
                    (unordered-list
                      (map
                        #(var-link (str (first hash)) (str %))
                        (second hash)))))
            vars-hash))))))

(defn cache
  [resp]
  (let [k "Cache-Control" v (str "public, max-age: " (* 60 60))]
    (cond
      (string? resp)  {:headers {k v} :body resp}
      (map? resp)     (assoc resp :headers (assoc (resp :headers) k v)))))


(defmulti jsonify class)

(defmethod jsonify clojure.lang.Named
  [x]
  (jsonify (name x)))

(defmethod jsonify String
  [x]
  (string/trim (prn-str x)))

(defmethod jsonify Number
  [x]
  (str x))

(defmethod jsonify clojure.lang.IPersistentMap
  [x]
  (str \{
       (string/join \, (map (fn [[k v]] (str (jsonify k) \: (jsonify v))) x))
       \}))

(defn json-reply
  [obj]
  {:headers {"Content-Type" "application/json"}
   :body (str (jsonify obj) "\n")})

(defn accepting
  [headers & options]
  (if (or (= (first options) :else)
          (= (first options) (headers "accept")))
    (second options)
    (apply accepting headers (drop 2 options))))

(defn ns-uris-hash
  []
  {:namespaces
   (apply hash-map (mapcat #(list % (str "/v0/ns/" %)) (all-namespaces)))})

(defn ns-contents-hash
  [ns]
  (apply hash-map (mapcat #(list % (str "/v0/ns/" ns "/" %))
                          (sorted-publics (str ns)))))

(defn var-hash
  [ns var]
  (let [{src :src doc :doc} (persistent/get!  (str "var:" ns "/" var))]
    {:src src :doc doc}))

(def json "application/json")

(defn only-json
  [headers response]
  (accepting headers
    json response
    :else {:status 406})) ; 406 Not Acceptable

(defroutes our-routes
  (GET "/" {hdr :headers} (cache
                            (accepting hdr
                              json (json-reply {"API version 0" "/v0"})
                              :else (main-page))))
  (GET ["/doc/:ns", :ns #"[\w\-\.]+"] [ns] (cache (ns-contents ns)))
  (GET ["/doc/:ns/:var", :ns #"[\w\-\.]+", :var #".*"] [ns var]
       (cache (var-page ns var)))
  (POST "/search" {params :params} (search-results (params :what)))
  (POST "/scan" [name] (do
                         (background/add-task "/background_scan" {:name name})
                         (str "scheduled " name)))
  (POST "/background_scan" {{name :name} :params} (do
                                                    (jars-handler/scan name)
                                                    (str "scanned " name)))
  (GET ["/v0"] {hdr :headers}
       (only-json hdr (cache (json-reply (ns-uris-hash)))))
  (GET ["/v0/ns/:ns", :ns #"[\w\-\.]+"] {hdr :headers {ns :ns} :params}
       (only-json hdr (cache (json-reply (ns-contents-hash ns)))))
  (GET ["/v0/ns/:ns/:var", :ns #"[\w\-\.]+", :var #".*"]
       {hdr :headers {ns :ns var :var} :params}
       (only-json hdr (cache (json-reply (var-hash ns var)))))
  (route/not-found (not-found)))

(defservice
  (handler/api our-routes))
