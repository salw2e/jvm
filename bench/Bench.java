import java.util.*;

public class Bench {
    static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }

    public static void main(String[] args) {
        for (int round = 0; round < (args.length > 0 ? Integer.parseInt(args[0]) : 1); round++) run();
    }

    static void run() {
        long t0 = System.nanoTime();
        int f = fib(32);
        long t1 = System.nanoTime();
        int primes = 0;
        boolean[] comp = new boolean[5_000_001];
        for (int i = 2; i <= 5_000_000; i++) { if (!comp[i]) { primes++; for (long j = (long) i * i; j <= 5_000_000; j += i) comp[(int) j] = true; } }
        long t2 = System.nanoTime();
        HashMap<Integer, Integer> m = new HashMap<>();
        for (int i = 0; i < 300_000; i++) m.merge(i % 5000, 1, Integer::sum);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100_000; i++) sb.append(i % 10);
        long t3 = System.nanoTime();
        double x = 0;
        for (int i = 1; i < 3_000_000; i++) x += Math.sqrt(i) / (i + 1.0);
        long t4 = System.nanoTime();
        System.out.println(f + " " + primes + " " + m.size() + " " + sb.length() + " " + (long) x);
        System.out.printf("fib %d ms, sieve %d ms, map+string %d ms, float %d ms%n", (t1 - t0) / 1000000, (t2 - t1) / 1000000, (t3 - t2) / 1000000, (t4 - t3) / 1000000);
    }
}
