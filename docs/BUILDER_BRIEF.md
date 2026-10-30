# Builder Brief: Every Build for Phoenix Skyblock

> **Status:** v1.0, 2026-10-29. Owner-requested master brief for **every** build on the server.
> **Audience:** human builders, a build team, or an AI build assistant. Every section can be pasted on its own as a prompt.
> **Sources of truth:** `WORLD.md` (tiers, palettes, vanilla rules), `DESIGN.md` (features), `UI_STYLE.md` (brand colors), `server/plugins/PhoenixSkyblock/worlds.yml` (world settings), phase plans in `docs/phases/`.
> If this brief and `WORLD.md` disagree on a **gameplay number** (tier unlock, XP), `WORLD.md`/config wins. If they disagree on **how a build looks**, this brief wins.
> Everything marked **(assumption)** is a decision the owner has not made yet. It is listed again in [§14 Open questions](#14-open-questions-for-the-owner). Build it as described unless the owner says otherwise.

---

## Table of contents

0. [How to use this brief](#0-how-to-use-this-brief)
1. [Golden rules for every build](#1-golden-rules-for-every-build)
2. [Art direction: two identities, one world](#2-art-direction-two-identities-one-world)
3. [Technical rules: what the code needs from you](#3-technical-rules-what-the-code-needs-from-you)
4. [World map: where everything lives](#4-world-map-where-everything-lives)
5. [Spawn: the Phoenix Citadel](#5-spawn-the-phoenix-citadel)
6. [The starting island and the island grid](#6-the-starting-island-and-the-island-grid)
7. [Outposts: Greenhold, Wildwood, Emberdeep](#7-outposts-greenhold-wildwood-emberdeep)
8. [The Depths: The Gate hub](#8-the-depths-the-gate-hub)
9. [The Depths: Mine wing (12 tiers)](#9-the-depths-mine-wing-12-tiers)
10. [The Depths: Forest wing (11 tiers)](#10-the-depths-forest-wing-11-tiers)
11. [The Depths: Combat wing (10 tiers) and the world boss arena](#11-the-depths-combat-wing-10-tiers-and-the-world-boss-arena)
12. [The Warzone: KOTH keep, envoy field, duel arenas](#12-the-warzone-koth-keep-envoy-field-duel-arenas)
13. [Smaller builds: events, Hall of Fame, network hub, backdrops](#13-smaller-builds-events-hall-of-fame-network-hub-backdrops)
14. [Open questions for the owner](#14-open-questions-for-the-owner)
15. [Master acceptance checklist](#15-master-acceptance-checklist)

---

## 0. How to use this brief

**If you are a human builder:** read §1–§4 once (they apply to everything), then the section of the build you are making. Deliver exactly what the "Deliverables" and "Acceptance checklist" of that section ask for. If something is unclear, ask before building. Rebuilding is far more expensive than asking.

**If you are an AI or a build team receiving this as a prompt:** treat every "must" as a hard requirement and every "should" as a strong default. Treat every number as exact unless marked "about". Keep the brand palette (§2). When you propose a design, describe it as a walk-through ("you step off the arrival pad and see…"), list the block palette, list every functional point with coordinates relative to the build origin, and check it against the acceptance checklist before calling it done.

**Order of work (recommended):** the code ships **placeholder builds** first (owner decision), so the real builds can come in any order. Suggested order by player impact:
1. Spawn: the Phoenix Citadel (§5): first impression, needed for the tutorial.
2. Starting island schematic (§6): every player sees it in minute two.
3. The Gate + Mine tiers 1–4, Forest 1–4, Combat 1–3 (§8–§11): early game.
4. Warzone (§12).
5. Remaining Depths tiers, outposts, boss arena, event extras.

**What "done" means:** the build is delivered as `.schem` files plus a metadata file (§3.6), it passes its acceptance checklist, and it has been walked through once on **Java and Bedrock** on the test server.

---

## 1. Golden rules for every build

These are the rules most often broken on skyblock servers. Breaking them makes a server feel amateur, laggy or confusing.

1. **Gameplay first, beauty second, but both matter.** Every build exists to do a job (sell, teleport, mine, fight). If players can't find the job in 5 seconds, the build failed, no matter how pretty.
2. **Vanilla blocks only.** No resource pack is required to see any build correctly. Java and Bedrock must look the same. Avoid blocks Bedrock renders differently (see §3.3).
3. **Readable at a glance.** Every important place has a landmark you can see from far away (a tower, a statue, a colored roof, a beacon-like light). A player who is lost should be able to turn 360° and recognise where to go.
4. **Paths are wide and obvious.** Main roads 5–7 blocks, side paths at least 3. No path ever needs a jump, parkour, ladder, or crouching. Young players and Bedrock touch-screen players must reach everything.
5. **Never block the camera.** Ceilings in walk-through areas at least 4 blocks high (5+ preferred). No low overhangs at doorways. No leaves or banners hanging at head height on main paths.
6. **Light level 8+ everywhere a player can stand**, unless a zone is deliberately dark (Dark Oak forest, Witch Hollow); even then, paths stay lit. Hide light sources (light blocks are fine) when lanterns would clutter.
7. **Scale to the player, not to the screenshot.** A doorway is 2–3 wide and 3–4 tall. A normal room is 5–9 blocks across. "Giant" builds are only for landmarks (statue, castle keep, Gate). Oversized interiors feel empty; undersized ones feel cramped on Bedrock's wider FOV.
8. **Texture, but not noise.** Mix 3–5 blocks per surface (e.g. stone bricks + cracked + mossy + andesite), never 12. Gradients over long walls, not random checkerboards.
9. **Depth in every wall.** No flat walls longer than 7 blocks. Use pillars, beams, window frames, stairs/slabs for 1-block depth changes.
10. **Red is an accent, not a wall.** Phoenix red is about 30% of what draws the eye (roofs, banners, trims, key details), never a whole wall of red concrete. Body surfaces stay stone, wood, terracotta.
11. **No hidden walkable gaps or exploit spots.** No 1-block holes players can get stuck in, no ledges you can climb out of bounds, no water you can't swim out of, no roofs reachable by jumping from decorations.
12. **Performance budget is part of the design** (§3.4). A beautiful build that costs 5 TPS at 300 players is a broken build.
13. **Build for upgrades.** Code places NPCs, holograms, crates and gates. Leave the exact spaces described in each section, empty of decoration.

---

## 2. Art direction: two identities, one world

### 2.1 The two brands

| Identity | What it is | Colors | Where you use it |
|---|---|---|---|
| **SkybornMC** (network) | The network brand. Logo: an icy marble "S" on a spiked steel-blue plate sitting on a white cloud | Frost `#E4EBF0`, ice `#9FC6E8`, blue `#6CA1D6`, steel `#49649E`, navy `#192F66`, cloud white | Network hub (later), store/Discord showcase corner at spawn, cloud layers under every floating build, sky backdrop |
| **Phoenix Skyblock** (gamemode) | The phoenix: rebirth, fire, rising from ashes. Medieval sky citadel | Phoenix red `#C21F2B`, bright red `#FF3D2E`, ember `#FF8A1F`, flame gold `#FFD23F`, charcoal `#2B2B2B`, parchment `#E8DCC0` | Everything gameplay: spawn, Depths gates, warzone, banners, statues, NPC stalls |

**The story the builds tell without words:** the world floats above the clouds (Skyborn). On these floating islands stands a medieval citadel built around the phoenix. Fire is warm and alive (orange lanterns, campfires, braziers, lava behind glass), never destructive: no burnt ruins at spawn. Ruins and scorched stone belong to the Warzone and the deep Depths tiers only.

### 2.2 Block translation of the brand colors

| Brand color | Main blocks | Detail blocks |
|---|---|---|
| Phoenix red `#C21F2B` | Red nether bricks, red terracotta, red concrete (roofs only, small areas) | Red banners, red carpet, red nether brick stairs/slabs/walls, crimson planks (deep red wood) |
| Bright red `#FF3D2E` | Red wool and red concrete in small patches | Redstone lamps (lit), red stained glass, red candles |
| Ember `#FF8A1F` | Orange terracotta, orange concrete (small), cut copper, waxed exposed copper | Lanterns (orange flame), campfires, magma blocks behind glass, orange banners, copper bulbs |
| Flame gold `#FFD23F` | Gold blocks (sparingly), raw gold blocks, yellow terracotta | Bells, gold trims (gold block slivers in walls), yellow candles, honey blocks behind glass for "warm glass" glow |
| Charcoal `#2B2B2B` | Polished blackstone, blackstone bricks, deepslate tiles, dark oak | Chains, iron bars, anvils, black banners |
| Parchment `#E8DCC0` | White and light gray terracotta, smooth sandstone, birch planks, calcite | Paper-like: lecterns, bookshelves, white banners |
| Neutral stone (body) | Stone bricks, mossy and cracked stone bricks, polished andesite, tuff bricks | Stone brick walls/stairs/slabs, cobblestone, gravel paths |
| Neutral wood (body) | Spruce and dark oak planks/logs, stripped spruce beams | Barrels, fences, trapdoors as shutters |
| SkybornMC frost/ice | Packed ice, blue ice, snow block, calcite, white concrete, light blue terracotta | Light blue / blue stained glass, sea lanterns (cold light), soul lanterns (only for frost), blue banners |
| Cloud white | White wool, white concrete powder, snow blocks, cobweb-free | Diorite for shadowed cloud undersides |

**Do not use** anywhere in gameplay builds: obsidian walls (reads as "griefed"), plain red concrete walls, glazed terracotta floors (visual noise, Bedrock patterns differ in rotation), large plain glass walls (lag-free but cheap looking), shulker boxes as decoration, player heads as main decoration (they load skins per player, slow on Bedrock), item frames as main decoration (entities, see §3.4).

### 2.3 The phoenix motif library (reuse this everywhere)

To keep the world consistent, these motifs repeat in every build at different scales:

- **The Phoenix Statue** (spawn centerpiece, §5.4): wings spread upward, rising from a brazier.
- **The Phoenix Crest:** a shield shape (7 wide × 9 tall) in polished blackstone with a red-and-gold phoenix silhouette inside. Used above important doors (Bank, Forge, Gatehouse) and on Depths tier gates.
- **Wing arches:** archways whose two sides curve like feathered wings (stairs + slabs stepping outward in red nether brick, tipped with orange/yellow). Used for the main gates of every district.
- **Feather trims:** a repeating 3-block pattern along roof edges: red nether brick stair, orange terracotta, red nether brick stair. Used on key buildings only.
- **Braziers:** a campfire on a polished blackstone base with chains, occasionally magma under glass. Braziers mark "important here" (NPC stalls, gates, portals).
- **Phoenix banner (pattern recipe):** red base, orange "gradient up", yellow "flower charge", black "border". Same banner everywhere, so players learn it means "Phoenix Skyblock".
- **Ember particles:** code adds flame/ember particles at configured points. Builders only mark the spots (§3.6).

### 2.4 The sky island aesthetic

Every build that floats (spawn, islands, outposts, the Gate's outer edge, warzone) follows the same "floating island" rules so the world feels like one place:

- **Taper the underside.** A floating landmass is a cone or an irregular inverted mountain, roughly 1.5–2× as deep as it is wide at its edge, ending in 2–4 hanging points (stalactite-like), not a flat slab.
- **Underside palette, top to bottom:** grass/dirt crust (2–3 blocks) → dirt + coarse dirt + rooted dirt → stone + andesite + tuff → (deep tips) deepslate and a few ore specks. Hanging roots and a few vines/moss. On spawn, add hanging chains with lanterns from the underside edge.
- **Clouds below and around:** clusters of white wool/snow/concrete powder in flattened blobs (3–5 blocks thick), with diorite or light gray on the shadow side. Clouds sit 20–40 blocks below the main floor so players see them when they look down. They are decoration only; walking on clouds is never needed.
- **Waterfalls off edges** (optional, spawn only): water falling into the void is beautiful but creates many water source updates. Use **at most 2** and make them end in a hidden catch basin (not into the void, which spams flowing water forever).

---

## 3. Technical rules: what the code needs from you

### 3.1 Worlds and coordinates

All builds live in these void worlds (created by the `worlds` module; settings in `worlds.yml`):

| World | Time of day (fixed) | Border (diameter, blocks) | Difficulty | PvP | Notes for builders |
|---|---|---|---|---|---|
| `spawn` | 6000 (noon) | 1000 | peaceful | off | No mob spawning, no fall damage, random tick speed 0 (leaves never decay, crops never grow, so you can use any decorative plants) |
| `islands` | day/night cycle | none (per-island borders) | normal | off | Each island is a 512×512 slot; you only build the **starting island schematic** |
| `outposts` | day/night cycle | none | normal | off | Same grid; you build the **outpost starter schematics** |
| `depths` | 18000 (midnight, night sky) | 2000 | normal | off | Mobs spawned by the plugin only; time fixed at night, so **all lighting is yours** |
| `warzone` | 13000 (dusk) | 1000 | normal | **on** | Random tick 0; fixed dusk light, plan lighting for that mood |

- **Y levels:** the main floor of every hub build sits at **Y 100** (all world spawns are at `x 0.5, y 100, z 0.5`). You have roughly Y 40 to Y 240 for a build: undersides go down to ~Y 40, towers up to ~Y 200. Keep the area above Y 240 and below Y 30 empty.
- **Islands:** players can build from **Y 0 to Y 256** only. The starting island's top surface sits at Y 100 (assumption, matches world spawn height).
- **Grid alignment:** builds that the code moves or pastes (island schematics, outpost schematics, Depths tier zones) must align to **chunk boundaries** (multiples of 16). Hub builds (spawn, Gate, warzone) are pasted once and don't need chunk alignment, but placing important rooms inside single chunks helps performance.

### 3.2 Things the code places (leave space, don't build them)

The plugin creates these at configured positions. You only leave the space and mark the position:

| Thing | What the code spawns | Space to leave |
|---|---|---|
| **NPC** | A mannequin entity (player-shaped) with name + role text above it | A 1×1 pad, 2 blocks of air above, plus a 3×3 clear area in front (players click it). Pad block: a distinct stone (e.g. chiseled stone bricks) so staff can find it |
| **Hologram / text** | TextDisplay entities (leaderboards, info boards, crate key counts) | A flat wall or open air. For a leaderboard: a wall area 5 wide × 7 tall facing the walkway |
| **Physical crate** | The crate block (e.g. a chest or vault block) + particles + hologram | A 1×1 pedestal top, 3 blocks air above, 3×3 clear around for the opening animation |
| **Tier gate door** | Per-player door blocks (packets) that open when you unlock the tier | An exact opening (default 5 wide × 5 tall), framed, with nothing inside the opening |
| **Ore and log fields** | Per-player block states on top of the real blocks | The real ore/log blocks themselves (yes, build those!) inside one exact cuboid per field, with nothing else in that cuboid |
| **Mob spawn points** | Custom mobs per tier | Marked floor spots (≥ 8 per combat zone), on solid ground, 3 blocks of air above |
| **Portals / teleport pads** | A teleport trigger region + particles | A defined region (e.g. 3×1×4 inside a frame) filled with **air** or with a decorative non-solid block you agree with the dev (end gateway is NOT allowed: it teleports by itself) |
| **Envoy drop spots** | Falling crate + beacon beam + firework | Open sky above (no roof) and a 3×3 flat landing |
| **Particles** | Flame, ember, smoke, end rod | A marked point |

**Never build:** command blocks (disabled), working redstone contraptions (lag), spawners, working portals (nether/end portals are disabled and would confuse players), hoppers/droppers, real entities (armor stands, item frames, paintings, minecarts) without written approval and a count.

### 3.3 Java + Bedrock rendering rules

Bedrock players see the world through Geyser. Most blocks look the same, but avoid these traps:

- **Glazed terracotta** rotates/patterns differently: use only in small accents where rotation doesn't matter.
- **Banners with many layers** render with fewer layers on some Bedrock devices: keep to ≤ 4 layers (the phoenix banner recipe has 4).
- **Light blocks and barrier blocks** are invisible on both, good. **Structure void** is fine in schematics but must not stay in the world.
- **Waterlogged blocks, leaves and glass panes** can look slightly different; test them.
- **Heads with custom textures** may show as Steve heads on Bedrock without extra Geyser setup. Don't make a landmark out of player heads.
- **Fog and render distance:** Bedrock players often play at a lower render distance (6–8 chunks = 96–128 blocks). Every landmark that guides navigation must be visible within **96 blocks**.
- **Text on signs** is readable on both, but keep it to 4 short lines and dark text on light wood (spruce/birch) or glowing text on dark wood.
- **Touch controls:** no button that must be pressed while jumping, no tiny 1×1 click targets high on walls. Everything clickable is at chest or eye height.

### 3.4 Performance budget (300 players on 2 vCPU / 4 GB RAM today)

The test VPS is small (OVH VPS-1, 2 vCPU, 4 GB RAM); launch hardware will be bigger, but builds must already be cheap:

| Item | Budget per hub build (spawn, Gate, warzone) | Why |
|---|---|---|
| Entities placed by you (armor stands, item frames, paintings, display entities) | **0** without approval; max 30 per build if approved | Entities tick and are sent to every player in range |
| Tile entities (chests, barrels, signs, banners, lecterns, furnaces, skulls, beds, bells) | Max ~400 per build, max 40 per chunk | Each is extra data sent per chunk; banners and signs count |
| Light sources | Unlimited lanterns/light blocks, but **no** huge glowstone walls that force relights on edits | Lighting is static once built (random tick 0) |
| Water/lava source blocks | Max 20 flowing columns per build, all contained | Fluid updates on chunk load |
| Leaves | Fine (random tick 0 in spawn/warzone), but keep total leaf volume reasonable in the Depths forest (§10) | Packet size |
| Redstone | **None active.** Redstone lamps may be placed lit by setting them with a lit state in the schematic | Lag, exploits |
| Chunk count | Spawn ≤ 13×13 chunks of actual build (~200×200) | Every player on join loads spawn |

**Rule of thumb:** a chunk with a huge number of different block states and tile entities is expensive to send. Spread dense detail; don't stack it all in one chunk.

### 3.5 Schematic and file rules

- **Format:** Sponge `.schem` (FastAsyncWorldEdit/WorldEdit or Axiom export). Axiom is installed on the test server for builders (blocked in `islands`/`outposts` worlds by config).
- **Origin:** every schematic is saved with the copy point at a defined **origin block** that you also write in the metadata:
  - hub builds: origin = the exact world spawn block of that world (`0, 100, 0`), so pasting at origin puts everything in place;
  - island/outpost schematics: origin = **island center, top surface block** (the block players stand on at the island home);
  - Depths tier zones: origin = the north-west-bottom corner of the zone cuboid, aligned to a chunk corner.
- **Naming:** `<world>_<build>_v<version>.schem`, lowercase, e.g. `spawn_citadel_v3.schem`, `islands_starter_meadow_v1.schem`, `depths_mine_t04_gold_v2.schem`.
- **No air pasting surprises:** export with air included only when the build must clear terrain (hub builds: yes; island schematics: no air, so pasting never deletes player blocks).
- **Biomes:** set the biome inside each build where it changes colors (grass/leaves/water tint). Spawn: `plains` look for grass (warm green); Depths zones: per tier (e.g. `lush_caves`, `cherry_grove`, `pale_garden`, `crimson_forest`). The world default biome is `the_void` in spawn/depths, which gives **gray grass and odd water**, so every grass/leaf area must have its biome set.

### 3.6 The metadata file (the code depends on this)

Every delivery includes a metadata file next to the schematic: `<schematic-name>.yml`. Without it the build can't be wired. Template:

```yaml
schematic: spawn_citadel_v3.schem
world: spawn
origin: { x: 0, y: 100, z: 0 }          # where the copy point was
player_spawn: { x: 0.5, y: 100, z: 0.5, yaw: 180, pitch: 0 }
npcs:                                    # every NPC pad (block the NPC stands on, + facing)
  - { id: guide,        x: 4,   y: 100, z: -6,  yaw: 0 }
  - { id: quartermaster, x: -12, y: 100, z: 20, yaw: 90 }
holograms:                               # text displays
  - { id: info_start,   x: 0,  y: 103, z: -9, facing: south }
leaderboards:
  - { id: island_top_solo, x: 30, y: 101, z: 40, facing: west, size: [5, 7] }
crates:
  - { id: common, x: -40, y: 101, z: 10 }
portals:                                 # teleport trigger cuboids (inclusive corners)
  - { id: to_depths, from: { x: 60, y: 100, z: -2 }, to: { x: 60, y: 104, z: 2 }, target: depths }
regions:                                 # anything the code must know (zones, fields, gates, safe areas)
  - { id: spawn_protected, from: {...}, to: {...} }
particles:
  - { id: statue_flames, x: 0, y: 118, z: 0, type: flame }
signs_to_keep_empty: []                  # signs the code writes into
notes: "Black Market door is the barrel door at -55,100,30; it opens by code."
```

Write coordinates as **world coordinates** after pasting at the origin (not relative), so the developer can copy them straight into config.

### 3.7 Review and test procedure (for every build)

1. Build on the test server (or locally), in the right world.
2. Fill the metadata file.
3. Walk the build on **Java at render distance 8** and on **Bedrock (phone or tablet if possible)**. Check readability, paths, lighting, getting stuck.
4. Check performance: stand in the middle, `/spark tps` and `/spark profiler` for 2 minutes with a few players; send the dev the result.
5. Fix, export, hand over with the acceptance checklist ticked.

---

## 4. World map: where everything lives

```
spawn world (border 1000)            depths world (border 2000*, night)           warzone world (border 1000, dusk)
┌──────────────────────────┐         ┌───────────────────────────────────────┐    ┌──────────────────────────┐
│   clouds + decor islets  │         │            COMBAT wing (north)        │    │       envoy field        │
│   ┌──────────────────┐   │         │   T1 → T10, world boss arena beyond   │    │   ┌──────────────────┐   │
│   │ PHOENIX CITADEL  │   │         │                 │                     │    │   │   KOTH keep      │   │
│   │  ~200 × 200      │   │         │ MINE wing ── THE GATE ── FOREST wing  │    │   │   (center)       │   │
│   │  spawn @ 0,100,0 │   │         │ (west)      (0,100,0)     (east)      │    │   └──────────────────┘   │
│   └──────────────────┘   │         │ T1→T12        hub          T1→T11     │    │ duel arenas (south-east) │
└──────────────────────────┘         └───────────────────────────────────────┘    │ safe zone + portal (S)   │
                                                                                   └──────────────────────────┘
islands world: one 512×512 slot per island, spiral from 0,0     outposts world: same grid, outpost schematics
```

**Placement in the Depths (assumption, matches WORLD.md "The Gate with three wings"):** The Gate at the world spawn (0, 100, 0). Mine wing extends **west** (negative X), Forest wing **east** (positive X), Combat wing **north** (negative Z). Because the wings bend into a U (see below), tier 10 ends back near the Gate, so the **world boss arena is not at the end of the walk**: it sits on its own floating island at the **far north apex** (around Z -850, beyond the U's turn), reached by a **boss portal** in tier 10 (and a `/boss` warp later). This keeps the "distant fortress silhouette" visible from the Combat Gate and the U turn. South of the Gate stays empty (room for a future fourth wing, e.g. Fishing, if the owner adds one).

**Distances and the border problem (critique):** each tier zone is about 80 × 80 blocks plus a 20–40 block causeway between zones, so 12 Mine tiers in a straight line reach about X -1,400. A 2000 border (radius 1000) can't hold that. **Recommended:** each wing **bends into a U**: go out 6 tiers, turn, come back 6 tiers on a parallel causeway ~60 blocks away. Everything then fits in radius ~800, and late tiers end near the Gate (nice for high-level players). Alternative: raise the border in `worlds.yml` to 3,200. (assumption; §14 Q4)

**Where the coordinates live (M4.1):** the plugin ships this layout in `plugins/PhoenixSkyblock/zones.yml` (every zone's cuboid, field cuboids, gate opening, exit, causeway, arrival pad, the boss portal and arena) and builds a code placeholder of it with `/depths generate`. Build on top of the placeholder or replace it; then the dev copies your metadata coordinates into `zones.yml` and sets the zone to `built: true`, after which the placeholder generator never touches it again.

**Why tiers are separate zones, not one big cave:** each tier is its own place with its own gate (code opens it per player), palette and ore field. Players teleport between tiers with `/dungeon`, so walking the wing is never required, but the causeway lets them walk, explore and show off.

---

## 5. Spawn: the Phoenix Citadel

### 5.1 Purpose
The first thing every player sees, the place they return to every session, and the place players show friends. In 10 seconds it must say: *premium, medieval, phoenix, sky, and "everything is easy to find"*. Functionally it holds every NPC, the crates, the leaderboards, portals to the other worlds and the tutorial route.

### 5.2 Where and how big
- World `spawn`, centered on **(0, 100, 0)**. Player spawn around **(0.5, 100, 0.5)**; deliver the final spawn point and facing in the metadata (code reads it).
- Footprint: **about 200 × 200** blocks of walkable citadel (owner: "~200×200, compact"), plus decor islets and clouds within radius ~300.
- Height: main floor Y 100. Terraces may step between Y 96 and Y 106 with wide stairs or ramps (never more than a slab-step per block on main roads). Towers to ~Y 160; keep roof to ~Y 175. Underside down to ~Y 40.
- **Every key spot reachable on foot within 30 seconds from the spawn point.** Walking speed is ~4.3 blocks/s, so max ~130 blocks of walking distance. That is the real reason for "compact".

### 5.3 Layout (top view, not to scale)

```
                              N (−Z)
                ┌───────────── GATEHOUSE ─────────────┐
                │ portals: Depths · Warzone · Island  │
                │ Outposts (later) · Event board      │
     HALL OF    └──────────────┬──────────────────────┘      CRATE
     HEROES  ──────────────────┤                     ────────  YARD
  (leaderboards,               │                              (6 crates,
   Hall of Fame)        CASTLE COURTYARD                       key board)
                        (spawn point, phoenix statue,
   W (−X)               Guide NPC, info board)                    E (+X)
                               │
     TAVERN ───────────────────┼──────────────────── FORGE QUARTER
  (Quests, daily rewards,      │                    (Quartermaster, Blacksmith/
   vote, store/Discord         │                     Forge, Enchanter)
   showcase)             MARKET SQUARE
                     (Shop, Sell, Bank, AH board,
                      Black Market alley at its edge)
                              S (+Z)
```

Seven areas around one courtyard, like a **wheel**: each area is reached by one straight main road (5–7 wide) from the courtyard, plus a ring road (3–5 wide) linking neighbours. Players never have to go *through* one district to reach another.

> **Change vs WORLD.md:** WORLD.md lists 6 districts. This brief adds a **Forge Quarter** for the Quartermaster, Blacksmith (Forge) and Enchanter, because the tool systems decided on 2026-10-29 are central to the game and would crowd the Market Square. (assumption; §14 Q1)

### 5.4 Castle Courtyard (arrival, spawn point)

**Size:** a round or octagonal plaza about **45 blocks across**, open sky.

**As if standing there:** You appear on a slightly raised circular dais of polished deepslate and chiseled stone bricks, inlaid with a ring of red nether brick and a flat gold-and-orange sunburst in slabs. In front of you, 15–20 blocks away, the **Phoenix Statue** rises about **40 blocks**: a stylised phoenix with wings spread up and back, body in red nether bricks and red terracotta, feathers grading from red at the root through orange terracotta and cut copper to yellow terracotta and a few gold blocks at the tips, eyes of shroomlight. It rises out of a wide brazier of polished blackstone and chains, filled with campfires and magma under glass, so it always burns. Code adds flame particles at the brazier and wing tips. Behind the statue the **castle keep** towers over everything (dark stone, red banners, red-and-gold roofs): the landmark you can see from every point of spawn and from the decor islands.

Around you, the courtyard is ringed by low arcades (stone brick arches, spruce beams, lanterns on chains). The **six roads** (one per district: seven areas including the courtyard itself) leave between them, each through a **wing arch** of red nether brick with a district banner and a signpost (code text display: "◆ Market Square", "⛏ Forge Quarter"…). Roads are polished andesite with stone brick borders and a tuff brick center line the eye follows.

At your right hand, 4–6 blocks from the spawn point and inside the first view, stands the **Guide NPC** pad with a brazier beside it and the **info board** (code text: "Start here → /menu"). New players cannot miss it.

**Functional points:**
| Point | Count | Notes |
|---|---|---|
| Player spawn block | 1 | On the dais, facing the statue so statue + keep fill the first view |
| Guide NPC pad (`guide`) | 1 | 4–6 blocks from spawn, inside the first view, never behind the player |
| Info board hologram | 1 | Post or wall 5 wide × 4 tall, readable from the spawn point |
| Statue particle points | 4–8 | Brazier center, both wing tips, head |
| Tutorial start marker | 1 | Floor spot next to the Guide |
| Benches | 6–10 | Stairs + trapdoor armrests, for players waiting after restarts |

**Critique / traps:**
- A 40-block statue placed too close makes the camera stare at a red wall. Keep **15–20 blocks** between spawn and statue base.
- Don't fill the courtyard with stalls; it must stay calm (after a restart 50+ players stand here).
- The statue *is* the logo in 3D. Spend the most time here; the silhouette must read from every side and from below.
- Don't put the spawn under a roof: the first view must include sky.

### 5.5 Market Square (south)

**Purpose:** money in, money out: Shop (buy building blocks), Sell, Bank, the Auction House board and the Black Market.

**Size:** about **50 × 40**, buildings on three sides, the courtyard road entering from the north.

**As if standing there:** You walk south through a wing arch and the square opens up: andesite and cobblestone cobbles, a fountain in the middle (contained, max 4 water sources). Around it, half-timbered shopfronts: stone brick ground floors, white/light gray terracotta upper floors framed by spruce and dark oak beams, red terracotta tile roofs with feather-trim edges. Each NPC has a **market stall** in front of its building: a 5-wide awning (wool or carpet on fences), a counter of barrels and spruce slabs, and the NPC behind it. Each stall has its own color matching the feature's UI color, so players learn it:

| NPC (id) | Stall look | Awning | Building |
|---|---|---|---|
| **Shop** (`shop`), Builder Shop | Display "crates" of logs, sand, glass, terracotta (decor blocks, not containers) | Orange | General store |
| **Sell** (`sell`) | Scales (cauldrons + chains), sacks (brown wool), carts (decor) | Green (money color) | Trading house |
| **Bank** (`bank`) | The **biggest building**: polished blackstone + gold trims, columns, a Phoenix Crest above double doors; NPC at a counter just inside the open entrance, visible from the square; gold blocks behind iron bars as "vault" decor | Gold | Bank of the Phoenix |
| **Auction House** (`ah`) | A large notice board (spruce frame, birch "paper") with code hologram of top listings (7 wide × 5 tall) | Purple | Open auction arcade |
| **Black Market** (`black_market`) | See below | Black | Hidden alley |

**Black Market alley:** in the south-west corner, a narrow (3-wide) alley between two buildings, lit by **dim orange lanterns and candles** (no soul fire: blue belongs to the SkybornMC frost brand). At its end a heavy dark oak door with iron bars and a tiny hooded stall: barrels, a few cobwebs, chains, a black banner. The NPC appears only on schedule (code); when closed, a code-written sign says when it returns. It must *feel* secret but still be lit to level 8 and reachable without jumps.

**Functional points:** 5 NPC pads, AH hologram wall, 1 Black Market status sign (code writes it, keep it empty), fountain water ≤ 4 sources.

**Critique:** markets on most servers are a row of identical booths. Make each stall unique in props and silhouette, but keep every NPC at the **same height and distance from the path**, so players learn where to click.

### 5.6 Forge Quarter (east-south-east)

**Purpose:** tools. **Quartermaster** (free starter kit every 24 h, first tutorial step), **Blacksmith** (the Forge: tool tiers from Depths materials), **Enchanter** (custom enchants for crystals, Phase 6), and a wall for blueprints/skins later.

**As if standing there:** heat. The road opens into a cobbled yard with a big open **forge hall**: blackstone and deepslate walls, a huge chimney with campfires on top for smoke, anvils, grindstones, smithing tables, blast furnaces (unlit; few lit ones, they are tile entities), lava behind glass in a "smelter" (max 6 sources, contained, not within 2 blocks of a path), hanging chains with lanterns, cut copper floor inlays and lit copper bulbs. Left of the hall, the **Quartermaster's armory**: tool "racks" built from grindstones, tripwire hooks, trapdoors and fences (no item frames), barrels, a phoenix banner. Right of the hall, a round **Enchanter's tower**: calcite and deepslate, purple and red stained glass, bookshelves, an enchanting table and amethyst clusters (purple marks enchanting; crystals are aqua in the UI).

| NPC / element | Placement | Notes |
|---|---|---|
| **Quartermaster** (`quartermaster`) | Front of the armory, **first NPC you meet coming from the courtyard** | The tutorial sends players here first; visible from the courtyard road |
| **Blacksmith** (`blacksmith`) | At the main anvil in the forge hall opening | Opens `/forge` |
| **Enchanter** (`enchanter`) | At the tower entrance | Phase 6; code shows "Coming soon" until then |
| Blueprint/skin wall | 9 wide × 5 tall, facing the yard | Code shows skins/blueprints later |

### 5.7 Crate Yard (east-north-east)

**Purpose:** open crates. Six physical crates: **Common, Uncommon, Rare, Epic, Legendary, Monthly**. Keys are earned only (votes, quests, events). After a vote party, 30+ players gather here at once.

**As if standing there:** a semicircular stone terrace on the island edge, open to the sky, clouds behind. Six **pedestals** stand in a gentle arc, 6–8 blocks apart, left to right in rarity order, each themed in its rarity color (UI_STYLE §1.4):

| Crate | Color | Pedestal materials |
|---|---|---|
| Common | `#D9D9D9` | Smooth stone, light gray concrete trims, white candles |
| Uncommon | `#6BDB7A` | Mossy stone bricks, green terracotta, lime glass |
| Rare | `#4F9DFF` | Light blue terracotta, blue glass, lapis block accents |
| Epic | `#B266FF` | Purpur pillar, amethyst blocks, purple glass |
| Legendary | `#FFAA00` | Gold block trims, raw gold, orange glass, a small brazier |
| Monthly (Mythic) | `#FF55D4` | Cherry wood, magenta glass, pink candles, a few crying obsidian sparkles |

Each pedestal: 3×3 base two steps up, a **1×1 top where the code places the crate**, 3 blocks of air above, nothing within 1 block around the top (opening animation). Behind the arc, a **key board** wall (7 wide × 5 tall, code text: "How to get keys: /vote · quests · events" + drop odds).

**Critique:** crates are the most clicked thing at spawn. No narrow ledges, no props players click by accident, terrace at least 30 × 20.

### 5.8 Hall of Heroes (west-north-west)

**Purpose:** prestige: live leaderboards and the **Hall of Fame** for past season winners.

**As if standing there:** a long colonnade of calcite and smooth sandstone columns with red banners between them. Along the inner wall, **leaderboard panels**: dark backing (polished blackstone or deepslate tiles) framed by a thin gold line with a small Phoenix Crest on top, where the code writes the board. At the far end, an apse with **Hall of Fame plinths**.

| Element | Count | Size |
|---|---|---|
| Leaderboard panels | **10**: island top solo, island top team, Mining, Foraging, Farming, Combat, Fishing, boss damage, weekly island gain, weekly pillar gain | 5 wide × 7 tall, center at Y 102–103 |
| Hall of Fame plinths | **8** now, room for 4 more per year (solo + team winner each season) | 3×3, 6 blocks apart |
| Season banner wall | 1 | One banner per season (added by builders each season) |

Winners are shown first by mannequin NPCs placed by code; builders may later replace them with 10–12 block statues in the winner's colors. (assumption; §14)

**Critique:** leaderboards facing bright walls or the sun are unreadable. Dark panel, viewer's back to the light, no decoration in front.

### 5.9 Gatehouse (north)

**Purpose:** travel. Portals to **The Depths**, the **Warzone**, **Your Island** (`/is`: home or create), later **Outposts**. The **Event board** shows timers for KOTH, envoys, bosses and automatic events.

**As if standing there:** the north road ends at a fortified gatehouse in the citadel wall: a massive wing arch, two round towers with red cone roofs, a raised portcullis (iron bars) over the passage. Inside, a wide hall with **portal arches** side by side, each 5 wide × 6 tall, themed like its destination:

| Portal (id) | Frame theme | Inside | Destination |
|---|---|---|---|
| `to_depths` | Deepslate tiles, tuff, copper, chains with lanterns, ore specks in the frame (coal, iron, gold, diamond ore) | Air + code particles; dark recess 3 deep of black concrete | The Gate |
| `to_warzone` | Cracked stone bricks, broken walls, red/black banners, crossed "swords" (fences + tripwire hooks) | Air + red particles | Warzone safe zone |
| `to_island` | Grass, flowers, an oak and a tiny floating islet model above the arch | Air + green particles | `/is` |
| `to_outposts` | Three biome samples (wheat, dark oak, crimson) | Closed by a gate until Outposts exist | Outposts |
| spare (`to_events`) | Neutral stone | Closed | Future |

Each portal: a trigger cuboid of **air** 3 wide × 4 tall × 1 deep inside the frame, a 3×3 landing spot in front (arrivals from other worlds land here), and a signpost text display above (code). The **Event board** faces the courtyard road: 9 wide × 5 tall dark panel.

**Critique:** portals must never be walked into by accident while looking around: put them **one slab-step up** and set back 2 blocks from the walking line. No end gateway / nether portal blocks.

### 5.10 Tavern (west-south-west)

**Purpose:** quests and rewards: **Quests** NPC (daily/weekly, battle pass), **Daily rewards** NPC, **Vote** NPC (links + vote party progress), the **quest board**, and the **store & Discord showcase**.

**As if standing there:** a warm two-storey timber tavern (sign name is the owner's call, e.g. "The Burning Feather") with a big hearth (campfire under a stone chimney), long spruce tables, barrels, hanging lanterns. A covered porch holds the NPCs. On the outer wall, the **quest board** (7 wide × 5 tall) where code shows today's quests.

| NPC / element | Notes |
|---|---|
| **Quests** (`quests`) | Next to the quest board |
| **Daily rewards** (`daily`) | Beside a treasure chest prop (decor, keep it locked/empty) |
| **Vote** (`vote`) | Beside a vote party hologram (5 wide × 3 tall) |
| **Store & Discord showcase** (`showcase`) | A small open pavilion in **SkybornMC frost colors** (calcite, packed ice, light blue glass, white banners), the only frost corner in spawn because store and Discord are network-level. **Five rank plinths** (Squire, Knight, Paladin, Warlord, Monarch) where code places mannequins wearing each rank's cosmetics, plus a 5×3 hologram with store and Discord links |

### 5.11 Tutorial route

The tutorial (optional, under 3 minutes): Guide (courtyard) → **Quartermaster** (Forge Quarter) → Gatehouse **Your Island** portal → island → Depths portal → Market **Sell**.
- From the spawn view, the Forge Quarter road is the **first road on the right**.
- Mark the route with subtle floor inlays (cut copper or tuff brick center line); code adds particle arrows.
- No doors, gates or props on the route.

### 5.12 Info office and staff room
- **Info office** near the courtyard ring: rules board (code hologram 5×7), a "report" mailbox prop, a **Help** NPC (`help`, opens `/help`).
- **Staff room** under the keep (optional, low priority): door openable by staff only (code), a desk, a staff arrival pad. Not visible from player paths.

### 5.13 Secrets (achievements and titles)
DESIGN wants fun/secret achievements. Hide **8–12 small secrets**: a phoenix egg in the underside, a reading nook on a tower top, a fish in the fountain, a tiny model of the old red logo, a hidden bench with the best view… Each is reachable **without parkour** (stairs, a hidden door, at most one ladder) and listed in the metadata (`secrets:`); code awards the achievement.

### 5.14 The castle keep (landmark)
Mostly **exterior**: height to ~Y 175, big red cone and hip roofs with feather trims, gold finials, banners on every tower, windows lit warm (light blocks or lanterns behind glass). Interior optional (great hall with long tables and a phoenix throne), can host events or a future Hall of Fame expansion.

### 5.15 Edges, underside, backdrop
- Floating island per §2.4: underside 50–60 deep, 3–4 hanging rock tips, chains with lanterns hanging from the rim, roots and glow lichen in the rock.
- A citadel wall (battlements of stone brick walls) along the whole edge so **nobody falls by accident**; viewpoints are balconies with a parapet. If someone still falls, code returns them to spawn (no fall damage in `spawn`).
- **Decor islets:** 6–10 small islets in the distance (radius 120–300): a ruin, a lone tree, a tiny farm, at most 2 waterfalls in total. One islet in the east is an **island preview**: a copy of the starting island schematic so players see what they'll get.
- **Clouds** around and 20–40 blocks below the floor.
- **Time is fixed at noon:** still place lanterns, braziers and lit windows; they read as "alive" by day.

### 5.16 Spawn palette summary

| Use | Blocks |
|---|---|
| Walls (body) | Stone bricks, mossy/cracked stone bricks, polished andesite, tuff bricks |
| Upper floors | White and light gray terracotta framed by spruce/dark oak logs and stripped beams |
| Roofs | Red terracotta + red nether brick stairs (key buildings), spruce/dark oak stairs (houses), deepslate tile stairs (forge) |
| Trims and accents | Red nether brick, orange terracotta, cut copper, gold block slivers, polished blackstone |
| Paths | Polished andesite (main), tuff bricks (center line), gravel + coarse dirt edges, stone brick borders |
| Light | Lanterns, campfires, hidden shroomlight, light blocks, lit copper bulbs, candles |
| Greenery | Oak/spruce/azalea trees, flower boxes (trapdoors + flowers), leaf hedges, moss carpet in corners |
| **Avoid** | Soul lanterns/soul fire, sea lanterns (cold light belongs to frost), concrete walls, glazed terracotta floors, end rods everywhere, neon colors |

### 5.17 Deliverables
- `spawn_citadel_vN.schem` (origin 0,100,0) + `spawn_citadel_vN.yml`: spawn point; NPC pads (guide, quartermaster, blacksmith, enchanter, shop, sell, bank, ah, black_market, quests, daily, vote, help: **13**) + the showcase **display spot** (5 mannequin plinths + hologram, no NPC; see Q-showcase); all hologram/leaderboard spots; 6 crate tops; 5 portal cuboids + landing spots; particle points; secrets; the protected region.
- Screenshots: arrival view, each district, top view.
- A note listing entities used (target 0).

### 5.18 Acceptance checklist (spawn)
- [ ] First view from the spawn point shows the statue, the keep and the Guide NPC.
- [ ] Every district reachable in under 30 s of walking; no jumps or ladders on main routes.
- [ ] All 13 NPC pads at walking height with a 3×3 clear area in front.
- [ ] Light level ≥ 8 on every walkable block.
- [ ] No accidental falls; no access to roofs from decorations.
- [ ] Crates in rarity order, terrace ≥ 30 × 20.
- [ ] 10 readable leaderboard panels.
- [ ] Portals stepped up and set back; no real portal blocks.
- [ ] Biome set on all grass/leaves (no gray grass from `the_void`).
- [ ] Entities 0 (or approved list); tile entities within budget (§3.4).
- [ ] No measurable TPS drop with 20+ players in the courtyard (`/spark`).
- [ ] Walked on Bedrock touch controls without problems.

---

## 6. The starting island and the island grid

### 6.1 Purpose
Every player gets this island in their second minute. It is the seed of everything they'll build for three months, so it must be **charming, small, useful and easy to build around**. It's a schematic the code pastes into the player's slot (FAWE, async).

### 6.2 How the grid works (so you understand the constraints)
- Each island owns a **512 × 512 slot** (one region file) in the `islands` world. The island **center** is the center of the slot.
- The playable area starts at **3 × 3 chunks = 48 × 48 blocks**, centered on the island center, aligned to chunk borders. The Island Size upgrade adds one **chunk ring** per level (3×3 → 5×5 → 7×7 …) up to **15 × 15 chunks = 240 × 240**.
  > Note for the dev and owner: WORLD.md says "+1 chunk ring per level, max 15×15". A full ring adds 1 chunk on each side (3×3 → 5×5 → … → 15×15 = 6 levels). If more levels are wanted, growth must be half-rings. This doesn't change the schematic.
- The border is a per-player world border shown only to visitors and members of that island.
- Players can build **Y 0 to Y 256**. The island surface sits at **Y 100** (assumption).
- Islands are shown with a **per-player border**; outside it, nothing can be placed.

**What this means for the schematic:**
- The schematic must fit **well inside 48 × 48**: target **footprint about 15 × 15 to 21 × 21 blocks** at the top surface, leaving room to build inside the starting border. A schematic that fills the 48 × 48 leaves no room for the player's first farm.
- It must be **centered** on the slot center (origin = center top block).
- It must look good **alone in the void**, from every side, and from below (players fall, look down, and see the underside).

### 6.3 The default island: "Ember Meadow" (medieval islet)
Owner decision (placeholder exists in code): small medieval islet with grass/dirt, one oak tree, a chest with starter items, and a sign "Open /menu". The real build keeps exactly those functions and makes them beautiful.

**As if standing there:** you land on the **home spot**: a small 3×3 patch of coarse dirt and a stone path tile in front of a tiny timber **shed** (3×3 inside, spruce frame, light gray terracotta walls, a red terracotta roof with a phoenix banner over the door). To your left, a single **oak tree** (6–7 logs, natural crown, a few leaves overhanging the edge). To your right, a **small pond** (1 water source in a 2×2 basin, or 2 sources for an infinite source, see below) and a **patch of farmland** (2×4) beside it. Under the shed's awning, the **starter chest**. A sign beside the door: "Open /menu". The ground is grass with a few flowers (poppies, dandelions; red/yellow = phoenix colors), a dirt/stone edge, and 2–3 small boulders of andesite/cobblestone. Below, the island tapers into an inverted cone of dirt, stone and a few **ore specks** (1 coal, 1 iron), with hanging roots and one hanging lantern on a chain under the rim.

**Required elements (exact):**
| Element | Count | Details |
|---|---|---|
| Home spot | 1 | The block players teleport to; 2 air above; not under a tree; faces the shed or the open void view (yaw in metadata) |
| Starter chest | 1 | Placed by the schematic **empty**; code fills it (contents in config: lava bucket, ice, bone meal, bread, saplings, sugar cane, cactus, melon/pumpkin seeds) |
| "Open /menu" sign | 1 | Text in the schematic is fine; code may rewrite it |
| Oak tree | 1 | Real logs (players need wood). Leaves on islands decay normally when not attached to logs, so build the crown the natural way (leaves within 4 blocks of a log) |
| Water | 2 sources side by side in a 2×2 basin (infinite source) | Needed for the cobble generator and farming |
| Farmland/dirt | 8+ dirt blocks near water | First crops |
| Space to build a cobble generator | 1 | A clear flat 5×3 area of stone near the water, so the classic first generator is obvious |
| Ores in the underside | 2–4 specks | Small reward for digging; nothing valuable |
| Lava | **None** in the schematic | Lava comes from the starter chest (lava bucket), so new players don't burn their items |

**Palette:** grass, dirt, coarse dirt, rooted dirt, stone, andesite, cobblestone, mossy cobblestone, oak (tree), spruce + stripped spruce (shed frame), light gray terracotta (walls), red terracotta and red nether brick (roof, 1 banner), a lantern. Flowers: poppy, dandelion, cornflower (sparingly).

**Avoid:** anything valuable or farmable for profit (no diamond blocks, no extra trees), anything with tile entities beyond the chest and sign (furnaces etc. come from the player), glass, anything that blocks the player from expanding (big walls), anything players must break immediately to use the space.

**Critique:**
- Most servers' starter islands are an L-shaped dirt block with a tree. Ours must look **made**, but it must not be so pretty players fear changing it; keep the shed tiny and easy to remove.
- An island bigger than ~21 × 21 eats the 48 × 48 starting area. Players need room.
- The underside is what visitors see first when they fly in. Make it a real tapered shape, not a box.
- Leaves: random tick on islands is 3 (normal growth), so leaves not connected to logs decay. Build trees the natural way.

### 6.4 Optional starter variants (assumption)
The schematic is swappable in-game (DESIGN §3.1). If the owner wants players to **pick a starter style**, build 2–3 extra variants with the same required elements and the same footprint:
- **Frost Cliff** (SkybornMC flavored): snowy grass, spruce tree, packed ice boulders, a blue banner.
- **Desert Mesa**: sand, sandstone, terracotta bands, a cactus, an acacia tree, a small oasis pond.
- **Cherry Grove**: cherry tree, pink petals, a moss path.
Variants are cosmetic only (same chest, same water, same space). Don't build them until the owner confirms (§14 Q2).

### 6.5 Expansion-friendly design
The island border grows in chunk rings. Players will extend their island outward in every direction. The schematic's **edge** should not have a big wall or cliff face on one side that looks wrong once the island grows. Keep the outer ring of the schematic low (grass at Y 100, edge dropping smoothly into the underside).

### 6.6 Deliverables
- `islands_starter_meadow_vN.schem`, origin = the home block (center, top surface), **without air** (pasting never deletes player blocks).
- Metadata: home spot + yaw, chest position, sign position, footprint size, lowest Y of the underside.
- Screenshots from the home spot, from 30 blocks away at eye level, and from below.

### 6.7 Acceptance checklist (starting island)
- [ ] Footprint ≤ 21 × 21, centered on the origin, fits in the 48 × 48 start area with room left.
- [ ] Home spot safe (2 air above, not on leaves, not at an edge).
- [ ] Chest empty in the schematic; one sign; water infinite source; no lava.
- [ ] Tree survives (leaves attached to logs).
- [ ] Underside tapered, looks good from below and from a distance.
- [ ] Pasting 20 islands in a row on the test server shows no lag spike (FAWE async).

---

## 7. Outposts: Greenhold, Wildwood, Emberdeep

### 7.1 Purpose
Outposts are small **extra islands for farms**, unlocked by progression (max 3 per island). Each outpost has 3 biomes the player unlocks and upgrades; the biomes are set by code (biome change), not by building. You build **one starter schematic per outpost**.

| Outpost | Unlock | Theme | Biomes (code-applied) |
|---|---|---|---|
| **1: Greenhold** (farming) | All pillars ≥ 10 | A farmstead on a hill | Plains, Jungle, Desert |
| **2: Wildwood** (combat/spawners) | All pillars ≥ 25 + first world boss | A fortified hunting lodge in dark woods | Dark Forest, Swamp, Snowy Taiga |
| **3: Emberdeep** (late game) | All pillars ≥ 40 + Combat tier X | A volcanic forge-camp on blackened rock | Nether Wastes, Crimson Forest, Soul Sand Valley |

### 7.2 Constraints
- World `outposts`, same 512 × 512 slot grid as islands, centered.
- Fixed size **5 × 5 chunks (80 × 80)**, upgradable to **9 × 9 (144 × 144)**.
- Schematic footprint target **about 25 × 25 to 31 × 31**, leaving most of the 80 × 80 for farms and spawners.
- Surface at Y 100 (assumption). Building limit same as islands (Y 0–256).

### 7.3 Greenhold (farming outpost)
**As if standing there:** you arrive on a gentle grassy hill. A small stone-and-timber **barn** (5×7, red terracotta roof, hay bale stacks) stands beside a **windmill** (decor, stationary sails of white wool/birch fences; max 9 blocks tall) and a stone well (2 water sources, infinite). A fenced 9×9 **demo field** of farmland next to the well (empty: players plant). Scarecrow (hay bale + carved pumpkin + fence), barrels, composter.
**Palette:** grass, dirt path, cobblestone, stone bricks, oak and spruce, hay bales, white terracotta, red terracotta roof, flowers.
**Required:** home spot, 2 water sources, 9×9 farmland near water, open space ≥ 40 × 40 on one side for large farms. No chests needed (assumption).

### 7.4 Wildwood (combat/spawner outpost)
**As if standing there:** a small **palisade camp** of dark oak logs (wall 4 high, 2 gates) around a **hunting lodge** (dark oak and stripped dark oak, mossy cobblestone base, deepslate tile roof, fences and trapdoors as trophy racks), a **spawner pit** marker: a 9×9 flat cobblestone pad with a drain trench, showing players "build your mob farm here". Lanterns on posts. Moss, ferns, a few mushrooms.
**Palette:** dark oak, spruce, mossy cobblestone, cobblestone, deepslate tiles, moss, podzol, coarse dirt.
**Required:** home spot, a flat 9×9 spawner pad, open space ≥ 40 × 40. Light level ≥ 8 inside the palisade (mobs from spawners only; natural spawning is off anyway).

### 7.5 Emberdeep (late-game outpost)
**As if standing there:** black basalt and blackstone rock with veins of magma (under the surface, behind glass where visible), a small **forge camp**: a blackstone shelter with a red nether brick roof, an anvil, a lava "pool" behind glass (max 4 sources), crimson fungus decor, shroomlight lamps, a phoenix banner. The air is "hot": campfire smoke from vents.
**Palette:** blackstone, polished blackstone bricks, basalt, smooth basalt, magma (only behind glass or under slabs, players take damage standing on magma!), red nether bricks, crimson stems/planks, shroomlight, netherrack accents.
**Required:** home spot (never next to magma), open space ≥ 40 × 40, a flat 9×9 pad. **No exposed magma, lava or fire on any walkable block.**

### 7.6 Deliverables and checklist (outposts)
- `outposts_greenhold_vN.schem`, `outposts_wildwood_vN.schem`, `outposts_emberdeep_vN.schem`, origin = home block, no air, + metadata (home, pads, water).
- [ ] Each ≤ 31 × 31 footprint, centered, fits 80 × 80 with room.
- [ ] Biomes look right when code switches between the 3 biomes (grass/leaf colors change: check the build doesn't look broken in Desert or Snowy Taiga color).
- [ ] No hazards on walkable blocks; light ≥ 8.
- [ ] Underside tapered like islands.

---

## 8. The Depths: The Gate hub

### 8.1 Purpose
The Depths is the **main income loop** (~50% of income). The Gate is its hub: players arrive here from the spawn portal, see the three wings, and walk (or `/dungeon`) to their tier. It also holds a **Satchel/Sell point** and **Forge access** so players don't have to leave to sell (assumption, saves trips; §14 Q5).

### 8.2 Where and how big
- World `depths`, center **(0, 100, 0)**, fixed **midnight** (time 18000, night sky, stars, the moon). All light is yours.
- Footprint about **90 × 90**, a floating rock platform with an underside, at Y 100.
- Three causeway exits: **west → Mine**, **east → Forest**, **north → Combat**. The south edge is a cliff with a **view** (the void, clouds lit by lanterns hanging below).

### 8.3 As if standing there
You step out of the arrival portal on the south side of a vast **circular stone court** carved into a floating rock, under a starry night sky. In the center, a **great brazier tower**: a 12-block column of polished blackstone with a wide fire bowl on top (campfires, magma under glass) and four phoenix wing arches around its base, lighting the whole court orange. Around the edge, three monumental **wing gates**, each 9 wide × 12 tall, each themed like its wing and topped by its icon:
- **West: The Mine Gate** — deepslate tiles and tuff bricks, copper beams, minecart rails (decor) disappearing into the gate, an iron pickaxe crest (built from blocks), hanging lanterns. Through the gate you see the causeway descending into a cave mouth glowing with ore specks.
- **East: The Forest Gate** — two huge living trees (oak and birch, 20 tall) forming the arch, moss and vines, glow berries for warm light, a stone path continuing into a meadow under the stars.
- **North: The Combat Gate** — cracked stone bricks, iron bars, chains, skull-like shapes (bone blocks), red banners, crossed swords crest; beyond it a dark causeway lit by braziers leading toward a distant fortress silhouette (the Warlord's Citadel / boss arena on its own island at the far north apex; players reach it through the boss portal in tier 10, see §9 placement).

Between the gates, around the court: **services** under small stone pavilions:
| Element | Count | Notes |
|---|---|---|
| Arrival pad (from spawn) | 1 | South side, 3×3, facing north (all three gates visible) |
| Return portal to spawn (`to_spawn`) | 1 | Next to the arrival pad |
| Depths Guide NPC (`depths_guide`) | 1 | Explains tiers/`/dungeon` |
| Sell NPC (`depths_sell`) | 1 | Sells the satchel (players sell here without leaving) |
| Forge NPC (`depths_forge`) | 1 | Same `/forge` as spawn (assumption) |
| `/dungeon` board | 1 | Hologram 7 wide × 5 tall showing your unlocked tiers (code) |
| Wing signposts | 3 | Text displays at each gate: "⛏ Mine · Tier 1–12", "Forest", "⚔ Combat" |
| Event timer board | 1 | Mining rush / boss timers |

**Palette:** tuff bricks, polished tuff, deepslate bricks/tiles, polished blackstone (brazier), copper accents, red nether brick trims on the wing arches, lanterns + campfires (orange only), moss and dripstone at the edges, calcite highlights.

### 8.4 Critique
- At midnight, a dark hub is depressing and dangerous-looking. The Gate must be the **brightest, warmest place in the Depths**: braziers, lanterns, lit windows.
- The three gates must be recognisable from the arrival pad in one look. Different shapes (cave, trees, fortress), not just different colors.
- The causeways leaving the Gate are 7 wide with walls; no falling into the void on the way to tier 1.

### 8.5 Deliverables and checklist (The Gate)
- `depths_gate_vN.schem` (origin 0,100,0) + metadata (arrival, portal, 3 NPC pads, boards, the three causeway start points).
- [ ] All three gates visible and distinct from the arrival pad.
- [ ] Brightest area of the Depths; light ≥ 10 on the court.
- [ ] Causeway starts 7 wide, walled.
- [ ] Return portal next to the arrival pad.

---

## 9. The Depths: Mine wing (12 tiers)

### 9.1 How a mine tier works in code (read this before building)
- Each tier has **one ore field**: a cuboid region in config. Every block inside that cuboid that is the tier's **ore block** becomes a per-player block: when a player breaks it, *that player* sees the tier's **depleted block** in its place, and after **10 seconds** the ore returns for them. Other players keep seeing their own state. The real block on the server never changes.
- Consequence 1: **players can't dig down.** A broken ore shows a depleted block (solid), so the field is a **surface** to mine, not a volume. Every ore block must have **at least one exposed face** players can reach and aim at; buried ore inside the field is wasted and confuses the code's field counts.
- Consequence 2: the field should hold enough ore for many players **at once** (everyone has their own blocks, so 30 players can mine the same blocks), but one player mines about 1 block/s and each block returns after 10 s, so **one player needs ~10–15 reachable ore blocks** around them to never wait. A field with 300–600 exposed ore blocks is plenty.
- Consequence 3: only the tier's ore block may exist inside the field cuboid (plus air). **No decoration inside the field cuboid** (a stray stone block inside the cuboid is fine for code but confusing to players, and decoration blocks there would also "respawn" if the dev later widens the block list).
- The tier gate opens per player when they reach the required Mining skill level. Until then the gate opening looks closed (code shows bars or a portcullis inside the empty opening).

### 9.2 The tier zone template (every mine tier follows this)

```
          causeway in (7 wide)
                 │
    ┌────────────▼────────────┐   ← zone ≈ 80 × 80, aligned to 16 (5×5 chunks)
    │   TIER GATE (5w × 5h)   │   ← opening left EMPTY (code door)
    │  ┌───────────────────┐  │
    │  │  arrival pad 3×3  │  │   ← /dungeon teleport target, safe, lit
    │  │  tier sign + NPC? │  │
    │  ├───────────────────┤  │
    │  │                   │  │
    │  │   ORE FIELD       │  │   ← ~40 × 40 cuboid, ore surface only
    │  │   (pit + low      │  │
    │  │    ore walls)     │  │
    │  │                   │  │
    │  └───────────────────┘  │
    │ decoration ring: walls, │   ← tier palette, supports, lights,
    │ supports, landmark      │     ambient mob spawn markers
    └────────────┬────────────┘
          causeway out to the next tier
```

**Zone size:** about **80 × 80** (5 × 5 chunks), aligned to chunk borders; ceiling (if it's a cave) **at least 12 blocks above the field floor** so the camera never clips and Bedrock players don't feel trapped.
**Field size:** about **40 × 40** footprint (WORLD.md), shaped as a **shallow quarry**: a pit 2–4 blocks deep with stepped terraces (each step 1 block, so players walk, never jump), plus a few **ore "veins"**: low walls and pillars of ore (2–4 high) players can mine from both sides. This shape gives far more exposed ore than a flat floor and looks like a real mine.
**Arrival pad:** a 3×3 safe pad (`/dungeon` teleport target) at the gate side, facing the field.
**Ambient mobs:** code spawns harmless or weak ambient mobs per tier (WORLD.md §4.2). Mark **6–8 spawn spots** on the decoration ring, never inside the field.
**Lighting:** field light ≥ 8 so ore is readable; decoration may be darker for mood. Since the world is at midnight forever, every zone needs real light.
**Tier sign:** a code text display at the gate ("Tier 4 · Gold · Mining 15").
**Depleted block:** each tier's depleted look (code config) should contrast with the ore and match the zone (e.g. `tuff`, `cobbled_deepslate`, `smooth_basalt`). Builders propose; dev configures.

### 9.3 The 12 mine tiers

For every tier: theme, as-if-standing-there, palette, field ore, proposed depleted block, landmark, ambient mobs. Unlock levels and XP live in `WORLD.md` §4.2 / config.

**Tier 1 — Coal (`coal_ore`), Mining 0: "The Old Shaft"**
- *Standing there:* a friendly, well-lit starter mine. Oak timber supports every 6 blocks with lanterns hanging from the beams, rails and a parked minecart (decor blocks only: rails are fine, no minecart entity), a wooden crane over the pit, crates (barrels). The quarry walls are stone and andesite with coal veins.
- *Palette:* stone, cobblestone, andesite, oak logs/planks/fences, lanterns (no torches: they look cheap at scale), gravel paths.
- *Depleted:* `cobblestone`. *Landmark:* a big wooden headframe (mine tower) visible from the causeway. *Mobs:* bats.
- *Critique:* tier 1 is everyone's first Depths experience; make it bright, cheerful and **obvious** (the ore field right in front of the arrival pad).

**Tier 2 — Copper (`copper_ore`), Mining 5: "Verdigris Cut"**
- *Standing there:* granite and stone walls streaked with copper, oxidised copper beams in four stages (cut copper → exposed → weathered → oxidized) as decoration, copper lanterns, a small copper-roofed foreman's hut.
- *Palette:* stone, granite, polished granite, all copper stages (waxed so they don't change), copper grates, copper bulbs (lit).
- *Depleted:* `granite`. *Landmark:* a giant oxidised copper drill head (built from copper blocks + stairs). *Mobs:* silverfish (harmless setting).

**Tier 3 — Iron (`iron_ore`), Mining 10: "The Ironworks"**
- *Standing there:* an industrial mine: iron bars, chains, anvils, a stone furnace stack, stone and diorite walls with heavy stone brick arches. Grey and cold, warmed by furnace light (lit blast furnaces, a few).
- *Palette:* stone, diorite, polished diorite, stone bricks, iron bars, chains, iron blocks (accents), lanterns.
- *Depleted:* `diorite`. *Landmark:* a huge chained bucket-wheel or pulley. *Mobs:* weak zombies.

**Tier 4 — Gold (`gold_ore`), Mining 15: "The Sunken Vault"**
- *Standing there:* the ruins of an ancient treasury in sandstone: cut sandstone columns, a collapsed vault door, gold block trims, chiseled sandstone glyphs, warm light from lanterns and hidden shroomlights.
- *Palette:* sandstone (all variants), smooth sandstone, gold block accents, orange terracotta, red sandstone details.
- *Depleted:* `smooth_sandstone`. *Landmark:* a giant cracked vault door. *Mobs:* husks.

**Tier 5 — Redstone (`redstone_ore`), Mining 20: "The Red Depths"**
- *Standing there:* first deepslate tier; darker, red-lit by **lit redstone lamps** (placed lit in the schematic, not powered), polished blackstone trims, red veins in the walls. Phoenix red finally appears underground; this tier feels "phoenix".
- *Palette:* deepslate, cobbled/polished deepslate, polished blackstone, redstone lamps (lit state), red nether brick accents, red stained glass over light.
- *Depleted:* `cobbled_deepslate`. *Landmark:* a giant "heart" of redstone lamps and blocks in the cavern wall. *Mobs:* skeletons.

**Tier 6 — Lapis (`lapis_ore`), Mining 25: "The Flooded Mine"**
- *Standing there:* blue-lit, partly flooded tunnels: water channels **beside** the field (never inside), prismarine and deepslate tiles, sea lanterns under water for cold blue light, kelp (decor). The field stays dry.
- *Palette:* deepslate tiles, prismarine/prismarine bricks/dark prismarine, sea lanterns, lapis accents, blue glass.
- *Water budget:* still water only, no flowing columns (max 20 flowing in the whole zone).
- *Depleted:* `deepslate_tiles`. *Landmark:* a sunken pump with a waterwheel. *Mobs:* drowned (in channels).

**Tier 7 — Diamond (`deepslate_diamond_ore`), Mining 30: "The Glittering Deep"**
- *Standing there:* pale calcite and tuff caverns, cool soul lanterns (blue is allowed here; it's deep and cold), dripstone stalactites, diamond specks catching light.
- *Palette:* deepslate, calcite, tuff, smooth basalt, dripstone, soul lanterns, light blue glass.
- *Depleted:* `tuff`. *Landmark:* a huge diamond-shaped crystal sculpture (light blue glass + calcite). *Mobs:* strays.

**Tier 8 — Emerald (`deepslate_emerald_ore`), Mining 35: "The Lush Hollow"**
- *Standing there:* a lush cave: moss carpets, azalea and flowering azalea, glow berries hanging (light), big dripleaf, spore blossoms, mossy cobblestone ruins.
- *Palette:* moss, mossy cobblestone, rooted dirt, clay, azalea leaves, glow lichen, deepslate.
- *Biome:* set to `lush_caves` for colors. *Depleted:* `mossy_cobblestone`. *Landmark:* an enormous azalea tree growing through the cave roof. *Mobs:* cave spiders.

**Tier 9 — Amethyst (`amethyst_block`), Mining 42: "The Geode"**
- *Standing there:* you are **inside a giant geode**: smooth basalt outer shell, calcite layer, amethyst walls, budding crystals and clusters glowing purple. Sound of chimes (code).
- *Palette:* smooth basalt, calcite, amethyst blocks, amethyst clusters, tinted glass.
- *Depleted:* `calcite`. *Landmark:* the geode itself (a spherical cavern ~60 across). *Mobs:* endermites.
- *Critique:* amethyst clusters are fragile decoration; keep them out of walking paths (players break them by accident only with tools, but they also block movement).

**Tier 10 — Quartz (`nether_quartz_ore`), Mining 50: "The Ember Rift"**
- *Standing there:* a rift into a nether-like world: netherrack, nether bricks, crimson stems, magma (behind glass or under slabs), shroomlight, smoke vents. Hot, red-orange, phoenix fire everywhere.
- *Palette:* netherrack, nether bricks, red nether bricks, crimson planks/stems, shroomlight, magma (never on walkable surfaces), blackstone.
- *Biome:* `crimson_forest` colors. *Depleted:* `netherrack`. *Landmark:* a giant phoenix skull carved into the rift wall. *Mobs:* zombified piglins (neutral).

**Tier 11 — Netherite (`ancient_debris`), Mining 58: "The Forge Heart"**
- *Standing there:* the deepest forge: blackstone and basalt pillars, lavafalls **behind glass** walls (contained, max 6 sources), a colossal anvil sculpture, chains as thick as trees. Dark, heavy, epic.
- *Palette:* blackstone, polished blackstone bricks, gilded blackstone (accents), basalt, smooth basalt, lava (behind glass), chains, iron bars.
- *Depleted:* `blackstone`. *Landmark:* the colossal anvil. *Mobs:* magma cubes.

**Tier 12 — Voidstone (`crying_obsidian`), Mining 66: "The Void Edge"**
- *Standing there:* the end of the world: the cave opens to the void and stars, end stone islands floating, purpur ruins, end rods for light, obsidian spikes. Silent and mysterious. The SkybornMC sky meets the phoenix world here.
- *Palette:* end stone, end stone bricks, purpur blocks/pillars, obsidian, crying obsidian (only in the field), end rods (accent light only), black stained glass.
- *Depleted:* `end_stone` (pale and matte, the opposite of dark, glowing crying obsidian; **not** obsidian, which looks almost the same and breaks the contrast rule in §9.2). *Landmark:* a huge floating obsidian ring. *Mobs:* endermen (neutral).
- *Critique:* open void edges: walls or glass at every edge players can reach. Nobody may fall off in the final tier.

### 9.4 Mine wing causeway
- 7 wide, walled (stone brick walls or the next tier's palette), lit every 8 blocks. The palette **transitions** gradually between tiers (stone → granite → diorite → sandstone → deepslate…) so the walk feels like descending deeper.
- Each tier gate stands at the zone entrance; when locked, the code shows a closed door and a message ("Mining 15 needed · 9/15"). The causeway beyond a locked gate is not visible (a bend or a wall), so locked tiers stay a surprise.

### 9.5 Deliverables and checklist (Mine)
- One schematic per tier: `depths_mine_t01_coal_vN.schem` … `t12_voidstone`, origin at the zone's north-west-bottom corner on a chunk corner.
- Metadata per tier: **field cuboid** (two corners, exact), gate opening (two corners), arrival pad, ambient mob spawn spots, tier sign spot, proposed depleted block.
- [ ] Field cuboid contains only ore + air; ≥ 300 exposed ore faces; no buried ore.
- [ ] No jumps needed inside the field (1-block steps only).
- [ ] Field light ≥ 8; gate opening exactly 5×5 and empty.
- [ ] Ceiling ≥ 12 above the field floor where covered.
- [ ] No hazards (lava/magma/water) on or next to the field.
- [ ] Biome set where colors matter (tier 8, 10).

---

## 10. The Depths: Forest wing (11 tiers)

### 10.1 How a forest tier works in code
- Each tier has a **log field**: a cuboid containing the tier's trees. Every **log** block inside is per-player: broken logs show a depleted look (see below) for that player and return after **10 s**.
- **Leaves are decoration and can't be broken** (code). Treefeller (custom enchant) breaks every log of *your* tree.
- The depleted look for logs: proposed **stripped log of the same wood** (it reads as "already chopped" and keeps the tree's shape so the tree doesn't look floating). Dev configures.
- Because the world is the Depths (fixed midnight) the forest is a **night forest under the stars**, lit by lanterns, glow berries, shroomlights, fireflies (code particles).

### 10.2 The forest tier template
- Zone about **80 × 80**, open sky (no cave), on a floating island at Y 100 with the §2.4 underside.
- **Log field:** ~40 × 40 with **16–24 trees** of the tier's wood, each tree **5–9 logs** on a trunk players can reach from the ground (**no logs higher than 5 blocks above the ground**: players must not need to tower up). Spacing 5–7 blocks between trunks so players walk between them and the camera doesn't fight leaves.
- **Tree shapes:** custom, hand-built, 2–3 shapes per tier, rotated. Leaves start at least 3 blocks above the ground so trunks are visible. Max crown radius 4.
- Ground: the tier's ground palette (WORLD.md §4.3), with paths between the trees (2–3 wide).
- **Leaves performance:** leaves are many blocks; keep crowns compact (not huge jungle domes), and set leaves `persistent` in the schematic (they won't decay; random tick in the Depths world is default, so this matters).
- Arrival pad, tier gate (5×5 opening), tier sign, 4–6 ambient spots (harmless mobs/animals, assumption: rabbits, foxes, frogs per biome; no hostile mobs in the forest).

### 10.3 The 11 forest tiers

| Tier | Tree | Theme name | Standing there | Ground & decoration | Biome to set | Light |
|---|---|---|---|---|---|---|
| 1 | Oak | **Hearthwood Glade** | A friendly glade, picnic tables, a woodcutter's hut, stacked logs | Grass, poppies/dandelions, fences, hay | `forest` | Lanterns on posts |
| 2 | Birch | **Silverbark Grove** | White trunks glowing in moonlight, fallen birch logs, a stream (still water) | Grass, lily of the valley, ferns | `birch_forest` | Lanterns + glow berries |
| 3 | Spruce | **Frostpine Hollow** | Snowy taiga at night, a log cabin with warm windows, sled props | Podzol, snow layers, ferns, sweet berry bushes (decor, out of paths: they hurt) | `snowy_taiga` | Campfires, lit windows |
| 4 | Jungle | **Emerald Canopy** | Dense green, vines, cocoa pods, a ruined temple corner, bamboo clumps | Jungle grass, vines, bamboo, melons | `jungle` | Glow berries, shroomlight hidden in crowns |
| 5 | Acacia | **Sunset Savanna** | Flat-top acacias, terracotta rock outcrops, a waterhole | Coarse dirt, grass, terracotta boulders | `savanna` | Lanterns, campfires |
| 6 | Dark Oak | **Duskwood** | Dark, mushroom-lit, giant brown/red mushrooms, a witch-free cottage | Podzol, mushrooms, moss | `dark_forest` | Dim: shroomlight in mushrooms; paths still ≥ 8 |
| 7 | Mangrove | **Rootmire** | Mangrove roots over shallow mud and water, boardwalks of mangrove planks | Mud, mangrove roots, shallow water (paths stay dry: boardwalks) | `mangrove_swamp` | Lanterns on boardwalk posts, frog-lights (froglights!) |
| 8 | Cherry | **Petalfall** | Pink canopy, petals on the ground, a small shrine, falling petal particles (code) | Grass, pink petals, moss | `cherry_grove` | Lanterns, candles |
| 9 | Pale Oak | **The Pale Garden** | Eerie silver-white forest, pale hanging moss, creaking-free (code: no creakings), silent | Pale moss, pale moss carpet, pale hanging moss | `pale_garden` | Cold: soul lanterns allowed here, faint |
| 10 | Crimson | **Crimson Wilds** | Nether-like red forest under stars, weeping vines, shroomlights, warm and phoenix-red | Crimson nylium, netherrack, shroomlights | `crimson_forest` | Shroomlights |
| 11 | Warped | **Warped Reach** | Teal alien forest, twisting vines, soul fire braziers, the eeriest tier | Warped nylium, warped wart blocks, soul soil | `warped_forest` | Soul fire / soul lanterns |

> Crimson and warped "trees" are huge fungi: their stems count as the logs. Keep stems ≤ 5 high to the first reachable block; wart blocks are the "leaves".

### 10.4 Critique (forest)
- The classic mistake is copy-pasted identical trees in a grid: it looks fake. Use 2–3 shapes, rotate, vary trunk height by ±1, vary spacing slightly.
- Leaves at head height on paths = camera hell on Bedrock. Leaves start ≥ 3 blocks up, paths clear.
- Night forests can be too dark to see logs. Light every trunk area to ≥ 8.
- Sweet berry bushes and dripleaf/water near paths hurt or trap players. Keep them as edge decoration.

### 10.5 Deliverables and checklist (Forest)
- `depths_forest_t01_oak_vN.schem` … `t11_warped`, origin on a chunk corner + metadata: log field cuboid, tree count, gate opening, arrival pad, ambient spots, biome.
- [ ] 16–24 trees per field, logs reachable from the ground (≤ 5 high).
- [ ] Leaves persistent; crowns compact; nothing at head height on paths.
- [ ] Light ≥ 8 around trunks; biome set correctly.
- [ ] Gate 5×5 empty; arrival pad safe.

---

## 11. The Depths: Combat wing (10 tiers) and the world boss arena

### 11.1 How a combat tier works in code
- **Shared mobs** (everyone sees the same mobs; visibility forced to "everyone"). Code spawns custom mobs per tier at marked **spawn points**, with spawn caps per zone, despawning when nobody is near.
- Each tier has a **mini-boss** spawning every X minutes on its **mini-boss pad** inside a small arena.
- **No creepers anywhere** (no block damage), **no PvP**. Tiers 1–3 are safe for new players.
- Mobs must not escape the zone: walls/fences around the fighting area; mobs can't path out through the gate (gate opening closed by code for locked players; for unlocked players the code also blocks mob pathing).

### 11.2 The combat tier template
- Zone about **80 × 80**, fighting arena about **50 × 50**, **flat or gently stepped** (mobs path badly on complex terrain; players need to kite and dodge).
- **Spawn points:** **8–12 per tier**, spread around the arena edges, on solid blocks with 3 air above (4 for tall mobs like ravagers: mark 4 air).
- **Cover:** a few obstacles (pillars, ruins, gravestones) for interest, but **no** places where mobs get stuck forever (1-block pits, fences that trap) and **no** safe-spots where players can hit mobs that can't reach them (players on a 2-high pillar farming zombies = exploit).
- **Mini-boss arena:** a circular or square sub-area **20–25 across** inside the zone, open to the main arena, with a clearly marked center pad (the boss spawns there) and a code hologram spot above (boss bar / timer).
- Arrival pad (safe, behind the gate, mobs can't reach it: a small walled or raised area with a slab-step), tier gate 5×5, tier sign.
- Light: the arena ≥ 8 (natural spawning is off anyway; light is for players). Mood lighting outside the arena.

### 11.3 The 10 combat tiers

**Tier 1 — Graveyard** (zombies; mini-boss **Grave Warden**, a big zombie)
Crooked gravestones (stone brick walls, andesite slabs), a chapel ruin, dead bushes, iron fences, lanterns on old posts, mist-like cobwebs only in corners (not on paths: they slow players). Palette: stone bricks (cracked/mossy), coarse dirt, podzol, dark oak, iron bars. Mini-boss arena: the open grave ring in front of the chapel.

**Tier 2 — Bone Crypt** (skeletons; **Crypt Archer**)
An underground crypt: bone blocks, polished basalt pillars, sarcophagi (stone + slabs), orange lanterns (phoenix warmth against the cold bone), candles in niches. Arena: the central burial hall; ranged mobs → give players pillars for cover.

**Tier 3 — Spider Den** (spiders, cave spiders; **Broodmother**)
A cave thick with cobweb **decoration on walls and ceilings only** (never on the floor: cobwebs freeze players and make the zone unplayable), egg sacs (white wool clumps), tuff and deepslate. Arena: a nest bowl.

**Tier 4 — Slime Bog** (slimes; **King Slime**)
A swamp: mud, clay, shallow water edges (fighting floor dry), lily pads, mangrove roots, slime blocks as decoration **not on the floor** (they bounce players). Arena: a dry island in the bog.

**Tier 5 — Sunken Ruins** (drowned; **Tide Caller**)
Partially flooded temple: prismarine, dark prismarine, sea lanterns, kelp in the water channels **around** a dry central arena (drowned come out of the water). Max 20 flowing water blocks; mostly still water.

**Tier 6 — Witch Hollow** (witches; **Coven Elder**)
Dark oak and spruce swamp village of crooked huts, cauldrons (decor), purple and green glass lanterns, mushrooms. Dark mood, lit paths. Arena: the ritual circle (candles in a ring, no fire).

**Tier 7 — Pillager Outpost** (pillagers, vindicators; **Raid Captain**)
A fortified wooden outpost: dark oak palisades, watchtower (decor, not climbable from the arena, so players don't escape mobs), pillager banners (the ominous banner pattern), cages. Arena: the courtyard.

**Tier 8 — Blaze Forge** (blazes; **Forge Lord**)
A nether fortress forge: nether bricks, lava behind glass, magma under slabs, iron bars, blackstone. Flying mobs: ceiling ≥ 15 high, open arena so players can see and hit blazes.

**Tier 9 — Wither Keep** (wither skeletons; **Bone King**)
A black fortress: blackstone, polished blackstone bricks, soul sand floor patches **only in decoration** (soul sand slows players), wither-rose-free (they damage players). Tall halls, red banners turned black and torn.

**Tier 10 — Warlord's Citadel** (ravagers, evokers; **Warlord**)
The grand finale before the world boss: a huge war citadel in blackstone, red nether brick and gold, phoenix banners captured by the enemy (black-red). Ravagers are big: arena ≥ 60 × 60, doorways ≥ 4 wide × 4 tall, no low ceilings. The causeway continues north to the **world boss arena**.

### 11.4 World boss arena (beyond tier 10)
- **Purpose:** scheduled world bosses (Iron Colossus, The Hollow King, Wyrm of the Deep; Phase 8), announced 5 minutes before, boss bar, rewards by damage share, top 3 extra. Up to **100+ players** at once.
- **Size:** a round arena **~100 across** (circle or octagon), flat main floor, a raised ring of spectator terraces/balconies around it, connected to the floor by **wide stairs** (fall damage is on in the Depths, so late arrivals must be able to walk down, not jump).
- **As if standing there:** a colossal ruined amphitheatre floating in the void, under the stars, ringed by twelve giant braziers (one per mine tier, small homage), with a central **boss pad** (5×5, marked). Pillars for cover at mid-range. The far side opens onto the void with a gigantic phoenix sculpture rising behind the arena, half in shadow: the "final boss" view.
- **Functional points:** boss spawn pad (center), 4 add-spawn points (for boss minions), arena boundary cuboid (code keeps boss and players inside), 4 entrance ramps, hologram spot for the damage leaderboard (top 10) near the entrance, a return portal to The Gate after the fight.
- **Performance:** 100 players + boss + particles: keep the arena **low on tile entities and leaves**, mostly solid blocks; braziers' fire is code particles + campfires (12 campfires ok).
- **Critique:** boss arenas on many servers are too small (players stack on one block) or too complex (players get stuck). Keep the floor flat, round, and wide.

### 11.5 Deliverables and checklist (Combat + boss)
- `depths_combat_t01_graveyard_vN.schem` … `t10_citadel`, `depths_boss_arena_vN.schem` + metadata: arena cuboid, 8–12 spawn points (with air height), mini-boss pad, gate opening, arrival pad, hologram spots.
- [ ] Flat/stepped arena, no stuck spots, no safe-spots to exploit.
- [ ] No cobwebs, slime, soul sand, berry bushes, magma or wither roses on the fighting floor.
- [ ] Mobs can't leave the zone; arrival pad unreachable for mobs.
- [ ] Ceiling ≥ 15 for flying-mob tiers; doorways ≥ 4×4 in tier 10.
- [ ] Boss arena ~100 across, flat, entrances wide, return portal present.

---

## 12. The Warzone: KOTH keep, envoy field, duel arenas

### 12.1 Purpose
The only PvP world. **KOTH** (capture the keep's courtyard for Conquest XP and rewards), **envoys** (scheduled supply drops across the field), **duel arenas** (`/duel` with wagers), plus free PvP. Fixed **dusk** (time 13000): orange-purple sky, long shadows, dramatic.

### 12.2 Layout
- World `warzone`, center (0, 100, 0), border 1000 (radius 500). Playable battlefield about **300 × 300** on one large floating landmass (assumption) with the §2.4 underside.
- **South:** the **safe zone** (no PvP, code region) with the arrival portal from spawn and the return portal, a small camp (tents of wool, banners).
- **Center:** the **ruined keep** (KOTH).
- **Around:** the **envoy field**: open plains with ruins, trenches, hills.
- **South-east:** **4 duel arenas** in a walled compound.

### 12.3 Safe zone (south)
**As if standing there:** you arrive in a fortified war camp: canvas tents (white/light gray wool on fences), a palisade, a field kitchen (campfire, barrels), a quartermaster stall (decor), red phoenix banners. A clear gate leads north onto the battlefield, with a big sign/hologram: "⚔ PvP beyond this point". The safe-zone edge is **visually obvious** (a line of red banners/fences on the ground) so nobody steps into PvP by accident.
**Functional:** arrival pad, return portal (`to_spawn`), safe-zone cuboid, a hologram spot for the next KOTH/envoy timer.

### 12.4 The KOTH keep (center)
**As if standing there:** a ruined castle keep on a low hill, 4 broken towers, collapsed walls of cracked stone bricks and cobblestone, burnt timber (stripped dark oak), campfires in braziers, torn banners. The **courtyard** in the middle is the **capture zone**: a square **15 × 15** (assumption) marked by a ring of polished blackstone in the floor and a central banner pole (code shows capture progress with a hologram + particles).
**Gameplay rules for the build:**
- **4 entrances** (north/east/south/west) to the courtyard, each 3–5 wide, so no single chokepoint can be camped by one team forever.
- Walls around the courtyard 4–6 high with a few **broken sections** and two ramps up to wall walks (high ground for archers, reachable from inside and outside).
- **No** 1×1 hiding holes, no blocks that let players see through walls (glass panes), no places to glitch into walls, no water (water stops fall damage and breaks KOTH fights), no ladders (pearl-and-ladder exploits).
- Cover inside the courtyard: 3–4 waist-high rubble piles.
**Palette:** cracked/mossy stone bricks, cobblestone and cobblestone walls, stripped dark oak (burnt), gravel and coarse dirt, campfires, black and red banners, chains.

### 12.5 Envoy field
- Open, rolling plains around the keep (grass, coarse dirt, gravel paths, a few dead trees, trenches, ruined farmhouses, a broken windmill).
- **Envoy drop spots: 20–30** marked flat 3×3 spots with **open sky above** (no roof, no leaves above), spread so no single spot is camped and players must move. Code picks random spots per envoy (beacon beam + firework).
- Sightlines: mix open areas (ranged fights) and cover (ruins), so no class dominates.

### 12.6 Duel arenas (south-east)
- **4 small arenas** (assumption: 30 × 30 each, identical layout so duels are fair), each a castle courtyard with 2 symmetric spawn pads (exact, facing each other ~20 blocks apart), low symmetric cover, walls 6 high, a spectator balcony.
- Each arena: two spawn pads, an arena cuboid (code), a spectator area separate from the fighting floor.
- **Symmetry is mandatory** (fairness).

### 12.7 Critique (warzone)
- PvP maps die when one spot is unbeatable. Test: can 3 players hold the courtyard against 10? They shouldn't for long.
- Bedrock players fight with touch: avoid parkour-only routes, 1-wide bridges and complex staircases in combat areas.
- Dusk lighting looks great, but shadows hide players: light combat areas to ≥ 8 with lanterns/campfires.

### 12.8 Deliverables and checklist (warzone)
- `warzone_battlefield_vN.schem` (origin 0,100,0) + metadata: safe zone cuboid, arrival pad, portals, KOTH capture cuboid, 20–30 envoy spots, 4 duel arenas × 2 spawn pads + cuboids, hologram spots.
- [ ] Safe-zone border obvious on the ground.
- [ ] KOTH courtyard 4 entrances, no water/ladders/glass/hiding holes.
- [ ] Envoy spots open to the sky, spread out.
- [ ] Duel arenas identical and symmetric.
- [ ] No fall-into-void without walls near the safe zone; edges of the battlefield walled or clearly dangerous (falling into the void = death in PvP; code handles it, but make edges visible).

---

## 13. Smaller builds: events, Hall of Fame, network hub, backdrops

### 13.1 Dungeons (`/dungeon`)
`/dungeon` is the teleport menu for **Depths tiers** (mine, forest, combat). There are **no separate dungeon builds**: the combat tiers *are* the dungeon zones, and "high-tier dungeon zones with leaderboards" (endgame) are combat tiers 8–10 plus the boss arena. If the owner later wants instanced dungeons (party runs through rooms), that is a new build type and needs its own brief. (§14 Q6)

### 13.2 Summonable bosses (Phase 8)
DESIGN mentions summonable bosses besides world bosses. Assumption: they are summoned on the **mini-boss pads** of combat tiers or in the world boss arena when no world boss is active. No separate build until the owner decides.

### 13.3 Event support (automatic events)
Events are automatic (treasure hunt, money event, farming event, mining rush, double XP). Most need **no build**, but two need marked spots:
- **Treasure hunt:** **40 hiding spots** spread over spawn (20) and The Gate + early Depths tiers (20): nooks, under stairs, behind waterfalls, on balconies. Each spot is a 1×1 position where code places a treasure chest/marker; reachable without parkour; listed in metadata (`treasure_spots:`).
- **Mining rush:** uses the Depths fields; a **"Rush" banner spot** at The Gate (hologram) is enough.
- **Envoys** use the warzone envoy spots (§12.5).
No drop parties or build contests (owner decision), so no event stage is needed.

### 13.4 Hall of Fame statues (each season)
After each season, builders add statues/banners for the winners in the Hall of Heroes (§5.8): a **10–12 block statue** of the winner's skin colors on the next free plinth, plus a season banner. Keep a consistent style: same pose family, same plinth, a plaque (sign) with name + season + island level.

### 13.5 Pre-season
The pre-season is one week of normal gameplay with boosts, then a wipe. It uses the **same builds**; no separate lobby. Before launch, spawn can show a **countdown hologram** in the courtyard (code).

### 13.6 SkybornMC network hub (before beta, later)
Before beta a **Velocity proxy + hub server** arrives (DESIGN §15). The hub is a separate build in **SkybornMC frost colors** (it's the network, not Phoenix):
- **Theme:** a floating city of ice and cloud above the sky: packed and blue ice, calcite, white concrete, light blue/blue glass, sea lanterns, soul lanterns, snow, with the logo's spiked steel-blue ring as a giant monument (the "S" plate, built from blue terracotta, light blue concrete and iron-bar spikes) on a cloud of white wool.
- **Function:** arrival plaza, a **gamemode selector** portal for Phoenix Skyblock (a fire-red portal arch: the only red in the hub, so it stands out), room for future gamemode portals, store/Discord/vote area, parkour (optional, not required), leaderboards for the network.
- Not part of the current phases; brief it in detail when the network work starts. (§14)

### 13.7 Vanilla Nether and End
Disabled. The owner plans a different future use as custom worlds (TBD). **Do not build anything for them yet.**

### 13.8 Sky backdrops per world
| World | Backdrop |
|---|---|
| `spawn` | Noon sky, cloud layers below (20–40 blocks down), 6–10 decor islets |
| `islands` / `outposts` | Nothing (players' islands are the backdrop; no decor allowed in slots) |
| `depths` | Midnight stars; a few distant floating rocks with hanging lanterns between wings for atmosphere; clouds lit warm by lanterns under The Gate |
| `warzone` | Dusk; a few burning distant ruins on far islets (campfires), smoke columns (code particles) |

World borders: per `worlds.yml` (spawn 1000, depths 2000 → see §4 for the needed change, warzone 1000). No build may come within 50 blocks of a border.

---

## 14. Open questions for the owner

Each has a default used in this brief. Answer only the ones you disagree with.

| # | Question | Default in this brief |
|---|---|---|
| Q1 | Add a **Forge Quarter** district at spawn (Quartermaster, Blacksmith, Enchanter) instead of putting them in the Market? | Yes, 7 areas around the courtyard |
| Q2 | Do players **choose a starting island style** (Meadow + Frost Cliff + Desert Mesa + Cherry Grove), or is there one starter island? | One (Ember Meadow); variants only if you say yes |
| Q3 | Island surface height **Y 100** (same as world spawn)? | Yes |
| Q-showcase | Store & Discord showcase: pavilion with mannequins only (default, 13 NPCs), or also a "Herald" NPC that opens the store/Discord menu (14 NPCs)? | Mannequins only |
| Q4 | Depths layout: wings **bend into a U** to fit the 2000 border, or **raise the border** to ~3,200 and keep straight wings? | U-shaped wings |
| Q5 | Should **The Gate** have its own Sell NPC and Forge NPC so players don't go back to spawn? | Yes |
| Q6 | Are **instanced party dungeons** wanted later, or are the combat tiers the dungeons? | Combat tiers are the dungeons |
| Q7 | KOTH capture zone size **15 × 15** with 4 entrances? | Yes |
| Q8 | Duel arenas: **4 identical** 30 × 30 arenas, or different themed maps per duel? | 4 identical |
| Q9 | Hall of Fame: mannequin NPCs first, real statues by builders after each season? | Yes |
| Q10 | Tavern name and district names (e.g. "The Burning Feather", "Bank of the Phoenix")? | Placeholder names in this brief; your call |
| Q11 | Forest ambient animals (rabbits, foxes, frogs) per biome, no hostile mobs in the forest? | Yes |
| Q12 | Network hub (frost theme) brief now or when the network work starts? | Later |

---

## 15. Master acceptance checklist

Applies to every build, on top of its own checklist:

- [ ] Delivered as `.schem` (correct origin, correct name) **plus** the metadata `.yml` with world coordinates of every functional point.
- [ ] Vanilla blocks only; looks the same on Java and Bedrock (tested on both).
- [ ] Phoenix palette respected (red ≈ 30% accent, never whole red walls); frost colors only where §2 allows.
- [ ] Paths ≥ 3 wide (main 5–7), no jumps/parkour/ladders on required routes, ceilings ≥ 4 (Depths caves ≥ 12 above fields).
- [ ] Light ≥ 8 on every walkable block (except deliberate dark decor with lit paths).
- [ ] No accidental falls into the void; no exploit spots (roof access, stuck holes, safe-spots, see-through walls in PvP).
- [ ] Spaces for code-placed things left empty and exact (NPC pads + 3×3 front, crate tops, gate openings 5×5, field cuboids clean, portal cuboids of air).
- [ ] Biomes set where colors matter (no gray void grass).
- [ ] Entities 0 without approval; tile entities and fluids within §3.4 budgets.
- [ ] Landmarks visible within 96 blocks (Bedrock render distance).
- [ ] `/spark` check with players present: no measurable TPS drop.
- [ ] Screenshots + walk-through notes sent with the delivery.

---

*Questions about this brief → the developer (and the owner for design decisions). Update this file when a build changes: it is the contract between builders and code.*
