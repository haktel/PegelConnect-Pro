package de.bais.pegelconnect;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class WeatherClient {

    private static final Map<String, Coordinates> LOCATIONS = Map.of(
            "KÖLN", new Coordinates(50.9375, 6.9603),
            "MAINZ", new Coordinates(49.9929, 8.2473),
            "BONN", new Coordinates(50.7374, 7.0982)
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public JSONObject fetch(String station)
            throws IOException, InterruptedException {

        Coordinates coordinates = LOCATIONS.get(station);

        if (coordinates == null) {
            throw new IllegalArgumentException(
                    "Keine Wetter-Koordinaten für " + station
            );
        }

        String url =
                "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + coordinates.latitude()
                + "&longitude=" + coordinates.longitude()
                + "&current="
                + "temperature_2m,"
                + "apparent_temperature,"
                + "precipitation,"
                + "weather_code,"
                + "wind_speed_10m,"
                + "wind_direction_10m"
                + "&timezone=Europe%2FBerlin";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Weather API HTTP "
                    + response.statusCode()
                    + " für "
                    + station
            );
        }

        JSONObject root = new JSONObject(response.body());
        JSONObject current = root.getJSONObject("current");

        JSONObject result = new JSONObject();

        result.put("station", station);
        result.put("temperature",
                current.optDouble("temperature_2m", Double.NaN));

        result.put("apparentTemperature",
                current.optDouble(
                        "apparent_temperature",
                        Double.NaN
                ));

        result.put("precipitation",
                current.optDouble("precipitation", 0));

        result.put("windSpeed",
                current.optDouble("wind_speed_10m", 0));

        result.put("windDirection",
                current.optDouble("wind_direction_10m", 0));

        result.put("weatherCode",
                current.optInt("weather_code", -1));

        result.put("timestamp",
                current.optString("time", ""));

        return result;
    }

    private record Coordinates(
            double latitude,
            double longitude
    ) {}
}