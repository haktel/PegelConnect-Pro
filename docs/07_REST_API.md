# 07 - REST API

Basis über NGINX:

```text
http://127.0.0.1:8088
```

## Endpunkte

### `GET /api/state`

Aktueller Systemzustand, Stationswerte, In-Memory-Historie und MQTT-Verbindungsstatus.

### `GET /api/config`

Read-Only-Ausgabe der aktiven Laufzeitkonfiguration.

### `GET /api/weather?station=KÖLN`

Aktuelle Wetterdaten der Station.

### `GET /api/history?station=KÖLN&period=24h`

Unterstützte Perioden:

- `24h`
- `7d`
- `30d`

## Fehlerfälle

Ungültige Station:

```json
{"error":"invalid_request","message":"Unbekannte Station: MUENCHEN"}
```

Ungültige Periode:

```json
{"error":"invalid_period"}
```

Beide Fälle liefern HTTP 400.

## Security Header

Die API lieferte im Test unter anderem:

- `X-Frame-Options: SAMEORIGIN`
- `X-Content-Type-Options: nosniff`
- `Cache-Control: no-store`
