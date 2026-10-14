import java.util.*;

public class Basics {
    interface Shape { double area(); default String describe() { return getClass().getSimpleName() + " " + area(); } }
    static class Circle implements Shape { double r; Circle(double r) { this.r = r; } public double area() { return Math.PI * r * r; } }
    static class Square implements Shape { double s; Square(double s) { this.s = s; } public double area() { return s * s; } public String toString() { return "Square(" + s + ")"; } }
    enum Color { RED, GREEN, BLUE; Color next() { return values()[(ordinal() + 1) % values().length]; } }

    static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }

    public static void main(String[] args) {
        System.out.println(fib(20));
        long big = 1L << 40;
        System.out.println(big * 3 + " " + (big >>> 3) + " " + (-big % 7) + " " + Long.MAX_VALUE);
        System.out.println(7 / 2 + " " + -7 / 2 + " " + 7 % -3 + " " + (1 << 33) + " " + (-1 >>> 28) + " " + (byte) 200 + " " + (char) 97 + " " + (short) 70000);
        System.out.println(1.0 / 3 + " " + (float) 1 / 3 + " " + 0.1 + 0.2 + " " + Double.NaN + " " + 1e300 * 1e10 + " " + (int) 3.99 + " " + (long) -2.5 + " " + (int) Double.NaN + " " + (int) 1e20);
        System.out.println(Math.sqrt(2) + " " + Math.pow(2, 0.5) + " " + Math.sin(1) + " " + Math.floorMod(-7, 3) + " " + Math.round(2.5) + " " + Math.abs(-0.0));
        int[] a = {5, 3, 9, 1, 7};
        Arrays.sort(a);
        System.out.println(Arrays.toString(a) + " " + Arrays.stream(a).sum());
        int[][] grid = new int[3][4];
        grid[1][2] = 42;
        System.out.println(Arrays.deepToString(grid));
        List<Shape> shapes = new ArrayList<>();
        shapes.add(new Circle(1));
        shapes.add(new Square(2));
        for (Shape s : shapes) System.out.println(s.describe());
        System.out.println(shapes.get(1));
        Map<String, Integer> counts = new TreeMap<>();
        for (String w : "the quick brown fox jumps over the lazy dog the end".split(" ")) counts.merge(w, 1, Integer::sum);
        System.out.println(counts);
        Map<Color, String> em = new EnumMap<>(Color.class);
        for (Color c : Color.values()) em.put(c, c.next().name().toLowerCase());
        System.out.println(em + " " + Color.valueOf("GREEN").ordinal());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i).append(',');
        sb.setLength(sb.length() - 1);
        System.out.println(sb.reverse());
        String s = "Hello, Wörld ✓ 日本";
        System.out.println(s.length() + " " + s.toUpperCase() + " " + s.indexOf("W") + " " + s.substring(7) + " " + s.hashCode() + " " + s.contains("✓"));
        System.out.println(String.format("%5d|%-5s|%08.3f|%x|%,d", 42, "ab", 3.14159, 255, 1234567));
        System.out.println(Integer.toBinaryString(10) + " " + Integer.parseInt("-123") + " " + Long.toHexString(-1L) + " " + Double.parseDouble("1e-3") + " " + Integer.MAX_VALUE + 1);
        Object o = shapes;
        System.out.println(o instanceof List && !(o instanceof Map));
        switch (s.substring(0, 5)) {
            case "Hello" -> System.out.println("hi");
            default -> System.out.println("?");
        }
        char c = 'x';
        c += 1;
        System.out.println(c + " " + (int) c + " " + Character.isLetter(c) + " " + (c > 'a' ? "yes" : "no"));
        System.out.println(Objects.hash(1, 2, 3) + " " + List.of(1, 2, 3).hashCode() + " " + "abc".repeat(2) + " " + String.join("-", "a", "b"));
    }
}
