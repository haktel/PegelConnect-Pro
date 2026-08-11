#!/usr/bin/env bash
set -euo pipefail

echo "[1/7] Pakete aktualisieren"
apt-get update -y

echo "[2/7] Java, Maven und Mosquitto installieren"
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  openjdk-17-jdk-headless maven mosquitto mosquitto-clients curl

echo "[3/7] Mosquitto konfigurieren"
cat >/etc/mosquitto/conf.d/pegelconnect-pro.conf <<'EOF'
listener 1883
allow_anonymous true
persistence true
persistence_location /var/lib/mosquitto/
EOF
systemctl enable mosquitto
systemctl restart mosquitto

echo "[4/7] Anwendung bauen"
cd /vagrant
mvn -q -DskipTests clean package

echo "[5/7] Anwendung installieren"
install -d -m 0755 /opt/pegelconnect-pro
install -m 0644 target/PegelConnect-Pro.jar /opt/pegelconnect-pro/PegelConnect-Pro.jar

cat >/etc/pegelconnect-pro.env <<'EOF'
MQTT_BROKER_URI=tcp://localhost:1883
MQTT_CLIENT_ID=pegelconnect-pro
PEGEL_STATIONS=KOELN,MAINZ,BONN
FETCH_INTERVAL_SECONDS=3600
HTTP_PORT=8080
EOF

cat >/etc/systemd/system/pegelconnect-pro.service <<'EOF'
[Unit]
Description=PegelConnect Pro Java Backend
After=network-online.target mosquitto.service
Wants=network-online.target
Requires=mosquitto.service

[Service]
Type=simple
EnvironmentFile=/etc/pegelconnect-pro.env
ExecStart=/usr/bin/java -jar /opt/pegelconnect-pro/PegelConnect-Pro.jar
Restart=on-failure
RestartSec=5
User=vagrant
Group=vagrant

[Install]
WantedBy=multi-user.target
EOF

echo "[6/7] Dienst starten"
systemctl daemon-reload
systemctl enable pegelconnect-pro
systemctl restart pegelconnect-pro

echo "[7/7] Funktionstest"
for i in {1..30}; do
  if curl -fsS http://127.0.0.1:8080/api/state >/dev/null; then
    echo "PegelConnect Pro ist erreichbar."
    systemctl --no-pager --full status pegelconnect-pro | head -n 12 || true
    exit 0
  fi
  sleep 2
done

echo "FEHLER: HTTP-Dienst wurde nicht rechtzeitig erreichbar."
journalctl -u pegelconnect-pro -n 80 --no-pager || true
exit 1
