#!/usr/bin/env bash
# Writes values from /opt/skyblock/.env into plugin configs on the VPS (never committed to git).
# Run after every deploy (the deploy workflow does this automatically).
set -euo pipefail
cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-/opt/skyblock/.env}"
[ -f "$ENV_FILE" ] || { echo "No $ENV_FILE: skipping (local test server uses H2)."; exit 0; }
# shellcheck disable=SC1090
. "$ENV_FILE"

LP=plugins/LuckPerms/config.yml
if [ -f "$LP" ]; then
  python3 - "$LP" "$DB_HOST:$DB_PORT" "$DB_USER" "$DB_PASS" <<'PY'
import re, sys
path, addr, user, pw = sys.argv[1:]
s = open(path).read()
s = re.sub(r'^storage-method: .*$', 'storage-method: mariadb', s, flags=re.M)
# First 'data:' block holds the SQL settings.
s = re.sub(r'^(data:\n(?:.*\n)*?  address: ).*$', r'\g<1>' + addr, s, count=1, flags=re.M)
s = re.sub(r'^(data:\n(?:.*\n)*?  database: ).*$', r'\g<1>luckperms', s, count=1, flags=re.M)
s = re.sub(r'^(data:\n(?:.*\n)*?  username: ).*$', r'\g<1>' + user, s, count=1, flags=re.M)
s = re.sub(r"^(data:\n(?:.*\n)*?  password: ).*$", r"\g<1>'" + pw + "'", s, count=1, flags=re.M)
open(path, 'w').write(s)
PY
  echo "LuckPerms -> MariaDB"
fi
# CoreProtect and the Skyblock plugin get their DB settings here once they exist.
