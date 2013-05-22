(ns todo.simulated.start
  (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app.render.push.handlers.automatic :as d]
            [todo.start :as start]
            [todo.simulated.services :as services]
            [io.pedestal.app-tools.tooling :as tooling]
            [todo.rendering :as rendering]))

(defn ^:export main []
  (let [uri (goog.Uri. (.toString (.-location js/document)))
        renderer (.getParameterValue uri "renderer")
        render-config (if (= renderer "auto")
                        d/data-renderer-config
                        (rendering/render-config))
        app (start/create-app render-config)
        services (services/->MockServices (:app app))]
    (p/start services)
    app))





