#!/usr/bin/env bash
# Updates the VPS with the latest configs, plugins and our plugin jar. No questions asked.
# Run FROM YOUR MAC:   cd ~/Development/skyblock && ./server/scripts/update-vps.sh
# The VPS address is remembered from install-on-vps.sh (stored in server/.vps-target, not in git).
set -euo pipefail
export COPYFILE_DISABLE=1   # macOS: never pack ._ metadata files
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TARGET="${1:-$(cat "$ROOT/server/.vps-target" 2>/dev/null || true)}"
[ -n "$TARGET" ] || { echo "Usage: ./server/scripts/update-vps.sh ubuntu@<vps-address>   (only needed the first time)"; exit 1; }
echo "$TARGET" > "$ROOT/server/.vps-target"

SOCK="/tmp/skyborn-ssh-$$"
SSH=(ssh -o ControlMaster=auto -o ControlPath="$SOCK" -o ControlPersist=10m -o StrictHostKeyChecking=accept-new)
trap 'ssh -o ControlPath="$SOCK" -O exit "$TARGET" 2>/dev/null || true' EXIT

echo "== Uploading configs to $TARGET"
LIST=$(mktemp)
git -C "$ROOT" ls-files server > "$LIST"
JAR=$(cd "$ROOT" && ls -t plugin/build/libs/PhoenixSkyblock-*.jar server/plugins/PhoenixSkyblock-*.jar 2>/dev/null | head -1 || true)
tar -C "$ROOT" -czf - -T "$LIST" ${JAR:+"$JAR"} 2>/dev/null | "${SSH[@]}" "$TARGET" "rm -rf ~/skyborn-upload && mkdir -p ~/skyborn-upload && tar -xzf - -C ~/skyborn-upload 2>/dev/null"
rm -f "$LIST"

echo "== Installing and restarting"
"${SSH[@]}" -t "$TARGET" "sudo rsync -a ~/skyborn-upload/server/ /opt/skyblock/server/ \
  && if [ -n '$JAR' ]; then sudo rm -f /opt/skyblock/server/plugins/PhoenixSkyblock-*.jar; sudo cp ~/skyborn-upload/$JAR /opt/skyblock/server/plugins/; fi \
  && sudo find /opt/skyblock/server -name '._*' -type f -delete \
  && sudo chown -R minecraft:minecraft /opt/skyblock/server \
  && sudo -u minecraft bash -c 'cd /opt/skyblock/server && ./scripts/fetch.sh && ./scripts/apply-secrets.sh' \
  && { sudo -u minecraft tmux send-keys -t skyblock 'say Updating: restart in 10 seconds' Enter 2>/dev/null || true; } \
  && sleep 10 && SINCE=\$(date +%s) && sudo systemctl restart skyblock && echo 'Server restarting with the update.' \
  && sudo -u minecraft /opt/skyblock/server/scripts/apply-permissions.sh \$SINCE"
echo "Done. The server is running the update."
