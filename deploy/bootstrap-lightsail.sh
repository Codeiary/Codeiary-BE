#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -eq 0 ]]; then
  echo "Run this script as the normal Lightsail user, not root." >&2
  exit 1
fi

sudo apt-get update
sudo apt-get install -y ca-certificates curl docker.io docker-compose-v2 nginx unzip
temporary_directory=$(mktemp -d)
trap 'rm -rf "$temporary_directory"' EXIT
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o "$temporary_directory/awscliv2.zip"
unzip -q "$temporary_directory/awscliv2.zip" -d "$temporary_directory"
sudo "$temporary_directory/aws/install" --update
sudo systemctl enable --now docker nginx
sudo usermod -aG docker "$USER"
sudo install -d -m 755 /var/www/codeiary /etc/nginx/sites-available /etc/nginx/sites-enabled
sudo install -d -m 700 /etc/ssl/codeiary
sudo install -d -m 755 /opt/codeiary
sudo install -m 644 "$(dirname "$0")/../compose.yaml" /opt/codeiary/compose.yaml
sudo install -m 755 "$(dirname "$0")/deploy-api.sh" /usr/local/sbin/codeiary-deploy-api

echo "Bootstrap complete. Configure the Parameter Store values before deploying."
echo "Sign out and back in before running Docker without sudo."
