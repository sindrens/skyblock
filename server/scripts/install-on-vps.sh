#!/usr/bin/env bash
# Installs the test server on a fresh Ubuntu VPS, run FROM YOUR MAC:
#   cd ~/Development/skyblock && ./server/scripts/install-on-vps.sh ubuntu@<vps-address>
# You type the VPS password once. Takes ~5-10 minutes.
set -euo pipefail
TARGET="${1:?Usage: ./server/scripts/install-on-vps.sh user@host   (e.g. ubuntu@vps-xxxx.vps.ovh.net)}"
HOST="${TARGET#*@}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SOCK="/tmp/skyborn-ssh-$$"
SSH=(ssh -o ControlMaster=auto -o ControlPath="$SOCK" -o ControlPersist=20m -o StrictHostKeyChecking=accept-new)
trap 'ssh -o ControlPath="$SOCK" -O exit "$TARGET" 2>/dev/null || true' EXIT

read -r -p "Your Java Minecraft username (gets whitelist + op): " MCNAME
echo "The Minecraft EULA must be accepted to run a server: https://aka.ms/MinecraftEULA"
read -r -p "Do you accept the EULA? [y/N] " EULA
[[ "$EULA" =~ ^[Yy]$ ]] || { echo "EULA not accepted. Stopping."; exit 1; }

echo "== Connecting (enter the VPS password if asked)"
"${SSH[@]}" -t "$TARGET" "echo Connected to \$(hostname)" || true
# A forced password change on first login ends the session; reconnect with the new password.
if ! "${SSH[@]}" "$TARGET" true 2>/dev/null; then
  ssh -o ControlPath="$SOCK" -O exit "$TARGET" 2>/dev/null || true
  echo "== Reconnecting (enter your NEW password)"
  "${SSH[@]}" -t "$TARGET" "echo Connected to \$(hostname)"
  "${SSH[@]}" "$TARGET" true || { echo "Could not log in. Run the script again."; exit 1; }
fi

echo "== Uploading server configs"
LIST=$(mktemp)
git -C "$ROOT" ls-files server > "$LIST"
JAR=$(cd "$ROOT" && ls server/plugins/PhoenixSkyblock-*.jar plugin/build/libs/PhoenixSkyblock-*.jar 2>/dev/null | head -1 || true)
tar -C "$ROOT" -czf - -T "$LIST" ${JAR:+"$JAR"} | "${SSH[@]}" "$TARGET" "rm -rf ~/skyborn-upload && mkdir -p ~/skyborn-upload && tar -xzf - -C ~/skyborn-upload"
rm -f "$LIST"

echo "== Running setup (Java 25, MariaDB, firewall, service, backups)"
"${SSH[@]}" -t "$TARGET" "sudo bash ~/skyborn-upload/server/scripts/setup-vps.sh"

echo "== Installing server files"
"${SSH[@]}" -t "$TARGET" "sudo rsync -a ~/skyborn-upload/server/ /opt/skyblock/server/ \
  && if [ -n '$JAR' ]; then sudo cp ~/skyborn-upload/$JAR /opt/skyblock/server/plugins/; fi \
  && sudo chown -R minecraft:minecraft /opt/skyblock/server \
  && sudo -u minecraft bash -c 'cd /opt/skyblock/server && ./scripts/fetch.sh && ./scripts/apply-secrets.sh && echo eula=true > eula.txt'"

echo "== Starting the server"
"${SSH[@]}" -t "$TARGET" "sudo systemctl restart skyblock; \
  for i in \$(seq 1 90); do sudo grep -q 'Done (' /opt/skyblock/server/logs/latest.log 2>/dev/null && break; sleep 2; done; \
  sudo -u minecraft tmux send-keys -t skyblock 'whitelist add $MCNAME' Enter; \
  sudo -u minecraft tmux send-keys -t skyblock 'op $MCNAME' Enter; \
  sleep 2; sudo tail -n 5 /opt/skyblock/server/logs/latest.log"

echo
echo "Done! Join in Minecraft:"
echo "  Java:    $HOST"
echo "  Bedrock: $HOST  port 19132   (whitelist Bedrock players in the console: fwhitelist add <gamertag>)"
echo "Console: ssh $TARGET, then: sudo -u minecraft tmux attach -t skyblock   (leave with Ctrl+B, then D)"
