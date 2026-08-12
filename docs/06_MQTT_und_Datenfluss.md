# 06 - MQTT und Datenfluss

## Broker

```text
tcp://localhost:1883
```

## Topics

```text
pegel/status
pegel/koeln/wasserstand
pegel/bonn/wasserstand
pegel/mainz/wasserstand
```

## Beispiel Payload

```json
{
  "unit": "cm",
  "trend": 0,
  "station": "KÖLN",
  "value": 55,
  "timestamp": "2026-08-12T15:00:00+02:00"
}
```

## Status

- `online` nach erfolgreicher MQTT-Verbindung
- Last Will `offline` bei ungeplantem Prozessabbruch
- nach automatischem Neustart wieder `online`

## Retain und QoS

Die Wasserstandsdaten und der Status werden so veröffentlicht, dass ein neu verbundener Subscriber den letzten Zustand sofort erhalten kann.

## Getestetes Verhalten

Der Test T12 hat die Sequenz `online -> offline -> online` bei einem SIGKILL des Java-Prozesses bestätigt. T14 hat bestätigt, dass ein Broker-Ausfall das Java-Backend nach der Korrektur nicht mehr beendet und die Verbindung nach Broker-Rückkehr erneut aufgebaut wird.
