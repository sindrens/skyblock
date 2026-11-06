#!/usr/bin/env bash
# Restarts the server gracefully (players get a warning) and re-applies permissions.
# Used by the GitHub deploy workflow. Run as the minecraft user on the VPS.
set -euo pipefail
cd "$(dirname "$0")/.."
OLD_INODE=$(stat -c %i logs/latest.log 2>/dev/null || echo 0)
if tmux has-session -t skyblock 2>/dev/null; then
  WARN=5; [ "${QUICK:-0}" = "1" ] && WARN=0
  if [ "$WARN" -gt 0 ]; then
    tmux send-keys -t skyblock -l "say Updating: restart in $WARN seconds"; tmux send-keys -t skyblock Enter
    sleep "$WARN"
  fi
  tmux send-keys -t skyblock -l "stop"; tmux send-keys -t skyblock Enter
  for _ in $(seq 1 60); do tmux has-session -t skyblock 2>/dev/null || break; sleep 1; done
  # 3 Nov 2026: console commands can fail ("getLevel() is null"), so /stop never ran and the old jar kept running.
  # Closing the session stops the JVM (worlds are saved by its shutdown hook); systemd starts it again (Restart=always).
  if tmux has-session -t skyblock 2>/dev/null; then
    echo "The server did not stop on 'stop'; closing its session instead."
    tmux kill-session -t skyblock || true
    sleep 5
  fi
fi
SINCE=$(date +%s)
echo "Server stopped; systemd starts it again in a few seconds."
./scripts/apply-permissions.sh "$SINCE" "$OLD_INODE"
