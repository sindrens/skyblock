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

## Staff
| Command | Description | Permission | Status |
|---|---|---|---|
| `/staff` | Staff mode (vanish, inspect, freeze, teleport) | `skyblock.staff.mode` | ⬜ |
| `/inspect <player>` | Player overview GUI | `skyblock.staff.inspect` | ⬜ |
| `/admin` | In-game config editor | `skyblock.admin.config` | ⬜ |
| `/skyblock modules` | Module status | `skyblock.admin.modules` | ✅ |
| `/skyblock reload` | Reload configs | `skyblock.admin.reload` | ✅ |
| `/season` | Season tools (reset, pre-season) | `skyblock.owner.season` | ⬜ |
