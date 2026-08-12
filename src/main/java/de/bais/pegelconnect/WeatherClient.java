package de.bais.pegelconnect;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class WeatherClient {

    private final AppConfig config;

    private final HttpClient httpClient;


    // =========================================================
    // KONSTRUKTOR
    // =========================================================

    public WeatherClient(
            AppConfig config
    ) {

        this.config =
                config;

        this.httpClient =
                HttpClient
                        .newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(
                                        config.weatherTimeoutSeconds()
                                )
                        )
                        .build();
    }


    // =========================================================
    // WETTERDATEN ABRUFEN
    // =========================================================

    public JSONObject fetch(
            String station
    ) throws IOException, InterruptedException {

        if (
                !config.weatherEnabled()
        ) {

            throw new IllegalStateException(
                    "Weather API ist deaktiviert."
            );
        }


        AppConfig.StationConfig stationConfig =
                requireStation(
                        station
                );


        if (
                !Double.isFinite(
                        stationConfig.latitude()
                )
                        || !Double.isFinite(
                                stationConfig.longitude()
                        )
        ) {

            throw new IllegalArgumentException(
                    "Keine gültigen Wetter-Koordinaten für "
                            + station
            );
        }


        URI uri =
                buildUri(
                        stationConfig
                );


        HttpRequest request =
                HttpRequest
                        .newBuilder(
                                uri
                        )
                        .timeout(
                                Duration.ofSeconds(
                                        config.weatherTimeoutSeconds()
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


        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse
                                .BodyHandlers
                                .ofString()
                );


        if (
                response.statusCode()
                        < 200
                        || response.statusCode()
                        >= 300
        ) {

            throw new IOException(
                    "Weather API HTTP "
                            + response.statusCode()
                            + " für "
                            + station
            );
        }


        JSONObject root =
                new JSONObject(
                        response.body()
                );


        JSONObject current =
                root.optJSONObject(
                        "current"
                );


        if (current == null) {

            throw new IOException(
                    "Weather API enthält keine current-Daten für "
                            + station
            );
        }


        JSONObject result =
                new JSONObject();


        /*
         * WICHTIG:
         *
         * Diese Feldnamen bleiben bewusst
         * identisch zur bisherigen Version,
         * damit app.js nicht geändert werden muss.
         */

        result.put(
                "station",
                stationConfig.name()
        );


        result.put(
                "temperature",
                current.optDouble(
                        "temperature_2m",
                        Double.NaN
                )
        );


        result.put(
                "apparentTemperature",
                current.optDouble(
                        "apparent_temperature",
                        Double.NaN
                )
        );


        result.put(
                "precipitation",
                current.optDouble(
                        "precipitation",
                        0
                )
        );


        result.put(
                "windSpeed",
                current.optDouble(
                        "wind_speed_10m",
                        0
                )
        );


        result.put(
                "windDirection",
                current.optDouble(
                        "wind_direction_10m",
                        0
                )
        );


        result.put(
                "weatherCode",
                current.optInt(
                        "weather_code",
                        -1
                )
        );


        result.put(
                "timestamp",
                current.optString(
                        "time",
                        ""
                )
        );


        return result;
    }


    // =========================================================
    // URI BAUEN
    // =========================================================

    private URI buildUri(
            AppConfig.StationConfig station
    ) {

        String server =
                trimTrailingSlash(
                        config.weatherServer()
                );


        String timezone =
                URLEncoder.encode(
                        config.weatherTimezone(),
                        StandardCharsets.UTF_8
                );


        String url =
                server
                        + "?latitude="
                        + station.latitude()

                        + "&longitude="
                        + station.longitude()

                        + "&current="

                        + "temperature_2m,"
                        + "apparent_temperature,"
                        + "precipitation,"
                        + "weather_code,"
                        + "wind_speed_10m,"
                        + "wind_direction_10m"

                        + "&timezone="
                        + timezone;


        return URI.create(
                url
        );
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
                    "Unbekannte Wetterstation: "
                            + station
            );
        }


        return result;
    }


    // =========================================================
    // URL HELPER
    // =========================================================

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
}