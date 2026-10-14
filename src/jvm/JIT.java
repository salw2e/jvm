package jvm;

import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// translates often-called guest methods into host bytecode so the host JVM compiles them to machine code
final class JIT {
    interface Code {
        void call(T t, long[] P, Obj[] R, int b);
    }

    static final int THRESHOLD = Integer.parseInt(System.getenv().getOrDefault("JVM_JIT", "1000"));
    static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    static int count;
    static final HashMap<Method, String> names = new HashMap<>();

    static final class Bail extends RuntimeException {
        Bail(String m) { super(m, null, false, false); }
    }

    // helpers called from compiled code

    static void init(T t, Klass k) { VM.init(t, k); }

    static Obj alloc(T t, Klass k) {
        if (k.state != Klass.INITED) VM.init(t, k);
        return k.alloc();
    }

    static Obj newarray(int n, int type) { return VM.newArray(type, n); }

    static Obj anewarray(int n, Klass k) { return new Obj(VM.arrayOf(k), new Obj[n]); }

    static int len(Obj a) { return java.lang.reflect.Array.getLength(a.x); }

    static RuntimeException athrow(Obj e) {
        if (e == null) throw new NullPointerException();
        return new Throw(e);
    }

    static Obj checkcast(Obj o, T t, Klass to) {
        if (o != null && o.k != to && !o.k.is(to))
            throw VM.thrown(t, "java/lang/ClassCastException", "class " + VM.dots(o.k) + " cannot be cast to class " + VM.dots(to));
        return o;
    }

    static int isInstance(Obj o, Klass to) { return o != null && o.k.is(to) ? 1 : 0; }

    static void aastore(Obj a, int i, Obj v, T t) {
        Obj[] d = (Obj[]) a.x;
        if (v != null && !v.k.is(a.k.comp)) {
            if (i < 0 || i >= d.length) throw new ArrayIndexOutOfBoundsException("Index " + i + " out of bounds for length " + d.length);
            throw VM.thrown(t, "java/lang/ArrayStoreException", v.k.name.replace('/', '.'));
        }
        d[i] = v;
    }

    static RuntimeException overflow(T t) { return VM.overflow(t); }

    static int dynamic(T t, Klass k, int i, int sp) {
        t.top = sp + 4;
        return Invoke.dynamic(t, k, i, sp);
    }

    static void invoke(T t, Method r, int kind, int b) {
        t.top = b + r.argSlots + 2;
        Method m = r;
        if (kind >= 2) {
            Klass rk = t.R[b].k;
            m = r.vindex >= 0 ? rk.vtable[r.vindex] : r.isPrivate() || kind == 2 && !r.klass.isInterface() ? r : rk.select(r);
        } else if (kind == 1) {
            if (t.R[b] == null) throw new NullPointerException();
        } else if (r.klass.state != Klass.INITED) VM.init(t, r.klass);
        Interp.invoke(t, m, b);
    }

    // compiling

    static boolean eligible(Method m) {
        return m.code != null && m.ex.length == 0 && (m.flags & 0x20) == 0 && !m.nojit && m.code.length < 4000;
    }

