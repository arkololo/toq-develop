#!/bin/sh
set -eu
task_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$task_root"
sh ./mvnw --batch-mode --no-transfer-progress verify
(
  cd frontend
  npm ci
  npm run lint
  npm run test
  npm run build
)
docker compose config --quiet
printf '%s\n' 'Vérifications locales réussies.'

