(ns docjure.persistent
  (:use am.ik.clj-gae-ds.core)
  (:require [docjure.cache :as cache]))

(defn- serialise
  [obj]
  (with-out-str (print-dup obj *out*)))

(defn- deserialise
  [^String str]
  (with-in-str str (read)))

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
