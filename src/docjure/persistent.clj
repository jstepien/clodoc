(ns docjure.persistent
  (:use am.ik.clj-gae-ds.core)
  (:require [docjure.cache :as cache])
  (:import [java.util.zip GZIPOutputStream GZIPInputStream]
           [org.apache.commons.codec.binary Base64]
           [java.io ByteArrayOutputStream ByteArrayInputStream
                    InputStreamReader]))

(defn- compress
  "Returns a string compressed with gzip and encoded with base64."
  [string]
  (let [out (ByteArrayOutputStream.)
        gzip (GZIPOutputStream. out)]
    (spit gzip string)
    (String. (Base64/encodeBase64 (.toByteArray out)))))

(defn- decompress
  "Does the opposite of compress, not surprisingly."
  [^String data]
  (let [is (ByteArrayInputStream. (Base64/decodeBase64 data))
        gzip (GZIPInputStream. is)]
    (slurp gzip)))

(defn- serialise
  [obj]
  (let [dump #(with-out-str (print-dup % *out*))
        reasonable-size-difference 100
        raw (dump obj)
        compressed (compress raw)]
    (dump
      (if (> (- (count raw) (count compressed)) reasonable-size-difference)
        [:compressed compressed]
        [:raw raw]))))

(defn- deserialise
  [^String str]
  (let [load #(with-in-str % (read))
        loaded (load str)
        data (second loaded)]
    (case (first loaded)
      :raw        (load data)
      :compressed (load (decompress data)))))

(defn- get-entity
  [key]
  (try
    (first (query-seq (add-filter (query "kv") "key" = key)))
    (catch NullPointerException ex nil)))

(defn put!
  [^String key value]
  (let [serialised (serialise value)]
    (with-transaction
      (if-let [old (get-entity key)]
        (let [old-key (get-key old)]
          (ds-put (map-entity "kv" "key" key "val" serialised :parent old-key))
          (ds-delete old-key))
        (ds-put (map-entity "kv" "key" key "val" serialised))))
    (cache/delete! key)))

(defn get!
  [^String key]
  (or (cache/get! key)
      (try
        (let [value (deserialise (get-prop (get-entity key) "val"))]
          (cache/put! key value)
          value)
        (catch NullPointerException ex nil))))
