#!/usr/bin/env bash
# One-time: lets GitHub deploy to the test VPS automatically on every push to main.
# Run FROM YOUR MAC:   cd ~/Development/skyblock && ./server/scripts/setup-github-deploy.sh
# 1) creates a deploy key (only for this purpose), 2) allows it to log in as the 'minecraft' user on the VPS,
# 3) adds the GitHub secrets (automatically with the GitHub CLI 'gh', otherwise shows you what to paste).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TARGET="${1:-$(cat "$ROOT/server/.vps-target" 2>/dev/null || true)}"
[ -n "$TARGET" ] || { echo "Usage: ./server/scripts/setup-github-deploy.sh ubuntu@<vps-address>"; exit 1; }
HOST="${TARGET#*@}"
KEY="$HOME/.ssh/skyborn_deploy"
REPO=$(git -C "$ROOT" remote get-url origin | sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##')

if [ ! -f "$KEY" ]; then
  ssh-keygen -t ed25519 -N "" -C "github-deploy-$REPO" -f "$KEY" -q
  echo "== Created deploy key $KEY"
fi
PUB=$(cat "$KEY.pub")

echo "== Allowing the deploy key on the VPS (user: minecraft)"
ssh -t "$TARGET" "sudo install -d -m 700 -o minecraft -g minecraft /opt/skyblock/.ssh \
  && sudo touch /opt/skyblock/.ssh/authorized_keys \
  && (sudo grep -qxF '$PUB' /opt/skyblock/.ssh/authorized_keys || echo '$PUB' | sudo tee -a /opt/skyblock/.ssh/authorized_keys >/dev/null) \
  && sudo chown minecraft:minecraft /opt/skyblock/.ssh/authorized_keys && sudo chmod 600 /opt/skyblock/.ssh/authorized_keys"

if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
  echo "== Adding GitHub secrets to $REPO"
  gh secret set TEST_HOST -R "$REPO" -b "$HOST"
  gh secret set TEST_USER -R "$REPO" -b "minecraft"
  gh secret set TEST_SSH_KEY -R "$REPO" < "$KEY"
  echo "Done. Every push to main now builds, tests and deploys to $HOST."
else
  echo
  echo "== Add these 3 secrets on GitHub (New repository secret):"
  echo "   https://github.com/$REPO/settings/secrets/actions"
  echo "   TEST_HOST     = $HOST"
  echo "   TEST_USER     = minecraft"
  echo "   TEST_SSH_KEY  = the private key (copied to your clipboard now; paste it as the value)"
  if command -v pbcopy >/dev/null; then pbcopy < "$KEY"; else echo "   (copy the contents of $KEY)"; fi
  echo "Tip: with the GitHub CLI (brew install gh && gh auth login) this script adds them for you."
fi
