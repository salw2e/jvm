package jvm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

final class Loaders {
    static Obj unnamed;
    static final ArrayList<Obj> pending = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static HashMap<String, Klass> map(Obj loader) {
        synchronized (loader) {
            if (loader.x == null) loader.x = new HashMap<String, Klass>();
            return (HashMap<String, Klass>) loader.x;
        }
    }

    static void record(Obj loader, String name, Klass k) {
        HashMap<String, Klass> m = map(loader);
        synchronized (m) { m.put(name, k); }
    }

    static Klass loaded(Obj loader, String name) {
        HashMap<String, Klass> m = map(loader);
        synchronized (m) { return m.get(name); }
    }

    static Klass load(T t, String name, Obj loader) {
        Klass k = loaded(loader, name);
        if (k != null) return k;
        Obj mirror;
        try {
            mirror = VM.callR(t, VM.virtual(loader, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;"), loader, VM.str(name.replace('/', '.')));
        } catch (Throw e) {
            if (!e.ex.k.name.equals("java/lang/ClassNotFoundException")) throw e;
            Klass w = VM.load(t, "java/lang/NoClassDefFoundError", null);
            Obj o = w.alloc();
            VM.call(t, w.mmap.get("<init>(Ljava/lang/String;)V"), o, VM.str(name));
            VM.call(t, VM.THROWABLE.mmap.get("initCause(Ljava/lang/Throwable;)Ljava/lang/Throwable;"), o, e.ex);
            throw new Throw(o);
        }
        if (mirror == null) throw VM.thrown(t, "java/lang/NoClassDefFoundError", name);
        k = VM.klass(mirror);
        record(loader, name, k);
        return k;
    }

    static final HashMap<String, Obj> bootPackages = new HashMap<>();
    static final java.util.HashSet<String> bootModules = new java.util.HashSet<>(java.util.List.of("java.base"));
    static boolean layered;

    static String pkg(Klass k) {
        int s = k.name.lastIndexOf('/');
        return s < 0 ? "" : k.name.substring(0, s);
    }

    @SuppressWarnings("unchecked")
    static Obj named(Obj loader, String pkg) {
        if (loader == null) synchronized (bootPackages) { return bootPackages.get(pkg); }
        Object m = loader.mods;
        if (m == null) return null;
        synchronized (m) { return ((HashMap<String, Obj>) m).get(pkg); }
    }

    static void module(Obj mirror, Klass k) {
        int slot = VM.CLASS.field("module").slot;
        Klass e = k;
        while (e.comp != null) e = e.comp;
        Obj m = e.prim != 0 ? named(null, "java/lang") : named(e.loader, pkg(e));
        if (m != null) mirror.r[slot] = m;
        else if (e.loader != null) mirror.r[slot] = e.loader.r[e.loader.k.field("unnamedModule").slot];
        else if (unnamed != null) mirror.r[slot] = unnamed;
        else synchronized (pending) { pending.add(mirror); }
    }

    @SuppressWarnings("unchecked")
    static void define(T t, Obj module, Obj[] pkgs) {
        Obj loader = module.r[module.k.field("loader").slot];
        String name = VM.str(module.r[module.k.field("name").slot]);
        HashMap<String, Obj> map;
        if (loader == null) {
            map = bootPackages;
            synchronized (bootModules) { bootModules.add(name); }
        } else synchronized (loader) {
            if (loader.mods == null) loader.mods = new HashMap<String, Obj>();
            map = (HashMap<String, Obj>) loader.mods;
        }
        java.util.HashSet<String> mine = new java.util.HashSet<>();
        for (Obj p : pkgs) mine.add(VM.str(p).replace('.', '/'));
        synchronized (map) { for (String p : mine) map.put(p, module); }
        if (loader != null) return;
        java.util.ArrayList<Klass> all;
        synchronized (VM.boot) { all = new java.util.ArrayList<>(VM.boot.values()); }
        int slot = VM.CLASS.field("module").slot;
        for (Klass k : all) {
            if (k.mirror == null || !mine.contains(pkg(k))) continue;
            k.mirror.r[slot] = module;
            for (Klass a = k.arr; a != null; a = a.arr) if (a.mirror != null) a.mirror.r[slot] = module;
        }
        if (name.equals("java.base")) {
            for (Klass p : VM.prims.values()) for (Klass a = p; a != null; a = a.arr) if (a.mirror != null) a.mirror.r[slot] = module;
        }
    }

    static void boot(T t) {
        Klass m = VM.load(t, "java/lang/Module", null);
        VM.init(t, m);
        Obj u = m.alloc();
        VM.call(t, m.mmap.get("<init>(Ljava/lang/ClassLoader;)V"), u, null);
        synchronized (pending) {
            unnamed = u;
            for (Obj o : pending) o.r[VM.CLASS.field("module").slot] = u;
            pending.clear();
        }
    }

    static void init() {
        String c = "java/lang/ClassLoader.";
        Natives.reg(c + "findBootstrapClass", (t, b) -> {
            Klass k = VM.bootLoad(t, VM.str(t.R[b]).replace('.', '/'));
            t.R[b] = k == null ? null : VM.mirror(k);
        });
        Natives.reg(c + "findLoadedClass0", (t, b) -> {
            Klass k = loaded(t.R[b], VM.str(t.R[b + 1]).replace('.', '/'));
            t.R[b] = k == null ? null : VM.mirror(k);
        });
        Natives.reg(c + "findInitiatedClass0", (t, b) -> t.R[b] = null);
        Natives.reg(c + "defineClass1", (t, b) -> {
            byte[] d = (byte[]) t.R[b + 2].x;
            int off = (int) t.P[b + 3], len = (int) t.P[b + 4];
            String n = VM.str(t.R[b + 1]);
            t.R[b] = VM.mirror(VM.define(t, n == null ? null : n.replace('.', '/'), Arrays.copyOfRange(d, off, off + len), t.R[b], t.R[b + 5], false));
        });
        Natives.reg(c + "defineClass0", (t, b) -> {
            byte[] d = (byte[]) t.R[b + 3].x;
            int off = (int) t.P[b + 4], len = (int) t.P[b + 5], flags = (int) t.P[b + 8];
            Klass lookup = VM.klass(t.R[b + 1]);
            String n = VM.str(t.R[b + 2]).replace('.', '/');
            boolean hidden = (flags & 2) != 0;
            Klass k = VM.define(t, n, Arrays.copyOfRange(d, off, off + len), t.R[b], t.R[b + 6], hidden);
            if ((flags & 1) != 0) k.host = Reflect.nestHost(t, lookup);
            k.classData = t.R[b + 9];
            VM.mirror(k).r[VM.CLASS.field("classData").slot] = t.R[b + 9];
            if (t.P[b + 7] != 0) VM.init(t, k);
            t.R[b] = VM.mirror(k);
        });
        Natives.reg("jdk/internal/loader/BootLoader.getSystemPackageLocation", (t, b) -> {
            java.util.List<String> mods;
            synchronized (VM.pkgs) { mods = VM.pkgs.get(VM.str(t.R[b]).replace('/', '.')); }
            t.R[b] = mods == null || mods.isEmpty() ? null : VM.str("jrt:/" + mods.get(0));
        });
        Natives.reg("jdk/internal/loader/BootLoader.setBootLoaderUnnamedModule0", Natives.NOP);
        Natives.reg("jdk/internal/loader/NativeLibraries.findBuiltinLib", (t, b) -> { });
        Natives.reg("jdk/internal/loader/NativeLibraries.load", (t, b) -> t.P[b] = 1);
        Natives.reg("jdk/internal/loader/NativeLibraries.unload", Natives.NOP);
        Natives.reg("jdk/internal/loader/NativeLibraries$NativeLibraryImpl.findEntry0", (t, b) -> t.P[b] = 0);
        for (String s : new String[]{"addReads0", "addExports0", "addExportsToAll0", "addExportsToAllUnnamed0"}) Natives.reg("java/lang/Module." + s, Natives.NOP);
        Natives.reg("java/lang/Module.defineModule0", (t, b) -> define(t, t.R[b], (Obj[]) t.R[b + 4].x));
        Natives.reg("jdk/internal/jimage/NativeImageBuffer.getNativeMap", (t, b) -> t.R[b] = null);
        Natives.reg(c + "defineClass2", (t, b) -> {
            Obj buf = t.R[b + 2];
            int off = (int) t.P[b + 3], len = (int) t.P[b + 4];
            byte[] d = new byte[len];
            Obj hb = buf.r[buf.k.field("hb").slot];
            if (hb != null) System.arraycopy(hb.x, (int) buf.p[buf.k.field("offset").slot] + off, d, 0, len);
            else Nio.U.copyMemory(null, buf.p[buf.k.field("address").slot] + off, d, Nio.BYTES, len);
            String n = VM.str(t.R[b + 1]);
            t.R[b] = VM.mirror(VM.define(t, n == null ? null : n.replace('.', '/'), d, t.R[b], t.R[b + 5], false));
        });
    }
}
