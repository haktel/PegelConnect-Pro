# 13 - Betrieb und Abnahme

## Start

Im Projektverzeichnis:

```powershell
vagrant up
```

Bei Änderungen:

```powershell
vagrant provision
```

## Dashboard

```text
http://127.0.0.1:8088
```

## Kernprüfung

```powershell
vagrant ssh -c "systemctl is-active pegelconnect-pro mosquitto nginx"
curl.exe -i "http://127.0.0.1:8088/api/state"
```

## Abnahmekriterien

- VM startet
- Mosquitto aktiv
- Java Backend aktiv
- NGINX aktiv
- Dashboard erreichbar
- KÖLN, BONN und MAINZ vorhanden
- MQTT verbunden
- Weather API liefert Daten
- History 24h/7d/30d liefert Daten
- Fehlerhafte Eingaben werden mit HTTP 400 behandelt
- Java-Prozess-Recovery funktioniert
- Broker-Recovery funktioniert
- Reverse Proxy reagiert bei Backend-Ausfall nachvollziehbar

## Quellen im Dashboard

- PEGELONLINE
- Open-Meteo
- OpenStreetMap
- Wikimedia Commons
