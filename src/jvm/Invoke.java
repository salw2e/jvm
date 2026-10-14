package jvm;

import java.util.HashMap;

final class Invoke {
    static final int IS_METHOD = 0x10000, IS_CONSTRUCTOR = 0x20000, IS_FIELD = 0x40000, TRUSTED_FINAL = 0x200000;

    static final class Site {
        Method ad;
        Obj appendix;
    }

    static final HashMap<String, Method> intrinsics = new HashMap<>();
    static Klass MN, MHN, MT, RMN;
    static int CLAZZ, NAME, TYPE, FLAGS, METHOD, RTYPE, PTYPES, FORM, VMENTRY;

    static synchronized void setup(T t) {
        if (MN != null) return;
        MHN = VM.load(t, "java/lang/invoke/MethodHandleNatives", null);
        MT = VM.load(t, "java/lang/invoke/MethodType", null);
        RMN = VM.load(t, "java/lang/invoke/ResolvedMethodName", null);
        Klass mn = VM.load(t, "java/lang/invoke/MemberName", null);
        CLAZZ = mn.field("clazz").slot;
        NAME = mn.field("name").slot;
        TYPE = mn.field("type").slot;
        FLAGS = mn.field("flags").slot;
        METHOD = mn.field("method").slot;
        RTYPE = MT.field("rtype").slot;
        PTYPES = MT.field("ptypes").slot;
        FORM = VM.MH.field("form").slot;
        VMENTRY = VM.load(t, "java/lang/invoke/LambdaForm", null).field("vmentry").slot;
        MN = mn;
        VM.init(t, MHN);
    }

    static String desc(Klass k) {
        return k.prim != 0 ? String.valueOf(k.prim) : k.comp != null ? k.name : "L" + k.name + ";";
    }

    static String desc(Obj type) {
        if (type.k == VM.STRING) return VM.str(type);
        if (type.k == VM.CLASS) return desc(VM.klass(type));
        Obj r;
        Obj[] p;
        if (type.k == MT) {
            r = type.r[RTYPE];
            p = (Obj[]) type.r[PTYPES].x;
        } else {
            Obj[] a = (Obj[]) type.x;
            r = a[0];
            p = (Obj[]) a[1].x;
        }
        StringBuilder s = new StringBuilder("(");
        for (Obj c : p) s.append(desc(VM.klass(c)));
        return s.append(')').append(desc(VM.klass(r))).toString();
    }

    static Obj methodType(T t, Klass k, String d) {
        setup(t);
        java.util.ArrayList<String> ps = Klass.params(d);
        Klass[] p = new Klass[ps.size()];
        for (int i = 0; i < p.length; i++) p[i] = VM.fromDesc(t, ps.get(i), k.loader);
        Klass r = VM.fromDesc(t, d.substring(d.indexOf(')') + 1), k.loader);
        return VM.callR(t, MHN.mmap.get("findMethodHandleType(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;"), VM.mirror(r), Reflect.classes(t, p));
    }

    static boolean isIntrinsic(String n) {
        return n.equals("invokeBasic") || n.startsWith("linkTo");
    }

