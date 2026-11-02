# Title

## 1

...
yield
...
visitVariable is here to first find all the variables, and then we can execute the code.
e.g.
```js
console.log(a);
var a = 3;
```
```js
var a;
console.log(a); // undefined
a = 3;
```

## 2

```java
// Loop through every expression
      case Block(List<Expr> exprs, int lineNumber) -> {
        for (var expr: exprs) {
          visit(expr, env);
        }
        yield UNDEFINED;
      }
```

```java
// Just return the value if it is a literal
      case Literal(Object value, int lineNumber) -> {
        yield value;
      }
```

## 4

```java
// Lookup for print function
// Check if it exists
// Get args values
// invoke the function with this undefined
      case Call(Expr qualifier, List<Expr> args, int lineNumber) -> {
        var object = env.lookupOrDefault("print", null);
        if (!(object instanceof JSObject function)) {
          throw new Failure("print not found");
        }
        var values = args.stream()
                .map(arg -> visit(arg, env))
                .toArray();
        yield function.invoke(UNDEFINED, values);
      }
```

## 5

```java
// Remove hard coded "print" and replace it with any qualifier
      case Call(Expr qualifier, List<Expr> args, int lineNumber) -> {
        var object = visit(qualifier, env);
        if (!(object instanceof JSObject function)) {
          throw new Failure("function not found");
        }
        var values = args.stream()
                .map(arg -> visit(arg, env))
                .toArray();
        yield function.invoke(UNDEFINED, values);
      }
      case Identifier(String name, int lineNumber) -> {
        yield env.lookupOrDefault(name, UNDEFINED);
      }
```

## 6

Nothing to add.

## 7

Nothing to add.

## 8

```
// Add a check null on Identifier
// For VarAssignment, get the value
// If not a declaration, check if it exists
// If all's good, register and yield
      case Identifier(String name, int lineNumber) -> {
        var value = env.lookupOrDefault(name, null);
        if (value == null) {
          throw new Failure("identifier not found");
        }
        yield value;
      }
      case VarAssignment(String name, Expr expr, boolean declaration, int lineNumber) -> {
        var value = visit(expr, env);
        if (!declaration && env.lookupOrDefault(name, null) == null) {
          throw new Failure("var not found");
        }
        env.register(name, value);
        yield value;
      }
```

## 9

Nothing to add.

## 10

```
      case Fun(String name, List<String> parameters, boolean toplevel, Block body, int lineNumber) -> {
         JSObject.Invoker invoker = new JSObject.Invoker() {
          @Override
          public Object invoke(Object receiver, Object... args) {
            // check the arguments length
            if (parameters.size() != args.length) {
              throw new Failure("wrong number of args when calling " + name);
            }
            // create a new environment
            var newEnv = JSObject.newEnv(env);
            // add this and all the parameters
            newEnv.register("this", receiver);
            for (var i = 0; i < parameters.size(); i++) {
              newEnv.register(parameters.get(i), args[i]);
            }
            // execute the body
            try {
              execute(body, newEnv);
            } catch (ReturnError e) {
              return e.getValue();
            }
            return UNDEFINED;
          }
         };
        // create the JS function with the invoker
        var function = JSObject.newFunction(name, invoker);
        // register it into the global env if it's a toplevel
        if (toplevel) {
          env.register(name, function);
        }
        // yield the function
        yield function;
      }
      case Return(Expr expr, int lineNumber) -> {
      // To unstack the execution stack, we throw an error
        var value = visit(expr, env);
        throw new ReturnError(value);
      }
```

## 11

```java
    case If(Expr condition, Block trueBlock, Block falseBlock, int lineNumber) -> {
        var value = visit(condition, env);
        if (value instanceof Integer bool) {
            if (bool != 0) {
                execute(trueBlock, env);
            } else {
                execute(falseBlock, env);
            }
        } else {
            throw new Failure("if condition is incorrect at " + lineNumber);
        }
        yield UNDEFINED;
    }
```

Builtin comparison functions return either a 0 or 1.

A new env is not needed for the branches.

## 12

It works.
For the test `callSeveralOperations`, the interpreter find the function f because it is register as an identifier in the env.
For the test `callAndRewrite`, the `op()` function change its implementation, and `f()` calls it.
12! works fine, but 13! does not give the right output, it should give 6,227,020,800 and not 1,932,053,504.
It fails because of the integer limit of 2^32 which is 4,294,967,296.
In fact the int limit + the wrong result we get equals the right result (4,294,967,296 + 1,932,053,504 = 6,227,020,800)

## 13

```java
      case ObjectLiteral(Map<String, Expr> initMap, int lineNumber) -> {
        var jsObject = JSObject.newObject(null);
        for (var entry : initMap.entrySet()) {
          var value = visit(entry.getValue(), env);
          jsObject.register(entry.getKey(), value);
        }
        yield jsObject;
      }
```

## 14

Works fine without rework.

## 15

```java
      case FieldAccess(Expr receiver, String name, int lineNumber) -> {
        var object = visit(receiver, env);
        if (object instanceof JSObject jsObject) {
          var value = jsObject.lookupOrDefault(name, UNDEFINED);
          yield value;
        } else {
          throw new Failure("error while trying to acess field at " + lineNumber);
        }
      }
```

## 16

```java
      case FieldAssignment(Expr receiver, String name, Expr expr, int lineNumber) -> {
        var object = visit(receiver, env);
        if (object instanceof JSObject jsObject) {
          var value = visit(expr, env);
          jsObject.register(name, value);
          yield value;
        } else {
          throw new Failure("error while trying to acess field at " + lineNumber);
        }
      }
```

