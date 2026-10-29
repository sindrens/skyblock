#!/usr/bin/env bash
# Sends permissions/luckperms.txt to the running server console (tmux session "skyblock").
# Waits until the server has finished starting. Usage (as the minecraft user):
#   ./scripts/apply-permissions.sh [started-after-epoch-seconds]
set -euo pipefail
cd "$(dirname "$0")/.."
SINCE="${1:-0}"
LOG=logs/latest.log
FILE=permissions/luckperms.txt

echo "Waiting for the server to finish starting..."
for _ in $(seq 1 180); do
  if tmux has-session -t skyblock 2>/dev/null && [ -f "$LOG" ] \
     && [ "$(stat -c %Y "$LOG")" -ge "$SINCE" ] && grep -q 'Done (' "$LOG"; then
    break
  fi
  sleep 2
done
grep -q 'Done (' "$LOG" 2>/dev/null || { echo "Server did not finish starting; permissions not applied."; exit 1; }

COUNT=0
while IFS= read -r line || [ -n "$line" ]; do
  [[ -z "${line// }" || "$line" =~ ^[[:space:]]*# ]] && continue
  tmux send-keys -t skyblock -l "$line"
  tmux send-keys -t skyblock Enter
  COUNT=$((COUNT + 1))
  sleep 0.2
done < "$FILE"
echo "Applied $COUNT permission commands."
