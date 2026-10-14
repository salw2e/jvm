import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class Lambdas {
    interface Op { int apply(int a, int b); }
    static int twice(IntUnaryOperator f, int x) { return f.applyAsInt(f.applyAsInt(x)); }
    static <T, R> List<R> map(List<T> in, Function<? super T, ? extends R> f) { List<R> out = new ArrayList<>(); for (T t : in) out.add(f.apply(t)); return out; }

    public static void main(String[] args) {
        Op add = (a, b) -> a + b, mul = (a, b) -> a * b;
        System.out.println(add.apply(3, 4) + " " + mul.apply(3, 4) + " " + twice(x -> x * x, 3));
        int base = 10;
        Supplier<Integer> sup = () -> base + args.length;
        Function<Integer, Function<Integer, Integer>> curried = a -> b -> a * b + base;
        System.out.println(sup.get() + " " + curried.apply(2).apply(5));
        List<String> words = Arrays.asList("delta", "alpha", "charlie", "bravo", "echo", "alpha");
        System.out.println(words.stream().filter(w -> w.length() > 4).map(String::toUpperCase).sorted().distinct().collect(Collectors.toList()));
        Map<Integer, Long> byLen = words.stream().collect(Collectors.groupingBy(String::length, TreeMap::new, Collectors.counting()));
        System.out.println(byLen);
        System.out.println(IntStream.rangeClosed(1, 10).filter(i -> i % 2 == 1).mapToObj(Integer::toString).collect(Collectors.joining("+", "[", "]")));
        System.out.println(Stream.iterate(1L, x -> x * 3).limit(12).reduce(0L, Long::sum));
        System.out.println(words.stream().mapToInt(String::length).summaryStatistics());
        Optional<String> first = words.stream().filter(w -> w.startsWith("z")).findFirst();
        System.out.println(first.map(String::length).orElse(-1) + " " + first.isPresent());
        System.out.println(map(words, w -> w.charAt(0)));
        Comparator<String> cmp = Comparator.comparing(String::length).thenComparing(Comparator.reverseOrder());
        List<String> sorted = new ArrayList<>(words);
        sorted.sort(cmp);
        System.out.println(sorted);
        BiFunction<Double, Double, Double> pow = Math::pow;
        UnaryOperator<String> up = String::trim;
        System.out.println(pow.apply(2.0, 10.0) + " [" + up.andThen(s -> s + "!").apply("  hi ") + "]");
        Runnable r = () -> System.out.println("run " + words.size());
        r.run();
        Map<Boolean, List<Integer>> parts = IntStream.range(0, 10).boxed().collect(Collectors.partitioningBy(i -> i % 3 == 0));
        System.out.println(parts);
        System.out.println(Stream.of("a", "bb", "ccc").flatMap(s -> s.chars().mapToObj(c -> (char) c)).count());
        Function<Integer, Integer>[] fs = new Function[3];
        for (int i = 0; i < 3; i++) { int k = i; fs[i] = x -> x + k; }
        System.out.println(fs[0].apply(1) + fs[1].apply(1) + fs[2].apply(1));
        System.out.println(DoubleStream.of(1.5, 2.5, 3.0).average().getAsDouble() + " " + Stream.of(3, 1, 2).max(Integer::compare).get());
        new Lambdas().inst();
    }

    int field = 7;
    void inst() {
        Supplier<Integer> s = () -> field * 2;
        Function<String, Lambdas> ctor = x -> new Lambdas();
        Supplier<List<String>> mk = ArrayList::new;
        List<String> l = mk.get();
        l.add("x");
        System.out.println(s.get() + " " + ctor.apply("").field + " " + l);
    }
}
