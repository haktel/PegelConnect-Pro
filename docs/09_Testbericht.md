# 09 - Testbericht

**Projekt:** PegelConnect Pro  
**Testdatum:** 12.08.2026  
**Testumgebung:** Windows Host, Vagrant, VirtualBox, Ubuntu 24.04, Java 17, Mosquitto, NGINX

## Ziel

Nachweis, dass Kernfunktionen, Fehlerbehandlung, Autostart, Reverse Proxy und Recovery funktionieren.

## Ergebnisübersicht

| ID | Testfall | Ergebnis | Nachweis / Beobachtung |
|---|---|---|---|
| T01 | Webserver und Backend erreichbar | PASS | NGINX liefert HTTP 200; /api/state liefert HTTP 200 und JSON. |
| T02 | MQTT Broker und Topics | PASS | Status sowie KÖLN, BONN und MAINZ werden auf den erwarteten Topics geliefert. |
| T03 | Weather API für drei Stationen | PASS | KÖLN, BONN und MAINZ liefern gültige Wetterdaten. |
| T04 | History API 24h | PASS mit Hinweis | KÖLN liefert 96 Messpunkte. Einzelne auffällige Werte 68/69 cm wurden als Plausibilitäts-Hinweis dokumentiert. |
| T05 | History API 7d | PASS | 672 Messpunkte = 7 x 24 x 4. |
| T06 | History API 30d | PASS | 2880 Messpunkte = 30 x 24 x 4. |
| T07 | Runtime Configuration | PASS | /api/config liefert aktive Laufzeitkonfiguration und alle drei Stationen. |
| T08 | Ungültige Station | PASS | Weather und History antworten für MUENCHEN mit HTTP 400 und strukturierter JSON-Fehlermeldung. |
| T09 | Ungültige History-Periode | PASS | period=99d wird mit HTTP 400 und invalid_period abgewiesen. |
| T10 | Autostart und Dienststatus | PASS | pegelconnect-pro, mosquitto und nginx sind enabled und active. |
| T11 | VM Neustart / Wiederanlauf | PASS | Nach vagrant reload starten alle Dienste; /api/state ist wieder erreichbar und MQTT verbunden. |
| T12 | MQTT Last Will / Prozessabbruch | PASS | SIGKILL erzeugt online -> offline -> online; systemd startet Java automatisch neu. |
| T13 | NGINX bei Backend-Ausfall | PASS | Backend stop -> HTTP 502; nach Start wieder HTTP 200. |
| T14 | MQTT Broker-Ausfall / Reconnect | PASS nach Korrektur | Initiale harte systemd-Abhängigkeit führte zum Fehler. Nach Wechsel von Requires auf Wants bleibt Backend aktiv; mqttConnected false/true funktioniert. |

## Detailbefunde

### T04 - Plausibilitäts-Hinweis

Die 24-Stunden-Historie enthielt überwiegend Werte zwischen 54 und 56 cm, daneben einzelne Werte von 68 bzw. 69 cm. Die History API selbst arbeitete korrekt. Die Daten sollten bei Bedarf direkt gegen die Datenquelle gegengeprüft werden.

### In-Memory-History und Duplikate

Im `/api/state` wurden während des 60-Sekunden-Abrufs mehrfach identische Messungen mit gleichem Zeitstempel gespeichert. Ursache: Die Anwendung fragt häufiger ab als PEGELONLINE einen neuen 15-Minuten-Messwert bereitstellt. Eine spätere Verbesserung ist Deduplication nach `station + timestamp`.

### T14 - Gefundener und behobener Infrastrukturfehler

Erster Test:

```text
Mosquitto stop -> pegelconnect-pro failed -> NGINX 502
```

Ursache war eine harte systemd-Abhängigkeit `Requires=mosquitto.service`.

Korrektur:

```ini
After=network-online.target mosquitto.service
Wants=network-online.target mosquitto.service
```

Nach der Korrektur blieb das Backend bei Broker-Ausfall aktiv und konnte den MQTT-Zustand von `false` nach `true` wiederherstellen.

## Gesamturteil

**BESTANDEN.** Alle 14 Testfälle wurden nach der dokumentierten Korrektur erfolgreich abgeschlossen. Die Anwendung zeigt reproduzierbares Start-, Fehler- und Recovery-Verhalten.
