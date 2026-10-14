package jvm;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

interface Native {
    void call(T t, int b);
}

final class Field {
    Klass klass;
    String name, desc;
    int flags, slot, cv;
    char type;
    boolean ref, wide;
    HashMap<String, byte[]> attrs;

    boolean isStatic() { return (flags & 8) != 0; }
    long offset() { return ((long) slot << 1) | (ref ? 1 : 0); }
}

final class Method {
    Klass klass;
    String name, desc;
    int flags, maxStack, maxLocals, argSlots, retSlots, vindex = -1, slot;
    byte[] code;
    int[] ex = {}, lines = {};
    char ret;
    boolean intrinsic;
    Native nat;
    HashMap<String, byte[]> attrs;
    Klass[] ptypes;
    Klass rtype;

    boolean isStatic() { return (flags & 8) != 0; }
    boolean isNative() { return (flags & 0x100) != 0; }
    boolean isAbstract() { return (flags & 0x400) != 0; }
    boolean isPrivate() { return (flags & 2) != 0; }
    boolean isInit() { return name.charAt(0) == '<'; }

    int line(int pc) {
        int l = -1;
        for (int i = 0; i < lines.length; i += 2) if (lines[i] <= pc) l = lines[i + 1];
        return l;
    }

    public String toString() { return klass.name + "." + name + desc; }
}

final class Klass {
    static final int LOADED = 0, INITING = 1, INITED = 2, FAILED = 3;
    static final long[] NOP = {};
    static final Obj[] NOR = {};

    String name;
    int flags, major;
    Klass sup, comp, host;
    Klass[] ifaces = {};
    Obj loader, mirror, statics, pd, classData;
    char prim;
    boolean hidden;

    byte[] tag;
    int[] ca, cb;
    long[] num;
    String[] utf;
    Object[] res;
    int supIdx;
    int[][] bsm;
    int[] ifIdx = {};

    Field[] fields = {};
    Method[] methods = {};
    HashMap<String, Method> mmap = new HashMap<>();
    HashMap<String, Field> fmap = new HashMap<>();
    HashMap<Method, Method> icache = new HashMap<>();
    HashMap<String, byte[]> attrs = new HashMap<>();
    Method[] vtable = {};
    Klass[] display;
    HashSet<Klass> all = new HashSet<>();
    int nprim, nref, snprim, snref, depth;
    volatile int state;
    T initBy;
    Klass arr;

    boolean isInterface() { return (flags & 0x200) != 0; }
    boolean isArray() { return comp != null; }

    static Klass parse(byte[] b) {
        try {
            return new Klass().read(new DataInputStream(new ByteArrayInputStream(b)));
        } catch (IOException e) {
            throw new ClassFormatError(e.toString());
        }
    }

