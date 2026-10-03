#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -eq 0 ]]; then
  echo "Run this script as the normal Lightsail user, not root." >&2
  exit 1
fi

sudo apt-get update
sudo apt-get install -y ca-certificates docker.io docker-compose-v2 nginx
sudo systemctl enable --now docker nginx
sudo usermod -aG docker "$USER"
sudo install -d -m 755 /var/www/codeiary /etc/nginx/sites-available /etc/nginx/sites-enabled
sudo install -d -m 700 /etc/ssl/codeiary
sudo install -d -m 755 /opt/codeiary
sudo install -m 644 "$(dirname "$0")/../compose.yaml" /opt/codeiary/compose.yaml

if [[ ! -f /opt/codeiary/.env ]]; then
  read -r -p "PostgreSQL database [codeiary]: " postgres_db
  read -r -p "PostgreSQL user [codeiary]: " postgres_user
  read -r -s -p "PostgreSQL password: " postgres_password
  echo
  if [[ -z "$postgres_password" ]]; then
    echo "PostgreSQL password must not be empty." >&2
    exit 1
  fi
  temporary_env=$(mktemp)
  chmod 600 "$temporary_env"
  cat > "$temporary_env" <<EOF
IMAGE=codeiary-api:latest
POSTGRES_DB=${postgres_db:-codeiary}
POSTGRES_USER=${postgres_user:-codeiary}
POSTGRES_PASSWORD=$postgres_password
EOF
  sudo install -m 600 "$temporary_env" /opt/codeiary/.env
  rm -f "$temporary_env"
fi

echo "Bootstrap complete. Sign out and back in before running Docker without sudo."
