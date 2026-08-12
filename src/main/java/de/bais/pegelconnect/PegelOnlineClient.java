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

    private static final String BASE =
            "https://www.pegelonline.wsv.de/webservices/rest-api/v2/stations/";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public RawMeasurement fetch(String station)
            throws IOException, InterruptedException {

        String encoded = encodeStation(station);

        URI uri = URI.create(
                BASE
                        + encoded
                        + "/W/currentmeasurement.json"
        );

        HttpRequest request = createRequest(uri);

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "PEGELONLINE HTTP "
                            + response.statusCode()
                            + " für "
                            + station
            );
        }

        JSONObject json =
                new JSONObject(response.body());

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
                json.getString("timestamp"),
                json.getDouble("value")
        );
    }

    public List<RawMeasurement> fetchHistory(
            String station,
            String period
    ) throws IOException, InterruptedException {

        String encoded = encodeStation(station);

        String apiPeriod = switch (period) {
            case "24h" -> "P1D";
            case "7d" -> "P7D";
            case "30d" -> "P30D";
            default -> "P1D";
        };

        URI uri = URI.create(
                BASE
                        + encoded
                        + "/W/measurements.json?start="
                        + apiPeriod
        );

        HttpRequest request = createRequest(uri);

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "PEGELONLINE History HTTP "
                            + response.statusCode()
                            + " für "
                            + station
            );
        }

        JSONArray json =
                new JSONArray(response.body());

        List<RawMeasurement> measurements =
                new ArrayList<>();

        for (int i = 0; i < json.length(); i++) {

            JSONObject item =
                    json.getJSONObject(i);

            if (
                    !item.has("timestamp")
                            || !item.has("value")
                            || item.isNull("value")
            ) {
                continue;
            }

            measurements.add(
                    new RawMeasurement(
                            item.getString("timestamp"),
                            item.getDouble("value")
                    )
            );
        }

        return measurements;
    }

    private HttpRequest createRequest(URI uri) {

        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header(
                        "Accept",
                        "application/json"
                )
                .header(
                        "User-Agent",
                        "PegelConnect-Pro/1.0"
                )
                .GET()
                .build();
    }

    private String encodeStation(String station) {

        return URLEncoder
                .encode(
                        station,
                        StandardCharsets.UTF_8
                )
                .replace("+", "%20");
    }

    public record RawMeasurement(
            String timestamp,
            double value
    ) {}
}