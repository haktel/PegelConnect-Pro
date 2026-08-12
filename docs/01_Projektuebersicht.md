# 01 - Projektübersicht

## Ziel

PegelConnect Pro ist ein eigenständiges Lern- und Referenzprojekt, das Softwareentwicklung und Systemintegration in einem System verbindet.

## Fachliche Funktionen

- Wasserstände für **KÖLN**, **BONN** und **MAINZ**
- Abruf über PEGELONLINE REST API
- Wetterdaten über Open-Meteo
- MQTT-Veröffentlichung über Mosquitto
- Historische Pegeldaten für 24 Stunden, 7 Tage und 30 Tage
- Dashboard mit Stationskarten, Karte, Verlauf, Status und Meldungen
- CSV-, JSON- und PDF-Report-Export
- Laufzeitkonfiguration über externe `config.json`

## Technische Kernkomponenten

| Bereich | Technologie |
|---|---|
| Backend | Java 17, Maven |
| Messaging | MQTT, Mosquitto |
| Web | Java HTTP Server, NGINX Reverse Proxy |
| VM | Ubuntu 24.04, Vagrant, VirtualBox |
| Service Management | systemd |
| Wasserstandsdaten | PEGELONLINE |
| Wetterdaten | Open-Meteo |
| Frontend | HTML, CSS, JavaScript, Leaflet |

## Abgrenzung

PegelConnect Pro erweitert die ursprüngliche Grundidee bewusst um historische Daten, Wetter, Reports, Karten und ein eigenes Dashboard. Diese Erweiterungen sind Teil des Pro-Projekts und nicht Voraussetzung für die ursprüngliche Team-Grundaufgabe.
