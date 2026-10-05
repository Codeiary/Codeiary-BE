#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run this script as root." >&2
  exit 1
fi

image=${1:?Usage: codeiary-deploy-api IMAGE}
region=ap-northeast-2
compose_file=/opt/codeiary/compose.yaml
temporary_env=$(mktemp /run/codeiary-compose.XXXXXX)
trap 'rm -f "$temporary_env"' EXIT
chmod 600 "$temporary_env"

get_parameter() {
  aws ssm get-parameter \
    --region "$region" \
    --name "/codeiary/prod/$1" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text
}

export IMAGE="$image"
POSTGRES_DB="$(get_parameter postgres-db)"
POSTGRES_USER="$(get_parameter postgres-user)"
POSTGRES_PASSWORD="$(get_parameter postgres-password)"
JWT_SECRET="$(get_parameter jwt-secret)"
export POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD JWT_SECRET

python3 - "$temporary_env" <<'PY'
import os
import sys

def quote(value):
    if "\n" in value or "\r" in value:
        raise SystemExit("Compose environment values must not contain newlines")
    return "'" + value.replace("'", "\\'") + "'"

with open(sys.argv[1], "w", encoding="utf-8") as env_file:
    for key in ("IMAGE", "POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD",
                "JWT_SECRET"):
        env_file.write(f"{key}={quote(os.environ[key])}\n")
PY

docker compose --env-file "$temporary_env" -f "$compose_file" pull api
docker compose --env-file "$temporary_env" -f "$compose_file" up -d --remove-orphans
rm -f /opt/codeiary/.env
docker image prune -f
