(ns docjure.cache
  (:import net.sf.jsr107cache.CacheManager
           java.util.Collections))

(def jcache
  (.createCache (.getCacheFactory (CacheManager/getInstance))
                (Collections/emptyMap)))

(def vm-cache (ref {}))

(defn put!
  [key value]
  (do
    (dosync (ref-set vm-cache (assoc @vm-cache key value)))
    (try
      (.put jcache key (with-out-str (print-dup value *out*)))
      (catch NullPointerException e nil))))

(defn get!
  ([key]
   (or
     (get @vm-cache key)
     (prn "Not present in vm-cache")
     (try
       (let [value (with-in-str (.get jcache key) (read))]
         (if value
           (dosync (ref-set vm-cache (assoc @vm-cache key value))))
         value)
       (catch NullPointerException e nil))
     (prn "Not present in jcache")))
  ([key func]
   (or
     (get! key)
     (let [value (func)]
       (put! key value)
       value))))
