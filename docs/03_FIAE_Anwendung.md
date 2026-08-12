# 03 - FIAE: Anwendung und Datenverarbeitung

## Zentrale Klassen

### `PegelConnectPro`

Startpunkt der Anwendung. Lädt Konfiguration, initialisiert Clients, MQTT, Webserver und Scheduler und steuert den zyklischen Abruf.

### `AppConfig`

Lädt externe Konfiguration. Priorität:

1. `PEGELCONNECT_CONFIG`
2. `/etc/pegelconnect-pro/config.json`
3. lokale `config/config.json`
4. interne Defaultwerte

### `PegelOnlineClient`

Verantwortlich für aktuelle und historische Pegelwerte. Die Klasse kapselt die HTTP-Kommunikation mit PEGELONLINE.

### `WeatherClient`

Liest Wetterdaten anhand der Stationskoordinaten aus Open-Meteo.

### `StationReading`

Internes Datenmodell einer Pegelmessung mit Station, Zeitstempel, Wert, Einheit und Trend.

### `MqttGateway`

Kapselt Aufbau, Status, Last Will, Publish und Reconnect der MQTT-Verbindung.

### `WebServer`

Stellt REST-Endpunkte und statische Webressourcen bereit.

## Trendlogik

- aktueller Wert < vorheriger Wert -> `-1`
- aktueller Wert = vorheriger Wert -> `0`
- aktueller Wert > vorheriger Wert -> `1`
- kein vorheriger Wert -> `null`

## Fehlerbehandlung

Ungültige Stationsnamen oder Perioden werden mit HTTP 400 beantwortet. Externe API-Fehler sollen geloggt werden, ohne dass ungültige Daten publiziert werden.

## Lernziele FIAE

- REST-Kommunikation
- JSON-Verarbeitung
- Konfigurationsmanagement
- Datenmodelle
- Fehlerbehandlung
- Zustandsverwaltung
- MQTT-Integration
- HTTP-Endpunkte
