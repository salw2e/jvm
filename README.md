# jvm

Java virtual machine written in Java. Runs the JDK 17 class library, and is complete enough to run javac.

```
./build
./jvm -cp classes Main
./test
```

Needs JDK 17+ and Linux.

```
$ ./jvm com.sun.tools.javac.Main -d a src/jvm/*.java
$ java com.sun.tools.javac.Main -d b src/jvm/*.java
$ diff -r a b && echo identical
identical
```

javac compiling this project takes 19 s here and 3.6 s on normal Java.

Missing: on-stack replacement, the bytecode verifier, JNI, UDP and GUI.
