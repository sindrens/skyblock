# The Depths v2: shared zones, tier layers, layered mobs

**Status:** plan v1 for the owner, 30 Oct 2026. Nothing is built yet.
**Decided by the owner:**
- **D1:** 5 zones per line plus boss areas, with tier palettes.
- **D2:** you can pick a lower tier, and every tier has its own rewards and amounts.
- **D3:** layered mobs from the start.
- **D4:** visibility setting.

**Source:** checked against update95 (`modules/depths`, live `depths.yml`, `zones.yml` and `mobs.yml`).
**Goes with:** feature card E1 (FEATURES.md). After the owner's answers, the developer turns this into milestones.

---

## 1. The idea in one paragraph
Each line (Mine, Forest, Combat) gets **5 big shared zones** instead of 10–12 small ones. Inside a zone, several tiers share the same place. **Each player sees their own tier:**
- In the mine you see coal where your friend sees iron, at the same blocks.
- In combat you see and fight graveyard zombies while your friend fights crypt skeletons in the same arena.

When you unlock the next tier, your blocks and mobs change in place. You can choose which players you see. Combat also gets a **dungeon boss hall** after zone 5, and there's one **main boss arena** for world bosses.

**Why it's better than today (33 small zones):**
- Zones feel alive: up to 3x more people in each.
- There's less to build: 15 zones instead of 33.
- Moving up a tier feels like an upgrade, not a walk to a new room.
- Friends on different tiers can still play side by side.

---

## 2. Zone map

### 2.1 Mine (12 tiers → 5 zones)
| Zone | Tiers inside (skill level needed) | Theme idea |
|---|---|---|
| M1 | Coal 0 · Copper 5 · Iron 10 | Old quarry (the built coal field) |
| M2 | Gold 15 · Redstone 20 · Lapis 25 | Deep veins |
| M3 | Diamond 30 · Emerald 35 | Crystal hollow |
| M4 | Amethyst 42 · Quartz 50 | Geode caverns |
| M5 | Netherite 58 · Voidstone 66 | The Abyss |

### 2.2 Forest (11 tiers → 5 zones)
| Zone | Tiers inside | Theme idea |
|---|---|---|
| F1 | Oak · Birch · Spruce | Old woods (built oak + birch fields) |
| F2 | Jungle · Acacia · Dark oak | Wildwood |
| F3 | Mangrove · Cherry | Blossom marsh |
| F4 | Pale oak · Crimson | Pale grove / red woods |
| F5 | Warped (a tier 12 can be added later) | The Warped reach |

### 2.3 Combat (10 tiers → 5 zones, 2 tiers each) + boss hall
| Zone | Tiers inside (Combat level) | Theme idea | Fits the mobs? |
|---|---|---|---|
| C1 | Graveyard 0 · Bone Crypt 6 | Barrows: graveyard above, crypt below (the two built arenas joined) | ✅ zombies + skeletons |
| C2 | Spider Den 12 · Slime Bog 18 | Rotting caves | ✅ |
| C3 | Sunken Ruins 24 · Witch Hollow 30 | Drowned swamp | ✅ |
| C4 | Pillager Outpost 38 · Blaze Forge 46 | War forge | 🟡 mixed theme |
| C5 | Wither Keep 55 · Warlord's Citadel 66 | The Citadel | ✅ |
| C+1 | **Dungeon boss hall** (after C5) | The Phoenix Vault | boss only |
| Main | **World boss arena** (Phase 8) | | world bosses |

- **Gates:** one gate per zone (15 instead of 33). It opens at the zone's first tier level. Higher tiers inside the zone unlock by skill level, with no gate.
- **The Gate hub** stays as the entry and gets a 15-zone map.

---

## 3. Tier layers (how two players see different things)

### 3.1 Blocks (mine and forest)
- **Today:**
  - The M4.3 engine keeps a personal state per player (your depleted blocks) and sends it with block-change packets.
  - It already shows different blocks to different players at one spot (tier gates show iron bars only to locked players).
- **New: a palette.**
  - Each ore or log spot is a **slot**. The real block in the world is the zone's first tier (coal, oak).
  - For each player the plugin shows the block of their **active tier**: coal → copper ore → iron ore, or the matching depleted look.
  - Tier-1 players need no extra packets.
