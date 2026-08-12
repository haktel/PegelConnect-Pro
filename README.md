# PegelConnect Pro

**PegelConnect Pro** ist eine eigenständige Lern-, Systemintegrations- und Softwareentwicklungsvariante des PegelConnect-Projekts. Die Pro-Version verbindet typische **FISI-Themen** wie Linux, Vagrant, Mosquitto, systemd und NGINX mit **FIAE-Themen** wie Java 17, REST-APIs, Datenmodelle, Fehlerbehandlung und Konfiguration.

> Das offizielle Team-Repository bleibt von dieser Pro-Version unberührt.

## Projektziel

Die Anwendung ruft reale Wasserstandsdaten von **PEGELONLINE** für **KÖLN, BONN und MAINZ** ab, verarbeitet die Messwerte im Java-Backend, veröffentlicht Live-Daten über MQTT und stellt zusätzlich eine browserbasierte Weboberfläche mit Wetterdaten, historischen Pegelverläufen, Reports und Exportfunktionen bereit.

## Architektur

```text
                    Internet
                       |
          +------------+------------+
          |                         |
          v                         v
   PEGELONLINE REST            Open-Meteo
          |                         |
          +------------+------------+
                       |
                       v
               PegelConnect Pro
                  Java 17
                       |
             +---------+---------+
             |                   |
             v                   v
        REST / HTTP            MQTT 3.1.1
          :8080               QoS 1 / Retain
             |                   |
             |                   v
             |               Mosquitto
             |                  :1883
             |
             v
           NGINX
          Port 80
             |
             v
      Web Dashboard / API
             |
   +---------+----------+----------+
   |                    |          |
   v                    v          v
History 24h/7d/30d    Weather    Reports
                                 CSV/JSON/PDF
```

## Hauptfunktionen

- Reale PEGELONLINE-Wasserstände für KÖLN, BONN und MAINZ
- MQTT-Publishing mit QoS 1 und Retain
- MQTT-Status über `pegel/status`
- Last-Will-Verhalten `online` / `offline`
- Java-17-Backend als systemd-Dienst
- NGINX Reverse Proxy
- Externe Runtime-Konfiguration über `config/config.json`
- Wetterdaten über Open-Meteo
- Historische Pegeldaten für 24 Stunden, 7 Tage und 30 Tage
- Interaktive Stationskarte
- Pegelverlauf und statistische Auswertung
- CSV-, JSON- und PDF-Export
- Fehlerbehandlung für ungültige Stationen und Zeiträume
- Automatisierte VM-Bereitstellung mit Vagrant
- Dokumentierte Funktions-, Fehler-, Restart- und Recovery-Tests

## Stationen

| Station | Koordinaten |
|---|---:|
| KÖLN | 50.9375 N / 6.9603 E |
| BONN | 50.7374 N / 7.0982 E |
| MAINZ | 49.9929 N / 8.2473 E |

### MQTT Topics

```text
pegel/koeln/wasserstand
pegel/bonn/wasserstand
pegel/mainz/wasserstand
pegel/status
```

Beispiel-Payload:

```json
{
  "station": "KÖLN",
  "timestamp": "2026-08-12T15:00:00+02:00",
  "value": 55,
  "unit": "cm",
  "trend": 0
}
```

## Schnellstart

### Voraussetzungen

- Windows 11 oder vergleichbares Host-System
- VirtualBox
- Vagrant

### VM starten

```powershell
vagrant up --provider=virtualbox
```

Bei bereits vorhandener VM:

```powershell
vagrant provision
```

## Zugänge

| Dienst | Adresse vom Windows-Host |
|---|---|
| Dashboard über NGINX | `http://127.0.0.1:8088` |
| Java Backend direkt | `http://127.0.0.1:8080` |
| MQTT Broker | `127.0.0.1:1885` |

### REST-Endpunkte

```text
GET /api/state
GET /api/config
GET /api/weather?station=KÖLN
GET /api/history?station=KÖLN&period=24h
```

Unterstützte History-Zeiträume:

