#!/usr/bin/env bash
# Sends the LuckPerms commands in permissions/ to the running server console (tmux session "skyblock"):
# permissions/base.txt first (groups, weights, prefixes, wildcards, people), then every other permissions/*.txt
# (one per module) in a stable, alphabetical order (LC_ALL=C).
# Waits until the server has finished starting. Usage (as the minecraft user):
#   ./scripts/apply-permissions.sh [started-after-epoch-seconds] [old-latest.log-inode]
set -euo pipefail
cd "$(dirname "$0")/.."
SINCE="${1:-0}"
# Inode of latest.log from BEFORE the restart: the old log already contains "For help", so we must wait for the
# new file (log4j rotates it on start). Without this, commands were typed early, buffered, and all failed.
OLD_INODE="${2:-}"
LOG=logs/latest.log
BASE=permissions/base.txt
[ -f "$BASE" ] || { echo "$BASE is missing; permissions not applied."; exit 1; }
FILES=("$BASE")
# luckperms.txt was the old single file (split in F2); updates copy without deleting, so a stale copy may remain.
LEGACY=permissions/luckperms.txt
[ -f "$LEGACY" ] && echo "Ignoring stale $LEGACY (replaced by base.txt + module files; safe to delete)."
while IFS= read -r f; do
  [ "$f" = "$BASE" ] || [ "$f" = "$LEGACY" ] || FILES+=("$f")
done < <(find permissions -maxdepth 1 -type f -name '*.txt' | LC_ALL=C sort)

# Skip when nothing changed since the last apply: LuckPerms keeps its data, and re-sending ~260 commands on every
# restart floods online staff with "[LP] LOG" lines. FORCE=1 re-applies anyway.
STAMP=permissions/.applied.sha
HASH="$(cat "${FILES[@]}" | sha256sum | cut -d' ' -f1)"
if [ "${FORCE:-0}" != "1" ] && [ -f "$STAMP" ] && [ "$(cat "$STAMP")" = "$HASH" ]; then
  echo "Permissions unchanged since the last apply; skipped (FORCE=1 to re-apply)."
  exit 0
fi

echo "Waiting for the server to finish starting..."
for _ in $(seq 1 180); do
  if tmux has-session -t skyblock 2>/dev/null && [ -f "$LOG" ] \
     && [ "$(stat -c %Y "$LOG")" -ge "$SINCE" ] && { [ -z "$OLD_INODE" ] || [ "$(stat -c %i "$LOG")" != "$OLD_INODE" ]; } \
     && grep -q 'For help, type "help"' "$LOG"; then
    break
  fi
  sleep 2
done
grep -q 'For help, type "help"' "$LOG" 2>/dev/null || { echo "Server did not finish starting; permissions not applied."; exit 1; }
# Paper finishes world setup a moment after "Done"; commands sent in the same tick fail (getLevel() null).
sleep 8

COUNT=0
for FILE in "${FILES[@]}"; do
  while IFS= read -r line || [ -n "$line" ]; do
    [[ -z "${line// }" || "$line" =~ ^[[:space:]]*# ]] && continue
    tmux send-keys -t skyblock -l "$line"
    tmux send-keys -t skyblock Enter
    COUNT=$((COUNT + 1))
    sleep 0.2
  done < "$FILE"
done
echo "$HASH" > "$STAMP"
echo "Applied $COUNT permission commands from ${#FILES[@]} file(s)."
