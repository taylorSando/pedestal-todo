**This is a work in progress**

The beginners guide to Pedestal


Think about the design of a complex system.  Each sub system is actually quite simple.  However, really understanding one component requires how it interacts with its neighbors, which can require understanding other components.  You can at various times group related components and group them together into an abstraction.  However, abstractions are not really real, the tree is not the forest.  The average is not really a person, it's simply an approximation.

This guide assumes that you already know basic clojure and know how to use leiningen.  

There are two sides to pedestal.  There is the application side, and then there is the services/server side.  For now, I will be focusing on the application side.

One of the most important components in the pedestal application is messages.  They are what allow the separate components of the application to be decoupled.  Messages are defined primarily by two parts.  The first part of a message is its topic, and the second part of a message is its type.

```clojure
{io.pedestal.app.messages/topic [:todo] io.pedestal.app.messages/type :create-todo}
```

In this example, the topic is **[:todo]**, and the type is **:create-todo**.

Messages can have additional keys and values, for example:

```clojure
{io.pedestal.app.messages/topic [:todo] io.pedestal.app.messages/type :add-task :details "Pick up cat" :completed false}
```

In this example, there is both  **:details**, and  **:completed** in the message.  These parameters can be used by those components that handle messages.

Now that we know that messages are used to decouple the components of an application, we should talk about what those components are.  The first component is the **data model**.  The data model stores all the information that an application needs.  This is an internal part of an application, it is not exposed to the public.  Instead, the outside world interacts with the data model by means of the input queue, which is another component of the application.  The input queue takes messages, one by one, and allows the application to process them.  The part of the application that processes the messages on the input queue is the **dataflow**.  The dataflow is a series of functions that process messages, alter the data model, and send their output in the form of messages, to two application components.  These components are the **app model queue** and the **output queue**.

Thus, we have the data model, the dataflow, and the three queues: app model, input and output.  These components make up the primary application, and communication is provided through **messages**.  **Messages** are passed to the **input queue**, which are handled by the **dataflow**.  The dataflow processes the messages.  It can update the **data model**, send messages to the **output queue** or the **app model queue**.  The messages on the output queue are handled by a services component of the application.  Generally, this means that these messages will be sent to a server to have some external effect.  Messages sent to the app model queue, on the other hand, are generally going to an area of the application that is concerned with the changes in the data model.   

The messages on the app model queue are special.  The are called **application deltas**, and they are reporting what has changed in the data model.  Application components that subscribe to the app model queue can be informed what has changed in the application data model without having to directly access the data model.  The **app model consumer** is a separate component from the main application.  As it says in the name, it's primary purpose is to consume messages from the application's app model queue.  The default app model consumer that comes with pedestal is created in the **io.pedestal.app.render** namespace, using **io.pedestal.app.render/consume-app-model**.

The app model consumer receives the application object, which contains all the dataflow, data model and queues.  The app model consumer is designed to subscribe to the app model queue.  It also saves a reference to the application's input queue.  That way, it can place messages onto the queue, which can effect the data model.  This means that the app model consumer does not directly change the data model.  Internally, the default app model consumer creates a tree structure.  This tree structure is a projection of the main data model.  It is built up using the application deltas in the app model messages that are coming from the app model queue.  Each application delta is incorporated into this tree in a transaction.  The tree keeps track of both the transactions and the actual values, making this tree a kind of mini database for changes in the data model.  For example, it has the ability to see changes over time.  You can see what the tree values looked like at different times.  This means that this tree is a kind of projection into the data model.

The default app model consumer not only takes an application object, but also a rendering function.  This render function is responsible for processing the app model deltas that are taken from the app model queue.  The render function uses this information to draw something onto the screen.  The default rendering function is the **push renderer**.

The push renderer is located in **io.pedestal.app.render.push** namespace.  Internally, the push renderer creates a special DOM renderer object.  This object is designed to assist in DOM manipulations.  A push renderer is created with 2 components, the first is the identification of the root item in the actual DOM, and the second is a rendering configuration.  The rendering configuration is a series of functions that are designed to handle app model delta messages, and update the DOM.

The push renderer contains a rendering configuration.  This configuration is a series of functions that are concerned with application deltas from the app model queue.  The push renderer receives each individual app delta from the app model consumer, which is subscribed to the app model queue.  The rendering functions receive three arguments.  The first is the internal DOM renderer object that the push renderer built.  The second item is the actual application delta.  The third item is the input queue.  Since the app model consumer has a reference to the input queue, it passes this to the push renderer, which passes it to the rendering function.

What's happening is that the app model consumer is receiving an application delta message from the app model queue.  It then passes this to the push renderer.  The push renderer then examines each of the rendering function in its configuration looking for a match.  If it finds a match, it passes the DOM renderer object, the application delta, and the input queue to the matching rendering function.  The rendering function then must decide what to do with the three objects.

So let's recap.  The main application is made up of an internal data model that holds all the information in the application.  It uses three queues, the input, output and app model queues.  The input queue receives input messages and passes them to the dataflow.  The dataflow processes the messages.  The dataflow can update the data model, and send new messages to either the output, or the app model queues.  The app model queue receives messages that deal with changes in the data model, while the output queue deals with messages that will be sent to an external service, such as the server.  External entities, such as the app model consumer, can subscribe to the app model queue to be informed about what has changed in the data model.  The app model consumer generally has a rendering function, which is used to render the app model deltas onto the screen.

Two options:

Learn more about the application deltas and how they will be rendered

Learn more about the dataflow application portion and how they generate the application deltas and their output messages.

##Application Deltas

There are six different application delta types: :node-create, :node-destroy, :value, :attr, :transform-enable and :transform-destroy.  Let us start with the first one, :node-create.

###:node-create

When the data model creates a new map or vector value, a :node-create application delta can be created.  For example, let us say you create an empty data model, {}.  This can be translated into a :node-create application delta:

```clojure
[:node-create [] :map]
```

The first item in the application delta is the type, which is :node-create.  The second item is the path.  The third item is what is being created.  In this example, an empty data model map is converted into a :node-create statement, at the root path, and that it's creating a map.  The path can be thought of as a path in a tree.  For example, let us say you had a path of [:todo :tasks 'task-4 :id].  This would mean that :todo is a child of the root path, :tasks is a child of :todo, 'task-4 is a child of :tasks, and :id is a child of 'task-4.

The sequence of application deltas to create this could be:

