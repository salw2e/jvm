package jvm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class IO {
    static final OutputStream OUT = new FileOutputStream(java.io.FileDescriptor.out), ERR = new FileOutputStream(java.io.FileDescriptor.err);
    static final InputStream IN = new java.io.FileInputStream(java.io.FileDescriptor.in);
    static final ConcurrentHashMap<Integer, RandomAccessFile> files = new ConcurrentHashMap<>();
    static final AtomicInteger next = new AtomicInteger(3);

    static int fd(Obj stream) {
        Obj d = stream.r[stream.k.field("fd").slot];
        return (int) d.p[d.k.field("fd").slot];
    }

    static RandomAccessFile file(T t, Obj stream) {
        RandomAccessFile f = files.get(fd(stream));
        if (f == null) throw VM.thrown(t, "java/io/IOException", "Stream Closed");
        return f;
    }

    static void open(T t, Obj stream, String path, String mode, boolean append) {
        try {
            if (mode.equals("r") && new File(path).isDirectory()) throw new IOException(path + " (Is a directory)");
            RandomAccessFile f = new RandomAccessFile(path, mode.equals("r") ? "r" : "rw");
            if (append) f.seek(f.length());
            else if (mode.equals("rwt")) f.setLength(0);
            int n = next.getAndIncrement();
            files.put(n, f);
            Obj d = stream.r[stream.k.field("fd").slot];
            d.p[d.k.field("fd").slot] = n;
        } catch (IOException e) {
            throw VM.thrown(t, "java/io/FileNotFoundException", e.getMessage());
        }
    }

    static Throw io(T t, IOException e) { return VM.thrown(t, "java/io/IOException", e.getMessage()); }

    static String path(Obj file) { return VM.str(file.r[file.k.field("path").slot]); }

    static void init() {
        Natives.reg("java/io/FileDescriptor.getHandle", (t, b) -> t.P[b] = -1);
        Natives.reg("java/io/FileDescriptor.getAppend", (t, b) -> t.P[b] = 0);
        Natives.reg("java/io/FileDescriptor.close0", (t, b) -> {
            Obj d = t.R[b];
            int n = (int) d.p[d.k.field("fd").slot];
            RandomAccessFile f = files.remove(n);
            d.p[d.k.field("fd").slot] = -1;
            try { if (f != null) f.close(); } catch (IOException e) { throw io(t, e); }
        });

        Natives.reg("java/io/FileOutputStream.writeBytes", (t, b) -> {
            byte[] d = (byte[]) t.R[b + 1].x;
            int off = (int) t.P[b + 2], len = (int) t.P[b + 3], fd = fd(t.R[b]);
            try {
                if (fd == 1) OUT.write(d, off, len);
                else if (fd == 2) ERR.write(d, off, len);
                else file(t, t.R[b]).write(d, off, len);
            } catch (IOException e) { throw io(t, e); }
        });
        Natives.reg("java/io/FileOutputStream.write(IZ)V", (t, b) -> {
            int fd = fd(t.R[b]);
            try {
                if (fd == 1) OUT.write((int) t.P[b + 1]);
                else if (fd == 2) ERR.write((int) t.P[b + 1]);
                else file(t, t.R[b]).write((int) t.P[b + 1]);
            } catch (IOException e) { throw io(t, e); }
        });
        Natives.reg("java/io/FileOutputStream.open0", (t, b) -> open(t, t.R[b], VM.str(t.R[b + 1]), t.P[b + 2] != 0 ? "rw" : "rwt", t.P[b + 2] != 0));

        Natives.reg("java/io/FileInputStream.open0", (t, b) -> open(t, t.R[b], VM.str(t.R[b + 1]), "r", false));
        Natives.reg("java/io/FileInputStream.readBytes", (t, b) -> {
            byte[] d = (byte[]) t.R[b + 1].x;
            int off = (int) t.P[b + 2], len = (int) t.P[b + 3], fd = fd(t.R[b]);
            try {
                t.P[b] = fd == 0 ? IN.read(d, off, len) : file(t, t.R[b]).read(d, off, len);
            } catch (IOException e) { throw io(t, e); }
        });
        Natives.reg("java/io/FileInputStream.read0", (t, b) -> {
            try {
                t.P[b] = fd(t.R[b]) == 0 ? IN.read() : file(t, t.R[b]).read();
            } catch (IOException e) { throw io(t, e); }
        });
        Natives.reg("java/io/FileInputStream.available0", (t, b) -> {
            try {
                if (fd(t.R[b]) == 0) { t.P[b] = IN.available(); return; }
                RandomAccessFile f = file(t, t.R[b]);
                t.P[b] = (int) Math.min(Integer.MAX_VALUE, f.length() - f.getFilePointer());
            } catch (IOException e) { throw io(t, e); }
        });
        Natives.reg("java/io/FileInputStream.skip0", (t, b) -> {
            try {
                RandomAccessFile f = file(t, t.R[b]);
                f.seek(f.getFilePointer() + t.P[b + 1]);
                t.P[b] = t.P[b + 1];
            } catch (IOException e) { throw io(t, e); }
        });
        Natives.reg("java/io/FileInputStream.length0", (t, b) -> { try { t.P[b] = file(t, t.R[b]).length(); } catch (IOException e) { throw io(t, e); } });
        Natives.reg("java/io/FileInputStream.position0", (t, b) -> { try { t.P[b] = file(t, t.R[b]).getFilePointer(); } catch (IOException e) { throw io(t, e); } });

        String r = "java/io/RandomAccessFile.";
        Natives.reg(r + "open0", (t, b) -> open(t, t.R[b], VM.str(t.R[b + 1]), (t.P[b + 2] & 2) != 0 ? "rw" : "r", false));
        Natives.reg(r + "read0", (t, b) -> { try { t.P[b] = file(t, t.R[b]).read(); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "readBytes0", (t, b) -> { try { t.P[b] = file(t, t.R[b]).read((byte[]) t.R[b + 1].x, (int) t.P[b + 2], (int) t.P[b + 3]); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "readBytes", (t, b) -> { try { t.P[b] = file(t, t.R[b]).read((byte[]) t.R[b + 1].x, (int) t.P[b + 2], (int) t.P[b + 3]); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "write0", (t, b) -> { try { file(t, t.R[b]).write((int) t.P[b + 1]); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "writeBytes0", (t, b) -> { try { file(t, t.R[b]).write((byte[]) t.R[b + 1].x, (int) t.P[b + 2], (int) t.P[b + 3]); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "writeBytes", (t, b) -> { try { file(t, t.R[b]).write((byte[]) t.R[b + 1].x, (int) t.P[b + 2], (int) t.P[b + 3]); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "seek0", (t, b) -> { try { file(t, t.R[b]).seek(t.P[b + 1]); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "getFilePointer", (t, b) -> { try { t.P[b] = file(t, t.R[b]).getFilePointer(); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "length", (t, b) -> { try { t.P[b] = file(t, t.R[b]).length(); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "length0", (t, b) -> { try { t.P[b] = file(t, t.R[b]).length(); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "setLength0", (t, b) -> { try { file(t, t.R[b]).setLength(t.P[b + 1]); } catch (IOException e) { throw io(t, e); } });
        Natives.reg(r + "setLength", (t, b) -> { try { file(t, t.R[b]).setLength(t.P[b + 1]); } catch (IOException e) { throw io(t, e); } });

        String u = "java/io/UnixFileSystem.";
        Natives.reg(u + "getBooleanAttributes0", (t, b) -> {
            File f = new File(path(t.R[b + 1]));
            t.P[b] = f.exists() ? 1 | (f.isFile() ? 2 : 0) | (f.isDirectory() ? 4 : 0) : 0;
        });
        Natives.reg(u + "canonicalize0", (t, b) -> {
            try { t.R[b] = VM.str(new File(VM.str(t.R[b + 1])).getCanonicalPath()); } catch (IOException e) { throw io(t, e); }
        });
        Natives.reg(u + "getLength", (t, b) -> t.P[b] = new File(path(t.R[b + 1])).length());
        Natives.reg(u + "getLastModifiedTime", (t, b) -> t.P[b] = new File(path(t.R[b + 1])).lastModified());
        Natives.reg(u + "checkAccess", (t, b) -> {
            File f = new File(path(t.R[b + 1]));
            int a = (int) t.P[b + 2];
            t.P[b] = f.exists() && ((a & 4) == 0 || f.canRead()) && ((a & 2) == 0 || f.canWrite()) && ((a & 1) == 0 || f.canExecute()) ? 1 : 0;
        });
        Natives.reg(u + "list", (t, b) -> {
            String[] l = new File(path(t.R[b + 1])).list();
            t.R[b] = l == null ? null : VM.strings(t, l);
        });
        Natives.reg(u + "createDirectory", (t, b) -> t.P[b] = new File(path(t.R[b + 1])).mkdir() ? 1 : 0);
        Natives.reg(u + "delete0", (t, b) -> t.P[b] = new File(path(t.R[b + 1])).delete() ? 1 : 0);
        Natives.reg(u + "rename0", (t, b) -> t.P[b] = new File(path(t.R[b + 1])).renameTo(new File(path(t.R[b + 2]))) ? 1 : 0);
        Natives.reg(u + "createFileExclusively", (t, b) -> {
            try { t.P[b] = new File(VM.str(t.R[b + 1])).createNewFile() ? 1 : 0; } catch (IOException e) { throw io(t, e); }
        });
        Natives.reg(u + "setLastModifiedTime", (t, b) -> t.P[b] = new File(path(t.R[b + 1])).setLastModified(t.P[b + 2]) ? 1 : 0);
        Natives.reg(u + "getNameMax0", (t, b) -> t.P[b] = 255);
    }
}
