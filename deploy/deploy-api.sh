#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" == 0 ]] || { echo 'Run as root' >&2; exit 1; }
commit=${1:?Usage: codeiary-deploy-api FULL_COMMIT_SHA}
[[ "$commit" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid commit SHA' >&2; exit 1; }
REGION=ap-northeast-2
REGISTRY=448049834331.dkr.ecr.ap-northeast-2.amazonaws.com
IMAGE="$REGISTRY/codeiary-api:$commit"
exec 9>/run/codeiary-deploy.lock
flock -n 9 || { echo 'Another deployment is running' >&2; exit 1; }
temporary_env=$(mktemp /run/codeiary-env.XXXXXX)
trap 'rm -f "$temporary_env"' EXIT
chmod 600 "$temporary_env"

# Fetch secrets without placing their values in SSM command output or logs.
python3 - "$temporary_env" <<'PY'
import json,subprocess,sys,urllib.parse
region='ap-northeast-2'
required={'db-url':'DB_URL','db-username':'DB_USERNAME','db-password':'DB_PASSWORD','jwt-secret':'JWT_SECRET','google-client-id':'GOOGLE_CLIENT_ID','google-client-secret':'GOOGLE_CLIENT_SECRET'}
optional={'oauth2-redirect-home':('OAUTH2_REDIRECT_HOME','https://codeiary.com/auth/callback'),'media-s3-bucket':('MEDIA_S3_BUCKET',''),'media-public-base-url':('MEDIA_PUBLIC_BASE_URL','')}
names=['/codeiary/prod/'+name for name in [*required,*optional]]
response=subprocess.run(['aws','ssm','get-parameters','--region',region,'--with-decryption','--names',*names,'--output','json'],capture_output=True,text=True)
if response.returncode:
    raise SystemExit('Unable to read runtime parameters; inspect EC2 role permissions')
data=json.loads(response.stdout)
values={p['Name'].rsplit('/',1)[-1]:p['Value'] for p in data['Parameters']}
missing=[name for name in required if not values.get(name)]
if missing:
    raise SystemExit('Missing required Parameter Store names: '+', '.join(missing))
env={key:values[name] for name,key in required.items()}
env.update({key:values.get(name,default) for name,(key,default) in optional.items()})
env.update(SPRING_PROFILES_ACTIVE='prod',AWS_REGION=region,TOKEN_COOKIE_SECURE='true',TOKEN_COOKIE_DOMAIN='.codeiary.com',TOKEN_COOKIE_SAME_SITE='Lax',JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0')
url=urllib.parse.urlsplit(env['DB_URL'].removeprefix('jdbc:'))
if url.scheme!='postgresql' or url.hostname!='rested-postgres.cfoukaeewtg4.ap-northeast-2.rds.amazonaws.com' or url.port!=5432 or url.path!='/codeiary':
    raise SystemExit('DB_URL must point to the expected rested-postgres/codeiary database')
if urllib.parse.parse_qs(url.query).get('sslmode',[''])[0] not in ['require','verify-ca','verify-full']:
    raise SystemExit('DB_URL must explicitly enable PostgreSQL SSL')
for key,value in env.items():
    if '\n' in value or '\r' in value or '\x00' in value:
        raise SystemExit('Invalid multiline runtime parameter: '+key)
with open(sys.argv[1],'w') as file:
    file.write(''.join(key+'='+value+'\n' for key,value in env.items()))
PY

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null 2>&1
docker pull "$IMAGE" >/dev/null

# Alloy reads the API container's bounded Docker log files and forwards them
# over the private VPC network to Loki on the monitoring instance.
mkdir -p /opt/codeiary/alloy
config_container=$(docker create "$IMAGE")
docker cp "$config_container:/app/alloy.alloy" /opt/codeiary/alloy/config.alloy >/dev/null
docker rm "$config_container" >/dev/null
chmod 600 /opt/codeiary/alloy/config.alloy
docker pull grafana/alloy:v1.20.1 >/dev/null
docker rm -f codeiary-alloy >/dev/null 2>&1 || true
docker run -d --name codeiary-alloy --restart unless-stopped \
  --memory=128m --cpus=0.25 --cap-drop ALL --security-opt no-new-privileges \
  --log-driver local --log-opt max-size=10m --log-opt max-file=3 \
  --mount type=bind,src=/var/lib/docker/containers,dst=/var/lib/docker/containers,readonly \
  -v /opt/codeiary/alloy/config.alloy:/etc/alloy/config.alloy:ro \
  -v codeiary-alloy-data:/var/lib/alloy/data \
  grafana/alloy:v1.20.1 run --storage.path=/var/lib/alloy/data /etc/alloy/config.alloy >/dev/null

previous_image=$(docker inspect --format '{{.Config.Image}}' codeiary-api 2>/dev/null || true)
start_container() {
  docker run -d --name codeiary-api --restart unless-stopped \
    --memory=768m --cpus=1.5 --cap-drop ALL --security-opt no-new-privileges \
    --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
    --env-file "$temporary_env" -p 8080:8080 -p 9091:9091 "$1" >/dev/null
}
docker rm -f codeiary-api >/dev/null 2>&1 || true
if ! start_container "$IMAGE"; then
  echo 'Unable to start the new container' >&2
  if [[ -n "$previous_image" ]]; then
    start_container "$previous_image"
  fi
  exit 1
fi
for attempt in {1..60}; do
  if curl --silent --fail --max-time 3 http://127.0.0.1:8080/health >/dev/null; then
    echo "Codeiary API deployed: $commit; /health returned 200"
    exit 0
  fi
  sleep 5
done
echo 'Deployment health check failed; application logs were not printed to avoid exposing secrets' >&2
docker rm -f codeiary-api >/dev/null 2>&1 || true
if [[ -n "$previous_image" ]]; then
  start_container "$previous_image"
  echo 'Previous image restarted; database migrations, if any, are not reverted' >&2
fi
exit 1
