# World and Block Design

Worlds, builds, tiers and vanilla rules. Builders use this as their brief; developers use it as the source for tier configs.
All tier values are **(tunable)** and live in config. Only **vanilla blocks** are used for gameplay blocks so Java and Bedrock look identical without texture mappings.

---

## 1. Worlds

| World | Type | Purpose |
|---|---|---|
| `spawn` | Built, void around | Hub, tutorial, NPCs, crates, leaderboards |
| `islands` | Void | Main islands |
| `outposts` | Void | Outposts (same grid system) |
| `depths` | Built | The Depths: mine, forest, combat zones, world boss arena |
| `warzone` | Built | KOTH, envoys, PvP arena, duels |

All five are **void worlds** created and configured by the `worlds` module from `server/plugins/PhoenixSkyblock/worlds.yml` (game rules, difficulty, spawn, fixed time, border, a small spawn platform for builders). Staff/builders list and jump between them with `/skyblock worlds` and `/skyblock tp <world>`.

The vanilla Nether and End dimensions are **disabled**. The owner plans a different use for them later as custom worlds (see §6).

---

## 2. Spawn (medieval)

**Size:** ~200×200, compact. Every key spot within 30 s walk, and everything is also reachable by `/menu`.
**Palette:** stone bricks, mossy/cracked stone bricks, deepslate tiles, polished andesite paths, spruce + dark oak timber framing, white/light-gray terracotta walls, lanterns + chains, barrels, campfires (smoke), flower boxes.
**Phoenix Skyblock red details** (mainly red, balanced): red nether brick and red terracotta trims, red/black/gold banners (phoenix pattern), red concrete roof accents on key buildings, copper and gold block highlights, magma/lava glow features behind glass, campfires and soul-free (orange) lanterns. Stone and wood stay the base so red reads as accent, not as a wall of red.
**Centerpiece:** a large **phoenix statue** in the Castle Courtyard (red/orange/gold blocks, wings spread, rising from a lava-and-campfire brazier, with `flame` particles). It's the spawn point landmark and the logo in 3D.

| District | Contents |
|---|---|
| **Castle Courtyard** (spawn point) | Phoenix statue, spawn point, **Guide NPC** (tutorial), info board (TextDisplay: "Start here → /menu") |
| **Market Square** | Shop NPC, Sell NPC, Auction House board, Bank NPC, Black Market alley (dark, hidden-feeling corner, opens on schedule) |
| **Crate Yard** | 6 physical crates (Common → Legendary + Monthly), each on a pedestal with rarity-colored particles and a hologram showing the key count |
| **Hall of Heroes** | Leaderboards (island top solo/team, skills, bosses) as TextDisplays + **Hall of Fame** statues of past season winners |
| **Gatehouse** | Portals to The Depths and the Warzone, with Event board (next KOTH/envoy/boss timers) |
| **Tavern** | Quests NPC, daily rewards, vote NPC, Discord/store signs |

Rules: no parkour or tight jumps needed to reach anything (young players, Bedrock touch controls). Wide paths (3+ blocks). Lighting level ≥ 8 everywhere (no mob spawning, readable at night).

NPCs use 1.21.9+ **mannequin** entities (custom built), with name + one-line role above ("Shop · Click to open").

---

## 3. Island grid

- Each island owns one **region-file slot (512 × 512 blocks)**. Island center = center of the region. Deleting/resetting an island = deleting one region file (fast, safe).
- Start size **3×3 chunks (48×48)**, Island Size upgrade grows by 1 chunk ring per level, max **15×15 chunks (240×240)**.
- The border is a per-player world border (packet), shown in the island's border color (cosmetic).
- Height: world range -64 to 320, but **building on islands is limited to Y 0–256** (owner decision).
- Starting schematic: configurable. Default: small medieval islet (grass/dirt, 1 oak tree, a chest with starter items, a sign "Open /menu").
- Void fall → teleport to island home, no damage.
- Outposts: same grid in `outposts`. Fixed **5×5 chunks**, upgradable to 9×9.

---

## 4. The Depths

