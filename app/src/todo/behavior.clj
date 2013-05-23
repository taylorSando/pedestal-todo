(ns ^:shared todo.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app :as app]
              [io.pedestal.app.dataflow :as dataflow]
              [io.pedestal.app.messages :as msg]))

(defn create-todo [tasks msg]  
  {:tasks [] :count 0 :filter :any})

(defn set-todo-filter [v msg]    
  (:filter msg))

(defn add-task [tasks msg]  
  (let [task {:details (:details msg)}
        id (or (:id msg) (gensym "task"))]
    (conj tasks (assoc task :id id))))

(defn edit-task [tasks msg]
  (mapv #(if (= (:id %) (:id msg)) (assoc % :details (:details msg)) %) tasks))

(defn remove-task [tasks msg]
  (vec (remove #(= (:id msg) (:id %)) tasks)))

(defn remove-completed [tasks msg]
  (vec (remove #(:completed %) tasks)))

(defn toggle-tasks [tasks msg]
  (mapv #(update-in % [:completed] not) tasks))

(defn toggle-task [tasks msg]
  (mapv #(if (= (:id %) (:id msg)) (update-in % [:completed] not) %) tasks))

(mapv #(update-in % [:completed] not) [{:x 5 :completed true} {:y 6 :completed false}])

(defn filter-tasks [_ inputs]  
  (let [tasks (get-in inputs [:new-model :todo :tasks])
        filter-type (get-in inputs [:new-model :todo :filter])]        
    (condp = filter-type
          :completed (vec (filter #(:completed %) tasks))
          :active (vec (remove #(:completed %) tasks))
          :any tasks)))

(defn init-emitter [inputs]
  (when (= (:new-model inputs) {:tasks {}})
    [ [:node-create [] :map]
      [:node-create [:todo] :map]
      [:node-create [:todo :tasks] :map]                  
      [:transform-enable [:todo :tasks] :add-tasks [{msg/type :add-task msg/topic [:todo :tasks]
                                               (msg/param :details) {}}]]]))

(defn count-tasks [_ inputs]
  (count (get-in inputs [:new-model :todo :filtered-tasks])))

(defn todo-emitter [inputs]
  ;; Find out if the main tasks map was just created
  (if (= (first (:added inputs)) [:todo :tasks])
    (init-emitter inputs)
    ;; Deal with the individual tasks
    (let [added (dataflow/added-inputs inputs) ]
      (mapcat (fn [[_ k]]                
                [[:node-create [:todo :tasks k] :map]
                 [:value [:todo :tasks k] (get-in (:new-model inputs) [:todo :tasks k])]
                 [:transform-enable [:todo :tasks k] :complete-task [{msg/topic [:todo :tasks] msg/type :complete-task :id k}]]
                 ])
              (:added inputs))
      
      )))

(defn count-emit [inputs]
  ;; Find out if the main tasks map was just created
  ;(.log js/console "asdfasdfasdasdfsdfasdfasdf")
  )

(def example-app
  {:version 2
   :transform [[:create-todo [:todo] create-todo]
               [:set-todo-filter [:todo :filter] set-todo-filter]
               [:add-task [:todo :tasks] add-task]
               [:remove-task [:todo :tasks] remove-task]
               [:edit-task [:todo :tasks] edit-task]               
               [:remove-completed [:todo :tasks] remove-completed]
               [:toggle-tasks [:todo :tasks] toggle-tasks]
               [:toggle-task [:todo :tasks] toggle-task]
               ]
   ;:emit [{:in #{[:**]} :fn (app/default-emitter)}]
  :derive [[#{[:todo :filtered-tasks]} [:todo :count] count-tasks]
            [#{[:todo :filter] [:todo :tasks]} [:todo :filtered-tasks] filter-tasks]
            ]
   #_:emit #_[
          #_{:in #{[:todo :tasks]} :fn todo-emitter}
          #_{:in #{[:todo :tasks-left]} :fn count-emit}]})
