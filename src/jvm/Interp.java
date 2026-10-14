package jvm;

import java.lang.reflect.Array;

final class Interp {
    static void invoke(T t, Method m, int b) {
        if (m.code != null) exec(t, m, b);
        else VM.callNative(t, m, b);
    }

    static void exec(T t, Method m, int base) {
        if (run(t, m, base, false)) return;
        while (!run(t, m, base, true)) { }
    }

    static boolean run(T t, Method m, int base, boolean resume) {
        final long[] P = t.P;
        final Obj[] R = t.R;
        final byte[] c = m.code;
        final Klass k = m.klass;
        final Object[] res = k.res;
        final int fend = base + m.maxLocals + m.maxStack;
        final int fd;
        int sp, pc, budget = 20000;
        Obj mon = null;
        if (resume) {
            fd = t.depth - 1;
            pc = t.fpc[fd];
            sp = t.rsp;
            mon = t.rmon;
        } else {
            sp = base + m.maxLocals;
            pc = 0;
            fd = t.depth;
            if (fend + 16 > P.length || fd >= t.limit) throw VM.overflow(t);
            t.depth = fd + 1;
            t.fm[fd] = m;
            t.fpc[fd] = 0;
            t.top = fend;
            if ((m.flags & 0x20) != 0) {
                mon = (m.flags & 8) != 0 ? VM.mirror(k) : R[base];
                VM.lock(t, mon);
            }
            if (VM.trace) VM.traceEnter(t, m);
        }
        t.top = fend;
        for (;;) {
            Throw thr;
            try {
                for (;;) {
                    switch (c[pc] & 0xff) {
                        case 0 -> pc++;
                        case 1 -> { R[sp++] = null; pc++; }
                        case 2, 3, 4, 5, 6, 7, 8 -> { P[sp++] = (c[pc] & 0xff) - 3; pc++; }
                        case 9, 10 -> { P[sp] = (c[pc] & 0xff) - 9; sp += 2; pc++; }
                        case 11, 12, 13 -> { P[sp++] = Float.floatToRawIntBits((c[pc] & 0xff) - 11); pc++; }
                        case 14, 15 -> { P[sp] = Double.doubleToRawLongBits((c[pc] & 0xff) - 14); sp += 2; pc++; }
                        case 16 -> { P[sp++] = c[pc + 1]; pc += 2; }
                        case 17 -> { P[sp++] = (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff); pc += 3; }
                        case 18, 19 -> {
                            int i = c[pc] == 18 ? c[pc + 1] & 0xff : (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            switch (k.tag[i]) {
                                case 3, 4 -> P[sp++] = k.num[i];
                                default -> {
                                    Object o = res[i];
                                    if (o == null || k.tag[i] == 7) { t.fpc[fd] = pc; o = VM.constant(t, k, i); t.top = fend; }
                                    if (k.tag[i] == 17 && k.utf[k.cb[k.cb[i]]].length() == 1) P[sp++] = Natives.unbox((Obj) o);
                                    else R[sp++] = (Obj) o;
                                }
                            }
                            pc += c[pc] == 18 ? 2 : 3;
                        }
                        case 20 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            if (k.tag[i] == 17) { t.fpc[fd] = pc; P[sp] = Natives.unbox((Obj) VM.constant(t, k, i)); t.top = fend; } else P[sp] = k.num[i];
                            sp += 2;
                            pc += 3;
                        }
                        case 21, 23 -> { P[sp++] = P[base + (c[pc + 1] & 0xff)]; pc += 2; }
                        case 22, 24 -> { P[sp] = P[base + (c[pc + 1] & 0xff)]; sp += 2; pc += 2; }
                        case 25 -> { R[sp++] = R[base + (c[pc + 1] & 0xff)]; pc += 2; }
                        case 26, 27, 28, 29 -> { P[sp++] = P[base + (c[pc] & 0xff) - 26]; pc++; }
                        case 30, 31, 32, 33 -> { P[sp] = P[base + (c[pc] & 0xff) - 30]; sp += 2; pc++; }
                        case 34, 35, 36, 37 -> { P[sp++] = P[base + (c[pc] & 0xff) - 34]; pc++; }
                        case 38, 39, 40, 41 -> { P[sp] = P[base + (c[pc] & 0xff) - 38]; sp += 2; pc++; }
                        case 42, 43, 44, 45 -> { R[sp++] = R[base + (c[pc] & 0xff) - 42]; pc++; }
                        case 46 -> { sp--; P[sp - 1] = ((int[]) R[sp - 1].x)[(int) P[sp]]; pc++; }
                        case 47 -> { P[sp - 2] = ((long[]) R[sp - 2].x)[(int) P[sp - 1]]; pc++; }
                        case 48 -> { sp--; P[sp - 1] = Float.floatToRawIntBits(((float[]) R[sp - 1].x)[(int) P[sp]]); pc++; }
                        case 49 -> { P[sp - 2] = Double.doubleToRawLongBits(((double[]) R[sp - 2].x)[(int) P[sp - 1]]); pc++; }
                        case 50 -> { sp--; R[sp - 1] = ((Obj[]) R[sp - 1].x)[(int) P[sp]]; pc++; }
                        case 51 -> { sp--; P[sp - 1] = ((byte[]) R[sp - 1].x)[(int) P[sp]]; pc++; }
                        case 52 -> { sp--; P[sp - 1] = ((char[]) R[sp - 1].x)[(int) P[sp]]; pc++; }
                        case 53 -> { sp--; P[sp - 1] = ((short[]) R[sp - 1].x)[(int) P[sp]]; pc++; }
                        case 54, 56 -> { P[base + (c[pc + 1] & 0xff)] = P[--sp]; pc += 2; }
                        case 55, 57 -> { sp -= 2; P[base + (c[pc + 1] & 0xff)] = P[sp]; pc += 2; }
                        case 58 -> { R[base + (c[pc + 1] & 0xff)] = R[--sp]; pc += 2; }
                        case 59, 60, 61, 62 -> { P[base + (c[pc] & 0xff) - 59] = P[--sp]; pc++; }
                        case 63, 64, 65, 66 -> { sp -= 2; P[base + (c[pc] & 0xff) - 63] = P[sp]; pc++; }
                        case 67, 68, 69, 70 -> { P[base + (c[pc] & 0xff) - 67] = P[--sp]; pc++; }
                        case 71, 72, 73, 74 -> { sp -= 2; P[base + (c[pc] & 0xff) - 71] = P[sp]; pc++; }
                        case 75, 76, 77, 78 -> { R[base + (c[pc] & 0xff) - 75] = R[--sp]; pc++; }
                        case 79 -> { sp -= 3; ((int[]) R[sp].x)[(int) P[sp + 1]] = (int) P[sp + 2]; pc++; }
                        case 80 -> { sp -= 4; ((long[]) R[sp].x)[(int) P[sp + 1]] = P[sp + 2]; pc++; }
                        case 81 -> { sp -= 3; ((float[]) R[sp].x)[(int) P[sp + 1]] = Float.intBitsToFloat((int) P[sp + 2]); pc++; }
                        case 82 -> { sp -= 4; ((double[]) R[sp].x)[(int) P[sp + 1]] = Double.longBitsToDouble(P[sp + 2]); pc++; }
                        case 83 -> {
                            sp -= 3;
                            Obj a = R[sp], v = R[sp + 2];
                            Obj[] d = (Obj[]) a.x;
                            int i = (int) P[sp + 1];
                            if (v != null && !v.k.is(a.k.comp)) {
                                if (i < 0 || i >= d.length) throw new ArrayIndexOutOfBoundsException("Index " + i + " out of bounds for length " + d.length);
                                t.fpc[fd] = pc;
                                throw VM.thrown(t, "java/lang/ArrayStoreException", v.k.name.replace('/', '.'));
                            }
                            d[i] = v;
                            pc++;
                        }
                        case 84 -> { sp -= 3; ((byte[]) R[sp].x)[(int) P[sp + 1]] = (byte) P[sp + 2]; pc++; }
                        case 85 -> { sp -= 3; ((char[]) R[sp].x)[(int) P[sp + 1]] = (char) P[sp + 2]; pc++; }
                        case 86 -> { sp -= 3; ((short[]) R[sp].x)[(int) P[sp + 1]] = (short) P[sp + 2]; pc++; }
                        case 87 -> { sp--; pc++; }
                        case 88 -> { sp -= 2; pc++; }
                        case 89 -> { P[sp] = P[sp - 1]; R[sp] = R[sp - 1]; sp++; pc++; }
                        case 90 -> {
                            P[sp] = P[sp - 1]; R[sp] = R[sp - 1];
                            P[sp - 1] = P[sp - 2]; R[sp - 1] = R[sp - 2];
                            P[sp - 2] = P[sp]; R[sp - 2] = R[sp];
                            sp++; pc++;
                        }
                        case 91 -> {
                            P[sp] = P[sp - 1]; R[sp] = R[sp - 1];
                            P[sp - 1] = P[sp - 2]; R[sp - 1] = R[sp - 2];
                            P[sp - 2] = P[sp - 3]; R[sp - 2] = R[sp - 3];
                            P[sp - 3] = P[sp]; R[sp - 3] = R[sp];
                            sp++; pc++;
                        }
                        case 92 -> {
                            P[sp] = P[sp - 2]; R[sp] = R[sp - 2];
                            P[sp + 1] = P[sp - 1]; R[sp + 1] = R[sp - 1];
                            sp += 2; pc++;
                        }
                        case 93 -> {
                            P[sp + 1] = P[sp - 1]; R[sp + 1] = R[sp - 1];
                            P[sp] = P[sp - 2]; R[sp] = R[sp - 2];
                            P[sp - 1] = P[sp - 3]; R[sp - 1] = R[sp - 3];
                            P[sp - 2] = P[sp + 1]; R[sp - 2] = R[sp + 1];
                            P[sp - 3] = P[sp]; R[sp - 3] = R[sp];
                            sp += 2; pc++;
                        }
                        case 94 -> {
                            P[sp + 1] = P[sp - 1]; R[sp + 1] = R[sp - 1];
                            P[sp] = P[sp - 2]; R[sp] = R[sp - 2];
                            P[sp - 1] = P[sp - 3]; R[sp - 1] = R[sp - 3];
                            P[sp - 2] = P[sp - 4]; R[sp - 2] = R[sp - 4];
                            P[sp - 3] = P[sp + 1]; R[sp - 3] = R[sp + 1];
                            P[sp - 4] = P[sp]; R[sp - 4] = R[sp];
                            sp += 2; pc++;
                        }
                        case 95 -> {
                            long p = P[sp - 1]; Obj r = R[sp - 1];
                            P[sp - 1] = P[sp - 2]; R[sp - 1] = R[sp - 2];
                            P[sp - 2] = p; R[sp - 2] = r;
                            pc++;
                        }
                        case 96 -> { sp--; P[sp - 1] = (int) P[sp - 1] + (int) P[sp]; pc++; }
                        case 97 -> { sp -= 2; P[sp - 2] += P[sp]; pc++; }
                        case 98 -> { sp--; P[sp - 1] = fb(f(P[sp - 1]) + f(P[sp])); pc++; }
                        case 99 -> { sp -= 2; P[sp - 2] = db(d(P[sp - 2]) + d(P[sp])); pc++; }
                        case 100 -> { sp--; P[sp - 1] = (int) P[sp - 1] - (int) P[sp]; pc++; }
                        case 101 -> { sp -= 2; P[sp - 2] -= P[sp]; pc++; }
                        case 102 -> { sp--; P[sp - 1] = fb(f(P[sp - 1]) - f(P[sp])); pc++; }
                        case 103 -> { sp -= 2; P[sp - 2] = db(d(P[sp - 2]) - d(P[sp])); pc++; }
                        case 104 -> { sp--; P[sp - 1] = (int) P[sp - 1] * (int) P[sp]; pc++; }
                        case 105 -> { sp -= 2; P[sp - 2] *= P[sp]; pc++; }
                        case 106 -> { sp--; P[sp - 1] = fb(f(P[sp - 1]) * f(P[sp])); pc++; }
                        case 107 -> { sp -= 2; P[sp - 2] = db(d(P[sp - 2]) * d(P[sp])); pc++; }
                        case 108 -> { sp--; P[sp - 1] = (int) P[sp - 1] / (int) P[sp]; pc++; }
                        case 109 -> { sp -= 2; P[sp - 2] /= P[sp]; pc++; }
                        case 110 -> { sp--; P[sp - 1] = fb(f(P[sp - 1]) / f(P[sp])); pc++; }
                        case 111 -> { sp -= 2; P[sp - 2] = db(d(P[sp - 2]) / d(P[sp])); pc++; }
                        case 112 -> { sp--; P[sp - 1] = (int) P[sp - 1] % (int) P[sp]; pc++; }
                        case 113 -> { sp -= 2; P[sp - 2] %= P[sp]; pc++; }
                        case 114 -> { sp--; P[sp - 1] = fb(f(P[sp - 1]) % f(P[sp])); pc++; }
                        case 115 -> { sp -= 2; P[sp - 2] = db(d(P[sp - 2]) % d(P[sp])); pc++; }
                        case 116 -> { P[sp - 1] = -(int) P[sp - 1]; pc++; }
                        case 117 -> { P[sp - 2] = -P[sp - 2]; pc++; }
                        case 118 -> { P[sp - 1] = fb(-f(P[sp - 1])); pc++; }
                        case 119 -> { P[sp - 2] = db(-d(P[sp - 2])); pc++; }
                        case 120 -> { sp--; P[sp - 1] = (int) P[sp - 1] << (int) P[sp]; pc++; }
                        case 121 -> { sp--; P[sp - 2] <<= (int) P[sp]; pc++; }
                        case 122 -> { sp--; P[sp - 1] = (int) P[sp - 1] >> (int) P[sp]; pc++; }
                        case 123 -> { sp--; P[sp - 2] >>= (int) P[sp]; pc++; }
                        case 124 -> { sp--; P[sp - 1] = (int) P[sp - 1] >>> (int) P[sp]; pc++; }
                        case 125 -> { sp--; P[sp - 2] >>>= (int) P[sp]; pc++; }
                        case 126 -> { sp--; P[sp - 1] = (int) P[sp - 1] & (int) P[sp]; pc++; }
                        case 127 -> { sp -= 2; P[sp - 2] &= P[sp]; pc++; }
                        case 128 -> { sp--; P[sp - 1] = (int) P[sp - 1] | (int) P[sp]; pc++; }
                        case 129 -> { sp -= 2; P[sp - 2] |= P[sp]; pc++; }
                        case 130 -> { sp--; P[sp - 1] = (int) P[sp - 1] ^ (int) P[sp]; pc++; }
                        case 131 -> { sp -= 2; P[sp - 2] ^= P[sp]; pc++; }
                        case 132 -> { int i = base + (c[pc + 1] & 0xff); P[i] = (int) P[i] + c[pc + 2]; pc += 3; }
                        case 133 -> { sp++; pc++; }
                        case 134 -> { P[sp - 1] = fb((float) (int) P[sp - 1]); pc++; }
                        case 135 -> { P[sp - 1] = db((double) (int) P[sp - 1]); sp++; pc++; }
                        case 136 -> { sp--; P[sp - 1] = (int) P[sp - 1]; pc++; }
                        case 137 -> { sp--; P[sp - 1] = fb((float) P[sp - 1]); pc++; }
                        case 138 -> { P[sp - 2] = db((double) P[sp - 2]); pc++; }
                        case 139 -> { P[sp - 1] = (int) f(P[sp - 1]); pc++; }
                        case 140 -> { P[sp - 1] = (long) f(P[sp - 1]); sp++; pc++; }
                        case 141 -> { P[sp - 1] = db((double) f(P[sp - 1])); sp++; pc++; }
                        case 142 -> { sp--; P[sp - 1] = (int) d(P[sp - 1]); pc++; }
                        case 143 -> { P[sp - 2] = (long) d(P[sp - 2]); pc++; }
                        case 144 -> { sp--; P[sp - 1] = fb((float) d(P[sp - 1])); pc++; }
                        case 145 -> { P[sp - 1] = (byte) P[sp - 1]; pc++; }
                        case 146 -> { P[sp - 1] = (char) P[sp - 1]; pc++; }
                        case 147 -> { P[sp - 1] = (short) P[sp - 1]; pc++; }
                        case 148 -> { sp -= 3; P[sp - 1] = Long.compare(P[sp - 1], P[sp + 1]); pc++; }
                        case 149, 150 -> {
                            sp--;
                            float a = f(P[sp - 1]), b = f(P[sp]);
                            P[sp - 1] = a > b ? 1 : a == b ? 0 : a < b ? -1 : c[pc] == (byte) 149 ? -1 : 1;
                            pc++;
                        }
                        case 151, 152 -> {
                            sp -= 3;
                            double a = d(P[sp - 1]), b = d(P[sp + 1]);
                            P[sp - 1] = a > b ? 1 : a == b ? 0 : a < b ? -1 : c[pc] == (byte) 151 ? -1 : 1;
                            pc++;
                        }
                        case 153 -> { int o = (int) P[--sp] == 0 ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 154 -> { int o = (int) P[--sp] != 0 ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 155 -> { int o = (int) P[--sp] < 0 ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 156 -> { int o = (int) P[--sp] >= 0 ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 157 -> { int o = (int) P[--sp] > 0 ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 158 -> { int o = (int) P[--sp] <= 0 ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 159 -> { sp -= 2; int o = (int) P[sp] == (int) P[sp + 1] ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 160 -> { sp -= 2; int o = (int) P[sp] != (int) P[sp + 1] ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 161 -> { sp -= 2; int o = (int) P[sp] < (int) P[sp + 1] ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 162 -> { sp -= 2; int o = (int) P[sp] >= (int) P[sp + 1] ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 163 -> { sp -= 2; int o = (int) P[sp] > (int) P[sp + 1] ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 164 -> { sp -= 2; int o = (int) P[sp] <= (int) P[sp + 1] ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 165 -> { sp -= 2; int o = R[sp] == R[sp + 1] ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 166 -> { sp -= 2; int o = R[sp] != R[sp + 1] ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 167 -> { int o = (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff); pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 170 -> {
                            int a = (pc + 4) & ~3;
                            int i = (int) P[--sp], lo = i4(c, a + 4), hi = i4(c, a + 8);
                            pc += i < lo || i > hi ? i4(c, a) : i4(c, a + 12 + (i - lo) * 4);
                        }
                        case 171 -> {
                            int a = (pc + 4) & ~3;
                            int key = (int) P[--sp], lo = 0, hi = i4(c, a + 4) - 1, off = i4(c, a);
                            while (lo <= hi) {
                                int mid = (lo + hi) >>> 1, v = i4(c, a + 8 + mid * 8);
                                if (v < key) lo = mid + 1;
                                else if (v > key) hi = mid - 1;
                                else { off = i4(c, a + 12 + mid * 8); break; }
                            }
                            pc += off;
                        }
                        case 172, 174 -> { P[base] = P[sp - 1]; t.depth = fd; if (mon != null) VM.unlock(t, mon); return true; }
                        case 173, 175 -> { P[base] = P[sp - 2]; t.depth = fd; if (mon != null) VM.unlock(t, mon); return true; }
                        case 176 -> { R[base] = R[sp - 1]; t.depth = fd; if (mon != null) VM.unlock(t, mon); return true; }
                        case 177 -> { t.depth = fd; if (mon != null) VM.unlock(t, mon); return true; }
                        case 178 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Field f = (Field) res[i];
                            if (f == null) { t.fpc[fd] = pc; f = VM.fieldAt(t, k, i); }
                            Klass o = f.klass;
                            if (o.state != 2) { t.fpc[fd] = pc; VM.init(t, o); t.top = fend; }
                            if (f.ref) R[sp++] = o.statics.r[f.slot];
                            else { P[sp] = o.statics.p[f.slot]; sp += f.wide ? 2 : 1; }
                            pc += 3;
                        }
                        case 179 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Field f = (Field) res[i];
                            if (f == null) { t.fpc[fd] = pc; f = VM.fieldAt(t, k, i); }
                            Klass o = f.klass;
                            if (o.state != 2) { t.fpc[fd] = pc; VM.init(t, o); t.top = fend; }
                            if (f.ref) o.statics.r[f.slot] = R[--sp];
                            else { sp -= f.wide ? 2 : 1; o.statics.p[f.slot] = narrow(f.type, P[sp]); }
                            pc += 3;
                        }
                        case 180 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Field f = (Field) res[i];
                            if (f == null) { t.fpc[fd] = pc; f = VM.fieldAt(t, k, i); }
                            Obj o = R[sp - 1];
                            if (f.ref) R[sp - 1] = o.r[f.slot];
                            else { P[sp - 1] = o.p[f.slot]; if (f.wide) sp++; }
                            pc += 3;
                        }
                        case 181 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Field f = (Field) res[i];
                            if (f == null) { t.fpc[fd] = pc; f = VM.fieldAt(t, k, i); }
                            if (f.ref) { sp -= 2; R[sp].r[f.slot] = R[sp + 1]; }
                            else if (f.wide) { sp -= 3; R[sp].p[f.slot] = P[sp + 1]; }
                            else { sp -= 2; R[sp].p[f.slot] = narrow(f.type, P[sp + 1]); }
                            pc += 3;
                        }
                        case 182 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Method r = (Method) res[i];
                            t.fpc[fd] = pc;
                            if (r == null) r = VM.methodAt(t, k, i);
                            int nb = sp - r.argSlots;
                            Klass rk = R[nb].k;
                            Method tm = r.vindex >= 0 ? rk.vtable[r.vindex] : r.isPrivate() || !r.klass.isInterface() ? r : rk.select(r);
                            if (tm.code != null) exec(t, tm, nb); else VM.callNative(t, tm, nb);
                            sp = nb + r.retSlots;
                            t.top = fend;
                            pc += 3;
                        }
                        case 183 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Method r = (Method) res[i];
                            t.fpc[fd] = pc;
                            if (r == null) r = VM.specialAt(t, k, i);
                            int nb = sp - r.argSlots;
                            if (R[nb] == null) throw new NullPointerException();
                            if (r.code != null) exec(t, r, nb); else VM.callNative(t, r, nb);
                            sp = nb + r.retSlots;
                            t.top = fend;
                            pc += 3;
                        }
                        case 184 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Method r = (Method) res[i];
                            t.fpc[fd] = pc;
                            if (r == null) r = VM.methodAt(t, k, i);
                            if (r.klass.state != 2) { VM.init(t, r.klass); t.top = fend; }
                            int nb = sp - r.argSlots;
                            if (r.code != null) exec(t, r, nb); else VM.callNative(t, r, nb);
                            sp = nb + r.retSlots;
                            t.top = fend;
                            pc += 3;
                        }
                        case 185 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Method r = (Method) res[i];
                            t.fpc[fd] = pc;
                            if (r == null) r = VM.methodAt(t, k, i);
                            int nb = sp - r.argSlots;
                            Klass rk = R[nb].k;
                            Method tm = r.vindex >= 0 ? rk.vtable[r.vindex] : r.isPrivate() ? r : rk.select(r);
                            if (tm.code != null) exec(t, tm, nb); else VM.callNative(t, tm, nb);
                            sp = nb + r.retSlots;
                            t.top = fend;
                            pc += 5;
                        }
                        case 186 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            t.fpc[fd] = pc;
                            sp = Invoke.dynamic(t, k, i, sp);
                            t.top = fend;
                            pc += 5;
                        }
                        case 187 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Klass o = (Klass) res[i];
                            if (o == null) { t.fpc[fd] = pc; o = VM.klassAt(t, k, i); }
                            if (o.state != 2) { t.fpc[fd] = pc; VM.init(t, o); t.top = fend; }
                            R[sp++] = o.alloc();
                            pc += 3;
                        }
                        case 188 -> { R[sp - 1] = VM.newArray(c[pc + 1], (int) P[sp - 1]); pc += 2; }
                        case 189 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Klass o = (Klass) res[i];
                            if (o == null) { t.fpc[fd] = pc; o = VM.klassAt(t, k, i); }
                            R[sp - 1] = new Obj(VM.arrayOf(o), new Obj[(int) P[sp - 1]]);
                            pc += 3;
                        }
                        case 190 -> { P[sp - 1] = Array.getLength(R[sp - 1].x); pc++; }
                        case 191 -> {
                            Obj e = R[sp - 1];
                            if (e == null) throw new NullPointerException();
                            throw new Throw(e);
                        }
                        case 192 -> {
                            Obj o = R[sp - 1];
                            if (o != null) {
                                int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                                Klass to = (Klass) res[i];
                                if (to == null) { t.fpc[fd] = pc; to = VM.klassAt(t, k, i); }
                                if (o.k != to && !o.k.is(to)) {
                                    t.fpc[fd] = pc;
                                    throw VM.thrown(t, "java/lang/ClassCastException", "class " + VM.dots(o.k) + " cannot be cast to class " + VM.dots(to));
                                }
                            }
                            pc += 3;
                        }
                        case 193 -> {
                            Obj o = R[sp - 1];
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff;
                            Klass to = (Klass) res[i];
                            if (to == null) { t.fpc[fd] = pc; to = VM.klassAt(t, k, i); }
                            P[sp - 1] = o != null && o.k.is(to) ? 1 : 0;
                            pc += 3;
                        }
                        case 194 -> { Obj o = R[--sp]; if (o == null) throw new NullPointerException(); VM.lock(t, o); pc++; }
                        case 195 -> { Obj o = R[--sp]; if (o == null) throw new NullPointerException(); t.fpc[fd] = pc; VM.unlock(t, o); pc++; }
                        case 196 -> {
                            int i = base + ((c[pc + 2] & 0xff) << 8 | c[pc + 3] & 0xff);
                            switch (c[pc + 1] & 0xff) {
                                case 21, 23 -> P[sp++] = P[i];
                                case 22, 24 -> { P[sp] = P[i]; sp += 2; }
                                case 25 -> R[sp++] = R[i];
                                case 54, 56 -> P[i] = P[--sp];
                                case 55, 57 -> { sp -= 2; P[i] = P[sp]; }
                                case 58 -> R[i] = R[--sp];
                                case 132 -> { P[i] = (int) P[i] + (short) (c[pc + 4] << 8 | c[pc + 5] & 0xff); pc += 2; }
                                default -> throw new InternalError("wide " + (c[pc + 1] & 0xff));
                            }
                            pc += 4;
                        }
                        case 197 -> {
                            int i = (c[pc + 1] & 0xff) << 8 | c[pc + 2] & 0xff, n = c[pc + 3] & 0xff;
                            Klass o = (Klass) res[i];
                            if (o == null) { t.fpc[fd] = pc; o = VM.klassAt(t, k, i); }
                            int[] dims = new int[n];
                            sp -= n;
                            for (int j = 0; j < n; j++) dims[j] = (int) P[sp + j];
                            R[sp++] = VM.multi(o, dims, 0);
                            pc += 4;
                        }
                        case 198 -> { int o = R[--sp] == null ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 199 -> { int o = R[--sp] != null ? (short) (c[pc + 1] << 8 | c[pc + 2] & 0xff) : 3; pc += o; if (o < 0 && --budget < 0) return pause(t, fd, pc, sp, mon); }
                        case 200 -> pc += i4(c, pc + 1);
                        default -> throw new InternalError("opcode " + (c[pc] & 0xff) + " in " + m);
                    }
                }
            } catch (Throw e) {
                thr = e;
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | ArithmeticException | NegativeArraySizeException | StackOverflowError | OutOfMemoryError | ClassCastException e) {
                t.depth = fd + 1;
                t.fpc[fd] = pc;
                t.top = fend;
                thr = VM.convert(t, e);
            }
            t.depth = fd + 1;
            t.top = fend;
            int[] ex = m.ex;
            boolean found = false;
            for (int i = 0; i < ex.length; i += 4) {
                if (pc >= ex[i] && pc < ex[i + 1] && (ex[i + 3] == 0 || thr.ex.k.is(VM.klassAt(t, k, ex[i + 3])))) {
                    pc = ex[i + 2];
                    sp = base + m.maxLocals;
                    R[sp++] = thr.ex;
                    found = true;
                    break;
                }
            }
            if (!found) {
                t.depth = fd;
                if (mon != null) VM.unlock(t, mon);
                throw thr;
            }
        }
    }

    // long loops leave run() and come straight back in, so the host never needs on-stack replacement
    static boolean pause(T t, int fd, int pc, int sp, Obj mon) {
        t.fpc[fd] = pc;
        t.rsp = sp;
        t.rmon = mon;
        return false;
    }

    static float f(long v) { return Float.intBitsToFloat((int) v); }
    static double d(long v) { return Double.longBitsToDouble(v); }
    static long fb(float v) { return Float.floatToRawIntBits(v); }
    static long db(double v) { return Double.doubleToRawLongBits(v); }

    static int i4(byte[] c, int i) {
        return c[i] << 24 | (c[i + 1] & 0xff) << 16 | (c[i + 2] & 0xff) << 8 | c[i + 3] & 0xff;
    }

    static long narrow(char type, long v) {
        return switch (type) {
            case 'Z' -> v & 1;
            case 'B' -> (byte) v;
            case 'C' -> (char) v;
            case 'S' -> (short) v;
            default -> v;
        };
    }
}
