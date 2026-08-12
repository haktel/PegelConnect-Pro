package de.bais.pegelconnect;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class PegelOnlineClient {

    private final AppConfig config;

    private final HttpClient http;


    // =========================================================
    // KONSTRUKTOR
    // =========================================================

    public PegelOnlineClient(
            AppConfig config
    ) {

        this.config =
                config;

        this.http =
                HttpClient
                        .newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(
                                        config.pegelOnlineTimeoutSeconds()
                                )
                        )
                        .build();
    }


    // =========================================================
    // AKTUELLEN PEGEL ABRUFEN
    // =========================================================

    public RawMeasurement fetch(
            String station
    ) throws IOException, InterruptedException {

        AppConfig.StationConfig stationConfig =
                requireStation(
                        station
                );

        String encoded =
                encodeStation(
                        stationConfig.apiName()
                );

        URI uri =
                URI.create(
                        baseUrl()
                                + "/stations/"
                                + encoded
                                + "/W/currentmeasurement.json"
                );

        HttpRequest request =
                createRequest(
                        uri
                );

        HttpResponse<String> response =
                http.send(
                        request,
                        HttpResponse
                                .BodyHandlers
                                .ofString()
                );

        ensureSuccess(
                response,
                "PEGELONLINE",
                station
        );


        JSONObject json =
                new JSONObject(
                        response.body()
                );


        if (
                !json.has("timestamp")
                        || !json.has("value")
                        || json.isNull("value")
        ) {

            throw new IOException(
                    "Unvollständige PEGELONLINE-Antwort für "
                            + station
            );
        }


        return new RawMeasurement(
                json.getString(
                        "timestamp"
                ),
                json.getDouble(
                        "value"
                )
        );
    }


    // =========================================================
    // HISTORIE ABRUFEN
    // =========================================================

    public List<RawMeasurement> fetchHistory(
            String station,
            String period
    ) throws IOException, InterruptedException {

        AppConfig.StationConfig stationConfig =
                requireStation(
                        station
                );

        String encoded =
                encodeStation(
                        stationConfig.apiName()
                );


        String apiPeriod =
                mapPeriod(
                        period
                );


        URI uri =
                URI.create(
                        baseUrl()
                                + "/stations/"
                                + encoded
                                + "/W/measurements.json?start="
                                + apiPeriod
                );


        HttpRequest request =
                createRequest(
                        uri
                );


        HttpResponse<String> response =
                http.send(
                        request,
                        HttpResponse
                                .BodyHandlers
                                .ofString()
                );


        ensureSuccess(
                response,
                "PEGELONLINE History",
                station
        );


        JSONArray json =
                new JSONArray(
                        response.body()
                );


        List<RawMeasurement> measurements =
                new ArrayList<>();


        for (
                int i = 0;
                i < json.length();
                i++
        ) {

            JSONObject item =
                    json.optJSONObject(
                            i
                    );

            if (item == null) {
                continue;
            }


            if (
                    !item.has("timestamp")
                            || !item.has("value")
                            || item.isNull("value")
            ) {
                continue;
            }


            measurements.add(
                    new RawMeasurement(
                            item.getString(
                                    "timestamp"
                            ),
                            item.getDouble(
                                    "value"
                            )
                    )
            );
        }


        return List.copyOf(
                measurements
        );
    }


    // =========================================================
    // BASE URL
    // =========================================================

    private String baseUrl() {

        String server =
                trimTrailingSlash(
                        config.pegelOnlineServer()
                );

        String apiPath =
                normalizePath(
                        config.pegelOnlineApiPath()
                );


        return server
                + apiPath;
    }


    // =========================================================
    // STATION VALIDIEREN
    // =========================================================

    private AppConfig.StationConfig requireStation(
            String station
    ) {

        AppConfig.StationConfig result =
                config.station(
                        station
                );


        if (result == null) {

            throw new IllegalArgumentException(
                    "Unbekannte Pegelstation: "
                            + station
            );
        }


        return result;
    }


    // =========================================================
    // ZEITRAUM
    // =========================================================

    private String mapPeriod(
            String period
    ) {

        if (period == null) {
            return "P1D";
        }


        return switch (
                period.toLowerCase()
        ) {

            case "24h" ->
                    "P1D";

            case "7d" ->
                    "P7D";

            case "30d" -> {

                int days =
                        Math.min(
                                30,
                                config.historyMaxDays()
                        );

                yield "P"
                        + days
                        + "D";
            }

            default ->
                    throw new IllegalArgumentException(
                            "Ungültiger History-Zeitraum: "
                                    + period
                    );
        };
    }


    // =========================================================
    // HTTP REQUEST
    // =========================================================

    private HttpRequest createRequest(
            URI uri
    ) {

        return HttpRequest
                .newBuilder(
                        uri
                )
                .timeout(
                        Duration.ofSeconds(
                                config.pegelOnlineTimeoutSeconds()
                        )
                )
                .header(
                        "Accept",
                        "application/json"
                )
                .header(
                        "User-Agent",
                        config.applicationName()
                                .replace(
                                        " ",
                                        "-"
                                )
                                + "/1.0"
                )
                .GET()
                .build();
    }


    // =========================================================
    // HTTP STATUS
    // =========================================================

    private static void ensureSuccess(
            HttpResponse<String> response,
            String service,
            String station
    ) throws IOException {

        if (
                response.statusCode()
                        < 200
                        || response.statusCode()
                        >= 300
        ) {

            throw new IOException(
                    service
                            + " HTTP "
                            + response.statusCode()
                            + " für "
                            + station
            );
        }
    }


    // =========================================================
    // URL HELPERS
    // =========================================================

    private static String encodeStation(
            String station
    ) {

        return URLEncoder
                .encode(
                        station,
                        StandardCharsets.UTF_8
                )
                .replace(
                        "+",
                        "%20"
                );
    }


    private static String trimTrailingSlash(
            String value
    ) {

        if (value == null) {
            return "";
        }


        String result =
                value.trim();


        while (
                result.endsWith("/")
        ) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }


        return result;
    }


    private static String normalizePath(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return "";
        }


        String result =
                value.trim();


        if (
                !result.startsWith("/")
        ) {

            result =
                    "/"
                            + result;
        }


        while (
                result.endsWith("/")
        ) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }


        return result;
    }


    // =========================================================
    // RAW MEASUREMENT
    // =========================================================

    public record RawMeasurement(
            String timestamp,
            double value
    ) {}
}