(ns docjure.cache
  (:require [docjure.persistent :as persistent])
  (:import net.sf.jsr107cache.CacheManager
           java.util.Collections))

(def #^net.sf.jsr107cache.Cache jcache
  (.createCache (.getCacheFactory (CacheManager/getInstance))
                (Collections/emptyMap)))

(def vm-cache (ref {}))

(defn- serialise
  [obj]
  (with-out-str (print-dup obj *out*)))

(defn- deserialise
  [str]
  (with-in-str str (read)))

(defn put!
  [^Named key value]
  (let [serialised (serialise value)]
    (dosync (ref-set vm-cache (assoc @vm-cache key value)))
    (try
      (.put jcache key serialised)
      (catch NullPointerException e nil))
    (persistent/put! key serialised)))

(defn get!
  ([^Named key]
   (let [put-in-vmcache #(dosync (ref-set vm-cache (assoc @vm-cache key %)))]
   (or
     (get @vm-cache key)
     (println "Not present in vm-cache: " key)
     (try
       (if-let [value (deserialise (.get jcache key))]
         (do
           (put-in-vmcache value)
           value))
       (catch NullPointerException e nil))
     (println "Not present in jcache: " key)
     (if-let [value (deserialise (persistent/get! key))]
       (do
         (put-in-vmcache value)
         (.put jcache key (serialise value))
         value))
     (println "Not present in persistent storage: " key))))
  ([^Named key func]
   (or
     (get! key)
     (let [value (func)]
       (put! key value)
       value))))
