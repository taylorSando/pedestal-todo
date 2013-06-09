### To Do in Pedestal
A todo app written with the pedestal framework.

### Prerequisites

The app depends upon the app-tools and app-template components from the pedestal project. These are not avalaible in clojars and thus need to be pulled in from the pedestal project.

- Clone the pedestal repo -

``` bash
 git clone git://github.com/pedestal/pedestal.git

```
- Change into the cloned repo
- run ``` lein sub install ``` to install the components.

### Project Installation

To run, clone the repo

```
cd pedestal-todo/client

Create a repl

```
lein repl
(dev)
(run)
```

Navigate to localhost:3000/todo-data-ui.html

It should work

If you want to change the port number if starts at try the following:
Use: (run port-number :todo) where port-number is the port number, i.e. (run 8000 :todo)

```
lein repl
(dev)
(run 8000 :todo)
```
