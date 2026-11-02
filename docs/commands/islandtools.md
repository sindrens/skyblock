# Island tools module (`modules/islandtools`): the island pickaxe, axe, hoe and sword (Complete Developer Spec D1-D12, T1-T13)

| Command | Description | Permission | Status |
|---|---|---|---|
| `/is tools` | Your island tools: tier (wood → netherite, crystals), 7 upgrades per tool (crystals; confirm above `confirm-above`), a new copy (older copies stop working), the hoe storage (take a stack, sell all). Sneak + click with a tool opens it too | `skyblock.islandtools.use` | ✅ |
| `/islandtools` (`/itools`) | Same menu | `skyblock.islandtools.use` | ✅ |
| `/islandtools reset <player>` | Staff: back to the wooden set, hoe storage emptied (audited `island_tools_reset`) | `skyblock.islandtools.admin` | ✅ |

How they work: every profile gets the wooden set on first join (D12, bound: never traded, T6). They only work on islands
(D1), are put away in the Depths and come back in the same slot on the island (T1/D6; `put-away-worlds`), are kept on
death and can't be dropped. The hoe harvests a row of ripe crops (Row upgrade, 1/3/5/7/9) into its storage (256, x3 per
Storage level), the crops replant and are ripe after the regrow time (60 s → 15 s with the island's crop-growth upgrade,
perks down to 10 s), with a Growth Burst chance from the same upgrade, and a ripe cue (T9-T13). Extra drops go through the
bonus service (source `island_tool`).
