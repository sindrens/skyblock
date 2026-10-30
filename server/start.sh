#!/usr/bin/env bash
# Starts the server (requires Java 25+) with tuned JVM flags (Aikar's G1GC flags, recommended by PaperMC).
# Memory: set MEMORY (e.g. MEMORY=10G ./start.sh). Default 6G for the test server.
# Production (300 players): 16G-24G on a dedicated machine.
set -euo pipefail
cd "$(dirname "$0")"
MEMORY="${MEMORY:-6G}"

if [ ! -f paper.jar ]; then
  echo "paper.jar missing. Run ./scripts/fetch.sh first."; exit 1
fi
if ! grep -qs "eula=true" eula.txt; then
  echo "You must accept the Minecraft EULA (https://aka.ms/MinecraftEULA)."
  read -r -p "Do you accept it? [y/N] " a
  [[ "$a" =~ ^[Yy]$ ]] && echo "eula=true" > eula.txt || exit 1
fi

exec "${JAVA:-java}" -Xms"$MEMORY" -Xmx"$MEMORY" \
  -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem \
  -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 \
  -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 \
  -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 \
  -XX:SurvivorRatio=32 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -Duser.timezone=Europe/Oslo \
  -jar paper.jar --nogui
