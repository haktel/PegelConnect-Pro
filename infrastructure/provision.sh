#!/usr/bin/env bash
set -euo pipefail

echo "[1/9] Paketquellen aktualisieren"
apt-get update -y

echo "[2/9] Java, Maven, Mosquitto, NGINX und Tools installieren"
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  openjdk-17-jdk-headless \
  maven \
  mosquitto \
  mosquitto-clients \
  nginx \
  curl

echo "[3/9] Mosquitto konfigurieren"
cat >/etc/mosquitto/conf.d/pegelconnect-pro.conf <<'EOF'
listener 1883
allow_anonymous true
persistence true
EOF

systemctl enable mosquitto
systemctl restart mosquitto

echo "[4/9] Anwendung bauen"
cd /tmp/pegelconnect-pro
mvn -q -DskipTests clean package

echo "[5/9] Anwendung installieren"
install -d -m 0755 /opt/pegelconnect-pro
install -m 0644 \
  target/PegelConnect-Pro.jar \
  /opt/pegelconnect-pro/PegelConnect-Pro.jar

cat >/etc/pegelconnect-pro.env <<'EOF'
MQTT_BROKER_URI=tcp://localhost:1883
MQTT_CLIENT_ID=pegelconnect-pro
PEGEL_STATIONS=KÖLN,MAINZ,BONN
FETCH_INTERVAL_SECONDS=60
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

echo "[6/9] Java-Dienst aktivieren"
systemctl daemon-reload
systemctl enable pegelconnect-pro
systemctl restart pegelconnect-pro

echo "[7/9] NGINX Reverse Proxy konfigurieren"
cat >/etc/nginx/sites-available/pegelconnect-pro <<'EOF'
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 10s;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }
}
EOF

rm -f /etc/nginx/sites-enabled/default
ln -sfn \
  /etc/nginx/sites-available/pegelconnect-pro \
  /etc/nginx/sites-enabled/pegelconnect-pro

nginx -t
systemctl enable nginx
systemctl restart nginx

echo "[8/9] Dienste prüfen"
systemctl is-active --quiet mosquitto
systemctl is-active --quiet pegelconnect-pro
systemctl is-active --quiet nginx

echo "[9/9] Funktionstest"

BACKEND_OK=false
NGINX_OK=false

for i in {1..30}; do
  if curl -fsS http://127.0.0.1:8080/api/state >/dev/null; then
    BACKEND_OK=true
    break
  fi
  sleep 2
done

if [ "$BACKEND_OK" != "true" ]; then
  echo "FEHLER: Java Backend ist nicht erreichbar."
  journalctl -u pegelconnect-pro -n 80 --no-pager || true
  exit 1
fi

for i in {1..15}; do
  if curl -fsS http://127.0.0.1/ >/dev/null; then
    NGINX_OK=true
    break
  fi
  sleep 1
done

if [ "$NGINX_OK" != "true" ]; then
  echo "FEHLER: NGINX Webserver ist nicht erreichbar."
  journalctl -u nginx -n 80 --no-pager || true
  exit 1
fi

echo
echo "=========================================="
echo " PegelConnect Pro erfolgreich gestartet"
echo "=========================================="
echo " Mosquitto:    ACTIVE"
echo " Java Backend: ACTIVE"
echo " NGINX:        ACTIVE"
echo " Backend:      http://127.0.0.1:8080"
echo " Webserver:    http://127.0.0.1"
echo "=========================================="