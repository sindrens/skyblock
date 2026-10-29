# Staff tools (M1.9, module `staff`)

Settings in `staff.yml`, texts in `messages/staff.yml`, data in `db/staff` (`staff_mode`, `staff_frozen`, `reports`). Every action is audited (category `staff`; frozen chat in `chat`, logout while frozen in `punishment`).

| Command | What | Permission | Status |
|---|---|---|---|
| `/staff` (`/staffmode`, `/mod`) | Staff mode: inventory saved (crash-safe) and swapped for the staff hotbar: Inspect (ender eye by default, `mode.inspect-tool-material`; not a spyglass, which zooms), Freeze, Random player, Logs here, Vanish (on/off item), Punish, Reports, Leave. Vanish, fly, night vision, no damage/hunger/pickup | `skyblock.staff.mode` | ✅ |
| `/freeze <player>` (`/ss`) · `/unfreeze <player>` | Freeze for a check: the player can't change position (looking around is fine), build, interact, use items/inventories, drop/pick up, ride, glide, use riptide/pearls/chorus/portals, get knocked back, take or deal damage, or get hungry; no commands except `freeze.allowed-commands` (none by default, so no `/msg` or `/r`); chat goes to staff only; title + Discord link repeat. Staff/plugin teleports still move them (`freeze.allowed-teleport-causes`). The freeze is stored and lasts until `/unfreeze`: across logout and restarts (`freeze.keep-on-rejoin`). Logout = `freeze.logout-action` (alert / ban). `/unfreeze` also works for offline players (completes every frozen name). Slowness + no jump are only visual hints | `skyblock.staff.freeze` (`.exempt`) | ✅ |
| `/inspect <player>` (`/check`) | Profile (rank, playtime, first/last seen, platform, ping, location), report counts, inventory, ender chest, teleport, freeze, punish, logs (offline players too) | `skyblock.staff.inspect` | ✅ |
| `/report <player> [reason]` | Report a player: reason picker GUI or typed reason; cooldown, max open reports, one open report per target | `skyblock.player.report` (everyone; `skyblock.report.bypass-cooldown`) | ✅ |
| `/reports [open/claimed/closed]` · `/reports claim/unclaim <id>` · `/reports close <id> <outcome>` · `/reports player <name>` | Report list GUI, claim, close with outcome (punished, warned, no-evidence, invalid, duplicate); the reporter is told the result (also later on join) | `skyblock.staff.reports` | ✅ |

Staff tools dispatch `/punish` (moderation module), `/logs` (M1.10) and `/invsee`/`/ecsee` (essentials); missing ones say "isn't available yet".

Permission definitions: `modules/staff/src/main/resources/permissions/staff.yml`. Rank grants: `server/permissions/staff.txt`.
