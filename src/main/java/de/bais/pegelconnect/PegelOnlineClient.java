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

public final class PegelOnlineClient {
    private static final String BASE =
            "https://www.pegelonline.wsv.de/webservices/rest-api/v2/stations/";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public RawMeasurement fetch(String station) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(station, StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create(BASE + encoded + "/W/currentmeasurement.json");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "PegelConnect-Pro/1.0")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("PEGELONLINE HTTP " + response.statusCode() + " für " + station);
        }

        JSONObject json = new JSONObject(response.body());
        if (!json.has("timestamp") || !json.has("value") || json.isNull("value")) {
            throw new IOException("Unvollständige PEGELONLINE-Antwort für " + station);
        }

        return new RawMeasurement(json.getString("timestamp"), json.getDouble("value"));
    }

    public record RawMeasurement(String timestamp, double value) {}
}
