#!/usr/bin/env bash
# Baut alle Images ohne Cache neu, startet die Container und räumt danach auf.
# Bricht beim ersten Fehler ab, dann läuft der alte Container weiter.
set -euo pipefail

# Im Ordner des Skripts arbeiten, dort liegen docker-compose.yml und .env
cd "$(dirname "$0")"

docker compose build --no-cache --pull
docker compose up -d --remove-orphans

# Erst nach "up", vorher hängt der alte Container noch an seinem Image
docker image prune -f
docker builder prune -f
