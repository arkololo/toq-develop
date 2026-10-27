#!/bin/sh
set -eu
task_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$task_root"
check_http() {
  actual=$(curl --silent --show-error --output /dev/null --max-time 15 --write-out '%{http_code}' "$1")
  if [ "$actual" != "$2" ]; then
    printf 'Unexpected HTTP %s for %s (expected %s)\n' "$actual" "$1" "$2" >&2
    exit 1
  fi
  printf 'OK HTTP %s %s\n' "$actual" "$1"
}
check_http http://localhost:8080/ 200
check_http http://localhost:8080/healthz 200
check_http http://localhost:8080/actuator/health 404
check_http http://localhost:8080/internal/v1/test 404
check_http http://localhost:8080/api/v1/users/actuator/health 404
check_http http://localhost:8080/api/v1/unknown 404
check_http http://localhost:8080/api/v1/users/me 401
check_http http://localhost:8080/api/v1/assistant/status 401
check_http http://localhost:8080/api/v1/recipes 401
check_http http://localhost:8080/api/v1/nutrition/foods 401
check_http http://localhost:8080/api/v1/plans/2026-09-21 401
check_http http://localhost:8080/api/v1/social/publications 401
check_http http://localhost:8081/realms/master/.well-known/openid-configuration 200
for service in postgres keycloak user-service recipe-service nutrition-service planner-service social-service frontend-edge; do
  container=$(docker compose ps --quiet "$service")
  [ -n "$container" ] || { printf 'Missing container: %s\n' "$service" >&2; exit 1; }
  [ "$(docker inspect --format '{{.State.Health.Status}}' "$container")" = healthy ] || exit 1
  bindings=$(docker inspect --format '{{range $p, $bindings := .NetworkSettings.Ports}}{{range $bindings}}{{println .HostIp}}{{end}}{{end}}' "$container")
  case "$service" in
    frontend-edge|keycloak) [ "$bindings" = '127.0.0.1' ] || exit 1 ;;
    *) [ -z "$bindings" ] || exit 1 ;;
  esac
  printf 'OK health and network isolation: %s\n' "$service"
done
docker compose exec -T postgres psql --username=toq_admin --dbname=postgres --no-psqlrc --set=ON_ERROR_STOP=1 < scripts/check-database-isolation.sql
printf '%s\n' 'Local smoke tests passed.'
