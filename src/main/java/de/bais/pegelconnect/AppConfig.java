package de.bais.pegelconnect;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record AppConfig(
        String applicationName,
        String mqttBrokerUri,
        String mqttClientId,
        int mqttTimeoutSeconds,
        List<StationConfig> stations,
        long fetchIntervalSeconds,
        int httpPort,
        String pegelOnlineServer,
        String pegelOnlineApiPath,
        int pegelOnlineTimeoutSeconds,
        int historyMaxDays,
        boolean weatherEnabled,
        String weatherServer,
        int weatherTimeoutSeconds,
        String weatherTimezone,
        String logDirectory,
        int logRetentionDays
) {

    private static final String DEFAULT_CONFIG_PATH =
            "/etc/pegelconnect-pro/config.json";

    private static final String LOCAL_CONFIG_PATH =
            "config/config.json";


    // =========================================================
    // KONFIGURATION LADEN
    // =========================================================

    public static AppConfig load() {

        Path configPath =
                resolveConfigPath();

        JSONObject json =
                readJson(configPath);

        JSONObject application =
                json.optJSONObject("application");

        JSONObject pegelonline =
                json.optJSONObject("pegelonline");

        JSONObject mqtt =
                json.optJSONObject("mqtt");

        JSONObject weather =
                json.optJSONObject("weather");

        JSONObject logging =
                json.optJSONObject("logging");


        String applicationName =
                env(
                        "APPLICATION_NAME",
                        stringValue(
                                application,
                                "name",
                                "PegelConnect Pro"
                        )
                );


        int httpPort =
                intEnv(
                        "HTTP_PORT",
                        intValue(
                                application,
                                "httpPort",
                                8080
                        ),
                        1
                );


        long fetchIntervalSeconds =
                longEnv(
                        "FETCH_INTERVAL_SECONDS",
                        longValue(
                                application,
                                "fetchIntervalSeconds",
                                60L
                        ),
                        10L
                );


        String pegelOnlineServer =
                env(
                        "PEGELONLINE_SERVER",
                        stringValue(
                                pegelonline,
                                "server",
                                "https://www.pegelonline.wsv.de"
                        )
                );


        String pegelOnlineApiPath =
                env(
                        "PEGELONLINE_API_PATH",
                        stringValue(
                                pegelonline,
                                "apiPath",
                                "/webservices/rest-api/v2"
                        )
                );


        int pegelOnlineTimeoutSeconds =
                intEnv(
                        "PEGELONLINE_TIMEOUT_SECONDS",
                        intValue(
                                pegelonline,
                                "timeoutSeconds",
                                30
                        ),
                        1
                );


        int historyMaxDays =
                intEnv(
                        "HISTORY_MAX_DAYS",
                        intValue(
                                pegelonline,
                                "historyMaxDays",
                                30
                        ),
                        1
                );


        String mqttBrokerUri =
                env(
                        "MQTT_BROKER_URI",
                        stringValue(
                                mqtt,
                                "server",
                                "tcp://localhost:1883"
                        )
                );


        String mqttClientId =
                env(
                        "MQTT_CLIENT_ID",
                        stringValue(
                                mqtt,
                                "clientId",
                                "pegelconnect-pro"
                        )
                );


        int mqttTimeoutSeconds =
                intEnv(
                        "MQTT_TIMEOUT_SECONDS",
                        intValue(
                                mqtt,
                                "timeoutSeconds",
                                10
                        ),
                        1
                );


        boolean weatherEnabled =
                booleanEnv(
                        "WEATHER_ENABLED",
                        booleanValue(
                                weather,
                                "enabled",
                                true
                        )
                );


        String weatherServer =
                env(
                        "WEATHER_SERVER",
                        stringValue(
                                weather,
                                "server",
                                "https://api.open-meteo.com/v1/forecast"
                        )
                );


        int weatherTimeoutSeconds =
                intEnv(
                        "WEATHER_TIMEOUT_SECONDS",
                        intValue(
                                weather,
                                "timeoutSeconds",
                                15
                        ),
                        1
                );


        String weatherTimezone =
                env(
                        "WEATHER_TIMEZONE",
                        stringValue(
                                weather,
                                "timezone",
                                "Europe/Berlin"
                        )
                );


        String logDirectory =
                env(
                        "LOG_DIRECTORY",
                        stringValue(
                                logging,
                                "directory",
                                "/var/log/pegelconnect-pro"
                        )
                );


        int logRetentionDays =
                intEnv(
                        "LOG_RETENTION_DAYS",
                        intValue(
                                logging,
                                "retentionDays",
                                90
                        ),
                        1
                );


        List<StationConfig> stations =
                loadStations(json);


        return new AppConfig(
                applicationName,
                mqttBrokerUri,
                mqttClientId,
                mqttTimeoutSeconds,
                stations,
                fetchIntervalSeconds,
                httpPort,
                pegelOnlineServer,
                pegelOnlineApiPath,
                pegelOnlineTimeoutSeconds,
                historyMaxDays,
                weatherEnabled,
                weatherServer,
                weatherTimeoutSeconds,
                weatherTimezone,
                logDirectory,
                logRetentionDays
        );
    }


    // =========================================================
    // CONFIG PATH
    // =========================================================

    private static Path resolveConfigPath() {

        String explicit =
                System.getenv(
                        "PEGELCONNECT_CONFIG"
                );

        if (
                explicit != null
                        && !explicit.isBlank()
        ) {
            return Path.of(
                    explicit.trim()
            );
        }


        Path systemPath =
                Path.of(
                        DEFAULT_CONFIG_PATH
                );

        if (
                Files.exists(
                        systemPath
                )
        ) {
            return systemPath;
        }


        Path localPath =
                Path.of(
                        LOCAL_CONFIG_PATH
                );

        if (
                Files.exists(
                        localPath
                )
        ) {
            return localPath;
        }


        return null;
    }


    // =========================================================
    // JSON LADEN
    // =========================================================

    private static JSONObject readJson(
            Path path
    ) {

        if (path == null) {

            System.out.println(
                    "Keine config.json gefunden. "
                            + "Standardwerte / ENV werden verwendet."
            );

            return new JSONObject();
        }


        try {

            String content =
                    Files.readString(
                            path,
                            StandardCharsets.UTF_8
                    );

            System.out.println(
                    "Konfiguration geladen: "
                            + path.toAbsolutePath()
            );

            return new JSONObject(
                    content
            );

        } catch (IOException ex) {

            throw new IllegalStateException(
                    "Konfigurationsdatei konnte nicht gelesen werden: "
                            + path,
                    ex
            );

        } catch (Exception ex) {

            throw new IllegalStateException(
                    "Ungültige JSON-Konfiguration: "
                            + path,
                    ex
            );
        }
    }


    // =========================================================
    // STATIONEN
    // =========================================================

    private static List<StationConfig> loadStations(
            JSONObject root
    ) {

        JSONArray array =
                root.optJSONArray(
                        "stations"
                );

        List<StationConfig> result =
                new ArrayList<>();


        if (array != null) {

            for (
                    int i = 0;
                    i < array.length();
                    i++
            ) {

                JSONObject item =
                        array.optJSONObject(
                                i
                        );

                if (item == null) {
                    continue;
                }


                String name =
                        item
                                .optString(
                                        "name",
                                        ""
                                )
                                .trim()
                                .toUpperCase();


                if (name.isBlank()) {
                    continue;
                }


                String apiName =
                        item
                                .optString(
                                        "apiName",
                                        name
                                )
                                .trim()
                                .toUpperCase();


                String uuid =
                        item
                                .optString(
                                        "uuid",
                                        ""
                                )
                                .trim();


                double latitude =
                        item.optDouble(
                                "latitude",
                                Double.NaN
                        );


                double longitude =
                        item.optDouble(
                                "longitude",
                                Double.NaN
                        );


                result.add(
                        new StationConfig(
                                name,
                                apiName,
                                uuid,
                                latitude,
                                longitude
                        )
                );
            }
        }


        /*
         * Fallback, falls keine Stationen
         * in config.json definiert wurden.
         */

        if (result.isEmpty()) {

            result.add(
                    new StationConfig(
                            "KÖLN",
                            "KÖLN",
                            "",
                            50.9375,
                            6.9603
                    )
            );

            result.add(
                    new StationConfig(
                            "BONN",
                            "BONN",
                            "",
                            50.7374,
                            7.0982
                    )
            );

            result.add(
                    new StationConfig(
                            "MAINZ",
                            "MAINZ",
                            "",
                            49.9929,
                            8.2473
                    )
            );
        }


        /*
         * Optional ENV Override:
         *
         * PEGEL_STATIONS=KÖLN,MAINZ
         *
         * filtert nur die bereits aus
         * config.json geladenen Stationen.
         */

        String stationOverride =
                System.getenv(
                        "PEGEL_STATIONS"
                );

        if (
                stationOverride != null
                        && !stationOverride.isBlank()
        ) {

            List<String> allowed =
                    java.util.Arrays
                            .stream(
                                    stationOverride.split(",")
                            )
                            .map(String::trim)
                            .map(String::toUpperCase)
                            .filter(
                                    s ->
                                            !s.isBlank()
                            )
                            .toList();


            result =
                    result
                            .stream()
                            .filter(
                                    station ->
                                            allowed.contains(
                                                    station.name()
                                            )
                            )
                            .toList();
        }


        return List.copyOf(
                result
        );
    }


    public List<String> stationNames() {

        return stations
                .stream()
                .map(
                        StationConfig::name
                )
                .toList();
    }


    public StationConfig station(
            String name
    ) {

        if (name == null) {
            return null;
        }

        String normalized =
                name
                        .trim()
                        .toUpperCase();

        return stations
                .stream()
                .filter(
                        station ->
                                station
                                        .name()
                                        .equals(
                                                normalized
                                        )
                )
                .findFirst()
                .orElse(null);
    }


    // =========================================================
    // JSON VALUE HELPERS
    // =========================================================

    private static String stringValue(
            JSONObject object,
            String key,
            String fallback
    ) {

        if (object == null) {
            return fallback;
        }

        String value =
                object.optString(
                        key,
                        fallback
                );

        return value == null
                || value.isBlank()
                ? fallback
                : value.trim();
    }


    private static int intValue(
            JSONObject object,
            String key,
            int fallback
    ) {

        if (object == null) {
            return fallback;
        }

        return object.optInt(
                key,
                fallback
        );
    }


    private static long longValue(
            JSONObject object,
            String key,
            long fallback
    ) {

        if (object == null) {
            return fallback;
        }

        return object.optLong(
                key,
                fallback
        );
    }


    private static boolean booleanValue(
            JSONObject object,
            String key,
            boolean fallback
    ) {

        if (object == null) {
            return fallback;
        }

        return object.optBoolean(
                key,
                fallback
        );
    }


    // =========================================================
    // ENV OVERRIDES
    // =========================================================

    private static String env(
            String name,
            String fallback
    ) {

        String value =
                System.getenv(
                        name
                );

        return value == null
                || value.isBlank()
                ? fallback
                : value.trim();
    }


    private static int intEnv(
            String name,
            int fallback,
            int minimum
    ) {

        try {

            return Math.max(
                    minimum,
                    Integer.parseInt(
                            env(
                                    name,
                                    Integer.toString(
                                            fallback
                                    )
                            )
                    )
            );

        } catch (
                NumberFormatException ex
        ) {

            return fallback;
        }
    }


    private static long longEnv(
            String name,
            long fallback,
            long minimum
    ) {

        try {

            return Math.max(
                    minimum,
                    Long.parseLong(
                            env(
                                    name,
                                    Long.toString(
                                            fallback
                                    )
                            )
                    )
            );

        } catch (
                NumberFormatException ex
        ) {

            return fallback;
        }
    }


    private static boolean booleanEnv(
            String name,
            boolean fallback
    ) {

        String value =
                System.getenv(
                        name
                );

        if (
                value == null
                        || value.isBlank()
        ) {
            return fallback;
        }

        return Boolean.parseBoolean(
                value.trim()
        );
    }


    // =========================================================
    // STATION CONFIG
    // =========================================================

    public record StationConfig(
            String name,
            String apiName,
            String uuid,
            double latitude,
            double longitude
    ) {}
}