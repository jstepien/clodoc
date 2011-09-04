(ns docjure.cache
  (:use docjure.common)
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

(declare get!)
(declare put!)

(defmacro preventing-recursion
  [& body]
  `(let [fn-stack# (map #(str (.getClassName ^StackTraceElement %))
                       (.getStackTrace (.fillInStackTrace (Throwable.))))
         current# (first fn-stack#)]
     (if (not ((apply hash-set (distinct (rest fn-stack#))) current#))
       (do ~@body))))


(defn- check-cache-version
  []
  (preventing-recursion
    (let [key "cache-version"
          cache-version (get! key)]
      (if (not (= version cache-version))
        (do
          (println "Versions differ, clearing cache")
          (dosync (ref-set vm-cache {}))
          (.clear jcache)
          (put! key version))))))

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
     (check-cache-version)
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
