package jvm;

import java.lang.reflect.Array;
import java.util.ArrayList;

final class Reflect {
    static Klass k(T t, int i) { return VM.klass(t.R[i]); }

    static Field field(Obj f) {
        Klass c = VM.klass(f.r[f.k.field("clazz").slot]);
        return c.fields[(int) f.p[f.k.field("slot").slot]];
    }

    static Method method(Obj m) {
        Klass c = VM.klass(m.r[m.k.field("clazz").slot]);
        return c.methods[(int) m.p[m.k.field("slot").slot]];
    }

    static Obj classes(T t, Klass[] ks) {
        Obj[] o = new Obj[ks.length];
        for (int i = 0; i < o.length; i++) o[i] = VM.mirror(ks[i]);
        return new Obj(VM.arrayOf(VM.CLASS), o);
    }

    static void types(T t, Method m) {
        if (m.ptypes != null) return;
        ArrayList<String> ps = Klass.params(m.desc);
        Klass[] p = new Klass[ps.size()];
        for (int i = 0; i < p.length; i++) p[i] = VM.fromDesc(t, ps.get(i), m.klass.loader);
        m.rtype = VM.fromDesc(t, m.desc.substring(m.desc.indexOf(')') + 1), m.klass.loader);
        m.ptypes = p;
    }

    static Obj bytes(byte[] b) { return b == null ? null : VM.bytes(b); }

    static String sig(Klass k, java.util.HashMap<String, byte[]> attrs) {
        byte[] s = attrs.get("Signature");
        return s == null ? null : k.utf[(s[0] & 0xff) << 8 | s[1] & 0xff];
    }

    static Klass[] exceptions(T t, Method m) {
        byte[] e = m.attrs.get("Exceptions");
        if (e == null) return new Klass[0];
        Klass[] out = new Klass[(e[0] & 0xff) << 8 | e[1] & 0xff];
        for (int i = 0; i < out.length; i++) out[i] = VM.klassAt(t, m.klass, (e[2 + i * 2] & 0xff) << 8 | e[3 + i * 2] & 0xff);
        return out;
    }

    static Obj newField(T t, Field f, int slot) {
        Klass fk = VM.load(t, "java/lang/reflect/Field", null);
        Obj o = fk.alloc();
        VM.call(t, fk.mmap.get("<init>(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;IZILjava/lang/String;[B)V"), o,
                VM.mirror(f.klass), VM.intern(f.name), VM.mirror(VM.fromDesc(t, f.desc, f.klass.loader)), f.flags & 0xffff,
                (f.flags & 0x10) != 0 && (f.isStatic() || f.klass.hidden || f.klass.attrs.containsKey("Record")), slot,
                VM.str(sig(f.klass, f.attrs)), bytes(f.attrs.get("RuntimeVisibleAnnotations")));
        return o;
    }

    static Obj newMethod(T t, Method m) {
        types(t, m);
        Klass mk = VM.load(t, "java/lang/reflect/Method", null);
        Obj o = mk.alloc();
        VM.call(t, mk.mmap.get("<init>(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;Ljava/lang/Class;[Ljava/lang/Class;IILjava/lang/String;[B[B[B)V"), o,
                VM.mirror(m.klass), VM.intern(m.name), classes(t, m.ptypes), VM.mirror(m.rtype), classes(t, exceptions(t, m)), m.flags & 0xffff, m.slot,
                VM.str(sig(m.klass, m.attrs)), bytes(m.attrs.get("RuntimeVisibleAnnotations")), bytes(m.attrs.get("RuntimeVisibleParameterAnnotations")), bytes(m.attrs.get("AnnotationDefault")));
        return o;
    }