### 4.1 Layout
- **The Gate** (central hub) with three wings: **Mine**, **Forest**, **Combat**. A world boss arena sits behind the Combat wing.
- Each tier is its own zone, gated by a door that opens per player (packet) when the tier is unlocked. `/dungeon` teleports to any unlocked tier.
- **Per-player blocks:** all players mine the *same* ore field coordinates, but each sees their own blocks. 300 players fit easily, and nobody steals ore.
- Ore/log fields ~40×40 per tier, with decoration around (walls, supports, lights) in the tier palette.
- Broken block → shows the tier's "depleted" block (e.g. bedrock-looking `tuff`/`stone`) → respawns after **10 s**.
- **No PvP.** Visibility setting: everyone / island / none.

### 4.2 Mine tiers (Mining skill unlocks)
XP per block grows ~×1.45 per tier.

| Tier | Ore (block shown) | Unlock (Mining skill) | XP | Zone palette | Ambient mobs |
|---|---|---|---|---|---|
| 1 | Coal (`coal_ore`) | 0 | 1 | Stone, cobblestone, andesite, oak supports, torches | Bats |
| 2 | Copper (`copper_ore`) | 5 | 1.5 | Stone + granite, exposed/weathered copper accents, copper lanterns | Silverfish (harmless) |
| 3 | Iron (`iron_ore`) | 10 | 2.5 | Stone + diorite, iron bars, chains | Zombies (weak) |
| 4 | Gold (`gold_ore`) | 15 | 4 | Sandstone, cut sandstone, gold block accents (ruined vault) | Husks |
| 5 | Redstone (`redstone_ore`) | 20 | 6 | Deepslate, redstone lamps, polished blackstone trims | Skeletons |
| 6 | Lapis (`lapis_ore`) | 25 | 9 | Deepslate tiles, prismarine, water features (flooded mine) | Drowned |
| 7 | Diamond (`deepslate_diamond_ore`) | 30 | 13 | Deepslate, calcite, tuff, soul lanterns | Strays |
| 8 | Emerald (`deepslate_emerald_ore`) | 35 | 19 | Moss, mossy cobblestone, glow lichen, azalea (lush cave) | Cave spiders |
| 9 | **Amethyst** (`amethyst_block`) | 42 | 28 | Calcite, smooth basalt, amethyst clusters (geode) | Endermites |
| 10 | **Quartz** (`nether_quartz_ore`) | 50 | 40 | Netherrack, nether bricks, magma, crimson accents | Zombified piglins (neutral) |
| 11 | **Netherite** (`ancient_debris`) | 58 | 58 | Blackstone, basalt, lava falls (behind glass) | Magma cubes |
| 12 | **Voidstone** (`crying_obsidian`) | 66 | 85 | End stone, purpur, obsidian, end rods | Endermen (neutral) |

### 4.3 Forest tiers (Foraging skill unlocks)
| Tier | Tree | Unlock | XP/log | Ground and decoration |
|---|---|---|---|---|
| 1 | Oak | 0 | 1 | Grass, flowers, fences |
| 2 | Birch | 5 | 1.5 | Grass, lily of the valley, birch logs fallen |
| 3 | Spruce | 10 | 2.5 | Podzol, ferns, snow layers |
| 4 | Jungle | 15 | 4 | Jungle grass, vines, cocoa, bamboo |
| 5 | Acacia | 20 | 6 | Coarse dirt, savanna grass, terracotta rocks |
| 6 | Dark Oak | 25 | 9 | Podzol, mushrooms, dim light |
| 7 | Mangrove | 30 | 13 | Mud, mangrove roots, shallow water |
| 8 | Cherry | 35 | 19 | Grass, pink petals, cherry leaves particles |
| 9 | Pale Oak | 42 | 28 | Pale moss, pale hanging moss, eerie light |
| 10 | Crimson | 50 | 40 | Crimson nylium, shroomlights, weeping vines |
| 11 | Warped | 58 | 58 | Warped nylium, twisting vines, soul fire |

Logs respawn per player after 10 s. Leaves are decoration (not breakable). Treefeller enchant breaks all logs of *your* tree.

### 4.4 Combat tiers (Combat skill unlocks)
Shared mobs (visibility forced to "everyone"). Each tier has a **mini-boss** that spawns every X minutes.

