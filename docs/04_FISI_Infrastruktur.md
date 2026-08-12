# 04 - FISI: Infrastruktur und Betrieb

## Virtuelle Maschine

- Ubuntu 24.04
- 2 vCPU
- 2048 MB RAM
- Bereitstellung mit Vagrant und VirtualBox
- Shared Folder bewusst deaktiviert; benötigte Dateien werden explizit provisioniert

## Dienste

| Dienst | Aufgabe |
|---|---|
| `pegelconnect-pro.service` | Java Backend |
| `mosquitto.service` | MQTT Broker |
| `nginx.service` | Reverse Proxy / Webzugriff |

## Autostart

Alle drei Dienste sind über systemd aktiviert und starten nach einem VM-Neustart automatisch.

## Fehlertoleranz

Die Java-Anwendung darf nicht hart an Mosquitto gebunden sein. Während des Tests wurde die systemd-Abhängigkeit von `Requires=mosquitto.service` auf `Wants=mosquitto.service` geändert. Dadurch bleibt das Backend bei einem Broker-Ausfall aktiv und kann später wieder verbinden.

## Netzwerk

NGINX ist der primäre Browser-Einstiegspunkt. Der Host-Port `8088` wird auf Gast-Port `80` weitergeleitet. Das Java Backend läuft intern auf `8080`.

## Lernziele FISI

- Linux-Dienste
- systemd
- Reverse Proxy
- MQTT Broker
- VM-Automatisierung
- Portweiterleitungen
- Troubleshooting
- Service Recovery
