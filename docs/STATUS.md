# Status (single source of truth for a fresh session)

Read this file and `docs/QUEUE.md`, then continue. Last updated: 2026-10-29 (after Phase 1 merge).

## Where we are
- **Phase 0 (foundation): done.** Config, style/messages, DB (MariaDB/H2 + migrations), player data, commands (enforced tab completion), menus (redesigned look), items (+dupe detection), audit log, services/placeholders (PlaceholderAPI optional), LuckPerms hooks, LegacyColors (`&` codes).
- **Phase 1 (core experience): code done, owner testing.** Modules: worlds, motd, moderation (M1.8 punishments), essentials (M1.1 join/spawn + approved essentials), hud (M1.4 scoreboard), social, perks (nicks `~`), chat (M1.3 chat, M1.5 tab/nametags), menus (M1.6 /menu /help, M1.7 /settings, M1.12 /admin, /staff hub), staff (M1.9 staff mode `/staffmode`, freeze, inspect, reports), logs (M1.10 /logs), cosmetics (M1.11 tags & titles, /display).
- **Phase 4 started:** M4.3 per-player blocks engine merged (modules/depths).
- **M3.0 balance sheet** merged as DRAFT (`docs/ECONOMY_BALANCE.md`, model `tools/economy_model.py --check`); owner must approve §12 before M3.4 prices go live.
- **Phase 2 started:** M2.1 islands (grid, /is, FAWE/placeholder starter island, borders, home) merged.
- **M3.1 economy core** merged (EconomyService in common, /bal /baltop /pay /transactions /eco). **M4.5 skills** merged (SkillService in common, /skills).
- Before each release run `python3 tools/sync_server_configs.py` (tracked server configs follow module resources).
- **M2.2 island protection** merged. **Phase P** (owner's punishment spec) added as its own phase with priority; spec in `docs/specs/`.
- **M4.1 Depths layout** merged (`zones.yml` written on first start, not tracked; `/depths generate`, `/depths tp`).
- **Builds:** done by a separate builder agent/team from `docs/BUILDER_BRIEF.md` (not our job).

## Release / infrastructure
- Queue agents are launched with `Workflow({scriptPath: '/home/claude/wfscripts/queue-item.js', args: {item, task}})` (copy kept in `tools/queue-item.js`).
- Code repo in the cloud workspace: `/home/claude/skyblock` (branch `main`). The owner's Mac clone: `~/Development/skyblock` (GitHub `sindrens/skyblock`).
- Release (one call): build jar → `git bundle create /mnt/user-data/outputs/updateN.bundle <last>..main` + copy jar as `phoenix-uN.jar` → `device_commit_files` both to `~/Development/.skyborn-claude/` → device: `timeout 175 ~/mnt/Development/.skyborn-claude/release.sh N` (pulls bundle, pushes GitHub, deploys to VPS, prints jar md5 + log). The jar md5 printed must equal the local one.
- Console access for verification: device `~/vps.sh '<cmd>'`, console commands via `tmux send-keys -t skyblock "<cmd>" Enter`. Restore anything changed.
- Last released bundle: update33. Next bundle number: 34.
- Owner gets a push notification on every release (PushNotification tool).

## Owner rules (always)
- Unit tests allowed: `./gradlew build` (with tests) must pass before every merge. Never a test server without permission.
- Tab completion on every command/argument. All text in messages files, all numbers in typed configs, everything audited.
- Ask before adding new commands. Open questions never block: safest default + add to `docs/OPEN_QUESTIONS.md` → "Waiting for owner".
- Agents: max 3 at once, distinct lanes, merge each one as soon as it finishes, then start the next queue item (see QUEUE.md).

## Running agents (background workflows, each in /home/claude/wt/<item> on branch q/<item>)
- P0 punishment/staff bug fixes
- M3.6 auction house
- M2.3 island members & roles

## Parked / broken
(none)
