#!/usr/bin/env bash
# One command to run the Phoenix Skyblock test server on your own computer (Mac/Linux).
#   cd ~/Development/skyblock && ./server/scripts/run-local.sh
# 1) checks Java 25, 2) builds our plugin, 3) downloads Paper + plugins (first time), 4) starts the server.
# Memory: MEMORY=4G ./server/scripts/run-local.sh   (default 3G, fine for a few testers)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERVER="$ROOT/server"

# Java 25 check (macOS: prefer the installed Temurin 25 if java on PATH is older)
if [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 25 >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
  export JAVA="$JAVA_HOME/bin/java"
fi
JAVA="${JAVA:-java}"
JAVA_VER=$("$JAVA" -version 2>&1 || true)
if ! grep -qE 'version "(2[5-9]|[3-9][0-9])' <<<"$JAVA_VER"; then
  echo "Java 25 is required."
  echo "  Mac: download 'Temurin 25 (JDK)' from https://adoptium.net and install it,"
  echo "       or with Homebrew: brew install --cask temurin@25"
  exit 1
fi

echo "== Building the PhoenixSkyblock plugin"
(cd "$ROOT" && ./gradlew build -x test -q)
echo "   Database: H2 file in server/plugins/PhoenixSkyblock/data/ (database.yml type: h2), no MariaDB needed."

cd "$SERVER"
if [ ! -f paper.jar ]; then
  echo "== First run: downloading Paper and plugins"
  ./scripts/fetch.sh
fi
rm -f plugins/PhoenixSkyblock-*.jar
cp "$ROOT"/plugin/build/libs/PhoenixSkyblock-*.jar plugins/

echo "== Starting. Join with localhost (Java) or your computer's IP, port 19132 (Bedrock)."
echo "   First time, type in this console:  whitelist add <YourName>   and   op <YourName>"
echo "   Stop the server by typing: stop"
MEMORY="${MEMORY:-3G}" JAVA="$JAVA" exec ./start.sh
