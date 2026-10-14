package jvm;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NetworkChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ConcurrentHashMap;

final class Net {
    static final class Sock {
        ServerSocketChannel server;
        SocketChannel client;
        Pipe.SourceChannel source;
        Pipe.SinkChannel sink;
        volatile boolean blocking = true;
        Selector wait;
        final ConcurrentHashMap<Integer, Integer> opts = new ConcurrentHashMap<>();

        SelectableChannel channel() { return server != null ? server : client != null ? client : source != null ? source : sink; }
    }

    static final ConcurrentHashMap<Integer, Sock> socks = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Integer, Selector> polls = new ConcurrentHashMap<>();
    static final sun.misc.Unsafe U = Unsafe.U;

    static int add(Sock s) {
        int n = IO.next.getAndIncrement();
        socks.put(n, s);
        return n;
    }

    static Sock sock(T t, Obj fd) {
        Sock s = socks.get(Nio.fd(fd));
        if (s == null) throw VM.thrown(t, "java/net/SocketException", "Socket closed");
        return s;
    }

    static Throw net(T t, IOException e) {
        String c = e instanceof java.net.ConnectException ? "java/net/ConnectException"
                : e instanceof java.net.BindException ? "java/net/BindException"
                : e instanceof java.nio.channels.ClosedChannelException ? "java/net/SocketException" : "java/io/IOException";
        String m = e instanceof java.nio.channels.ClosedChannelException ? "Socket closed" : e.getMessage();
        return VM.thrown(t, c, m);
    }

    interface IOCall {
        long run() throws IOException;
    }

    static long io(T t, IOCall c) {
        try {
            return c.run();
        } catch (IOException e) {
            throw net(t, e);
        }
    }

    static void await(Sock s, int op, long ms) throws IOException {
        Selector sel;
        synchronized (s) {
            if (s.wait == null) s.wait = Selector.open();
            sel = s.wait;
        }
        synchronized (sel) {
            SelectionKey k = s.channel().register(sel, op);
            if (ms < 0) sel.select(); else sel.select(Math.max(ms, 1));
            k.cancel();
            sel.selectNow();
        }
    }

    static InetAddress host(Obj a) {
        Obj h = a.r[a.k.field("holder").slot];
        int v = (int) h.p[h.k.field("address").slot];
        try {
            return InetAddress.getByAddress(new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v});
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    static Obj guest(T t, InetAddress a) {
        if (a == null) return null;
        byte[] b = a.getAddress();
        if (b.length != 4) b = new byte[]{127, 0, 0, 1};
        return VM.callR(t, VM.method(t, "java/net/InetAddress", "getByAddress", "([B)Ljava/net/InetAddress;"), VM.bytes(b));
    }

    static InetSocketAddress local(Sock s) throws IOException {
        NetworkChannel c = s.server != null ? s.server : s.client;
        return c == null ? null : (InetSocketAddress) c.getLocalAddress();
    }

    static int read(T t, Sock s, long addr, int len) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(len);
        ReadableByteChannel ch = s.client != null ? s.client : s.source;
        for (;;) {
            int n = ch.read(bb);
            if (n > 0) {
                U.copyMemory(bb.array(), Nio.BYTES, null, addr, n);
                return n;
            }
            if (n < 0) return -1;
            if (!s.blocking) return -2;
            await(s, SelectionKey.OP_READ, -1);
        }
    }

    static int write(T t, Sock s, long addr, int len) throws IOException {
        byte[] b = new byte[len];
        U.copyMemory(null, addr, b, Nio.BYTES, len);
        ByteBuffer bb = ByteBuffer.wrap(b);
        WritableByteChannel ch = s.client != null ? s.client : s.sink;
        int total = 0;
        for (;;) {
            total += ch.write(bb);
            if (!bb.hasRemaining() || !s.blocking) return total == 0 ? -2 : total;
            await(s, SelectionKey.OP_WRITE, -1);
        }
    }

    static boolean close(int fd) {
        Sock s = socks.remove(fd);
        if (s == null) return false;
        try {
            s.channel().close();
            if (s.sink != null) s.sink.close();
            if (s.wait != null) s.wait.close();
        } catch (IOException e) { }
        return true;
    }

    static int ops(Sock s, int events) {
        int o = 0;
        if ((events & 1) != 0) o |= s.server != null ? SelectionKey.OP_ACCEPT : SelectionKey.OP_READ;
        if ((events & 4) != 0) o |= s.client != null && s.client.isConnectionPending() ? SelectionKey.OP_CONNECT : s.sink != null || s.client != null ? SelectionKey.OP_WRITE : 0;
        return o & s.channel().validOps();
    }

