#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " PegelConnect Pro - Provisioning"
echo "=========================================="

APP_DIR="/opt/pegelconnect-pro"
BUILD_DIR="/tmp/pegelconnect-pro"
CONFIG_DIR="/etc/pegelconnect-pro"
LOG_DIR="/var/log/pegelconnect-pro"
SERVICE_FILE="/etc/systemd/system/pegelconnect-pro.service"
ENV_FILE="/etc/pegelconnect-pro.env"

echo "[1/10] Pakete installieren"

export DEBIAN_FRONTEND=noninteractive

apt-get update -y

apt-get install -y \
    openjdk-17-jdk \
    maven \
    mosquitto \
    mosquitto-clients \
    nginx \
    curl

echo "[2/10] Mosquitto konfigurieren"

cat > /etc/mosquitto/conf.d/pegelconnect.conf <<'EOF'
listener 1883
allow_anonymous true
persistence true
EOF

systemctl enable mosquitto
systemctl restart mosquitto

echo "[3/10] Verzeichnisse vorbereiten"

mkdir -p "$APP_DIR"
mkdir -p "$CONFIG_DIR"
mkdir -p "$LOG_DIR"

chown -R vagrant:vagrant "$APP_DIR"
chown -R vagrant:vagrant "$LOG_DIR"

chmod 755 "$APP_DIR"
chmod 755 "$LOG_DIR"

echo "[4/10] Externe Konfiguration installieren"

if [ ! -f "$BUILD_DIR/config.json" ]; then
    echo "FEHLER: $BUILD_DIR/config.json wurde nicht gefunden."
    exit 1
fi

install \
    -m 0644 \
    "$BUILD_DIR/config.json" \
    "$CONFIG_DIR/config.json"

echo "[5/10] Java-Projekt bauen"

cd "$BUILD_DIR"

rm -rf target

mvn -q -DskipTests clean package

JAR_FILE="$(find "$BUILD_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*' | head -n 1)"

if [ -z "$JAR_FILE" ]; then
    echo "FEHLER: Keine ausführbare JAR-Datei gefunden."
    exit 1
fi

install \
    -m 0755 \
    "$JAR_FILE" \
    "$APP_DIR/PegelConnect-Pro.jar"

echo "[6/10] Environment konfigurieren"

cat > "$ENV_FILE" <<'EOF'
PEGELCONNECT_CONFIG=/etc/pegelconnect-pro/config.json
EOF

chmod 0644 "$ENV_FILE"

echo "[7/10] systemd-Service konfigurieren"

cat > "$SERVICE_FILE" <<'EOF'
[Unit]
Description=PegelConnect Pro Java Backend
After=network-online.target mosquitto.service
Wants=network-online.target mosquitto.service

[Service]
Type=simple
EnvironmentFile=/etc/pegelconnect-pro.env
WorkingDirectory=/opt/pegelconnect-pro
ExecStart=/usr/bin/java -jar /opt/pegelconnect-pro/PegelConnect-Pro.jar

Restart=on-failure
RestartSec=5

User=vagrant
Group=vagrant

NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable pegelconnect-pro
systemctl restart pegelconnect-pro

echo "[8/10] NGINX konfigurieren"

cat > /etc/nginx/sites-available/pegelconnect-pro <<'EOF'
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
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
EOF

rm -f /etc/nginx/sites-enabled/default

ln -sf \
    /etc/nginx/sites-available/pegelconnect-pro \
    /etc/nginx/sites-enabled/pegelconnect-pro

nginx -t

systemctl enable nginx
systemctl restart nginx

echo "[9/10] Dienste prüfen"

if systemctl is-active --quiet mosquitto; then
    echo "Mosquitto: ACTIVE"
else
    echo "Mosquitto: FAILED"
    systemctl status mosquitto --no-pager || true
fi

if systemctl is-active --quiet pegelconnect-pro; then
    echo "Java Backend: ACTIVE"
else
    echo "Java Backend: FAILED"
    journalctl -u pegelconnect-pro -n 50 --no-pager || true
fi

if systemctl is-active --quiet nginx; then
    echo "NGINX: ACTIVE"
else
    echo "NGINX: FAILED"
    systemctl status nginx --no-pager || true
fi

echo "[10/10] Funktionstest"

BACKEND_OK=false

for i in $(seq 1 20); do

    if curl -fsS \
        http://127.0.0.1:8080/api/state \
        >/dev/null 2>&1; then

        BACKEND_OK=true
        break
    fi

    sleep 2
done

if [ "$BACKEND_OK" = true ]; then

    echo "Backend API: OK"

else

    echo "Backend API: FEHLER"

    journalctl \
        -u pegelconnect-pro \
        -n 50 \
        --no-pager || true
fi


if curl -fsS \
    http://127.0.0.1:8080/api/config \
    >/dev/null 2>&1; then

    echo "Config API: OK"

else

    echo "Config API: FEHLER"
fi


if curl -fsS \
    http://127.0.0.1/ \
    >/dev/null 2>&1; then

    echo "NGINX Webserver: OK"

else

    echo "NGINX Webserver: FEHLER"
fi


echo
echo "=========================================="
echo " PegelConnect Pro erfolgreich gestartet"
echo "=========================================="
echo
echo " Mosquitto:       $(systemctl is-active mosquitto)"
echo " Java Backend:    $(systemctl is-active pegelconnect-pro)"
echo " NGINX:           $(systemctl is-active nginx)"
echo " External Config: ACTIVE"
echo
echo " Config:"
echo " /etc/pegelconnect-pro/config.json"
echo
echo " Backend:"
echo " http://127.0.0.1:8080"
echo
echo " Config API:"
echo " http://127.0.0.1:8080/api/config"
echo
echo " Webserver:"
echo " http://127.0.0.1"
echo
echo "=========================================="