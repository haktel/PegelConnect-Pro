# 05 - Konfiguration

## Datei

Produktiv in der VM:

```text
/etc/pegelconnect-pro/config.json
```

Lokale Projektdatei:

```text
config/config.json
```

## Laufzeitparameter

- Anwendungsname
- HTTP-Port
- Abrufintervall
- PEGELONLINE Server und API-Pfad
- MQTT Broker und Client-ID
- Weather API
- Stationsliste
- UUIDs
- Koordinaten
- Logging-Verzeichnis
- Historienperioden

## Aktuelle Stationen

| Station | Breite | Länge |
|---|---:|---:|
| KÖLN | 50.9375 | 6.9603 |
| BONN | 50.7374 | 7.0982 |
| MAINZ | 49.9929 | 8.2473 |

## Vorteil

Betriebsparameter können geändert werden, ohne Java-Quellcode neu zu schreiben. Dadurch werden Code und Betriebsumgebung klar getrennt.
