# Island tools module (`modules/islandtools`): the island pickaxe, axe, hoe and sword (Complete Developer Spec D1-D12, T1-T13)

| Command | Description | Permission | Status |
|---|---|---|---|
| `/is tools` | Your island's shared tools: tier (wood → netherite, shards + money), 8 upgrades per tool incl. Shard Finder (shards + the island task for that tool; confirm above `confirm-above`), a new copy (older copies stop working), the hoe storage (take a stack, sell all). Right-click in the air with a tool (main hand, no sneak needed) opens that tool; right-click on a block works the vanilla way (tilling, stripping) | `skyblock.islandtools.use` | ✅ |
| `/tool` | The menu of the island tool in your hand (for Bedrock, where a click in the air may not arrive); no island tool in hand: all island tools. When the custom tools module is on it owns `/tool` and opens the island tool's menu the same way (`HeldToolMenu`) | `skyblock.islandtools.tool` (everyone; `skyblock.tools.menu` while the tools module owns `/tool`) | ✅ |
| `/islandtools` (`/itools`) | Same menu | `skyblock.islandtools.use` | ✅ |
| `/islandtools reset <player>` | Staff: back to the wooden set, hoe storage emptied (audited `island_tools_reset`) | `skyblock.islandtools.admin` | ✅ |

How they work: every profile gets the wooden set on first join (D12, bound: never traded, T6). They only work on islands
(D1), are put away in the Depths and come back in the same slot on the island (T1/D6; `put-away-worlds`), are kept on
death and can't be dropped. The hoe harvests a row of ripe crops (Row upgrade, 1/3/5/7/9) into its storage (256, x3 per
Storage level), the crops replant and are ripe after the regrow time (60 s → 15 s with the island's crop-growth upgrade,
perks down to 10 s), with a Growth Burst chance from the same upgrade, and a ripe cue (T9-T13). Extra drops go through the
bonus service (source `island_tool`).
