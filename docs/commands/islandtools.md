# Island tools module (`modules/islandtools`): the island's harvester hoe and pickaxe (owner 4 Nov 2026 rework)

| Command | Description | Permission | Status |
|---|---|---|---|
| `/is tools` | Your island's two tools: level, XP, prestige; click one for its menu: the tool's storage (capacity bar, take a stack / shift as much as fits, Sell all), the perks (click x1, shift x10, right-click x100 at the summed price; each locked one says its unlock level), Prestige at the max level (owner/co-owner, confirm screen) and "Get your tool" when you have none. Right-click in the air with a tool (main hand) opens its menu; on a block it works the vanilla way (tilling) | `skyblock.islandtools.use` | ✅ |
| `/tool` | The menu of the island tool in your hand (for Bedrock, where a click in the air may not arrive); no island tool in hand: `/is tools`. When the custom tools module is on it owns `/tool` and opens the island tool's menu the same way (`HeldToolMenu`) | `skyblock.islandtools.tool` (everyone; `skyblock.tools.menu` while the tools module owns `/tool`) | ✅ |
| `/islandtools` (`/itools`) | Same as `/is tools` | `skyblock.islandtools.use` | ✅ |
| `/islandtools reset <player>` | Staff: the player's handle markers and both tool storages are reset (new handles on the next load; audited `island_tools_reset`) | `skyblock.islandtools.admin` | ✅ |
| `/islandtools resetisland <player>` | Staff: the tools of the player's island back to level 1, no perks, prestige 0 (audited `island_tools_reset_island`) | `skyblock.islandtools.admin` | ✅ |
| `/islandtools addxp <player> <hoe\|pickaxe> <amount>` | Staff: tool XP for the player's island tool, to test levels and prestige (audited `island_tool_xp`) | `skyblock.islandtools.admin` | ✅ |
| `/islandtools give <player> <hoe\|pickaxe>` | Staff: the handle of the player's island tool (never a second one) | `skyblock.islandtools.admin` | ✅ |

Shards are ledger currencies: give, take or set them with `/eco give|take|set <player> shards|mining_shards <amount>`
(Farming Shards are the old `shards` key; tab completion lists both).

How they work: the tools belong to the island (`island_tool_progress`: XP, prestige, perks per island and tool). Every
member carries a handle (bound, never traded; one per tool); a handle of another island is removed (audit
`island_tool_removed`). Both start at diamond. XP per relevant natural block (ripe crops / ores and stone) on the island
and at /farm and /mine; the level needs `12 × L^1.6 × (1 + 0.35 × prestige)` XP, max level 50. Perks unlock at tool
levels and cost the tool's own shards (hoe: Farming Shards, pickaxe: Mining Shards; personal per profile). Magnet is
built in; loot goes to the bag, then the tool's storage (never on the ground at /farm and /mine). Prestige resets the
tool and every member's shards of that type, for +5% XP and shards and 5% cheaper perks per prestige; Sweep (hoe) and
Vein (pickaxe) unlock at prestige 1. Speed I while holding the hoe (Speed II from prestige 2). The island's
Harvest Yield / Mine Fortune upgrades add their percent to everything the hoe / pickaxe gets.
