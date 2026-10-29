# Staff tools (M1.9, module `staff`)

Settings in `staff.yml`, texts in `messages/staff.yml`, data in `db/staff` (`staff_mode`, `reports`). Every action is audited (category `staff`; frozen chat in `chat`, logout while frozen in `punishment`).

| Command | What | Permission | Status |
|---|---|---|---|
| `/staff` (`/staffmode`, `/mod`) | Staff mode: inventory saved (crash-safe) and swapped for the staff hotbar: Inspect, Freeze, Random player, Logs here, Vanish, Punish, Reports, Leave. Vanish, fly, night vision, no damage/hunger/pickup | `skyblock.staff.mode` | ✅ |
| `/freeze <player>` (`/ss`) · `/unfreeze <player>` | Freeze for a check: no moving/interacting/teleporting/commands (except `freeze.allowed-commands`), chat to staff only, title + Discord link; logout = `freeze.logout-action` (alert / ban) | `skyblock.staff.freeze` (`.exempt`) | ✅ |
| `/inspect <player>` (`/check`) | Profile (rank, playtime, first/last seen, platform, ping, location), report counts, inventory, ender chest, teleport, freeze, punish, logs (offline players too) | `skyblock.staff.inspect` | ✅ |
| `/report <player> [reason]` | Report a player: reason picker GUI or typed reason; cooldown, max open reports, one open report per target | `skyblock.player.report` (everyone; `skyblock.report.bypass-cooldown`) | ✅ |
| `/reports [open/claimed/closed]` · `/reports claim/unclaim <id>` · `/reports close <id> <outcome>` · `/reports player <name>` | Report list GUI, claim, close with outcome (punished, warned, no-evidence, invalid, duplicate); the reporter is told the result (also later on join) | `skyblock.staff.reports` | ✅ |

Staff tools dispatch `/punish` (moderation module), `/logs` (M1.10) and `/invsee`/`/ecsee` (essentials); missing ones say "isn't available yet".

Permission definitions: `modules/staff/src/main/resources/permissions/staff.yml`. Rank grants: `server/permissions/staff.txt`.
