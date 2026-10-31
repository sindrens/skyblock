# Planned commands

Planned list from the design (not built yet). When a module builds one, move its row to that module's file.
Status: ⬜ planned · 🟡 in progress · ✅ done.

## Player

| Command | Description | Permission | Status |
|---|---|---|---|
| `/menu` | Main hub GUI | `skyblock.menu` | ⬜ |
| `/help [topic]` | Help GUI | `skyblock.help` | ⬜ |
| `/spawn` | Teleport to spawn | `skyblock.spawn` | ⬜ |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/back` | Teleports | `skyblock.tpa`, `skyblock.back` | ⬜ |
| `/warp`, `/warps` | Server + island warps | `skyblock.warp` | ⬜ |
| `/is` | Island GUI / home | `skyblock.island` | ⬜ |
| `/is visit <player>`, `/is invite`, `/is kick`, `/is lock` | Island management | `skyblock.island.*` | ⬜ |
| `/is upgrade` (= `/is masteries`) | Upgrades + tasks | `skyblock.island.upgrade` | ⬜ |
| `/is top` | Island leaderboards (stats + `IslandStatsService.top` ready since M2.6; `/is stats` is live, see `islands.md`) | `skyblock.island.stats` | ⬜ |
| `/is outposts`, `/is biomes` | Outposts and biomes | `skyblock.island.outposts` | ⬜ |
| `/dungeon` | The Depths tier selector | `skyblock.depths` | ⬜ |
| `/withdraw` | Money notes (M3.2); `/bal`, `/pay`, `/baltop`, `/transactions`, `/eco` are live: see `economy.md` | `skyblock.economy.*` | ⬜ |
| `/bank` | Personal + island bank | `skyblock.bank` | ⬜ |
| `/sell` (built: [economy](economy.md)), `/shop` (built: [shop](shop.md)), `/ah` (built: [auction](auction.md)) | Selling and trading | see module files | ✅ |
| `/wardrobe` (replaces `/sets`), `/loadout [name]` (`/skills` built: [skills](skills.md), `/enchanter` built: [enchants](enchants.md)) | Progression (plans: `docs/plans/WARDROBE.md`, `docs/plans/ENCHANTS.md`) | `skyblock.wardrobe`, `skyblock.loadout` … | ⬜ |
| `/vote`, `/pass`, `/blackmarket`, `/collection` | Rewards | … | ⬜ |
| `/quests` | Rewards: built in M10.7, see [quests.md](quests.md) | `skyblock.quests.use` | ✅ |
| `/vote`, `/quests`, `/pass`, `/blackmarket` | Rewards | … | ⬜ |
| `/daily` | Rewards: built in M10.6, see [rewards.md](rewards.md) | `skyblock.rewards.daily` | ✅ |
| `/crates` | Rewards: built in M10.1, see [crates.md](crates.md) | `skyblock.crates.use` | ✅ |
| `/collection` | Collection log: built in M10.10, see [collection.md](collection.md) | `skyblock.collection.use` | ✅ |
| `/blackmarket` | Black Market: built in M10.11, see [blackmarket.md](blackmarket.md) | `skyblock.blackmarket.use` | ✅ |
| `/pets`, `/duel`, `/events`, `/report`, `/pv` | Misc | … | ⬜ |
| `/is chat` (`/is leave`, `/is disband`, `/is transfer` built: [islands](islands.md)) | Island membership (REVIEW B10) | `skyblock.island.*` | ⬜ |
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
