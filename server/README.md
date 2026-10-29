# Server

Phoenix Skyblock (SkybornMC) tracked Paper **26.3** server configuration and scripts. Jars, worlds, logs, player data and keys are ignored (see `.gitignore`).
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
**Easiest: one command from your Mac:**
```bash
cd ~/Development/skyblock && ./server/scripts/install-on-vps.sh ubuntu@<vps-address>
```
It uploads the configs, runs `setup-vps.sh` (Java 25, MariaDB, firewall, fail2ban, systemd service, daily backups, memory sized to the VPS), downloads Paper + plugins, accepts the EULA (after asking you), starts the server and whitelists you.

**Updates (every time after the first install):** no questions, just:
```bash
cd ~/Development/skyblock && ./server/scripts/update-vps.sh
```

Console: `ssh ubuntu@<vps-address>` then `sudo -u minecraft tmux attach -t skyblock` (leave with Ctrl+B, then D).
Restart: `sudo systemctl restart skyblock`. Logs: `/opt/skyblock/server/logs/latest.log`.

**Automatic deploys (later):** create an SSH key for deploys, put the public key in `/opt/skyblock/.ssh/authorized_keys`, and add `TEST_HOST`, `TEST_USER` (`minecraft`), `TEST_SSH_KEY` as GitHub secrets. Every push to `main` then builds, tests and deploys.

## Staff ranks
Builder, Helper, Moderator, Admin, Owner: defined in `permissions/base.txt` (groups, prefixes, wildcards, people); each module's grants live in `permissions/<module>.txt`. `scripts/apply-permissions.sh` applies base.txt first, then the module files alphabetically, after every update. To give someone a rank, add a line in the People section of `permissions/base.txt` (e.g. `lp user Steve parent add builder`) and run `update-vps.sh`.

## Releases by Claude (one-time setup)
```bash
cd ~/Development/skyblock && ./server/scripts/authorize-claude.sh
```
Allows Claude's own deploy key (stored next to the repo in `~/Development/.skyborn-claude/`, never in git) to log in to the VPS as `minecraft` (no sudo) and to push to GitHub. After that Claude builds, commits, pushes and releases (`server/scripts/deploy.sh`) without you running anything. Revoke any time by removing the key from `/opt/skyblock/.ssh/authorized_keys` and from GitHub → Settings → Deploy keys.

## Automatic deploys from GitHub (one-time setup)
```bash
cd ~/Development/skyblock && ./server/scripts/setup-github-deploy.sh
```
After that, every push to `main` builds, tests and deploys to the test VPS (configs, plugin, restart, permissions).

## Worlds
`spawn`, `islands`, `outposts`, `depths`, `warzone`: void worlds created by the plugin from `plugins/PhoenixSkyblock/worlds.yml`. In game: `/skyblock worlds` (click to teleport) or `/skyblock tp <world>`. Each spawn has a small platform so builders can start right away.

## External plugins
Geyser, Floodgate, LuckPerms, CoreProtect, Grim, FastAsyncWorldEdit, ViaVersion, ViaBackwards, Axiom (DESIGN.md §14). Axiom needs the Axiom client mod (Fabric, Java only; https://axiom.moulberry.com) and the builder rank. Java players on 1.21.5 to 26.3 can join. spark is built into Paper.
CoreProtect has no 26.3 release yet; `fetch.sh` skips it until one exists.

Never commit passwords or tokens. They live in `/opt/skyblock/.env` on the VPS and are applied by `scripts/apply-secrets.sh`.
