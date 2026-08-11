package de.bais.pegelconnect;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

public final class WebServer implements AutoCloseable {
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".html", "text/html; charset=UTF-8",
            ".css", "text/css; charset=UTF-8",
            ".js", "application/javascript; charset=UTF-8",
            ".svg", "image/svg+xml"
    );

    private final HttpServer server;

    public WebServer(int port, ReadingStore store, BooleanSupplier mqttConnected) throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/api/state", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "application/json", "{\"error\":\"method_not_allowed\"}");
                return;
            }
            send(exchange, 200, "application/json; charset=UTF-8",
                    store.snapshot(mqttConnected.getAsBoolean()).toString());
        });

        server.createContext("/", this::staticFile);
    }

    public void start() {
        server.start();
    }

    private void staticFile(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
	String path = requestPath.equals("/") ? "/index.html" : requestPath;
        if (path.contains("..")) {
            send(exchange, 400, "text/plain", "Bad request");
            return;
        }

        String resource = "/web" + path;
        try (InputStream in = WebServer.class.getResourceAsStream(resource)) {
            if (in == null) {
                send(exchange, 404, "text/plain; charset=UTF-8", "Nicht gefunden");
                return;
            }
            byte[] bytes = in.readAllBytes();
            String contentType = CONTENT_TYPES.entrySet().stream()
                    .filter(entry -> path.endsWith(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("application/octet-stream");
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(1);
    }
}
