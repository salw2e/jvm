# jvm

Java Virtual Machine written in Java. It reads class files, interprets the bytecode and runs the real JDK 17 class library on top, module system included. It can run javac, and javac running on it compiles this project to the same class files as on normal Java.

```
./build
./jvm -cp classes Main arg1 arg2
./test
```

Needs JDK 17 or newer to build. Programs run against the JDK 17 class library, which it finds through `JVM_JDK` (default `/usr/lib/jvm/java-17-openjdk-amd64`). Linux only.

```
$ ./jvm com.sun.tools.javac.Main -d a src/jvm/*.java
$ java com.sun.tools.javac.Main -d b src/jvm/*.java
$ diff -r a b && echo identical
identical
```

The programs in `tests/` have to print the same output as real Java. They cover lambdas, records, threads, reflection, proxies, serialization, java.nio.file, sockets and the JDK's HTTP server and clients.

Methods that get called a lot are compiled to host bytecode, so the host JVM's JIT picks them up. On a 2-core VPS javac compiling this project takes 19 s here and 3.6 s on normal Java. `JVM_JIT=0` turns it off.

Missing: on-stack replacement, the bytecode verifier, clearing weak references, finalizers, UDP, child processes, JNI and GUI.

How the pieces fit together is in [docs/notes.md](docs/notes.md).
