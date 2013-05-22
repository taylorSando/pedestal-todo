(ns todo.rendering
  (:require [domina :as dom]
            [io.pedestal.app.render.push :as render]
            [io.pedestal.app.render.push.templates :as templates]
            [io.pedestal.app.render.push.handlers.automatic :as d])
  (:require-macros [todo.html-templates :as html-templates]))

;; Load templates.

(def templates (html-templates/todo-templates))

(defn render-page [renderer [_ path] transmitter]
  (let [parent (render/get-parent-id renderer path)        
        id (render/new-id! renderer path)        
        html (templates/add-template renderer path (:todo-page templates))]    
    (dom/append! (dom/by-id parent) (html {:id id}))))


#_(defn render-task [renderer [_ path] transmitter]
  (let [parent (render/get-parent-id renderer path)        
        id (render/new-id! renderer path)        
        html (templates/add-template renderer path (:task templates))]    
    (dom/append! (dom/by-id parent) (html {:id id}))))

(defn render-message [renderer [_ path _ new-value] transmitter]
  (templates/update-t renderer path {:message new-value}))

(defn render-config []
  [[:node-create  [:tasks] render-page]
   ;[:node-create  [:tasks :*] render-task]   
   [:node-destroy   [:tasks] d/default-exit]   
   [:value [:greeting] render-message]])

