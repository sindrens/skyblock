package net.skybornmc.skyblock.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Tiny web server that serves exactly one file: {@code GET /<file>} as application/zip, 404 for everything else. */
final class PackHost {

    /** What a request gets. */
    enum Route { OK, NOT_FOUND, METHOD_NOT_ALLOWED }

    /** The served file. */
    record Served(String name, byte[] data) {
    }

    private final String bind;
    private final int port;
    private volatile Served served;
    private HttpServer server;

    PackHost(String bind, int port) {
        this.bind = bind == null ? "" : bind.trim();
        this.port = port;
    }

    /** Pure routing, for tests. Only GET of the exact file path is served; a query string is ignored. */
    static Route route(String method, String path, String fileName) {
        if (path == null || fileName == null || !path.equals("/" + fileName)) {
            return Route.NOT_FOUND;
        }
        return "GET".equalsIgnoreCase(method) ? Route.OK : Route.METHOD_NOT_ALLOWED;
    }

    boolean matches(String bind, int port) {
        return this.bind.equals(bind == null ? "" : bind.trim()) && this.port == port;
    }

    void serve(Served file) {
        this.served = file;
    }

    void start() throws IOException {
        InetSocketAddress address = bind.isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(bind, port);
        HttpServer s = HttpServer.create(address, 64);
        s.createContext("/", this::handle);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); // virtual threads are daemon threads
        s.start();
        server = s;
    }

    void stop() {
        HttpServer s = server;
        server = null;
        if (s != null) {
            s.stop(0);
        }
    }

    boolean running() {
        return server != null;
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            Served file = served;
            Route route = route(ex.getRequestMethod(), ex.getRequestURI().getPath(), file == null ? null : file.name());
            switch (route) {
                case OK -> {
                    ex.getResponseHeaders().set("Content-Type", "application/zip");
                    ex.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable"); // the name has the hash
                    ex.sendResponseHeaders(200, file.data().length);
                    try (OutputStream out = ex.getResponseBody()) {
                        out.write(file.data());
                    }
                }
                case METHOD_NOT_ALLOWED -> {
                    ex.getResponseHeaders().set("Allow", "GET");
                    ex.sendResponseHeaders(405, -1);
                }
                case NOT_FOUND -> ex.sendResponseHeaders(404, -1);
            }
        }
    }
}