    static Method intrinsic(String name, String desc) {
        synchronized (intrinsics) {
            Method m = intrinsics.get(name + desc);
            if (m != null) return m;
            Method s = m = new Method();
            m.klass = VM.MH;
            m.name = name;
            m.desc = desc;
            boolean basic = name.equals("invokeBasic");
            m.flags = basic ? 0x1111 : 0x1119;
            m.argSlots = Klass.argSlots(desc) + (basic ? 1 : 0);
            m.ret = desc.charAt(desc.indexOf(')') + 1);
            m.retSlots = m.ret == 'V' ? 0 : m.ret == 'J' || m.ret == 'D' ? 2 : 1;
            m.attrs = new HashMap<>();
            m.intrinsic = true;
            m.nat = switch (name) {
                case "invokeBasic" -> (t, b) -> Interp.invoke(t, (Method) t.R[b].r[FORM].r[VMENTRY].x, b);
                case "linkToStatic" -> (t, b) -> {
                    Method x = (Method) t.R[b + s.argSlots - 1].x;
                    if (x.klass.state != Klass.INITED) VM.init(t, x.klass);
                    Interp.invoke(t, x, b);
                };
                case "linkToSpecial" -> (t, b) -> {
                    if (t.R[b] == null) throw new NullPointerException();
                    Interp.invoke(t, (Method) t.R[b + s.argSlots - 1].x, b);
                };
                case "linkToVirtual", "linkToInterface" -> (t, b) -> {
                    Method x = (Method) t.R[b + s.argSlots - 1].x;
                    Klass rk = t.R[b].k;
                    Interp.invoke(t, x.vindex >= 0 ? rk.vtable[x.vindex] : x.isPrivate() ? x : rk.select(x), b);
                };
                default -> null;
            };
            intrinsics.put(name + desc, m);
            return m;
        }
    }

    static Method polymorphic(T t, Klass k, Klass c, String name, String desc) {
        Method g = c.mmap.get(name + "([Ljava/lang/Object;)Ljava/lang/Object;");
        if (g == null) g = c.mmap.get(name + "([Ljava/lang/Object;)V");
        if (g == null) g = c.mmap.get(name + "([Ljava/lang/Object;)Z");
        if (g == null || (g.flags & 0x180) != 0x180) return null;
        if (c == VM.MH && isIntrinsic(name)) return intrinsic(name, desc);
        setup(t);
        Obj appendix = new Obj(VM.arrayOf(VM.OBJECT), new Obj[1]);
        Obj mn = VM.callR(t, MHN.mmap.get("linkMethod(Ljava/lang/Class;ILjava/lang/Class;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/invoke/MemberName;"),
                VM.mirror(k), 5, VM.mirror(c), VM.intern(name), methodType(t, k, desc), appendix);
        Method ad = (Method) mn.x;
        Obj ap = ((Obj[]) appendix.x)[0];
        Method s = new Method();
        s.klass = c;
        s.name = name;
        s.desc = desc;
        s.flags = 0x1111;
        s.argSlots = Klass.argSlots(desc) + 1;
        s.ret = desc.charAt(desc.indexOf(')') + 1);
        s.retSlots = s.ret == 'V' ? 0 : s.ret == 'J' || s.ret == 'D' ? 2 : 1;
        s.attrs = new HashMap<>();
        s.intrinsic = true;
        s.nat = (tt, b) -> {
            if (tt.R[b] == null) throw new NullPointerException();
            if (ap != null) tt.R[b + s.argSlots] = ap;
            Interp.invoke(tt, ad, b);
        };
        return s;
    }

