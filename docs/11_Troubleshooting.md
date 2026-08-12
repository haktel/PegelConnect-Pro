# 11 - Troubleshooting

## NGINX liefert 502

Prüfen:

```powershell
vagrant ssh -c "systemctl is-active pegelconnect-pro"
```

Bei gestopptem Backend ist 502 erwartbar.

## Mosquitto startet nicht

Bekannter Fehler im Test: zwei Konfigurationsdateien definierten beide `listener 1883`.

Prüfen:

```bash
grep -RniE '^(listener|port|persistence_location)' /etc/mosquitto
```

Es darf nicht mehrfach derselbe Listener konfiguriert sein.

## Java Backend startet nicht

```bash
systemctl status pegelconnect-pro
journalctl -u pegelconnect-pro -n 100 --no-pager
```

## API erreichbar?

```powershell
curl.exe -i "http://127.0.0.1:8088/api/state"
```

## MQTT prüfen

```bash
mosquitto_sub -h localhost -p 1883 -t 'pegel/#' -v
```

## Konfiguration prüfen

```powershell
curl.exe -s "http://127.0.0.1:8088/api/config"
```
