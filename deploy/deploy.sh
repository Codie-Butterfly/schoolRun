#!/usr/bin/env bash
set -euo pipefail
image=${1:?Supply immutable Docker image}
[[ "$image" =~ ^[a-zA-Z0-9._/-]+:[a-f0-9]{40}$ ]] || { echo 'Expected commit-tagged image'; exit 1; }
cd /opt/school-run
test -f .env || { echo 'Provision /opt/school-run/.env first'; exit 1; }
if docker compose version >/dev/null 2>&1; then
  compose=(docker compose -p school-run -f compose.production.yaml)
else
  compose=(docker-compose -p school-run -f compose.production.yaml)
fi
old=$(cat current-image 2>/dev/null || true)
export SCHOOLRUN_IMAGE="$image"
"${compose[@]}" pull api
stamp=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "backups/$stamp" data
chmod 700 backups
chown 10001:10001 data
# H2 must be stopped before copying its database files.
"${compose[@]}" stop api
cp -a data "backups/$stamp/data"
if [[ -n "$old" ]]; then printf '%s\n' "$old" > "backups/$stamp/image"; fi
# Compose v1 recreation has a known ContainerConfig failure on this VPS.
"${compose[@]}" rm -f api
if "${compose[@]}" up -d --no-deps api; then
  for attempt in {1..30}; do
    if curl -fsS http://127.0.0.1:9200/api/health >/dev/null; then
      printf '%s\n' "$image" > current-image
      echo 'School Run healthy'; exit 0
    fi
    sleep 5
  done
fi
echo 'New School Run image failed health check' >&2
if [[ -n "$old" ]]; then
  export SCHOOLRUN_IMAGE="$old"
  "${compose[@]}" stop api
  "${compose[@]}" rm -f api
  mv data "backups/$stamp/failed-data"
  cp -a "backups/$stamp/data" data
  "${compose[@]}" up -d --no-deps api
  echo 'Previous image and database restored' >&2
fi
exit 1
