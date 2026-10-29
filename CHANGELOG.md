# Changelog

## [Unreleased]
### Added
- Repository, design and process docs, CI.
- Gradle multi-module skeleton (common, plugin, modules) for Paper 1.21.11 / Java 21.
- Module system with dependency ordering and failure isolation.
- `/skyblock modules` and `/skyblock reload` admin commands.
- Design review #1 (docs/REVIEW.md), UI style guide (docs/UI_STYLE.md), world and block spec (docs/WORLD.md).
- Decided external plugin list (DESIGN §14).
- Switched to Minecraft 26.3 (Java 25, Gradle 9.8) so Geyser stays current.
- Test server setup: tuned server.properties, bukkit/spigot/paper configs, plugin configs (Geyser, Floodgate, LuckPerms, Grim, FAWE), fetch/start scripts, Ubuntu VPS setup script with MariaDB, firewall, backups, systemd, and a deploy-to-test GitHub workflow.
- Owner decisions: crate keys earn-only, team prizes split by contribution, LuckPerms + CoreProtect confirmed, vanilla Nether/End off, villager trading and elytra allowed.
- Rebrand to **PhoenixMC** (play.phoenixmc.net): red-first phoenix palette, package `net.skybornmc.skyblock`, plugin `PhoenixSkyblock`, MOTD, Geyser name.
- `server/scripts/run-local.sh`: one command to build and run the test server locally.
- Network renamed to **SkybornMC** (play.skybornmc.net). The skyblock gamemode is **Phoenix Skyblock** (phoenix theme kept). Package `net.skybornmc.skyblock`.
- `install-on-vps.sh`: one-command VPS install from the Mac; setup script sizes Java memory to the VPS RAM.
- ViaVersion + ViaBackwards: Java clients 1.21.5 to 26.3 can join (older versions get a friendly kick message).
- Worlds module (5 configured void worlds, `/skyblock worlds`, `/skyblock tp`), MOTD module (animated hex MOTDs), staff ranks via LuckPerms, `setup-github-deploy.sh`.
- SkybornMC logo (branding/) and server icon.
- **M0.1 config framework:** typed `@Setting` config classes with descriptions, ranges and kebab-case keys; new keys merged on update without touching edits; `config-version` + rename migrations; wrong values reported (file → key: reason) and the old value kept; broken YAML never overwrites the file; `core.yml`; `motd.yml` settings now validated; `/skyblock reload [core|module]`, `/skyblock config [file]`; staff see config problems on join.
- **M0.2 style & messages:** `style.yml` (palette incl. SkybornMC frost blue, currencies, rarities, pillars, gradients, symbols, prefix) as MiniMessage tags `<c:…>`, `<cur:…>`, `<rarity:…>`, `<pillar:…>`, `<sym:…>`, `<grad:…>`, `<prefix>`; Bedrock players get legacy colors (Floodgate); `messages/<module>.yml` with merge-on-update and `[missing: key]` fallback; Bedrock-safe symbol check; `sounds.yml`; titles/action bars; `Format` (money, numbers, durations, `1.5k` parsing); all existing texts moved out of code; `/skyblock style` preview; new MOTD style with `{countdown}`.
- **M0.3 database + M0.4 player data:** MariaDB (VPS) / H2 (local) via HikariCP on virtual threads; per-module migrations tracked in `schema_version`; prepared-statement helpers; `players` + `name_history` (playtime, Bedrock flag, settings); data loads before join (5 s timeout → friendly message), autosave 60 s, save on quit/shutdown; `database.yml` filled from `.env` on the VPS.
- **M0.5 command framework:** `CommandSpec` on Brigadier; tab completion enforced on every argument; permissions, player/console-only, cooldowns (+bypass), confirm, typed args (online/offline player, world, duration, amount, choices, text); clickable usage; `commands.yml` aliases/cooldowns; help index. `/unban` migrated.
- **M0.6 menu framework:** red/black 6-row frame, paged lists, confirm, number picker, player picker, toggles, chat prompts, async/live slots, total click protection, debounce, escaped menu items purged; `/skyblock demo-menu`.
- **M0.7 item framework:** item registry with PDC id, UUID for unique items, UI_STYLE lore builder, no italics/vanilla tooltips, item_model ready, dupe detection with staff alerts; `/skyblock give-item`.
- **Axiom** (Paper plugin 26.3 build) for builders; disabled in the islands/outposts worlds.
- `/unban <player|ip>` (moderation module) with banned-name tab completion and staff notifications.
- `deploy.sh` (no-sudo deploy as minecraft, used by Claude and GitHub Actions) and `authorize-claude.sh` (one-time approval of Claude's deploy key).
- Logo v2: red S, feathered wings with red tips, sunrise sky, gold rim with red gems, floating island.
- Deploy scripts no longer upload macOS `._` metadata files (they caused a harmless plugin load error).
- Planning: `docs/CONTEXT.md` (exact stack, plugins, economy meta, hardware, roles) and detailed phase plans `docs/phases/` (0–4) with milestones, UI mockups, staff log system, test checklists. New phase order: Islands → Economy → Depths.
- UI decisions: detailed scoreboard (name, rank, money, tokens, gems, island level; no fuel/members), tab `[RANK] Name [Title] [Tag]` editable by staff, `/tags`, join messages for ranks only, menu item in slot 9.
- Decisions: Omnitool replaced by starter kit + Forge + living tools + blueprints + skins (tradeable, no durability); Tags & Titles in one menu (tags as redeemable, AH-sellable items; titles from achievements); audit retention 30 days / forever on our own server with backups + archives; island build Y 0–256.
