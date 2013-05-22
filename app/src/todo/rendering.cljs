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

;; Load templates.

(def templates (html-templates/todo-templates))

(defn render-page [renderer [_ path] transmitter]
  (let [parent (render/get-parent-id renderer path)        
        id (render/new-id! renderer path)        
        html (templates/add-template renderer path (:todo-page templates))]    
    (dom/append! (dom/by-id parent) (html {:id id}))))


(defn render-task [renderer [_ path] transmitter]
  (let [parent (render/get-parent-id renderer path)        
        id (render/new-id! renderer path)        
        html (templates/add-template renderer path (:task templates))]    
    (dom/append! (dom/by-id parent) (html {:id id}))))

(defn render-message [renderer [_ path _ new-value] transmitter]
  (templates/update-t renderer path {:details (:details new-value) :completed (str (:completed new-value))}))

;; need to import domina.events, and dom.css if you want to use the sel syntax
;; send-on can use domina object which is dom/DomContent

;; Using add-send-on-click
;; Don't have to manually set up the send-on with the input-queue and msgs
;; We know it's a click, just need to specify the domina element
;; Passing a string means it's automatically going to look up the ID

;; Make sure when creating lists of vectors, they are actually lists or dictionaries.
;; and not three separate vectors

;; Early bug had me doing [:node-create [:tasks] :map]
;; This was creating everything, but technically, the first node create was not being sent

;; Need to add key down handlers


(defn render-config []  
  [[:node-create  [:tasks] render-page]
   [:node-create  [:tasks :*] render-task]
   [:transform-enable [:tasks] (fn [r [_ p k messages] input-queue]
                                 (let [todo-input (dom/by-id "new-todo")]
                                  (de/listen! todo-input :keydown
                                              (fn [e]
                                                (when (= (.-keyCode (.-evt e)) 13)
                                                  (let [details (dom/value todo-input)
                                                        new-msgs (msgs/fill :add-task messages {:details details})]
                                                    (dom/set-value! todo-input "")
                                                    (doseq [m new-msgs]
                                                      (p/put-message input-queue m)))
                                                  ))))                                    
                                    )]
   [:value [:tasks :*] render-message]
   [:transform-enable [:tasks :*] (fn [r [_ p k msgs] input-queue]
                                    (evts/send-on :click (dc/sel (str "#" (render/get-id r p) " input")) input-queue msgs )
                                    )]
   [:node-destroy   [:tasks] d/default-exit]])


