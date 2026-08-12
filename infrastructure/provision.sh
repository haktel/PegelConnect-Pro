#!/usr/bin/env bash

set -euo pipefail


# ==============================================================
# PEGELCONNECT PRO PROVISIONING
# ==============================================================

echo
echo "=========================================="
echo " PegelConnect Pro Provisioning"
echo "=========================================="
echo


# ==============================================================
# 1/10 PAKETQUELLEN
# ==============================================================

echo "[1/10] Paketquellen aktualisieren"

apt-get update -y


# ==============================================================
# 2/10 SOFTWARE
# ==============================================================

echo "[2/10] Java, Maven, Mosquitto, NGINX und Tools installieren"

DEBIAN_FRONTEND=noninteractive apt-get install -y \
  openjdk-17-jdk-headless \
  maven \
  mosquitto \
  mosquitto-clients \
  nginx \
  curl


# ==============================================================
# 3/10 MOSQUITTO
# ==============================================================

echo "[3/10] Mosquitto konfigurieren"

cat >/etc/mosquitto/conf.d/pegelconnect-pro.conf <<'EOF'
listener 1883
allow_anonymous true
persistence true
EOF


systemctl enable mosquitto

systemctl restart mosquitto


# ==============================================================
# 4/10 CONFIGURATION
# ==============================================================

echo "[4/10] External Configuration installieren"


if [ ! -f /tmp/pegelconnect-pro/config.json ]; then

  echo "FEHLER: config.json wurde nicht durch Vagrant übertragen."

  exit 1
fi


install -d \
  -m 0755 \
  /etc/pegelconnect-pro


install \
  -m 0644 \
  /tmp/pegelconnect-pro/config.json \
  /etc/pegelconnect-pro/config.json


echo "Config installiert:"
echo "/etc/pegelconnect-pro/config.json"


# ==============================================================
# LOG DIRECTORY
# ==============================================================

install -d \
  -o vagrant \
  -g vagrant \
  -m 0755 \
  /var/log/pegelconnect-pro


# ==============================================================
# 5/10 BUILD
# ==============================================================

echo "[5/10] Anwendung bauen"

cd /tmp/pegelconnect-pro


rm -rf target


mvn \
  -q \
  -DskipTests \
  clean package


# ==============================================================
# 6/10 APPLICATION INSTALL
# ==============================================================

echo "[6/10] Anwendung installieren"


install -d \
  -m 0755 \
  /opt/pegelconnect-pro


install \
  -m 0644 \
  target/PegelConnect-Pro.jar \
  /opt/pegelconnect-pro/PegelConnect-Pro.jar


# ==============================================================
# ENVIRONMENT
# ==============================================================

cat >/etc/pegelconnect-pro.env <<'EOF'
PEGELCONNECT_CONFIG=/etc/pegelconnect-pro/config.json
EOF


chmod 0644 \
  /etc/pegelconnect-pro.env


# ==============================================================
# SYSTEMD SERVICE
# ==============================================================

cat >/etc/systemd/system/pegelconnect-pro.service <<'EOF'
[Unit]

Description=PegelConnect Pro Java Backend

After=network-online.target mosquitto.service

Wants=network-online.target

Requires=mosquitto.service


[Service]

Type=simple

EnvironmentFile=/etc/pegelconnect-pro.env

WorkingDirectory=/opt/pegelconnect-pro

ExecStart=/usr/bin/java -jar /opt/pegelconnect-pro/PegelConnect-Pro.jar

Restart=on-failure

RestartSec=5

User=vagrant

Group=vagrant


# Basic systemd hardening

NoNewPrivileges=true

PrivateTmp=true

ProtectHome=true


[Install]

WantedBy=multi-user.target
EOF


# ==============================================================
# 7/10 JAVA SERVICE
# ==============================================================

echo "[7/10] Java-Dienst aktivieren"


systemctl daemon-reload

systemctl enable pegelconnect-pro

systemctl restart pegelconnect-pro


# ==============================================================
# 8/10 NGINX
# ==============================================================

echo "[8/10] NGINX Reverse Proxy konfigurieren"


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


rm -f \
  /etc/nginx/sites-enabled/default


ln -sfn \
  /etc/nginx/sites-available/pegelconnect-pro \
  /etc/nginx/sites-enabled/pegelconnect-pro


nginx -t


systemctl enable nginx

systemctl restart nginx


# ==============================================================
# 9/10 SERVICE CHECK
# ==============================================================

echo "[9/10] Dienste prüfen"


systemctl is-active --quiet mosquitto

systemctl is-active --quiet pegelconnect-pro

systemctl is-active --quiet nginx


# ==============================================================
# 10/10 FUNCTION TEST
# ==============================================================

echo "[10/10] Funktionstest"


BACKEND_OK=false

CONFIG_OK=false

NGINX_OK=false


# --------------------------------------------------------------
# Backend
# --------------------------------------------------------------

for i in {1..30}; do

  if curl \
      -fsS \
      http://127.0.0.1:8080/api/state \
      >/dev/null; then

    BACKEND_OK=true

    break

  fi

  sleep 2

done


if [ "$BACKEND_OK" != "true" ]; then

  echo
  echo "FEHLER: Java Backend ist nicht erreichbar."
  echo

  journalctl \
    -u pegelconnect-pro \
    -n 100 \
    --no-pager \
    || true

  exit 1
fi


# --------------------------------------------------------------
# External Config API
# --------------------------------------------------------------

if curl \
    -fsS \
    http://127.0.0.1:8080/api/config \
    >/dev/null; then

  CONFIG_OK=true

fi


if [ "$CONFIG_OK" != "true" ]; then

  echo
  echo "FEHLER: /api/config ist nicht erreichbar."
  echo

  journalctl \
    -u pegelconnect-pro \
    -n 100 \
    --no-pager \
    || true

  exit 1
fi


# --------------------------------------------------------------
# NGINX
# --------------------------------------------------------------

for i in {1..15}; do

  if curl \
      -fsS \
      http://127.0.0.1/ \
      >/dev/null; then

    NGINX_OK=true

    break

  fi

  sleep 1

done


if [ "$NGINX_OK" != "true" ]; then

  echo
  echo "FEHLER: NGINX Webserver ist nicht erreichbar."
  echo

  journalctl \
    -u nginx \
    -n 80 \
    --no-pager \
    || true

  exit 1
fi


# ==============================================================
# RESULT
# ==============================================================

echo
echo "=========================================="
echo " PegelConnect Pro erfolgreich gestartet"
echo "=========================================="
echo
echo " Mosquitto:       ACTIVE"
echo " Java Backend:    ACTIVE"
echo " NGINX:           ACTIVE"
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