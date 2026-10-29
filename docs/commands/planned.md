# Planned commands

Planned list from the design (not built yet). When a module builds one, move its row to that module's file.
Status: ⬜ planned · 🟡 in progress · ✅ done.

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
| `/withdraw` | Money notes (M3.2); `/bal`, `/pay`, `/baltop`, `/transactions`, `/eco` are live: see `economy.md` | `skyblock.economy.*` | ⬜ |
| `/bank` | Personal + island bank | `skyblock.bank` | ⬜ |
| `/sell`, `/ah`, `/shop` | Selling and trading | `skyblock.sell`, `skyblock.ah` | ⬜ |
| `/sets`, `/enchanter` (`/skills` built: [skills](skills.md)) | Progression | … | ⬜ |
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
| `/tags` | Choose an owned cosmetic tag shown in chat/tab | `skyblock.tags` | ⬜ |
| `/is fuel` | Island Fuel status (offline production) | `skyblock.island.fuel` | ⬜ |

## Staff

| Command | Description | Permission | Status |
|---|---|---|---|
| `/staff` | Staff mode (vanish, inspect, freeze, teleport) | `skyblock.staff.mode` | ⬜ |
| `/inspect <player>` | Player overview GUI | `skyblock.staff.inspect` | ⬜ |
| `/admin` | In-game config editor | `skyblock.admin.config` | ⬜ |
| `/season` | Season tools (reset, pre-season) | `skyblock.owner.season` | ⬜ |
| `/sc <msg>` | Staff chat | `skyblock.staff.chat` | ⬜ |
| `/maintenance` | Maintenance mode + whitelist | `skyblock.admin.maintenance` | ⬜ |
| `/eco admin` | Economy dashboard and corrections (logged) | `skyblock.owner.economy` | ⬜ |
