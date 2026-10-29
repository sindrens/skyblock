#!/usr/bin/env bash
# Downloads Paper 26.3 and the approved external plugins (DESIGN.md §14) into this server folder.
# Usage: ./scripts/fetch.sh            (run from the server/ folder)
# Requires: curl, python3. Safe to re-run: replaces jars with the latest builds.
set -euo pipefail

MC_VERSION="26.3"
cd "$(dirname "$0")/.."
mkdir -p plugins

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
get()  { echo "  -> $2"; curl -fsSL -o "$2.tmp" "$1" && mv "$2.tmp" "$2"; }

echo "Paper $MC_VERSION"
url=$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds/latest" -H "User-Agent: skyblock-setup" \
      | json "d['downloads']['server:default']['url']")
get "$url" paper.jar

modrinth() { # $1 = project slug, $2 = output file
  echo "$1 (Modrinth)"
  local u
  # Prefer a release tagged for MC_VERSION; otherwise fall back to the newest Paper release (and warn).
  u=$(curl -fsSL "https://api.modrinth.com/v2/project/$1/version?loaders=%5B%22paper%22%5D" \
      -H "User-Agent: skyblock-setup" \
      | json "next((v['files'][0]['url'] for v in d if v['version_type']=='release' and '$MC_VERSION' in v['game_versions']), None) or ('FALLBACK ' + next(v['files'][0]['url'] for v in d if v['version_type']=='release'))")
  if [[ "$u" == FALLBACK* ]]; then
    echo "  !! $1 has no release for $MC_VERSION yet. Skipped. Re-run this script after it updates."
    rm -f "plugins/$2"
    return 0
  fi
  get "$u" "plugins/$2"
}

echo "Geyser + Floodgate"
get "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot" plugins/Geyser-Spigot.jar
get "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot" plugins/floodgate-spigot.jar

echo "LuckPerms"
url=$(curl -fsSL https://metadata.luckperms.net/data/all | json "d['downloads']['bukkit']")
get "$url" plugins/LuckPerms-Bukkit.jar

modrinth coreprotect        CoreProtect.jar
modrinth grimac             GrimAC.jar
modrinth fastasyncworldedit FastAsyncWorldEdit.jar
modrinth viaversion         ViaVersion.jar       # lets other client versions join (min version: plugins/ViaVersion/config.yml)
modrinth viabackwards       ViaBackwards.jar     # needed for clients OLDER than the server
modrinth axiom-paper-plugin AxiomPaper.jar       # Axiom building tool (builders need the Axiom client mod, Java/Fabric only)
modrinth placeholderapi     PlaceholderAPI.jar   # %placeholders% for other plugins; our expansion %phoenix_<key>% registers automatically

# spark is built into Paper (use /spark). No separate plugin needed.
echo "Done. Start with ./start.sh"
