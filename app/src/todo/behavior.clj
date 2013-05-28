(ns ^:shared todo.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app :as app]
              [io.pedestal.app.dataflow :as dataflow]
              [io.pedestal.app.messages :as msg]))

(defn create-todo [todo msg]  
  (assoc todo :tasks {} :count 0 :completed-count 0 :filter :any))

(defn set-todo-filter [v msg]  
  (:filter msg))

(defn add-task [tasks msg]  
  (let [task {:details (:details msg)}
        id (or (:id msg) (gensym "task"))]    
    (assoc tasks id (assoc task :id id))))

(defn remove-task [tasks msg]
  (dissoc tasks (:id msg)))

(defn remove-completed [tasks msg]
  (vec (remove #(:completed %) tasks)))

(defn toggle-task [tasks msg]  
  (update-in tasks [(:id msg) :completed] not))

;; Need to toggle


(defn toggle-all [todo msg]
  (let [filter-type (:filter todo)
        tasks (:tasks todo)]
    ;; The tasks are going to be toggled, but only those that would be visible
    (assoc todo :tasks
           (reduce
            (fn [toggled-tasks [task-id task]]                                              
              (if (or (= filter-type :any)
                      (and (= filter-type :completed) (:completed task))
                      (and (= filter-type :active) (not (:completed task))))
                (assoc toggled-tasks task-id (update-in task [:completed] not))
                (assoc toggled-tasks task-id task)))
            {}
            tasks))))

(defn init-emitter [inputs]
  [[:transform-enable [:todo :filter] :set-filter [{msg/type :set-filter msg/topic [:todo :filter]
                                                  (msg/param :filter) {}}]]
   [:transform-enable [:todo :tasks] :add-tasks [{msg/type :add-task msg/topic [:todo :tasks]
                                                  (msg/param :details) {}}]]
   [:transform-enable [:todo] :toggle-all [{msg/type :toggle-all msg/topic [:todo]}]]
   [:transform-enable [:todo :tasks] :clear-completed [{msg/type :clear-completed msg/topic [:todo :tasks]}]]])

(defn clear-completed [tasks msg]
  (reduce
   (fn [[task-id task] remaining-tasks]
     (if (:completed task)
       remaining-tasks
       (assoc remaining-tasks task-id task)))
   {}
   tasks))

(defn todo-emitter [inputs]  
  (vec (concat
        ((app/default-emitter) inputs)
        (mapcat (fn [[_ _ task]]
                  (when (symbol? task)
                    [[:transform-enable [:todo :tasks task] :toggle-task [{msg/topic [:todo :tasks] msg/type :toggle-task :id task}]]
                     [:transform-enable [:todo :tasks task] :remove-task [{msg/topic [:todo :tasks] msg/type :remove-task :id task}]]]))
                (:added inputs))
        (mapcat (fn [[_ _ task completed :as path]]
               (when (and (symbol? task) (= completed :completed))                                                
                 [[:value path (get-in inputs (concat [:old-model] path)) (get-in inputs (concat [:new-model] path))]]
                 ))
                (:updated inputs))
        (mapcat (fn [[_ _ task :as path]]
                  (when (symbol? task)                    
                    [[:node-destroy path]]
                    ))
                (:removed inputs)))
       ))

(defn count-tasks [_ inputs]
  (let [filter-type (get-in inputs [:new-model :todo :filter])
        filter-fn (cond (= :any filter-type) (constantly true)
                        (= :completed filter-type) #(:completed %)
                        (= :active filter-type) #(not (:completed %)))]
    ;; Need to call vals on the tasks, because the tasks are returned as
    ;; {taskID {:details details-str :id taskID :completed true/false}}
    ;; By just getting the vals, it's just the task map themselves
    (count (filter filter-fn (vals (get-in inputs [:new-model :todo :tasks]))))))

(defn completed-count [_ inputs]
  (count (filter #(:completed %) (vals (get-in inputs [:new-model :todo :tasks])))))

;; Create individual tasks
;; [:todo :tasks :*]
;; Leave add task at [:todo :tasks]
;; Will make emitting easier

;; Don't try to specify :** in the emitter, it will consider all children
;; to be values.  Won't create new node-create values

(def example-app
  {:version 2
   :transform [{:fn create-todo :out [:todo] :key :create-todo} 
               [:set-filter [:todo :filter] set-todo-filter]
               [:add-task [:todo :tasks] add-task]
               [:remove-task [:todo :tasks] remove-task]
               [:clear-completed [:todo :tasks] clear-completed]
               [:toggle-all [:todo] toggle-all]               
               [:toggle-task [:todo :tasks] toggle-task]
               ]
   ;; visible-tasks
   ;; Ignore the display properties, not needed
  :derive [ [#{[:todo :tasks] [:todo :filter]} [:todo :count] count-tasks]
            [#{[:todo :tasks]} [:todo :completed-count] completed-count]
            ;[#{[:todo :filter] [:todo :tasks]} [:todo :filtered-tasks] filter-tasks]
            ]
   :emit [
          ;{:in #{[:todo :tasks :* :completed]} :fn (app/default-emitter nil) }          
          ;{:in #{[:todo :tasks :* :*]} :fn (app/default-emitter nil) }
          {:in #{[:todo :tasks :* :*]} :fn todo-emitter  :mode :always :init init-emitter }
          {:in #{[:todo :count]} :fn (app/default-emitter nil) :mode :always}
          {:in #{[:todo :filter]} :fn (app/default-emitter nil) :mode :always}
          {:in #{[:todo :completed-count]} :fn (app/default-emitter nil) :mode :always}
          
          ;{:in #{[:todo :tasks]} :fn (app/default-emitter nil) }
          ;{:in #{[:todo]} :fn (app/default-emitter nil)}
          ]})



