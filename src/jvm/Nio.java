package jvm;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

final class Nio {
    interface Action {
        void run() throws IOException;
    }

    static final sun.misc.Unsafe U = Unsafe.U;
    static final long BYTES = sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;
    static final ConcurrentHashMap<Integer, String> paths = new ConcurrentHashMap<>();
    static final Set<Integer> appends = ConcurrentHashMap.newKeySet();
    static final ConcurrentHashMap<Long, Object> handles = new ConcurrentHashMap<>();
    static final AtomicLong nextHandle = new AtomicLong(1);

    static String cstr(long a) {
        int n = 0;
        while (U.getByte(a + n) != 0) n++;
        byte[] b = new byte[n];
        U.copyMemory(null, a, b, BYTES, n);
        return new String(b, StandardCharsets.UTF_8);
    }

    static Throw unix(T t, int errno) {
        Klass k = VM.load(t, "sun/nio/fs/UnixException", null);
        Obj e = k.alloc();
        VM.call(t, k.mmap.get("<init>(I)V"), e, errno);
        return new Throw(e);
    }

    static int errno(IOException e) {
        if (e instanceof NoSuchFileException) return 2;
        if (e instanceof FileAlreadyExistsException) return 17;
        if (e instanceof AccessDeniedException) return 13;
        if (e instanceof DirectoryNotEmptyException) return 39;
        if (e instanceof NotDirectoryException) return 20;
        return 5;
    }

    static void fs(T t, Action a) {
        try {
            a.run();
        } catch (IOException e) {
            throw unix(t, errno(e));
        }
    }

    static void io(T t, Action a) {
        try {
            a.run();
        } catch (IOException e) {
            throw IO.io(t, e);
        }
    }

    static int fd(Obj d) { return (int) d.p[d.k.field("fd").slot]; }

    static RandomAccessFile raf(T t, int fd) {
        RandomAccessFile f = IO.files.get(fd);
        if (f == null) throw VM.thrown(t, "java/io/IOException", "Bad file descriptor");
        return f;
    }

    static void stat(T t, String path, Obj a, boolean follow) {
        fs(t, () -> fill(a, Files.readAttributes(Path.of(path), "unix:*", follow ? new LinkOption[0] : new LinkOption[]{LinkOption.NOFOLLOW_LINKS})));
    }

    static void fill(Obj a, Map<String, Object> m) {
        Klass k = a.k;
        a.p[k.field("st_mode").slot] = (Integer) m.get("mode");
        a.p[k.field("st_ino").slot] = (Long) m.get("ino");
        a.p[k.field("st_dev").slot] = (Long) m.get("dev");
        a.p[k.field("st_rdev").slot] = (Long) m.get("rdev");
        a.p[k.field("st_nlink").slot] = (Integer) m.get("nlink");
        a.p[k.field("st_uid").slot] = (Integer) m.get("uid");
        a.p[k.field("st_gid").slot] = (Integer) m.get("gid");
        a.p[k.field("st_size").slot] = (Long) m.get("size");
        time(a, "st_atime", (FileTime) m.get("lastAccessTime"));
        time(a, "st_mtime", (FileTime) m.get("lastModifiedTime"));
        time(a, "st_ctime", (FileTime) m.get("ctime"));
    }

    static void time(Obj a, String f, FileTime ft) {
        a.p[a.k.field(f + "_sec").slot] = ft.toInstant().getEpochSecond();
        a.p[a.k.field(f + "_nsec").slot] = ft.toInstant().getNano();
    }

    static int read(T t, int fd, long addr, int len, long pos) throws IOException {
        byte[] buf = new byte[len];
        int n;
        if (fd == 0) n = IO.IN.read(buf, 0, len);
        else if (pos < 0) n = raf(t, fd).read(buf, 0, len);
        else n = raf(t, fd).getChannel().read(ByteBuffer.wrap(buf), pos);
        if (n > 0) U.copyMemory(buf, BYTES, null, addr, n);
        return n;
    }

    static int write(T t, int fd, long addr, int len, long pos) throws IOException {
        byte[] buf = new byte[len];
        U.copyMemory(null, addr, buf, BYTES, len);
        if (fd == 1) IO.OUT.write(buf);
        else if (fd == 2) IO.ERR.write(buf);
        else if (pos >= 0) raf(t, fd).getChannel().write(ByteBuffer.wrap(buf), pos);
        else {
            RandomAccessFile f = raf(t, fd);
            if (appends.contains(fd)) f.seek(f.length());
            f.write(buf);
        }
        return len;
    }

