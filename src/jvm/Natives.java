package jvm;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;

final class Natives {
    static final HashMap<String, Native> map = new HashMap<>();
    static final Native NOP = (t, b) -> { };

    static void reg(String key, Native n) { map.put(key, n); }

    static void bind(Klass k) {
        for (Method m : k.methods) {
            Native n = map.get(k.name + "." + m.name + m.desc);
            if (n == null && m.isNative()) n = map.get(k.name + "." + m.name);
            if (n != null) { m.nat = n; m.code = null; }
            else if (m.isNative() && (m.name.equals("registerNatives") || m.name.equals("initIDs"))) m.nat = NOP;
            else if (m.isNative() && k.name.equals("jdk/net/LinuxSocketOptions") && m.name.endsWith("Supported0")) m.nat = (t, b) -> t.P[b] = 0;
        }
    }

    static Obj box(T t, char type, long v) {
        String c = switch (type) {
            case 'Z' -> "java/lang/Boolean";
            case 'B' -> "java/lang/Byte";
            case 'C' -> "java/lang/Character";
            case 'S' -> "java/lang/Short";
            case 'I' -> "java/lang/Integer";
            case 'J' -> "java/lang/Long";
            case 'F' -> "java/lang/Float";
            default -> "java/lang/Double";
        };
        Method m = VM.method(t, c, "valueOf", "(" + type + ")L" + c + ";");
        return type == 'J' || type == 'D' ? VM.callR(t, m, (Object) v) : VM.callR(t, m, (Object) (int) v);
    }

    static long unbox(Obj o) { return o.p[o.k.field("value").slot]; }

    static {
        Lang.init();
        Unsafe.init();
        Reflect.init();
        IO.init();
        Threads.init();
        Loaders.init();
        Nio.init();
        Net.init();
        Invoke.init();
    }
}