    private Klass read(DataInputStream in) throws IOException {
        if (in.readInt() != 0xCAFEBABE) throw new ClassFormatError("magic");
        in.readUnsignedShort();
        major = in.readUnsignedShort();
        int n = in.readUnsignedShort();
        tag = new byte[n];
        ca = new int[n];
        cb = new int[n];
        num = new long[n];
        utf = new String[n];
        res = new Object[n];
        for (int i = 1; i < n; i++) {
            switch (tag[i] = in.readByte()) {
                case 1 -> utf[i] = in.readUTF();
                case 3, 4 -> num[i] = in.readInt();
                case 5, 6 -> num[i++] = in.readLong();
                case 7, 8, 16, 19, 20 -> ca[i] = in.readUnsignedShort();
                case 9, 10, 11, 12, 17, 18 -> { ca[i] = in.readUnsignedShort(); cb[i] = in.readUnsignedShort(); }
                case 15 -> { ca[i] = in.readUnsignedByte(); cb[i] = in.readUnsignedShort(); }
                default -> throw new ClassFormatError("constant tag " + tag[i]);
            }
        }
        flags = in.readUnsignedShort();
        name = utf[ca[in.readUnsignedShort()]];
        supIdx = in.readUnsignedShort();
        ifIdx = new int[in.readUnsignedShort()];
        for (int i = 0; i < ifIdx.length; i++) ifIdx[i] = in.readUnsignedShort();

        fields = new Field[in.readUnsignedShort()];
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i] = new Field();
            f.klass = this;
            f.flags = in.readUnsignedShort();
            f.name = utf[in.readUnsignedShort()];
            f.desc = utf[in.readUnsignedShort()];
            f.attrs = attrs(in);
            f.type = f.desc.charAt(0);
            f.ref = f.type == 'L' || f.type == '[';
            f.wide = f.type == 'J' || f.type == 'D';
            f.slot = i;
            byte[] cv = f.attrs.get("ConstantValue");
            if (cv != null) f.cv = (cv[0] & 0xff) << 8 | cv[1] & 0xff;
            fmap.put(f.name + ":" + f.desc, f);
        }

        methods = new Method[in.readUnsignedShort()];
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i] = new Method();
            m.klass = this;
            m.slot = i;
            m.flags = in.readUnsignedShort();
            m.name = utf[in.readUnsignedShort()];
            m.desc = utf[in.readUnsignedShort()];
            m.attrs = attrs(in);
            m.argSlots = argSlots(m.desc) + (m.isStatic() ? 0 : 1);
            m.ret = m.desc.charAt(m.desc.indexOf(')') + 1);
            m.retSlots = m.ret == 'V' ? 0 : m.ret == 'J' || m.ret == 'D' ? 2 : 1;
            byte[] code = m.attrs.remove("Code");
            if (code != null) code(m, code);
            mmap.put(m.name + m.desc, m);
        }
        attrs = attrs(in);
        return this;
    }

    private HashMap<String, byte[]> attrs(DataInputStream in) throws IOException {
        int n = in.readUnsignedShort();
        HashMap<String, byte[]> m = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            String a = utf[in.readUnsignedShort()];
            byte[] d = new byte[in.readInt()];
            in.readFully(d);
            m.put(a, d);
        }
        return m;
    }

    private void code(Method m, byte[] b) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
        m.maxStack = in.readUnsignedShort();
        m.maxLocals = in.readUnsignedShort();
        m.code = new byte[in.readInt()];
        in.readFully(m.code);
        m.ex = new int[in.readUnsignedShort() * 4];
        for (int i = 0; i < m.ex.length; i++) m.ex[i] = in.readUnsignedShort();
        byte[] ln = attrs(in).get("LineNumberTable");
        if (ln != null) {
            m.lines = new int[(ln.length - 2) / 2];
            for (int i = 0; i < m.lines.length; i++) m.lines[i] = (ln[2 + i * 2] & 0xff) << 8 | ln[3 + i * 2] & 0xff;
        }
    }

    static int argSlots(String d) {
        int n = 0;
        for (int i = 1; d.charAt(i) != ')'; i++) {
            char c = d.charAt(i);
            n += c == 'J' || c == 'D' ? 2 : 1;
            while (d.charAt(i) == '[') i++;
            if (d.charAt(i) == 'L') i = d.indexOf(';', i);
        }
        return n;
    }

    static ArrayList<String> params(String d) {
        ArrayList<String> out = new ArrayList<>();
        for (int i = 1; d.charAt(i) != ')'; i++) {
            int s = i;
            while (d.charAt(i) == '[') i++;
            if (d.charAt(i) == 'L') i = d.indexOf(';', i);
            out.add(d.substring(s, i + 1));
        }
        return out;
    }

    void link() {
        if (sup != null) {
            nprim = sup.nprim;
            nref = sup.nref;
            all.addAll(sup.all);
            depth = sup.depth + 1;
        }
        for (Klass i : ifaces) {
            all.add(i);
            all.addAll(i.all);
        }
        display = new Klass[depth + 1];
        if (sup != null) System.arraycopy(sup.display, 0, display, 0, depth);
        display[depth] = this;

        for (Field f : fields) {
            if (f.isStatic()) f.slot = f.ref ? snref++ : snprim++;
            else f.slot = f.ref ? nref++ : nprim++;
        }
        statics = new Obj(this, snprim == 0 ? NOP : new long[snprim], snref == 0 ? NOR : new Obj[snref]);

        if (isInterface()) return;
        ArrayList<Method> vt = new ArrayList<>();
        if (sup != null) for (Method m : sup.vtable) vt.add(m);
        for (Method m : methods) {
            if (m.isStatic() || m.isPrivate() || m.isInit()) continue;
            // package-private methods also override across packages here, the spec checks the package
            for (int i = 0; i < vt.size(); i++) {
                Method s = vt.get(i);
                if (s.name.equals(m.name) && s.desc.equals(m.desc)) { m.vindex = i; break; }
            }
            if (m.vindex < 0) { m.vindex = vt.size(); vt.add(m); } else vt.set(m.vindex, m);
        }
        vtable = vt.toArray(new Method[0]);
    }

    Obj alloc() {
        return new Obj(this, nprim == 0 ? NOP : new long[nprim], nref == 0 ? NOR : new Obj[nref]);
    }

    boolean is(Klass t) {
        if (this == t) return true;
        if (t.isInterface()) return all.contains(t);
        if (t.comp != null) {
            if (comp == null) return false;
            if (t.comp.prim != 0 || comp.prim != 0) return t.comp == comp;
            return comp.is(t.comp);
        }
        return t.depth < display.length && display[t.depth] == t;
    }

    Field field(String name, String desc) {
        Field f = fmap.get(name + ":" + desc);
        if (f != null) return f;
        for (Klass i : ifaces) if ((f = i.field(name, desc)) != null) return f;
        return sup == null ? null : sup.field(name, desc);
    }

    Field field(String name) {
        for (Klass c = this; c != null; c = c.sup) for (Field f : c.fields) if (f.name.equals(name)) return f;
        throw new InternalError(this.name + "." + name);
    }

    Method method(String name, String desc) {
        String key = name + desc;
        for (Klass c = this; c != null; c = c.sup) {
            Method m = c.mmap.get(key);
            if (m != null) return m;
        }
        return imethod(key);
    }

    private Method imethod(String key) {
        Method best = null;
        for (Klass i : all) {
            Method m = i.mmap.get(key);
            if (m == null || m.isStatic() || m.isPrivate()) continue;
            if (best == null || best.isAbstract() && !m.isAbstract() || m.isAbstract() == best.isAbstract() && i.all.contains(best.klass)) best = m;
        }
        return best;
    }

    Method select(Method rm) {
        Method m = icache.get(rm);
        if (m != null) return m;
        String key = rm.name + rm.desc;
        for (Klass c = this; c != null && m == null; c = c.sup) {
            m = c.mmap.get(key);
            if (m != null && (m.isStatic() || m.isPrivate() && m != rm)) m = null;
        }
        if (m == null) m = imethod(key);
        if (m == null) m = rm;
        synchronized (icache) { icache.put(rm, m); }
        return m;
    }

    String className(int i) { return utf[ca[i]]; }

    public String toString() { return name; }
}
