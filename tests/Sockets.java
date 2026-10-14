import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;

public class Sockets {
    public static void main(String[] args) throws Exception {
        try (ServerSocket ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread echo = new Thread(() -> {
                try (Socket c = ss.accept(); BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream())); PrintWriter out = new PrintWriter(c.getOutputStream(), true)) {
                    for (String l; (l = in.readLine()) != null; ) out.println(l.toUpperCase());
                } catch (IOException e) { System.out.println("server " + e); }
            });
            echo.start();
            try (Socket s = new Socket(InetAddress.getLoopbackAddress(), ss.getLocalPort()); BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream())); PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                out.println("hello");
                out.println("sockets ✓");
                System.out.println(in.readLine() + " | " + in.readLine() + " " + s.isConnected() + " " + (s.getLocalPort() > 0));
            }
            echo.join();
        }
        try { new Socket(InetAddress.getLoopbackAddress(), 1); } catch (ConnectException e) { System.out.println("refused"); }
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/hi", ex -> {
            byte[] body = ("hi " + ex.getRequestMethod() + " " + ex.getRequestURI().getQuery()).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("X-Test", "yes");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(body); }
        });
        server.start();
        int port = server.getAddress().getPort();
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/hi?a=1").openConnection();
        System.out.println(c.getResponseCode() + " " + c.getHeaderField("X-Test") + " " + new String(c.getInputStream().readAllBytes()));
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/hi?b=2")).POST(HttpRequest.BodyPublishers.ofString("x")).build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(r.statusCode() + " " + r.body());
        server.stop(0);
        System.out.println(InetAddress.getByName("localhost").isLoopbackAddress());
        System.exit(0);
    }
}
