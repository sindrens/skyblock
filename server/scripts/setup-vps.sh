#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 24.04/26.04 VPS for the Skyblock server.
# Run as root:  curl/scp this file to the server, then:  sudo bash setup-vps.sh
# Installs: Java 25 (Temurin), MariaDB, tmux, firewall (ufw), fail2ban, a 'minecraft' user,
# a systemd service, daily backups. Safe to re-run.
set -euo pipefail

BASE=/opt/skyblock
SERVER=$BASE/server
MC_USER=minecraft
# Java heap: RAM minus 1.5 GB for the OS + MariaDB (min 1 GB). Override: sudo MEMORY=10G bash setup-vps.sh
TOTAL_MB=$(awk '/MemTotal/{print int($2/1024)}' /proc/meminfo)
AUTO_MB=$(( TOTAL_MB - 1536 )); [ "$AUTO_MB" -lt 1024 ] && AUTO_MB=1024
MEMORY="${MEMORY:-${AUTO_MB}M}"

[ "$(id -u)" -eq 0 ] || { echo "Run as root (sudo)."; exit 1; }

echo "== Packages"
apt-get update -y
apt-get upgrade -y
apt-get install -y curl wget gnupg ca-certificates apt-transport-https tmux ufw fail2ban mariadb-server python3 rsync unzip

echo "== Java 25"
JAVA_VER=$(java -version 2>&1 || true)
if ! grep -qE 'version "(2[5-9]|[3-9][0-9])' <<<"$JAVA_VER"; then
  # Ubuntu 26.04+ ships OpenJDK 25; older releases use the Eclipse Temurin repo.
  if ! apt-get install -y openjdk-25-jre-headless; then
    install -d -m 0755 /etc/apt/keyrings
    wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release; echo "$VERSION_CODENAME") main" \
      > /etc/apt/sources.list.d/adoptium.list
    apt-get update -y
    apt-get install -y temurin-25-jre
  fi
fi
java -version

echo "== User and folders"
id "$MC_USER" >/dev/null 2>&1 || useradd --system --create-home --home-dir "$BASE" --shell /bin/bash "$MC_USER"
install -d -o "$MC_USER" -g "$MC_USER" "$SERVER" "$BASE/backups" "$SERVER/plugins"

echo "== MariaDB (databases: skyblock, luckperms, coreprotect)"
ENV_FILE=$BASE/.env
if [ ! -f "$ENV_FILE" ]; then
  DB_PASS=$(openssl rand -hex 24)
  cat > "$ENV_FILE" <<ENV
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=skyblock
DB_PASS=$DB_PASS
MEMORY=$MEMORY
ENV
  chown "$MC_USER:$MC_USER" "$ENV_FILE"; chmod 600 "$ENV_FILE"
fi
# shellcheck disable=SC1090
. "$ENV_FILE"
mysql <<SQL
CREATE DATABASE IF NOT EXISTS skyblock    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS luckperms   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS coreprotect CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON skyblock.*    TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON luckperms.*   TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON coreprotect.* TO '$DB_USER'@'localhost';
FLUSH PRIVILEGES;
SQL
# MariaDB only listens locally (default on Ubuntu); make sure:
sed -i 's/^bind-address.*/bind-address = 127.0.0.1/' /etc/mysql/mariadb.conf.d/50-server.cnf || true
systemctl restart mariadb

echo "== Firewall"
ufw allow OpenSSH
ufw allow 25565/tcp   # Java
ufw allow 19132/udp   # Bedrock (Geyser)
ufw --force enable
systemctl enable --now fail2ban

echo "== systemd service (console: sudo -u $MC_USER tmux attach -t skyblock, detach with Ctrl+B then D)"
cat > /etc/systemd/system/skyblock.service <<UNIT
[Unit]
Description=Skyblock Minecraft server
After=network-online.target mariadb.service
Wants=network-online.target

[Service]
Type=forking
User=$MC_USER
WorkingDirectory=$SERVER
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/tmux new-session -d -s skyblock '$SERVER/start.sh'
ExecStop=/usr/bin/tmux send-keys -t skyblock 'stop' Enter
ExecStop=/bin/bash -c 'while tmux has-session -t skyblock 2>/dev/null; do sleep 1; done'
TimeoutStopSec=120
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable skyblock

echo "== Daily backups (04:30, keeps 7 days)"
cat > $BASE/backup.sh <<'BACKUP'
#!/usr/bin/env bash
set -euo pipefail
BASE=/opt/skyblock; STAMP=$(date +%F_%H%M); DEST=$BASE/backups
. $BASE/.env
mysqldump -u"$DB_USER" -p"$DB_PASS" --single-transaction --databases skyblock luckperms coreprotect | gzip > "$DEST/db_$STAMP.sql.gz"
tmux send-keys -t skyblock 'save-off' Enter 2>/dev/null || true
tmux send-keys -t skyblock 'save-all flush' Enter 2>/dev/null || true; sleep 10
tar --exclude='*/cache' --exclude='*/logs' --exclude='*.jar' -czf "$DEST/server_$STAMP.tar.gz" -C $BASE server
tmux send-keys -t skyblock 'save-on' Enter 2>/dev/null || true
find "$DEST" -type f -mtime +7 -delete
BACKUP
chmod +x $BASE/backup.sh; chown "$MC_USER:$MC_USER" $BASE/backup.sh
echo "30 4 * * * $MC_USER $BASE/backup.sh >> $BASE/backups/backup.log 2>&1" > /etc/cron.d/skyblock-backup

echo
echo "Done. Next steps:"
echo " 1. Add the GitHub deploy secrets (see server/README.md), then push to main to deploy, OR copy the repo's server/ folder to $SERVER."
echo " 2. As $MC_USER: cd $SERVER && ./scripts/fetch.sh && ./scripts/apply-secrets.sh"
echo " 3. Accept the EULA: echo eula=true > $SERVER/eula.txt   (read https://aka.ms/MinecraftEULA)"
echo " 4. systemctl start skyblock"
echo " NOTE: backups are stored on this machine only. Add off-site copies before launch."
