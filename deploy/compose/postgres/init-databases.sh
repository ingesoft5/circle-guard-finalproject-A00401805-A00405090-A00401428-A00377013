#!/bin/bash
# CG-DOC: Creates the extra CircleGuard databases in the shared PostgreSQL instance.
set -euo pipefail

for db in $(echo "${POSTGRES_MULTIPLE_DATABASES:-}" | tr ',' ' '); do
  [ -n "${db}" ] || continue
  if ! psql --username "${POSTGRES_USER}" --dbname postgres -tAc \
      "SELECT 1 FROM pg_database WHERE datname='${db}'" | grep -q 1; then
    psql --username "${POSTGRES_USER}" --dbname postgres -c "CREATE DATABASE ${db}"
  fi
done
