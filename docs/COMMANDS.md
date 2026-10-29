# Commands and Permissions

Planned list. Update as features ship. Every command must also be reachable from a GUI.
Permission root: `skyblock.`. Status: ⬜ planned · 🟡 in progress · ✅ done.

## Player
| Command | Description | Permission | Status |
|---|---|---|---|
| `/menu` | Main hub GUI | `skyblock.menu` | ⬜ |
| `/help [topic]` | Help GUI | `skyblock.help` | ⬜ |
| `/tutorial` | Start/resume tutorial | `skyblock.tutorial` | ⬜ |
| `/spawn` | Teleport to spawn | `skyblock.spawn` | ⬜ |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/back` | Teleports | `skyblock.tpa`, `skyblock.back` | ⬜ |
| `/warp`, `/warps` | Server + island warps | `skyblock.warp` | ⬜ |
| `/is` | Island GUI / home | `skyblock.island` | ⬜ |
| `/is visit <player>`, `/is invite`, `/is kick`, `/is lock` | Island management | `skyblock.island.*` | ⬜ |
| `/is upgrade` (= `/is masteries`) | Upgrades + tasks | `skyblock.island.upgrade` | ⬜ |
| `/is stats`, `/is top` | Stats, leaderboards | `skyblock.island.stats` | ⬜ |
| `/is outposts`, `/is biomes` | Outposts and biomes | `skyblock.island.outposts` | ⬜ |
| `/dungeon` | The Depths tier selector | `skyblock.depths` | ⬜ |
| `/bal`, `/pay`, `/withdraw` | Currencies | `skyblock.economy.*` | ⬜ |
| `/bank` | Personal + island bank | `skyblock.bank` | ⬜ |
| `/sell`, `/ah`, `/shop` | Selling and trading | `skyblock.sell`, `skyblock.ah` | ⬜ |
| `/skills`, `/sets`, `/enchanter` | Progression | `skyblock.skills` … | ⬜ |
| `/crates`, `/vote`, `/daily`, `/quests`, `/pass`, `/blackmarket`, `/collection` | Rewards | … | ⬜ |
| `/pets`, `/duel`, `/events`, `/report`, `/pv` | Misc | … | ⬜ |
| `/is leave`, `/is disband`, `/is transfer <member>`, `/is chat` | Island membership (REVIEW B10) | `skyblock.island.*` | ⬜ |
| `/trade <player>` | Secure two-sided trade | `skyblock.trade` | ⬜ |
| `/msg`, `/r`, `/ignore`, `/mail` | Messaging | `skyblock.msg` … | ⬜ |
| `/settings` | Personal toggles | `skyblock.settings` | ⬜ |
| `/stats [player]`, `/titles`, `/cosmetics` | Profile, titles, cosmetics | `skyblock.stats` … | ⬜ |
| `/satchel` | Depths satchel + sell all | `skyblock.depths.satchel` | ⬜ |
| `/fly` | Fly on own island (perk) | `skyblock.fly` | ⬜ |
| `/trash`, `/afk`, `/rules`, `/discord`, `/store`, `/halloffame` | Utility | … | ⬜ |

## Staff
| Command | Description | Permission | Status |
|---|---|---|---|
| `/staff` | Staff mode (vanish, inspect, freeze, teleport) | `skyblock.staff.mode` | ⬜ |
| `/inspect <player>` | Player overview GUI | `skyblock.staff.inspect` | ⬜ |
| `/admin` | In-game config editor | `skyblock.admin.config` | ⬜ |
| `/skyblock modules` | Module status | `skyblock.admin.modules` | ✅ |
| `/skyblock reload` | Reload configs | `skyblock.admin.reload` | ✅ |
| `/skyblock worlds` | List worlds (click to teleport) | `skyblock.admin.worlds` | ✅ |
| `/skyblock tp <world>` | Teleport to a world's spawn | `skyblock.admin.worlds` | ✅ |
| `/season` | Season tools (reset, pre-season) | `skyblock.owner.season` | ⬜ |
| `/unban <player\|ip>` | Unban a player (tab-completes banned names) or IP; staff are notified | `skyblock.staff.unban` | ✅ |
| `/sc <msg>` | Staff chat | `skyblock.staff.chat` | ⬜ |
| `/maintenance` | Maintenance mode + whitelist | `skyblock.admin.maintenance` | ⬜ |
| `/eco admin` | Economy dashboard and corrections (logged) | `skyblock.owner.economy` | ⬜ |

## Staff ranks (LuckPerms, `server/permissions/luckperms.txt`)
| Rank | Weight | Inherits | Can |
|---|---|---|---|
| Builder | 5 | default | WorldEdit/FAWE, gamemode, teleport, time, `/skyblock worlds` + `tp` |
| Helper | 10 | default | Cheat alerts, staff chat |
| Moderator | 20 | Helper | Staff mode, inspect, kick, ban, teleport, gamemode, block lookups |
| Admin | 30 | Moderator + Builder | All `skyblock.admin.*`, all vanilla commands, Grim, CoreProtect, Via, Geyser |
| Owner | 100 | Admin | Everything (`*`) |
Applied automatically after every update/deploy. Add people at the bottom of the file (`lp user <name> parent add <rank>`).
