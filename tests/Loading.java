import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;

public class Loading {
    static class Mine extends ClassLoader {
        Mine(ClassLoader parent) { super(parent); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            try (InputStream in = new FileInputStream("tout/" + name.substring(name.indexOf('.') + 1) + ".class")) {
                byte[] b = in.readAllBytes();
                return defineClass(null, b, 0, b.length);
            } catch (IOException e) { throw new ClassNotFoundException(name); }
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals("Hello")) { Class<?> c = findLoadedClass(name); return c != null ? c : findClass("x." + name); }
            return super.loadClass(name, resolve);
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader app = Loading.class.getClassLoader();
        System.out.println(app.getClass().getName() + " " + (app == ClassLoader.getSystemClassLoader()) + " " + String.class.getClassLoader() + " " + app.getParent().getClass().getSimpleName() + " " + (Thread.currentThread().getContextClassLoader() == app));
        try (InputStream in = Loading.class.getResourceAsStream("Loading.class")) { System.out.println(Integer.toHexString(new DataInputStream(in).readInt())); }
        System.out.println(app.getResource("Hello.class").getProtocol() + " " + (app.getResource("nope.txt") == null) + " " + Collections.list(app.getResources("Hello.class")).size());
        Mine a = new Mine(app), b = new Mine(app);
        Class<?> ha = a.loadClass("Hello"), hb = b.loadClass("Hello"), h = Class.forName("Hello");
        System.out.println((ha != hb) + " " + (ha != h) + " " + (ha.getClassLoader() == a) + " " + ha.getName() + " " + (a.loadClass("Hello") == ha) + " " + (a.loadClass("Basics") == Class.forName("Basics")));
        ha.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        try (URLClassLoader u = new URLClassLoader(new URL[]{new File("tout/lib.jar").toURI().toURL()}, null)) {
            Class<?> c = u.loadClass("Concat");
            System.out.println((c.getClassLoader() == u) + " " + (c != Class.forName("Concat")) + " " + new String(u.getResourceAsStream("res/data.txt").readAllBytes()).trim() + " " + c.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith("lib.jar"));
            c.getMethod("main", String[].class).invoke(null, (Object) new String[]{"1", "2"});
            try { u.loadClass("Missing"); } catch (ClassNotFoundException e) { System.out.println("cnf"); }
        }
        try (java.util.jar.JarFile jf = new java.util.jar.JarFile("tout/lib.jar")) {
            List<String> names = new ArrayList<>();
            jf.stream().forEach(e -> names.add(e.getName()));
            Collections.sort(names);
            System.out.println(names + " " + jf.getManifest().getMainAttributes().getValue("Created-By").isEmpty());
        }
        ServiceLoader<Runnable> sl = ServiceLoader.load(Runnable.class);
        System.out.println(sl.iterator().hasNext() + " " + Package.getPackage("java.lang").getName() + " " + Loading.class.getPackage().getName().isEmpty() + " " + Loading.class.getModule().isNamed());
        try { Class.forName("no.Such"); } catch (ClassNotFoundException e) { System.out.println(e.getMessage()); }
        try { new Broken(); } catch (NoClassDefFoundError e) { System.out.println("ncdfe " + e.getMessage()); }
        try { Init.touch(); } catch (ExceptionInInitializerError e) { System.out.println("eiie " + e.getCause().getMessage()); }
        try { Init.touch(); } catch (NoClassDefFoundError e) { System.out.println(e.getMessage()); }
    }
    static class Init { static { if (true) throw new IllegalStateException("static boom"); } static void touch() { } }
}
class Broken { Gone g = new Gone(); }
class Gone { }
