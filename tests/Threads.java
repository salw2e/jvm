import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class Threads {
    static int counter;
    static synchronized void inc() { counter++; }

    public static void main(String[] args) throws Exception {
        Thread[] ts = new Thread[4];
        for (int i = 0; i < ts.length; i++) { ts[i] = new Thread(() -> { for (int j = 0; j < 20000; j++) inc(); }, "w" + i); ts[i].start(); }
        for (Thread t : ts) t.join();
        System.out.println(counter);

        AtomicInteger at = new AtomicInteger();
        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 10; i++) { int n = i; fs.add(pool.submit(() -> { at.addAndGet(n); map.merge(n % 3, 1, Integer::sum); return n * n; })); }
        int sum = 0;
        for (Future<Integer> f : fs) sum += f.get();
        pool.shutdown();
        System.out.println(pool.awaitTermination(5, TimeUnit.SECONDS) + " " + sum + " " + at.get() + " " + new TreeMap<>(map));

        BlockingQueue<String> q = new ArrayBlockingQueue<>(2);
        Thread prod = new Thread(() -> { try { for (int i = 0; i < 5; i++) q.put("m" + i); q.put("end"); } catch (InterruptedException e) { } });
        prod.start();
        StringBuilder sb = new StringBuilder();
        for (String s; !(s = q.take()).equals("end"); ) sb.append(s);
        System.out.println(sb);

        Object lock = new Object();
        boolean[] ready = {false};
        Thread waiter = new Thread(() -> { synchronized (lock) { while (!ready[0]) { try { lock.wait(); } catch (InterruptedException e) { } } System.out.println("woken"); } });
        waiter.start();
        Thread.sleep(50);
        synchronized (lock) { ready[0] = true; lock.notifyAll(); }
        waiter.join();

        Thread sleeper = new Thread(() -> { try { Thread.sleep(10000); System.out.println("no"); } catch (InterruptedException e) { System.out.println("interrupted " + Thread.currentThread().isInterrupted()); } });
        sleeper.start();
        sleeper.interrupt();
        sleeper.join();
        System.out.println(sleeper.isAlive() + " " + sleeper.getState() + " " + Thread.currentThread().getName());

        CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> "cf").thenApply(s -> s + "!").thenCombine(CompletableFuture.completedFuture(1), (s, i) -> s + i);
        System.out.println(cf.get());
        ThreadLocal<Integer> tl = ThreadLocal.withInitial(() -> 5);
        Thread t2 = new Thread(() -> tl.set(9));
        t2.start();
        t2.join();
        System.out.println(tl.get());
        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) new Thread(latch::countDown).start();
        System.out.println(latch.await(5, TimeUnit.SECONDS));
        Thread d = new Thread(() -> { while (true) { } });
        d.setDaemon(true);
        d.start();
        Thread bad = new Thread(() -> { throw new IllegalStateException("boom"); }, "bad");
        bad.setUncaughtExceptionHandler((th, e) -> System.out.println(th.getName() + " died: " + e.getMessage()));
        bad.start();
        bad.join();
    }
}