```clojure
;; Data Model

{:todo
 {:tasks
  {'task-4
   {:id 'task-4}}}}


;; Application Deltas

[:node-create [] :map]
[:node-create [:todo] :map]
[:node-create [:todo :tasks] :map]
[:node-create [:todo :tasks 'task-4] :map]
[:node-create [:todo :tasks 'task-4 :id] :map]
```

###:node-destroy

When the data model removes an item, a :node-destroy application delta can be created.  For example:

```clojure
;; Original data model
{:todo
 {:tasks
  {'task-4
   {:id 'task-4}}}}

;; New data model
{:todo
 {:tasks}}

;; Application Deltas

[:node-destroy [:todo :tasks 'task-4 :id]]
[:node-destroy [:todo :tasks 'task-4]]
```

In this example, there are two :node-destroy that occur.  This is because two values were actually removed, both the :id, and 'task-4 were removed as children from [:todo :tasks].

###:value

When the value of a data model changes, a :value application delta is created.

```clojure
;; Original Data Model
{:todo
 {:tasks
  {}}

;; New Data Model
{:todo
 {:tasks
  {'task-4
   {:id 'task-4}}}}

;; Application Deltas
[:node-create [:todo :tasks 'task-4] :map]
[:node-create [:todo :tasks 'task-4 :id] :map]
[:value [:todo :tasks 'task-4 :id] nil 'task-4]
```

In this example, two :node-create got created, and one :value application delta happened.  The 3rd item in the :value delta specifies the old value, which is nil, meaning it had no old value.  The 4th item represents the value of the id, which is 'task-4.  This exact sequence of :node-create and :value need not to be how the application deltas *must* be represented, rather this is one possible interpretation.  For example:

```clojure
;; Original Data Model
{:todo
 {:tasks
  {}}

;; New Data Model
{:todo
 {:tasks
  {'task-4
   {:id 'task-4}}}}

;; Application Deltas
[:value [:todo :tasks] nil {'task-4 {:id 'task-4}}]

;; Another possible sequence
[:node-create [:todo :tasks 'task-4] :map]
[:value [:todo :tasks 'task-4] nil {:id 'task-4}]
```

