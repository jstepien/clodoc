(ns docjure.cache
  (:use docjure.common)
  (:import net.sf.jsr107cache.CacheManager
           java.util.Collections))

(def #^net.sf.jsr107cache.Cache jcache
  (.createCache (.getCacheFactory (CacheManager/getInstance))
                (Collections/emptyMap)))

(def vm-cache (ref {}))

(declare get!)
(declare put!)
(declare clear!)

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
          (println "Versions differ")
          (clear!)
          (put! key version))))))

(defn- put-in-vmcache
  [key val]
  (dosync (ref-set vm-cache (assoc @vm-cache key val))))

(defn put!
  [^clojure.lang.Named key value]
  (do
    (put-in-vmcache key value)
    (try
      (.put jcache key value)
      (catch NullPointerException e nil))))

(defn get!
  ([^clojure.lang.Named key]
   (do
     (check-cache-version)
     (or
       (get @vm-cache key)
       (println "Not present in vm-cache: " key)
       (try
         (if-let [value (.get jcache key)]
           (do
             (put-in-vmcache key value)
             value))
         (catch NullPointerException e nil))
       (println "Not present in jcache: " key))))
  ([^clojure.lang.Named key func]
   (or
     (get! key)
     (let [value (func)]
       (put! key value)
       value))))

(defn delete!
  [^clojure.lang.Named key]
  (do
    (dosync (ref-set vm-cache (dissoc @vm-cache key)))
    (try
      (.remove jcache key)
      (catch NullPointerException e nil))))

(defn clear!
  []
  (do
    (println "Clearing cache")
    (dosync (ref-set vm-cache {}))
    (.clear jcache)))