```text
24h
7d
30d
```

## Externe Konfiguration

Die zentrale Projektkonfiguration befindet sich unter:

```text
config/config.json
```

Beim Provisioning wird sie nach:

```text
/etc/pegelconnect-pro/config.json
```

installiert.

Konfigurierbar sind unter anderem:

- HTTP-Port
- Abrufintervall
- PEGELONLINE-Server und API-Pfad
- MQTT-Broker und Client-ID
- Wetter-API
- Stationen, UUIDs und Koordinaten
- Logging- und Reportparameter

## Betrieb und Kontrolle

Dienststatus prüfen:

```powershell
vagrant ssh -c "systemctl is-active pegelconnect-pro mosquitto nginx"
```

Logs des Java-Backends:

```powershell
vagrant ssh -c "journalctl -u pegelconnect-pro -n 100 --no-pager"
```

MQTT-Nachrichten prüfen:

```powershell
vagrant ssh -c "mosquitto_sub -h localhost -p 1883 -t 'pegel/#' -v -C 4"
```

## Getestete Betriebsfälle

- Dashboard und REST-Backend erreichbar
- MQTT-Topics aller drei Stationen
- Weather API für KÖLN, BONN und MAINZ
- History API mit 96 / 672 / 2880 Messpunkten für 24h / 7d / 30d
- Runtime-Konfiguration
- Ungültige Station
- Ungültiger Zeitraum
- systemd-Autostart
- VM-Neustart und Wiederanlauf
- MQTT Last Will
- NGINX-Verhalten bei Backend-Ausfall
- MQTT-Broker-Ausfall und automatische Wiederverbindung

## Dokumentation

Die vollständige technische Dokumentation liegt im Ordner [`docs/`](docs/00_Dokumentationsindex.md).

Direkte Einstiege:

- [Projektübersicht](docs/01_Projektuebersicht.md)
- [Architektur](docs/02_Architektur.md)
- [FIAE – Anwendung](docs/03_FIAE_Anwendung.md)
- [FISI – Infrastruktur](docs/04_FISI_Infrastruktur.md)
- [Konfiguration](docs/05_Konfiguration.md)
- [MQTT und Datenfluss](docs/06_MQTT_und_Datenfluss.md)
- [REST API](docs/07_REST_API.md)
- [Vagrant, systemd und NGINX](docs/08_Vagrant_Systemd_NGINX.md)
- [Testbericht](docs/09_Testbericht.md)
- [Schreibtischtest](docs/10_Schreibtischtest.md)
- [Troubleshooting](docs/11_Troubleshooting.md)
- [Erweiterungen](docs/12_Erweiterungen.md)
- [Betrieb und Abnahme](docs/13_Betrieb_und_Abnahme.md)

PDF-Versionen:

- [Gesamtdokumentation](docs/pdf/PegelConnect-Pro_Gesamtdokumentation.pdf)
- [Testbericht](docs/pdf/PegelConnect-Pro_Testbericht.pdf)

## Lernschwerpunkte

### FISI

Linux-VM, Netzwerkports, Vagrant, Provisioning, Mosquitto, MQTT, systemd, NGINX, Dienstabhängigkeiten, Logs, Fehleranalyse, Restart- und Recovery-Verhalten.

### FIAE

Java 17, REST-Clients, JSON-Verarbeitung, Datenmodelle, Konfigurationsmanagement, Fehlerbehandlung, REST-Endpunkte, historische Datenaufbereitung und Reporting.

## Projektgrenze

PegelConnect Pro ist eine separate Lern- und Portfolio-Erweiterung. Sie verändert weder das FISI- noch das FIAE-Team-Repository.

## Datenquellen

- PEGELONLINE / Wasserstraßen- und Schifffahrtsverwaltung des Bundes
- Open-Meteo
- OpenStreetMap
- Wikimedia Commons

---

**PegelConnect Pro – Erweiterung und technische Dokumentation: Bünyamin Atik**  
Copyright © 2026 Bünyamin Atik
