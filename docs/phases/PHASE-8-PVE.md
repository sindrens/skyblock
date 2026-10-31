# Phase 8: PvE (mobs, bosses, pets)

**Goal:** fighting gets depth and big shared moments. Depths mobs get stats and abilities. **World bosses** spawn on a schedule in the boss arena, the whole server fights them, and everyone is paid by their **damage share**. **Summon bosses** are called with sigils made from Depths drops, for small groups. **Pets** hatch from eggs, level up while you play and give a small perk. Advanced fishing (sea creatures, lava fishing, contests) builds on Phase 7 basic fishing.

Depends on Phases 0–5 and the built combat pieces: Depths combat zones, mobs and mini-bosses (M4.8, `modules/depths` combat package), Combat skill (M4.5), swords and enchants (M4.2, E1–E5), wardrobe sets and loadouts (W1–W3), stacking (STACK), profiles (PROF), NPCs, holograms and leaderboards (NPCHOLO). Fishing extras need Phase 7 M7.8. Design: `DESIGN.md` §7, `WORLD.md` §4.4–4.5, `REVIEW.md` B6, `ECONOMY_BALANCE.md` §3.2, §9.3, §9.8, `plans/ENCHANTS.md` §3 (boss book rolls), §6.5, `plans/WARDROBE.md` §3 (set XP), §7.6 (pet hook), `PHASE-10-REWARDS.md` (pet egg in the Eclipse crate, collection log, quests), `PHASE-7-PRODUCTION.md` M7.8–M7.10.

**Every name, number and rule below is a default** the owner can change. Numbers go into typed configs, text into `messages/<module>.yml`. Unanswered decisions are in the QUESTIONS JSON at the end.

Owner decisions this plan follows:
- **DESIGN §7:** custom mobs with abilities by tier; world bosses and summonable bosses with a damage leaderboard and top-3 rewards; pets from a pet egg in the monthly crate that opens into a random rarity, level through use and give buffs.
- **FI 79** boss events yes. **FI 91** pets cosmetic + stat yes. **FI 80 / EV2** fishing contests every 4 h, 15 min. **FI 64** parties yes (not built yet).
- **130** pets are per profile. **132** profile switching blocked in the boss arena. **345** teleports have a 3 s delay in the boss arena. **377** `/ec` and `/craft` blocked in the boss arena (built). **#76** PvE death keeps the inventory and only adds a death stat.
- **S1** mobs never stack in The Depths or in boss fights. **REVIEW B6** combat zones and world bosses use shared mobs. **#43** shards come from bosses, envoys, KOTH and high Depths tiers. **REVIEW C** pet skins are cosmetics kept across seasons.
- **Income meta:** mini-boss and world boss rewards are **event income** (`ECONOMY_BALANCE.md` §3.2), inside the ~15% for events, never on top of The Depths.
- **Principles:** no waiting, nothing lost by accident, menus for everything (Bedrock), English only, short lores, V2 look.

---

## How the pieces fit (one job each)

| System | Job | Module |
|---|---|---|
| Mob framework | Stats, abilities, drop tables, elites for every custom mob | `common/mob` (API) + `modules/depths` (runtime, already owns mobs) |
| Boss core | Fight state, damage per profile, bands, anti-leech, payouts | `common/boss` (pure rules) + new `modules/bosses` |
| World bosses | Schedule, arena, 2–3 big bosses, server-wide fights | `modules/bosses` |
| Summon bosses | Fragments → sigils → small group fights in summon pits | `modules/bosses` |
| Boss leaderboards | Live fight board, fight report, season and weekly boards | `modules/bosses` + `modules/npcs` (new board source) |
| Pets | Eggs, hatching, levels, perks, follow entity, skins | new `modules/pets` (implements `common/pet/PetHook`, built) |
| Advanced fishing | Sea creatures, lava fishing, contests | `modules/fishing` (Phase 7) |

Modules never call each other directly. Bosses use `MobService` (registered by depths), `BonusService`, `EconomyService`, `BoxService`, `EnchantService`, `WardrobeService`, `SkillService` and `ProfileService` through `core.services()`.

---

## M8.1 Mob framework v2
Today `mobs.yml` has health, damage, speed, scale, XP, crystals and drops (M4.8). Phase 8 adds stats, abilities, shared drop tables and elites. The catalog parser moves to `common/mob` (pure, unit-tested) so bosses, summon bosses and sea creatures use the same format.

**Stats** (per mob, all optional):
| Stat | Meaning | Default |
|---|---|---|
| `health`, `damage`, `speed`, `scale`, `size` | as today | vanilla |
| `armor` | damage reduction against player hits, % | 0, max 60 |
| `knockback-resistance` | 0–1 | vanilla |
| `attack-cooldown` | ticks between melee hits | vanilla |
| `follow-range` | blocks | 16 |
| `abilities` | list of `{id, cooldown, chance, params}` | none |
| `drop-table` | id of a shared table (or inline `drops` as today) | – |
| `elite-chance` | chance to spawn as an elite | 0 |

**Abilities** are a small fixed list in code, tuned in config. Every ability is **telegraphed for 1 s** before it hits (see Bedrock-safe visuals), so a careful player can always dodge.
| Ability | What it does |
|---|---|
| `slam` | Ground ring around the mob; damage + knockback to players inside |
| `leap` | Jumps at the farthest player in range |
| `charge` | Runs in a straight line; players in the path take damage |
| `volley` | 3–8 projectiles in a fan |
| `pull` | Pulls players within range toward the mob |
| `summon` | Spawns adds from the zone's mob list (capped per mob) |
| `shield` | Takes 80% less damage for a few seconds (shown on the name) |
| `ring` | Fire or soul ring on the ground for a few seconds |
| `curse` | Short slowness, weakness or wither on players hit |
| `teleport` | Teleports behind its target |
| `enrage` | Below X% health: faster attacks and +damage |

- **Tiers 1–3 have no abilities** (WORLD §4.4: safe for new players). Tiers 4–10: one ability per mob type. Mini-bosses: two. Bosses: three to six by phase.
- **Elites:** 2% of tier 4+ mobs spawn as elites: 2× health, +1 ability, 3× drops, a `★` before the name. Elites are counted in the drop budget below, so they add excitement, not income.
- **Drop tables** (`mobs.yml → drop-tables`, a list catalog): shared rolls `{item, min, max, chance}`. Rare rolls (summon fragments, key fragments, pet eggs, blueprints) sit in a separate `rare-drops` table per tier band. Book drops stay in the enchants module (E2).
- **Drop budget test** (`DropBudgetTest`): loads the bundled `mobs.yml` and `sell.yml` and fails the build if a tier's average money per kill (elites included) drifts more than ±12% from `ECONOMY_BALANCE.md` §9.9, or if rare drops are worth more than 5% of that tier's `REF` per played hour.
- **Kill credit** in shared zones: the profile that dealt the **most damage** gets the kill rewards, not the last hitter (stops kill stealing). Assists get nothing (normal mobs die in ~2.5 s).
- **Ability scheduler:** one timing wheel for all mobs (like the block engine), abilities only fire while a player is within 24 blocks. No task per mob.

