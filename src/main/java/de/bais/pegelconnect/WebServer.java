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

    private final AppConfig config;

    private final WeatherClient weatherClient;

    private final PegelOnlineClient pegelOnlineClient;


    // =========================================================
    // KONSTRUKTOR
    // =========================================================

    public WebServer(
            AppConfig config,
            ReadingStore store,
            BooleanSupplier mqttConnected,
            PegelOnlineClient pegelOnlineClient
    ) throws IOException {

        this.config =
                config;

        this.pegelOnlineClient =
                pegelOnlineClient;

        this.weatherClient =
                new WeatherClient(
                        config
                );


        server =
                HttpServer.create(
                        new InetSocketAddress(
                                "0.0.0.0",
                                config.httpPort()
                        ),
                        0
                );


        server.setExecutor(
                Executors.newCachedThreadPool()
        );


        // =====================================================
        // API STATE
        // =====================================================

        server.createContext(
                "/api/state",
                exchange -> {

                    if (
                            !"GET".equals(
                                    exchange.getRequestMethod()
                            )
                    ) {

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
                }
        );


        // =====================================================
        // API HISTORY
        // =====================================================

        server.createContext(
                "/api/history",
                exchange -> {

                    if (
                            !"GET".equals(
                                    exchange.getRequestMethod()
                            )
                    ) {

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
                                                defaultStation()
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


                        validateStation(
                                station
                        );


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


                            values.put(
                                    item
                            );
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


                    } catch (
                            IllegalArgumentException ex
                    ) {

                        send(
                                exchange,
                                400,
                                "application/json; charset=UTF-8",
                                errorJson(
                                        "invalid_request",
                                        ex
                                )
                        );


                    } catch (Exception ex) {

                        send(
                                exchange,
                                500,
                                "application/json; charset=UTF-8",
                                errorJson(
                                        "history_failed",
                                        ex
                                )
                        );
                    }
                }
        );


        // =====================================================
        // API WEATHER
        // =====================================================

        server.createContext(
                "/api/weather",
                exchange -> {

                    if (
                            !"GET".equals(
                                    exchange.getRequestMethod()
                            )
                    ) {

                        send(
                                exchange,
                                405,
                                "application/json",
                                "{\"error\":\"method_not_allowed\"}"
                        );

                        return;
                    }


                    try {

                        if (
                                !config.weatherEnabled()
                        ) {

                            send(
                                    exchange,
                                    503,
                                    "application/json; charset=UTF-8",
                                    "{\"error\":\"weather_disabled\"}"
                            );

                            return;
                        }


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
                                                defaultStation()
                                        )
                                        .toUpperCase();


                        validateStation(
                                station
                        );


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


                    } catch (
                            IllegalArgumentException ex
                    ) {

                        send(
                                exchange,
                                400,
                                "application/json; charset=UTF-8",
                                errorJson(
                                        "invalid_request",
                                        ex
                                )
                        );


                    } catch (Exception ex) {

                        send(
                                exchange,
                                500,
                                "application/json; charset=UTF-8",
                                errorJson(
                                        "weather_failed",
                                        ex
                                )
                        );
                    }
                }
        );


        // =====================================================
        // API CONFIG
        // =====================================================

        server.createContext(
                "/api/config",
                exchange -> {

                    if (
                            !"GET".equals(
                                    exchange.getRequestMethod()
                            )
                    ) {

                        send(
                                exchange,
                                405,
                                "application/json",
                                "{\"error\":\"method_not_allowed\"}"
                        );

                        return;
                    }


                    JSONObject response =
                            new JSONObject();


                    response.put(
                            "application",
                            config.applicationName()
                    );


                    response.put(
                            "httpPort",
                            config.httpPort()
                    );


                    response.put(
                            "fetchIntervalSeconds",
                            config.fetchIntervalSeconds()
                    );


                    response.put(
                            "pegelOnlineServer",
                            config.pegelOnlineServer()
                    );


                    response.put(
                            "pegelOnlineApiPath",
                            config.pegelOnlineApiPath()
                    );


                    response.put(
                            "weatherEnabled",
                            config.weatherEnabled()
                    );


                    response.put(
                            "weatherServer",
                            config.weatherServer()
                    );


                    response.put(
                            "mqttBroker",
                            config.mqttBrokerUri()
                    );


                    response.put(
                            "mqttClientId",
                            config.mqttClientId()
                    );


                    JSONArray stations =
                            new JSONArray();


                    for (
                            AppConfig.StationConfig station
                            : config.stations()
                    ) {

                        JSONObject item =
                                new JSONObject();


                        item.put(
                                "name",
                                station.name()
                        );


                        item.put(
                                "apiName",
                                station.apiName()
                        );


                        item.put(
                                "uuid",
                                station.uuid()
                        );


                        item.put(
                                "latitude",
                                station.latitude()
                        );


                        item.put(
                                "longitude",
                                station.longitude()
                        );


                        stations.put(
                                item
                        );
                    }


                    response.put(
                            "stations",
                            stations
                    );


                    send(
                            exchange,
                            200,
                            "application/json; charset=UTF-8",
                            response.toString()
                    );
                }
        );


        // =====================================================
        // FRONTEND
        // =====================================================

        server.createContext(
                "/",
                this::staticFile
        );
    }


    // =========================================================
    // START
    // =========================================================

    public void start() {

        server.start();

        System.out.println(
                "WebServer gestartet auf Port "
                        + config.httpPort()
        );
    }


    // =========================================================
    // DEFAULT STATION
    // =========================================================

    private String defaultStation() {

        if (
                config.stationNames().isEmpty()
        ) {

            throw new IllegalStateException(
                    "Keine Station konfiguriert."
            );
        }


        return config
                .stationNames()
                .get(0);
    }


    // =========================================================
    // STATION VALIDIERUNG
    // =========================================================

    private void validateStation(
            String station
    ) {

        if (
                config.station(
                        station
                ) == null
        ) {

            throw new IllegalArgumentException(
                    "Unbekannte Station: "
                            + station
            );
        }
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


            if (
                    parts.length != 2
            ) {

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


        if (
                path.contains("..")
        ) {

            send(
                    exchange,
                    400,
                    "text/plain; charset=UTF-8",
                    "Bad request"
            );

            return;
        }


        String resource =
                "/web"
                        + path;


        try (
                InputStream in =
                        WebServer.class
                                .getResourceAsStream(
                                        resource
                                )
        ) {

            if (
                    in == null
            ) {

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
                    .write(
                            bytes
                    );


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


        if (
                message == null
                        || message.isBlank()
        ) {

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


        exchange
                .getResponseHeaders()
                .set(
                        "X-Content-Type-Options",
                        "nosniff"
                );


        exchange
                .getResponseHeaders()
                .set(
                        "X-Frame-Options",
                        "SAMEORIGIN"
                );


        exchange.sendResponseHeaders(
                status,
                bytes.length
        );


        exchange
                .getResponseBody()
                .write(
                        bytes
                );


        exchange.close();
    }


    // =========================================================
    // SHUTDOWN
    // =========================================================

    @Override
    public void close() {

        server.stop(
                1
        );
    }
}