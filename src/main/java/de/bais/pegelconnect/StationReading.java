package de.bais.pegelconnect;

import org.json.JSONObject;

public record StationReading(
        String station,
        String timestamp,
        double value,
        String unit,
        Integer trend
) {
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("station", station);
        json.put("timestamp", timestamp);
        json.put("value", value);
        json.put("unit", unit);
        json.put("trend", trend == null ? JSONObject.NULL : trend);
        return json;
    }
}
