(ns clodoc.jars-handler
  (:require [clojure.xml :as xml]
            [clojure.contrib.str-utils2 :as str2]
            [clojure.string :as string]
            [clojure-http.resourcefully :as res]
            [clodoc.persistent :as persistent]
            [clodoc.cache :as cache]))

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
        buf (byte-array buflen)
        data (in-parens
               (loop [contents ""]
                 (let [nbytes (.read zip buf 0 buflen)]
                   (if (= -1 nbytes)
                     contents
                     (recur (str contents (String. buf 0 nbytes)))))))]
    {:code data :sexps (with-in-str data (read))}))

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
  (str2/join "\n"
             (map str2/trim
                  (str2/split-lines
                    (or
                      (:doc (meta (second def)))
                      (let [doc (second (rest def))]
                        (and (string? doc) doc))
                      "")))))

(defn log [& msg]
  (println (.format (java.text.DateFormat/getDateTimeInstance)
                    (java.util.Date.))
           "-" (string/join " " (map str msg))))

(def definitions
  '#{def definline definterface defmacro defmulti defn defonce defprotocol
     defrecord defstruct deftype})

(defn definition?
  [sexp]
  (definitions (first sexp)))

(defn try-read
  [^String code]
  (let [lines (string/split-lines code)
        lines-count (count lines)]
    (loop [num 2]
      (if (> num lines-count)
        ['() ""]
        (let [joined (string/join "\n" (take num lines))
              read-str #(with-in-str % (read))
              sexp (try (read-str joined)
                     (catch RuntimeException ex nil))]
          (if sexp
            [sexp joined]
            (recur (inc num))))))))

(defn source-string
  [^String whole-code def]
  "This function badly needs to be optimised."
  (let [to-next-paren (fn [string] (apply str (drop-while #(not (= \( %)) string)))
        defines? (fn [code-sexps] (= (take 2 def) (take 2 code-sexps)))]
    (apply log (take 2 def))
    (loop [code- (.substring whole-code 1)]
      (let [^String code (to-next-paren code-)
            [sexps sexps-code] (try-read code)]
        ;(print "  ") (prn (apply str (take 20 code)) (take 2 sexps))
        (if (empty? code)
          (throw (Exception. (str "Failed to read "
                                  (string/join " " (take 2 def))))))
        (if (defines? sexps)
          sexps-code
          (recur (.substring code 1)))))))

(defn ns-contents
  [{sexps :sexps code :code}]
  (reduce
    (fn [acc def]
      (assoc acc (second def)
             {:doc (doc-string def)
              :src (source-string code def)}))
    {} (filter definition? sexps)))

(defn get-namespace
  [sexps]
  (let [ns (second (first (filter #('#{ns in-ns} (first %)) sexps)))]
    (cond (symbol? ns)          ns
          (= 'quote (first ns)) (second ns))))

(defn ns-publics-in
  [sexps]
  (reduce
    (fn [acc [filename contents]]
      (let [ns (get-namespace (:sexps contents))]
        (if (or (nil? ns) (re-find #"\.examples?\.?" (name ns)))
          acc
          (do
            (log "Namespace: " ns)
            (assoc acc ns (conj (or (acc ns) {})
                                (ns-contents contents)))))))
    {} sexps))

(defn clj-files-in
  [name]
  (if (re-matches #"^http.*\.jar$" name)
    (clj-files-in-jar name)
    (clj-files-in-jar (get-url-for name))))

(defn populate-storage
  [data]
  (let [old-ns (or (persistent/get! "all-ns") [])]
    (cache/clear!)
    (persistent/put! "all-ns" (sort (distinct (concat old-ns (map first data)))))
    (doseq [[ns vars] data]
      (persistent/put! (str "ns:" ns) (map first vars))
      (doseq [[name meta] vars]
        (persistent/put! (str "var:" ns "/" name) meta)))))

(defn scan
  [name]
  (populate-storage (ns-publics-in (clj-files-in name))))