**As built (M8-1, 31 Oct):** the parser lives in `common/mob` (`MobCatalogParser`, `MobDef`, `MobStats`, `AbilityId`, `AbilitySpec`, `EliteRules`, `AbilityClock`, `StrikeQueue`, `MobCombatMath`), the runtime in `modules/depths` (`combat/MobAbilities`, wired in `CombatZones`/`MobFactory`). Every M8.1 key is optional; an M4.8 `mobs.yml` reads unchanged and the startup upgrade adds no new section to it. New `mobs.yml` parts: `drop-tables` (list catalog, merged before a mob's inline `drops`), `ability-defaults` (per ability: cooldown, chance and settings; the code holds the same defaults), per mob `armor`, `knockback-resistance`, `attack-cooldown`, `follow-range`, `drop-table`, `elite-chance`, `abilities`. Settings in `depths.yml → combat.abilities.*` and `combat.elite.*`; texts in `messages/depths.yml → combat.ability.*`, `combat.elite.name`. One shared scheduler (`combat.abilities.tick-ticks`) with per-mob cooldown clocks and a capped effect queue (`max-active`); players are only looked up in the mob's own zone. Not built here (waiting for PV1 / a balance pass): most-damage kill credit, `rare-drops`, `DropBudgetTest`. Open points: `OPEN_QUESTIONS.md` → M8-1.

## M8.2 Mini-bosses for real
The ten mini-bosses (Grave Warden … Warlord) exist as placeholders (15× health, glowing, boss bar).
- Each gets **two abilities** from its theme (Broodmother: `summon` spiders + `curse` slowness; Forge Lord: `volley` + `ring`; Warlord: `charge` + `slam`), set in `mobs.yml`.
- **Leashed** to the build's `miniboss_arena` region (builder files ask for this): outside it, the mini-boss walks back and heals 5%/s until it is inside.
- Rewards stay as built: full rewards for every profile with ≥ 10% damage (OPEN_QUESTIONS M4.8). New: **1–2 summon fragments** of its tier band for each of them, and the ENCHANTS §3 mini-boss book roll goes live.
- Spawn timer stays 10 min after the last death, only while players are in the zone.

**As built (M8-2, 31 Oct):** all in `modules/depths` (combat zones untouched as simple tier areas, no zone restructuring).
- **Abilities** in `mobs.yml` per mini-boss: two attacks from its theme + `enrage` below 30% (faster, harder hits). Grave Warden slam + summon, Crypt Archer volley + teleport, Broodmother summon + curse (slowness), King Slime slam + leap, Tide Caller pull + volley, Coven Elder curse (weakness) + teleport, Raid Captain charge + summon, Forge Lord volley + fire ring, Bone King curse (wither) + shield, Warlord charge + slam. Tiers 1-3 are gentle (hits at most 1x the boss's own damage, enrage +25%). Every attack keeps the M8.1 1 s warning. Switch: `depths.yml → combat.mini-boss.abilities` (default `true`; off = no attacks and no enrage, everything else stays).
- **Live server:** a one-time, logged and audited (`config/migrate`) text upgrade of `mobs.yml` (`MiniBossUpgrade`) replaces only mini-boss lines that are exactly as M4.8 shipped them (spacing ignored) with the new lines; an edited line is left alone and logged. The rest of the file stays byte for byte the same (a YAML save would reformat it), the old file is kept as `mobs.yml.before-m8-2`, and `applied-upgrades: [m8-2-mini-boss-abilities]` at the end makes it run once. Chosen over the switch alone because the switch can't add config to a live file, and over a YAML rewrite because nothing the owner wrote changes.
- **Phase:** `MiniBossPhases` (normal → enraged, never back; same rule as the enrage ability, health scaling keeps the share). The boss bar gets `[Enraged]` and turns `combat.mini-boss.enraged-bar-color` (purple).
- **Leash:** a ring of `combat.mini-boss.leash-radius` (11, like the builders' `miniboss_arena`) around its spawn spot (`MiniBossLeash`). Pulled out, it drops its target, walks home (`leash-walk-speed`), heals `leash-heal-percent` (5%/s), starts no ability and picks no new target until it is back in the inner half; stuck for `leash-teleport-seconds` (10) it is put back home. Players near it get one action bar line. It heals only when a player pulled or kited it out, not when its own leap, charge or teleport carried it out. One shared task (`leash-check-ticks`, 10) for every live mini-boss; not checked while its own ability runs. Leaving the zone sends it home, not to a random spot.
- **Announcements:** chat line (as before) + a short title (`combat.mini-boss.spawn-title`, ≤ 20 / 36 characters) for players in the zone; the boss bar as built (R3-10 health and scaling).
- **Rewards as built:** R3-10 eligibility (10%, 90 s, not AFK, 3 per IP), every eligible player gets the full reward once, E2 mini-boss book roll (30%) unchanged. **Not built:** summon fragments (no fragment item until M8.6). Open points: `OPEN_QUESTIONS.md` → M8-2.

## M8.3 Boss core (shared by world and summon bosses)
One state machine, pure and unit-tested in `common/boss`:

`SCHEDULED → ANNOUNCED (5 min) → OPEN (arena open, 60 s join window) → ACTIVE (phase 1 → 2 → 3) → KILLED | ESCAPED | ABORTED → PAYOUT → CLOSED`

- **Damage ledger per profile** (`ProfileService.profileKey`), like `DamageShares` today. Counted: the player's melee hits, their own projectiles and their sword enchant effects (Ember Strike, Cleave, Phoenix Wrath). Not counted: environment, other mobs, damage from outside the arena region. **Per-hit cap:** 5% of the boss's max health (stops bugs and exploits).
- **Phases** at 66% and 33% health. Each phase adds an ability or adds; phase 3 enrages.
- **Contribution bands** (relative to the average, so they work for 5 or 150 fighters):
  `average = total damage ÷ fighters`
  | Band | Needs |
  |---|---|
  | Top 3 | the three highest damage dealers |
  | Gold | ≥ 150% of the average |
  | Silver | ≥ 50% of the average |
  | Bronze | ≥ 10% of the average |
  | none | below Bronze, or failed the anti-leech checks |
- **Anti-leech** (all must pass for any reward):
  1. In the arena when the boss dies (or died in the last 30 s and respawned there).
  2. At least one counted hit in the last 90 s of the fight.
  3. Not AFK (afk module detection) during the last 2 minutes.
  4. Bronze or better.
  5. One reward per account per fight; at most **3 accounts per IP** get rewards (same rule as votes and the AFK pool).
  6. Can't hit from outside: flying, elytra, ender pearls, wind charges and riptide are off in arenas; players outside the arena region deal no damage. Arenas have no high ground (builder brief).
- **Death in a fight:** keep inventory (#76), respawn at the arena spawn, damage is kept.
- **Escape:** if the boss is not dead after the time limit it leaves. Nobody gets the top rewards; fighters who passed the checks get the Bronze reward at half value. Phase progress is shown, so the server learns how big the next fight is.
- **Payout is atomic and idempotent:** one transaction per player (ledger + `/box` items + reward row) keyed by `(fight_id, profile_key)`. A crash mid-payout never pays twice; on restart, unpaid rows are paid. Offline winners get their rewards in `/box` (principle 3).
- **Fight report:** chat summary on Java (top 5 with hover), a **Fight report** menu for everyone (Bedrock), and the last 10 fights in `/bosses`.

## M8.4 World bosses
**The arena:** the built `boss-arena` zone in the `depths` world (`zones.yml`, its own island, portal from Combat tier 10). Phase 8 changes its rules:
- **Open only during a fight** (from the announcement to the end). Outside fights the portal and menu show the next boss and its time.
- **Entry:** **Combat level 12** (Spider Den), through the `/bosses` menu Teleport button, the event board at The Gate and the tier 10 portal. Teleport is instant (3 s if combat tagged, answer 345). Profile switching is blocked inside (132).
- Builder brief: flat 100×100 floor, no high ground, 4 entry pads, space for adds, safe spectator balcony outside the arena region.

**Schedule:** every **2 hours from 12:00 to 00:00 Europe/Oslo** (7 fights a day), in `bosses.yml → schedule` as times. The boss rotates. A small `EventSchedule` in `common` runs it (Phase 9 events reuse it).
- **5 min before:** chat line with a `[Teleport]` button (Java), title for players with "Event sounds" on, boss bar countdown, the Gate event board hologram (`event_timer_board`, built point) and the Discord sink.
- **Health** is set when the join window closes: `target seconds (300) × sum of each fighter's expected DPS` (`bosses.yml → dps-by-sword-tier`, from the sword tier they hold), min 10k, max 2M. Players who join later don't change it.
- **Time limit:** 10 minutes, then the boss escapes.

**Bosses** (WORLD §4.5):
| Boss | Body (vanilla, scale) | Phase 1 | Phase 2 (66%) | Phase 3 (33%) |
|---|---|---|---|---|
| **Iron Colossus** | iron golem, 3.0 | `slam`, `charge` | `volley` (boulders), `summon` iron sentries (×6) | `enrage`, `slam` every 6 s |
| **The Hollow King** | wither skeleton, 2.5 | `ring` (soul fire), `curse` wither | `summon` skeleton guards (×8), `shield` | `teleport` strikes, `pull` |
| **Wyrm of the Deep** | custom model (resource pack) | after the pack (M8.13) | | |

- Adds are capped at 30 alive per fight. Bosses never leave the arena (leash) and are immune to knockback, fire, potions and fall.
- Each boss has its own drop table and **two boss-only blueprints** (Phase 4 M4.2 d: blueprints from bosses), abilities from the small list in `tools.yml`.

**Rewards** (per fight, money in minutes of `REF` at the **player's own** best Depths tier, so it feels the same on day 1 and in month 3):
| Band | Money | Crystals | Shards | Books (ENCHANTS §3) | Set XP | Extra |
|---|---|---|---|---|---|---|
| #1 | Gold + 10 min | Gold × 2 | 20 | Legendary (3% Mythic) | 1,500 | 3% boss blueprint, 2% pet egg |
| #2 / #3 | Gold + 7 / + 5 min | Gold × 1.5 | 15 / 10 | Epic | 1,500 | 3% blueprint, 2% pet egg |
| Gold | 10 min | 300 × 1.3^(t−1) | 5 | 20% Rare+ | 1,500 | 1% blueprint, 1% pet egg |
| Silver | 6 min | 180 × 1.3^(t−1) | 3 | 20% Rare+ | 1,000 | 0.5% blueprint |
| Bronze | 3 min | 90 × 1.3^(t−1) | 1 | 20% Rare+ | 500 | – |

- Shards follow `ECONOMY_BALANCE.md` §9.3 (top 3: 20 / 15 / 10); "everyone else with ≥ 1% damage: 3" becomes the bands above.
- ENCHANTS "everyone else with ≥ 1% damage" means Bronze or better. WARDROBE "≥ 5% damage: 1,500 set XP" becomes the band column above.
- Combat skill XP: Gold 3×, Silver 2×, Bronze 1× the player's current combat tier mini-boss XP. Combat pillar XP for the island by the same rule (DESIGN: bosses by damage share).
- The final hit grants the existing **Titan Breaker** achievement (`boss_slayer`).

## M8.5 Damage leaderboards
- **During a fight:** a boss place scoreboard (SB per-place scoreboards, new place `boss`): boss name, health %, phase, time left, **top 3 damage**, your rank, your share and band. Updated once per second. Boss bar = boss health.
- **After a fight:** chat summary and the Fight report menu (all fighters, damage, band, reward).
- **Boss points** (season and weekly): each fight gives every rewarded fighter `100 × their share of the total damage`, top 3 +10 / +6 / +3. Summed per profile; boards use the player's **best profile** (P7).
- **Hall of Heroes:** the built `boss_damage` board ("Coming soon" today) gets a new source `boss points [season|this_week]`. The weekly board cycles with the other weekly boards (PG28).
- **`/bosses` Leaderboards tab:** season and weekly boss points, most world boss kills, and per summon boss the **fastest kill** (group) and **best damage in one fight**.
- Season #1 of boss points is a candidate for the Flameheart rank (`RANKS_TITLES_ACHIEVEMENTS.md`, already listed).

## M8.6 Summon bosses
Small group bosses players call themselves. One per two combat tiers.
| Boss | Tier band | Needs | Body | Fragment (drops in) |
|---|---|---|---|---|
| **The Rotfather** | 1–2 | Combat 6 | zombie, 2.2 | Grave Fragment (Graveyard, Bone Crypt) |
| **The Bog Matriarch** | 3–4 | Combat 18 | spider, 2.5 (summons slimes) | Bog Fragment (Spider Den, Slime Bog) |
| **The Tide Witch** | 5–6 | Combat 30 | drowned, 2.0 | Tide Fragment (Sunken Ruins, Witch Hollow) |
| **The Pyre Marshal** | 7–8 | Combat 46 | vindicator, 2.0 (blaze adds) | Pyre Fragment (Pillager Outpost, Blaze Forge) |
| **The Ashen Tyrant** | 9–10 | Combat 66 | wither skeleton, 2.8 (ravager phase) | Ashen Fragment (Wither Keep, Citadel) |

- **Fragments:** 1 in 150 kills of that band's mobs (≈ 3 per played hour of combat), plus 1–2 from each mini-boss reward. Fragments go to the satchel as custom items, sellable and tradeable.
- **Sigils:** 8 fragments + money (`REF` of the band's top tier × 0.25 h) at the **Summoner NPC** in The Gate. A sigil is a unique item (PDC id + UUID, dupe detection from M0.7), tradeable on `/trade` and the AH.
- **Summon pits:** 6 small arenas (25×25) next to the world boss arena (`zones.yml → summon-pits`, builder brief). Right-click a sigil or use `/bosses` → Summon: the first free pit is reserved for your group. All busy: you get a queue place and a ping when it's yours (queue is short: fights last ~2 min).
- **Group:** the summoner plus up to **4 players** picked in the summon menu (online island members first, then any online player who accepts). When Parties (FI 64) exist, the party is used.
- **Fight:** health = `120 s × sum of the group's expected DPS`, 5 min time limit, one or two phases, adds capped at 10.
- **Rewards:** every group member with ≥ 10% damage gets the full table (like mini-bosses): money 8 min of `REF` at the **boss's** band (low bosses stay small for late players), crystals, a Combat book roll (50% chance, one band better than the tier), set XP 600, 0.5% boss blueprint, 0.5% pet egg; Pyre Marshal and Ashen Tyrant also give 1 shard each.
- **Failure** (time limit, the group dies or leaves): the sigil is used up. **Crash, restart or staff stop:** the sigil goes back to the summoner's `/box`.
- A player can be in one fight at a time: the summon menu refuses while you are in the world boss arena, and pits are outside the arena region.

## M8.7 Pets (core)
**Getting a pet:**
- A **Mystery Pet Egg** (Eclipse crate 8%, built table; world and summon bosses, see rewards; Black Market rotation) opens into a **rarity egg**: Common 50%, Uncommon 28%, Rare 14%, Epic 6%, Legendary 2%. The rarity egg hatches into a random species of the pool. **Hatching is instant** (title, sound, firework), no timers (principle 1).
- Eggs are unique items and **tradeable**. A hatched pet is **bound to the profile** (per profile, answer 130) and lives in the `/pets` menu, not in the inventory.
- Max 30 pets per profile (2 menu pages). A pet can be **released** (two-step confirm, audited) to free space.

**Species** (every species exists in every rarity; the Phoenix Chick is Legendary only):
| Pet | Body | Works in | Main perk (family) |
|---|---|---|---|
| Ember Fox | fox | Depths mine | extra drops |
| Geode Armadillo | armadillo | Depths mine | crystals |
| Rockhopper Frog | frog | Depths mine | mining speed (speed) |
| Sap Bee | bee | Depths forest | extra drops |
| Timber Wolf | baby wolf | Depths forest | chopping speed (speed) |
| Canopy Parrot | parrot | Depths forest | rare finds |
| Ash Wolf | baby wolf | combat | mob damage |
| Ironhide Turtle | baby turtle | combat | damage reduction (mobs only) |
| Magma Imp | small magma cube | combat | boss damage (mob damage, bosses only) |
| Harvest Hen | baby chicken | farming | extra drops (crops) |
| Seed Sniffer | baby sniffer | farming | rare finds (Golden Seeds) |
| Tide Axolotl | axolotl | fishing | fishing speed (speed) |
| Lure Frog | frog | fishing | rare finds (rare fish) |
| Coin Allay | allay | anywhere | sell (half strength) |
| Phoenix Chick | baby chicken (red) | anywhere | skill XP, all skills |

**Perks and levels:**
- **One active pet.** Its main perk at level `L` = rarity max × `L ÷ 100`. Rarity max: Common 3%, Uncommon 5%, Rare 8%, Epic 11%, Legendary 15% (Coin Allay half: max +7.5% sell).
- **Epic and Legendary** add a second perk: +5% / +8% skill XP for that activity.
- Perks feed the existing **bonus families** through `BonusService`, so every cap holds (+300% sell, +200% drops, ...). Even a Legendary Lv 100 pet uses at most 7.5% of a family cap.
- **Pets never fight**, take damage or block anything. They give perks only.
- **Levels 1–100.** Pet XP = 1 per action in its activity (Depths block or log 1, combat kill 4, mature crop 0.5, fishing catch 6), 25% from other activities. XP for a level = `121 × 1.04^(L−1)`. Reference for a regular: Lv 10 in ~35 min, Lv 50 in ~7 h, **Lv 100 in ~60 active hours** of its activity (fast start, slower later, #61).
- Lower rarities level faster (Common needs 50% of the XP, Uncommon 65%, Rare 80%, Epic 90%).
- Offline production, AFK pool and spawner grinders give **no** pet XP (same rule as tool and set XP).
- **Pet Treats** (calendar, battle pass, crates): +500 / +2,500 / +10,000 pet XP on the active pet, bound.

**Follow entity (Bedrock-safe):**
- The active pet is a **small vanilla mob** (its body, `scale` 0.5) with no AI, invulnerable, silent, no collision, not saved to the world. It floats 1.5 blocks behind the owner's shoulder, so it is never in the owner's line of attack.
- **Inside combat zones, summon pits and the boss arena, other players' pets are hidden** (`Player#hideEntity`), so a pet never takes a hit meant for a mob.
- `/settings` toggle **Show other players' pets** (default on outside fights). Staff in vanish or staff mode have no visible pet.
- Removed on world change, death, teleport and profile switch, then spawned again at the new spot.

**Loadouts:** `modules/pets` registers its `PetHook`, so the built loadout pet slot starts working (summon on apply, "keep" stays as is).

## M8.8 Pet skins, collection and achievements
- **Pet skins:** cosmetic only, account-wide, kept across seasons (REVIEW C). A skin changes the body or color (Ember Fox → Snow Fox, Ash Wolf → Phoenix Wolf, resource pack models later). From gems (Phase 11), crates and the monthly Eclipse theme. Applied in the pet's menu.
- **Collection log** Pets page (PHASE-10: "Pets (Phase 8)"): every species × rarity found. Bosses page: each world and summon boss killed, boss blueprints.
- **Achievements** (`RANKS_TITLES_ACHIEVEMENTS.md`): first pet, 10 species, a Legendary pet, Lv 100 pet; first world boss, 10 Gold bands, every summon boss, Titan Breaker (exists).
- **Quests:** the PHASE-10 quest type "Deal damage to the world boss" switches on, plus "Kill a summon boss" and "Level your pet".

## M8.9 Advanced fishing (after Phase 7 M7.8)
- **Sea creatures:** from Fishing 15, 1 in 25 catches (custom rod only) pulls up a sea creature instead of loot. 8 creatures by fishing tier band, built with the mob framework (stats, abilities, drop tables): Bog Lurker, Reef Crawler, Ember Eel, Abyssal Guardian … They give Fishing XP + Combat XP and drop fishing-only items (Tide Chest keys for E6, fish scales for rods). A sea creature belongs to the angler for 10 s (kill credit), then to anyone. Max 3 alive per angler, despawn after 60 s.
- **Lava fishing:** in Emberdeep outpost lava and marked spawn lava ponds (build region kind `lava_fishing`), from Fishing 30 with a lava-tier rod (Forge, Phase 7 rods). 5 lava fish (Cinderfin, Magma Ray, Obsidian Pike, Blaze Koi, Phoenix Scale) at 1.2× the fishing value of the same tier.
- **Fishing contests** (FI 80, EV2 decided: every 4 h, 15 min): scored by **points per catch by rarity** (common 1, uncommon 2, rare 5, epic 15, legendary 50), anti-AFK rules from M7.8 apply. Top 3: Blaze / Cinder / Ember key; everyone with 10+ points: a small crystal pouch. Boss bar timer, contest scoreboard place, results in chat and `/fish` menu (Phase 7).
- Sea creatures and contests feed the Fishing pages of the collection log and quests.

## M8.10 Hooks into other systems
| System | What Phase 8 switches on |
|---|---|
| Outposts (M7.9) | **Wildwood** unlock adds "any island member got Bronze or better in a world boss kill" (DESIGN 3.7). Islands that already unlocked it with Combat pillar 30 keep it |
| Biomes (M7.10) | **Soul Sand Valley** effect = summon fragment chance for **player kills** of spawner mobs on that outpost (+1 in 2,000 per biome tier), max 5 fragments per island per day |
| Crates (PHASE-10) | Eclipse pet egg hatches; Pet Treats in calendar/pass tables |
| Black Market (M10.11) | Mystery Pet Egg (120 shards) and a random sigil (40 shards) in the rotation |
| Island pillars (M5.1) | Combat pillar XP from boss bands |
| Island stats (M2.6) | `bosses killed` (built category) counts world boss Bronze+ and summon kills |
| Enchants (E2) | Boss book rolls go from stub to live |
| Wardrobe (W1) | Set XP from bosses by band |
| HUD (SB) | New scoreboard places `boss` and `summon` |
| Discord (P3 sink) | World boss announcement and top 3 result |
| Profiles | `ProfileSwitchEvent` refused in the arena and summon pits |

## M8.11 Balance pass (BAL8)
Before any Phase 8 reward goes live, `tools/economy_model.py` and `ECONOMY_BALANCE.md` get a boss section:
- World boss attendance per archetype: casual 0.5, regular 1.5, grinder 4 fights per day (Silver average, Gold for the grinder).
- Summon bosses from fragments: one sigil per ~2.5 played hours of combat.
- Elites and rare drops inside the per-kill parity (DropBudgetTest).
- Pets in the families they touch (§9.8 table gets a "max from a pet" column).
- **Rules the model checks:**
  1. World + summon + mini-bosses together pay **≤ 6%** of a regular's income (inside the ~15% event share).
  2. A summon fight pays **≤ 1.3×** the Depths combat income of the time it took to farm its fragments (sigils are a bonus, not a new main income).
  3. Shard supply from bosses stays inside §9.3 (≈ 1–3 per regular per day).
  4. No family cap is reached by enchants + set + pet alone.

## M8.12 Staff tools and dashboard
- `/boss start <boss>` and `/boss stop` (admin, audited, needs approval): start a test fight or end a broken one (stop = ABORTED, sigils refunded, no rewards).
- `/boss give <player> <sigil> [amount]` and `/pet give <player> <species|egg> [rarity]` (admin + owner, audited, overflow to `/box`).
- `/pet view <player>` (mods): pets, levels, active pet, hatch history.
- M3.8 dashboard rows: boss money per day (`boss_reward` ledger reason), shards created per source, sigils made and used, pet eggs hatched by rarity, average fighters per world boss, escapes.
- Fight log in `/logs`: every fight with all damage rows (helps judge leech and exploit reports).

## M8.13 Wyrm of the Deep (after the resource pack)
- Third world boss with a custom model (item display model on an invisible base mob, Java; Geyser custom entity mapping or a scaled vanilla fallback for Bedrock). Waits for RP1 and the owner's art. Until then the rotation is Colossus and Hollow King.

---

## Bedrock-safe visuals
| Need | How (Java and Bedrock) |
|---|---|
| Boss health | Boss bar (short title ≤ 30 characters) |
| Telegraphs | **Fake block ring** (red concrete / magma, `sendBlockChange` to players within 32 blocks) for 1 s + sound + action bar `» Slam! Move!`. Particles (flame, dust, soul fire flame) only as extra |
| Big mobs | `scale` attribute (already used by mini-bosses; owner checks it on Bedrock) |
| Elite / boss marker | Name always visible with `★` and the V2 rarity color; **no glow outline needed** |
| Damage numbers | Not shown (TextDisplays are not reliable on Bedrock); the scoreboard shows your damage and rank |
| Announcements | Chat line, title ≤ 20 / subtitle ≤ 36 characters, boss bar countdown |
| Choices | Chest menus only (summon group, fight report, pets). No clickable chat needed |
| Pets | Real small vanilla mobs (Geyser shows them), no display entities |

## Limits and performance (300 players)
Target from `CONTEXT.md` §4: **20 TPS, MSPT < 40.** Budget: **≤ 4 ms per second** of main-thread time for mobs, abilities and pets outside fights; **≤ 8 ms per second** during a world boss fight with 150 fighters.
- **World boss:** 1 boss + max 30 adds. Damage ledger is a hash map (O(1) per hit). The top list is sorted once per second. Scoreboards update once per second, the boss bar at most 4 times per second. Ability effects go only to players within 48 blocks; fake block rings ≤ 64 blocks each.
- **Arena chunks** are loaded async at the announcement and held with a chunk ticket until the fight closes.
- **Summon pits:** max 6 fights at once, each 1 boss + max 10 adds.
- **Abilities:** one timing wheel, active only with a player within 24 blocks; combat zone caps unchanged (`depths.yml → combat.max-total-mobs`).
- **Pets:** max one per online player. The follow task moves only pets whose owner moved, every 2 ticks, ≤ 0.5 ms per tick. Hidden pets are never sent. Stray pet entities (tagged) are removed at startup and chunk load.
- **Database:** fight results in one async transaction per fight (batch insert of damage rows); payouts one transaction per player; pet XP in memory, saved every 30 s and on quit or profile switch.
- Phase 13 bot load test: 150 bots in a world boss fight, 6 summon fights and 300 pets at once.

---

## UI mockups (V2 lore, vanilla items now, resource pack later)

### `/bosses` (6 rows)
```
Row 1  red frame · [Next world boss: Iron Colossus · 14:00 (in 38m) · iron block] · red frame
Row 2  [Teleport: ender eye (only when open)] [Summon: echo shard] [Leaderboards: gold block] [Last fights: book] [Rewards guide: chest]
Row 3-5  page of the selected tab (summon bosses with ✔ / ✘ Combat 18 · 12/18, sigils you own, queue place)
Row 6  standard navigation · [Help]
```

### World boss scoreboard (place `boss`)
```
  ✦ IRON COLOSSUS ✦
 ──────────────────
  Health   62% · Phase 2
  Time     6:12

  #1 Ashreach    14.2%
  #2 Pouched     11.8%
  #3 Emberfall    9.1%

  You      #17 · 1.4% · Silver
 ──────────────────
  play.skybornmc.net
```

### Sigil item lore
```
Sigil of the Pyre Marshal            ← bold, epic purple
Summon · Tier band 7-8

Calls the Pyre Marshal in a
summon pit for your group.

| Requirements
| Combat 46

| Group
| You + up to 4 players

» RIGHT-CLICK summon

EPIC SIGIL
```

### Pet item lore (in `/pets`)
```
Ember Fox                            ← bold, legendary gold
Pet · Legendary · Lv 64

| Perk
| +9.6% drops in the Depths mine
| +8% Mining XP

| Level
| ▰▰▰▰▰▰▱▱▱▱ 61%

» CLICK summon · SHIFT-CLICK skins

LEGENDARY PET
```

### `/pets` (6 rows)
```
Row 1  red frame · [Active pet: its item, perk now] · red frame
Row 2-5  owned pets (click summon, shift-click menu: skins, release), eggs to hatch first
Row 6  standard navigation · [Show other pets: on/off] · [Collection] · [Help]
```

### Fight end (chat, Java hover; Bedrock gets the Fight report menu)
```
Phoenix » Iron Colossus fell in 4:51! 112 fighters.
 #1 Ashreach 14.2% · #2 Pouched 11.8% · #3 Emberfall 9.1%
 You: #17 · Silver · $256k, 3 shards  [Report]
```

## Data (tables)
`boss_fights` (fight_id, boss_id, fight_kind, arena_id, started_at, ended_at, outcome, max_health, fighter_count, season_id), `boss_damage` (fight_id, profile_key, player_uuid, damage_dealt, hit_count, last_hit_at, band, placement, paid_at; unique fight_id + profile_key), `boss_points` (profile_key, season_id, week_start, points, kills, best_damage), `summon_sigils` (item_uuid, boss_id, created_by, sigil_state, created_at, used_at, fight_id), `summon_records` (boss_id, season_id, group_hash, member_keys, kill_ms, best_damage), `pets` (pet_uuid, profile_key, species, rarity, pet_level, pet_xp, skin_id, is_active, hatched_at, source), `pet_egg_items` (egg_uuid, egg_kind, rarity, created_at, hatched_at), `pet_skins_owned` (account_uuid, skin_id, source, obtained_at), `fishing_contests`, `fishing_contest_entries`. All rows carry `season_id` where it matters. Column names avoid MariaDB reserved words (`placement`, `band`, `pet_level`, `sigil_state`, `hit_count`; never `rank`, `key`, `order`, `rows`, `lines`, `range`).

## Config files
`mobs.yml` (stats, abilities, `drop-tables`, `rare-drops`, elites; depths), `bosses.yml` (schedule, arena rules, dps table, bands, anti-leech, rewards, world bosses list), `summons.yml` (summon bosses list, fragments, sigil cost, pits, queue), `pets.yml` (rarities, species list, curve, perks, follow, treats), `fishing.yml` additions (sea creatures, lava fish, contests). Catalogs are lists with **no plain `@Setting` keys inside a list section**. Texts in `messages/bosses.yml`, `messages/pets.yml`, `messages/depths.yml` (ability lines), `messages/fishing.yml`.

## Permissions
| Node | Default | Use |
|---|---|---|
| `skyblock.bosses.menu` | everyone | `/bosses` and its menu button |
| `skyblock.bosses.summon` | everyone | use sigils |
| `skyblock.bosses.admin.start` | admin | `/boss start`, `/boss stop` |
| `skyblock.bosses.admin.give` | admin | `/boss give` |
| `skyblock.pets.use` | everyone | `/pets`, hatching |
| `skyblock.pets.admin.view` | mod | `/pet view` |
| `skyblock.pets.admin.give` | admin | `/pet give` |

Files: `modules/bosses/src/main/resources/permissions/bosses.yml`, `modules/pets/src/main/resources/permissions/pets.yml`; grants in `server/permissions/bosses.txt`, `pets.txt`; command docs in `docs/commands/bosses.md`, `pets.md`. `/menu` already has a **Pets** button (PHASE-1 mockup); a **Bosses** button joins it.

---

## Milestone order and dependencies

| # | Milestone | Depends on | Lane |
|---|---|---|---|
| M8.1 | Mob framework v2: catalog to `common/mob`, stats, abilities + timing wheel + telegraphs, drop tables, elites, kill credit, `DropBudgetTest` | M4.8, E2 (built) | common/mob, modules/depths (combat) |
| M8.2 | Mini-bosses: abilities, leash, fragments, live book roll | M8.1 | modules/depths, modules/enchants (hook) |
| M8.3 | Boss core: state machine, ledger, bands, anti-leech, idempotent payout, fight report | M8.1 | common/boss, new modules/bosses |
| M8.4 | World bosses: schedule, arena rules, announcements, HP scaling, Colossus + Hollow King, `/bosses` | M8.3, arena build | modules/bosses, worlds (portal), hud (place) |
| M8.5 | Leaderboards: fight scoreboard, boss points, `boss_damage` board source, menu tab | M8.4, NPCHOLO (built) | modules/bosses, modules/npcs |
| M8.6 | Summon bosses: fragments, Summoner NPC, sigils, pits, groups, queue, 5 bosses | M8.2, M8.3, pit builds | modules/bosses, modules/depths (drops) |
| M8.7 | Pets core: eggs, hatching, species, levels, perks, follow entity, `/pets`, PetHook live | PROF, W3, BonusService (built) | new modules/pets (parallel lane) |
| M8.8 | Pet skins, collection log, achievements, quests | M8.7, M10.x (collection, quests) | modules/pets, rewards, achievements |
| M8.9 | Advanced fishing: sea creatures, lava fishing, contests | M8.1, **M7.8** | modules/fishing |
| M8.10 | Hooks: Wildwood, Soul Sand Valley, crates, Black Market, pillars, stats, Discord | M8.4, M8.6, M8.7, M7.9–M7.10, M10.11 | several (small edits) |
| M8.11 | Balance pass BAL8 | M8.4, M8.6, M8.7 configs | docs, tools |
| M8.12 | Staff tools + dashboard rows + fight log | M8.4, M8.6, M8.7 | bosses, pets, economy (admin), logs |
| M8.13 | Wyrm of the Deep | M8.4, RP1 (art) | modules/bosses, resource pack |

- **Parallel lanes:** mobs and bosses (M8.1 → M8.2 → M8.3 → M8.4 → M8.5 → M8.6), pets (M8.7 → M8.8, can start any time), fishing (M8.9 after Phase 7 M7.8).
- **Builds needed:** arena brief update (flat floor, no high ground, spectator balcony), 6 summon pits, Summoner NPC spot at The Gate, lava fishing pond at spawn.

## Release / test checklists (owner)
| Milestone | You check (Java + Bedrock) |
|---|---|
| M8.1 | Tier 1–3 mobs have no abilities; a tier 4+ mob shows the red ring before its slam and you can step out; an elite has `★` and drops more; a friend hits your mob first and you finish it → the friend gets the kill |
| M8.2 | Grave Warden uses two abilities, walks back into its ring when pulled out, rewards give fragments |
| M8.3–M8.4 | Staff starts a world boss: announcement 5 min before (chat, title, boss bar, Gate board); teleport from `/bosses`; phases change at 66% / 33%; stand still or fly → no reward; die → respawn in the arena with your damage kept; kill it → band and rewards, Fight report menu on Bedrock |
| M8.5 | Fight scoreboard shows top 3 and your rank; Hall of Heroes boss board fills; weekly board cycles |
| M8.6 | Mini-boss gives a fragment; make a sigil at the Summoner; summon with 2 friends; all three get loot; staff stop refunds the sigil to `/box` |
| M8.7 | Open a Mystery Pet Egg → rarity egg → pet; summon it, it follows behind you; in a combat zone you can't see other pets; pet levels while mining; loadout swaps the pet |
| M8.8 | Apply a pet skin; collection log Pets page fills |
| M8.9 | Sea creature at Fishing 15+; lava fishing at the spawn pond; a contest runs with a boss bar and pays the top 3 |
| M8.12 | Staff give and stop show in `/logs`; dashboard boss rows |

## Decided
- World bosses + summon bosses with a damage leaderboard and top-3 rewards; custom mobs with abilities by tier (DESIGN §7).
- Pets from a monthly crate egg that opens into a random rarity, level through use, give buffs (DESIGN §7, FI 91); pets per profile (130).
- Boss arena: profile switching blocked (132), 3 s teleport delay (345), `/ec` and `/craft` blocked (377). PvE death keeps everything (#76).
- No mob stacking in The Depths or boss fights (S1). Shards from bosses, envoys, KOTH, high Depths tiers (#43).
- Fishing contests every 4 h, 15 min (EV2). Basic fishing in Phase 7 (PD24 default).
- Boss rewards are event income inside the ~15% event share (`ECONOMY_BALANCE.md` §3.2).

## QUESTIONS
Only decisions not answered yet. "s" is what this plan does if the owner doesn't answer. `p: 1` = important for launch.

```json
[
  {"q": "PV1: In shared combat zones, the player who did the most damage gets a normal mob's rewards (not the last hit)?", "s": "Yes", "why": "Stops kill stealing in busy zones; normal mobs die in ~2.5 s, so assists don't need rewards.", "p": 1},
  {"q": "PV2: Combat tiers 1-3 have no mob abilities; tiers 4-10 one ability per mob type, mini-bosses two, every ability warned 1 s before it hits?", "s": "Yes", "why": "Tiers 1-3 stay safe for new players (WORLD 4.4); the warning makes abilities fair on Bedrock too.", "p": 1},
  {"q": "PV3: 2% of tier 4+ mobs spawn as elites (2x health, +1 ability, 3x drops, a star before the name), counted inside the normal money per kill?", "s": "Yes", "why": "Adds surprise without raising combat income above the mine.", "p": 0},
  {"q": "PV4: Mini-bosses get two abilities, stay leashed to their ring and also drop 1-2 summon fragments to everyone with 10%+ damage?", "s": "Yes", "why": "Makes mini-bosses real fights and the main source of summon fragments.", "p": 1},
  {"q": "PV5: World boss every 2 hours from 12:00 to 00:00 Oslo time (7 a day), announced 5 min before?", "s": "Yes", "why": "Most players can join one in an evening session; WORLD 4.5 already says 5 min warning.", "p": 1},
  {"q": "PV6: The boss arena opens only during a fight, for players with Combat 12+, through /bosses, the Gate event board and the tier 10 portal?", "s": "Yes", "why": "The tier 10 portal alone would leave the boss to late-game players; Combat 12 keeps total beginners out.", "p": 1},
  {"q": "PV7: World boss health is set from the fighters' sword tiers when the 60 s join window closes (aim: about 5 minutes), and it escapes after 10 minutes?", "s": "Yes", "why": "The fight lasts about the same with 10 or 150 players.", "p": 1},
  {"q": "PV8: Reward bands: top 3, Gold (150%+ of the average damage), Silver (50%+), Bronze (10%+), so they work for any number of fighters?", "s": "Yes", "why": "Fixed percentages break with 150 fighters (1% each); relative bands stay fair.", "p": 1},
  {"q": "PV9: Anti-leech: rewards only if you are in the arena at the kill, hit the boss in the last 90 s, are not AFK, reach Bronze, and max 3 accounts per IP?", "s": "Yes", "why": "Stops AFK alts in the arena from farming boss loot.", "p": 1},
  {"q": "PV10: World boss money in minutes of your own tier's reference income: Bronze 3, Silver 6, Gold 10 min, top 3 get +10 / +7 / +5 min?", "s": "Yes", "why": "Feels the same on day 1 and in month 3 and keeps bosses inside the event share.", "p": 1},
  {"q": "PV11: World boss shards: top 3 get 20 / 15 / 10, Gold 5, Silver 3, Bronze 1?", "s": "Yes", "why": "Keeps the balance sheet's top-3 numbers; bands replace 'everyone with 1%+ damage gets 3'.", "p": 1},
  {"q": "PV12: Set XP from world bosses by band: Gold 1,500, Silver 1,000, Bronze 500 (instead of 1,500 for 5%+ damage)?", "s": "Yes", "why": "With 100+ fighters almost nobody reaches 5% of the damage.", "p": 0},
  {"q": "PV13: Launch with two world bosses (Iron Colossus, The Hollow King); Wyrm of the Deep comes with the resource pack?", "s": "Yes", "why": "The Wyrm needs a custom model; the other two work with scaled vanilla bodies on Bedrock.", "p": 0},
  {"q": "PV14: Boss points (100 x your share of the damage per fight, top 3 bonus) fill the Hall of Heroes boss board per season and per week, using your best profile?", "s": "Yes", "why": "Raw damage favours whoever fights most often in big fights; share points are fair across fight sizes.", "p": 0},
  {"q": "PV15: Each world boss has two boss-only blueprints (1-3% chance by band)?", "s": "Yes", "why": "Phase 4 promised blueprints from bosses; they give a reason to keep fighting late in the season.", "p": 0},
  {"q": "PV16: Five summon bosses, one per two combat tiers (Rotfather, Bog Matriarch, Tide Witch, Pyre Marshal, Ashen Tyrant)?", "s": "Yes", "why": "Every stage of the Depths has a boss to work toward.", "p": 1},
  {"q": "PV17: Summon fragments drop 1 in 150 kills in their tier band plus from mini-bosses; 8 fragments + money make a sigil at a Summoner NPC in The Gate?", "s": "Yes", "why": "About one sigil per 2-3 hours of combat.", "p": 1},
  {"q": "PV18: Fragments and sigils are tradeable (trade and AH)?", "s": "Yes", "why": "Like tools and blueprints; gives combat players something to sell.", "p": 0},
  {"q": "PV19: Summon fights happen in 6 shared summon pits next to the boss arena with a short queue (no separate instance worlds)?", "s": "Yes", "why": "Instance worlds cost a lot of memory and CPU at 300 players.", "p": 1},
  {"q": "PV20: A summon group is the summoner plus up to 4 players picked in the menu (the party once parties exist)?", "s": "Yes", "why": "Small groups keep summon bosses personal; picking in a menu works on Bedrock.", "p": 0},
  {"q": "PV21: A failed summon (time runs out or the group dies or leaves) uses up the sigil; a crash, restart or staff stop gives it back in /box?", "s": "Yes", "why": "Failing is part of the fight; losing it to a bug is not (nothing lost by accident).", "p": 1},
  {"q": "PV22: Everyone in the summon group with 10%+ damage gets the full loot, and summon money uses the boss's tier (not yours)?", "s": "Yes", "why": "Same rule as mini-bosses; late players can't farm low bosses for big money.", "p": 0},
  {"q": "PV23: Summon boss leaderboards: fastest kill (group) and best damage in one fight, per boss per season?", "s": "Yes", "why": "Gives summon bosses their own damage board.", "p": 0},
  {"q": "PV24: A Mystery Pet Egg opens into a rarity egg (Common 50, Uncommon 28, Rare 14, Epic 6, Legendary 2%) that hatches instantly into a random species?", "s": "Yes", "why": "Matches DESIGN 7; instant hatching follows 'no waiting'.", "p": 1},
  {"q": "PV25: Pet eggs come from the Eclipse crate (8%), world and summon bosses (0.5-2%) and the Black Market (120 shards)?", "s": "Yes", "why": "Keeps pets rare and tied to the monthly crate and bosses.", "p": 1},
  {"q": "PV26: Unhatched eggs are tradeable; a hatched pet is bound to its profile?", "s": "Yes", "why": "Eggs give a market; bound pets keep the 'nothing moves between profiles' rule simple and dupe-safe.", "p": 1},
  {"q": "PV27: One active pet; its perk grows with level up to 3 / 5 / 8 / 11 / 15% by rarity at level 100, Epic and Legendary add +5 / +8% skill XP, all inside the existing bonus caps?", "s": "Yes", "why": "Felt but small; a pet never breaks the +300% sell cap or other caps.", "p": 1},
  {"q": "PV28: 15 species tied to an activity (mine, forest, combat, farming, fishing, anywhere), every species in every rarity, Phoenix Chick Legendary only?", "s": "Yes", "why": "Each playstyle has pets worth hunting; loadouts can swap them.", "p": 0},
  {"q": "PV29: Pet levels 1-100 from actions in its activity (Lv 100 after about 60 active hours), no XP from offline, AFK or grinders?", "s": "Yes", "why": "Fast start, slower later (your #61), and XP comes from playing.", "p": 1},
  {"q": "PV30: Pets never fight or take damage; they only give perks?", "s": "Yes", "why": "Fighting pets would add lag and mess up boss damage shares.", "p": 0},
  {"q": "PV31: Pets are small vanilla mobs following behind you; other players' pets are hidden in combat zones, summon pits and the boss arena, and /settings can hide all of them?", "s": "Yes", "why": "Works on Bedrock and a pet never blocks a hit meant for a mob.", "p": 1},
  {"q": "PV32: Pets reset with the season like other profile progress; pet skins are account cosmetics and stay?", "s": "Yes", "why": "Same rule as tools and sets; skins are cosmetics (REVIEW C).", "p": 1},
  {"q": "PV33: New commands /bosses (next boss, teleport, summon, leaderboards, last fights) and /pets, also as buttons in /menu?", "s": "Yes", "why": "Players check boss times and pets often; menus work on Bedrock.", "p": 1},
  {"q": "PV34: Staff commands /boss start|stop|give and /pet give|view, all audited?", "s": "Yes", "why": "Staff need to test fights, end broken ones and fix lost items.", "p": 0},
  {"q": "PV35: Once Phase 8 is live, the Wildwood outpost also unlocks when a member got Bronze or better in a world boss kill (islands that unlocked it already keep it)?", "s": "Yes", "why": "Matches DESIGN 3.7 'first world boss defeated' without taking anything away.", "p": 0},
  {"q": "PV36: Soul Sand Valley biome gives summon fragments from player kills of spawner mobs on that outpost, max 5 per island per day?", "s": "Yes", "why": "Its 'boss key chance' needs something to drop; the daily cap stops AFK farms.", "p": 0},
  {"q": "PV37: Sea creatures from Fishing 15 (1 in 25 catches, custom rod), they give Fishing and Combat XP and fishing-only drops?", "s": "Yes", "why": "Adds action to fishing using the same mob framework.", "p": 0},
  {"q": "PV38: Lava fishing in Emberdeep lava and a spawn lava pond from Fishing 30 with a lava rod, 5 lava fish worth 1.2x?", "s": "Yes", "why": "A late fishing goal tied to the third outpost.", "p": 0},
  {"q": "PV39: Fishing contests score points by fish rarity (1 / 2 / 5 / 15 / 50); top 3 get Blaze / Cinder / Ember keys, everyone with 10+ points a small crystal pouch?", "s": "Yes", "why": "Rarity points reward skill and luck, not only speed; small rewards for everyone keep people joining.", "p": 0},
  {"q": "PV40: Balance rule: world, summon and mini-bosses together pay at most 6% of a regular player's income?", "s": "Yes", "why": "Keeps bosses as exciting bursts inside the ~15% event share, never a main income.", "p": 1}
]
```
