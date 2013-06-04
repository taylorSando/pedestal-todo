
(ns ^:shared todo.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app :as app]
              [io.pedestal.app.dataflow :as dataflow]
              [io.pedestal.app.messages :as msg]))

(defn create-todo [todo msg]
  ;; Create the initial todo map
  ;; tasks is all the tasks
  ;; filter is the type of filter on the tasks
  ;; filtered-tasks are the tasks that are currently being used
  ;; count refers to the number of filtered tasks
  ;; visible-count is the number of tasks in filtered tasks
  ;; completed-count is the number of completed tasks
  (assoc todo :tasks {} :count 0, :visible-count 0, :completed-count 0, :filter :any, :filtered-tasks {}))

(defn compute-filtered-tasks [_ inputs]  
  (let [filter-type (get-in inputs [:new-model :todo :filter])
        tasks (get-in inputs [:new-model :todo :tasks])]
    (if (= filter-type :any)
      tasks
      (into {} (filter (fn [[task-id task :as tm]]
                         (cond
                          (and (= filter-type :completed) (:completed task)) tm
                          (and (= filter-type :active) (not (:completed task))) tm))
                       tasks)))))

;; This sets the filter on the tasks
(defn set-todo-filter [v msg]  
  (:filter msg))

;; Add a new to the todo list
(defn add-task [todo msg]  
  (let [id (or (:id msg) (gensym "task"))
        task {:details (:details msg) :completed false :id id}]
    ;; Insert the new task into the tasks map, which needs to be
    ;; inserted back into the main todo map
    (assoc todo :tasks
           (assoc (:tasks todo) id task))))

(defn remove-task [todo msg]  
  ;; Remove the task from tasks, then place the new tasks into the todo
  (assoc todo :tasks (dissoc (:tasks todo) (:id msg) )))

(defn toggle-task [todo msg]
  ;; Toggle the completed value in the task in tasks, then put tasks back into the todo map
  (assoc todo :tasks
         (update-in (:tasks todo) [(:id msg) :completed] not)))

(defn toggle-all [todo msg]
  ;; This goes through each of the filtered-tasks and flips their completed value
  ;; It merges this with the main tasks, and then updates the todo map
  (assoc todo :tasks
         (merge (:tasks todo)
                (into {} (map (fn [[task-id task]]
                                {task-id (update-in task [:completed] not)})
                              (:filtered-tasks todo))))))

(defn clear-completed [todo msg]
  ;; Remove all the completed tasks and then add the removed map back into todo map
  (assoc todo :tasks
         (into {} (remove (fn [[task-id task]] (:completed task)) (:tasks todo)))))

(defn init-emitter [inputs]
  ;; Set up some initial transform-enables for the DOM to interact with
  [[:transform-enable [:todo :filter] :set-filter [{msg/type :set-filter msg/topic [:todo :filter]
                                                    (msg/param :filter) {}}]]
   [:transform-enable [:todo] :add-tasks [{msg/type :add-task msg/topic [:todo]
                                           (msg/param :details) {}}]]
   [:transform-enable [:todo] :toggle-all [{msg/type :toggle-all msg/topic [:todo]}]]
   [:transform-enable [:todo] :clear-completed [{msg/type :clear-completed msg/topic [:todo]}]]])

(defn todo-emitter [inputs]
  (vec (concat
        ((app/default-emitter) inputs)
        (mapcat (fn [[_ _ task]]
                  ;; When a new task is added, it needs to have transform-enables associated with it
                  ;; It needs to be able to be toggled, and it should be removed.
                  (when (symbol? task)
                    [[:transform-enable [:todo :filtered-tasks task] :toggle-task [{msg/topic [:todo] msg/type :toggle-task :id task}]]
                     [:transform-enable [:todo :filtered-tasks task] :remove-task [{msg/topic [:todo] msg/type :remove-task :id task}]]]))
                (:added inputs))
        (mapcat (fn [[_ _ task completed :as path]]
                  ;; Right now the default emitter is not updating the value if it is nil or false
                  ;; So I'm manually inserting the value 
                  (when (and (symbol? task) (= completed :completed))                 
                    [[:value path (get-in inputs (concat [:new-model] path))]]
                    ))
                (:updated inputs))
        (mapcat (fn [[_ _ task :as path]]
                  ;; Make sure that the tasks are removed when they are removed from the data model
                  (when (symbol? task)                    
                    [[:node-destroy path]]
                    ))
                (:removed inputs)))
       ))

(defn visible-count [_ inputs]
  ;; Count the filtered tasks, which are what is visible
  (count (get-in inputs [:new-model :todo :filtered-tasks])))

(defn count-tasks [_ inputs]
  ;; Count all the active tasks
  (count (remove (fn [[k t]] (:completed t)) (get-in inputs [:new-model :todo :tasks]))))

(defn completed-count [_ inputs]
  ;; Count all the completed tasks
  (count (filter (fn [[task-id task]] (:completed task)) (get-in inputs [:new-model :todo :tasks]))))

(def example-app
  {:version 2
   :transform [[:create-todo [:todo] create-todo]
               [:set-filter [:todo :filter] set-todo-filter]
               [:add-task [:todo] add-task]
               [:remove-task [:todo] remove-task]
               [:clear-completed [:todo] clear-completed]
               [:toggle-all [:todo] toggle-all]               
               [:toggle-task [:todo] toggle-task]]   
  :derive [ [#{[:todo :filtered-tasks]} [:todo :count] count-tasks]
            [#{[:todo :tasks]} [:todo :completed-count] completed-count]
            [#{[:todo :filtered-tasks]} [:todo :visible-count] visible-count]
            [#{[:todo :filter] [:todo :tasks]} [:todo :filtered-tasks] compute-filtered-tasks]]
   :emit [{:in #{[:todo :filtered-tasks :* :*]} :fn todo-emitter  :mode :always :init init-emitter }
          {:in #{[:todo :completed-count] [:todo :count] [:todo :filter] [:todo :visible-count]}
           :fn (app/default-emitter nil) :mode :always}]})


