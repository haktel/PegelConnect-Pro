# Testplan

## Infrastruktur

```powershell
vagrant up --provider=virtualbox
vagrant ssh -c "systemctl is-active mosquitto"
vagrant ssh -c "systemctl is-active pegelconnect-pro"
```

Erwartet jeweils: `active`.

## HTTP

```powershell
curl.exe http://127.0.0.1:8080/api/state
```

Erwartet: JSON mit `stations`, `history`, `mqttConnected`.

## MQTT

```powershell
vagrant ssh -c "mosquitto_sub -h localhost -t 'pegel/#' -v -C 4"
```

Erwartet: Status und Pegeldaten.

## Autostart

```powershell
vagrant reload
vagrant ssh -c "systemctl is-active mosquitto; systemctl is-active pegelconnect-pro"
```

Erwartet: beide `active`.

## Fehlerfall

Broker stoppen:

```powershell
vagrant ssh -c "sudo systemctl stop mosquitto"
```

Backend soll nicht unkontrolliert abstürzen; nach Broker-Neustart muss die Verbindung wiederhergestellt werden.
