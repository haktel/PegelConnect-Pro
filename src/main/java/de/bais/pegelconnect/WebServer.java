package de.bais.pegelconnect;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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

    public WebServer(
            int port,
            ReadingStore store,
            BooleanSupplier mqttConnected
    ) throws IOException {

        server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", port),
                0
        );

        server.setExecutor(Executors.newCachedThreadPool());

        WeatherClient weatherClient =
                new WeatherClient();

        PegelOnlineClient pegelOnlineClient =
                new PegelOnlineClient();


        // =====================================================
        // SYSTEMSTATUS / AKTUELLE PEGELDATEN
        // =====================================================

        server.createContext("/api/state", exchange -> {

            if (!"GET".equals(exchange.getRequestMethod())) {

                send(
                        exchange,
                        405,
                        "application/json",
                        "{\"error\":\"method_not_allowed\"}"
                );

                return;
            }

            send(
                    exchange,
                    200,
                    "application/json; charset=UTF-8",
                    store.snapshot(
                            mqttConnected.getAsBoolean()
                    ).toString()
            );
        });


        // =====================================================
        // PEGEL-HISTORIE
        //
        // Beispiele:
        //
        // /api/history?station=KÖLN&period=24h
        // /api/history?station=BONN&period=7d
        // /api/history?station=MAINZ&period=30d
        // =====================================================

        server.createContext("/api/history", exchange -> {

            if (!"GET".equals(exchange.getRequestMethod())) {

                send(
                        exchange,
                        405,
                        "application/json",
                        "{\"error\":\"method_not_allowed\"}"
                );

                return;
            }

            try {

                Map<String, String> query =
                        parseQuery(
                                exchange
                                        .getRequestURI()
                                        .getRawQuery()
                        );

                String station =
                        query
                                .getOrDefault(
                                        "station",
                                        "KÖLN"
                                )
                                .toUpperCase();

                String period =
                        query
                                .getOrDefault(
                                        "period",
                                        "24h"
                                )
                                .toLowerCase();

                if (
                        !"24h".equals(period)
                                && !"7d".equals(period)
                                && !"30d".equals(period)
                ) {

                    send(
                            exchange,
                            400,
                            "application/json; charset=UTF-8",
                            "{\"error\":\"invalid_period\"}"
                    );

                    return;
                }

                var measurements =
                        pegelOnlineClient.fetchHistory(
                                station,
                                period
                        );

                JSONArray values =
                        new JSONArray();

                for (
                        PegelOnlineClient.RawMeasurement measurement
                        : measurements
                ) {

                    JSONObject item =
                            new JSONObject();

                    item.put(
                            "timestamp",
                            measurement.timestamp()
                    );

                    item.put(
                            "value",
                            measurement.value()
                    );

                    values.put(item);
                }

                JSONObject response =
                        new JSONObject();

                response.put(
                        "station",
                        station
                );

                response.put(
                        "period",
                        period
                );

                response.put(
                        "count",
                        values.length()
                );

                response.put(
                        "measurements",
                        values
                );

                send(
                        exchange,
                        200,
                        "application/json; charset=UTF-8",
                        response.toString()
                );

            } catch (Exception e) {

                send(
                        exchange,
                        500,
                        "application/json; charset=UTF-8",
                        errorJson(
                                "history_failed",
                                e
                        )
                );
            }
        });


        // =====================================================
        // WETTER
        //
        // Beispiel:
        //
        // /api/weather?station=KÖLN
        // =====================================================

        server.createContext("/api/weather", exchange -> {

            if (!"GET".equals(exchange.getRequestMethod())) {

                send(
                        exchange,
                        405,
                        "application/json",
                        "{\"error\":\"method_not_allowed\"}"
                );

                return;
            }

            try {

                Map<String, String> query =
                        parseQuery(
                                exchange
                                        .getRequestURI()
                                        .getRawQuery()
                        );

                String station =
                        query
                                .getOrDefault(
                                        "station",
                                        "KÖLN"
                                )
                                .toUpperCase();

                JSONObject weather =
                        weatherClient.fetch(
                                station
                        );

                send(
                        exchange,
                        200,
                        "application/json; charset=UTF-8",
                        weather.toString()
                );

            } catch (Exception e) {

                send(
                        exchange,
                        500,
                        "application/json; charset=UTF-8",
                        errorJson(
                                "weather_failed",
                                e
                        )
                );
            }
        });


        // =====================================================
        // FRONTEND
        // =====================================================

        server.createContext(
                "/",
                this::staticFile
        );
    }


    public void start() {
        server.start();
    }


    // =========================================================
    // QUERY PARAMETER
    // =========================================================

    private static Map<String, String> parseQuery(
            String rawQuery
    ) {

        Map<String, String> result =
                new java.util.HashMap<>();

        if (
                rawQuery == null
                        || rawQuery.isBlank()
        ) {
            return result;
        }

        for (
                String parameter
                : rawQuery.split("&")
        ) {

            String[] parts =
                    parameter.split(
                            "=",
                            2
                    );

            if (parts.length != 2) {
                continue;
            }

            String key =
                    URLDecoder.decode(
                            parts[0],
                            StandardCharsets.UTF_8
                    );

            String value =
                    URLDecoder.decode(
                            parts[1],
                            StandardCharsets.UTF_8
                    );

            result.put(
                    key,
                    value
            );
        }

        return result;
    }


    // =========================================================
    // STATIC FILE SERVER
    // =========================================================

    private void staticFile(
            HttpExchange exchange
    ) throws IOException {

        String requestPath =
                exchange
                        .getRequestURI()
                        .getPath();

        String path =
                requestPath.equals("/")
                        ? "/index.html"
                        : requestPath;

        if (path.contains("..")) {

            send(
                    exchange,
                    400,
                    "text/plain; charset=UTF-8",
                    "Bad request"
            );

            return;
        }

        String resource =
                "/web" + path;

        try (
                InputStream in =
                        WebServer.class
                                .getResourceAsStream(
                                        resource
                                )
        ) {

            if (in == null) {

                send(
                        exchange,
                        404,
                        "text/plain; charset=UTF-8",
                        "Nicht gefunden"
                );

                return;
            }

            byte[] bytes =
                    in.readAllBytes();

            String contentType =
                    CONTENT_TYPES
                            .entrySet()
                            .stream()
                            .filter(
                                    entry ->
                                            path.endsWith(
                                                    entry.getKey()
                                            )
                            )
                            .map(
                                    Map.Entry::getValue
                            )
                            .findFirst()
                            .orElse(
                                    "application/octet-stream"
                            );

            exchange
                    .getResponseHeaders()
                    .set(
                            "Content-Type",
                            contentType
                    );

            exchange
                    .getResponseHeaders()
                    .set(
                            "Cache-Control",
                            "no-cache"
                    );

            exchange.sendResponseHeaders(
                    200,
                    bytes.length
            );

            exchange
                    .getResponseBody()
                    .write(bytes);

            exchange.close();
        }
    }


    // =========================================================
    // JSON ERROR
    // =========================================================

    private static String errorJson(
            String error,
            Exception exception
    ) {

        JSONObject json =
                new JSONObject();

        json.put(
                "error",
                error
        );

        String message =
                exception.getMessage();

        if (message == null) {
            message =
                    exception
                            .getClass()
                            .getSimpleName();
        }

        json.put(
                "message",
                message
        );

        return json.toString();
    }


    // =========================================================
    // HTTP RESPONSE
    // =========================================================

    private static void send(
            HttpExchange exchange,
            int status,
            String type,
            String body
    ) throws IOException {

        byte[] bytes =
                body.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange
                .getResponseHeaders()
                .set(
                        "Content-Type",
                        type
                );

        exchange
                .getResponseHeaders()
                .set(
                        "Cache-Control",
                        "no-store"
                );

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        exchange
                .getResponseBody()
                .write(bytes);

        exchange.close();
    }


    @Override
    public void close() {
        server.stop(1);
    }
}