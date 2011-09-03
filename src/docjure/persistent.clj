(ns docjure.persistent
  (:use am.ik.clj-gae-ds.core))

(defn- get-entity
  [key]
  (try
    (first (query-seq (add-filter (query "kv") "key" = key)))
    (catch NullPointerException ex nil)))

(defn put!
  [^String key ^String value]
  (with-transaction
    (if-let [old (get-entity key)]
      (let [old-key (get-key old)]
        (ds-put (map-entity "kv" "key" key "val" value :parent old-key))
        (ds-delete old-key))
      (ds-put (map-entity "kv" "key" key "val" value)))))

(defn get!
  [^String key]
  (try
    (get-prop (get-entity key) "val")
    (catch NullPointerException ex "nil")))
