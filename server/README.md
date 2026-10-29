# Server

Tracked Paper **26.3** server configuration and scripts. Jars, worlds, logs, player data and keys are ignored (see `.gitignore`).
Why each setting is what it is: [`CONFIG_NOTES.md`](CONFIG_NOTES.md).

## Run locally (your Mac)
Requires Java 25 (`brew install --cask temurin@25`).
```bash
cd server
./scripts/fetch.sh     # downloads Paper 26.3 + Geyser, Floodgate, LuckPerms, CoreProtect, Grim, FAWE
cp ../plugin/build/libs/Skyblock-*.jar plugins/   # after ./gradlew build in the repo root
./start.sh             # asks you to accept the EULA the first time
```
Add yourself to the whitelist in the console: `whitelist add <name>` and `op <name>`.
Java: `localhost:25565`. Bedrock: `localhost`, port `19132`.

## Test VPS (Ubuntu 24.04)
1. `sudo bash scripts/setup-vps.sh`: Java 25, MariaDB, firewall, fail2ban, systemd service, daily backups.
2. Create an SSH key for deploys, put the public key in `/opt/skyblock/.ssh/authorized_keys`, and add `TEST_HOST`, `TEST_USER` (`minecraft`), `TEST_SSH_KEY` as GitHub secrets.
3. Push to `main`: GitHub builds, tests and deploys automatically (`.github/workflows/deploy-test.yml`).
4. On the VPS as `minecraft`: `./scripts/fetch.sh`, accept the EULA, `sudo systemctl start skyblock`.
Console: `sudo -u minecraft tmux attach -t skyblock` (leave with Ctrl+B, then D).

## External plugins
Geyser, Floodgate, LuckPerms, CoreProtect, Grim, FastAsyncWorldEdit (DESIGN.md §14). spark is built into Paper.
CoreProtect has no 26.3 release yet; `fetch.sh` skips it until one exists.

Never commit passwords or tokens. They live in `/opt/skyblock/.env` on the VPS and are applied by `scripts/apply-secrets.sh`.
