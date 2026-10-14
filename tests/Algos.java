import java.util.*;

public class Algos {
    static void quick(int[] a, int lo, int hi) {
        if (lo >= hi) return;
        int p = a[(lo + hi) >>> 1], i = lo, j = hi;
        while (i <= j) { while (a[i] < p) i++; while (a[j] > p) j--; if (i <= j) { int t = a[i]; a[i] = a[j]; a[j] = t; i++; j--; } }
        quick(a, lo, j); quick(a, i, hi);
    }
    static int queens(int n, int row, int cols, int d1, int d2) {
        if (row == n) return 1;
        int c = 0;
        for (int i = 0; i < n; i++) { int a = 1 << i, b = 1 << (row + i), e = 1 << (row - i + n); if ((cols & a) == 0 && (d1 & b) == 0 && (d2 & e) == 0) c += queens(n, row + 1, cols | a, d1 | b, d2 | e); }
        return c;
    }
    static void permute(char[] s, int k, List<String> out) { if (k == s.length) { out.add(new String(s)); return; } for (int i = k; i < s.length; i++) { char t = s[k]; s[k] = s[i]; s[i] = t; permute(s, k + 1, out); t = s[k]; s[k] = s[i]; s[i] = t; } }

    public static void main(String[] args) {
        int[] a = new int[20000];
        long seed = 12345;
        for (int i = 0; i < a.length; i++) { seed = seed * 6364136223846793005L + 1442695040888963407L; a[i] = (int) (seed >>> 40) % 100000; }
        quick(a, 0, a.length - 1);
        boolean ok = true;
        for (int i = 1; i < a.length; i++) ok &= a[i - 1] <= a[i];
        System.out.println(ok + " " + a[0] + " " + a[a.length / 2] + " " + a[a.length - 1] + " " + Arrays.binarySearch(a, a[777]));
        boolean[] comp = new boolean[200001];
        int primes = 0;
        for (int i = 2; i <= 200000; i++) { if (!comp[i]) { primes++; for (long j = (long) i * i; j <= 200000; j += i) comp[(int) j] = true; } }
        System.out.println(primes + " " + queens(8, 0, 0, 0, 0));
        List<String> perms = new ArrayList<>();
        permute("abcd".toCharArray(), 0, perms);
        System.out.println(perms.size() + " " + perms.get(5) + " " + new TreeSet<>(perms).last());
        double[][] m = new double[30][30], r = new double[30][30];
        for (int i = 0; i < 30; i++) for (int j = 0; j < 30; j++) m[i][j] = (i * 31 + j * 17) % 10 / 10.0;
        for (int i = 0; i < 30; i++) for (int j = 0; j < 30; j++) { double s = 0; for (int k = 0; k < 30; k++) s += m[i][k] * m[k][j]; r[i][j] = s; }
        System.out.println(r[3][7] + " " + r[29][29]);
        Map<String, Integer> freq = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) { String k = "k" + (i * 7919 % 613); freq.merge(k, 1, Integer::sum); if (i % 1000 == 0) sb.append(k); }
        System.out.println(freq.size() + " " + freq.get("k5") + " " + sb);
        StringBuilder out = new StringBuilder();
        for (int y = -8; y <= 8; y += 2) { for (int x = -30; x <= 10; x += 2) { double cr = x / 15.0, ci = y / 8.0, zr = 0, zi = 0; int it = 0; while (zr * zr + zi * zi < 4 && it < 50) { double t = zr * zr - zi * zi + cr; zi = 2 * zr * zi + ci; zr = t; it++; } out.append(it == 50 ? '#' : it > 5 ? '+' : '.'); } out.append('\n'); }
        System.out.print(out);
        PriorityQueue<int[]> pq = new PriorityQueue<>((p, q) -> p[0] != q[0] ? Integer.compare(p[0], q[0]) : Integer.compare(q[1], p[1]));
        for (int i = 0; i < 50; i++) pq.add(new int[]{i * 37 % 11, i});
        ArrayDeque<Integer> dq = new ArrayDeque<>();
        while (pq.size() > 45) dq.addFirst(pq.poll()[1]);
        LinkedList<Integer> ll = new LinkedList<>(dq);
        ll.removeIf(v -> v % 2 == 0);
        BitSet bs = new BitSet();
        for (int i = 0; i < 100; i += 7) bs.set(i);
        System.out.println(dq + " " + ll + " " + bs.cardinality() + " " + bs.nextSetBit(50) + " " + Collections.max(dq) + " " + Collections.frequency(List.of(1, 2, 1), 1));
        long fibs = 0, x = 0, y = 1;
        for (int i = 0; i < 80; i++) { long t = x + y; x = y; y = t; fibs ^= t; }
        System.out.println(fibs + " " + gcd(1071, 462) + " " + Integer.toBinaryString(collatz(27)));
    }
    static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }
    static int collatz(long n) { int s = 0; while (n != 1) { n = n % 2 == 0 ? n / 2 : 3 * n + 1; s++; } return s; }
}
