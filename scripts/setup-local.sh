#!/bin/sh
set -eu
umask 077

project_root=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
env_path="$project_root/.env"
temp_path=$(mktemp "$project_root/.env.setup.XXXXXX")
trap 'rm -f -- "$temp_path"' EXIT HUP INT TERM

if [ -f "$env_path" ]; then
    cp -- "$env_path" "$temp_path"
else
    cp -- "$project_root/.env.example" "$temp_path"
fi

for key in POSTGRES_PASSWORD TOQ_USER_DB_PASSWORD TOQ_RECIPE_DB_PASSWORD TOQ_NUTRITION_DB_PASSWORD TOQ_PLANNER_DB_PASSWORD TOQ_SOCIAL_DB_PASSWORD KEYCLOAK_DB_PASSWORD KEYCLOAK_ADMIN_PASSWORD; do
    count=$(awk -F= -v key="$key" '$1 == key { count++ } END { print count+0 }' "$temp_path")
    if [ "$count" -gt 1 ]; then
        printf 'Duplicate key %s in .env; resolve it before continuing.\n' "$key" >&2
        exit 1
    fi
    if [ "$count" -eq 1 ] && awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); gsub(/[\r[:space:]]/, ""); if (length($0) > 0) found=1 } END { exit !found }' "$temp_path"; then
        continue
    fi
    value=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
    if [ "${#value}" -ne 64 ]; then
        printf 'Could not obtain enough cryptographic random bytes.\n' >&2
        exit 1
    fi
    # Empty placeholders are discarded, then replaced. Never evaluate .env as shell code.
    contents=$(awk -F= -v key="$key" '$1 != key { sub(/\r$/, ""); print }' "$temp_path")
    printf '%s\n%s=%s\n' "$contents" "$key" "$value" > "$temp_path"
done

chmod 600 "$temp_path"
mv -- "$temp_path" "$env_path"
trap - EXIT HUP INT TERM
printf 'Local .env ready; existing non-empty credentials preserved. No containers started.\n'
