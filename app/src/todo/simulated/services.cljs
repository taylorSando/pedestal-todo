(ns todo.simulated.services
  (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [io.pedestal.app.util.platform :as platform]))

(defn receive-messages [app]

  (p/put-message (:input app) {msg/type :create-tasks msg/topic [:tasks]})
  (p/put-message (:input app) {msg/type :add-task msg/topic [:tasks]
                               :value {:details "I am a task"}})
  (p/put-message (:input app) {msg/type :add-task msg/topic [:tasks]
                               :value {:details "I am another task"}})
  (p/put-message (:input app) {msg/type :complete-task msg/topic [:tasks]
                               :value {:details "I am another task"}})  
  (p/put-message (:input app) {msg/type :add-task msg/topic [:tasks]
                               :value {:details "I am a third task"}})
  (platform/create-timeout 3000
                           #(p/put-message (:input app) {msg/type :remove-task msg/topic [:tasks]
                                                        :value {:details "I am a third task"}}))
  #_(platform/create-timeout 5000 #(receive-messages app)))

(defrecord MockServices [app]
  p/Activity
  (start [this]
    (receive-messages app))
  (stop [this]))

(defn services-fn [message input-queue]
  (.log js/console (str "Sending message to server: " message)))
