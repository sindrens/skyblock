#!/usr/bin/env bash
# ONE-TIME: lets Claude release updates by itself.
# Run FROM YOUR MAC:   cd ~/Development/skyblock && ./server/scripts/authorize-claude.sh
# 1) allows Claude's deploy key (../.skyborn-claude/deploy_key) to log in to the VPS as 'minecraft' (no sudo rights),
# 2) copies the key so you can add it on GitHub as a deploy key with write access (so Claude can push code too).
# Revoke any time: delete the line from /opt/skyblock/.ssh/authorized_keys on the VPS and the key on GitHub.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PUBFILE="$ROOT/../.skyborn-claude/deploy_key.pub"
[ -f "$PUBFILE" ] || { echo "Deploy key not found at $PUBFILE"; exit 1; }
PUB=$(cat "$PUBFILE")
TARGET="$(cat "$ROOT/server/.vps-target" 2>/dev/null || echo "ubuntu@vps-24fa665d.vps.ovh.net")"
REPO=$(git -C "$ROOT" remote get-url origin | sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##')

echo "== Allowing Claude's key on the VPS (user: minecraft)"
ssh -t "$TARGET" "sudo install -d -m 700 -o minecraft -g minecraft /opt/skyblock/.ssh \
  && sudo touch /opt/skyblock/.ssh/authorized_keys \
  && (sudo grep -qxF '$PUB' /opt/skyblock/.ssh/authorized_keys || echo '$PUB' | sudo tee -a /opt/skyblock/.ssh/authorized_keys >/dev/null) \
  && sudo chown minecraft:minecraft /opt/skyblock/.ssh/authorized_keys && sudo chmod 600 /opt/skyblock/.ssh/authorized_keys \
  && echo 'VPS: done.'"

echo
echo "== Last step: add the key on GitHub (it's on your clipboard now)"
echo "   1. A browser tab opens: github.com/$REPO/settings/keys/new"
echo "   2. Title: Claude deploy"
echo "   3. Key: paste (Cmd+V)"
echo "   4. Tick 'Allow write access', then 'Add key'"
command -v pbcopy >/dev/null && printf '%s' "$PUB" | pbcopy
command -v open >/dev/null && open "https://github.com/$REPO/settings/keys/new"
echo "Then tell Claude it's done."
