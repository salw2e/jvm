package jvm;

class Obj {
    final Klass k;
    long[] p;
    Obj[] r;
    Object x, mods;
    volatile T owner;
    int locks, waiters;
    java.util.ArrayDeque<Threads.Waiter> wq;

    Obj(Klass k, long[] p, Obj[] r) {
        this.k = k;
        this.p = p;
        this.r = r;
    }

    Obj(Klass k, Object array) {
        this.k = k;
        this.x = array;
    }

    public String toString() { return k.name + "@" + Integer.toHexString(System.identityHashCode(this)); }
}

final class Throw extends RuntimeException {
    final Obj ex;

    Throw(Obj ex) {
        super(null, null, false, false);
        this.ex = ex;
    }

    public String getMessage() { return VM.describe(ex); }
}

final class T {
    static final int MAXD = 1 << 14;
    final long[] P;
    final Obj[] R;
    final Method[] fm = new Method[MAXD + 64];
    final int[] fpc = new int[MAXD + 64];
    int depth, top, limit = MAXD, rsp;
    Obj rmon;
    volatile Obj waitingOn;
    Obj thread;
    volatile boolean interrupted;
    Thread host;

    T(int slots) {
        P = new long[slots];
        R = new Obj[slots];
    }
}
