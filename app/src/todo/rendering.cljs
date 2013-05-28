(ns todo.rendering
  (:require [domina :as dom]
            [domina.css :as dc]
            [domina.events :as de]
            [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msgs]
            [io.pedestal.app.render.push :as render]
            [io.pedestal.app.render.push.templates :as templates]            
            [io.pedestal.app.render.events :as evts]
            [io.pedestal.app.render.push.handlers :as h]
            [io.pedestal.app.render.push.handlers.automatic :as d])
  (:require-macros [todo.html-templates :as html-templates]))

;; This loads all the html templates
(def templates (html-templates/todo-templates))

;; Set up the initial todo page
(defn render-todo [renderer [_ path] transmitter]  
  (let [parent (render/get-parent-id renderer path)        
        id (render/new-id! renderer path "todoapp")        
        html (templates/add-template renderer path (:todo-page templates))]    
    (dom/append! (dom/by-id parent) (html))))

;; When a task is removed, it needs to be removed from the DOM
(defn destroy-task [renderer [_ path] transmitter]  
  (dom/destroy! (dom/by-id (render/get-id renderer path))))

;; This represents all the tasks that are currently on the page
;; It needs an id.  This is used to set up the path hierarchy
(defn render-tasks [renderer [_ path] transmitter]
  (render/new-id! renderer path "todo-list"))

;; The filter value was just changed, so the DOM needs to be updated
;; The css works by having a 'selected' class in it, so this needs to
;; be set
(defn render-task-filter [_ [_ _ _ filter] _]
  (dom/remove-class! (dc/sel "#filters a") "selected")
  (cond
   (= filter :any)
   (dom/add-class! (dc/sel "#all-filter") "selected")   
   (= filter :completed)
   (dom/add-class! (dc/sel "#completed-filter") "selected") 
   (= filter :active)
   (dom/add-class! (dc/sel "#active-filter") "selected")))
  

;; A new task was just added
(defn render-task [renderer [_ path] transmitter]
  ;; Extract the parent's render id
  (let [parent (render/get-parent-id renderer path)
        ;; Create an id for the current task
        id (render/new-id! renderer path)
        ;; Add the template to the current task path
        html (templates/add-template renderer path (:task templates))]
    ;; Append the new task to the dom
    (dom/append! (dom/by-id parent) (html {:id id}))))

;; The path to this is [:todo :tasks task-id :details]
;; The parent template needs to be updated so that it can update the html
(defn render-task-details [renderer [_ path _ new-details] transmitter]
  (templates/update-parent-t renderer path {:details new-details}))

;; The value at [:todo :tasks task-id :completed] has been updated
;; Need to make sure the completed value is reflected in the
;; checkbox and in the css style of the dom element holding the task
(defn render-task-completed [renderer [_ path _ completed] transmitter]  
  ;; Need to get the parent's id, because that is where the class needs to be placed
  (let [task-container (dom/by-id (str (render/get-parent-id renderer path)))
        ;; This is the checkbox for the task
        task-checkbox (dc/sel (str "#" (render/get-parent-id renderer path) " input"))
        ;; The completed class is either going to be added, or removed, depending
        ;; on the checkbox value
        class-fn (if completed dom/add-class! dom/remove-class!)]
    ;; Can't simply use dom/set-attr! to checked, because it won't be reflected in the
    ;; dom.  Have to set the actual checked value of the checkbox
    (set! (.-checked (dom/single-node task-checkbox)) completed)
    ;; Update the class type on the container
    (class-fn task-container "completed")))

;; Update the number of completed tasks
(defn render-completed-count-value [r [_ _ _ v] input-queue]  
  (dom/set-text! (dc/sel (str "#clear-completed span")) (str v)))

;; The toggle all button should only be visible if there are visible tasks
(defn render-visible-count-value [renderer [_ path _ new-value] transmitter]
  (if (> new-value 0)
    (dom/set-style! (dom/by-id "toggle-all") :display "block")
    (dom/set-style! (dom/by-id "toggle-all") :display "none")))

;; Update the number of active tasks
(defn render-task-count-value [renderer [_ path _ new-value] transmitter]
  (dom/set-text! (dc/sel (str "#todo-count strong")) (str new-value)))

;; Using a multi method here.  It dispatches on the key of the transform-enable
(defmulti handle-todo-transforms (fn [r [t p k messages] input-queue]
                                    k))
;; Handle toggle-all
(defmethod handle-todo-transforms :toggle-all [r [t p k messages] input-queue]
  (evts/send-on :click (dom/by-id "toggle-all") input-queue messages ))

;; Handle add-tasks
(defmethod handle-todo-transforms :add-tasks [r [_ _ _ messages] input-queue]
  (let [todo-input (dom/by-id "new-todo")]
    (de/listen! todo-input :keydown
                (fn [e]                  
                  (when (= (.-keyCode (.-evt e)) 13)
                    (let [details (dom/value todo-input)
                          new-msgs (msgs/fill :add-task messages {:details details})]
                      (dom/set-value! todo-input "")
                      (doseq [m new-msgs]
                        (p/put-message input-queue m))))))))

;; Handle clear completed
(defmethod handle-todo-transforms :clear-completed [r [t p k messages] input-queue]
  (do
    ;; Remove any
     #_(de/listen! (dom/by-id "clear-completed") :click
                 (fn [_]
                   (dom/destroy! (dc/sel "#todo-list .completed"))))          
     (evts/send-on :click (dom/by-id "clear-completed") input-queue messages )))

;; Handle DOM events for when the filter buttons are clicked
;; This will update the filter value
(def filter-transforms
  (fn [r [_ p k messages] input-queue]                                        
    (evts/send-on :click (dom/by-id "all-filter")
                  input-queue
                  (msgs/fill :set-filter messages {:filter :any}) )
    (evts/send-on :click (dom/by-id "active-filter")
                  input-queue
                  (msgs/fill :set-filter messages {:filter :active}) )
    (evts/send-on :click (dom/by-id "completed-filter")
                  input-queue
                  (msgs/fill :set-filter messages {:filter :completed}))))

;; Handle the toggle-task and remove-task messages when the checkbox, or the
;; remove button is clicked
(def filtered-tasks-transforms 
  (fn [r [_ p k messages] input-queue]                                          
      (cond
       (= k :toggle-task)                                           
       (evts/send-on :click (dc/sel (str "#" (render/get-id r p) " input")) input-queue messages )
       (= k :remove-task)                                           
       (evts/send-on :click (dc/sel (str "#" (render/get-id r p) " .destroy")) input-queue messages ))))

(defn render-config []  
  [[:node-create  [:todo] render-todo]
   [:transform-enable [:todo] handle-todo-transforms]
   [:node-destroy   [:todo] d/default-exit]
   [:node-create  [:todo :filtered-tasks] render-tasks]
   [:node-create  [:todo :filtered-tasks :*] render-task]
   [:node-destroy [:todo :filtered-tasks :*] destroy-task]
   [:transform-enable [:todo :filtered-tasks :*] filtered-tasks-transforms]      
   [:value  [:todo :filtered-tasks :* :details] render-task-details]
   [:value  [:todo :filtered-tasks :* :completed] render-task-completed]
   [:value [:todo :filter] render-task-filter]   
   [:transform-enable [:todo :filter] filter-transforms]   
   [:value [:todo :count] render-task-count-value]
   [:value [:todo :visible-count] render-visible-count-value]
   [:value [:todo :completed-count] render-completed-count-value]])

