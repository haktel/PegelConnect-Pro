# Prozessorientierter Projektbericht (POB) - PegelConnect Pro

**Autor:** Bünyamin Atik  
**Stand:** August 2026  
**Schwerpunkt:** Fachinformatik Systemintegration + Anwendungsentwicklung

## 1. Projektkontext und Ausgangslage
PegelConnect Pro ist ein eigenständiges Lern- und Referenzprojekt, das FIAE- und FISI-Perspektiven in einem End-to-End-System verbindet. Ziel war nicht nur eine funktionierende Visualisierung, sondern ein reproduzierbares, dokumentiertes und getestetes Gesamtsystem.

## 2. Zieldefinition
- Wasserstände für **KÖLN, BONN und MAINZ**
- PEGELONLINE REST API
- Open-Meteo Wetterdaten
- MQTT/Mosquitto mit Status, Retain und Last Will
- Java-REST-Endpunkte
- Historie 24h / 7d / 30d
- Dashboard, Karte, Verlauf, Reports
- Externe `config.json`
- Ubuntu 24.04, Vagrant, systemd, NGINX
- Testbericht, Schreibtischtest und vollständige Dokumentation

## 3. Vorgehensmodell
Der Prozess wurde iterativ durchgeführt:

1. Analyse
2. Konzeption
3. Implementierung
4. Integration
5. Test
6. Fehleranalyse und Korrektur
7. Retest und Abnahme
8. Dokumentation

Qualitätsprinzip: **Ändern -> Kompilieren -> Provisionieren -> Laufzeittest -> Fehleranalyse -> Retest**.

## 4. Architektur
```text
PEGELONLINE + Open-Meteo + config.json
              |
              v
        Java 17 Backend
        /             \
      MQTT            REST
       |               |
   Mosquitto         NGINX
       |               |
       +-------> Web Dashboard
```

| Komponente | Gast | Host |
|---|---:|---:|
| Java Backend | 8080 | 8080 |
| Mosquitto | 1883 | 1885 |
| NGINX | 80 | 8088 |

## 5. FIAE-Umsetzung
Zentrale Klassen: `PegelConnectPro`, `AppConfig`, `PegelOnlineClient`, `WeatherClient`, `StationReading`, `MqttGateway`, `WebServer`.

Datenfluss: Konfiguration laden -> API-Abruf -> Datenmodell -> Trendberechnung -> Store -> MQTT -> REST/Dashboard.

## 6. FISI-Umsetzung
- Ubuntu 24.04
- Vagrant + VirtualBox
- Java 17 + Maven
- Mosquitto
- NGINX Reverse Proxy
- systemd Autostart und Recovery
- explizites Provisioning ohne Shared Folder

## 7. Reale Fehleranalyse
### Doppelter Mosquitto Listener
Zwei Konfigurationsdateien definierten gleichzeitig `listener 1883`. Der Broker startete nicht. Die veraltete Konfiguration wurde entfernt und der Fehler in Provisioning/Troubleshooting berücksichtigt.

### systemd-Abhängigkeit
`Requires=mosquitto.service` führte beim Broker-Stopp zum Ausfall des Java-Dienstes. Die Kopplung wurde auf `Wants=network-online.target mosquitto.service` geändert. Danach blieb das Backend aktiv und reconnectete nach Broker-Neustart.

## 8. Test und Abnahme
14 Testfälle wurden durchgeführt. Nach der dokumentierten T14-Korrektur waren alle Tests erfolgreich.

| ID | Test | Ergebnis |
|---|---|---|
| T01 | Webserver/Backend | PASS |
| T02 | MQTT Topics | PASS |
| T03 | Weather API | PASS |
| T04 | History 24h | PASS mit Hinweis |
| T05 | History 7d | PASS |
| T06 | History 30d | PASS |
| T07 | Runtime Config | PASS |
| T08 | Ungültige Station | PASS |
| T09 | Ungültige Periode | PASS |
| T10 | Autostart | PASS |
| T11 | VM-Reboot | PASS |
| T12 | MQTT Last Will | PASS |
| T13 | NGINX Backend-Ausfall | PASS |
| T14 | Broker-Ausfall/Reconnect | PASS nach Korrektur |

**Abnahmeurteil: BESTANDEN.**

## 9. Soll-Ist-Ergebnis
Alle definierten Kernziele wurden erreicht: Pegel/Wetter, MQTT, REST, Historie, Dashboard, Reports, externe Konfiguration, NGINX, systemd, Vagrant und Dokumentation.

## 10. Reflexion
Der zentrale Lerngewinn besteht in der Verbindung beider Fachrichtungen: Softwarelogik muss zuverlässig betrieben werden, und Infrastruktur muss die fachliche Anwendung auch bei Fehlern sinnvoll unterstützen.

## 11. Ausblick
- History-Deduplication nach `station + timestamp`
- Automatisierte Test-Suite
- optionale persistente Datenhaltung
- zusätzliche Security-Härtung
- optional Node-RED als zusätzlicher Visualisierungs-Client

## 12. Quellen
Repository und interne Projektdokumentation:  
https://github.com/haktel/PegelConnect-Pro

---
**Copyright © 2026 Bünyamin Atik**