Here, we have two different ways of representing the same thing.  A single :value application delta could be created.  This means that the value at the path of [:todo :tasks] is simply a map.  We don't bother creating the children nodes, we simply assign the map as a value.  The other possibility is to create the node at [:todo :tasks 'task-4], and assigning a value of {:id 'task-4} to it.  How to control what gets emitted by the application deltas will be covered later.  For now the take away is simply to get a rough idea of how to interpret a :value application delta.

One final point about the :value application delta.  There is a 3 argument version, where the 3rd argument specifies the new value, and there is no fourth value.

```clojure
;; Mean the same thing
[:value [:todo :tasks 'task-4] nil {:id 'task-4}]
[:value [:todo :tasks 'task-4] {:id 'task-4}]
```
I would suggest always using the four argument version to reduce confusion.


###:attr

Related to a node's value is its attributes.  Attributes provide a more fine grained control over a node's content.

```clojure
;; Data Model
{:todo
 {:tasks
  {'task-4
   {:id 'task-4
    :completed true
	:details "Pick up the dog"
	:created-at "1369634836"}}}}

;; One possible sequence of Application Deltas
[:node-create [:todo :tasks 'task-4] :map]
[:value [:todo :tasks 'task-4] nil "Pick up the dog"]
[:attr [:todo :tasks 'task-4] :id nil 'task-4]
[:attr [:todo :tasks 'task-4] :completed nil true]
[:attr [:todo :tasks 'task-4] :created-at nil "1369634836"]
```

Here we are creating a node at [:todo :tasks 'task-4].  We assign the value of **Pick up the dog**, which is the data model's :details value.  There are three attributes, corresponding to the :id, :completed and :created-at fields.  When to use values and attrs, and choosing one over the other will be talked about later.  For now, realize that an :attr application delta is made up of 5 values.  The first is the :attr type, the second is the path, the third is the attribute name, the four item is the old attribute value, and the fifth item is the new attribute value.


### :transform-enable

The most common use case for consuming application deltas is a view of some kind.  A view is something that a user should be able to interact with.  Therefore, there needs to be a way to communicate back to the application data model.  The way to facilitate this interaction is through the use of application deltas called :transforms.  An application delta of :transform-enable says that interaction with the data model is possible.  What a transform does is specify a series of messages that will be sent to the application's input queue.

```clojure
;; Data Model
{:todo
 {:tasks
  {'task-4
   {:id 'task-4
    :completed true
	:details "Pick up the dog"
	:created-at "1369634836"}}}}

;; Application Delta
[:transform-enable
 [:todo :tasks 'task-4 :details]
 :change-task
 [{io.pedestal.app.messages/topic [:todo :tasks 'task-4 :details]
   io.pedestal.app.messages/type :update-details
   (io.pedestal.app.messages/param :details) {}}]
]
```

A :transform-enable generally has four components.  The first item is the application delta type, which is obviously :transform-enable.  The second item is the path, the third item is the transform key, which helps identify the transform.  The fourth item is a vector containing messages.  In this example, we have a vector containing a single message.

The message identifies the topic as [:todo :tasks 'task-4 :details] and the type as :update-details.  It also contains a param function.  This can be used with the fill function from the **io.pedestal.app.messages** namespace.  For example, you can use **io.pedestal.app.messages/fill** with the messages vector in the following way:

```clojure
(io.pedestal.app.messages/fill :update-details
                               [{io.pedestal.app.messages/topic [:todo :tasks 'task-4 :details]
                                 io.pedestal.app.messages/type :update-details
                                (io.pedestal.app.messages/param :details) {}}]
                               {:details "Pick up the cat"})
;; becomes

[{io.pedestal.app.messages/topic [:todo :tasks 'task-4 :details]
   io.pedestal.app.messages/type :update-details
   :details "Pick up the cat"}]
```

What is happening here is that io.pedestal.app.messages/fill takes three parameters.  The first parameter is the type value.  This corresponds to the type in the message if it exists.  The second parameter is the actual vector of messages.  The last item is the map which should be use to fill the param values in the message.  The fill function can do this for multiple messages.  The messages must specify a type message that matches the fill's first parameter type value.  

```clojure

(io.pedestal.app.messages/fill :update-details
                               [{io.pedestal.app.messages/topic [:todo :tasks 'task-4 :details]
                                 io.pedestal.app.messages/type :update-details
                                (io.pedestal.app.messages/param :details) {}}
                                {io.pedestal.app.messages/topic [:todo :tasks 'task-5 :details]
                                 (io.pedestal.app.messages/param :details) {}}]
                               {:details "Pick up the cat"})

;; becomes
[{io.pedestal.app.messages/topic [:todo :tasks 'task-4 :details]
  io.pedestal.app.messages/type :update-details
  :details "Pick up the cat"}
 {io.pedestal.app.messages/topic [:todo :tasks 'task-5 :details]
  io.pedestal.app.messages/type :update-details
  :details "Pick up the cat"}]
```

In this example, there are two messages in the vector.  The second message does not specify a type parameter, but the fill message fills it in anyways.

An application delta that produces a :transform-enable does not mean that a message has been, or will be sent.  It simply sets up the possibility of sending a message back to the input queue.  It is up to the function consuming the app model and the :transform-enable application delta to stick the messages back into the application's input queue.  In these examples, the messages will update the details on the todo tasks.  This might happen if the user was interacting with a web form, and they typed in a text box and saved the results.  How this gets wired up will come later.  For now, just understand that a :transform-enable sets up messages that can be sent back to the application through the input queue.

###:transform-disable

This is the opposite of :transform-enable.  It is designed to stop any messages from being sent to the application input queue.  For example, if the user is no longer on the todo form, it doesn't make sense to continue to have the ability for the view function to send messages back to the application input queue that have to do with the todo tasks.

```clojure
[:transform-disable [:todo :tasks 'task-id :details] :change-task]
[:transform-disable [:todo :tasks 'task-id :details]]
```

A :transform-disable is made up of three items.  The first is the type, the second is the path, and the third item is the transform key.  The third parameter is optional. In the above example, the first :transform-disable will specifically remove all messages related to :change-task at the path, while the second one will remove all transforms at the path.

```clojure
[[:transform-enable
 [:todo :tasks 'task-4 :details]
 :change-task
 [{io.pedestal.app.messages/topic [:todo :tasks 'task-4 :details]
   io.pedestal.app.messages/type :update-details}
   (io.pedestal.app.messages/param :details) {}]]
[:transform-enable
 [:todo]
 :add-task
 [{io.pedestal.app.messages/topic [:todo]
   io.pedestal.app.messages/type :new-task}
   (io.pedestal.app.messages/param :details) {}
   (io.pedestal.app.messages/param :completed) {}]]
[:transform-enable
 [:todo]
 :remove-task
 [{io.pedestal.app.messages/topic [:todo]
   io.pedestal.app.messages/type :remove-task}
   (io.pedestal.app.messages/param :id) {}]]]

;; There are three transforms active

;; This would remove the details transform
[:transform-disable [:todo :tasks 'task-4 :details]]

;; This would remove the :remove-task transform, but leave the :add-task
[:transform-disable [:todo] :remove-task]

;; This would remove both
[:transform-disable [:todo]]
```


## Dataflow

The dataflow are the actual functions that control an application.  The dataflow is designed to take messages from the input queue.  It then processes these messages, one by one.  As it is processing the message, it might update the data model.  It can also produce messages of its own.  For example, it may also produce application delta messages that will be placed on the app model queue. It may also produce output messages, which are placed onto the output queue, which may be consumed by some external service.  There are five dataflow functions, transforms, derives, continues, emits and effects.

## Transform

The most basic dataflow component is the transform.  Do not confuse a dataflow transform with a :transform-enable from the application deltas, they are separate things.  A transform is used to update the data model.  A transform is made up of three components.  The first component is the transform's key.  The key helps identify the transform for a message.  It corresponds to the **io.pedestal.app.messages/type** component of a message.  The second component of a transform is the output path.  This is the location in the data model that holds the transform's value.  For example, if the output path was [:todo :tasks], and the value there was **{'task-4 {:id 'task-4 :details "Pick up trash"}}**, then we would say that the transform's value was equal to that task map.  The third component of a transform is the function that is used to process messages.

So let's put this all together.  A message comes into the dataflow, and the first thing that processes the message is the transform dataflow components.  In order for a transform to handle a message, it must first match a message.  For a message to match a transform function, two things must match.  The message's type must match the transform's key, and the message's topic must match the transform's output path.

```clojure
{io.pedestal.app.messages/topic [:todo :tasks 'task-4 :details]
:io.pedestal.app.messages/type :update-details
:details "Write a tutorial"}

;; Would match
[:update-details [:todo :tasks 'task-4 :details] 'transform-fn]

;; Would not match
[:update-details [:todo :tasks] 'transform-fn]
[:update-id [:todo :tasks 'task-4 :details] 'transform-fn]
```

When a message is found to match a transform dataflow, the transform function is called.  This function gets two values.  The first value is the old value that was held at the transform's output path.  The second value is the message.

```clojure

;; Original data model
{:todo {:tasks [{:id "id1" :details "Pick up milk"}]}}

;; Message
{io.pedestal.app.messages/topic [:todo]
 :io.pedestal.app.messages/type :new-task
 :details "Write a tutorial"}

;; transform function
(defn new-task-handler [old-todo msg]
  (assoc old-todo :tasks
         (conj (:tasks old-todo)
               {:details (:details msg) :id (gensym "id")})))

;; Transform dataflow
[:new-task [:todo] new-task-handler]


;; New data model
{:todo {:tasks [{:id "id1" :details "Pick up milk"}
                {:id "id2" :details "Write a tutorial"}]}}
```


So the message is passed to the input queue, and into the dataflow.  The only transform component that can handle the message is the one with the key of :new-task and an output task of [:todo].  This is because :new-task matches the message's type, and the [:todo] of the topic matches the transform's output path.  Next, the transform function is called, which receives the old todo data along with the message.  Inside the function, we are associated the old todo with the tasks.  Since tasks is a vector, we are conj'ing the new task onto the old tasks.  We automatically create an id, and extract the details from the message and create a map.  This is placed in tasks.  At the end, the data model is now updated.

There are actually two ways of defining a transform dataflow, with a vector, or with a map.

```clojure
; Vector way
[:new-task [:todo] new-task-handler]
; Map way
{:fn new-task-handler :key :new-task :out [:todo]}
```

The vector must be in the following order, **[key out fn]**.

## Derive

The next dataflow component is the **derive**.  The derive component does not directly handle messages.  The derive component is concerned with changes in the data model.  For example, let us say you had an application that handled temperatures and did weather forecasts.  The *Wind Chill* is calculated by two variables, the wind speed and the outside temperature.  Therefore, if you know the wind speed, and you know the outside temperature, you can *derive* the wind chill.  This is the idea behind the derive dataflow.  It's designed to be used for values that can be derived, or calculated, based on existing values within the data model. A derive dataflow is triggered when its inputs change.  For example, if either the temperature, or the wind speed changes, then the wind chill changes.

A derive dataflow is made up of three components.  The first is the **input paths**.  The input paths are the paths to the data model.  For example, this might be **#{[:app :sensor :temperature] [:app :sensor :wind-speed]}**.  The second component is the **output path**.  This is where the output of the derive value goes to.  It is a path in the data model, which is updated.  For example, this could be **[:app :values :wind-chill]**.  The third component is the actual **derive function**.  This is what is called when either of the inputs have changed.  The value that is produced by the derive function will be stored in the data model, at the location specified by the output path.

There are two ways to create a derive dataflow, either with a vector, or with a map.

```clojure
;; Vector form
[#{[:app :sensor :temperature] [:app :sensor :wind-speed]} [:app :values :wind-chill] wind-chill-fn]
;; Map form
{:fn wind-chill-fn :in #{[:app :sensor :temperature] [:app :sensor :wind-speed]} :out [:app :values :wind-chill] }
```

The vector form must be in the order of input, output and function.

The derive function receives two arguments when it is called.  The first item is the old data model value at the output path.  The second item is a **tracking map**.  A tracking map is a special pedestal map that keeps track of changes in the data model.  A tracking map is made up of the following keys: **:removed, :added, :updated, :input-paths, :old-model, :new-model, and :message**.

####:added

This is a list of paths that have been added to the data model.  

####:updated

This is a list of paths that have been updated in the data model.

####:removed

This is a list of paths that have been removed in the data model.

####:input-paths

This is a list of the input paths that are defined by the derive function.  For example, it would contain [:app :sensor :temperature] and [:app :sensor :wind-speed].

####:old-model
This is the data model before the message was sent to the dataflow.  It contains all the values prior to any transform or derive dataflow function was called.

####:new-model
This is the current state of the data model.  It contains values that have been changed by the transforms or any derives.

###:message
This is the message that is currently active in the dataflow.

In the case of the wind chill function, we are interested in the new values for temperature and wind speed.  Therefore, we need to grab those values from the :new-model.  Below is an example of how to put this all together.

```clojure
(defn temperature-fn [old-temp msg]
  (:temperature msg))

(defn wind-speed-fn [old-speed msg]
  (:wind-speed msg))

(defn wind-chill-fn [old-wind-chill inputs]
  (let [t (get-in inputs [:new-model :app :sensor :temperature])
        v (get-in inputs [:new-model :app :sensor :wind-speed])]
    ;; simple wind chill formula is 0.0817 * (3.71v^0.5 + 5.81 – 0.25 V) * (T – 91.4) + 91.4
    (+ (* 0.0817 (- (+ (* (Math/sqrt v) 3.71) 5.81) (* 0.25 v)) (- t 91.4)) 91.4)))

(def dataflow
  {:transform [[:set-temperature [:app :sensor :temperature] temperature-fn]
               [:set-wind-speed [:app :sensor :wind-speed] wind-speed-fn]]
   :derive [[#{[:app :sensor :temperature] [:app :sensor :wind-speed]} [:app :values :wind-chill] wind-chill-fn]]})
```

Transform functions are called first.  After they are finished, the derive functions are given a chance to run.  The Derive dataflow components only run if their inputs change.  It's possible for the output of one derive function to be the input of another derive function.  This means that you can have calculations that are dependent on other calculations.  For example, if there was a variable that depended on the wind chill, it would be called when the wind chill changed, and the wind chill would change if the temperature and/or the wind speed changed.

### Continue

The next dataflow component is the continue.  The continue is a slightly advanced feature.  You might want to skip this section and return to it later.

Continues are harder to understand, because they don't directly process messages, and they don't directly effect the data model.  Continues are used for generating messages that are used within the dataflow.  Messages are always processed one at a time.  This means that a single message from the input queue is completely processed by the dataflow before the next one can be processed.  The exception to this is if a continue function generates a message.  These messages are processed within the same dataflow transaction as the original message.  We will refer to this message as m0.

m0 is first processed by the transform functions.  It may alter the data model.  If it does, the corresponding derive functions listening to that part of the data model are called.  When those functions have been ran, the continue components have a chance to run.  The continue dataflow functions will only run if their inputs were changed.  The continue dataflow is like the derive dataflow, in that both have inputs.  The difference is that the continue function creates messages, whereas the derive function outputs a value to the data model.

To the outside world, the data model exists in one state before a message, and in another state after the message is processed.  The outsie world is not aware of any intermediate values that the transforms or derive components caused in the data model.

With that in mind, let us imagine that you are a simple data analysis program.  Let us say that you need to analyze a series of data points.  Let us say that there are two possible intepretations of the data points.  There are two algorithms for analzying points.  Each algorithm analyzes the points in a different way and produces a certainity value that the points match the algorithm's parameters.  Each algorithm only gets a fixed amount of time to analyze their data before giving their certainity level.  In the application, there is a minimum certainity level that must be reached.

The following are data:

Minimum certainity threshold
Maximum analysis time
Algorithm A certainity level 
Algorithm B certanity level
The data points
Final Decision

There are four candidates for being transforms, minimum certainity threshold, maximum analysis time, data points and final decision.  These can be set by messages to the application.  The candidates for derives are the algorithm certainity levels, because they dependent on the three transform values.  We need to be certain that one of the algorithms reaches the required certainity threshold.  One option would be to allow each algorithm derive to extract the certainity level for the other algorithms in the tracking map parameter.  This is a problem, because now the algorithm is doing two things.  It's calculating its own certainity value, and it's also comparing itself to its other certainity level.  Let us say that we wanted to add additional algorithms, this would mean that each algorithm would need to be rewritten each time a new algorithm was added.  That's not a good situation to be in.

An additional problem happens when you consider the fact that both algorithms may not reach the minimum certainity threshold.  In that case, one of three things could happen, you could increase the number of data points, you could increase the maximum analysis time, or you could decrease the certainity threshold.  Since transforms are only concerned with their own values, they can't do this.  A derive can only output to a single data model location, so it can't do it.  This is where the continue function comes in.  While the continue function does not directly change data models, it can produce messages within the same transaction, which can effect the data model.

So let's take this from the top.  Let us assume that the minimum certainity level and the maximum calculation time already exist in the data model.  A new message, m0 comes into the dataflow.  It specifies a new series of data points.  The transform handling the data points writes the new data points.  Both derive algorithms detect the new data points and they run until they reach the maximum calculation time and produce their results.  Finally, the continue function detects new algorithm certainity values.  Let us say that one is 0.65, and the other is 0.5.  The minimum certainity level is 0.7, so there's a problem, we haven't reached the minimum certainity level.  Let us assume that we don't want to decrease the certanity level, and we can't increase the number of data points.  The alternative is to increase the maximum calculation time.  Therefore, the continue function creates a new message, m1.

Assuming that m0 has been processed completely, m1 enters the dataflow.  First, the transforms get a chance to handle the message.  The transform responsible for the maximum calculation time is then changed.  The algorithm derive functions detect changes to their inputs, and they recalculate their certainity values using the new calculation time.  Let us say that the new certainities are 0.71 and 0.69.  The continue function would realize that 0.71 is greater than the other value, and also greater than the minimum, therefore it produces a new message, m2.  m2 is designed to set the final decision.  m2 then enters the dataflow.  The transform that handles the final decision data model updates its values.  No derive functions, and no continue functions are ran.  Therefore, we have a final value, and it's the algorithm A interpretation of the data points.

You could ask why couldn't the continue functions simply change the data model themselves, instead of simply sending a message.  The answer is that it's a more decoupled design.  The continue function can send a message back to the transforms and derives, which can then handle that message, just like in a normal dataflow.  If a continue function could directly alter the data model, the continue function might be putting the data model into an invalid state.  For example, it could be altering a value that a derive function depends on.  The derive functino would not get an option to run again.  By strickly separating the dataflow into three distinct processes, you minimize complexity.

First, the transform functions direclty alter the data model by using the message.  Next, the derive functions get a chance to run, assumign their inputs change.  The derive functions also update the data model.  This can lead to other derive functions being called, because it's possible for a derive function to depend on another one.  However, at some point, all the derive functions will stop running.  Then, it becomes time for the continue functions to run.  Continue functions, assuming their inputs change, have the opportunity to create new messages that are put on an internal queue.  Each of these messages will be processed, one by one, just like the messages from the normal app input queue.  Once all the messages have been processed, the data model will be left in its new, consistent state.  To the outside world, it will seem as though the single message push the data model into its new state on its own.  It has no idea how many continue messages may have been produced in the interim.

Continue dataflow components have two elements.  The first is the list of inputs paths in the data model.  Whenever the value at one of these paths changes, the continue dataflow function is ran.  Which brings us to the second element of the continue dataflow, which is the continue function.  The continue function takes a single argument, a tracking map.  The continue function should then examine its inputs and do one of two things.  It should either produce a message, or messages, or it should return nothing.  By returning nothing, it signals that it has nothing left to do.  If the continue functions were to always return a message, the dataflow transaction would never complete.  So make sure that the continue function will eventually stop sending messages.

There are two ways to create the continue dataflow.  As always, there is a vector form, and there is a map form.

```clojure
[ #{[:algorithms :a :certainity-value] [:algorithms :b :certainity-value]} continue-algorithm]
{:in #{[:algorithms :a :certainity-value] [:algorithms :b :certainity-value]} :fn continue-algorithm}
```
To summarize, continue functions are designed to act as a kind of validation on the data model.  It can help make sure the transforms and derives are leaving the data model in a valid state.  It does this by sending internal messages that are not visible to the outside world.

### Emit

Previously, I had talked about application deltas.  You were probably wondered where those came from.  They come from emitters, which are the next dataflow component.  Emitters emit application delta messages that are eventually placed onto the the app model queue, where some consumer can consume those application deltas.  To briefly summarize the application delta section, there are six kinds of application deltas, **:node-create, :node-destroy, :attr, :value, :transform-enable, and :transform-disable.  Each of these is designed to signal a change in the data model The exceptions are the transforms.  Transforms define messages which allow an application consumer to use those to place transform messages onto the input queue, where the messages can then go through the dataflow and update the data model.

Emitters are made up of two components.  The first component is a list of input paths into the data model.  The second component is the emitter function.  When one of the values corresponding to the data model input paths change, the emitter function is called.  The emitter function is designed to take a single argument, which is a tracking map.  By using the tracking map, you can determine what has changed, and can produce the application deltas necessary to signal this change.  For example, if a path was added to the data model, this could be signalled with a :node-create.  If one was removed, it could be :node-destroy.  When a value is changed in the data model, an application delta could be either :attr, or :value, depending on how fine grained the change you want to signal.

Emitters are not triggered until after the transform, derive and continue functions have already ran.  Thus, the dataflow can be split into two parts, the first part is concerned with changing the data model, and is made up of the transform, derive and continue functions.  The next parts, the emit and output dataflows, are concerned with producing messages and sending them to the app model and output queue respectively.  We can dive into understanding how to make up an emitter function.  Understanding the default emitter is a good place to start.

The default emitter is located at **io.pedestal.app/default-emitter**.  The default emitter can take a single parameter, which is the path prefix.  If you recall, application deltas contain a path element.  For example [:node-create [:todo :tasks 'task-id]] says to create a new node at the path in the vector.  By using a prefix argument, you can automatically add the prefix to all paths.  For example, if you have an emitter that is always concerned with a certain part of the data model, you can express the prefix. In this case, we could say the prefix is [:todo].  Therefore, every application delta will automatically be prefixed by :todo.  If an application delta was going to be [:tasks 'task-id], it would now be [:todo :tasks 'task-id].

Here is how you could define an emit component that uses the default emitter:

```clojure
;; Vector form
[#{[:todo]} (io.pedestal.app/default-emitter)]

;; Map form
{:in #{[:todo]} :fn (io.pedestal.app/default-emitter)}
```

I should note, the default-emitter is actually returning a function, which takes a single argument, the tracking map.  The default emitter function is designed to look at the tracking map, and use the input paths to determine what has changed.  For example, in this case, we have specified [:todo] as the input path.  This means that within the data model, if the path to :todo, or any of its descendants changes, default-emitter will be called.  For example, changes to [:todo], [:todo :tasks], or [:todo :tasks 'task-4] would all trigger the emitter function to be called.

```clojure
;; Old Data model
{}

;; New Data model
{:todo {:tasks {'task-1 {:details "Get new mouse" :id 'task-1}}}}
```

Let us assume that there was a new message to the dataflow, and it caused the data model to look like the second map.  There are quite a few different things that has changed, and many ways of interpretating what has happened.  In the case of the default emitter, however, there is only one interpretation.  Since the emitter component we defined earlier had a single input at [:todo], that is how all application deltas will be created relative to.  For example, this would be the output:

```clojure
[:node-create [] :map]
[:node-create [:todo] :map]
[:value [:todo] nil {:tasks {'task-1 {:details "Get new mouse" :id 'task-1}}}]
```

You might be asking, why does it create the application deltas this way, and not say like this?

```clojure
[:node-create [] :map]
[:node-create [:todo] :map]
[:node-create [:todo :tasks] :map]
[:value [:todo :tasks] nil {'task-1 {:details "Get new mouse" :id 'task-1}}]
```

Both are valid ways of describing the changes to the data model.  The answer for why the first one was chosen over the second lies in how we defined the input path to the emitter.  The output is always relative to the paths.  Since we defined a single path at [:todo], everything is happening relative to it.  When the default emitter function detected that :todo was created, it needed to create this node.  However, it also realized that it had to create its parent, which is the root element.  Therefore, it created both **[:node-create [] :map]** and **[:node-create [:todo] :map]**.  Having created all the nodes specified by the input path, it now needs to set the value of the node.  It would look into the data model and realize that it had to create the internal :tasks map and all its children.  This is exactly what it did with **[:value [:todo] nil {:tasks {'task-1 {:details "Get new mouse" :id 'task-1}}}]**.  The old value stored there was nil, and now it is the tasks map.

If, on the other hand, we defined the emitter as follows:

```clojure
[#{[:todo :tasks]} (io.pedestal.app/default-emitter)]
```

The output would match the second series of application deltas.

Another important point is that while the emitters detect when the data values at the input paths, or any of their descendants change, it does not signal when an ancestor of sibling changes.  For example, the emitter that we just defined would not detect this change:

```clojure
;; Old Model
{:todo
 {:tasks {'task-1 {:details "Get new mouse" :id 'task-1}}}}

;; New Model
{:todo
 {:tasks {'task-1 {:details "Get new mouse" :id 'task-1}}
  :count 1}}
```

What changed was that **[:todo :count]** was added.  However, **[:todo :count]** is a sibling to **[:todo :tasks]**, not a descendant.  This would mean that the emitter would not emit any application deltas.  If the emitter was watching **[:app :todo :tasks]**, and there was a change to **[:app :todo]**, or **[:app :other]**, then the emitter would not emit anything, because those are both ancestors.

When the default emitter detects an update in the tracking map at one of its input paths, it will create :value application deltas to reflect the change.  For example, assume that we have a default emitter watching [:todo :tasks].

```clojure
;; Old Model
{:todo
 {:tasks {'task-1 {:details "Get new mouse" :id 'task-1}}}}

;; New Model
{:todo
 {:tasks {'task-1 {:details "Get new mouse" :id 'task-1}
          'task-2 {:details "Get new keyboard" :id 'task-2}}}}

;; Application Delta
[:value
 [:todo :tasks]
 {'task-1 {:details "Get new mouse" :id 'task-1}}
 {'task-1 {:details "Get new mouse" :id 'task-1}
  'task-2 {:details "Get new keyboard" :id 'task-2}}]
```

You can see the old task map is replaced by the new one containing the new task.

The final case handled by the default emitter is when a data model object is removed.  For example,

```clojure
;; Old Model
{:todo
 {:tasks {'task-1 {:details "Get new mouse" :id 'task-1}
          'task-2 {:details "Get new keyboard" :id 'task-2}}}}
;; New Model
{:todo
 {}}

;; Application Delta
[:node-destroy [:todo :tasks]]
```

You might be wondering what would happen if the task's map was removed:

```clojure
;; Old Model
{:todo
 {:tasks {'task-1 {:details "Get new mouse" :id 'task-1}
          'task-2 {:details "Get new keyboard" :id 'task-2}}}}

;; New Model
{:todo
 {:tasks {}}}

;; Application Deltas
[:value
 [:todo :tasks]
 {'task-1 {:details "Get new mouse" :id 'task-1}
  'task-2 {:details "Get new keyboard" :id 'task-2}}
 {}]
```

As you can see, it produces a :value application delta, not a :node-destroy.  This is because the map at [:tasks :todo] changed from having two items, to being empty.  This is considered as the value changing.

#### Understanding Paths and Emitters

When defining the input paths to the emitters you do not necessarily need to specify the exact path sequence.  For example, let us say that you wanted to emit values at something more specific than just [:todo :tasks].  For example, we want to have values for **[:todo :tasks 'task-1]** and **[:todo :tasks 'task-2]**, not just for [:todo :tasks], which gives the whole tasks map.  The naive solution would be to specify **[:todo :tasks 'task-1]** and **[:todo :tasks 'task-2]** in the paths of the emitter.  The problem with this is obvious.  How are you going to deal with new tasks that are added?  The solution is to use wild card paths.

Wild card paths are designed to match any value.  

##### :*
This defines a single wildcard value.  It is designed to match any element at the given value.  For example, if you had a path of **[:todo :tasks :*]**, it would match both **[:todo :tasks 'task-1]** and **[:todo :tasks 'task-2]**, allowing you to specify just one emitter.

```clojure
[#{[:todo :tasks :*]} (io.pedestal.app/default-emitter)]

;; Old Model
{}

;; New Model
{:todo {:tasks {'task-1 {:details "Do something special" :id 'task-1}}}}

;; Application Deltas
[[:node-create [] :map]
 [:node-create [:todo] :map]
 [:node-create [:todo :tasks] :map]
 [:node-create [:todo :tasks 'task-1] :map]
 [:value [:todo :tasks 'task-1] nil {:details "Do something special" :id 'task-1}]]

;; New Model
{:todo {:tasks {'task-1 {:details "Do something special" :id 'task-1}
                'task-2 {:details "Do something less special" :id 'task-2}}}}

;; Application Delta
[[:node-create [:todo :tasks 'task-2] :map]
 [:value [:todo :tasks 'task-2] nil {:details "Do something less special" :id 'task-2}]]
```

As you can see, the values produced are more fine grained compared to an emitter whose input path was  **[:todo :tasks]**.

You can specify more than one wild card path, for example:

```clojure

[#{[:todo :tasks :* :*]} (io.pedestal.app/default-emitter)]

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
```

You can specify as many wild card characters as you require, and you can mix and match them, for example, **[:app :todo :* :details]**, **[:* :* :something :* :*]** and **[:* :a :b :* :*]** would all be valid paths.

### Effect

The final dataflow component is **effect**.  Effect is used to generate output messages  These messages will be placed onto the output queue.  Once on the output queue, the messages can be consumed by a service consuming the output queue.  Effects are made up of two components, a list of inputs, and an effect function.  As with the other dataflow components, an effect can be defined using two methods, a vector and a map form.

```clojure
[#{[:path :to :input]} 'effect-fn]
{:in #{[:path :to :input]} :fn 'effect-fn}
```

The effect function takes a single argument, which is a **tracking map**.  The function must return a vector of messages.  These messages will eventually be placed onto the output queue.  It will be up to the service consuming the output queue to determine what to do with the messages.  More than likely these messages will be sent to the server.

### Rendering Application Deltas

The default app model queue consumer, defined by **io.pedestal.app.render/consume-app-model**.  One of its parameters is a function that renders application deltas.  The app model consumer forwards each delta to the rendering function.

#### Automatic Renderer
One of the simplest render functions to use is the one defined in **io.pedestal.app.push.handlers.automatic**, *data-renderer-config*.  This displays the tree data structure that is generated by the application deltas.  It will display the paths to the values, and show those values.  It does this automatically, so that you do not have to write any rendering functions on your own.

#### Push Renderer
The next renderer that you will find yourself using is the **push renderer**, which is located at **io.pedestal.app.render.push/renderer**.  This also takes each application delta and attempts to render it.  However, unlike the automatic renderer, you must specify your own rendering functions when you configure the push renderer.

The push renderer creates an internal DOM structure that is useful for creating templates and mapping from the application tree to the actual browser's DOM.  It does this by creating an internal **DomRenderer**, which is defined by the following methods ** get-id, get-parent-id, new-id!, delete-id!, on-destroy!, set-data!, drop-data! and get-data**.  The idea is that these functions take a path from an application delta and map it to an object in the DOM.  For example [:todo :tasks] might have its own data and id.  You could then use this to help you map application delta data onto the DOM.

When creating the push renderer, it requires 2 arguments, but can take 3 arguments.  The first argument is an id that will represent the root path, for example if [:todo :tasks] is the path.  Its parent is [:todo], and its parent is [], which is the root.  This item requires an id, which you specify.  The second argument is a list of application delta handlers, which I'll talk about below.  The last argument is an optional logging function, which can help print out the application deltas to a log, or to the console.

#### Defining Application Delta Handlers

The push renderer requires functions, called handlers, which are designed to match application deltas that it receives.  It is up to these handlers to process the application deltas into DOM actions.  For example, [:node-create [:todo]] could be passed to a handler that maps the path [:todo] to a DOM id.  It would then create a DOM element, which could be used to render other todo elements.

The push renderer is set up to be configured with its handlers.  The configuration is defined by a vector of vectors.  Each vector is defined by three elements.  The first element is the application delta type.  The second element is a path.  The third element is a function that will handle the application delta.  For example:

```clojure
[[:node-create [:todo] render-todo]
 [:node-create  [:todo :tasks] render-tasks]
 [:node-create  [:todo :tasks :*] render-task]
 [:node-destroy [:todo :tasks :*] destroy-task]
 [:transform-enable [:todo :tasks :*] task-transforms]
 [:node-destroy [:todo] render-destroy]]
```

Application deltas that match [:node-create [:todo]] will be handled by render-todo.  Application deltas that match [:transform-enable [:todo :tasks :*]] will be handled by task-transforms.  You can specify a wild card in the paths.  This means that it will match any item, for example [:todo :tasks 'id-5] or [:todo :tasks :id-5] would both match.

The actual functions that are being called by the push render when it receives an application delta actually receive 3 arguments.  The first is the **DOM Renderer object**.  The second is the actual application delta.  The third argument is the input queue.  Using these three items, a handler should be able to take the actions necessary to project the application deltas onto the browser DOM.

For example:

```clojure
(defn render-todo [renderer [type path] input-queue]
  (let [parent (io.pedestal.app.render.push/get-parent-id renderer path)
        id (io.pedestal.app.render.push/new-id! renderer path "todoapp")
    (domina/append! (domina/by-id parent) "<div>I am todo content</div>)))
```

Here we have render-todo.  It takes in the 3 arguments.  Generally, the second argument for the application delta is destructured, in this case, it's destructed into a type and a path.  The actual body of the function is setting up the renderer object to store new ids to help map the paths to the DOM.

The first item, **io.pedestal.app.render.push/get-parent-id**, retrieves the id stored at the path in the renderer.  If you remember from above, the push renderer requires a root id.  This is what will be retrieved by get-parent-id, because [:todo] is the child, and [] is the parent, which is the root.  This item identifies some area in the DOM.

The second item, **io.pedestal.app.render.push/new-id!** will create a new id in the renderer using the path.  In this case, it's specifying a third parameter "todoapp".  This means that the id will be set to this item.  If there was no third argument, new-id! would have created a random id automatically.  The path [:todo] will now be mapped to the DOM id "todoapp."

The last item is using **domina**.  It's appending the div content to the area specified by the parent's id in the DOM.  





##### Templates

##### Mapping Transform-Enable

##### Sending messages back to the input queue





Transforms allow init messages

Emits allow init deltas, 

# This will be the more in depth guide to the todo app




The todo app has become the hello world of the MVC world, therefore, I decided to make one using the pedestal framework.  Pedestal takes awhile to really get your ahead around.   However, the steeper learning curve compared to other MVC frameworks around is almost certainly due to the infancy of the project, and the lack of good documentation and tutorials.  I learned what I did primarily by going through the source code.  As of writing this, I know there are efforts to improve the documentation, and there are new tutorials in the pipeline.  I should be releasing a series of my own in depth overview of pedestal.  I will post links to where they can be found when I get around to writing them.

Everything starts in **app/src/todo/services/start.cljs** this is where the magic happens for this demo app.  There are actually two start files, there is also one in **app/src/todo/start.cljs**.  You'll have to ignore that one for now.

```clojure
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
```

This is extracting the url value to determine the kind of rendering engine that is used.  If you were to enter **http://localhost:3000/todo-data-ui.html?renderer=auto** you would get the auto renderer.  This is very useful for debugging purposes.  I'll explain what that does in some later section.  For now, use the regular renderer. Do this by going to **http://localhost:3000/todo-data-ui.html** You should see the todo application, and should be able to interact with it.

Back to the code and how this happens.  renderer is extracting the query parameter.  If it's auto, it grabs the automatic renderer.  If it's anything else, it grabs the one that I created, which is located at **app/src/todo/rendering.cljs**.  This is where the mapping between application deltas and the DOM happen.  When the application's data model changes, it sends out what are called Application Deltas.  There are six of them: **:node-create, :node-destroy, :transform-enable, :transform-disable, :value, and :attr**.  So what is an Application Delta?  It's a report of a change in the data model in a way that maps into a tree data structure.  For example, let us say that you had a data model like the following:

```clojure
{:todo
 {:tasks
  {'task1 {:task-id 'task1
           :details "Pick up milk"}}
  {'task2 {:task-id 'task2
                  :details "Pick up dogs"}}}}
```

Implementing this in a tree structure is quite easy.  Obviouslly, the root is :todo, and it has a single child, which is :tasks.  :tasks itself has two children, 'task1 and 'task2.  These both have their own children, :task-id and :details.  Paths are described by a vector.   For example, here is the path to task1's details: **[:todo :tasks 'task1 :details]**

In order for the rendering engine to be able to display the data model, it does so through a series of changes.  For example, more than likely, you are starting with an empty data model.  **{}**.  Then, you would create the todo map, **{:todo {}}**.  Next, you could create the :tasks, **{{:todo {:tasks {}}}}**.  Don't worry about how this creation process happens, I'll explain how it works later.  What's important to note here is that as the data model is changing, there is the opportunity to report the application deltas.  In this case, the data model can emit change data in the form of these application deltas.  For example, in this case, the following could be produced:

```clojure
[:node-create [] :map]
[:node-create [:todo] :map]
[:node-create [:todo :tasks] :map]
```

In a tree structure, the root is a map.  Then :todo is the child of the root, which is also a map.  The :todo then has a child, which is a map, which is :tasks.

```clojure
[:node-create [:todo :tasks `task1] :map]
[:node-create [:todo :tasks 'task1 :task-id] :map]
[:node-create [:todo :tasks 'task1 :details] :map]
```

Here 'task1 is created.  In the data model, the value of task-id is not a map, but in the application deltas, and the tree that it is building, it will be.  Each of these nodes can have three attributes that you can specify: **value, attr and transform**.  The value is a single value for the node.  Attrs are a map of values.  The transform is harder to understand now, so we will skip it for now.

Here is what the values are:

```clojure
[:value [:todo :tasks 'task1 'task-id] 'task1]
[:value [:todo :tasks 'task1 :details] "Pick Up Milk"]
```

To quickly recap.  The first item in an application delta is the delta type.  The second item is the path, and the third item is the new value.  Application deltas always have the form [type path].  They can also have additional parameters, which is the case with :value.

You may also see application value deltas written like this:

```clojure
[:value [:todo :tasks 'task1 'task-id] nil 'task1]
[:value [:todo :tasks 'task1 :details] nil "Pick Up Milk"]
```

You're probably wondering what the nil value is.  That represents the old value, it's the third item in the vector.  The fourth item is the new item.  The :value application delta can be represented in either way.  I believe that it is best to represent your deltas using the four parameter syntax.

This is what the rendering config looks like:

```clojure
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
```

The format for a rendering config is that it should be a vector of vectors.  Each vector identifies the delta type, path and a function.  Application deltas that match the type and path are called using the function.  For example, **[node-create [:todo] render-todo]**, this is designed to handle application deltas that have a :node-create, which are at the [:todo] path, and it uses the render-todo to do this.  The rendering function takes three arguments.

```clojure
(defn render-todo [renderer [_ path] transmitter]  
  (let [parent (render/get-parent-id renderer path)        
        id (render/new-id! renderer path "todoapp")        
        html (templates/add-template renderer path (:todo-page templates))]    
    (dom/append! (dom/by-id parent) (html))))
```

Just focus on the arguments for now, don't worry about what's happening inside.  You should see 3 arguments.  The first argument is the renderer.  This represents a DOM-like object that is used to help manipulate the DOM.  I'll have more to see about this later.  The second argument is a destructing pattern.  This represents the application delta.  In this case, since :node-create specifies a vector of two, we have a destructing form of **[_ path]**.  The full pattern would actually be more like **[type path]**, where type is going to equal :node-create and path is going to equal [:todo].  The third parameter, transmitter, is the input queue.  This can be used to send messages back to the application.  I'll have more to say about the input queue, and how it works later.

This was just a basic introduction to the rendering functions, the application deltas, and the rendering config.  You should have a very basic idea of how they work.  I will move on for now, but we will be revisiting all this ideas later.

The app is created using the function **(start/create-app render-config)**

create-app is a function defined by me.

The first item in create-app is the following: **(app/build behavior/example-app)**

This is used to actually build the application.  **behavior/example-app** represents the dataflow configuration, and is where we will turn our attention to now.

The dataflow configuration is located in **app/src/todo/behavior.clj**
   
```clojure
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
```

There is quite a bit going on here, so we will break it down step by step.  The first place we will start is the :transform section.  A transform is the most basic dataflow function.  It is used to create, update and remove items in the the data model.  A transform is made up of three items.  Let us look at the create-todo transform as an example.

```clojure
[:create-todo [:todo] create-todo]
````

The first is the transform's key, which is :create-todo.  The second item is the path to the data model, in this case, it's [:todo].  The third item is the transform function, which is create-todo.

```clojure
(defn create-todo [todo msg]
  ;; Create the initial todo map
  ;; tasks is all the tasks
  ;; filter is the type of filter on the tasks
  ;; filtered-tasks are the tasks that are currently being used
  ;; count refers to the number of filtered tasks
  ;; visible-count is the number of tasks in filtered tasks
  ;; completed-count is the number of completed tasks
  (assoc todo :tasks {} :count 0, :visible-count 0, :completed-count 0, :filter :any, :filtered-tasks {}))
```

Transform functions always take 2 parameters.  The first parameter is the old data model.  In this case, it will be nil, but when the data model exists already, it will contain the old value.  The second parameter is the message.  I haven't talked about messages yet.  Basically a message is what is sent to the input queue of the app.  The transforms are designed to handle these messages.  A message is generally created as follows:

```clojure
{io.pedestal.app.messages/topic [:todo] io.pedestal.app.messages/type :create-todo}
```

Generally the type of a message corresponds to the transform key, and the topic represents the transform's path.  When a message is processed, this means that the key and the topic are matched to their corresponding transform functions.  It's possible that a message won't match any transform though.

So let us assume that the :create-todo message was sent to the application and that the transform function was ran.  The data model will now look like the following:

```clojure
{:todo {:tasks {}
        :filter :any}}
```

Will have to change the other attribute values, they aren't needed, since the derive functions will actually create them.
 
                