    static long inflate(T t, long h, byte[] in, int inOff, int inLen, byte[] out, int outOff, int outLen) {
        Inflater inf = (Inflater) handles.get(h);
        inf.setInput(in, inOff, inLen);
        int n;
        try {
            n = inf.inflate(out, outOff, outLen);
        } catch (DataFormatException e) {
            throw VM.thrown(t, "java/util/zip/DataFormatException", e.getMessage());
        }
        return (long) (inLen - inf.getRemaining()) | (long) n << 31 | (inf.finished() ? 1L << 62 : 0) | (inf.needsDictionary() ? 1L << 63 : 0);
    }

    static byte[] copyIn(long addr, int len) {
        byte[] b = new byte[len];
        U.copyMemory(null, addr, b, BYTES, len);
        return b;
    }

    @SuppressWarnings("unchecked")
    static void init() {
        String d = "sun/nio/fs/UnixNativeDispatcher.";
        Natives.reg(d + "init", (t, b) -> t.P[b] = 0);
        Natives.reg("sun/nio/fs/LinuxNativeDispatcher.init", Natives.NOP);
        Natives.reg(d + "getcwd", (t, b) -> t.R[b] = VM.bytes(System.getProperty("user.dir").getBytes()));
        Natives.reg(d + "strerror", (t, b) -> {
            String m = switch ((int) t.P[b]) {
                case 2 -> "No such file or directory"; case 13 -> "Permission denied"; case 17 -> "File exists";
                case 20 -> "Not a directory"; case 21 -> "Is a directory"; case 39 -> "Directory not empty";
                default -> "Input/output error";
            };
            t.R[b] = VM.bytes(m.getBytes());
        });
        Natives.reg(d + "open0", (t, b) -> {
            String p = cstr(t.P[b]);
            int flags = (int) t.P[b + 2];
            File f = new File(p);
            boolean write = (flags & 3) != 0;
            if (f.exists() && (flags & 0xc0) == 0xc0) throw unix(t, 17);
            if (!f.exists() && (flags & 0x40) == 0) throw unix(t, 2);
            if (f.isDirectory()) throw unix(t, 21);
            try {
                RandomAccessFile r = new RandomAccessFile(f, write ? "rw" : "r");
                if ((flags & 0x200) != 0) r.setLength(0);
                int n = IO.next.getAndIncrement();
                IO.files.put(n, r);
                paths.put(n, p);
                if ((flags & 0x400) != 0) appends.add(n);
                t.P[b] = n;
            } catch (IOException e) {
                throw unix(t, f.exists() ? 13 : 2);
            }
        });
        Natives.reg(d + "close0", (t, b) -> {
            RandomAccessFile f = IO.files.remove((int) t.P[b]);
            paths.remove((int) t.P[b]);
            if (f != null) io(t, f::close);
        });
        Natives.reg(d + "dup", (t, b) -> {
            int n = IO.next.getAndIncrement();
            IO.files.put(n, raf(t, (int) t.P[b]));
            t.P[b] = n;
        });
        Natives.reg(d + "stat0", (t, b) -> stat(t, cstr(t.P[b]), t.R[b + 2], true));
        Natives.reg(d + "lstat0", (t, b) -> stat(t, cstr(t.P[b]), t.R[b + 2], false));
        Natives.reg(d + "fstat", (t, b) -> stat(t, paths.getOrDefault((int) t.P[b], "/dev/null"), t.R[b + 1], true));
        Natives.reg(d + "stat1", (t, b) -> {
            try {
                t.P[b] = (Integer) Files.getAttribute(Path.of(cstr(t.P[b])), "unix:mode");
            } catch (IOException e) {
                t.P[b] = 0;
            }
        });
        Natives.reg(d + "exists0", (t, b) -> t.P[b] = new File(cstr(t.P[b])).exists() ? 1 : 0);
        Natives.reg(d + "access0", (t, b) -> {
            File f = new File(cstr(t.P[b]));
            int m = (int) t.P[b + 2];
            if (!f.exists()) throw unix(t, 2);
            if ((m & 4) != 0 && !f.canRead() || (m & 2) != 0 && !f.canWrite() || (m & 1) != 0 && !f.canExecute()) throw unix(t, 13);
        });
        Natives.reg(d + "opendir0", (t, b) -> {
            File f = new File(cstr(t.P[b]));
            String[] l = f.list();
            if (l == null) throw unix(t, f.exists() ? 20 : 2);
            long h = nextHandle.getAndIncrement();
            handles.put(h, List.of(l).iterator());
            t.P[b] = h;
        });
        Natives.reg(d + "readdir", (t, b) -> {
            Iterator<String> it = (Iterator<String>) handles.get(t.P[b]);
            t.R[b] = it.hasNext() ? VM.bytes(it.next().getBytes(StandardCharsets.UTF_8)) : null;
        });
        Natives.reg(d + "closedir", (t, b) -> handles.remove(t.P[b]));
        Natives.reg(d + "mkdir0", (t, b) -> { String p = cstr(t.P[b]); fs(t, () -> Files.createDirectory(Path.of(p))); });
        Natives.reg(d + "rmdir0", (t, b) -> { String p = cstr(t.P[b]); fs(t, () -> Files.delete(Path.of(p))); });
        Natives.reg(d + "unlink0", (t, b) -> { String p = cstr(t.P[b]); fs(t, () -> Files.delete(Path.of(p))); });
        Natives.reg(d + "rename0", (t, b) -> { String p = cstr(t.P[b]), q = cstr(t.P[b + 2]); fs(t, () -> Files.move(Path.of(p), Path.of(q), StandardCopyOption.REPLACE_EXISTING)); });
        Natives.reg(d + "link0", (t, b) -> { String p = cstr(t.P[b]), q = cstr(t.P[b + 2]); fs(t, () -> Files.createLink(Path.of(q), Path.of(p))); });
        Natives.reg(d + "symlink0", (t, b) -> { String p = cstr(t.P[b]), q = cstr(t.P[b + 2]); fs(t, () -> Files.createSymbolicLink(Path.of(q), Path.of(p))); });
        Natives.reg(d + "readlink0", (t, b) -> { String p = cstr(t.P[b]); fs(t, () -> t.R[b] = VM.bytes(Files.readSymbolicLink(Path.of(p)).toString().getBytes())); });
        Natives.reg(d + "realpath0", (t, b) -> { String p = cstr(t.P[b]); fs(t, () -> t.R[b] = VM.bytes(Path.of(p).toRealPath().toString().getBytes())); });
        Natives.reg(d + "chmod0", (t, b) -> {
            String p = cstr(t.P[b]);
            int m = (int) t.P[b + 2];
            Set<PosixFilePermission> s = new HashSet<>();
            PosixFilePermission[] all = PosixFilePermission.values();
            for (int i = 0; i < 9; i++) if ((m & 1 << 8 - i) != 0) s.add(all[i]);
            fs(t, () -> Files.setPosixFilePermissions(Path.of(p), s));
        });
        Natives.reg(d + "chown0", Natives.NOP);
        Natives.reg(d + "lchown0", Natives.NOP);
        Natives.reg(d + "utimes0", (t, b) -> { String p = cstr(t.P[b]); long m = t.P[b + 4]; fs(t, () -> Files.setLastModifiedTime(Path.of(p), FileTime.from(m, java.util.concurrent.TimeUnit.MICROSECONDS))); });
        Natives.reg(d + "futimes", Natives.NOP);
        Natives.reg(d + "futimens", Natives.NOP);
        Natives.reg(d + "getpwuid", (t, b) -> t.R[b] = VM.bytes(System.getProperty("user.name").getBytes()));
        Natives.reg(d + "getgrgid", (t, b) -> t.R[b] = VM.bytes(System.getProperty("user.name").getBytes()));
        Natives.reg(d + "read", (t, b) -> { int fd = (int) t.P[b]; long a = t.P[b + 1]; int n = (int) t.P[b + 3]; io(t, () -> t.P[b] = read(t, fd, a, n, -1)); });
        Natives.reg(d + "write", (t, b) -> { int fd = (int) t.P[b]; long a = t.P[b + 1]; int n = (int) t.P[b + 3]; io(t, () -> t.P[b] = write(t, fd, a, n, -1)); });
        Natives.reg("sun/nio/fs/UnixCopyFile.transfer", (t, b) -> {
            RandomAccessFile dst = raf(t, (int) t.P[b]), src = raf(t, (int) t.P[b + 1]);
            io(t, () -> { byte[] buf = new byte[8192]; for (int n; (n = src.read(buf)) > 0; ) dst.write(buf, 0, n); });
        });

        String f = "sun/nio/ch/FileDispatcherImpl.";
        Natives.reg(f + "init", Natives.NOP);
        Natives.reg(f + "read0", (t, b) -> { int fd = fd(t.R[b]); long a = t.P[b + 1]; int n = (int) t.P[b + 3]; io(t, () -> t.P[b] = read(t, fd, a, n, -1)); });
        Natives.reg(f + "pread0", (t, b) -> { int fd = fd(t.R[b]); long a = t.P[b + 1], pos = t.P[b + 4]; int n = (int) t.P[b + 3]; io(t, () -> t.P[b] = read(t, fd, a, n, pos)); });
        Natives.reg(f + "write0", (t, b) -> { int fd = fd(t.R[b]); long a = t.P[b + 1]; int n = (int) t.P[b + 3]; io(t, () -> t.P[b] = write(t, fd, a, n, -1)); });
        Natives.reg(f + "pwrite0", (t, b) -> { int fd = fd(t.R[b]); long a = t.P[b + 1], pos = t.P[b + 4]; int n = (int) t.P[b + 3]; io(t, () -> t.P[b] = write(t, fd, a, n, pos)); });
        Natives.reg(f + "readv0", (t, b) -> {
            int fd = fd(t.R[b]);
            long a = t.P[b + 1];
            int cnt = (int) t.P[b + 3];
            io(t, () -> {
                long total = 0;
                for (int i = 0; i < cnt; i++) {
                    int len = (int) U.getLong(a + i * 16L + 8), n = len == 0 ? 0 : read(t, fd, U.getLong(a + i * 16L), len, -1);
                    if (n < 0) { if (total == 0) total = -1; break; }
                    total += n;
                    if (n < len) break;
                }
                t.P[b] = total;
            });
        });
        Natives.reg(f + "writev0", (t, b) -> {
            int fd = fd(t.R[b]);
            long a = t.P[b + 1];
            int cnt = (int) t.P[b + 3];
            io(t, () -> {
                long total = 0;
                for (int i = 0; i < cnt; i++) total += write(t, fd, U.getLong(a + i * 16L), (int) U.getLong(a + i * 16L + 8), -1);
                t.P[b] = total;
            });
        });
        Natives.reg(f + "force0", (t, b) -> { int fd = fd(t.R[b]); io(t, () -> { if (fd > 2) raf(t, fd).getChannel().force(true); t.P[b] = 0; }); });
        Natives.reg(f + "seek0", (t, b) -> {
            RandomAccessFile r = raf(t, fd(t.R[b]));
            long off = t.P[b + 1];
            io(t, () -> { if (off >= 0) r.seek(off); t.P[b] = r.getFilePointer(); });
        });
        Natives.reg(f + "truncate0", (t, b) -> { RandomAccessFile r = raf(t, fd(t.R[b])); long n = t.P[b + 1]; io(t, () -> { r.getChannel().truncate(n); t.P[b] = 0; }); });
        Natives.reg(f + "size0", (t, b) -> { RandomAccessFile r = raf(t, fd(t.R[b])); io(t, () -> t.P[b] = r.length()); });
        Natives.reg(f + "lock0", (t, b) -> t.P[b] = 0);
        Natives.reg(f + "release0", Natives.NOP);
        Natives.reg(f + "preClose0", Natives.NOP);
        Natives.reg(f + "close0", (t, b) -> {
            int fd = fd(t.R[b]);
            RandomAccessFile r = IO.files.remove(fd);
            paths.remove(fd);
            if (r != null) io(t, r::close);
        });
        Natives.reg(f + "closeIntFD", (t, b) -> {
            RandomAccessFile r = IO.files.remove((int) t.P[b]);
            if (r != null) io(t, r::close);
        });
        Natives.reg(f + "canTransferToFromOverlappedMap0", (t, b) -> t.P[b] = 1);
        Natives.reg(f + "setDirect0", (t, b) -> t.P[b] = -1);

        String c = "sun/nio/ch/FileChannelImpl.";
        Natives.reg(c + "initIDs", (t, b) -> t.P[b] = 4096);
        Natives.reg(c + "maxDirectTransferSize0", (t, b) -> t.P[b] = Integer.MAX_VALUE);
        Natives.reg(c + "transferTo0", (t, b) -> t.P[b] = -4);
        Natives.reg(c + "map0", (t, b) -> {
            Obj ch = t.R[b];
            RandomAccessFile r = raf(t, fd(ch.r[ch.k.field("fd").slot]));
            int prot = (int) t.P[b + 1];
            long pos = t.P[b + 2], len = t.P[b + 4];
            io(t, () -> {
                ByteBuffer m = r.getChannel().map(prot == 0 ? FileChannel.MapMode.READ_ONLY : prot == 1 ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.PRIVATE, pos, len);
                long a;
                try {
                    a = U.getLong(m, U.objectFieldOffset(java.nio.Buffer.class.getDeclaredField("address")));
                } catch (NoSuchFieldException e) {
                    throw new InternalError(e);
                }
                handles.put(-a, m);
                t.P[b] = a;
            });
        });
        Natives.reg(c + "unmap0", (t, b) -> { handles.remove(-t.P[b]); t.P[b] = 0; });

        String u = "sun/nio/ch/IOUtil.";
        Natives.reg(u + "iovMax", (t, b) -> t.P[b] = 16);
        Natives.reg(u + "fdLimit", (t, b) -> t.P[b] = 1024);
        Natives.reg(u + "fdVal", (t, b) -> t.P[b] = fd(t.R[b]));
        Natives.reg(u + "setfdVal", (t, b) -> t.R[b].p[t.R[b].k.field("fd").slot] = (int) t.P[b + 1]);
        Natives.reg(u + "configureBlocking", Natives.NOP);
        Natives.reg(u + "randomBytes", (t, b) -> { new java.security.SecureRandom().nextBytes((byte[]) t.R[b].x); t.P[b] = 1; });
        Natives.reg("sun/nio/ch/NativeThread.init", Natives.NOP);
        Natives.reg("sun/nio/ch/NativeThread.current", (t, b) -> t.P[b] = 0);
        Natives.reg("sun/nio/ch/NativeThread.signal", Natives.NOP);
        Natives.reg("sun/nio/ch/NativeThread.supportPendingSignals0", (t, b) -> t.P[b] = 0);
        Natives.reg("sun/nio/ch/FileKey.init", Natives.NOP);

        String z = "java/util/zip/Inflater.";
        Natives.reg(z + "init", (t, b) -> {
            long h = nextHandle.getAndIncrement();
            handles.put(h, new Inflater(t.P[b] != 0));
            t.P[b] = h;
        });
        Natives.reg(z + "inflateBytesBytes", (t, b) -> t.P[b] = inflate(t, t.P[b + 1], (byte[]) t.R[b + 3].x, (int) t.P[b + 4], (int) t.P[b + 5], (byte[]) t.R[b + 6].x, (int) t.P[b + 7], (int) t.P[b + 8]));
        Natives.reg(z + "inflateBytesBuffer", (t, b) -> {
            int n = (int) t.P[b + 8];
            byte[] out = new byte[n];
            long r = inflate(t, t.P[b + 1], (byte[]) t.R[b + 3].x, (int) t.P[b + 4], (int) t.P[b + 5], out, 0, n);
            U.copyMemory(out, BYTES, null, t.P[b + 6], (int) (r >>> 31 & 0x7fffffff));
            t.P[b] = r;
        });
        Natives.reg(z + "inflateBufferBytes", (t, b) -> {
            int n = (int) t.P[b + 5];
            t.P[b] = inflate(t, t.P[b + 1], copyIn(t.P[b + 3], n), 0, n, (byte[]) t.R[b + 6].x, (int) t.P[b + 7], (int) t.P[b + 8]);
        });
        Natives.reg(z + "inflateBufferBuffer", (t, b) -> {
            int n = (int) t.P[b + 5], on = (int) t.P[b + 8];
            byte[] out = new byte[on];
            long r = inflate(t, t.P[b + 1], copyIn(t.P[b + 3], n), 0, n, out, 0, on);
            U.copyMemory(out, BYTES, null, t.P[b + 6], (int) (r >>> 31 & 0x7fffffff));
            t.P[b] = r;
        });
        Natives.reg(z + "setDictionary", (t, b) -> ((Inflater) handles.get(t.P[b])).setDictionary((byte[]) t.R[b + 2].x, (int) t.P[b + 3], (int) t.P[b + 4]));
        Natives.reg(z + "getAdler", (t, b) -> t.P[b] = ((Inflater) handles.get(t.P[b])).getAdler());
        Natives.reg(z + "reset", (t, b) -> ((Inflater) handles.get(t.P[b])).reset());
        Natives.reg(z + "end", (t, b) -> { Inflater i = (Inflater) handles.remove(t.P[b]); if (i != null) i.end(); });
        Natives.reg("java/util/zip/Adler32.update", (t, b) -> t.P[b] = adler((int) t.P[b], new byte[]{(byte) t.P[b + 1]}, 0, 1));
        Natives.reg("java/util/zip/Adler32.updateBytes", (t, b) -> t.P[b] = adler((int) t.P[b], (byte[]) t.R[b + 1].x, (int) t.P[b + 2], (int) t.P[b + 3]));
    }

    static int adler(int adler, byte[] d, int off, int len) {
        int a = adler & 0xffff, s = adler >>> 16;
        for (int i = off; i < off + len; i++) {
            a = (a + (d[i] & 0xff)) % 65521;
            s = (s + a) % 65521;
        }
        return s << 16 | a;
    }
}
