package de.bais.pegelconnect;

import java.util.Arrays;
import java.util.List;

public record AppConfig(
        String mqttBrokerUri,
        String mqttClientId,
        List<String> stations,
        long fetchIntervalSeconds,
        int httpPort
) {
    public static AppConfig fromEnvironment() {
        String broker = env("MQTT_BROKER_URI", "tcp://localhost:1883");
        String clientId = env("MQTT_CLIENT_ID", "pegelconnect-pro");
        List<String> stations = Arrays.stream(env("PEGEL_STATIONS", "KÖLN,MAINZ,BONN").split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .toList();
        long interval = parseLong("FETCH_INTERVAL_SECONDS", 3600L, 10L);
        int port = (int) parseLong("HTTP_PORT", 8080L, 1L);
        return new AppConfig(broker, clientId, stations, interval, port);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static long parseLong(String name, long fallback, long minimum) {
        try {
            return Math.max(minimum, Long.parseLong(env(name, Long.toString(fallback))));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
