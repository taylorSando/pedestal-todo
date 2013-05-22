(ns ^:shared todo.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app :as app]
              [io.pedestal.app.dataflow :as dataflow]
              [io.pedestal.app.messages :as msg]))


(defn add-task [tasks msg]
  (let [task (:value msg)
        uuid (gensym "task")]
    (assoc tasks uuid (assoc task :uuid uuid))))

(defn alter-tasks [tasks msg]
  (let [task (:value msg)]
    (condp = (msg/type msg)
      :create-tasks {}      
      :remove-task (vec (remove #(= task %) tasks ) tasks)
      :complete-task (mapv #(if (= task %) (assoc task :completed true) task) tasks))))

(defn init-emitter [_]
  [:node-create [] :map]
  [:node-create [:tasks] :map])

(defn todo-emitter [inputs]
  ;; Find out if the main tasks map was just created
  (if (= (first (:added inputs)) [:tasks])
    (init-emitter)
    ;; Deal with the individual tasks
    (let [added (dataflow/added-inputs inputs) ]
      (mapcat (fn [[_ k]]                
                [[:node-create [:tasks k] :map]
                 [:value [:tasks k] (get-in (:new-model inputs) [:tasks k])]])
              (:added inputs))
      
      )))

(def example-app
  {:version 2
   :transform [[:create-tasks [:tasks] alter-tasks]
               [:add-task [:tasks] add-task]
               [:remove-task [:tasks] alter-tasks]
               [:complete-task [:tasks] alter-tasks]]
   :emit [{:in #{[:tasks]} :fn todo-emitter}]})