    static int[][] bsm(Klass k) {
        if (k.bsm != null) return k.bsm;
        byte[] a = k.attrs.get("BootstrapMethods");
        int n = (a[0] & 0xff) << 8 | a[1] & 0xff, at = 2;
        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) {
            int argc = (a[at + 2] & 0xff) << 8 | a[at + 3] & 0xff;
            out[i] = new int[argc + 1];
            out[i][0] = (a[at] & 0xff) << 8 | a[at + 1] & 0xff;
            for (int j = 0; j < argc; j++) out[i][j + 1] = (a[at + 4 + j * 2] & 0xff) << 8 | a[at + 5 + j * 2] & 0xff;
            at += 4 + argc * 2;
        }
        return k.bsm = out;
    }

    static Obj arg(T t, Klass k, int i) {
        return switch (k.tag[i]) {
            case 3 -> Natives.box(t, 'I', k.num[i]);
            case 4 -> Natives.box(t, 'F', k.num[i]);
            case 5 -> Natives.box(t, 'J', k.num[i]);
            case 6 -> Natives.box(t, 'D', k.num[i]);
            default -> (Obj) VM.constant(t, k, i);
        };
    }

    static Obj args(T t, Klass k, int[] bm) {
        if (bm.length == 1) return null;
        Obj[] a = new Obj[bm.length - 1];
        for (int i = 0; i < a.length; i++) a[i] = arg(t, k, bm[i + 1]);
        return new Obj(VM.arrayOf(VM.OBJECT), a);
    }

    static Object constant(T t, Klass k, int i) {
        Object c = k.res[i];
        if (c != null) return c;
        setup(t);
        switch (k.tag[i]) {
            case 16 -> c = methodType(t, k, k.utf[k.ca[i]]);
            case 15 -> {
                int kind = k.ca[i], ref = k.cb[i], nt = k.cb[ref];
                Klass defc = VM.klassAt(t, k, k.ca[ref]);
                String name = k.utf[k.ca[nt]], d = k.utf[k.cb[nt]];
                Obj type = kind <= 4 ? VM.mirror(VM.fromDesc(t, d, k.loader)) : methodType(t, k, d);
                c = VM.callR(t, MHN.mmap.get("linkMethodHandleConstant(Ljava/lang/Class;ILjava/lang/Class;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;"),
                        VM.mirror(k), kind, VM.mirror(defc), VM.intern(name), type);
            }
            case 17 -> {
                int[] bm = bsm(k)[k.ca[i]];
                int nt = k.cb[i];
                Obj mh = (Obj) constant(t, k, bm[0]);
                c = VM.callR(t, MHN.mmap.get("linkDynamicConstant(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                        VM.mirror(k), i, mh, VM.intern(k.utf[k.ca[nt]]), VM.mirror(VM.fromDesc(t, k.utf[k.cb[nt]], k.loader)), args(t, k, bm));
            }
            default -> throw new InternalError("constant tag " + k.tag[i]);
        }
        return k.res[i] = c;
    }

    static int dynamic(T t, Klass k, int i, int sp) {
        Site s = (Site) k.res[i];
        if (s == null) {
            setup(t);
            int[] bm = bsm(k)[k.ca[i]];
            int nt = k.cb[i];
            Obj mh = (Obj) constant(t, k, bm[0]);
            Obj appendix = new Obj(VM.arrayOf(VM.OBJECT), new Obj[1]);
            Obj mn = VM.callR(t, MHN.mmap.get("linkCallSite(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/invoke/MemberName;"),
                    VM.mirror(k), i, mh, VM.intern(k.utf[k.ca[nt]]), methodType(t, k, k.utf[k.cb[nt]]), args(t, k, bm), appendix);
            s = new Site();
            s.ad = (Method) mn.x;
            s.appendix = ((Obj[]) appendix.x)[0];
            k.res[i] = s;
        }
        if (s.appendix != null) t.R[sp++] = s.appendix;
        int nb = sp - s.ad.argSlots;
        Interp.invoke(t, s.ad, nb);
        return nb + s.ad.retSlots;
    }

    static void bindMethod(Obj mn, Method m, int flags) {
        mn.x = m;
        mn.p[FLAGS] = flags;
        Obj r = RMN.alloc();
        r.x = m;
        mn.r[METHOD] = r;
        if (!m.intrinsic) mn.r[CLAZZ] = VM.mirror(m.klass);
    }

    static void bindField(Obj mn, Field f, boolean setter) {
        mn.x = f;
        int kind = (f.isStatic() ? 2 : 1) + (setter ? 2 : 0);
        boolean trusted = (f.flags & 0x10) != 0 && (f.isStatic() || f.klass.hidden || f.klass.attrs.containsKey("Record"));
        mn.p[FLAGS] = f.flags & 0xffff | IS_FIELD | kind << 24 | (trusted ? TRUSTED_FINAL : 0);
        mn.r[CLAZZ] = VM.mirror(f.klass);
    }

    static void init() {
        String n = "java/lang/invoke/MethodHandleNatives.";
        Natives.reg(n + "init", (t, b) -> {
            setup(t);
            Obj mn = t.R[b], ref = t.R[b + 1];
            switch (ref.k.name) {
                case "java/lang/reflect/Field" -> bindField(mn, Reflect.field(ref), false);
                case "java/lang/reflect/Constructor" -> bindMethod(mn, Reflect.method(ref), Reflect.method(ref).flags & 0xffff | IS_CONSTRUCTOR | 8 << 24);
                default -> {
                    Method m = Reflect.method(ref);
                    int kind = m.isStatic() ? 6 : m.klass.isInterface() ? 9 : m.isPrivate() ? 7 : 5;
                    bindMethod(mn, m, m.flags & 0xffff | IS_METHOD | kind << 24);
                }
            }
        });
        Natives.reg(n + "expand", (t, b) -> {
            Obj mn = t.R[b];
            if (mn.x instanceof Method m) {
                if (mn.r[NAME] == null) mn.r[NAME] = VM.intern(m.name);
                if (mn.r[TYPE] == null) mn.r[TYPE] = VM.str(m.desc);
            } else if (mn.x instanceof Field f) {
                if (mn.r[NAME] == null) mn.r[NAME] = VM.intern(f.name);
                if (mn.r[TYPE] == null) mn.r[TYPE] = VM.mirror(VM.fromDesc(t, f.desc, f.klass.loader));
            }
        });
        Natives.reg(n + "resolve", (t, b) -> {
            setup(t);
            Obj mn = t.R[b];
            boolean speculative = t.P[b + 3] != 0;
            Klass c = VM.klass(mn.r[CLAZZ]);
            String name = VM.str(mn.r[NAME]), d = desc(mn.r[TYPE]);
            int flags = (int) mn.p[FLAGS], kind = flags >> 24 & 15;
            if ((flags & (IS_METHOD | IS_CONSTRUCTOR)) != 0) {
                Method m = (flags & IS_CONSTRUCTOR) != 0 ? c.mmap.get("<init>" + d) : c == VM.MH && isIntrinsic(name) ? intrinsic(name, d) : c.method(name, d);
                if (m != null && (kind == 6) != m.isStatic()) m = null;
                if (m == null) {
                    t.R[b] = null;
                    if (speculative) return;
                    throw VM.thrown(t, "java/lang/NoSuchMethodError", VM.dots(c) + "." + name + d);
                }
                bindMethod(mn, m, m.flags & 0xffff | flags & (IS_METHOD | IS_CONSTRUCTOR) | kind << 24);
            } else if ((flags & IS_FIELD) != 0) {
                Field f = c.field(name, d);
                if (f == null) {
                    t.R[b] = null;
                    if (speculative) return;
                    throw VM.thrown(t, "java/lang/NoSuchFieldError", name);
                }
                bindField(mn, f, kind == 3 || kind == 4);
            } else throw VM.thrown(t, "java/lang/InternalError", "resolve " + name);
        });
        Natives.reg(n + "objectFieldOffset", (t, b) -> t.P[b] = ((Field) t.R[b].x).offset());
        Natives.reg(n + "staticFieldOffset", (t, b) -> t.P[b] = ((Field) t.R[b].x).offset());
        Natives.reg(n + "staticFieldBase", (t, b) -> t.R[b] = ((Field) t.R[b].x).klass.statics);
        Natives.reg(n + "getMemberVMInfo", (t, b) -> t.R[b] = null);
        Natives.reg(n + "setCallSiteTargetNormal", (t, b) -> t.R[b].r[t.R[b].k.field("target").slot] = t.R[b + 1]);
        Natives.reg(n + "setCallSiteTargetVolatile", (t, b) -> t.R[b].r[t.R[b].k.field("target").slot] = t.R[b + 1]);
        Natives.reg(n + "clearCallSiteContext", Natives.NOP);
        Natives.reg(n + "getNamedCon", (t, b) -> t.P[b] = 0);
    }
}