| Tier | Zone | Mobs | Mini-boss |
|---|---|---|---|
| 1 | Graveyard | Zombies | Grave Warden (big zombie) |
| 2 | Bone Crypt | Skeletons | Crypt Archer |
| 3 | Spider Den | Spiders, cave spiders | Broodmother |
| 4 | Slime Bog | Slimes | King Slime |
| 5 | Sunken Ruins | Drowned | Tide Caller |
| 6 | Witch Hollow | Witches | Coven Elder |
| 7 | Pillager Outpost | Pillagers, vindicators | Raid Captain |
| 8 | Blaze Forge | Blazes | Forge Lord |
| 9 | Wither Keep | Wither skeletons | Bone King |
| 10 | Warlord's Citadel | Ravagers, evokers | Warlord (world boss arena next to it) |

Creepers: none (no block damage anywhere). Mob damage scales by tier, and tier 1–3 is safe for new players.

### 4.5 World bosses
Arena behind the Combat wing. Spawn on a schedule (announced 5 min before, boss bar HP). Rewards by damage share, top 3 extra. Proposed bosses: **Iron Colossus** (golem), **The Hollow King** (wither-skeleton king), **Wyrm of the Deep** (custom model, phase 2).

---

## 5. Island resources

### 5.1 Crops (Farming skill unlocks which seeds can be bought/planted)
| Tier | Crop | Unlock |
|---|---|---|
| 1 | Wheat | 0 |
| 2 | Carrot, Potato | 5 |
| 3 | Beetroot | 10 |
| 4 | Sugar cane | 15 |
| 5 | Melon, Pumpkin | 20 |
| 6 | Cocoa | 25 |
| 7 | Cactus | 30 |
| 8 | Sweet berries | 35 |
| 9 | Nether wart | 42 |
| 10 | Torchflower | 50 |
| 11 | Pitcher plant | 58 |

### 5.2 Generator (cobble generator ore tiers, Generator upgrade)
Any vanilla cobblestone generator (water + lava) produces a random block from the island's Generator tier table instead of always cobblestone. Tier 1: cobblestone 100%. Each tier adds better ores at low weights (coal → copper → iron → gold → redstone → lapis → diamond → emerald). Ore blocks drop items that sell for **less** than Depths ore (the Depths stay the main mining place).

### 5.3 Fishing
Fishing works in any water. Loot tables scale with the Fishing skill (junk → fish → treasure → rare custom fish → crate key fragments). Custom fish are named variants of vanilla fish items (Bedrock-safe).

---

## 6. Vanilla rules (proposed defaults)

| Topic | Rule | Why |
|---|---|---|
| Nether / End | **No portals from islands** (vanilla dimensions disabled). The owner has a different future use for Nether/End (TBD); they will be custom worlds | Decided 2026-10-29 |
| Natural mob spawning on islands | Off (mobs only from spawners) | Performance and economy control |
| Villagers / wandering traders | **Trading allowed** (owner decision). Villager AI tuned for performance in paper config; trade prices to be watched in the economy dashboard | Decided 2026-10-29 |
| Iron golem farms | Natural iron golem spawning off on islands (economy + lag); golems from players only | Proposal |
| Vanilla enchanting / anvils | Allowed on vanilla gear only | Custom enchants stay on custom tools + sets |
| Elytra | **Allowed** (owner decision; obtained via crates/Black Market/events since the End is closed). Disabled in the warzone | Decided 2026-10-29 |
| Redstone | Allowed. Per-chunk limits on pistons/observers (tunable); lag machines detected and disabled | |
| Hoppers | No count limit (expensive); transfer rate per Paper config | Owner decision |
| Entities per chunk | Cap (e.g. 30 mobs, 20 item frames/armor stands); mob stacking on | Performance |
| Weather on islands | Off | Visual noise, no gameplay use |
| Phantoms | Off | |
| PvP | Only in `warzone` | |
| Ender pearls | Allowed (cooldown in warzone) | |
| Vanilla XP | Given normally (needed for vanilla enchanting) | |
| Building materials | Builder Shop (`/shop`) sells sand, gravel, clay, glass, dirt, wood types, terracotta, concrete powder, nether/end decorative blocks, etc. for money | A real money sink; skyblock players need blocks |

---

## 7. Warzone (medieval battlefield)

- **KOTH hill:** a ruined castle keep in the center. The capture zone is the courtyard.
- **Envoy field:** open plains around the castle. Envoy crates fall at random marked spots (beacon beam + firework).
- **Duel arenas:** 4 small arenas (castle courtyards), queued via `/duel`.
- Safe zone at the entrance with a portal back to spawn.
- Palette: cracked stone bricks, cobblestone walls, burned spruce (stripped dark oak), campfires, broken banners, gravel paths.