- **Your tier changes** when you unlock a new one (auto) or pick a lower one (D2). All slot blocks near you swap in about 1 tick, with a title and a sound.
  - About 4 KB per swap on Java. Bedrock gets at most about 300 blocks per tick, to stay smooth.
- **Rewards per tier (D2):** the same slot gives the drops, amounts, skill XP and book/blueprint chances of **your** tier. Existing per-tier tables in `depths.yml` stay; the code reads "shown tier" instead of "real block".
- **Mining speed trap (must fix):** the client times breaking from the block it sees, but the server checks the real block.
  - The anti-fast-break check must use the shown block's hardness.
  - The real block should be the softest one in the palette.

### 3.2 Mobs (combat): layered mobs from the start (D3)
- Every combat zone runs **one mob population per tier** (a "layer"). A mob belongs to one layer.
- **You only see, hit and get hit by the mobs of your own layer.** Mobs of other layers aren't sent to your game at all (Paper: hidden by default, shown per player).
- What has to be handled, all server-side:

| Problem | Fix |
|---|---|
| A hidden mob targets someone on another layer | Targeting only allowed on the same layer (cancel the target event) |
| Arrows, fireballs, witch potions, evoker fangs | Every projectile or summoned effect gets its mob's layer and is shown only to that layer. Damage, potion effects and knockback on other layers are cancelled |
| Another player's arrow hits a hidden mob | The arrow passes through (cancel the hit) |
| Hidden mobs block movement | Combat mobs are set non-collidable for players |
| Mob sounds come "from nowhere" | Combat mobs are silent. Hurt and death sounds are played to their own layer only |
| Ravager roar, explosions | Knockback and damage are filtered by layer |
| Loot on the ground | None: drops and XP go straight to the killer (as today) |
| Kill stealing | Only on the same layer: the first hit tags the mob for 8 s |

- **Spawning per layer:** mobs alive = base 6 + 3 per player on that layer, max 40 per layer. Spawn speed grows with players too, because today's 2 mobs a second only feed about 16 players.
  - The global cap goes from 300 to about 450, to be tuned by a load test.
  - A layer with no players has no mobs (after 30 s, as today).
- **Mini-bosses:** one per layer, with its own 10-minute timer and a boss bar shown only to that layer.
  - Health +25% per extra eligible player, up to 10 (R3-10).
  - Eligible = 10% of the damage, hit in the last 90 s, not AFK, max 3 accounts per IP.
- **Groups:** friends on different tiers each fight their own mobs. To fight together, they pick the same tier: the lower one, via D2.

### 3.3 Picking your tier (D2)
- **Default:** your highest unlocked tier in that zone.
- **Switching:** one click in `/dungeon`, the zone's gate hologram or the zone menu.
- **New unlock while you stand in the zone:**
  - Mine and forest switch automatically.
  - Combat switches when you have been out of combat for 5 s, so your fight isn't cut off. You get a title: "Crypt unlocked · Switch now?" with a button.
- **Why lower tiers matter:** the Forge and blueprints still need low materials (e.g. 256 depths coal) after you move up.

---

