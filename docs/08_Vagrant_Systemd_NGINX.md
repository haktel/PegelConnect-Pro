# 08 - Vagrant, systemd und NGINX

## Vagrant

`Vagrantfile` definiert VM, Ressourcen, Portweiterleitungen und Datei-Provisioner. Die VM kann mit einem einzigen Provisioning-Lauf reproduzierbar aufgebaut werden.

```powershell
vagrant up
vagrant provision
```

## systemd

Der Java-Dienst startet die ausführbare JAR und nutzt eine Environment-Datei mit dem Pfad zur externen Konfiguration.

Wichtige Eigenschaften:

- `Restart=on-failure`
- `RestartSec=5`
- `User=vagrant`
- `NoNewPrivileges=true`
- `PrivateTmp=true`
- `ProtectHome=true`

## Abhängigkeit zu Mosquitto

Korrekte Betriebslogik:

```ini
After=network-online.target mosquitto.service
Wants=network-online.target mosquitto.service
```

Kein hartes `Requires=mosquitto.service`, damit das Backend bei Broker-Ausfall weiterläuft.

## NGINX

NGINX lauscht auf Gast-Port 80 und leitet Requests an `127.0.0.1:8080` weiter.

Getestetes Verhalten:

- Backend aktiv -> HTTP 200
- Backend gestoppt -> HTTP 502 Bad Gateway
- Backend neu gestartet -> wieder HTTP 200
