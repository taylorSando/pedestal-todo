A todo app written with the pedestal framework.

Make sure that you have the pedestal app installed

You should clone the pedestal app first at https://github.com/pedestal/pedestal

Then run **lein sub install** in the root directory of the repository

Now, to run the todo app, clone this repo

Navigate to the root directory of repo

Create a repl

```
lein repl
(dev)
(run 3000 :todo)
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
