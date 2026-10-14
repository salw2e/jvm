import java.util.*;

public class Modern {
    record Point(int x, int y) { Point { if (x < 0) throw new IllegalArgumentException("neg"); } double dist() { return Math.sqrt(x * x + y * y); } }
    record Pair<A, B>(A first, B second) { }
    sealed interface Expr permits Num, Add, Mul { }
    record Num(int v) implements Expr { }
    record Add(Expr l, Expr r) implements Expr { }
    record Mul(Expr l, Expr r) implements Expr { }

    static int eval(Expr e) {
        if (e instanceof Num n) return n.v();
        if (e instanceof Add a) return eval(a.l()) + eval(a.r());
        if (e instanceof Mul m) return eval(m.l()) * eval(m.r());
        throw new IllegalStateException();
    }

    enum Planet {
        MERCURY(3.303e+23, 2.4397e6), EARTH(5.976e+24, 6.37814e6);
        final double mass, radius;
        Planet(double m, double r) { mass = m; radius = r; }
        double gravity() { return 6.67300E-11 * mass / (radius * radius); }
    }

    enum Op {
        PLUS("+") { int apply(int a, int b) { return a + b; } }, TIMES("*") { int apply(int a, int b) { return a * b; } };
        final String sym;
        Op(String s) { sym = s; }
        abstract int apply(int a, int b);
    }

    static String kind(Object o) {
        if (o == null) return "null";
        return o instanceof Integer i && i > 10 ? "big" : o instanceof String s ? "str" + s.length() : "other";
    }

    static String day(int d) {
        return switch (d) {
            case 1, 7 -> "weekend";
            case 2, 3, 4, 5, 6 -> { String s = "week"; yield s + "day"; }
            default -> throw new IllegalArgumentException("day " + d);
        };
    }

    public static void main(String[] args) {
        Point p = new Point(3, 4);
        System.out.println(p + " " + p.dist() + " " + p.equals(new Point(3, 4)) + " " + (p.hashCode() == new Point(3, 4).hashCode()) + " " + new Pair<>("a", 1));
        try { new Point(-1, 0); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
        System.out.println(eval(new Add(new Num(2), new Mul(new Num(3), new Num(4)))));
        for (Planet pl : Planet.values()) System.out.printf("%s %.2f%n", pl, pl.gravity());
        for (Op o : Op.values()) System.out.println(o + o.sym + o.apply(6, 7) + " " + o.getClass() + " " + o.getDeclaringClass());
        System.out.println(day(1) + " " + day(4) + " " + kind(null) + kind(11) + kind("abc") + kind(2.0));
        var list = new ArrayList<Map<String, Integer>>();
        list.add(Map.of("k", 1));
        for (var m : list) System.out.println(m);
        String text = """
            Hello,
              "block" \t tab
            end\
            """;
        System.out.println(text + "|" + text.lines().count());
        System.out.println(EnumSet.range(Planet.MERCURY, Planet.EARTH) + " " + Planet.valueOf("EARTH").compareTo(Planet.MERCURY));
        Object o = 42;
        if (o instanceof Integer i && i > 40) System.out.println("int " + (i + 1));
        System.out.println("x".isBlank() + " " + " y ".strip() + " " + "a,b".transform(s -> s.split(",").length) + " " + String.valueOf((Object) null));
    }
}
