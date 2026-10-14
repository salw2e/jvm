import java.util.*;

public class Classes {
    static StringBuilder log = new StringBuilder();
    static abstract class Animal implements Comparable<Animal>, Cloneable {
        static int count;
        static { log.append("[Animal static]"); }
        { log.append("[Animal init]"); }
        final String name;
        int[] data = {1, 2};
        Animal(String n) { name = n; count++; log.append("[Animal ctor]"); speak(); }
        abstract String sound();
        void speak() { log.append(name + ":" + sound() + ";"); }
        public int compareTo(Animal o) { return name.compareTo(o.name); }
        @Override public boolean equals(Object o) { return o instanceof Animal a && a.name.equals(name); }
        @Override public int hashCode() { return name.hashCode(); }
        @Override public String toString() { return getClass().getSimpleName() + "(" + name + ")"; }
        @Override protected Animal clone() throws CloneNotSupportedException { return (Animal) super.clone(); }
    }
    static class Dog extends Animal {
        String trick = "sit";
        static { log.append("[Dog static]"); }
        Dog(String n) { super(n); log.append("[Dog ctor]"); }
        String sound() { return "woof" + trick; }
    }
    static class Puppy extends Dog {
        Puppy(String n) { super(n + "jr"); }
        @Override String sound() { return "yip"; }
        @Override void speak() { super.speak(); log.append("(puppy)"); }
    }
    interface Stack<T> extends Iterable<T> {
        void push(T t);
        T pop();
        static <T> Stack<T> create() {
            return new Stack<T>() {
                private final ArrayList<T> items = new ArrayList<>();
                public void push(T t) { items.add(t); }
                public T pop() { return items.remove(items.size() - 1); }
                public Iterator<T> iterator() { return items.iterator(); }
            };
        }
        default int size() { int n = 0; for (T t : this) n++; return n; }
    }
    class Inner { int v = outerField * 2; int get() { return v + Classes.this.outerField; } }
    int outerField = 21;
    static int varargs(String label, int... xs) { int s = 0; for (int x : xs) s += x; return s + label.length(); }
    static long over(long x) { return 1; }
    static long over(Integer x) { return 2; }
    static long over(Object x) { return 3; }
    static long over(int... x) { return 4; }

    public static void main(String[] args) throws Exception {
        Animal d = new Dog("rex"), p = new Puppy("rex");
        System.out.println(log);
        System.out.println(d + " " + p + " " + Animal.count + " " + d.equals(new Dog("rex")) + " " + d.compareTo(p) + " " + (d instanceof Dog) + " " + (d instanceof Puppy) + " " + p.sound());
        Animal cl = d.clone();
        cl.data[0] = 9;
        System.out.println((cl != d) + " " + cl.equals(d) + " " + d.data[0] + " " + cl.getClass().getName());
        Stack<String> st = Stack.create();
        st.push("a"); st.push("b"); st.push("c");
        System.out.println(st.pop() + st.size() + " " + st.getClass().getName() + " " + st.getClass().isAnonymousClass());
        Classes outer = new Classes();
        Classes.Inner in = outer.new Inner();
        System.out.println(in.get() + " " + varargs("ab") + " " + varargs("ab", 1, 2, 3) + " " + over(1) + over((short) 1) + over(Integer.valueOf(1)) + over("s") + over());
        class Local { String hi() { return "local" + args.length; } }
        System.out.println(new Local().hi() + " " + new Object() { int twice(int x) { return 2 * x; } }.twice(4));
        Set<Animal> set = new HashSet<>(List.of(d));
        System.out.println(set.contains(new Dog("rex")) + " " + new TreeSet<>(List.of(new Dog("b"), new Dog("a"))).first());
        Object[] objs = {1, "s", 2.0, 'c', null, new int[0], Color.RED};
        StringBuilder sb = new StringBuilder();
        for (Object o : objs) sb.append(o == null ? "null" : o.getClass().getSimpleName()).append(' ');
        System.out.println(sb);
        outer: for (int i = 0; i < 5; i++) { for (int j = 0; j < 5; j++) { if (j == 3) continue outer; if (i == 2) break outer; System.out.print(i * 10 + j + " "); } }
        System.out.println();
        int x = 0; x += x++ + ++x; int y = x-- - --x;
        System.out.println(x + " " + y + " " + (x > 0 ? "pos" : x < 0 ? "neg" : "zero") + " " + ((Object) "s" == "s") + " " + (new String("s") == "s") + " " + (new String("s").intern() == "s"));
        switch (3) { case 1: System.out.print("one "); case 3: System.out.print("three "); case 4: System.out.print("four "); break; default: System.out.print("dflt"); }
        System.out.println(Color.valueOf("RED").ordinal() + " " + Color.BLUE.compareTo(Color.RED));
    }
    enum Color { RED, GREEN, BLUE }
}