final class Lang {
    static void init() {
        Natives.reg("java/lang/Object.getClass", (t, b) -> t.R[b] = VM.mirror(t.R[b].k));
        Natives.reg("java/lang/Object.hashCode", (t, b) -> t.P[b] = System.identityHashCode(t.R[b]));
        Natives.reg("java/lang/Object.clone", (t, b) -> {
            Obj o = t.R[b];
            if (o.k.comp != null) {
                int n = Array.getLength(o.x);
                Object c = Array.newInstance(o.x.getClass().getComponentType(), n);
                System.arraycopy(o.x, 0, c, 0, n);
                t.R[b] = new Obj(o.k, c);
                return;
            }
            if (!o.k.is(VM.CLONEABLE)) throw VM.thrown(t, "java/lang/CloneNotSupportedException", VM.dots(o.k));
            Obj c = new Obj(o.k, o.p.length == 0 ? o.p : o.p.clone(), o.r.length == 0 ? o.r : o.r.clone());
            c.x = o.x;
            t.R[b] = c;
        });

        Natives.reg("java/lang/System.arraycopy", (t, b) -> {
            Obj s = t.R[b], d = t.R[b + 2];
            int sp = (int) t.P[b + 1], dp = (int) t.P[b + 3], n = (int) t.P[b + 4];
            if (s.k.comp == null || d.k.comp == null) throw VM.thrown(t, "java/lang/ArrayStoreException", "arraycopy: not an array");
            if (s.k == d.k || s.k.comp.prim == 0 && d.k.comp.prim == 0 && s.k.comp.is(d.k.comp)) {
                System.arraycopy(s.x, sp, d.x, dp, n);
                return;
            }
            if (s.k.comp.prim != 0 || d.k.comp.prim != 0) throw VM.thrown(t, "java/lang/ArrayStoreException", "arraycopy: type mismatch");
            Obj[] sa = (Obj[]) s.x, da = (Obj[]) d.x;
            if (sp < 0 || dp < 0 || n < 0 || sp + n > sa.length || dp + n > da.length) throw new ArrayIndexOutOfBoundsException("arraycopy: out of bounds");
            for (int i = 0; i < n; i++) {
                Obj v = sa[sp + i];
                if (v != null && !v.k.is(d.k.comp)) throw VM.thrown(t, "java/lang/ArrayStoreException", "arraycopy: element type mismatch");
                da[dp + i] = v;
            }
        });
        Natives.reg("java/lang/System.identityHashCode", (t, b) -> t.P[b] = t.R[b] == null ? 0 : System.identityHashCode(t.R[b]));
        Natives.reg("java/lang/System.nanoTime", (t, b) -> t.P[b] = System.nanoTime());
        Natives.reg("java/lang/System.currentTimeMillis", (t, b) -> t.P[b] = System.currentTimeMillis());
        Natives.reg("java/lang/System.setIn0", (t, b) -> statics("java/lang/System").r[VM.boot.get("java/lang/System").field("in").slot] = t.R[b]);
        Natives.reg("java/lang/System.setOut0", (t, b) -> statics("java/lang/System").r[VM.boot.get("java/lang/System").field("out").slot] = t.R[b]);
        Natives.reg("java/lang/System.setErr0", (t, b) -> statics("java/lang/System").r[VM.boot.get("java/lang/System").field("err").slot] = t.R[b]);
        Natives.reg("java/lang/System.mapLibraryName", (t, b) -> t.R[b] = VM.str("lib" + VM.str(t.R[b]) + ".so"));

        Natives.reg("java/lang/Runtime.availableProcessors", (t, b) -> t.P[b] = Runtime.getRuntime().availableProcessors());
        Natives.reg("java/lang/Runtime.freeMemory", (t, b) -> t.P[b] = Runtime.getRuntime().freeMemory());
        Natives.reg("java/lang/Runtime.totalMemory", (t, b) -> t.P[b] = Runtime.getRuntime().totalMemory());
        Natives.reg("java/lang/Runtime.maxMemory", (t, b) -> t.P[b] = Runtime.getRuntime().maxMemory());
        Natives.reg("java/lang/Runtime.gc", (t, b) -> System.gc());
        Natives.reg("java/lang/Shutdown.beforeHalt", Natives.NOP);
        Natives.reg("java/lang/Shutdown.halt0", (t, b) -> Runtime.getRuntime().halt((int) t.P[b]));

        Natives.reg("java/lang/Float.floatToRawIntBits", Natives.NOP);
        Natives.reg("java/lang/Float.intBitsToFloat", Natives.NOP);
        Natives.reg("java/lang/Double.doubleToRawLongBits", Natives.NOP);
        Natives.reg("java/lang/Double.longBitsToDouble", Natives.NOP);
        java.util.Map<String, java.util.function.DoubleUnaryOperator> strict = java.util.Map.ofEntries(
                java.util.Map.entry("sin", StrictMath::sin), java.util.Map.entry("cos", StrictMath::cos), java.util.Map.entry("tan", StrictMath::tan),
                java.util.Map.entry("asin", StrictMath::asin), java.util.Map.entry("acos", StrictMath::acos), java.util.Map.entry("atan", StrictMath::atan),
                java.util.Map.entry("log", StrictMath::log), java.util.Map.entry("log10", StrictMath::log10), java.util.Map.entry("sqrt", StrictMath::sqrt),
                java.util.Map.entry("sinh", StrictMath::sinh), java.util.Map.entry("cosh", StrictMath::cosh), java.util.Map.entry("tanh", StrictMath::tanh),
                java.util.Map.entry("expm1", StrictMath::expm1), java.util.Map.entry("log1p", StrictMath::log1p));
        strict.forEach((f, op) -> Natives.reg("java/lang/StrictMath." + f, (t, b) -> t.P[b] = Interp.db(op.applyAsDouble(Interp.d(t.P[b])))));
        Natives.reg("java/lang/StrictMath.atan2", (t, b) -> t.P[b] = Interp.db(StrictMath.atan2(Interp.d(t.P[b]), Interp.d(t.P[b + 2]))));
        Natives.reg("java/lang/StrictMath.IEEEremainder", (t, b) -> t.P[b] = Interp.db(StrictMath.IEEEremainder(Interp.d(t.P[b]), Interp.d(t.P[b + 2]))));
        java.util.Map<String, java.util.function.DoubleUnaryOperator> math = java.util.Map.of(
                "sin", Math::sin, "cos", Math::cos, "tan", Math::tan, "exp", Math::exp, "log", Math::log, "log10", Math::log10, "sqrt", Math::sqrt);
        math.forEach((f, op) -> Natives.reg("java/lang/Math." + f + "(D)D", (t, b) -> t.P[b] = Interp.db(op.applyAsDouble(Interp.d(t.P[b])))));
        Natives.reg("java/lang/Math.pow(DD)D", (t, b) -> t.P[b] = Interp.db(Math.pow(Interp.d(t.P[b]), Interp.d(t.P[b + 2]))));
        Natives.reg("java/lang/ProcessEnvironment.environ", (t, b) -> {
            java.util.ArrayList<Obj> out = new java.util.ArrayList<>();
            System.getenv().forEach((k, v) -> { out.add(VM.bytes(k.getBytes())); out.add(VM.bytes(v.getBytes())); });
            t.R[b] = new Obj(VM.arrayOf(VM.BYTES), out.toArray(new Obj[0]));
        });
        Natives.reg("java/io/ObjectStreamClass.initNative", Natives.NOP);
        Natives.reg("jdk/internal/perf/Perf.createLong", (t, b) -> t.R[b] = VM.callR(t, VM.method(t, "java/nio/ByteBuffer", "allocateDirect", "(I)Ljava/nio/ByteBuffer;"), 8));
        Natives.reg("java/io/ObjectStreamClass.hasStaticInitializer", (t, b) -> t.P[b] = VM.klass(t.R[b]).mmap.containsKey("<clinit>()V") ? 1 : 0);

        Natives.reg("java/lang/String.intern", (t, b) -> t.R[b] = VM.intern(VM.str(t.R[b])));
        Natives.reg("java/lang/StringUTF16.isBigEndian", (t, b) -> t.P[b] = 0);
        Natives.reg("java/lang/StringCoding.err", (t, b) -> System.err.print(VM.str(t.R[b])));
        Natives.reg("java/lang/NullPointerException.getExtendedNPEMessage", (t, b) -> t.R[b] = null);

        Natives.reg("java/lang/Throwable.fillInStackTrace(I)Ljava/lang/Throwable;", (t, b) -> {
            Obj e = t.R[b];
            int d = t.depth - 1;
            while (d > 0 && t.fm[d - 1].name.equals("fillInStackTrace") && e.k.is(t.fm[d - 1].klass)) d--;
            while (d > 0 && t.fm[d - 1].isInit() && e.k.is(t.fm[d - 1].klass)) d--;
            int top = d;
            d = Math.min(d, 1024);
            Object[] bt = new Object[d * 2];
            for (int i = 0; i < d; i++) {
                bt[i * 2] = t.fm[top - 1 - i];
                bt[i * 2 + 1] = t.fpc[top - 1 - i];
            }
            Obj h = VM.OBJECT.alloc();
            h.x = bt;
            e.r[VM.THROWABLE.field("backtrace").slot] = h;
            e.p[VM.THROWABLE.field("depth").slot] = d;
        });
        Natives.reg("java/lang/StackTraceElement.initStackTraceElements", (t, b) -> {
            Obj[] els = (Obj[]) t.R[b].x;
            Object[] bt = (Object[]) t.R[b + 1].r[VM.THROWABLE.field("backtrace").slot].x;
            for (int i = 0; i < els.length; i++) element(t, els[i], (Method) bt[i * 2], (Integer) bt[i * 2 + 1]);
        });

        Natives.reg("jdk/internal/misc/VM.initialize", Natives.NOP);
        Natives.reg("jdk/internal/misc/VM.initializeFromArchive", Natives.NOP);
        Natives.reg("jdk/internal/misc/VM.latestUserDefinedLoader0", (t, b) -> t.R[b] = null);
        Natives.reg("jdk/internal/misc/VM.getNanoTimeAdjustment", (t, b) -> {
            java.time.Instant now = java.time.Instant.now();
            long secs = now.getEpochSecond() - t.P[b];
            t.P[b] = Math.abs(secs) > 1L << 32 ? -1 : secs * 1_000_000_000L + now.getNano();
        });
        Natives.reg("jdk/internal/misc/VM.getRuntimeArguments", (t, b) -> t.R[b] = VM.strings(t, new String[0]));
        Natives.reg("jdk/internal/misc/CDS.isDumpingClassList0", (t, b) -> t.P[b] = 0);
        Natives.reg("jdk/internal/misc/CDS.isDumpingArchive0", (t, b) -> t.P[b] = 0);
        Natives.reg("jdk/internal/misc/CDS.isSharingEnabled0", (t, b) -> t.P[b] = 0);
        Natives.reg("jdk/internal/misc/CDS.getRandomSeedForDumping", (t, b) -> t.P[b] = 0);
        Natives.reg("jdk/internal/misc/CDS.initializeFromArchive", Natives.NOP);
        Natives.reg("jdk/internal/misc/CDS.defineArchivedModules", Natives.NOP);
        Natives.reg("jdk/internal/misc/Signal.findSignal0", (t, b) -> t.P[b] = switch (VM.str(t.R[b])) { case "HUP" -> 1; case "INT" -> 2; case "TERM" -> 15; default -> -1; });
        Natives.reg("jdk/internal/misc/Signal.handle0", (t, b) -> t.P[b] = 0);
        Natives.reg("jdk/internal/misc/ScopedMemoryAccess.closeScope0", (t, b) -> t.P[b] = 1);
        Natives.reg("java/util/concurrent/atomic/AtomicLong.VMSupportsCS8", (t, b) -> t.P[b] = 1);
        Natives.reg("java/util/zip/CRC32.update(II)I", (t, b) -> t.P[b] = crc((int) t.P[b], new byte[]{(byte) t.P[b + 1]}, 0, 1));
        Natives.reg("java/util/zip/CRC32.updateBytes0", (t, b) -> t.P[b] = crc((int) t.P[b], (byte[]) t.R[b + 1].x, (int) t.P[b + 2], (int) t.P[b + 3]));
        Natives.reg("java/util/TimeZone.getSystemTimeZoneID", (t, b) -> t.R[b] = VM.str(java.util.TimeZone.getDefault().getID()));

        Natives.reg("jdk/internal/util/SystemProps$Raw.vmProperties", (t, b) -> {
            ArrayList<String> p = new ArrayList<>();
            String[] kv = {
                    "java.home", VM.home,
                    "java.class.path", String.join(":", VM.classpath),
                    "java.vm.specification.name", "Java Virtual Machine Specification",
                    "java.vm.specification.vendor", "Oracle Corporation",
                    "java.vm.specification.version", "17",
                    "java.vm.name", "jvm",
                    "java.vm.vendor", "salw2e",
                    "java.vm.version", "0.1",
                    "java.vm.info", "interpreted mode",
                    "sun.boot.library.path", VM.home + "/lib",
                    "java.library.path", "",
                    "jdk.reflect.useDirectMethodHandle", "false",
                    "sun.reflect.inflationThreshold", "2147483647",
            };
            for (String s : kv) p.add(s);
            VM.props.forEach((k, v) -> { p.add(k); p.add(v); });
            p.add(null);
            t.R[b] = VM.strings(t, p.toArray(new String[0]));
        });
        Natives.reg("jdk/internal/util/SystemProps$Raw.platformProperties", (t, b) -> {
            Klass raw = VM.boot.get("jdk/internal/util/SystemProps$Raw");
            String[] out = new String[(int) raw.num[raw.field("FIXED_LENGTH").cv]];
            for (Field f : raw.fields) {
                if (!f.name.endsWith("_NDX")) continue;
                String key = f.name.substring(1, f.name.length() - 4).replace('_', '.');
                out[(int) raw.num[f.cv]] = switch (key) {
                    case "file.encoding", "sun.jnu.encoding" -> "UTF-8";
                    case "sun.io.unicode.encoding" -> "UnicodeLittle";
                    case "sun.arch.data.model" -> "64";
                    case "sun.cpu.endian" -> "little";
                    case "user.language", "display.language", "format.language" -> "en";
                    case "sun.stdout.encoding", "sun.stderr.encoding" -> null;
                    default -> System.getProperty(key);
                };
            }
            t.R[b] = VM.strings(t, out);
        });
    }

