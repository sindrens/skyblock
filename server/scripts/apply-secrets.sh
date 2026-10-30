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
SB=plugins/PhoenixSkyblock/database.yml
mkdir -p plugins/PhoenixSkyblock
[ -f "$SB" ] || printf 'type: h2\n' > "$SB"
python3 - "$SB" "$DB_HOST" "$DB_PORT" "$DB_USER" "$DB_PASS" <<'PY'
import re, sys
path, host, port, user, pw = sys.argv[1:]
s = open(path).read()
def put(key, value):
    global s
    line = key + ': ' + value
    if re.search(r'^' + re.escape(key) + r':.*$', s, flags=re.M):
        s = re.sub(r'^' + re.escape(key) + r':.*$', lambda m: line, s, count=1, flags=re.M)
    else:
        s += ('' if s.endswith('\n') else '\n') + line + '\n'
put('type', 'mariadb')
put('host', host)
put('port', port)
put('database', 'skyblock')
put('user', user)
put('password', "'" + pw.replace("'", "''") + "'")
open(path, 'w').write(s)
PY
chmod 600 "$SB"
echo "PhoenixSkyblock -> MariaDB"
# Discord webhooks (Phase P 12.11; Discord is postponed, docs/LATER.md). Secrets: never in a tracked file.
# Set DISCORD_WEBHOOK_PUNISHMENTS=https://discord.com/api/webhooks/... in the .env to fill the "punishments" channel;
# the log also needs punishments.yml -> extras.discord.enabled: true. No variable = the file is not written = off.
if [ -n "${DISCORD_WEBHOOK_PUNISHMENTS:-}" ]; then
  mkdir -p plugins/PhoenixSkyblock/secrets
  python3 - plugins/PhoenixSkyblock/secrets/discord.yml "$DISCORD_WEBHOOK_PUNISHMENTS" <<'PY2'
import sys
path, url = sys.argv[1:]
open(path, 'w').write("# Written by server/scripts/apply-secrets.sh from /opt/skyblock/.env. Do not commit.\n"
                      "webhooks:\n  punishments: '" + url.replace("'", "''") + "'\n")
PY2
  chmod 600 plugins/PhoenixSkyblock/secrets/discord.yml
  echo "Discord webhook (punishments) -> secrets/discord.yml"
fi
# CoreProtect gets its DB settings here once it supports 26.3.

# Audit log archive folder (M0.8): chat/command/item logs older than 30 days are archived here before purge.
mkdir -p /opt/skyblock/backups/audit-archive 2>/dev/null || true