    static Obj newCtor(T t, Method m) {
        types(t, m);
        Klass ck = VM.load(t, "java/lang/reflect/Constructor", null);
        Obj o = ck.alloc();
        VM.call(t, ck.mmap.get("<init>(Ljava/lang/Class;[Ljava/lang/Class;[Ljava/lang/Class;IILjava/lang/String;[B[B)V"), o,
                VM.mirror(m.klass), classes(t, m.ptypes), classes(t, exceptions(t, m)), m.flags & 0xffff, m.slot,
                VM.str(sig(m.klass, m.attrs)), bytes(m.attrs.get("RuntimeVisibleAnnotations")), bytes(m.attrs.get("RuntimeVisibleParameterAnnotations")));
        return o;
    }

    static int pushArgs(T t, Method m, Obj self, Obj args) {
        types(t, m);
        Obj[] a = args == null ? new Obj[0] : (Obj[]) args.x;
        if (a.length != m.ptypes.length) throw VM.thrown(t, "java/lang/IllegalArgumentException", "wrong number of arguments");
        int base = t.top, i = base;
        if (!m.isStatic()) t.R[i++] = self;
        for (int j = 0; j < a.length; j++) {
            Klass p = m.ptypes[j];
            if (p.prim == 0) {
                if (a[j] != null && !a[j].k.is(p)) throw VM.thrown(t, "java/lang/IllegalArgumentException", "argument type mismatch");
                t.R[i++] = a[j];
                continue;
            }
            if (a[j] == null) throw VM.thrown(t, "java/lang/IllegalArgumentException", "argument type mismatch");
            long v = widen(t, a[j], p.prim);
            t.P[i] = v;
            i += p.prim == 'J' || p.prim == 'D' ? 2 : 1;
        }
        return base;
    }

    static char primOf(Klass box) {
        return switch (box.name) {
            case "java/lang/Boolean" -> 'Z';
            case "java/lang/Byte" -> 'B';
            case "java/lang/Character" -> 'C';
            case "java/lang/Short" -> 'S';
            case "java/lang/Integer" -> 'I';
            case "java/lang/Long" -> 'J';
            case "java/lang/Float" -> 'F';
            case "java/lang/Double" -> 'D';
            default -> 0;
        };
    }

    static long widen(T t, Obj box, char to) {
        char from = primOf(box.k);
        long v = from == 0 ? 0 : Natives.unbox(box);
        if (from == to) return v;
        String order = "BSIJFD";
        int a = from == 'C' ? 2 : order.indexOf(from), z = order.indexOf(to);
        if (from == 0 || from == 'Z' || to == 'Z' || to == 'C' || a < 0 || z < a || from == 'C' && z < 2) throw VM.thrown(t, "java/lang/IllegalArgumentException", "argument type mismatch");
        double d = from == 'F' ? Interp.f(v) : from == 'D' ? Interp.d(v) : v;
        return switch (to) {
            case 'F' -> Interp.fb((float) d);
            case 'D' -> Interp.db(d);
            default -> v;
        };
    }

    static Obj invoke(T t, Method m, Obj self, Obj args, boolean virtual) {
        int base = pushArgs(t, m, self, args);
        if (!m.isStatic()) {
            if (self == null) throw new NullPointerException();
            if (!self.k.is(m.klass)) throw VM.thrown(t, "java/lang/IllegalArgumentException", "object is not an instance of declaring class");
            if (virtual) m = m.vindex >= 0 ? self.k.vtable[m.vindex] : m.isPrivate() ? m : self.k.select(m);
        } else VM.init(t, m.klass);
        try {
            Interp.invoke(t, m, base);
        } catch (Throw e) {
            t.top = base;
            Klass w = VM.load(t, "java/lang/reflect/InvocationTargetException", null);
            Obj o = w.alloc();
            VM.call(t, w.mmap.get("<init>(Ljava/lang/Throwable;)V"), o, e.ex);
            throw new Throw(o);
        }
        t.top = base;
        if (m.ret == 'V') return null;
        if (m.ret == 'L' || m.ret == '[') return t.R[base];
        return Natives.box(t, m.ret, t.P[base]);
    }

