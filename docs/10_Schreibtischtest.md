# 10 - Schreibtischtest

Der Schreibtischtest prüft den logischen Ablauf ohne einen neuen technischen Testlauf. Der Programmfluss wird Schritt für Schritt nachvollzogen.

## ST01 - Programmstart

| Schritt | Ablauf | Erwartung |
|---:|---|---|
| 1 | `AppConfig.load()` | Konfiguration wird geladen |
| 2 | `ReadingStore` wird erzeugt | Leerer Zustand vorhanden |
| 3 | `PegelOnlineClient(config)` | API Client besitzt Server, Pfad, Timeout und Stationen |
| 4 | `MqttGateway` verbindet | Status wird online |
| 5 | `WebServer` startet | Port 8080 ist erreichbar |
| 6 | Scheduler startet | Zyklischer Abruf beginnt |
| 7 | Stationen werden iteriert | KÖLN, BONN, MAINZ werden bearbeitet |
| 8 | Messwert wird gelesen | Wert und Timestamp vorhanden |
| 9 | Trend wird berechnet | -1 / 0 / 1 / null |
| 10 | Store wird aktualisiert | REST kann neuen Zustand liefern |
| 11 | MQTT Publish | Topic erhält JSON Payload |

## ST02 - Ungültige Station

1. Request enthält `MUENCHEN`.
2. Konfiguration enthält diese Station nicht.
3. Webserver verwirft den Request.
4. Antwort: HTTP 400.
5. Prozess bleibt aktiv.

## ST03 - Java-Prozess fällt ungeplant aus

1. MQTT-Verbindung besteht und Status ist `online`.
2. Java-Prozess wird per SIGKILL beendet.
3. Broker erkennt Verbindungsverlust.
4. Broker publiziert Last Will `offline`.
5. systemd erkennt Fehler.
6. Java wird nach `RestartSec` neu gestartet.
7. MQTT-Verbindung wird neu aufgebaut.
8. Status wird wieder `online`.

## ST04 - MQTT Broker fällt aus

1. Java läuft.
2. Mosquitto wird gestoppt.
3. Java bleibt aufgrund `Wants=` aktiv.
4. MQTT-Zustand wird `false`.
5. Mosquitto wird gestartet.
6. Reconnect stellt Verbindung wieder her.
7. MQTT-Zustand wird `true`.

## ST05 - Backend fällt aus, NGINX bleibt aktiv

1. NGINX läuft.
2. Java Backend wird gestoppt.
3. NGINX kann Upstream nicht erreichen.
4. Antwort: HTTP 502 Bad Gateway.
5. Java wird gestartet.
6. API liefert wieder HTTP 200.
