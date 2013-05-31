(defproject todo "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1586"]
                 [domina "1.0.1"]
                 [ch.qos.logback/logback-classic "1.0.7" :exclusions [org.slf4j/slf4j-api]]
                 [io.pedestal/pedestal.app "0.1.9-SNAPSHOT"]
                 [io.pedestal/pedestal.app-tools "0.1.9-SNAPSHOT"]]
  :profiles {:dev {:source-paths ["dev"]}}
  :min-lein-version "2.0.0"
  :source-paths ["app/src" "app/templates"]
  :resource-paths ["config"]
  :target-path "out/"
  :aliases {"dumbrepl" ["trampoline" "run" "-m" "clojure.main/main"]})


;; Old Model
{}

;; New Model
{:todo {:tasks {'task-1 {:details "Do something special" :id 'task-1}}}}

;; Application Deltas
[[:node-create [] :map]
 [:node-create [:todo] :map]
 [:node-create [:todo :tasks] :map]
 [:node-create [:todo :tasks 'task-1] :map]
 [:node-create [:todo :tasks 'task-1 :details] :map]
 [:node-create [:todo :tasks 'task-1 :id] :map]
 [:value [:todo :tasks 'task-1 :details] nil {:details "Do something special"}]
 [:value [:todo :tasks 'task-1 :id] nil {:id 'task-1}]]

;; New Model
{:todo {:tasks {'task-1 {:details "Do something special" :id 'task-1}
                'task-2 {:details "Do something less special" :id 'task-2}}}}

;; Application Delta
[[:node-create [:todo :tasks 'task-2] :map]
 [:node-create [:todo :tasks 'task-2 :details] :map]
 [:node-create [:todo :tasks 'task-2 :id] :map]
 [:value [:todo :tasks 'task-2 :details] nil {:details "Do something less special"}]
 [:value [:todo :tasks 'task-2 :id] nil {:id 'task-2}]]



