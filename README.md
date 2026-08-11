# PegelConnect Pro

Eigenständige Demo-/Portfolio-Variante des PegelConnect-Projekts. Das offizielle Team-Repository bleibt davon unberührt.

## Ziel

PegelConnect Pro ruft aktuelle Wasserstände von PEGELONLINE ab, veröffentlicht sie per MQTT und zeigt sie parallel in einer modernen Web-GUI an.

## Architektur

```text
PEGELONLINE REST API
        |
        v
Java 17 Backend
  |           |
  |           +--> HTTP API + Web-GUI :8080
  |
  +--> MQTT 3.1.1 / QoS 1 / Retain
              |
              v
          Mosquitto :1883
```

## Stationen

- KÖLN
- MAINZ
- BONN

MQTT Topics:

- `pegel/koeln/wasserstand`
- `pegel/mainz/wasserstand`
- `pegel/bonn/wasserstand`
- `pegel/status`

## Schnellstart mit Vagrant

Voraussetzungen: Vagrant + VirtualBox.

```powershell
vagrant up --provider=virtualbox
```

Danach:

- Web-GUI: `http://127.0.0.1:8080`
- MQTT vom Windows-Host: `127.0.0.1:1885`

Status prüfen:

```powershell
vagrant ssh -c "systemctl status pegelconnect-pro --no-pager"
vagrant ssh -c "systemctl status mosquitto --no-pager"
```

Logs:

```powershell
vagrant ssh -c "journalctl -u pegelconnect-pro -n 100 --no-pager"
```

## Lokal bauen

Java 17 + Maven erforderlich.

```bash
mvn clean package
java -jar target/PegelConnect-Pro.jar
```

## Konfiguration

| Variable | Standard |
|---|---|
| `MQTT_BROKER_URI` | `tcp://localhost:1883` |
| `MQTT_CLIENT_ID` | `pegelconnect-pro` |
| `PEGEL_STATIONS` | `KOELN,MAINZ,BONN` |
| `FETCH_INTERVAL_SECONDS` | `3600` |
| `HTTP_PORT` | `8080` |

## Projektgrenze

Diese Pro-Version ist eine separate Lern-/Portfolio-Erweiterung mit eigener Web-GUI. Sie verändert weder das FISI- noch das FIAE-Team-Repository.

Datenquelle: © www.pegelonline.wsv.de
