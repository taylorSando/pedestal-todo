(ns todo.simulated.services
  (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [io.pedestal.app.util.platform :as platform]))

(defn receive-messages [app]

  (p/put-message (:input app) {msg/type :create-todo msg/topic [:todo]})  
  (p/put-message (:input app) {msg/type :add-task msg/topic [:todo :tasks]
                               :details "I am a task" :id 'task1})
  (p/put-message (:input app) {msg/type :add-task msg/topic [:todo :tasks]
                               :details "I am a task13" :id 'task2})
  (p/put-message (:input app) {msg/type :add-task msg/topic [:todo :tasks]
                               :details "I am a task2" :id 'task3})
  (p/put-message (:input app) {msg/type :add-task msg/topic [:todo :tasks]
                               :details "I am a task0" :id 'task0})
  (platform/create-timeout 2000
                           #(p/put-message (:input app) {msg/type :remove-task msg/topic [:todo :tasks]
                                                        :id 'task1}))
  (p/put-message (:input app) {msg/type :add-task msg/topic [:todo :tasks]
                               :details "I am a task2" :id 'task9})

  (platform/create-timeout 3000
                           #(p/put-message (:input app) {msg/type :add-task msg/topic [:todo :tasks]
                                                         :details "I am a task99" :id 'task4}))
  (platform/create-timeout 3500
                           #(p/put-message (:input app) {msg/type :toggle-task msg/topic [:todo :tasks]
                                                         :id 'task4}))

  (platform/create-timeout 4000
                           #(p/put-message (:input app) {msg/type :toggle-task msg/topic [:todo :tasks]
                                                         :id 'task2}))
  (platform/create-timeout 5000
                           #(p/put-message (:input app) {msg/type :set-todo-filter msg/topic [:todo :filter]
                                                         :filter :completed}))
  (platform/create-timeout 8000
                           #(p/put-message (:input app) {msg/type :set-todo-filter msg/topic [:todo :filter]
                                                         :filter :active}))
  (platform/create-timeout 11000
                           #(p/put-message (:input app) {msg/type :set-todo-filter msg/topic [:todo :filter]
                                                         :filter :any}))
  (platform/create-timeout 13000
                           #(p/put-message (:input app) {msg/type :remove-completed msg/topic [:todo :tasks]}))
  (platform/create-timeout 14000
                           #(p/put-message (:input app) {msg/type :toggle-tasks msg/topic [:todo :tasks]}))
  (platform/create-timeout 15000
                           #(p/put-message (:input app) {msg/type :toggle-tasks msg/topic [:todo :tasks]}))
  (platform/create-timeout 17000
                           #(p/put-message (:input app) {msg/type :edit-task msg/topic [:todo :tasks]
                                                         :id 'task9 :details "New Details for task 9"}))
  #_(platform/create-timeout 5000 #(receive-messages app)))

(defrecord MockServices [app]
  p/Activity
  (start [this]
    (receive-messages app))
  (stop [this]))

(defn services-fn [message input-queue]
  (.log js/console (str "Sending message to server: " message)))
