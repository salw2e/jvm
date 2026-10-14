import java.io.*;
import java.util.*;

public class FileIO {
    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "jvmtest" + System.nanoTime());
        System.out.println(dir.mkdir() + " " + dir.isDirectory());
        File f = new File(dir, "a.txt");
        try (PrintWriter w = new PrintWriter(new FileWriter(f))) { for (int i = 0; i < 5; i++) w.println("line " + i + " ✓"); }
        System.out.println(f.exists() + " " + f.length() + " " + f.getName() + " " + f.getParentFile().equals(dir));
        try (BufferedReader r = new BufferedReader(new FileReader(f))) { String l; int n = 0; while ((l = r.readLine()) != null) n += l.length(); System.out.println(n); }
        try (FileOutputStream o = new FileOutputStream(f, true)) { o.write("tail\n".getBytes("UTF-8")); }
        try (FileInputStream in = new FileInputStream(f)) { byte[] all = in.readAllBytes(); System.out.println(all.length + " " + new String(all, "UTF-8").endsWith("tail\n")); }
        try (DataOutputStream d = new DataOutputStream(new FileOutputStream(new File(dir, "b.bin")))) { d.writeInt(42); d.writeDouble(2.5); d.writeUTF("héllo"); d.writeLong(-1); }
        try (DataInputStream d = new DataInputStream(new FileInputStream(new File(dir, "b.bin")))) { System.out.println(d.readInt() + " " + d.readDouble() + " " + d.readUTF() + " " + d.readLong()); }
        try (RandomAccessFile raf = new RandomAccessFile(new File(dir, "b.bin"), "rw")) { raf.seek(4); System.out.println(raf.readDouble() + " " + raf.length()); raf.seek(0); raf.writeInt(7); raf.seek(0); System.out.println(raf.readInt()); }
        String[] names = dir.list();
        Arrays.sort(names);
        System.out.println(Arrays.toString(names));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ObjectOutputStream oo = new ObjectOutputStream(bo)) { oo.writeObject(new ArrayList<>(List.of("x", 1, 2.5))); oo.writeObject(new int[]{1, 2}); }
        try (ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(bo.toByteArray()))) { System.out.println(oi.readObject() + " " + ((int[]) oi.readObject()).length); }
        try { new FileInputStream(new File(dir, "missing")); } catch (FileNotFoundException e) { System.out.println("missing"); }
        for (File x : dir.listFiles()) x.delete();
        System.out.println(dir.delete() + " " + dir.exists());
        Scanner sc = new Scanner("12 abc 3.5\nnext line");
        System.out.println(sc.nextInt() + sc.next() + sc.nextDouble() + sc.nextLine() + "|" + sc.nextLine());
        StringWriter sw = new StringWriter();
        new RuntimeException("trace").printStackTrace(new PrintWriter(sw));
        System.out.println(sw.toString().split("\n")[0] + " " + sw.toString().contains("FileIO.main"));
        System.err.println("to stderr");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println(in.readLine());
    }
}
