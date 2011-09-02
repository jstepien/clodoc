(ns docjure.search-cache
  (:require [docjure.cache :as cache])
  (:import com.google.appengine.api.taskqueue.QueueFactory
           com.google.appengine.api.taskqueue.TaskOptions$Builder))

(let [cache-prepared (ref false)]
  (defn prepare
    [namespaces]
    (if (not @cache-prepared)
      (dosync
        (cache/put!
          "ns-publics-hash"
          (reduce
            (fn [hash ns]
              (prn ns)
              (try
                (do
                  (require ns)
                  (let
                    [vars (map first (ns-publics ns))]
                    (if (empty? vars)
                      hash
                      (assoc hash ns vars))))
                (catch Exception e hash)
                (catch NoClassDefFoundError e hash)))
            {} namespaces))
        (ref-set cache-prepared true)))))

(let [cache-preparation-enqueued (ref false)]
  (defn enqueue-preparation
    []
    (if (not @cache-preparation-enqueued)
      (dosync
        (. (QueueFactory/getDefaultQueue) add
           (TaskOptions$Builder/withUrl "/build_search_cache"))
        (ref-set cache-preparation-enqueued true)))))