    static int events(int ready) {
        int e = 0;
        if ((ready & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0) e |= 1;
        if ((ready & (SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT)) != 0) e |= 4;
        return e;
    }

    static void init() {
        String n = "sun/nio/ch/Net.";
        for (String f : new String[]{"isIPv6Available0", "isReusePortAvailable0", "shouldShutdownWriteBeforeClose0", "shouldSetBothIPv4AndIPv6Options0", "canIPv6SocketJoinIPv4Group0", "canJoin6WithIPv4Group0", "canUseIPv6OptionsWithIPv4LocalAddress0"})
            Natives.reg(n + f, (t, b) -> t.P[b] = 0);
        Natives.reg(n + "isExclusiveBindAvailable", (t, b) -> t.P[b] = -1);
        Natives.reg(n + "pollinValue", (t, b) -> t.P[b] = 1);
        Natives.reg(n + "polloutValue", (t, b) -> t.P[b] = 4);
        Natives.reg(n + "pollerrValue", (t, b) -> t.P[b] = 8);
        Natives.reg(n + "pollhupValue", (t, b) -> t.P[b] = 16);
        Natives.reg(n + "pollnvalValue", (t, b) -> t.P[b] = 32);
        Natives.reg(n + "pollconnValue", (t, b) -> t.P[b] = 4);
        Natives.reg(n + "socket0", (t, b) -> {
            if (t.P[b + 1] == 0) throw VM.thrown(t, "java/net/SocketException", "UDP is not supported");
            t.P[b] = add(new Sock());
        });
        Natives.reg(n + "bind0", (t, b) -> {
            Sock s = sock(t, t.R[b]);
            InetSocketAddress a = new InetSocketAddress(host(t.R[b + 3]), (int) t.P[b + 4]);
            io(t, () -> {
                s.server = ServerSocketChannel.open();
                s.server.configureBlocking(false);
                s.server.bind(a, 50);
                return 0;
            });
        });
        Natives.reg(n + "listen", Natives.NOP);
        Natives.reg(n + "connect0", (t, b) -> {
            Sock s = sock(t, t.R[b + 1]);
            InetSocketAddress a = new InetSocketAddress(host(t.R[b + 2]), (int) t.P[b + 3]);
            t.P[b] = io(t, () -> {
                s.client = SocketChannel.open();
                s.client.configureBlocking(false);
                if (s.client.connect(a)) return 1;
                if (!s.blocking) return -2;
                while (!s.client.finishConnect()) await(s, SelectionKey.OP_CONNECT, -1);
                return 1;
            });
        });
        Natives.reg(n + "pollConnect", (t, b) -> {
            Sock s = sock(t, t.R[b]);
            long ms = t.P[b + 1];
            t.P[b] = io(t, () -> {
                if (s.client.finishConnect()) return 1;
                if (ms == 0) return 0;
                await(s, SelectionKey.OP_CONNECT, ms);
                return s.client.finishConnect() ? 1 : 0;
            });
        });
        Natives.reg(n + "accept", (t, b) -> {
            Sock s = sock(t, t.R[b]);
            Obj nfd = t.R[b + 1];
            Obj[] isa = (Obj[]) t.R[b + 2].x;
            long r = io(t, () -> {
                SocketChannel ch;
                while ((ch = s.server.accept()) == null) {
                    if (!s.blocking) return -2;
                    await(s, SelectionKey.OP_ACCEPT, -1);
                }
                ch.configureBlocking(false);
                Sock ns = new Sock();
                ns.client = ch;
                nfd.p[nfd.k.field("fd").slot] = add(ns);
                InetSocketAddress ra = (InetSocketAddress) ch.getRemoteAddress();
                Klass k = VM.load(t, "java/net/InetSocketAddress", null);
                Obj o = k.alloc();
                VM.call(t, k.mmap.get("<init>(Ljava/net/InetAddress;I)V"), o, guest(t, ra.getAddress()), ra.getPort());
                isa[0] = o;
                return 1;
            });
            t.P[b] = r;
        });
        Natives.reg(n + "localPort", (t, b) -> { Sock s = sock(t, t.R[b]); t.P[b] = io(t, () -> { InetSocketAddress a = local(s); return a == null ? 0 : a.getPort(); }); });
        Natives.reg(n + "localInetAddress", (t, b) -> {
            Sock s = sock(t, t.R[b]);
            InetAddress[] a = {null};
            io(t, () -> { InetSocketAddress l = local(s); a[0] = l == null ? InetAddress.getByAddress(new byte[4]) : l.getAddress(); return 0; });
            t.R[b] = guest(t, a[0]);
        });
        Natives.reg(n + "remotePort", (t, b) -> { Sock s = sock(t, t.R[b]); t.P[b] = io(t, () -> ((InetSocketAddress) s.client.getRemoteAddress()).getPort()); });
        Natives.reg(n + "remoteInetAddress", (t, b) -> {
            Sock s = sock(t, t.R[b]);
            InetAddress[] a = {null};
            io(t, () -> { a[0] = ((InetSocketAddress) s.client.getRemoteAddress()).getAddress(); return 0; });
            t.R[b] = guest(t, a[0]);
        });
        Natives.reg(n + "getIntOption0", (t, b) -> t.P[b] = sock(t, t.R[b]).opts.getOrDefault((int) t.P[b + 3], 65536));
        Natives.reg(n + "setIntOption0", (t, b) -> sock(t, t.R[b]).opts.put((int) t.P[b + 3], (int) t.P[b + 4]));
        Natives.reg(n + "available", (t, b) -> t.P[b] = 0);
        Natives.reg(n + "shutdown", (t, b) -> {
            Sock s = sock(t, t.R[b]);
            int how = (int) t.P[b + 1];
            io(t, () -> {
                if (s.client != null && how != 1) s.client.shutdownInput();
                if (s.client != null && how != 0) s.client.shutdownOutput();
                return 0;
            });
        });
        Natives.reg(n + "poll", (t, b) -> {
            Sock s = sock(t, t.R[b]);
            int ev = (int) t.P[b + 1];
            long ms = t.P[b + 2];
            t.P[b] = io(t, () -> {
                int op = ops(s, ev);
                Selector sel;
                synchronized (s) {
                    if (s.wait == null) s.wait = Selector.open();
                    sel = s.wait;
                }
                synchronized (sel) {
                    SelectionKey k = s.channel().register(sel, op);
                    int got = ms < 0 ? sel.select() : ms == 0 ? sel.selectNow() : sel.select(ms);
                    int ready = got > 0 ? k.readyOps() : 0;
                    k.cancel();
                    sel.selectNow();
                    return events(ready);
                }
            });
        });
        Natives.reg("sun/nio/ch/SocketDispatcher.read0", (t, b) -> {
            Sock s = sock(t, t.R[b]);
            long a = t.P[b + 1];
            int len = (int) t.P[b + 3];
            t.P[b] = io(t, () -> read(t, s, a, len));
        });
        Natives.reg("sun/nio/ch/SocketDispatcher.readv0", (t, b) -> {
            Sock s = sock(t, t.R[b]);
            long a = t.P[b + 1];
            int cnt = (int) t.P[b + 3];
            t.P[b] = io(t, () -> {
                long total = 0;
                for (int i = 0; i < cnt; i++) {
                    int len = (int) U.getLong(a + i * 16L + 8);
                    if (len == 0) continue;
                    int r = read(t, s, U.getLong(a + i * 16L), len);
                    if (r < 0) return total == 0 ? r : total;
                    total += r;
                    if (r < len) break;
                    s.blocking = false;
                }
                return total;
            });
        });

        String u = "sun/nio/ch/IOUtil.";
        Natives.reg(u + "configureBlocking", (t, b) -> { Sock s = socks.get(Nio.fd(t.R[b])); if (s != null) s.blocking = t.P[b + 1] != 0; });
        Natives.reg(u + "makePipe", (t, b) -> {
            boolean blocking = t.P[b] != 0;
            t.P[b] = io(t, () -> {
                Pipe p = Pipe.open();
                p.source().configureBlocking(false);
                p.sink().configureBlocking(false);
                Sock r = new Sock(), w = new Sock();
                r.source = p.source();
                w.sink = p.sink();
                r.blocking = w.blocking = blocking;
                return (long) add(r) << 32 | add(w);
            });
        });
        Natives.reg(u + "write1", (t, b) -> {
            Sock s = socks.get((int) t.P[b]);
            t.P[b] = io(t, () -> s.sink.write(ByteBuffer.wrap(new byte[]{(byte) t.P[b + 1]})));
        });
        Natives.reg(u + "drain", (t, b) -> {
            Sock s = socks.get((int) t.P[b]);
            t.P[b] = io(t, () -> {
                ByteBuffer bb = ByteBuffer.allocate(128);
                boolean any = false;
                while (s.source.read(bb) > 0) { any = true; bb.clear(); }
                return any ? 1 : 0;
            });
        });
        Natives.reg(u + "drain1", (t, b) -> {
            Sock s = socks.get((int) t.P[b]);
            t.P[b] = io(t, () -> s.source.read(ByteBuffer.allocate(1)) > 0 ? 1 : 0);
        });

        Natives.reg("sun/nio/ch/EventFD.eventfd0", (t, b) -> t.P[b] = io(t, () -> {
            Pipe p = Pipe.open();
            p.source().configureBlocking(false);
            p.sink().configureBlocking(false);
            Sock s = new Sock();
            s.source = p.source();
            s.sink = p.sink();
            s.blocking = false;
            return add(s);
        }));
        Natives.reg("sun/nio/ch/EventFD.set0", (t, b) -> {
            Sock s = socks.get((int) t.P[b]);
            t.P[b] = io(t, () -> s.sink.write(ByteBuffer.wrap(new byte[]{1})));
        });
        String e = "sun/nio/ch/EPoll.";
        Natives.reg(e + "eventSize", (t, b) -> t.P[b] = 12);
        Natives.reg(e + "eventsOffset", (t, b) -> t.P[b] = 0);
        Natives.reg(e + "dataOffset", (t, b) -> t.P[b] = 4);
        Natives.reg(e + "create", (t, b) -> t.P[b] = io(t, () -> {
            int fd = IO.next.getAndIncrement();
            polls.put(fd, Selector.open());
            return fd;
        }));
        Natives.reg(e + "ctl", (t, b) -> {
            Selector sel = polls.get((int) t.P[b]);
            int op = (int) t.P[b + 1], fd = (int) t.P[b + 2], ev = (int) t.P[b + 3];
            Sock s = socks.get(fd);
            if (s == null) { t.P[b] = 9; return; }
            SelectionKey k = s.channel().keyFor(sel);
            t.P[b] = io(t, () -> {
                if (op == 2) { if (k != null) k.cancel(); return 0; }
                int ops = ops(s, ev);
                if (k != null && k.isValid()) k.interestOps(ops);
                else s.channel().register(sel, ops, fd);
                return 0;
            });
        });
        Natives.reg(e + "wait", (t, b) -> {
            Selector sel = polls.get((int) t.P[b]);
            long addr = t.P[b + 1];
            int max = (int) t.P[b + 3], ms = (int) t.P[b + 4];
            t.P[b] = io(t, () -> {
                sel.selectedKeys().clear();
                if (ms < 0) sel.select(); else if (ms == 0) sel.selectNow(); else sel.select(ms);
                int i = 0;
                for (SelectionKey k : sel.selectedKeys()) {
                    if (i == max) break;
                    U.putInt(addr + i * 12L, events(k.readyOps()));
                    U.putLong(addr + i * 12L + 4, (Integer) k.attachment());
                    i++;
                }
                sel.selectedKeys().clear();
                return i;
            });
        });

        for (String c : new String[]{"java/net/InetAddress.init", "java/net/Inet4Address.init", "java/net/Inet6Address.init", "java/net/NetworkInterface.init"}) Natives.reg(c, Natives.NOP);
        Natives.reg("java/net/InetAddressImplFactory.isIPv6Supported", (t, b) -> t.P[b] = 0);
        Natives.reg("java/net/Inet4AddressImpl.getLocalHostName", (t, b) -> t.R[b] = VM.str("localhost"));
        Natives.reg("java/net/Inet4AddressImpl.lookupAllHostAddr", (t, b) -> {
            String h = VM.str(t.R[b + 1]);
            java.util.ArrayList<Obj> out = new java.util.ArrayList<>();
            try {
                for (InetAddress a : InetAddress.getAllByName(h)) if (a.getAddress().length == 4)
                    out.add(VM.callR(t, VM.method(t, "java/net/InetAddress", "getByAddress", "(Ljava/lang/String;[B)Ljava/net/InetAddress;"), VM.str(h), VM.bytes(a.getAddress())));
            } catch (IOException x) { }
            if (out.isEmpty()) throw VM.thrown(t, "java/net/UnknownHostException", h);
            t.R[b] = new Obj(VM.arrayOf(VM.load(t, "java/net/InetAddress", null)), out.toArray(new Obj[0]));
        });
        Natives.reg("java/net/Inet4AddressImpl.getHostByAddr", (t, b) -> {
            try {
                t.R[b] = VM.str(InetAddress.getByAddress((byte[]) t.R[b + 1].x).getCanonicalHostName());
            } catch (IOException x) {
                throw VM.thrown(t, "java/net/UnknownHostException", null);
            }
        });
        Natives.reg("java/net/Inet4AddressImpl.isReachable0", (t, b) -> t.P[b] = 0);
        Natives.reg("java/net/NetworkInterface.getAll", (t, b) -> t.R[b] = new Obj(VM.arrayOf(VM.load(t, "java/net/NetworkInterface", null)), new Obj[0]));
        Natives.reg("java/net/NetworkInterface.getByInetAddress0", (t, b) -> t.R[b] = null);
        Natives.reg("java/net/NetworkInterface.boundInetAddress0", (t, b) -> t.P[b] = 1);
    }
}
