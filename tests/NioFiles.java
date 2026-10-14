import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class NioFiles {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("jvmnio");
        Path f = dir.resolve("a.txt");
        Files.writeString(f, "héllo\nworld\nthird line\n");
        System.out.println(Files.exists(f) + " " + Files.size(f) + " " + Files.readAllLines(f) + " " + Files.isDirectory(dir) + " " + Files.isRegularFile(f) + " " + f.getFileName() + " " + f.getParent().equals(dir));
        try (Stream<String> lines = Files.lines(f)) { System.out.println(lines.filter(l -> l.contains("o")).count()); }
        Files.write(dir.resolve("b.bin"), new byte[]{1, 2, 3, 4, 5}, StandardOpenOption.CREATE);
        Files.write(f, List.of("appended"), StandardOpenOption.APPEND);
        Files.copy(f, dir.resolve("c.txt"));
        Files.createDirectories(dir.resolve("sub/deep"));
        Files.move(dir.resolve("c.txt"), dir.resolve("sub/deep/d.txt"));
        try (Stream<Path> w = Files.walk(dir)) { System.out.println(w.map(p -> dir.relativize(p).toString()).sorted().collect(Collectors.toList())); }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.{txt,bin}")) { List<String> n = new ArrayList<>(); for (Path p : ds) n.add(p.getFileName().toString()); Collections.sort(n); System.out.println(n); }
        try (FileChannel ch = FileChannel.open(dir.resolve("b.bin"), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer bb = ByteBuffer.allocate(8);
            int n = ch.read(bb);
            bb.flip();
            ch.position(ch.size());
            ch.write(ByteBuffer.wrap(new byte[]{9, 9}));
            ByteBuffer d = ByteBuffer.allocateDirect(4);
            ch.read(d, 3);
            d.flip();
            System.out.println(n + " " + bb.get(2) + " " + ch.size() + " " + d.get() + d.get() + d.get() + " " + ch.position());
        }
        try (var w = Files.newBufferedWriter(dir.resolve("e.txt"), StandardCharsets.UTF_16)) { w.write("wide ✓"); }
        System.out.println(Files.readString(dir.resolve("e.txt"), StandardCharsets.UTF_16) + " " + Files.readAllBytes(dir.resolve("e.txt")).length + " " + (Files.getLastModifiedTime(f).toMillis() > 0) + " " + Files.isReadable(f) + " " + Files.notExists(dir.resolve("zzz")));
        try { Files.readString(dir.resolve("zzz")); } catch (NoSuchFileException e) { System.out.println("nsf " + dir.relativize(Path.of(e.getFile()))); }
        try { Files.createDirectory(dir.resolve("sub")); } catch (FileAlreadyExistsException e) { System.out.println("exists"); }
        try { Files.delete(dir.resolve("sub")); } catch (DirectoryNotEmptyException e) { System.out.println("not empty"); }
        Path p = Path.of("/a/b/../c/./d.txt");
        System.out.println(p.normalize() + " " + p.getNameCount() + " " + p.isAbsolute() + " " + Path.of("x/y").resolveSibling("z") + " " + p.getFileName().toString().endsWith(".txt") + " " + Paths.get("a", "b", "c").startsWith("a/b") + " " + Path.of("").toAbsolutePath().equals(Path.of(System.getProperty("user.dir"))));
        try (Stream<Path> w = Files.walk(dir)) { for (Path x : w.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(x); }
        System.out.println(Files.exists(dir));
    }
}
