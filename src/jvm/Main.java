package jvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    public static void main(String[] a) throws Exception {
        List<String> args = new ArrayList<>(List.of(a));
        String dflt = "/usr/lib/jvm/java-17-openjdk-amd64";
        VM.home = System.getenv().getOrDefault("JVM_JDK", Files.isDirectory(Path.of(dflt)) ? dflt : System.getProperty("java.home"));
        Path release = Path.of(VM.home, "release");
        if (!Files.exists(release) || !Files.readString(release).contains("JAVA_VERSION=\"17")) {
            System.err.println("jvm: " + VM.home + " is not a JDK 17, set JVM_JDK to one");
            System.exit(2);
        }
        String cp = ".";
        while (!args.isEmpty() && args.get(0).startsWith("-")) {
            String o = args.remove(0);
            if (o.equals("-cp") || o.equals("-classpath")) cp = args.remove(0);
            else if (o.equals("-trace")) VM.trace = true;
            else if (o.equals("-debug")) VM.debug = true;
            else if (o.startsWith("-D")) {
                int e = o.indexOf('=');
                VM.props.put(e < 0 ? o.substring(2) : o.substring(2, e), e < 0 ? "" : o.substring(e + 1));
            }
        }
        if (args.isEmpty()) {
            System.err.println("usage: jvm [-cp path] Main [args]");
            System.exit(2);
        }
        VM.classpath.addAll(List.of(cp.split(":")));
        String main = args.remove(0).replace('.', '/');
        int[] code = {0};
        Thread th = new Thread(null, () -> code[0] = run(main, args.toArray(new String[0])), "main", 2L << 30);
        th.start();
        th.join();
        System.exit(code[0]);
    }

    static int run(String main, String[] args) {
        T t = VM.main = new T(1 << 20);
        t.host = Thread.currentThread();
        int code = 0;
        try {
            VM.boot(t);
            Obj scl = VM.callR(t, VM.method(t, "java/lang/ClassLoader", "getSystemClassLoader", "()Ljava/lang/ClassLoader;"));
            Klass k;
            try {
                k = VM.load(t, main, scl);
            } catch (Throw e) {
                if (!e.ex.k.name.equals("java/lang/NoClassDefFoundError")) throw e;
                System.err.println("Error: Could not find or load main class " + main.replace('/', '.'));
                return 1;
            }
            Method m = k.mmap.get("main([Ljava/lang/String;)V");
            if (m == null || !m.isStatic()) {
                System.err.println("Error: Main method not found in class " + main.replace('/', '.'));
                return 1;
            }
            VM.init(t, k);
            VM.call(t, m, VM.strings(t, args));
        } catch (Throw e) {
            code = 1;
            try {
                VM.call(t, VM.THREAD.method("dispatchUncaughtException", "(Ljava/lang/Throwable;)V"), t.thread, e.ex);
            } catch (Throwable e2) {
                System.err.println("Exception in thread \"main\" " + VM.describe(e.ex));
            }
        } catch (Throwable e) {
            e.printStackTrace();
            return 70;
        }
        try {
            Threads.finish0(t);
            VM.call(t, VM.method(t, "java/lang/Shutdown", "shutdown", "()V"));
        } catch (Throw e) {
            System.err.println(VM.describe(e.ex));
        }
        return code;
    }
}
