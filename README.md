# jvm

A Java Virtual Machine written from scratch in Java. It reads class files, interprets the bytecode and runs the real JDK 17 class library on top, module system included. It is complete enough to run `javac`: the Java compiler, running on this VM, compiles this VM's own source to exactly the same class files as on real Java.

```
./build
./jvm -cp classes Main arg1 arg2
./test
```

Building needs JDK 17 or newer. Programs run against the JDK 17 class library, found through `JVM_JDK` (default `/usr/lib/jvm/java-17-openjdk-amd64`). Linux only.

javac running on this VM, compiling this VM, against the same javac on real Java:

```
$ ./jvm com.sun.tools.javac.Main -d a src/jvm/*.java
$ java com.sun.tools.javac.Main -d b src/jvm/*.java
$ diff -r a b && echo identical
identical
```

## What works

Each program in `tests/` must print exactly what real Java prints and exit with the same code. Together they cover lambdas and streams, records and sealed types, threads, locks and `wait`/`notify`, reflection, annotations and proxies, exceptions and stack traces, `java.io` and `java.nio.file`, jars and custom class loaders, serialization, `BigInteger`, regex, `java.time`, other locales and charsets, sockets, the JDK's HTTP server and both HTTP clients.

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

## Limits

- No on-stack replacement: a long loop in a method that runs only once, like `main`, stays interpreted.
- No bytecode verifier.
- Weak and soft references are never cleared and finalizers never run.
- TCP over IPv4 only, no UDP. No child processes, no JNI libraries, no GUI.

## Todo

- [ ] On-stack replacement
- [ ] JDK 21 class library
