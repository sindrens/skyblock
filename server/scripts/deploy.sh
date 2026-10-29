#!/usr/bin/env bash
# Deploys the tracked server configs + the newest PhoenixSkyblock jar to the VPS as the 'minecraft' user
# (no sudo needed), downloads plugin updates, applies secrets, restarts gracefully and re-applies permissions.
# Used by Claude (automatic releases) and by the GitHub deploy workflow.
#   ./server/scripts/deploy.sh [ssh-key] [minecraft@host]
# Defaults: key ../.skyborn-claude/deploy_key (next to the repo), host from server/.vps-target.
set -euo pipefail
export COPYFILE_DISABLE=1   # macOS: never pack ._ metadata files
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KEY="${1:-$ROOT/../.skyborn-claude/deploy_key}"
HOST_DEFAULT="$(sed -E 's/^[^@]*@//' "$ROOT/server/.vps-target" 2>/dev/null || true)"
TARGET="${2:-minecraft@${HOST_DEFAULT}}"
[ -n "${TARGET#minecraft@}" ] || { echo "No VPS address. Usage: deploy.sh <key> minecraft@<host>"; exit 1; }

# ssh refuses keys readable by others; use a private copy with strict permissions.
TMPKEY=$(mktemp); cp "$KEY" "$TMPKEY"; chmod 600 "$TMPKEY"
STAGE=$(mktemp -d); LIST=$(mktemp)
trap 'rm -rf "$TMPKEY" "$STAGE" "$LIST"' EXIT
SSH=(ssh -i "$TMPKEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -o BatchMode=yes)

JAR=$(cd "$ROOT" && ls -t plugin/build/libs/PhoenixSkyblock-*.jar server/plugins/PhoenixSkyblock-*.jar 2>/dev/null | head -1 || true)
[ -n "$JAR" ] || { echo "No PhoenixSkyblock jar found (build it with ./gradlew build)."; exit 1; }
mkdir -p "$STAGE/server/plugins"; cp "$ROOT/$JAR" "$STAGE/server/plugins/"
git -C "$ROOT" ls-files server > "$LIST"

echo "== Deploying $(basename "$JAR") + configs to $TARGET"
tar -czf - -C "$ROOT" -T "$LIST" -C "$STAGE" "server/plugins/$(basename "$JAR")" 2>/dev/null \
  | "${SSH[@]}" "$TARGET" 'set -e; cd /opt/skyblock && rm -f server/plugins/PhoenixSkyblock-*.jar && tar -xzf - 2>/dev/null \
      && find server -name "._*" -type f -delete && chmod +x server/start.sh server/scripts/*.sh && cd server && ./scripts/fetch.sh && ./scripts/apply-secrets.sh \
      && ./scripts/restart-and-apply.sh'
echo "== Released."
