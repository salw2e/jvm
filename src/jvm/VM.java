package jvm;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

final class VM {
    static boolean trace, debug;
    static String home;
    static FileSystem jrt;
    static List<String> classpath = new ArrayList<>();
    static final HashMap<String, Klass> boot = new HashMap<>();
    static final HashMap<String, List<String>> pkgs = new HashMap<>();
    static final ConcurrentHashMap<String, Obj> strings = new ConcurrentHashMap<>();
    static final HashMap<Character, Klass> prims = new HashMap<>();
    static final HashMap<String, String> props = new HashMap<>();

    static Klass OBJECT, CLASS, STRING, THREAD, THROWABLE, CLONEABLE, SERIALIZABLE, MH, VH, BYTES;
    static Field S_VALUE, S_CODER;
    static final Klass[] PA = new Klass[12];
    static T main;

    // class loading

    static byte[] find(String name) {
        try {
            int s = name.lastIndexOf('/');
            String pkg = s < 0 ? "" : name.substring(0, s).replace('/', '.');
            List<String> mods;
            synchronized (pkgs) {
                mods = pkgs.get(pkg);
                if (mods == null) {
                    mods = new ArrayList<>();
                    Path p = jrt.getPath("/packages/" + pkg);
                    if (s >= 0 && Files.isDirectory(p)) {
                        try (Stream<Path> l = Files.list(p)) {
                            for (Path m : (Iterable<Path>) l::iterator) mods.add(m.getFileName().toString());
                        }
                    }
                    pkgs.put(pkg, mods);
                }
            }
            for (String m : mods) {
                if (Loaders.layered) synchronized (Loaders.bootModules) { if (!Loaders.bootModules.contains(m)) continue; }
                Path p = jrt.getPath("/modules/" + m + "/" + name + ".class");
                if (Files.exists(p)) return Files.readAllBytes(p);
            }
            return null;
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    static Klass load(T t, String name, Obj loader) {
        if (name.charAt(0) == '[') {
            char c = name.charAt(1);
            Klass comp = c == '[' ? load(t, name.substring(1), loader)
                    : c == 'L' ? load(t, name.substring(2, name.length() - 1), loader) : prims.get(c);
            return arrayOf(comp);
        }
        if (loader != null) return Loaders.load(t, name, loader);
        Klass k = bootLoad(t, name);
        if (k == null) throw thrown(t, "java/lang/NoClassDefFoundError", name);
        return k;
    }

    static Klass bootLoad(T t, String name) {
        synchronized (boot) {
            Klass k = boot.get(name);
            if (k != null) return k;
            byte[] b = find(name);
            if (b == null) return null;
            return define(t, name, b, null, null, false);
        }
    }

    static Klass define(T t, String name, byte[] b, Obj loader, Obj pd, boolean hidden) {
        Klass k;
        try {
            k = Klass.parse(b);
        } catch (ClassFormatError | RuntimeException e) {
            throw thrown(t, "java/lang/ClassFormatError", String.valueOf(e.getMessage()));
        }
        if (name != null && !hidden && !k.name.equals(name)) throw thrown(t, "java/lang/NoClassDefFoundError", name + " (wrong name: " + k.name + ")");
        k.loader = loader;
        k.pd = pd;
        k.hidden = hidden;
        if (k.supIdx != 0) k.sup = load(t, k.className(k.supIdx), loader);
        k.ifaces = new Klass[k.ifIdx.length];
        for (int i = 0; i < k.ifaces.length; i++) k.ifaces[i] = load(t, k.className(k.ifIdx[i]), loader);
        k.link();
        Natives.bind(k);
        if (!hidden) {
            if (loader == null) synchronized (boot) { boot.put(k.name, k); }
            else Loaders.record(loader, k.name, k);
        }
        return k;
    }

    static Klass arrayOf(Klass c) {
        Klass a = c.arr;
        return a != null ? a : arrayOf0(c);
    }

    private static synchronized Klass arrayOf0(Klass c) {
        Klass a = c.arr;
        if (a != null) return a;
        a = new Klass();
        a.name = "[" + (c.prim != 0 ? String.valueOf(c.prim) : c.comp != null ? c.name : "L" + c.name + ";");
        a.comp = c;
        a.sup = OBJECT;
        a.ifaces = new Klass[]{CLONEABLE, SERIALIZABLE};
        a.flags = 0x411 | c.flags & 1;
        a.loader = c.loader;
        a.link();
        a.state = Klass.INITED;
        return c.arr = a;
    }

    static Klass prim(char c, String name) {
        Klass k = new Klass();
        k.name = name;
        k.prim = c;
        k.flags = 0x411;
        k.display = new Klass[]{k};
        k.statics = new Obj(k, Klass.NOP, Klass.NOR);
        k.state = Klass.INITED;
        prims.put(c, k);
        return k;
    }

    static Klass fromDesc(T t, String d, Obj loader) {
        char c = d.charAt(0);
        if (c == 'L') return load(t, d.substring(1, d.length() - 1), loader);
        if (c == '[') return load(t, d, loader);
        return prims.get(c);
    }

    static Obj mirror(Klass k) {
        Obj m = k.mirror;
        if (m != null) return m;
        synchronized (k) {
            if ((m = k.mirror) == null) {
                m = CLASS.alloc();
                m.x = k;
                m.r[CLASS.field("classLoader").slot] = k.loader;
                if (k.comp != null) m.r[CLASS.field("componentType").slot] = mirror(k.comp);
                Loaders.module(m, k);
                k.mirror = m;
            }
        }
        return m;
    }

    static Klass klass(Obj mirror) { return (Klass) mirror.x; }

    static String dots(Klass k) { return k.name.replace('/', '.'); }

    // constant pool

    static Klass klassAt(T t, Klass k, int i) {
        Object o = k.res[i];
        if (o instanceof Klass c) return c;
        String n = k.utf[k.ca[i]];
        Klass c = n.equals(k.name) ? k : load(t, n, k.loader);
        k.res[i] = c;
        return c;
    }

    static Field fieldAt(T t, Klass k, int i) {
        Klass c = klassAt(t, k, k.ca[i]);
        int nt = k.cb[i];
        Field f = c.field(k.utf[k.ca[nt]], k.utf[k.cb[nt]]);
        if (f == null) throw thrown(t, "java/lang/NoSuchFieldError", k.utf[k.ca[nt]]);
        k.res[i] = f;
        return f;
    }

    static Method methodAt(T t, Klass k, int i) {
        Klass c = klassAt(t, k, k.ca[i]);
        int nt = k.cb[i];
        String name = k.utf[k.ca[nt]], desc = k.utf[k.cb[nt]];
        Method m = c == MH || c == VH ? Invoke.polymorphic(t, k, c, name, desc) : null;
        if (m == null) m = c.method(name, desc);
        if (m == null) throw thrown(t, "java/lang/NoSuchMethodError", dots(c) + "." + name + desc);
        k.res[i] = m;
        return m;
    }

    static Method specialAt(T t, Klass k, int i) {
        Method m = methodAt(t, k, i);
        if (!m.isInit() && !m.klass.isInterface() && m.klass != k && k.sup != null && k.is(m.klass) && (k.flags & 0x20) != 0) {
            m = k.sup.method(m.name, m.desc);
        }
        k.res[i] = m;
        return m;
    }

    static Object constant(T t, Klass k, int i) {
        switch (k.tag[i]) {
            case 8 -> { return k.res[i] = intern(k.utf[k.ca[i]]); }
            case 7 -> { return mirror(klassAt(t, k, i)); }
            default -> { return Invoke.constant(t, k, i); }
        }
    }

    // objects

    static Obj newArray(int type, int n) {
        return switch (type) {
            case 4 -> new Obj(PA[4], new byte[n]);
            case 5 -> new Obj(PA[5], new char[n]);
            case 6 -> new Obj(PA[6], new float[n]);
            case 7 -> new Obj(PA[7], new double[n]);
            case 8 -> new Obj(PA[8], new byte[n]);
            case 9 -> new Obj(PA[9], new short[n]);
            case 10 -> new Obj(PA[10], new int[n]);
            default -> new Obj(PA[11], new long[n]);
        };
    }

    static Obj newArray(Klass comp, int n) {
        return switch (comp.prim) {
            case 'Z' -> newArray(4, n);
            case 'C' -> newArray(5, n);
            case 'F' -> newArray(6, n);
            case 'D' -> newArray(7, n);
            case 'B' -> newArray(8, n);
            case 'S' -> newArray(9, n);
            case 'I' -> newArray(10, n);
            case 'J' -> newArray(11, n);
            default -> new Obj(arrayOf(comp), new Obj[n]);
        };
    }

    static Obj multi(Klass a, int[] dims, int d) {
        Obj o = newArray(a.comp, dims[d]);
        if (d + 1 < dims.length) {
            Obj[] e = (Obj[]) o.x;
            for (int i = 0; i < e.length; i++) e[i] = multi(a.comp, dims, d + 1);
        }
        return o;
    }

    static Obj bytes(byte[] b) { return new Obj(BYTES, b); }

    static Obj str(String s) {
        if (s == null) return null;
        Obj o = STRING.alloc();
        int n = s.length();
        boolean latin = true;
        for (int i = 0; i < n; i++) if (s.charAt(i) > 255) { latin = false; break; }
        byte[] v = new byte[latin ? n : n * 2];
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (latin) v[i] = (byte) c;
            else { v[i * 2] = (byte) c; v[i * 2 + 1] = (byte) (c >> 8); }
        }
        o.r[S_VALUE.slot] = bytes(v);
        o.p[S_CODER.slot] = latin ? 0 : 1;
        return o;
    }

    static String str(Obj o) {
        if (o == null) return null;
        byte[] v = (byte[]) o.r[S_VALUE.slot].x;
        char[] c;
        if (o.p[S_CODER.slot] == 0) {
            c = new char[v.length];
            for (int i = 0; i < c.length; i++) c[i] = (char) (v[i] & 0xff);
        } else {
            c = new char[v.length / 2];
            for (int i = 0; i < c.length; i++) c[i] = (char) (v[i * 2] & 0xff | v[i * 2 + 1] << 8);
        }
        return new String(c);
    }

    static Obj intern(String s) {
        Obj o = strings.get(s);
        if (o == null) {
            Obj n = str(s);
            o = strings.putIfAbsent(s, n);
            if (o == null) o = n;
        }
        return o;
    }

    static Obj strings(T t, String[] a) {
        Obj[] o = new Obj[a.length];
        for (int i = 0; i < a.length; i++) o[i] = str(a[i]);
        return new Obj(arrayOf(STRING), o);
    }

    // init

    static void init(T t, Klass k) {
        if (k.state == Klass.INITED) return;
        synchronized (k) {
            while (k.state == Klass.INITING && k.initBy != t) {
                try { k.wait(); } catch (InterruptedException e) { }
            }
            if (k.state == Klass.INITING || k.state == Klass.INITED) return;
            if (k.state == Klass.FAILED) throw thrown(t, "java/lang/NoClassDefFoundError", "Could not initialize class " + dots(k));
            k.state = Klass.INITING;
            k.initBy = t;
        }
        int state = Klass.FAILED;
        try {
            if (k.sup != null) init(t, k.sup);
            for (Klass i : k.all) if (i.state != Klass.INITED && !k.isInterface()) for (Method m : i.methods) if (!m.isStatic() && !m.isAbstract()) { init(t, i); break; }
            for (Field f : k.fields) {
                if (!f.isStatic() || f.cv == 0) continue;
                if (f.ref) k.statics.r[f.slot] = intern(k.utf[k.ca[f.cv]]);
                else k.statics.p[f.slot] = k.num[f.cv];
            }
            Method cl = k.mmap.get("<clinit>()V");
            if (cl != null) call(t, cl);
            state = Klass.INITED;
        } catch (Throw e) {
            if (e.ex.k.is(load(t, "java/lang/Error", null))) throw e;
            Klass w = load(t, "java/lang/ExceptionInInitializerError", null);
            Obj o = w.alloc();
            call(t, w.mmap.get("<init>(Ljava/lang/Throwable;)V"), o, e.ex);
            throw new Throw(o);
        } finally {
            synchronized (k) {
                k.state = state;
                k.initBy = null;
                k.notifyAll();
            }
        }
    }

    // calls into the guest

    static int push(T t, Object... args) {
        int b = t.top, i = b;
        for (Object a : args) {
            if (a == null || a instanceof Obj) t.R[i++] = (Obj) a;
            else if (a instanceof Long l) { t.P[i] = l; i += 2; }
            else if (a instanceof Double d) { t.P[i] = Double.doubleToRawLongBits(d); i += 2; }
            else if (a instanceof Float f) t.P[i++] = Float.floatToRawIntBits(f);
            else if (a instanceof Boolean z) t.P[i++] = z ? 1 : 0;
            else if (a instanceof Character c) t.P[i++] = c;
            else t.P[i++] = ((Number) a).intValue();
        }
        return b;
    }

    static long call(T t, Method m, Object... args) {
        int b = push(t, args);
        try {
            Interp.invoke(t, m, b);
        } finally {
            t.top = b;
        }
        return t.P[b];
    }

    static Obj callR(T t, Method m, Object... args) {
        int b = push(t, args);
        try {
            Interp.invoke(t, m, b);
        } finally {
            t.top = b;
        }
        return t.R[b];
    }

    static Method method(T t, String cls, String name, String desc) {
        Klass k = load(t, cls, null);
        Method m = k.method(name, desc);
        if (m == null) throw new InternalError(cls + "." + name + desc);
        if (m.isStatic()) init(t, k);
        return m;
    }

    static Method virtual(Obj o, String name, String desc) {
        Method m = o.k.method(name, desc);
        if (m == null) throw new InternalError(o.k.name + "." + name + desc);
        return m;
    }

    static void callNative(T t, Method m, int b) {
        Native n = m.nat;
        if (n == null) {
            if (!m.isNative()) throw thrown(t, "java/lang/AbstractMethodError", m.toString());
            throw thrown(t, "java/lang/UnsatisfiedLinkError", m.toString());
        }
        int d = t.depth;
        if (d >= t.limit) throw overflow(t);
        t.fm[d] = m;
        t.fpc[d] = -1;
        t.depth = d + 1;
        t.top = b + m.argSlots + 2;
        if (trace) traceEnter(t, m);
        try {
            n.call(t, b);
        } finally {
            t.depth = d;
        }
    }

    // exceptions

    static Throw thrown(T t, String cls, String msg) {
        if (debug) {
            System.err.println("thrown " + cls + ": " + msg);
            for (int i = t.depth - 1; i >= 0 && i > t.depth - 12; i--) System.err.println("    " + t.fm[i] + " @" + t.fpc[i]);
        }
        Klass k = load(t, cls, null);
        init(t, k);
        Obj e = k.alloc();
        if (msg == null) call(t, k.mmap.get("<init>()V"), e);
        else call(t, k.mmap.get("<init>(Ljava/lang/String;)V"), e, str(msg));
        return new Throw(e);
    }

    static Throw overflow(T t) {
        if (t.limit > T.MAXD) throw new InternalError("stack overflow while reporting stack overflow");
        t.limit = T.MAXD + 60;
        try {
            return thrown(t, "java/lang/StackOverflowError", null);
        } finally {
            t.limit = T.MAXD;
        }
    }

    static Throw convert(T t, Throwable e) {
        if (debug) System.err.println("host " + e + " at " + e.getStackTrace()[0]);
        String c = e instanceof NullPointerException ? "java/lang/NullPointerException"
                : e instanceof ArrayIndexOutOfBoundsException ? "java/lang/ArrayIndexOutOfBoundsException"
                : e instanceof ArithmeticException ? "java/lang/ArithmeticException"
                : e instanceof NegativeArraySizeException ? "java/lang/NegativeArraySizeException"
                : e instanceof OutOfMemoryError ? "java/lang/OutOfMemoryError" : "java/lang/InternalError";
        String msg = e instanceof NullPointerException || e instanceof StackOverflowError ? null : e.getMessage();
        if (e instanceof StackOverflowError) return overflow(t);
        if (e instanceof ClassCastException) { msg = "vm: " + e; e.printStackTrace(); }
        return thrown(t, c, msg);
    }

    static String describe(Obj ex) {
        try {
            return dots(ex.k) + ": " + str(ex.r[THROWABLE.field("detailMessage").slot]);
        } catch (RuntimeException e) {
            return ex.k.name;
        }
    }

    // monitors

    static void lock(T t, Obj o) {
        if (o.owner == t) { o.locks++; return; }
        synchronized (o) {
            while (o.owner != null) {
                o.waiters++;
                try { o.wait(); } catch (InterruptedException e) { } finally { o.waiters--; }
            }
            o.owner = t;
            o.locks = 1;
        }
    }

    static void unlock(T t, Obj o) {
        if (o.owner != t) throw thrown(t, "java/lang/IllegalMonitorStateException", null);
        if (--o.locks == 0) {
            synchronized (o) {
                o.owner = null;
                if (o.waiters > 0) o.notifyAll();
            }
        }
    }

    // boot

    static void boot(T t) throws IOException {
        jrt = FileSystems.newFileSystem(URI.create("jrt:/"), Map.of("java.home", home));
        prim('Z', "boolean"); prim('B', "byte"); prim('C', "char"); prim('S', "short");
        prim('I', "int"); prim('J', "long"); prim('F', "float"); prim('D', "double"); prim('V', "void");
        OBJECT = load(t, "java/lang/Object", null);
        CLONEABLE = load(t, "java/lang/Cloneable", null);
        SERIALIZABLE = load(t, "java/io/Serializable", null);
        CLASS = load(t, "java/lang/Class", null);
        STRING = load(t, "java/lang/String", null);
        THROWABLE = load(t, "java/lang/Throwable", null);
        THREAD = load(t, "java/lang/Thread", null);
        MH = load(t, "java/lang/invoke/MethodHandle", null);
        VH = load(t, "java/lang/invoke/VarHandle", null);
        for (int i = 4; i < 12; i++) PA[i] = arrayOf(prims.get("ZCFDBSIJ".charAt(i - 4)));
        BYTES = PA[8];
        S_VALUE = STRING.field("value");
        S_CODER = STRING.field("coder");

        Klass uc = load(t, "jdk/internal/misc/UnsafeConstants", null);
        init(t, uc);
        uc.statics.p[uc.field("ADDRESS_SIZE0").slot] = 8;
        uc.statics.p[uc.field("PAGE_SIZE").slot] = 4096;
        uc.statics.p[uc.field("UNALIGNED_ACCESS").slot] = 1;

        init(t, STRING);
        init(t, load(t, "java/lang/System", null));
        init(t, CLASS);
        Klass tg = load(t, "java/lang/ThreadGroup", null);
        init(t, tg);
        Obj sys = tg.alloc();
        call(t, tg.mmap.get("<init>()V"), sys);
        Obj grp = tg.alloc();
        call(t, tg.mmap.get("<init>(Ljava/lang/ThreadGroup;Ljava/lang/String;)V"), grp, sys, str("main"));
        Obj th = THREAD.alloc();
        th.p[THREAD.field("priority").slot] = 5;
        th.p[THREAD.field("threadStatus").slot] = 5;
        th.p[THREAD.field("eetop").slot] = 1;
        th.x = t;
        t.thread = th;
        init(t, THREAD);
        call(t, THREAD.mmap.get("<init>(Ljava/lang/ThreadGroup;Ljava/lang/String;)V"), th, grp, str("main"));
        init(t, load(t, "java/lang/reflect/Method", null));
        call(t, method(t, "java/lang/System", "initPhase1", "()V"));
        Loaders.boot(t);
        if (call(t, method(t, "java/lang/System", "initPhase2", "(ZZ)I"), true, true) != 0) throw new InternalError("module system failed to start");
        Loaders.layered = true;
        call(t, method(t, "java/lang/System", "initPhase3", "()V"));
    }

    static void traceEnter(T t, Method m) {
        StringBuilder s = new StringBuilder();
        for (int i = 1; i < t.depth && i < 60; i++) s.append(' ');
        System.err.println(s + m.toString());
    }
}
