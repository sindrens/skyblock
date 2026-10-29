# Server

PhoenixMC tracked Paper **26.3** server configuration and scripts. Jars, worlds, logs, player data and keys are ignored (see `.gitignore`).
Why each setting is what it is: [`CONFIG_NOTES.md`](CONFIG_NOTES.md).

## Run locally (your Mac)
1. Install **Java 25**: "Temurin 25 (JDK)" from https://adoptium.net (or `brew install --cask temurin@25`).
2. Run:
   ```bash
   cd ~/Development/skyblock && ./server/scripts/run-local.sh
   ```
   First run downloads Paper + plugins (~150 MB) and asks you to accept the Minecraft EULA.
3. When the console says `Done`, type: `whitelist add <YourJavaName>` and `op <YourJavaName>`.
4. Join:
   - **Java:** Multiplayer → Add Server → `localhost`
   - **Bedrock on the same Wi-Fi** (phone, tablet, console): Add Server → your Mac's IP (System Settings → Wi-Fi → Details), port `19132`. Whitelist Bedrock players with `fwhitelist add <Gamertag>`.
   - **Friends elsewhere:** wait for the test VPS (opening ports on a home router is not recommended).
5. Stop: type `stop` in the console.

## Test VPS (Ubuntu 26.04 or 24.04)
1. `sudo bash scripts/setup-vps.sh`: Java 25, MariaDB, firewall, fail2ban, systemd service, daily backups.
2. Create an SSH key for deploys, put the public key in `/opt/skyblock/.ssh/authorized_keys`, and add `TEST_HOST`, `TEST_USER` (`minecraft`), `TEST_SSH_KEY` as GitHub secrets.
3. Push to `main`: GitHub builds, tests and deploys automatically (`.github/workflows/deploy-test.yml`).
4. On the VPS as `minecraft`: `./scripts/fetch.sh`, accept the EULA, `sudo systemctl start skyblock`.
Console: `sudo -u minecraft tmux attach -t skyblock` (leave with Ctrl+B, then D).

## External plugins
Geyser, Floodgate, LuckPerms, CoreProtect, Grim, FastAsyncWorldEdit (DESIGN.md §14). spark is built into Paper.
CoreProtect has no 26.3 release yet; `fetch.sh` skips it until one exists.

Never commit passwords or tokens. They live in `/opt/skyblock/.env` on the VPS and are applied by `scripts/apply-secrets.sh`.