    static void init() {
        String c = "java/lang/Class.";
        Natives.reg(c + "getPrimitiveClass", (t, b) -> {
            String n = VM.str(t.R[b]);
            for (Klass p : VM.prims.values()) if (p.name.equals(n)) t.R[b] = VM.mirror(p);
        });
        Natives.reg(c + "desiredAssertionStatus0", (t, b) -> t.P[b] = 0);
        Natives.reg(c + "isArray", (t, b) -> t.P[b] = k(t, b).comp != null ? 1 : 0);
        Natives.reg(c + "isPrimitive", (t, b) -> t.P[b] = k(t, b).prim != 0 ? 1 : 0);
        Natives.reg(c + "isInterface", (t, b) -> t.P[b] = k(t, b).isInterface() ? 1 : 0);
        Natives.reg(c + "isHidden", (t, b) -> t.P[b] = k(t, b).hidden ? 1 : 0);
        Natives.reg(c + "isRecord0", (t, b) -> t.P[b] = k(t, b).attrs.containsKey("Record") ? 1 : 0);
        Natives.reg(c + "getModifiers", (t, b) -> {
            Klass k = k(t, b);
            int f = k.flags;
            byte[] ic = k.attrs.get("InnerClasses");
            if (ic != null) {
                for (int i = 2; i + 7 < ic.length; i += 8) {
                    int inner = (ic[i] & 0xff) << 8 | ic[i + 1] & 0xff;
                    if (k.utf[k.ca[inner]].equals(k.name)) f = (ic[i + 6] & 0xff) << 8 | ic[i + 7] & 0xff;
                }
            }
            t.P[b] = f & ~0x20 & 0x7fff;
        });
        Natives.reg(c + "getSuperclass", (t, b) -> {
            Klass k = k(t, b);
            t.R[b] = k.sup == null || k.isInterface() ? null : VM.mirror(k.sup);
        });
        Natives.reg(c + "getInterfaces0", (t, b) -> t.R[b] = classes(t, k(t, b).ifaces));
        Natives.reg(c + "initClassName", (t, b) -> {
            Klass k = k(t, b);
            Obj n = VM.intern(k.hidden ? VM.dots(k) + "/0x" + Integer.toHexString(System.identityHashCode(k)) : VM.dots(k));
            t.R[b].r[VM.CLASS.field("name").slot] = n;
            t.R[b] = n;
        });
        Natives.reg(c + "isInstance", (t, b) -> t.P[b] = t.R[b + 1] != null && t.R[b + 1].k.is(k(t, b)) ? 1 : 0);
        Natives.reg(c + "isAssignableFrom", (t, b) -> {
            Klass to = k(t, b), from = k(t, b + 1);
            t.P[b] = to.prim != 0 || from.prim != 0 ? (to == from ? 1 : 0) : from.is(to) ? 1 : 0;
        });
        Natives.reg(c + "forName0", (t, b) -> {
            String n = VM.str(t.R[b]);
            Klass k;
            try {
                k = VM.load(t, n.replace('.', '/'), t.R[b + 2]);
            } catch (Throw e) {
                if (!e.ex.k.name.equals("java/lang/NoClassDefFoundError")) throw e;
                throw VM.thrown(t, "java/lang/ClassNotFoundException", n);
            }
            if (t.P[b + 1] != 0) VM.init(t, k);
            t.R[b] = VM.mirror(k);
        });
        Natives.reg(c + "getDeclaredFields0", (t, b) -> {
            Klass k = k(t, b);
            boolean pub = t.P[b + 1] != 0;
            ArrayList<Obj> out = new ArrayList<>();
            for (int i = 0; i < k.fields.length; i++) {
                Field f = k.fields[i];
                if (pub && (f.flags & 1) == 0) continue;
                out.add(newField(t, f, i));
            }
            t.R[b] = new Obj(VM.arrayOf(VM.load(t, "java/lang/reflect/Field", null)), out.toArray(new Obj[0]));
        });
        Natives.reg(c + "getDeclaredMethods0", (t, b) -> {
            Klass k = k(t, b);
            boolean pub = t.P[b + 1] != 0;
            ArrayList<Obj> out = new ArrayList<>();
            for (Method m : k.methods) {
                if (m.isInit() || pub && (m.flags & 1) == 0) continue;
                out.add(newMethod(t, m));
            }
            t.R[b] = new Obj(VM.arrayOf(VM.load(t, "java/lang/reflect/Method", null)), out.toArray(new Obj[0]));
        });
        Natives.reg(c + "getDeclaredConstructors0", (t, b) -> {
            Klass k = k(t, b);
            boolean pub = t.P[b + 1] != 0;
            ArrayList<Obj> out = new ArrayList<>();
            for (Method m : k.methods) {
                if (!m.name.equals("<init>") || pub && (m.flags & 1) == 0) continue;
                out.add(newCtor(t, m));
            }
            t.R[b] = new Obj(VM.arrayOf(VM.load(t, "java/lang/reflect/Constructor", null)), out.toArray(new Obj[0]));
        });
        Natives.reg(c + "getDeclaredClasses0", (t, b) -> {
            Klass k = k(t, b);
            ArrayList<Klass> out = new ArrayList<>();
            byte[] ic = k.attrs.get("InnerClasses");
            if (ic != null) {
                for (int i = 2; i + 7 < ic.length; i += 8) {
                    int inner = (ic[i] & 0xff) << 8 | ic[i + 1] & 0xff, outer = (ic[i + 2] & 0xff) << 8 | ic[i + 3] & 0xff;
                    if (outer != 0 && k.utf[k.ca[outer]].equals(k.name)) out.add(VM.klassAt(t, k, inner));
                }
            }
            t.R[b] = classes(t, out.toArray(new Klass[0]));
        });
        Natives.reg(c + "getDeclaringClass0", (t, b) -> {
            Klass k = k(t, b);
            t.R[b] = null;
            byte[] ic = k.attrs.get("InnerClasses");
            if (ic == null) return;
            for (int i = 2; i + 7 < ic.length; i += 8) {
                int inner = (ic[i] & 0xff) << 8 | ic[i + 1] & 0xff, outer = (ic[i + 2] & 0xff) << 8 | ic[i + 3] & 0xff;
                if (outer != 0 && k.utf[k.ca[inner]].equals(k.name)) t.R[b] = VM.mirror(VM.klassAt(t, k, outer));
            }
        });
        Natives.reg(c + "getSimpleBinaryName0", (t, b) -> {
            Klass k = k(t, b);
            t.R[b] = null;
            byte[] ic = k.attrs.get("InnerClasses");
            if (ic == null) return;
            for (int i = 2; i + 7 < ic.length; i += 8) {
                int inner = (ic[i] & 0xff) << 8 | ic[i + 1] & 0xff, nm = (ic[i + 4] & 0xff) << 8 | ic[i + 5] & 0xff;
                if (nm != 0 && k.utf[k.ca[inner]].equals(k.name)) t.R[b] = VM.str(k.utf[nm]);
            }
        });
        Natives.reg(c + "getEnclosingMethod0", (t, b) -> {
            Klass k = k(t, b);
            t.R[b] = null;
            byte[] em = k.attrs.get("EnclosingMethod");
            if (em == null) return;
            int ci = (em[0] & 0xff) << 8 | em[1] & 0xff, mi = (em[2] & 0xff) << 8 | em[3] & 0xff;
            Obj[] o = new Obj[3];
            o[0] = VM.mirror(VM.klassAt(t, k, ci));
            if (mi != 0) { o[1] = VM.str(k.utf[k.ca[mi]]); o[2] = VM.str(k.utf[k.cb[mi]]); }
            t.R[b] = new Obj(VM.arrayOf(VM.OBJECT), o);
        });
        Natives.reg(c + "getNestHost0", (t, b) -> {
            Klass k = k(t, b);
            byte[] nh = k.attrs.get("NestHost");
            if (k.host != null) t.R[b] = VM.mirror(k.host);
            else if (nh != null) t.R[b] = VM.mirror(VM.klassAt(t, k, (nh[0] & 0xff) << 8 | nh[1] & 0xff));
        });
        Natives.reg(c + "getNestMembers0", (t, b) -> {
            Klass k = k(t, b);
            byte[] nm = k.attrs.get("NestMembers");
            ArrayList<Klass> out = new ArrayList<>();
            out.add(k);
            if (nm != null) for (int i = 2; i + 1 < nm.length; i += 2) out.add(VM.klassAt(t, k, (nm[i] & 0xff) << 8 | nm[i + 1] & 0xff));
            t.R[b] = classes(t, out.toArray(new Klass[0]));
        });
        Natives.reg(c + "getPermittedSubclasses0", (t, b) -> {
            Klass k = k(t, b);
            byte[] ps = k.attrs.get("PermittedSubclasses");
            t.R[b] = null;
            if (ps == null) return;
            Klass[] out = new Klass[(ps[0] & 0xff) << 8 | ps[1] & 0xff];
            for (int i = 0; i < out.length; i++) out[i] = VM.klassAt(t, k, (ps[2 + i * 2] & 0xff) << 8 | ps[3 + i * 2] & 0xff);
            t.R[b] = classes(t, out);
        });
        Natives.reg(c + "getRecordComponents0", (t, b) -> {
            Klass k = k(t, b);
            byte[] rc = k.attrs.get("Record");
            t.R[b] = null;
            if (rc == null) return;
            Klass rk = VM.load(t, "java/lang/reflect/RecordComponent", null);
            int n = (rc[0] & 0xff) << 8 | rc[1] & 0xff, at = 2;
            Obj[] out = new Obj[n];
            for (int i = 0; i < n; i++) {
                String name = k.utf[(rc[at] & 0xff) << 8 | rc[at + 1] & 0xff], desc = k.utf[(rc[at + 2] & 0xff) << 8 | rc[at + 3] & 0xff];
                int na = (rc[at + 4] & 0xff) << 8 | rc[at + 5] & 0xff;
                at += 6;
                Obj o = out[i] = rk.alloc();
                for (int j = 0; j < na; j++) {
                    String an = k.utf[(rc[at] & 0xff) << 8 | rc[at + 1] & 0xff];
                    int len = Interp.i4(rc, at + 2);
                    if (an.equals("Signature")) o.r[rk.field("signature").slot] = VM.str(k.utf[(rc[at + 6] & 0xff) << 8 | rc[at + 7] & 0xff]);
                    if (an.equals("RuntimeVisibleAnnotations")) o.r[rk.field("annotations").slot] = VM.bytes(java.util.Arrays.copyOfRange(rc, at + 6, at + 6 + len));
                    at += 6 + len;
                }
                o.r[rk.field("clazz").slot] = VM.mirror(k);
                o.r[rk.field("name").slot] = VM.intern(name);
                o.r[rk.field("type").slot] = VM.mirror(VM.fromDesc(t, desc, k.loader));
                Method acc = k.mmap.get(name + "()" + desc);
                if (acc != null) o.r[rk.field("accessor").slot] = newMethod(t, acc);
            }
            t.R[b] = new Obj(VM.arrayOf(rk), out);
        });
        Natives.reg(c + "getGenericSignature0", (t, b) -> t.R[b] = VM.str(sig(k(t, b), k(t, b).attrs)));
        Natives.reg(c + "getRawAnnotations", (t, b) -> t.R[b] = bytes(k(t, b).attrs.get("RuntimeVisibleAnnotations")));
        Natives.reg(c + "getRawTypeAnnotations", (t, b) -> t.R[b] = bytes(k(t, b).attrs.get("RuntimeVisibleTypeAnnotations")));
        Natives.reg(c + "getConstantPool", (t, b) -> {
            Klass cp = VM.load(t, "jdk/internal/reflect/ConstantPool", null);
            Obj o = cp.alloc();
            o.r[cp.field("constantPoolOop").slot] = t.R[b];
            t.R[b] = o;
        });
        Natives.reg(c + "getProtectionDomain0", (t, b) -> t.R[b] = k(t, b).pd);
        Natives.reg(c + "getSigners", (t, b) -> t.R[b] = null);
        Natives.reg(c + "setSigners", Natives.NOP);
        Natives.reg(c + "getClassData0", (t, b) -> t.R[b] = k(t, b).classData);

        String cp = "jdk/internal/reflect/ConstantPool.";
        Natives.reg(cp + "getSize0", (t, b) -> t.P[b] = k(t, b + 1).tag.length);
        Natives.reg(cp + "getUTF8At0", (t, b) -> t.R[b] = VM.str(k(t, b + 1).utf[(int) t.P[b + 2]]));
        Natives.reg(cp + "getIntAt0", (t, b) -> t.P[b] = (int) k(t, b + 1).num[(int) t.P[b + 2]]);
        Natives.reg(cp + "getFloatAt0", (t, b) -> t.P[b] = (int) k(t, b + 1).num[(int) t.P[b + 2]]);
        Natives.reg(cp + "getLongAt0", (t, b) -> t.P[b] = k(t, b + 1).num[(int) t.P[b + 2]]);
        Natives.reg(cp + "getDoubleAt0", (t, b) -> t.P[b] = k(t, b + 1).num[(int) t.P[b + 2]]);
        Natives.reg(cp + "getStringAt0", (t, b) -> { Klass k = k(t, b + 1); t.R[b] = VM.intern(k.utf[k.ca[(int) t.P[b + 2]]]); });
        Natives.reg(cp + "getClassAt0", (t, b) -> t.R[b] = VM.mirror(VM.klassAt(t, k(t, b + 1), (int) t.P[b + 2])));
        Natives.reg(cp + "getTagAt0", (t, b) -> t.P[b] = k(t, b + 1).tag[(int) t.P[b + 2]]);

        Natives.reg("jdk/internal/reflect/Reflection.getCallerClass()Ljava/lang/Class;", (t, b) -> {
            int d = t.depth - 3;
            while (d >= 0 && skip(t.fm[d])) d--;
            t.R[b] = d < 0 ? null : VM.mirror(t.fm[d].klass);
        });
        Natives.reg("jdk/internal/reflect/Reflection.getClassAccessFlags", (t, b) -> t.P[b] = k(t, b).flags & 0xffff);
        Natives.reg("jdk/internal/reflect/Reflection.areNestMates", (t, b) -> t.P[b] = nestHost(t, k(t, b)) == nestHost(t, k(t, b + 1)) ? 1 : 0);
        Natives.reg("jdk/internal/reflect/NativeMethodAccessorImpl.invoke0", (t, b) -> t.R[b] = invoke(t, method(t.R[b]), t.R[b + 1], t.R[b + 2], true));
        Natives.reg("jdk/internal/reflect/NativeConstructorAccessorImpl.newInstance0", (t, b) -> {
            Method m = method(t.R[b]);
            if ((m.klass.flags & 0x400) != 0) throw VM.thrown(t, "java/lang/InstantiationException", VM.dots(m.klass));
            VM.init(t, m.klass);
            Obj o = m.klass.alloc();
            int top = t.top;
            t.R[top] = o;
            t.top = top + 1;
            invoke(t, m, o, t.R[b + 1], false);
            t.R[b] = o;
        });

        String a = "java/lang/reflect/Array.";
        Natives.reg(a + "newArray", (t, b) -> {
            Klass k = k(t, b);
            if (k.prim == 'V') throw VM.thrown(t, "java/lang/IllegalArgumentException", null);
            t.R[b] = VM.newArray(k, (int) t.P[b + 1]);
        });
        Natives.reg(a + "multiNewArray", (t, b) -> {
            Klass k = k(t, b);
            int[] dims = (int[]) t.R[b + 1].x;
            for (int d : dims) { if (d < 0) throw new NegativeArraySizeException(String.valueOf(d)); k = VM.arrayOf(k); }
            t.R[b] = VM.multi(k, dims, 0);
        });
        Natives.reg(a + "getLength", (t, b) -> {
            if (t.R[b].k.comp == null) throw VM.thrown(t, "java/lang/IllegalArgumentException", "Argument is not an array");
            t.P[b] = Array.getLength(t.R[b].x);
        });
        Natives.reg(a + "get", (t, b) -> {
            Obj o = t.R[b];
            int i = (int) t.P[b + 1];
            if (o.k.comp == null) throw VM.thrown(t, "java/lang/IllegalArgumentException", "Argument is not an array");
            char p = o.k.comp.prim;
            t.R[b] = p == 0 ? ((Obj[]) o.x)[i] : Natives.box(t, p, elem(o, i));
        });
        for (String[] ty : new String[][]{{"Boolean", "Z"}, {"Byte", "B"}, {"Char", "C"}, {"Short", "S"}, {"Int", "I"}, {"Long", "J"}, {"Float", "F"}, {"Double", "D"}}) {
            char to = ty[1].charAt(0);
            Natives.reg(a + "get" + ty[0], (t, b) -> {
                Obj o = t.R[b];
                if (o.k.comp == null || o.k.comp.prim == 0) throw VM.thrown(t, "java/lang/IllegalArgumentException", "Argument is not an array");
                t.P[b] = widen(t, Natives.box(t, o.k.comp.prim, elem(o, (int) t.P[b + 1])), to);
            });
            Natives.reg(a + "set" + ty[0], (t, b) -> {
                Obj o = t.R[b];
                if (o.k.comp == null || o.k.comp.prim == 0) throw VM.thrown(t, "java/lang/IllegalArgumentException", "Argument is not an array");
                setElem(o, (int) t.P[b + 1], widen(t, Natives.box(t, to, t.P[b + 2]), o.k.comp.prim));
            });
        }
        Natives.reg(a + "set", (t, b) -> {
            Obj o = t.R[b], v = t.R[b + 2];
            int i = (int) t.P[b + 1];
            if (o.k.comp == null) throw VM.thrown(t, "java/lang/IllegalArgumentException", "Argument is not an array");
            char p = o.k.comp.prim;
            if (p == 0) {
                if (v != null && !v.k.is(o.k.comp)) throw VM.thrown(t, "java/lang/IllegalArgumentException", "array element type mismatch");
                ((Obj[]) o.x)[i] = v;
            } else setElem(o, i, widen(t, v, p));
        });
    }

