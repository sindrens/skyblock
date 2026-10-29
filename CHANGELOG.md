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
