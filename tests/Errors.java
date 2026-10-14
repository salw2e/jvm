import java.util.*;

public class Errors {
    static class MyEx extends Exception { MyEx(String m, Throwable c) { super(m, c); } }

    static int depth(int n) { return depth(n + 1) + 1; }

    static void thrower(int i) throws MyEx {
        try {
            if (i == 0) throw new IllegalStateException("zero");
            Object o = "str";
            Integer n = (Integer) o;
        } catch (IllegalStateException e) {
            throw new MyEx("wrapped", e);
        } finally {
            System.out.println("finally " + i);
        }
    }

    public static void main(String[] args) {
        try { thrower(0); } catch (MyEx e) { System.out.println(e + " <- " + e.getCause().getMessage()); }
        try { thrower(1); } catch (Exception e) { System.out.println(e.getClass().getName()); }
        try { int[] a = new int[2]; a[2] = 1; } catch (ArrayIndexOutOfBoundsException e) { System.out.println(e.getMessage()); }
        try { Object s = null; s.toString(); } catch (NullPointerException e) { System.out.println("npe"); }
        try { System.out.println(1 / (args.length)); } catch (ArithmeticException e) { System.out.println(e.getMessage()); }
        try { new ArrayList<Integer>().iterator().next(); } catch (NoSuchElementException e) { System.out.println("empty"); }
        try { Object[] o = new String[1]; o[0] = 1; } catch (ArrayStoreException e) { System.out.println(e.getMessage()); }
        try { int[] n = new int[-1]; } catch (NegativeArraySizeException e) { System.out.println(e.getMessage()); }
        try { depth(0); } catch (StackOverflowError e) { System.out.println("overflow"); }
        try { List.of(1).add(2); } catch (UnsupportedOperationException e) { System.out.println("immutable"); }
        try { Integer.parseInt("x1"); } catch (NumberFormatException e) { System.out.println(e.getMessage()); }
        try { throw new Error("custom"); } catch (Throwable e) {
            StackTraceElement[] st = e.getStackTrace();
            System.out.println(st[0].getMethodName() + " " + st[0].getClassName() + " " + st[0].getFileName() + ":" + st[0].getLineNumber() + " " + st.length);
        }
        System.out.println(test());
        throw new RuntimeException("bye");
    }

    @SuppressWarnings("finally")
    static int test() {
        try { return 1; } finally { System.out.println("in finally"); }
    }
}
