**This is a work in progress**

The beginners guide to Pedestal


Think about the design of a complex system.  Each sub system is actually quite simple.  However, really understanding one component requires how it interacts with its neighbors, which can require understanding other components.  You can at various times group related components and group them together into an abstraction.  However, abstractions are not really real, the tree is not the forest.  The average is not really a person, it's simply an approximation.

This guide assumes that you already know basic clojure and know how to use leiningen.  

There are two sides to pedestal.  There is the application side, and then there is the services/server side.  For now, I will be focusing on the application side.

One of the most important concepts in applications is the idea of messages.  They are what allow the components of the application to be decoupled.  Messages are defined primarily by two parts.  The first part of a message is its topic, and the second part of a message is its type.

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

Thus, we have the data model, the dataflow, and the three queues: app model, input and output.  These components make up the primary application, and communication is provided through **messages**.  **Messages** are passed to the **input queue**, which are handled by the **dataflow**.  The dataflow processes the messages.  It will can update the **data model**, send messages to the **output queue** or the **app model queue**.  The messages on the output queue are handled by a services component of the application.  Generally, this means that these messages will be sent to a server to have some external effect.  Messages sent to the app model queue, on the other hand, are generally going to an area of the application that is concerned with the changes in the data model.   

The messages on the app model queue are special.  The are called **application deltas**, and they are reporting what has changed in the data model.  Application components that subscribe to the app model queue can be informed what has changed in the application data model without having to directly access the data model.  The **app model consumer** is a separate component from the main application.  As it says in the name, it's primary purpose is to consume messages from the application's app model queue.  The default app model consumer that comes with pedestal is created in the **io.pedestal.app.render** namespace, using **io.pedestal.app.render/consume-app-model**.

The app model consumer receives the application object, which contains all the dataflow, data model and queues.  The app model consumer is designed to subscribe to the app model queue.  It also saves a reference to the application's input queue.  That way, it can place messages onto the queue, which can effect the data model.  This means that the app model consumer does not directly change the data model.  Internally, the default app model consumer creates a tree structure.  This tree structure is a projection of the main data model.  It is built up using the application deltas in the app model messages that are coming from the app model queue.  Each application delta is incorporated into this tree in a transaction.  The tree keeps track of both the transactions and the actual values, making this tree a kind of mini database for changes in the data model.  For example, it has the ability to see changes over time.  You can see what the tree values looked like at different times.  This means that this tree is a kind of projection into the data model.

The default app model consumer not only takes an application object, but also a rendering function.  This render function is responsible for processing the app model deltas that are taken from the app model queue.  The render function uses this information to draw something onto the screen.  The default rendering function is the **push renderer**.

The push renderer is located in **io.pedestal.app.render.push** namespace.  Internally, the push renderer creates a special DOM renderer object.  This object is designed to assist in DOM manipulations.  A push renderer is created with 2 components, the first is the identification of the root item in the actual DOM, and the second is a rendering configuration.  The rendering configuration is a series of functions that are designed to handle app model delta messages, and update the DOM.

The push renderer contains a rendering configuration.  This configuration is a series of functions that are concerned with application deltas from the app model queue.  The push renderer receives each individual app delta from the app model consumer, which is subscribed to the app model queue.  The rendering functions receive three arguments.  The first is the internal DOM renderer object that the push renderer built.  The second item is the actual application delta.  The third item is the input queue.  Since the app model consumer has a reference to the input queue, it passes this to the push renderer, which passes it to the rendering function.

What's happening is that the app model consumer is receiving an application delta message from the app model queue.  It then passes this to the push renderer.  The push renderer than examines each of the rendering function in its configuration looking for a match.  If it finds a match, it passes the DOM renderer object, the application delta, and the input queue to the matching rendering function.  The rendering function then must decide what to do with the three objects.

So let's recap.  The main application is made up of an internal data model that holds all the information in the application.  It uses three queues, the input, output and app model queues.  The input queue receives input messages and passes them to the dataflow.  The dataflow processes the messages.  The dataflow can update the data model, and send new messages to either the output, or the app model queues.  The app model queue receives messages that deal with changes in the data model, while the output queue deals with messages that will be sent to an external service, such as the server.  External entities, such as the app model consumer, can subscribe to the app model queue to be informed in what has changed in the data model.  The app model consumer generally has a rendering function, which is used to render the app model deltas onto the screen.

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

The first item in the application delta is the type, which is :node-create.  The second item is the path.  The third item is what is being created.  In this example, an empty data model map is converted into a :node-create statement, at the root path, and that it's creating a map.  The path can be thought of as a path in a tree.  For example, let us say you had a path of [:todo :tasks 'task-4 :id].  This would mean that :todo is a child of the root path, :tasks is a child of :todo, 'task-4 is a child of :todo, and :id is a child of 'task-4.

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
   io.pedestal.app.messages/type :update-details}
   (io.pedestal.app.messages/param :details) {}]
]
```

A :transform-enable generally has four components.  The first item is the application delta type, which is obviously :transform-enable.  The second item is the path, the third item is the transform key, which helps identify the transform.  The fourth item is a vector containing messages.  In this example, we have a vector containing a single message.

The message identifies the topic as [:todo :tasks 'task-4 :details] and the type as :update-details.  It also contains a param function.  This can be used will the fill function from the **io.pedestal.app.messages** namespace.  For example, you can use **io.pedestal.app.messages/fill** with the messages vector in the following way:

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

The dataflow is the actual functions that control an application.  The dataflow is designed to take messages from the input queue.  It then processes these messages, one by one.  As it is processing the message, it might update the data model.  It can also produce messages of its own.  For example, it may also produce application delta messages that will be placed on the app model queue. It may also produce output messages, which are placed onto the output queue, which may be consumed by some external service.  There are five dataflow functions, transforms, derives, continues, emits and effects.

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


So the message is passed to the input queue and into the dataflow.  The only transform component that can handle the message is the one with the key of :new-task and an output task of [:todo].  This is because :new-task matches the message's type, and the [:todo] of the topic matches the transform's output path.  Next, the transform function is called, which receives the old todo data along with the message.  Inside the function, we are associated the old todo with the tasks.  Since tasks is a vector, we are conj'ing the new task onto the old tasks.  We automatically create an id, but use the extract the details from the message and create a map.  This is placed onto the tasks.  At the end, the data model is now updated.
















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
 
