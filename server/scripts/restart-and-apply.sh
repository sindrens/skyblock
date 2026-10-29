#!/usr/bin/env bash
# Restarts the server gracefully (players get a warning) and re-applies permissions.
# Used by the GitHub deploy workflow. Run as the minecraft user on the VPS.
set -euo pipefail
cd "$(dirname "$0")/.."
OLD_INODE=$(stat -c %i logs/latest.log 2>/dev/null || echo 0)
if tmux has-session -t skyblock 2>/dev/null; then
  tmux send-keys -t skyblock -l "say Updating: restart in 10 seconds"; tmux send-keys -t skyblock Enter
  sleep 10
  tmux send-keys -t skyblock -l "stop"; tmux send-keys -t skyblock Enter
  for _ in $(seq 1 120); do tmux has-session -t skyblock 2>/dev/null || break; sleep 1; done
fi
SINCE=$(date +%s)
echo "Server stopped; systemd starts it again in a few seconds."
./scripts/apply-permissions.sh "$SINCE" "$OLD_INODE"
