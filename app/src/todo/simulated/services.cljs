(ns todo.simulated.services
  (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [io.pedestal.app.util.platform :as platform]))

(defn receive-messages [app]
  (p/put-message (:input app) {msg/type :create-todo msg/topic [:todo]}))

(defrecord MockServices [app]
  p/Activity
  (start [this]
    (receive-messages app))
  (stop [this]))

(defn services-fn [message input-queue]
  (.log js/console (str "Sending message to server: " message)))
