(ns docjure.jars-handler
  (:use [clojure.pprint :only [pprint]])
  (:require [clojure.xml :as xml]
            [clojure.contrib.str-utils2 :as str2]
            [clojure-http.resourcefully :as res]
            [docjure.cache :as cache]))

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

(defn read-zip-entry
  [zip]
  (let [in-parens #(str "(\n" % "\n)")
        buflen 4096
        buf (byte-array buflen)]
    (with-in-str
      (in-parens
        (loop [contents ""]
          (let [nbytes (.read zip buf 0 buflen)]
            (if (= -1 nbytes)
              contents
              (recur (str contents (String. buf 0 nbytes)))))))
      (read))))

(defn clj-files-in-jar
  [addr]
  (let [url (java.net.URL. addr)
        in (java.io.BufferedInputStream. (.openStream url))
        zip (java.util.zip.ZipInputStream. in)]
    (loop [entries {}]
      (if-let [entry (.getNextEntry zip)]
        (if (.endsWith (.getName entry) ".clj")
          (recur (assoc entries (.getName entry) (read-zip-entry zip)))
          (recur entries))
        entries))))

(defn doc-string
  [def]
  (or
    (:doc (meta (second def)))
    (let [doc (second (rest def))]
      (and (string? doc) doc))
    ""))

(def definitions
  '#{def definline definterface defmacro defmulti defn defonce defprotocol
     defrecord defstruct deftype})

(defn definition?
  [sexp]
  (definitions (first sexp)))

(defn ns-contents
  [ns sexps]
  (reduce
    (fn [acc def]
      (assoc acc (second def) {:doc (doc-string def)
                               :src (with-out-str (pprint def))}))
    {} (filter definition? sexps)))

(defn get-namespace
  [sexps]
  (second (first (filter #(= (first %) 'ns) sexps))))

(defn ns-publics-in
  [sexps]
  (reduce
    (fn [acc [filename contents]]
      (let [ns (get-namespace contents)]
        (if (or (nil? ns) (.endsWith (str ns) ".examples"))
          acc
          (assoc acc ns (ns-contents ns contents)))))
    {} sexps))

(defn clj-files-in
  [name]
  (if (re-matches #"^http.*\.jar$" name)
    (clj-files-in-jar name)
    (clj-files-in-jar (get-url-for name))))

(defn populate-cache
  [data]
  (let [old-ns (or (cache/get! "all-ns") [])]
    (cache/put! "all-ns" (sort (distinct (concat old-ns (map first data)))))
    (doseq [[ns vars] data]
      (cache/put! (str "ns:" ns) (map first vars))
      (doseq [[name meta] vars]
        (cache/put! (str "var:" ns "/" name) meta)))))

(defn scan
  [name]
  (populate-cache (ns-publics-in (clj-files-in name))))
