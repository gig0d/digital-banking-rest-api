#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -e .env ]; then echo '.env already exists. Review it locally.' >&2; exit 1; fi
command -v openssl >/dev/null 2>&1 || { echo 'OpenSSL is required.' >&2; exit 1; }
umask 077
{
  printf '# Local synthetic demo only. Never commit this file.\n'
  printf 'DB_PASSWORD=%s\n' "$(openssl rand -base64 32)"
  printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 32)"
  printf 'SPRING_PROFILES_ACTIVE=demo\nDEMO_ENABLED=true\n'
  printf 'DEMO_ADMIN_EMAIL=admin@example.test\n'
  printf 'DEMO_ADMIN_PASSWORD=%s\n' "$(openssl rand -base64 32)"
  printf 'API_DOCS_ENABLED=true\n'
} > .env
echo '.env created with random local credentials and the demo profile enabled. Keep it private.'
