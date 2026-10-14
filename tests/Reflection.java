import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

public class Reflection {
    @Retention(RetentionPolicy.RUNTIME) @interface Info { String name(); int level() default 3; }
    interface Greeter { String greet(String who); }

    @Info(name = "sample")
    public static class Sample {
        public int a = 1;
        private String b = "hidden";
        public static final long C = 99L;
        public Sample() { }
        public Sample(int a) { this.a = a; }
        @Info(name = "m", level = 7) public String hello(String s, int n) { return s.repeat(n) + a; }
        private static int secret(int x) { return x * 2; }
        public void fail() { throw new IllegalStateException("inner"); }
    }

    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("Reflection$Sample");
        System.out.println(c.getName() + " " + c.getSimpleName() + " " + Modifier.toString(c.getModifiers()) + " " + c.getSuperclass() + " " + c.getDeclaringClass().getName());
        Object o = c.getConstructor(int.class).newInstance(5);
        Method m = c.getMethod("hello", String.class, int.class);
        System.out.println(m.invoke(o, "ab", 2) + " " + m.getReturnType().getName() + " " + Arrays.toString(m.getParameterTypes()));
        Method s = c.getDeclaredMethod("secret", int.class);
        s.setAccessible(true);
        System.out.println(s.invoke(null, 21));
        Field b = c.getDeclaredField("b");
        b.setAccessible(true);
        b.set(o, "changed");
        System.out.println(b.get(o) + " " + c.getField("a").getInt(o) + " " + c.getField("C").get(null));
        List<String> names = new ArrayList<>();
        for (Method x : c.getDeclaredMethods()) names.add(x.getName());
        Collections.sort(names);
        System.out.println(names + " " + c.getDeclaredFields().length + " " + c.getConstructors().length);
        Info info = c.getAnnotation(Info.class);
        Info mi = m.getAnnotation(Info.class);
        System.out.println(info.name() + info.level() + " " + mi.name() + mi.level() + " " + c.isAnnotationPresent(Info.class));
        try { c.getMethod("fail").invoke(o); } catch (InvocationTargetException e) { System.out.println(e.getCause()); }
        try { c.getMethod("nope"); } catch (NoSuchMethodException e) { System.out.println("no method"); }
        try { Class.forName("does.not.Exist"); } catch (ClassNotFoundException e) { System.out.println("cnf " + e.getMessage()); }
        Greeter g = (Greeter) Proxy.newProxyInstance(Reflection.class.getClassLoader(), new Class<?>[]{Greeter.class}, (proxy, method, a) -> method.getName() + ":" + a[0]);
        System.out.println(g.greet("bob"));
        int[] arr = (int[]) Array.newInstance(int.class, 3);
        Array.setInt(arr, 1, 8);
        System.out.println(Array.get(arr, 1) + " " + arr.getClass().getName() + " " + String[][].class.getName() + " " + int.class + " " + Integer.TYPE.isPrimitive());
        System.out.println(List.class.isAssignableFrom(ArrayList.class) + " " + Number.class.isInstance(3) + " " + Runnable.class.isInterface() + " " + new int[0].getClass().getComponentType());
        Object e = Enum.valueOf((Class) Thread.State.class, "BLOCKED");
        System.out.println(e + " " + Thread.State.class.isEnum() + " " + Sample.class.getPackageName().isEmpty());
    }
}