    static final int[] CRC = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int c = i;
            for (int j = 0; j < 8; j++) c = (c & 1) != 0 ? c >>> 1 ^ 0xEDB88320 : c >>> 1;
            CRC[i] = c;
        }
    }

    static int crc(int crc, byte[] d, int off, int len) {
        crc = ~crc;
        for (int i = off; i < off + len; i++) crc = CRC[(crc ^ d[i]) & 0xff] ^ crc >>> 8;
        return ~crc;
    }

    static Obj statics(String cls) { return VM.boot.get(cls).statics; }

    static void element(T t, Obj el, Method m, int pc) {
        Klass k = el.k, c = m.klass;
        el.r[k.field("declaringClassObject").slot] = VM.mirror(c);
        el.r[k.field("declaringClass").slot] = VM.intern(VM.dots(c));
        el.r[k.field("methodName").slot] = VM.intern(m.name);
        byte[] sf = c.attrs.get("SourceFile");
        if (sf != null) el.r[k.field("fileName").slot] = VM.intern(c.utf[(sf[0] & 0xff) << 8 | sf[1] & 0xff]);
        el.p[k.field("lineNumber").slot] = m.isNative() ? -2 : m.line(pc);
        if (c.loader == null && VM.pkgs.containsKey(c.name.substring(0, Math.max(0, c.name.lastIndexOf('/'))).replace('/', '.'))) {
            java.util.List<String> mods = VM.pkgs.get(c.name.substring(0, Math.max(0, c.name.lastIndexOf('/'))).replace('/', '.'));
            if (!mods.isEmpty()) el.r[k.field("moduleName").slot] = VM.intern(mods.get(0));
        }
    }
}
