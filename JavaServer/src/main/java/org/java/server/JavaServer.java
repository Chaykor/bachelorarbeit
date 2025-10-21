package org.java.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Semaphore;

public class JavaServer {

    // Max. parallel verarbeitete Requests (pro Prozess)
    private static final int MAX_CONCURRENT = Math.max(64, Runtime.getRuntime().availableProcessors() * 8);
    private static final Semaphore PERMITS = new Semaphore(MAX_CONCURRENT);

    public static void main(String[] args) throws IOException {
        String publicUrl = System.getenv("SERVER_PUBLIC_URL");
        if (publicUrl != null && !publicUrl.isBlank()) {
            System.out.println("Extern erreichbar unter " + publicUrl);
        }

        final int port = 3000;
        final int backlog = 4096;

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), backlog);

        server.createContext("/json", new BackpressuredHandler(new JsonHandler()));
        server.createContext("/xml",  new BackpressuredHandler(new XmlHandler()));

        int cores = Runtime.getRuntime().availableProcessors();
        int corePool = Math.max(8, cores);          // Start
        int maxPool  = Math.max(128, cores * 32);   // Obergrenze
        int queueCap = 2000;

        ThreadFactory tf = new ThreadFactory() {
            final AtomicInteger n = new AtomicInteger(1);
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "http-worker-" + n.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };

        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                corePool, maxPool, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCap), tf,
                new ThreadPoolExecutor.AbortPolicy()
        );
        server.setExecutor(exec);

        String ipToShow = firstNonLoopbackIPv4();
        System.out.printf("Server läuft unter http://%s:%d mit %d..%d Threads, backlog=%d, maxConcurrent=%d%n",
                ipToShow, port, corePool, maxPool, backlog, MAX_CONCURRENT);

        server.start();
    }

    static class BackpressuredHandler implements HttpHandler {
        private final HttpHandler delegate;

        BackpressuredHandler(HttpHandler delegate) { this.delegate = delegate; }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }

            if (!PERMITS.tryAcquire()) {
                drainRequestQuietly(ex); // Body konsumieren, damit Socket sauber bleibt
                ex.getResponseHeaders().add("Retry-After", "1");
                byte[] msg = "Too Many Requests".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(429, msg.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
                return;
            }

            try {
                delegate.handle(ex);
            } finally {
                PERMITS.release();
            }
        }
    }

    static class JsonHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            discard(ex.getRequestBody()); // kompletten Body lesen & verwerfen
            byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, ok.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(ok); }
        }
    }

    static class XmlHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            discard(ex.getRequestBody());
            byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, ok.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(ok); }
        }
    }

    private static void discard(InputStream in) throws IOException {
        byte[] buf = new byte[1 << 14]; // 16 KiB
        while (in.read(buf) != -1) {  }
        in.close();
    }
    private static void drainRequestQuietly(HttpExchange ex) {
        try { discard(ex.getRequestBody()); } catch (Exception ignored) {}
        try { ex.getResponseBody(); } catch (Exception ignored) {}
    }

    static String firstNonLoopbackIPv4() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .flatMap(ni -> Collections.list(ni.getInetAddresses()).stream())
                    .filter(ia -> !ia.isLoopbackAddress() && ia instanceof Inet4Address)
                    .map(InetAddress::getHostAddress)
                    .findFirst().orElse("127.0.0.1");
        } catch (SocketException e) { return "127.0.0.1"; }
    }
}