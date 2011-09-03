(ns docjure.jars-handler
  (:use [clojure.contrib.find-namespaces :only [find-namespaces-in-jarfile]]
        [clojure.contrib.repl-utils :only [get-source]]
        compojure.core)
  (:require [clojure.xml :as xml]
            [clojure.contrib.str-utils2 :as str2]
            [clojure-http.resourcefully :as res]
            [compojure.handler :as handler]
            [docjure.security :as security]
            [ring.adapter.jetty :as jetty]
            [clojure.contrib.classpath :as cp])
  (:gen-class))

(defn get-body
  [url]
  (apply str (:body-seq (res/get url))))

(defn parse-xml-str [#^String xml]
  (xml/parse (java.io.ByteArrayInputStream. (.getBytes (.trim xml)))))

(defn get-url-for
  [name]
  (let [pkg (str2/replace name #".*/" "")
        path (str2/replace name \. \/)
        main-url (str "http://clojars.org/repo/" path "/")
        pkg-meta (parse-xml-str (get-body (str main-url "maven-metadata.xml")))
        versions (:content (first (:content (first
                                              (filter #(= (:tag %) :versioning)
                                                      (:content pkg-meta))))))
        newest-version (last
                         (filter
                           #(not (str2/contains? % "SNAPSHOT"))
                           (map #(first (:content %)) versions)))
        jar-url (str main-url newest-version "/" pkg "-" newest-version ".jar")]
    jar-url))

(def *trusted-sites*
  ["http://build.clojure.org/releases/org/clojure/"])

(defn trusted?
  [#^String addr]
  (not (empty?
         (filter true? (map #(.startsWith addr %) *trusted-sites*)))))

(defn download-jar
  [addr]
  (if (not (trusted? addr))
    (throw (Exception. (str addr " isn't a trusted URI")))
    (let [buflen 4096
          buf (byte-array buflen)
          file (java.io.File/createTempFile "docjure" ".jar")
          url (java.net.URL. addr)
          in (java.io.BufferedInputStream. (.openStream url))
          fos (java.io.FileOutputStream. file)
          bout (java.io.BufferedOutputStream. fos buflen)]
      (loop []
        (let [nbytes (.read in buf)]
          (if (= -1 nbytes)
            (do
              (.flush bout)
              (map #(.close %) [bout fos in])
              (java.util.jar.JarFile. file))
            (do
              (.write bout buf 0 nbytes)
              (recur))))))))

(defn namespaces-in
  [jar]
  (try
    (find-namespaces-in-jarfile jar)
    (catch Exception e (do (prn e) []))))

(defn spawn
  [& args]
  (.start (ProcessBuilder. (java.util.ArrayList. args))))

(defn unzip
  [file]
  (if (not (= 0 (.waitFor (spawn "unzip" "-qo" file "-d" "tmpclasspath/"))))
    (throw (Exception. (str "unzipping " file " failed")))))

(defn var-name
  [ns var]
  (symbol (str ns "/" var)))

(defn vars-data
  [ns keys]
  (reduce
    (fn [acc var]
      (assoc acc var
             {:doc
              (apply
                str
                (drop-while
                  (fn [c] (or (= c \-) (= c \newline)))
                  (with-out-str
                    (print-doc (find-var (var-name ns var))))))
              :source (get-source (var-name ns var))}))
    {} keys))

(defn ns-publics-in
  [#^java.util.jar.JarFile jar]
  (let [namespaces (namespaces-in jar)]
    (reduce (fn [acc ns]
              (do
                (unzip (.getName jar))
                (try
                  (do
                    (require (symbol ns))
                    (assoc acc ns (vars-data ns (keys (ns-publics ns)))))
                  (catch Exception ex acc))))
            {} namespaces)))

(defn fetch-jar-file
  [name]
  (if (re-matches #"^http.*\.jar$" name)
    (download-jar name)
    (download-jar (get-url-for name))))

(defn scan
  [name callback]
  (let [data (str (ns-publics-in (fetch-jar-file name)))]
    (res/post callback {} {:data data :signature (security/sign data)})))

(defn start-scan-process
  [name callback]
  (spawn "java" "-client" "-cp"
         "docjure-0.0.1-SNAPSHOT.jar:lib/*:lib/dev/*:tmpclasspath"
         "docjure.jars_handler" name callback))

(defroutes main-routes
  (POST "/scan" {{name :name callback :callback} :params}
        (do (start-scan-process name callback) "ok")))

(defn -main
  ([]
   (jetty/run-jetty (handler/api main-routes) {:port 8090}))
  ([name callback]
   (scan name callback)))
