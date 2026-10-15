# Notes

## How it works

- `Klass` parses the class file, lays out fields and builds the vtable.
- `Interp` is one switch over the bytecode. Each thread keeps its operand stacks and locals in two arrays, one for primitives and one for references.
- The JDK's native methods, a few hundred of them, are rewritten in Java in `Natives`, `Unsafe`, `Reflect`, `IO`, `Nio`, `Net`, `Threads`, `Loaders` and `Invoke`.
- `invokedynamic` and method handles go through the JDK's own `java.lang.invoke` code. The VM provides what HotSpot provides: member name resolution, `invokeBasic`, `linkTo*` and hidden classes.
- Boot follows the same steps as HotSpot: `initPhase1`, then the module system, then the system class loader. Application classes are loaded by the JDK's own class loader.
- `JIT` translates methods that are called often into host bytecode, which the host JVM then compiles to machine code. Code that runs only a few times stays in the interpreter.
- Guest objects are host objects, so garbage collection is done by the host JVM. Guest threads are host threads.

## Speed

Measured on a 2-core Haswell VPS.

| | this VM | real Java | real Java, `-Xint` |
|---|---|---|---|
| Hello world | 0.5 s | 0.05 s | |
| javac compiling this VM | 19 s | 3.6 s | |
| fib(32), warmed up | 48 ms | 12 ms | 690 ms |

`JVM_JIT=0` turns the JIT off (javac then takes 41 s, fib 1.8 s). `JVM_JIT=1` compiles every method on its first call, the tests also pass that way.