## 4. Who you see (D4)
- **Setting:** Everyone (default) / Friends + island / Nobody, plus **pin a player** to always see them.
  - Pinning needs a button in the player menu, or a new command (owner's choice).
- **What you see of others:** you see the other **players**, never their blocks or mobs.
  - Example: a friend on iron, seen from coal, looks like he mines coal blocks. In combat he swings at mobs you can't see.
- **Staff** are always visible to players (vanished staff stay hidden).
- **Hidden players** are non-collidable, so they never block you.
- **One shared visibility service** for vanish, the AFK pool, the Depths and spawn. Today's vanish uses the same Paper calls, and two separate systems would unhide vanished staff by mistake.
- The setting is global, and saved per profile.

---

## 5. Critical review (what could go wrong)
1. **Combat layers are the riskiest part (large build).**
   - Every mob ability needs a layer filter: projectiles, potions, fangs, roar, explosions. Each new mob type in Phase 8 must be checked the same way.
   - **Mitigation:** one central `LayerGuard` that every combat entity passes through, plus a unit test that fails if a mob type has no layer handling.
2. **Players swinging at nothing.** With visibility on Everyone, others fight invisible mobs.
   - It's normal in games with phasing, and players learn fast.
   - Optional polish: a faint hit particle shown to everyone.
3. **Bedrock (Geyser).** Hidden entities and per-player blocks must translate cleanly. Bedrock also times block breaking itself.
   - Must be tested first in a short spike (§7 step 1). If Bedrock breaks, the fallback is that Bedrock players only see tier 1 of each zone, which is bad.
4. **Crowding.** 15 zones instead of 33 means 2–3x more players per zone.
   - Ore fields must be bigger.
   - Players are non-collidable.
   - The load test decides the mob caps.
5. **Theme mismatch** in shared combat zones (C4: pillagers + blazes). It could be fixed by a better tier order or a split arena (an outpost courtyard + a forge hall inside one zone). Only a builder question.
6. **Server cost.**
   - Hidden mobs still think and move on the server.
   - Total mobs stay capped (about 450), and the network cost drops, because nobody receives mobs they can't see.
   - The real cost is more avatars per zone, which visibility helps with.
7. **Tutorial and help.** New players must understand "you see your tier".
   - One help hologram per gate.
   - A first-visit title: "Everyone here sees their own tier".
8. **Leaderboards and skills** don't change: XP stays per tier.

---

## 6. What changes in the code (for the developer)
| Part | Change | Size |
|---|---|---|
| Palette engine | Slot → shown block per tier, `activeTier` per player+zone, per-chunk slot index, resend on change, hardness fix | M |
| Events and hooks | `ResourceBreakEvent.getShownType()/getTier()`; skills, drops, satchel, tools and ambient mobs read them | S–M |
| Zone groups | `zones.yml` schema with tiers per zone, 15 gates, `/dungeon` menu, unlock migration (`zone:tier` keys) | M |
| Tier selector | Auto + manual, combat 5 s rule | S |
| Layered mobs | Per-layer populations, `LayerGuard` (target, damage, projectile, potion, knockback, collision, sound), per-layer mini-boss, spawn scaling | L |
| Visibility service | One owner of hide/show for vanish, AFK pool, Depths, spawn; settings; pins | M |
| Builds | Bigger ore fields, joined combat arenas, layout generator for 15 zones + boss hall | L (builders) |
| Load test | About 80 bots in one zone (owner's OK first, as always) | S–M |

---

## 7. Build order
1. **Spike (2–3 days):** a palette on the coal field, plus 2 layers in the graveyard. Test with 2 Java players and 1 Bedrock player. **Stop and report if Bedrock fails.**
2. Palette engine and event changes (mine and forest).
3. Zone groups, gates and tier selector.
4. Visibility service.
5. Layered mobs and `LayerGuard`, plus per-layer mini-bosses.
6. Builders: C1 (join graveyard + crypt), M1 and F1 districts, then zones 2–5 and the boss hall.
7. Load test, tune the caps, then release.

**Builds that stay useful:**
- Coal field → M1 as-is.
- Copper and iron fields → M1 districts after a one-line block replace.
- Oak and birch → F1.
- Graveyard + crypt → C1.
- The Gate stays (new signs).

---

## 8. Questions for the owner (one tap each on the answers page)
| # | Question | Recommended |
|---|---|---|
| V1 | Combat: 2 tiers per zone (C1 Graveyard+Crypt … C5 Wither Keep+Citadel)? | Yes |
| V2 | Mine 3/3/2/2/2 tiers and Forest 3/3/2/2/1 tiers per zone? | Yes |
| V3 | "+1 dungeon for boss" = one dungeon boss hall after combat zone 5, and the "main boss area" = the world boss arena? | Yes |
| V4 | Gates: one per zone, opening at the zone's first tier level? | Yes |
| V5 | On unlock: mine/forest switch at once, combat after 5 s out of combat, with a "Switch now" button? | Yes |
| V6 | You see other players (by your setting) but never their mobs or blocks? | Yes |
| V7 | Mob numbers per layer: 6 + 3 per player, max 40; global cap about 450 after the load test? | Yes |
| V8 | Pin a player: a button in the player menu (no new command)? | Yes |
| V9 | Spike first (2–3 days, Java + Bedrock test) before the full build? | Yes |
| V10 | The boss hall: entry needs the C5 mini-boss killed once (key or unlock)? | Yes |
