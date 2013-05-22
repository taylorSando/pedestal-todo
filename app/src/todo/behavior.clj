(ns ^:shared todo.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app.messages :as msg]))

(defn alter-tasks [tasks msg]
  (let [task (:value msg)]
   (condp = (msg/type msg)
     :create-tasks []
     :add-task (conj tasks task)
     :remove-task (vec (remove #(= task %) tasks ) tasks)
     :complete-task (mapv #(if (= task %) (assoc task :completed true) task) tasks))))

(def example-app
  {:version 2
   :transform [[:create-tasks [:tasks] alter-tasks]
               [:add-task [:tasks] alter-tasks]
               [:remove-task [:tasks] alter-tasks]
               [:complete-task [:tasks] alter-tasks]]})
