# Architektur

## Komponenten

1. **PEGELONLINE REST API** – Quelle der aktuellen Wasserstände.
2. **Java 17 Backend** – zyklischer Abruf, Validierung, Trendberechnung.
3. **Mosquitto** – MQTT-Broker.
4. **Web-GUI** – Statuskarten, Trendanzeige und Verlauf.
5. **Vagrant/VirtualBox** – reproduzierbare Infrastruktur.

## Datenfluss

```text
PEGELONLINE
   |
   | HTTPS / JSON
   v
PegelOnlineClient
   |
   +--> ReadingStore --> /api/state --> Browser
   |
   +--> MqttGateway --> Mosquitto --> pegel/<station>/wasserstand
```

## MQTT-Vertrag

- QoS: 1
- Retain: true
- Status: `pegel/status`
- Last Will: `offline`
- Online nach erfolgreichem Connect: `online`

## Trend

Der Trend wird pro Station aus dem aktuellen und dem zuletzt erfolgreich verarbeiteten Wert berechnet:

- kleiner: `-1`
- gleich: `0`
- größer: `1`
- erster Wert: `null`
