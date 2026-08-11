package de.bais.pegelconnect;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReadingStore {
    private static final int HISTORY_LIMIT = 72;

    private final Map<String, StationReading> latest = new LinkedHashMap<>();
    private final Map<String, Deque<StationReading>> history = new LinkedHashMap<>();
    private final Map<String, Double> previousValues = new LinkedHashMap<>();
    private volatile String lastUpdate = null;
    private volatile String lastError = null;

    public synchronized Integer calculateTrend(String station, double currentValue) {
        Double previous = previousValues.get(station);
        if (previous == null) return null;
        return Double.compare(currentValue, previous);
    }

    public synchronized void accept(StationReading reading) {
        latest.put(reading.station(), reading);
        previousValues.put(reading.station(), reading.value());
        history.computeIfAbsent(reading.station(), key -> new ArrayDeque<>()).addLast(reading);
        while (history.get(reading.station()).size() > HISTORY_LIMIT) {
            history.get(reading.station()).removeFirst();
        }
        lastUpdate = reading.timestamp();
        lastError = null;
    }

    public void setError(String error) {
        lastError = error;
    }

    public synchronized JSONObject snapshot(boolean mqttConnected) {
        JSONObject root = new JSONObject();
        JSONObject stations = new JSONObject();
        latest.forEach((key, value) -> stations.put(key, value.toJson()));

        JSONObject histories = new JSONObject();
        history.forEach((key, values) -> {
            JSONArray arr = new JSONArray();
            values.forEach(value -> arr.put(value.toJson()));
            histories.put(key, arr);
        });

        root.put("online", true);
        root.put("mqttConnected", mqttConnected);
        root.put("lastUpdate", lastUpdate == null ? JSONObject.NULL : lastUpdate);
        root.put("lastError", lastError == null ? JSONObject.NULL : lastError);
        root.put("stations", stations);
        root.put("history", histories);
        return root;
    }
}
