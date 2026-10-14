package jvm;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.util.concurrent.locks.LockSupport;

final class Unsafe {
    static final sun.misc.Unsafe U;
    static final VarHandle LONGS = MethodHandles.arrayElementVarHandle(long[].class);
    static final VarHandle INTS = MethodHandles.arrayElementVarHandle(int[].class);
    static final VarHandle OBJS = MethodHandles.arrayElementVarHandle(Obj[].class);
    static final VarHandle B_SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    static final VarHandle B_CHAR = MethodHandles.byteArrayViewVarHandle(char[].class, ByteOrder.LITTLE_ENDIAN);
    static final VarHandle B_INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    static final VarHandle B_LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    static final int BASE = 16;

    static {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (sun.misc.Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    static int scale(Klass a) {
        return switch (a.comp.prim) {
            case 'Z', 'B' -> 1;
            case 'C', 'S' -> 2;
            case 'J', 'D' -> 8;
            default -> 4;
        };
    }

    static long get(Obj o, long off, int size) {
        if (o == null) {
            return switch (size) {
                case 1 -> U.getByte(off);
                case 2 -> U.getShort(off);
                case 4 -> U.getInt(off);
                default -> U.getLong(off);
            };
        }
        if (o.k.comp == null) return o.p[(int) (off >> 1)];
        int i = (int) off - BASE;
        Object a = o.x;
        if (a instanceof byte[] v) {
            return switch (size) {
                case 1 -> v[i];
                case 2 -> (short) B_SHORT.get(v, i);
                case 4 -> (int) B_INT.get(v, i);
                default -> (long) B_LONG.get(v, i);
            };
        }
        if (a instanceof int[] v && size == 4) return v[i >> 2];
        if (a instanceof long[] v && size == 8) return v[i >> 3];
        if (a instanceof char[] v && size == 2) return v[i >> 1];
        if (a instanceof short[] v && size == 2) return v[i >> 1];
        if (a instanceof float[] v && size == 4) return Float.floatToRawIntBits(v[i >> 2]);
        if (a instanceof double[] v && size == 8) return Double.doubleToRawLongBits(v[i >> 3]);
        if (a instanceof char[] v && size == 8) {
            long r = 0;
            for (int j = 0; j < 4; j++) r |= (long) v[(i >> 1) + j] << (16 * j);
            return r;
        }
        if (a instanceof int[] v && size == 8) return v[i >> 2] & 0xffffffffL | (long) v[(i >> 2) + 1] << 32;
        throw new InternalError("unsafe get " + o.k.name + " size " + size);
    }

    static void put(Obj o, long off, int size, long x) {
        if (o == null) {
            switch (size) {
                case 1 -> U.putByte(off, (byte) x);
                case 2 -> U.putShort(off, (short) x);
                case 4 -> U.putInt(off, (int) x);
                default -> U.putLong(off, x);
            }
            return;
        }
        if (o.k.comp == null) { o.p[(int) (off >> 1)] = x; return; }
        int i = (int) off - BASE;
        Object a = o.x;
        if (a instanceof byte[] v) {
            switch (size) {
                case 1 -> v[i] = (byte) x;
                case 2 -> B_SHORT.set(v, i, (short) x);
                case 4 -> B_INT.set(v, i, (int) x);
                default -> B_LONG.set(v, i, x);
            }
        }
        else if (a instanceof int[] v && size == 4) v[i >> 2] = (int) x;
        else if (a instanceof long[] v && size == 8) v[i >> 3] = x;
        else if (a instanceof char[] v && size == 2) v[i >> 1] = (char) x;
        else if (a instanceof short[] v && size == 2) v[i >> 1] = (short) x;
        else if (a instanceof float[] v && size == 4) v[i >> 2] = Float.intBitsToFloat((int) x);
        else if (a instanceof double[] v && size == 8) v[i >> 3] = Double.longBitsToDouble(x);
        else throw new InternalError("unsafe put " + o.k.name + " size " + size);
    }

    static Obj getRef(Obj o, long off) {
        return o.k.comp == null ? o.r[(int) (off >> 1)] : ((Obj[]) o.x)[(int) (off - BASE) >> 2];
    }

    static void putRef(Obj o, long off, Obj v) {
        if (o.k.comp == null) o.r[(int) (off >> 1)] = v;
        else ((Obj[]) o.x)[(int) (off - BASE) >> 2] = v;
    }

    static boolean cas(Obj o, long off, int size, long e, long x) {
        if (o.k.comp == null) return LONGS.compareAndSet(o.p, (int) (off >> 1), e, x);
        int i = (int) off - BASE;
        if (o.x instanceof int[] v) return INTS.compareAndSet(v, i >> 2, (int) e, (int) x);
        if (o.x instanceof long[] v) return LONGS.compareAndSet(v, i >> 3, e, x);
        synchronized (o.x) {
            if (get(o, off, size) != e) return false;
            put(o, off, size, x);
            return true;
        }
    }

    static boolean casRef(Obj o, long off, Obj e, Obj x) {
        return o.k.comp == null ? OBJS.compareAndSet(o.r, (int) (off >> 1), e, x) : OBJS.compareAndSet((Obj[]) o.x, (int) (off - BASE) >> 2, e, x);
    }

    static void init() {
        String u = "jdk/internal/misc/Unsafe.";
        String[][] types = {{"Boolean", "Z", "1"}, {"Byte", "B", "1"}, {"Short", "S", "2"}, {"Char", "C", "2"}, {"Int", "I", "4"}, {"Float", "F", "4"}, {"Long", "J", "8"}, {"Double", "D", "8"}};
        for (String[] ty : types) {
            int size = Integer.parseInt(ty[2]);
            char c = ty[1].charAt(0);
            Native g = (t, b) -> {
                long v = get(t.R[b + 1], t.P[b + 2], size);
                t.P[b] = switch (c) { case 'Z' -> v & 1; case 'B' -> (byte) v; case 'S' -> (short) v; case 'C' -> (char) v; case 'I', 'F' -> (int) v; default -> v; };
            };
            Native p = (t, b) -> put(t.R[b + 1], t.P[b + 2], size, c == 'J' || c == 'D' ? t.P[b + 4] : Interp.narrow(c, (int) t.P[b + 4]));
            for (String s : new String[]{"", "Volatile", "Acquire", "Opaque"}) {
                Natives.reg(u + "get" + ty[0] + s + "(Ljava/lang/Object;J)" + ty[1], g);
            }
            for (String s : new String[]{"", "Volatile", "Release", "Opaque"}) {
                Natives.reg(u + "put" + ty[0] + s + "(Ljava/lang/Object;J" + ty[1] + ")V", p);
            }
        }
        Native gr = (t, b) -> t.R[b] = getRef(t.R[b + 1], t.P[b + 2]);
        Native pr = (t, b) -> putRef(t.R[b + 1], t.P[b + 2], t.R[b + 4]);
        for (String s : new String[]{"", "Volatile", "Acquire", "Opaque"}) Natives.reg(u + "getReference" + s, gr);
        for (String s : new String[]{"", "Volatile", "Release", "Opaque"}) Natives.reg(u + "putReference" + s, pr);

        Natives.reg(u + "compareAndSetInt", (t, b) -> t.P[b] = cas(t.R[b + 1], t.P[b + 2], 4, (int) t.P[b + 4], (int) t.P[b + 5]) ? 1 : 0);
        Natives.reg(u + "compareAndSetLong", (t, b) -> t.P[b] = cas(t.R[b + 1], t.P[b + 2], 8, t.P[b + 4], t.P[b + 6]) ? 1 : 0);
        Natives.reg(u + "compareAndSetReference", (t, b) -> t.P[b] = casRef(t.R[b + 1], t.P[b + 2], t.R[b + 4], t.R[b + 5]) ? 1 : 0);
        Natives.reg(u + "compareAndExchangeInt", (t, b) -> {
            Obj o = t.R[b + 1];
            long off = t.P[b + 2];
            for (;;) {
                long v = (int) get(o, off, 4);
                if (v != (int) t.P[b + 4] || cas(o, off, 4, v, (int) t.P[b + 5])) { t.P[b] = v; return; }
            }
        });
        Natives.reg(u + "compareAndExchangeLong", (t, b) -> {
            Obj o = t.R[b + 1];
            long off = t.P[b + 2];
            for (;;) {
                long v = get(o, off, 8);
                if (v != t.P[b + 4] || cas(o, off, 8, v, t.P[b + 6])) { t.P[b] = v; return; }
            }
        });
        Natives.reg(u + "compareAndExchangeReference", (t, b) -> {
            Obj o = t.R[b + 1];
            long off = t.P[b + 2];
            for (;;) {
                Obj v = getRef(o, off);
                if (v != t.R[b + 4] || casRef(o, off, v, t.R[b + 5])) { t.R[b] = v; return; }
            }
        });

        Natives.reg(u + "compareAndExchangeByte(Ljava/lang/Object;JBB)B", (t, b) -> {
            Obj o = t.R[b + 1];
            long off = t.P[b + 2];
            for (;;) {
                long v = (byte) get(o, off, 1);
                if (v != (byte) t.P[b + 4] || cas(o, off, 1, v, (byte) t.P[b + 5])) { t.P[b] = v; return; }
            }
        });
        Natives.reg(u + "compareAndExchangeShort(Ljava/lang/Object;JSS)S", (t, b) -> {
            Obj o = t.R[b + 1];
            long off = t.P[b + 2];
            for (;;) {
                long v = (short) get(o, off, 2);
                if (v != (short) t.P[b + 4] || cas(o, off, 2, v, (short) t.P[b + 5])) { t.P[b] = v; return; }
            }
        });
        Natives.reg(u + "objectFieldOffset0", (t, b) -> t.P[b] = Reflect.field(t.R[b + 1]).offset());
        Natives.reg(u + "objectFieldOffset1", (t, b) -> {
            String n = VM.str(t.R[b + 2]);
            for (Klass k = VM.klass(t.R[b + 1]); k != null; k = k.sup)
                for (Field f : k.fields) if (!f.isStatic() && f.name.equals(n)) { t.P[b] = f.offset(); return; }
            throw VM.thrown(t, "java/lang/InternalError", n);
        });
        Natives.reg(u + "staticFieldOffset0", (t, b) -> t.P[b] = Reflect.field(t.R[b + 1]).offset());
        Natives.reg(u + "staticFieldBase0", (t, b) -> t.R[b] = Reflect.field(t.R[b + 1]).klass.statics);
        Natives.reg(u + "shouldBeInitialized0", (t, b) -> t.P[b] = VM.klass(t.R[b + 1]).state != Klass.INITED ? 1 : 0);
        Natives.reg(u + "ensureClassInitialized0", (t, b) -> VM.init(t, VM.klass(t.R[b + 1])));
        Natives.reg(u + "arrayBaseOffset0", (t, b) -> t.P[b] = BASE);
        Natives.reg(u + "arrayIndexScale0", (t, b) -> t.P[b] = scale(VM.klass(t.R[b + 1])));
        Natives.reg(u + "allocateInstance", (t, b) -> {
            Klass k = VM.klass(t.R[b + 1]);
            VM.init(t, k);
            t.R[b] = k.alloc();
        });
        Natives.reg(u + "throwException", (t, b) -> { throw new Throw(t.R[b + 1]); });
        Natives.reg(u + "allocateMemory0", (t, b) -> t.P[b] = U.allocateMemory(t.P[b + 1]));
        Natives.reg(u + "reallocateMemory0", (t, b) -> t.P[b] = U.reallocateMemory(t.P[b + 1], t.P[b + 3]));
        Natives.reg(u + "freeMemory0", (t, b) -> U.freeMemory(t.P[b + 1]));
        Natives.reg(u + "setMemory0", (t, b) -> {
            Obj o = t.R[b + 1];
            long off = t.P[b + 2], n = t.P[b + 4];
            if (o == null) U.setMemory(off, n, (byte) t.P[b + 6]);
            else for (long i = 0; i < n; i++) put(o, off + i, 1, t.P[b + 6]);
        });
        Natives.reg(u + "copyMemory0", (t, b) -> {
            Obj s = t.R[b + 1], d = t.R[b + 4];
            long so = t.P[b + 2], dof = t.P[b + 5], n = t.P[b + 7];
            if (s != null && d != null && s.x != null && d.x != null && s.x.getClass() == d.x.getClass()) {
                int sc = scale(s.k);
                System.arraycopy(s.x, (int) (so - BASE) / sc, d.x, (int) (dof - BASE) / sc, (int) n / sc);
            } else if (s == null && d == null) U.copyMemory(so, dof, n);
            else if (s != null && s.x instanceof byte[] v && d == null) U.copyMemory(v, sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET + so - BASE, null, dof, n);
            else if (d != null && d.x instanceof byte[] v && s == null) U.copyMemory(null, so, v, sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET + dof - BASE, n);
            else for (long i = 0; i < n; i++) put(d, dof + i, 1, get(s, so + i, 1));
        });
        Natives.reg(u + "loadFence", Natives.NOP);
        Natives.reg(u + "storeFence", Natives.NOP);
        Natives.reg(u + "fullFence", Natives.NOP);
        Natives.reg(u + "park", (t, b) -> {
            boolean abs = t.P[b + 1] != 0;
            long time = t.P[b + 2];
            if (abs) LockSupport.parkUntil(time);
            else if (time == 0) LockSupport.park();
            else LockSupport.parkNanos(time);
        });
        Natives.reg(u + "unpark", (t, b) -> {
            Obj th = t.R[b + 1];
            if (th != null && th.x instanceof T o && o.host != null) LockSupport.unpark(o.host);
        });
        Natives.reg(u + "defineClass0", (t, b) -> {
            byte[] d = (byte[]) t.R[b + 2].x;
            int off = (int) t.P[b + 3], len = (int) t.P[b + 4];
            Klass k = VM.define(t, VM.str(t.R[b + 1]) == null ? null : VM.str(t.R[b + 1]).replace('.', '/'), java.util.Arrays.copyOfRange(d, off, off + len), t.R[b + 5], t.R[b + 6], false);
            t.R[b] = VM.mirror(k);
        });
        Natives.reg("jdk/internal/util/ArraysSupport.vectorizedMismatch(Ljava/lang/Object;JLjava/lang/Object;JII)I", (t, b) -> t.P[b] = ~(int) t.P[b + 6]);
    }

    static int length(Obj a) { return Array.getLength(a.x); }
}