    static synchronized void compile(T t, Method m) {
        if (m.jit != null || m.nojit) return;
        if (!eligible(m)) { m.nojit = true; return; }
        m.nojit = true;
        try {
            Gen g = new Gen(t, m);
            byte[] cls = g.build();
            if (System.getenv("JVM_JIT_DUMP") != null) java.nio.file.Files.write(java.nio.file.Path.of(System.getenv("JVM_JIT_DUMP"), g.name.substring(4) + ".class"), cls);
            Class<?> c = LOOKUP.defineClass(cls);
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                f.set(null, Gen.pending.get(c.getName()).get(Integer.parseInt(f.getName().substring(1))));
            }
            Gen.pending.remove(c.getName());
            m.jit = (Code) c.getDeclaredConstructor().newInstance();
            m.nojit = false;
            if (VM.debug) System.err.println("jit " + g.name + " " + m);
        } catch (Bail | Throw e) {
            if (VM.debug) System.err.println("jit skip " + m + ": " + e.getMessage());
        } catch (Throwable e) {
            if (VM.debug) { System.err.println("jit fail " + m); e.printStackTrace(); }
        }
    }

    static final class Gen {
        static final HashMap<String, List<Object>> pending = new HashMap<>();
        final T t;
        final Method m;
        final Klass k;
        final String name;
        final Pool cp = new Pool();
        final List<Object> consts = new ArrayList<>();
        final List<String> constDesc = new ArrayList<>();
        final Buf code = new Buf();
        final int[] map;
        final List<int[]> fixups = new ArrayList<>();
        final int tl, dl, topl, tmpI, tmpL, tmpF, tmpD, tmpA, spill;
        int maxSpill;

        Gen(T t, Method m) {
            this.t = t;
            this.m = m;
            this.k = m.klass;
            this.name = "jvm/J" + (++count);
            map = new int[m.code.length + 1];
            int n = m.maxLocals + 1;
            tl = m.argSlots;
            dl = n++;
            topl = n++;
            tmpI = n++;
            tmpL = n; n += 2;
            tmpF = n++;
            tmpD = n; n += 2;
            tmpA = n++;
            spill = n;
            synchronized (JIT.class) { names.put(m, name); }
        }

        int local(int l) { return l < m.argSlots ? l : l + 1; }

        static String type(char c) { return c == 'L' || c == '[' ? "Ljvm/Obj;" : String.valueOf(c); }

        static String hostDesc(String d) {
            StringBuilder s = new StringBuilder("(");
            for (String p : Klass.params(d)) s.append(type(p.charAt(0)));
            s.append("Ljvm/T;)");
            return s.append(type(d.charAt(d.indexOf(')') + 1))).toString();
        }

        String hostDesc() {
            String d = m.desc;
            StringBuilder s = new StringBuilder("(");
            if (!m.isStatic()) s.append("Ljvm/Obj;");
            for (String p : Klass.params(d)) s.append(type(p.charAt(0)));
            s.append("Ljvm/T;)");
            return s.append(type(d.charAt(d.indexOf(')') + 1))).toString();
        }

        int constant(Object o, String desc) {
            consts.add(o);
            constDesc.add(desc);
            return cp.field(name, "c" + (consts.size() - 1), desc);
        }

        void getConst(Object o, String desc) { code.op(178).u2(constant(o, desc)); }

        void loadLocal(char ty, int l) {
            int op = switch (ty) { case 'J' -> 22; case 'F' -> 23; case 'D' -> 24; case 'A' -> 25; default -> 21; };
            if (l < 256) code.op(op).u1(l); else code.op(196).op(op).u2(l);
        }

        void storeLocal(char ty, int l) {
            int op = switch (ty) { case 'J' -> 55; case 'F' -> 56; case 'D' -> 57; case 'A' -> 58; default -> 54; };
            if (l < 256) code.op(op).u1(l); else code.op(196).op(op).u2(l);
        }

        void push(int v) {
            if (v >= -1 && v <= 5) code.op(3 + v);
            else if (v >= -128 && v < 128) code.op(16).u1(v);
            else if (v >= -32768 && v < 32768) code.op(17).u2(v);
            else code.op(19).u2(cp.integer(v));
        }

        // long on the host stack -> value of guest type ty
        void fromLong(char ty) {
            switch (ty) {
                case 'J' -> { }
                case 'F' -> code.op(136).op(184).u2(cp.method("java/lang/Float", "intBitsToFloat", "(I)F"));
                case 'D' -> code.op(184).u2(cp.method("java/lang/Double", "longBitsToDouble", "(J)D"));
                default -> code.op(136);
            }
        }

        // value of guest type ty on the host stack -> long
        void toLong(char ty) {
            switch (ty) {
                case 'J' -> { }
                case 'F' -> code.op(184).u2(cp.method("java/lang/Float", "floatToRawIntBits", "(F)I")).op(133);
                case 'D' -> code.op(184).u2(cp.method("java/lang/Double", "doubleToRawLongBits", "(D)J"));
                default -> code.op(133);
            }
        }

        static char kind(char c) { return c == 'L' || c == '[' ? 'A' : c == 'Z' || c == 'B' || c == 'C' || c == 'S' ? 'I' : c; }

        void getField(Field f) {
            if (f.ref) code.op(180).u2(cp.field("jvm/Obj", "r", "[Ljvm/Obj;"));
            else code.op(180).u2(cp.field("jvm/Obj", "p", "[J"));
            push(f.slot);
            if (f.ref) code.op(50);
            else { code.op(47); fromLong(f.type); }
        }

        // stack: holder value -> (empty)
        void putField(Field f) {
            char ty = kind(f.type);
            if (f.ref) {
                storeLocal('A', tmpA);
                code.op(180).u2(cp.field("jvm/Obj", "r", "[Ljvm/Obj;"));
                push(f.slot);
                loadLocal('A', tmpA);
                code.op(83);
            } else {
                if (ty == 'I' && f.type != 'I') narrow(f.type);
                toLong(ty);
                storeLocal('J', tmpL);
                code.op(180).u2(cp.field("jvm/Obj", "p", "[J"));
                push(f.slot);
                loadLocal('J', tmpL);
                code.op(80);
            }
        }

        void narrow(char c) {
            switch (c) {
                case 'B' -> code.op(145);
                case 'C' -> code.op(146);
                case 'S' -> code.op(147);
                case 'Z' -> { code.op(4); code.op(126); }
                default -> { }
            }
        }

        void call(String owner, String nm, String desc) { code.op(184).u2(cp.method(owner, nm, desc)); }

        // spill the call's arguments into t.P / t.R at the method's top slot, call, read the result back
        void spillCall(List<String> params, boolean receiver, String ret, Runnable invoke) {
            List<Character> kinds = new ArrayList<>();
            if (receiver) kinds.add('A');
            for (String p : params) kinds.add(kind(p.charAt(0)));
            int[] at = new int[kinds.size()];
            int slot = 0, tmp = spill;
            int[] tmps = new int[kinds.size()];
            for (int i = 0; i < kinds.size(); i++) {
                at[i] = slot;
                char c = kinds.get(i);
                slot += c == 'J' || c == 'D' ? 2 : 1;
                tmps[i] = tmp;
                tmp += c == 'J' || c == 'D' ? 2 : 1;
            }
            maxSpill = Math.max(maxSpill, tmp - spill);
            for (int i = kinds.size() - 1; i >= 0; i--) storeLocal(kinds.get(i), tmps[i]);
            for (int i = 0; i < kinds.size(); i++) {
                char c = kinds.get(i);
                loadLocal('A', tl);
                if (c == 'A') code.op(180).u2(cp.field("jvm/T", "R", "[Ljvm/Obj;"));
                else code.op(180).u2(cp.field("jvm/T", "P", "[J"));
                loadLocal('I', topl);
                push(at[i]);
                code.op(96);
                loadLocal(c, tmps[i]);
                if (c == 'A') code.op(83);
                else { toLong(c); code.op(80); }
            }
            invoke.run();
            char r = ret.charAt(0);
            if (r != 'V') {
                loadLocal('A', tl);
                if (r == 'L' || r == '[') { code.op(180).u2(cp.field("jvm/T", "R", "[Ljvm/Obj;")); loadLocal('I', topl); code.op(50); }
                else { code.op(180).u2(cp.field("jvm/T", "P", "[J")); loadLocal('I', topl); code.op(47); fromLong(r); }
            }
            loadLocal('A', tl);
            loadLocal('I', topl);
            code.op(181).u2(cp.field("jvm/T", "top", "I"));
        }

        int u2(byte[] c, int i) { return (c[i] & 0xff) << 8 | c[i + 1] & 0xff; }

        void branch(int op, int target) {
            code.op(op);
            fixups.add(new int[]{code.len - 1, code.len, target, 2});
            code.u2(0);
        }

        byte[] build() {
            byte[] c = m.code;
            // entry: d = t.depth; overflow check; t.fm[d] = m; t.fpc[d] = 0; t.depth = d + 1; top = t.top
            loadLocal('A', tl);
            code.op(180).u2(cp.field("jvm/T", "depth", "I"));
            storeLocal('I', dl);
            loadLocal('I', dl);
            loadLocal('A', tl);
            code.op(180).u2(cp.field("jvm/T", "limit", "I"));
            int skip = code.len;
            code.op(161).u2(0);
            loadLocal('A', tl);
            call("jvm/JIT", "overflow", "(Ljvm/T;)Ljava/lang/RuntimeException;");
            code.op(191);
            code.patch2(skip + 1, code.len - skip);
            loadLocal('A', tl);
            code.op(180).u2(cp.field("jvm/T", "fm", "[Ljvm/Method;"));
            loadLocal('I', dl);
            getConst(m, "Ljvm/Method;");
            code.op(83);
            loadLocal('A', tl);
            code.op(180).u2(cp.field("jvm/T", "fpc", "[I"));
            loadLocal('I', dl);
            code.op(3).op(79);
            loadLocal('A', tl);
            loadLocal('I', dl);
            code.op(4).op(96);
            code.op(181).u2(cp.field("jvm/T", "depth", "I"));
            loadLocal('A', tl);
            code.op(180).u2(cp.field("jvm/T", "top", "I"));
            storeLocal('I', topl);

            for (int pc = 0; pc < c.length; ) {
                map[pc] = code.len;
                int op = c[pc] & 0xff, len = 1;
                switch (op) {
                    case 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 -> code.op(op);
                    case 16 -> { code.op(16).u1(c[pc + 1]); len = 2; }
                    case 17 -> { code.op(17).u1(c[pc + 1]).u1(c[pc + 2]); len = 3; }
                    case 18, 19, 20 -> {
                        int i = op == 18 ? c[pc + 1] & 0xff : u2(c, pc + 1);
                        len = op == 18 ? 2 : 3;
                        switch (k.tag[i]) {
                            case 3 -> push((int) k.num[i]);
                            case 4 -> code.op(19).u2(cp.flt((int) k.num[i]));
                            case 5 -> code.op(20).u2(cp.lng(k.num[i]));
                            case 6 -> code.op(20).u2(cp.dbl(k.num[i]));
                            case 7, 8, 15, 16 -> getConst((Obj) VM.constant(t, k, i), "Ljvm/Obj;");
                            default -> throw new Bail("ldc tag " + k.tag[i]);
                        }
                    }
                    case 21, 22, 23, 24, 25 -> { loadLocal("IJFDA".charAt(op - 21), local(c[pc + 1] & 0xff)); len = 2; }
                    case 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45 ->
                            loadLocal("IJFDA".charAt((op - 26) / 4), local((op - 26) % 4));
                    case 46, 47, 48, 49, 50, 51, 52, 53 -> {
                        code.op(95);
                        code.op(180).u2(cp.field("jvm/Obj", "x", "Ljava/lang/Object;"));
                        code.op(192).u2(cp.cls(new String[]{"[I", "[J", "[F", "[D", "[Ljvm/Obj;", "[B", "[C", "[S"}[op - 46]));
                        code.op(95);
                        code.op(op);
                    }
                    case 54, 55, 56, 57, 58 -> { storeLocal("IJFDA".charAt(op - 54), local(c[pc + 1] & 0xff)); len = 2; }
                    case 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78 ->
                            storeLocal("IJFDA".charAt((op - 59) / 4), local((op - 59) % 4));
                    case 83 -> { loadLocal('A', tl); call("jvm/JIT", "aastore", "(Ljvm/Obj;ILjvm/Obj;Ljvm/T;)V"); }
                    case 79, 80, 81, 82, 84, 85, 86 -> {
                        char ty = "IJFD?III".charAt(op - 79);
                        int tmp = ty == 'J' ? tmpL : ty == 'F' ? tmpF : ty == 'D' ? tmpD : tmpI;
                        storeLocal(ty, tmp);
                        code.op(95);
                        code.op(180).u2(cp.field("jvm/Obj", "x", "Ljava/lang/Object;"));
                        code.op(192).u2(cp.cls(new String[]{"[I", "[J", "[F", "[D", "", "[B", "[C", "[S"}[op - 79]));
                        code.op(95);
                        loadLocal(ty, tmp);
                        code.op(op);
                    }
                    case 87, 88, 89, 90, 91, 92, 93, 94, 95 -> code.op(op);
                    case 132 -> { int l = local(c[pc + 1] & 0xff); if (l < 256) code.op(132).u1(l).u1(c[pc + 2]); else code.op(196).op(132).u2(l).u2(c[pc + 2]); len = 3; }
                    case 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 198, 199 -> {
                        branch(op, pc + (short) u2(c, pc + 1));
                        len = 3;
                    }
                    case 200 -> { branch(167, pc + Interp.i4(c, pc + 1)); len = 5; }
                    case 170, 171 -> {
                        int a = (pc + 4) & ~3;
                        int at = code.len;
                        code.op(op);
                        while (code.len % 4 != 0) code.u1(0);
                        fixups.add(new int[]{at, code.len, pc + Interp.i4(c, a), 4});
                        code.u4(0);
                        if (op == 170) {
                            int lo = Interp.i4(c, a + 4), hi = Interp.i4(c, a + 8);
                            code.u4(lo).u4(hi);
                            for (int i = 0; i <= hi - lo; i++) { fixups.add(new int[]{at, code.len, pc + Interp.i4(c, a + 12 + i * 4), 4}); code.u4(0); }
                            len = a + 12 + (hi - lo + 1) * 4 - pc;
                        } else {
                            int n = Interp.i4(c, a + 4);
                            code.u4(n);
                            for (int i = 0; i < n; i++) { code.u4(Interp.i4(c, a + 8 + i * 8)); fixups.add(new int[]{at, code.len, pc + Interp.i4(c, a + 12 + i * 8), 4}); code.u4(0); }
                            len = a + 8 + n * 8 - pc;
                        }
                    }
                    case 172, 173, 174, 175, 176, 177 -> {
                        loadLocal('A', tl);
                        loadLocal('I', dl);
                        code.op(181).u2(cp.field("jvm/T", "depth", "I"));
                        code.op(op);
                    }
                    case 178, 179 -> {
                        Field f = VM.fieldAt(t, k, u2(c, pc + 1));
                        if (f.klass.state != Klass.INITED) { loadLocal('A', tl); getConst(f.klass, "Ljvm/Klass;"); call("jvm/JIT", "init", "(Ljvm/T;Ljvm/Klass;)V"); }
                        if (op == 178) { getConst(f.klass.statics, "Ljvm/Obj;"); getField(f); }
                        else {
                            char ty = kind(f.type);
                            int tmp = ty == 'A' ? tmpA : ty == 'J' ? tmpL : ty == 'F' ? tmpF : ty == 'D' ? tmpD : tmpI;
                            storeLocal(ty, tmp);
                            getConst(f.klass.statics, "Ljvm/Obj;");
                            loadLocal(ty, tmp);
                            putField(f);
                        }
                        len = 3;
                    }
                    case 180 -> { getField(VM.fieldAt(t, k, u2(c, pc + 1))); len = 3; }
                    case 181 -> { putField(VM.fieldAt(t, k, u2(c, pc + 1))); len = 3; }
                    case 182, 183, 184, 185 -> {
                        int i = u2(c, pc + 1);
                        Method r = op == 183 ? VM.specialAt(t, k, i) : VM.methodAt(t, k, i);
                        len = op == 185 ? 5 : 3;
                        String d = r.desc;
                        boolean direct = op == 184 && !r.intrinsic && r.code != null && r.ex.length == 0 && (r.flags & 0x20) == 0;
                        if (op == 184 && r.klass.state != Klass.INITED) direct = false;
                        if (direct && r != m && r.jit == null) compile(t, r);
                        String target;
                        synchronized (JIT.class) { target = r == m ? name : r.jit != null ? names.get(r) : null; }
                        if (direct && target != null) {
                            loadLocal('A', tl);
                            call(target, "m", hostDescOf(r));
                        } else {
                            int kind = op == 184 ? 0 : op == 183 ? 1 : op == 182 ? 2 : 3;
                            Method rr = r;
                            spillCall(Klass.params(d), op != 184, d.substring(d.indexOf(')') + 1), () -> {
                                loadLocal('A', tl);
                                getConst(rr, "Ljvm/Method;");
                                push(kind);
                                loadLocal('I', topl);
                                call("jvm/JIT", "invoke", "(Ljvm/T;Ljvm/Method;II)V");
                            });
                        }
                    }
                    case 186 -> {
                        int i = u2(c, pc + 1);
                        String d = k.utf[k.cb[k.cb[i]]];
                        List<String> ps = Klass.params(d);
                        int slots = Klass.argSlots(d);
                        spillCall(ps, false, d.substring(d.indexOf(')') + 1), () -> {
                            loadLocal('A', tl);
                            getConst(k, "Ljvm/Klass;");
                            push(i);
                            loadLocal('I', topl);
                            push(slots);
                            code.op(96);
                            call("jvm/JIT", "dynamic", "(Ljvm/T;Ljvm/Klass;II)I");
                            code.op(87);
                        });
                        len = 5;
                    }
                    case 187 -> { loadLocal('A', tl); getConst(VM.klassAt(t, k, u2(c, pc + 1)), "Ljvm/Klass;"); call("jvm/JIT", "alloc", "(Ljvm/T;Ljvm/Klass;)Ljvm/Obj;"); len = 3; }
                    case 188 -> { push(c[pc + 1]); call("jvm/JIT", "newarray", "(II)Ljvm/Obj;"); len = 2; }
                    case 189 -> { getConst(VM.klassAt(t, k, u2(c, pc + 1)), "Ljvm/Klass;"); call("jvm/JIT", "anewarray", "(ILjvm/Klass;)Ljvm/Obj;"); len = 3; }
                    case 190 -> call("jvm/JIT", "len", "(Ljvm/Obj;)I");
                    case 191 -> { call("jvm/JIT", "athrow", "(Ljvm/Obj;)Ljava/lang/RuntimeException;"); code.op(191); }
                    case 192 -> { loadLocal('A', tl); getConst(VM.klassAt(t, k, u2(c, pc + 1)), "Ljvm/Klass;"); call("jvm/JIT", "checkcast", "(Ljvm/Obj;Ljvm/T;Ljvm/Klass;)Ljvm/Obj;"); len = 3; }
                    case 193 -> { getConst(VM.klassAt(t, k, u2(c, pc + 1)), "Ljvm/Klass;"); call("jvm/JIT", "isInstance", "(Ljvm/Obj;Ljvm/Klass;)I"); len = 3; }
                    case 196 -> {
                        int sub = c[pc + 1] & 0xff, l = local(u2(c, pc + 2));
                        if (sub == 132) { code.op(196).op(132).u2(l).u1(c[pc + 4]).u1(c[pc + 5]); len = 6; }
                        else if (sub >= 21 && sub <= 25) { loadLocal("IJFDA".charAt(sub - 21), l); len = 4; }
                        else if (sub >= 54 && sub <= 58) { storeLocal("IJFDA".charAt(sub - 54), l); len = 4; }
                        else throw new Bail("wide " + sub);
                    }
                    default -> {
                        if (op >= 96 && op <= 152) code.op(op);
                        else throw new Bail("opcode " + op);
                    }
                }
                pc += len;
                if (code.len > 60000) throw new Bail("too big");
            }
            for (int[] f : fixups) {
                int off = map[f[2]] - f[0];
                if (f[3] == 2) {
                    if (off != (short) off) throw new Bail("branch too far");
                    code.patch2(f[1], off);
                } else code.patch4(f[1], off);
            }
            return classFile();
        }

        static String hostDescOf(Method r) {
            StringBuilder s = new StringBuilder("(");
            if (!r.isStatic()) s.append("Ljvm/Obj;");
            for (String p : Klass.params(r.desc)) s.append(type(p.charAt(0)));
            s.append("Ljvm/T;)");
            return s.append(type(r.ret)).toString();
        }

        byte[] bridge() {
            Buf b = new Buf();
            // t.top = b + argSlots + 2
            b.op(43).op(21).u1(4);
            pushTo(b, m.argSlots + 2);
            b.op(96).op(181).u2(cp.field("jvm/T", "top", "I"));
            List<String> ps = new ArrayList<>();
            if (!m.isStatic()) ps.add("L");
            ps.addAll(Klass.params(m.desc));
            int slot = 0;
            for (String p : ps) {
                char ty = kind(p.charAt(0));
                if (ty == 'A') b.op(45); else b.op(44);
                b.op(21).u1(4);
                pushTo(b, slot);
                b.op(96);
                if (ty == 'A') b.op(50);
                else {
                    b.op(47);
                    switch (ty) {
                        case 'F' -> b.op(136).op(184).u2(cp.method("java/lang/Float", "intBitsToFloat", "(I)F"));
                        case 'D' -> b.op(184).u2(cp.method("java/lang/Double", "longBitsToDouble", "(J)D"));
                        case 'I' -> b.op(136);
                        default -> { }
                    }
                }
                slot += ty == 'J' || ty == 'D' ? 2 : 1;
            }
            b.op(43);
            b.op(184).u2(cp.method(name, "m", hostDesc()));
            char r = kind(m.ret);
            if (r == 'A') { b.op(58).u1(5); b.op(45).op(21).u1(4).op(25).u1(5).op(83); }
            else if (r != 'V') {
                switch (r) {
                    case 'F' -> b.op(184).u2(cp.method("java/lang/Float", "floatToRawIntBits", "(F)I")).op(133);
                    case 'D' -> b.op(184).u2(cp.method("java/lang/Double", "doubleToRawLongBits", "(D)J"));
                    case 'I' -> b.op(133);
                    default -> { }
                }
                b.op(55).u1(5);
                b.op(44).op(21).u1(4).op(22).u1(5).op(80);
            }
            b.op(177);
            return b.bytes();
        }

        static void pushTo(Buf b, int v) {
            if (v <= 5) b.op(3 + v);
            else if (v < 128) b.op(16).u1(v);
            else b.op(17).u2(v);
        }

        byte[] classFile() {
            int thisC = cp.cls(name), superC = cp.cls("java/lang/Object"), iface = cp.cls("jvm/JIT$Code");
            int codeA = cp.utf("Code");
            byte[] main = code.bytes(), br = bridge();
            int initRef = cp.method("java/lang/Object", "<init>", "()V");
            int mName = cp.utf("m"), mDesc = cp.utf(hostDesc()), bName = cp.utf("call"), bDesc = cp.utf("(Ljvm/T;[J[Ljvm/Obj;I)V");
            int iName = cp.utf("<init>"), iDesc = cp.utf("()V");
            int[] fn = new int[consts.size()], fd = new int[consts.size()];
            for (int i = 0; i < fn.length; i++) { fn[i] = cp.utf("c" + i); fd[i] = cp.utf(constDesc.get(i)); }
            Buf o = new Buf();
            o.u4(0xCAFEBABE).u2(0).u2(49);
            o.raw(cp.bytes());
            o.u2(0x1031).u2(thisC).u2(superC).u2(1).u2(iface);
            o.u2(fn.length);
            for (int i = 0; i < fn.length; i++) o.u2(0x0008).u2(fn[i]).u2(fd[i]).u2(0);
            o.u2(3);
            o.u2(0x0001).u2(iName).u2(iDesc).u2(1);
            byte[] ic = {42, (byte) 183, (byte) (initRef >> 8), (byte) initRef, (byte) 177};
            codeAttr(o, codeA, ic, 1, 1);
            o.u2(0x0009).u2(mName).u2(mDesc).u2(1);
            codeAttr(o, codeA, main, m.maxStack + 8, spill + maxSpill + 2);
            o.u2(0x0001).u2(bName).u2(bDesc).u2(1);
            codeAttr(o, codeA, br, m.argSlots + 8, 7);
            o.u2(0);
            synchronized (Gen.class) { pending.put(name.replace('/', '.'), consts); }
            return o.bytes();
        }

        static void codeAttr(Buf o, int codeA, byte[] c, int stack, int locals) {
            o.u2(codeA).u4(12 + c.length).u2(stack).u2(locals).u4(c.length).raw(c).u2(0).u2(0);
        }
    }

    static final class Buf {
        byte[] b = new byte[256];
        int len;

        Buf u1(int v) {
            if (len == b.length) b = java.util.Arrays.copyOf(b, len * 2);
            b[len++] = (byte) v;
            return this;
        }

        Buf op(int v) { return u1(v); }
        Buf u2(int v) { return u1(v >> 8).u1(v); }
        Buf u4(int v) { return u2(v >> 16).u2(v); }
        Buf raw(byte[] d) { for (byte x : d) u1(x); return this; }
        void patch2(int at, int v) { b[at] = (byte) (v >> 8); b[at + 1] = (byte) v; }
        void patch4(int at, int v) { patch2(at, v >> 16); patch2(at + 2, v); }
        byte[] bytes() { return java.util.Arrays.copyOf(b, len); }
    }

    static final class Pool {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final HashMap<String, Integer> idx = new HashMap<>();
        int n = 1;

        int add(String key, int slots, byte[] entry) {
            Integer i = idx.get(key);
            if (i != null) return i;
            out.writeBytes(entry);
            idx.put(key, n);
            n += slots;
            return n - slots;
        }

        static byte[] b(int... v) {
            byte[] r = new byte[v.length];
            for (int i = 0; i < v.length; i++) r[i] = (byte) v[i];
            return r;
        }

        int utf(String s) {
            byte[] d = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] e = new byte[d.length + 3];
            e[0] = 1;
            e[1] = (byte) (d.length >> 8);
            e[2] = (byte) d.length;
            System.arraycopy(d, 0, e, 3, d.length);
            return add("U" + s, 1, e);
        }

        int cls(String s) { int u = utf(s); return add("C" + s, 1, b(7, u >> 8, u)); }
        int nat(String nm, String d) { int a = utf(nm), c = utf(d); return add("N" + nm + d, 1, b(12, a >> 8, a, c >> 8, c)); }
        int field(String o, String nm, String d) { int c = cls(o), t = nat(nm, d); return add("F" + o + nm + d, 1, b(9, c >> 8, c, t >> 8, t)); }
        int method(String o, String nm, String d) { int c = cls(o), t = nat(nm, d); return add("M" + o + nm + d, 1, b(10, c >> 8, c, t >> 8, t)); }
        int integer(int v) { return add("I" + v, 1, b(3, v >>> 24, v >>> 16, v >>> 8, v)); }
        int flt(int bits) { return add("F" + bits, 1, b(4, bits >>> 24, bits >>> 16, bits >>> 8, bits)); }
        int lng(long v) { return add("J" + v, 2, b(5, (int) (v >>> 56), (int) (v >>> 48), (int) (v >>> 40), (int) (v >>> 32), (int) (v >>> 24), (int) (v >>> 16), (int) (v >>> 8), (int) v)); }
        int dbl(long v) { return add("D" + v, 2, b(6, (int) (v >>> 56), (int) (v >>> 48), (int) (v >>> 40), (int) (v >>> 32), (int) (v >>> 24), (int) (v >>> 16), (int) (v >>> 8), (int) v)); }

        byte[] bytes() {
            byte[] d = out.toByteArray(), r = new byte[d.length + 2];
            r[0] = (byte) (n >> 8);
            r[1] = (byte) n;
            System.arraycopy(d, 0, r, 2, d.length);
            return r;
        }
    }
}
