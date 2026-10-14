package jvm;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

final class Threads {
    static final AtomicInteger live = new AtomicInteger();
    static final Object exit = new Object();

    static final class Waiter {
        boolean notified;
    }

    static void await(T t, Obj o, long ms) {
        if (o.owner != t) throw VM.thrown(t, "java/lang/IllegalMonitorStateException", "current thread is not owner");
        if (ms < 0) throw VM.thrown(t, "java/lang/IllegalArgumentException", "timeout value is negative");
        Waiter w = new Waiter();
        int saved = o.locks;
        boolean interrupted = false;
        synchronized (o) {
            if (o.wq == null) o.wq = new ArrayDeque<>();
            o.wq.add(w);
            o.owner = null;
            o.locks = 0;
            if (o.waiters > 0) o.notifyAll();
            long end = System.currentTimeMillis() + ms;
            t.waitingOn = o;
            try {
                while (!w.notified) {
                    if (interrupted(t)) { interrupted = true; break; }
                    if (ms == 0) o.wait();
                    else {
                        long left = end - System.currentTimeMillis();
                        if (left <= 0) break;
                        o.wait(left);
                    }
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
            t.waitingOn = null;
            o.wq.remove(w);
            while (o.owner != null) {
                o.waiters++;
                try { o.wait(); } catch (InterruptedException e) { interrupted = true; } finally { o.waiters--; }
            }
            o.owner = t;
            o.locks = saved;
        }
        if (interrupted) {
            t.thread.p[VM.THREAD.field("interrupted").slot] = 0;
            throw VM.thrown(t, "java/lang/InterruptedException", null);
        }
    }

    static boolean interrupted(T t) {
        return t.thread.p[VM.THREAD.field("interrupted").slot] != 0;
    }

    static void sleep(T t, long ms) {
        long end = System.currentTimeMillis() + ms;
        synchronized (t) {
            while (!interrupted(t)) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) return;
                try { t.wait(left); } catch (InterruptedException e) { }
            }
        }
        t.thread.p[VM.THREAD.field("interrupted").slot] = 0;
        throw VM.thrown(t, "java/lang/InterruptedException", "sleep interrupted");
    }

    static void interrupt(T o) {
        if (o.host != null) java.util.concurrent.locks.LockSupport.unpark(o.host);
        Obj w = o.waitingOn;
        if (w != null) synchronized (w) { w.notifyAll(); }
        synchronized (o) { o.notifyAll(); }
    }

    static void signal(T t, Obj o, boolean all) {
        if (o.owner != t) throw VM.thrown(t, "java/lang/IllegalMonitorStateException", "current thread is not owner");
        synchronized (o) {
            if (o.wq == null) return;
            boolean any = false;
            for (Waiter w : o.wq) {
                if (w.notified) continue;
                w.notified = any = true;
                if (!all) break;
            }
            if (any) o.notifyAll();
        }
    }

    static void run(T t) {
        Obj th = t.thread;
        try {
            VM.call(t, VM.virtual(th, "run", "()V"), th);
        } catch (Throw e) {
            try {
                VM.call(t, VM.THREAD.method("dispatchUncaughtException", "(Ljava/lang/Throwable;)V"), th, e.ex);
            } catch (Throw e2) { }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        finish(t);
    }

    static void finish(T t) {
        Obj th = t.thread;
        try {
            VM.call(t, VM.THREAD.method("exit", "()V"), th);
        } catch (Throw e) { }
        VM.lock(t, th);
        th.p[VM.THREAD.field("threadStatus").slot] = 2;
        th.p[VM.THREAD.field("eetop").slot] = 0;
        signal(t, th, true);
        VM.unlock(t, th);
        if (th.p[VM.THREAD.field("daemon").slot] == 0) {
            synchronized (exit) {
                live.decrementAndGet();
                exit.notifyAll();
            }
        }
    }

    static void finish0(T t) {
        synchronized (exit) {
            while (live.get() > 0) {
                try { exit.wait(); } catch (InterruptedException e) { }
            }
        }
    }

    static void init() {
        Natives.reg("java/lang/Object.wait(J)V", (t, b) -> await(t, t.R[b], t.P[b + 1]));
        Natives.reg("java/lang/Object.notify", (t, b) -> signal(t, t.R[b], false));
        Natives.reg("java/lang/Object.notifyAll", (t, b) -> signal(t, t.R[b], true));

        String c = "java/lang/Thread.";
        Natives.reg(c + "currentThread", (t, b) -> t.R[b] = t.thread);
        Natives.reg(c + "setPriority0", Natives.NOP);
        Natives.reg(c + "setNativeName", Natives.NOP);
        Natives.reg(c + "yield", (t, b) -> Thread.yield());
        Natives.reg(c + "isAlive", (t, b) -> t.P[b] = t.R[b].p[VM.THREAD.field("eetop").slot] != 0 ? 1 : 0);
        Natives.reg(c + "holdsLock", (t, b) -> t.P[b] = t.R[b].owner == t ? 1 : 0);
        Natives.reg(c + "sleep", (t, b) -> sleep(t, t.P[b]));
        Natives.reg(c + "interrupt0", (t, b) -> { if (t.R[b].x instanceof T o) interrupt(o); });
        Natives.reg(c + "clearInterruptEvent", Natives.NOP);
        Natives.reg(c + "start0", (t, b) -> {
            Obj th = t.R[b];
            T n = new T(1 << 17);
            n.thread = th;
            th.x = n;
            boolean daemon = th.p[VM.THREAD.field("daemon").slot] != 0;
            if (!daemon) live.incrementAndGet();
            th.p[VM.THREAD.field("threadStatus").slot] = 5;
            th.p[VM.THREAD.field("eetop").slot] = 1;
            n.host = new Thread(null, () -> run(n), VM.str(th.r[VM.THREAD.field("name").slot]), 256L << 20);
            n.host.setDaemon(true);
            n.host.start();
        });
        Natives.reg(c + "getThreads", (t, b) -> t.R[b] = new Obj(VM.arrayOf(VM.THREAD), new Obj[]{t.thread}));

        String r = "java/lang/ref/Reference.";
        Natives.reg(r + "getAndClearReferencePendingList", (t, b) -> t.R[b] = null);
        Natives.reg(r + "hasReferencePendingList", (t, b) -> t.P[b] = 0);
        Natives.reg(r + "waitForReferencePendingList", (t, b) -> {
            try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) { }
        });
        Natives.reg(r + "refersTo0", (t, b) -> t.P[b] = t.R[b].r[0] == t.R[b + 1] ? 1 : 0);
        Natives.reg("java/security/AccessController.getProtectionDomain", (t, b) -> t.R[b] = VM.klass(t.R[b]).pd);
        Natives.reg("java/lang/ref/PhantomReference.refersTo0", (t, b) -> t.P[b] = t.R[b].r[0] == t.R[b + 1] ? 1 : 0);
        Natives.reg(r + "clear0", (t, b) -> t.R[b].r[0] = null);
        Natives.reg("java/lang/ref/Finalizer.isFinalizationEnabled", (t, b) -> t.P[b] = 0);

        Natives.reg("java/security/AccessController.getStackAccessControlContext", (t, b) -> t.R[b] = null);
        Natives.reg("java/security/AccessController.getInheritedAccessControlContext", (t, b) -> t.R[b] = null);
        Natives.reg("java/security/AccessController.ensureMaterializedForStackWalk", Natives.NOP);
    }
}