    static long elem(Obj o, int i) {
        Object a = o.x;
        if (a instanceof byte[] v) return v[i];
        if (a instanceof int[] v) return v[i];
        if (a instanceof long[] v) return v[i];
        if (a instanceof char[] v) return v[i];
        if (a instanceof short[] v) return v[i];
        if (a instanceof float[] v) return Interp.fb(v[i]);
        return Interp.db(((double[]) a)[i]);
    }

    static void setElem(Obj o, int i, long x) {
        Object a = o.x;
        if (a instanceof byte[] v) v[i] = (byte) x;
        else if (a instanceof int[] v) v[i] = (int) x;
        else if (a instanceof long[] v) v[i] = x;
        else if (a instanceof char[] v) v[i] = (char) x;
        else if (a instanceof short[] v) v[i] = (short) x;
        else if (a instanceof float[] v) v[i] = Interp.f(x);
        else ((double[]) a)[i] = Interp.d(x);
    }

    static boolean skip(Method m) {
        String n = m.klass.name;
        if (m.intrinsic || m.klass.hidden && n.startsWith("java/lang/invoke/LambdaForm$") || n.startsWith("java/lang/invoke/") && n.endsWith("$Holder")) return true;
        return n.equals("java/lang/reflect/Method") && m.name.equals("invoke") || n.startsWith("jdk/internal/reflect/") && n.contains("AccessorImpl");
    }

    static Klass nestHost(T t, Klass k) {
        if (k.host != null) return k.host;
        byte[] nh = k.attrs.get("NestHost");
        return nh == null ? k : VM.klassAt(t, k, (nh[0] & 0xff) << 8 | nh[1] & 0xff);
    }
}
