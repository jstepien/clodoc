(ns docjure.background
  (:import com.google.appengine.api.taskqueue.QueueFactory
           com.google.appengine.api.taskqueue.TaskOptions$Builder))

(defn add-task
  [route params]
  (let [queue (QueueFactory/getDefaultQueue)
        opts (reduce
               (fn [opts [k v]] (.param opts (name k) (name v)))
               (TaskOptions$Builder/withUrl route) params)]
    (.add queue opts)))
