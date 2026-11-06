# Phase 4: Tools and The Depths

**Goal:** the main income loop (owner decision: The Depths ~50% of income). Players claim a starter kit, forge better tools, level them, hunt blueprints, mine/chop/fight in tiered zones where everyone has their own blocks, fill their satchel, level skills, unlock tiers and earn crystals.

Depends on Phases 0–3 (sell, crystals). Design: `DESIGN.md` §5–6, `WORLD.md` §4, `REVIEW.md` A2, A3, B5, B6.

---

## M4.1 Depths layout (placeholder build by code)
- Owner decision: placeholders first. The plugin generates **The Gate** (hub) and three wings (Mine, Forest, Combat) with one zone per tier using the `WORLD.md` palettes: ore fields ~40×40, decoration walls, lighting, tier gates.
- Zones are defined in `depths.yml` (name, aspect, tier, region corners, ore/log field regions, spawn point, gate). Real builds later only change coordinates, never code.
- A **builder brief** (`docs/BUILDER_BRIEF.md`) describes sizes, palettes and what must stay a region (ore fields, gates, spawn pads).

## M4.2 Tools (replaces the Omnitool, owner decision 2026-10-29)
All custom tools, blueprints and skins are **tradeable** (trade + auction house) and have **no durability**. Each tool is a unique item (PDC id + UUID; dupe detection from M0.7). Definitions in `tools.yml`.

**a) Starter kit: Quartermaster NPC (spawn)**
- Free basic custom pickaxe, axe, hoe, sword, rod. Re-claim **every 24 h** (owner decision; only the pieces you don't have). First tutorial step later.

**b) The Forge: Blacksmith NPC (spawn) + `/forge`**
- Menu tabs per tool type; each tool tier shows recipe (Depths materials + money), stats, requirement (skill level / Depths tier), owned ✔ / craftable / locked ✘ with progress.
- Tool tiers follow the Depths tiers (Mine: Coal → Copper → Iron → Gold → Redstone → Lapis → Diamond → Emerald → Amethyst → Quartz → Netherite → Voidstone; Forest: Oak → … → Warped axes; Combat: swords per combat tier; Farming hoes and rods in Phase 7).
- **Upgrading keeps progress:** forging the next tier from your current tool carries over its level, perks and stats (no reason to fear upgrading).

**c) Living tools (levels 1–50)**
- XP per block/log/kill with that tool; level-up sound + action bar; lore shows level bar, stats and history (blocks mined, kills).
- Levels raise speed/fortune and unlock **perk slots at Lv 5 / 15 / 30 / 45** (owner decision). Custom enchants go into perk slots (Phase 6 `/enchanter`; slots shown as "empty" until then).
- **Balance (owner: "it has to make sense in gameplay")**, pacing targets for an active player, tuned in the M3.0 balance sheet:
  | Slot | Level | Reached after (active play) | Why |
  |---|---|---|---|
  | 1 | 5 | ~1 hour | First reward fast, teaches the system |
  | 2 | 15 | ~day 2 | Daily goal |
  | 3 | 30 | ~week 1 | Mid-season power spike |
  | 4 | 45 | ~week 3–4 | Long-term goal for dedicated players |
  | 5 | Prestige | ~week 6+ | See below |
  - XP curve per level ~1.12× the previous (configurable); a higher tool tier gives more XP per block, so upgrading speeds levelling but is never required to reach a slot.
  - **Prestige:** at Lv 50 the tool can prestige: level back to 1, keeps perks and stats, gains **+1 perk slot** (max 1 prestige per tool per season) and a prestige star in the name. It costs the time to level again, so slot 5 is a real achievement, not a purchase.
  - Perks scale with slot count, not tool tier, so a cheaper tool with 4 slots stays competitive for a while (keeps the AH market for levelled tools alive).
- Break speed via item attribute modifiers (`block_break_speed`/mining efficiency), no client hacks, works on Bedrock.

**d) Blueprints**
- Rare drops from Depths tiers (and bosses in Phase 8), with rarity and drop source in the lore. Blueprint + materials at the Forge → special tool with a unique ability (e.g. *Magma Pickaxe*: auto-smelt + chance to melt nearby ores). Abilities from a small, balanced list in `tools.yml`.

**e) Skins**
- Cosmetic only. From crates/store/events (Phase 10/11), redeemed into a skin collection; applied in the tool's **Skins** menu; resource-pack model per skin (Java), name/lore change for Bedrock until mapped. Kept across seasons.

**Tool menu** (shift + right-click a tool): stats, level, perk slots, skin, history, "Open Forge".
**Death:** custom tools are kept on death (REVIEW B7).

## M4.3 Per-player blocks engine
- Ore/log blocks are real blocks in the world (all players see the full field). When a player breaks one: the break is handled by us, the player sees the **depleted block** (tier's depleted material) only for themselves, and after **10 s** it reappears **only for them**. Other players keep seeing their own state.
- Server-side the block never changes, so 300 players can mine the same field without conflicts. Chunk reloads resend the player's depleted states.
- Break validation: expected break time vs. actual (anti fast-break); Grim handles movement/reach.
- Respawn timers in a timing wheel (cheap at 300 players × 1 block/s).
- **Visibility setting** (everyone / island / none) hides other players in the zone (`/settings` + quick toggle item).

## M4.4 Satchel
- Drops go straight to the **satchel** (virtual storage per player), not the inventory.
- Satchel menu: contents by item, total value, **Sell all** (uses M3.4 prices), sell per item; full warning on action bar + sound; auto-sell toggle as a later perk.
- Shown on the Depths scoreboard (`Satchel 412/640`). Capacity grows with Mining/Foraging skill and satchel upgrades (Forge).

## M4.5 Skill core (pulled forward from Phase 6)
Tier unlocks depend on skills (REVIEW A2), so the **XP and level part** of skills is built here: Mining, Foraging, Combat (Farming/Fishing in Phase 7).
- XP per action from `depths.yml` (~1.45× per tier), level curve configurable, level-up title + sound, `/skills` GUI (levels, progress, next unlock). Perks come in Phase 6.
- Skill XP also feeds the island pillars (Phase 5 hooks are prepared).

## M4.6 Tiers and unlocks
- Mine 12 tiers, Forest 11, Combat 10 (`WORLD.md` §4). Unlock by skill level; locked gates explain the requirement ("Mining 15 needed: 9/15").
- `/dungeon` GUI: aspect tabs → tier list (unlocked = teleport, locked = requirement + progress).

## M4.7 Forest
- Same engine for logs (per player, 10 s respawn), tree templates per tier, leaves decorative.

## M4.8 Combat zones
- **Shared** mobs per tier (visibility forced to everyone in combat zones), custom mob definitions in `mobs.yml` (health, damage, speed, drops, XP, crystals), spawn caps per zone, despawn when no players nearby, mini-boss placeholder timer (real bosses Phase 8).
- Mine/forest ambient mobs are per-player and harmless (REVIEW B6).

## M4.9 Drops and crystals
- Drop tables per tier (`depths.yml`): ore items → satchel (also the Forge materials); crystal chance; **blueprint** chance; rare drops (geodes, key fragments) as placeholders for later phases.
- Crystals credited via `EconomyService` (ledger reason `depths_drop`), shown in the action bar.

---

## UI mockups

### Scoreboard: The Depths
```
  ✦ PHOENIX SKYBLOCK ✦
 ──────────────────
  ▸ Mine 40 Iron Ore 23/40

  Zone      Mine · Tier 3
  Satchel   412 / 640
  Crystals  ◆ 4,250
  Mining    Lv 12 (68%)
 ──────────────────
  play.skybornmc.net
```

### `/dungeon`
```
Row 1  red frame · [Iron pickaxe: Mine] [Oak log: Forest] [Iron sword: Combat] (tabs) · red frame
Row 2–5  tiers as items: unlocked (ore block, green "✔ Click to teleport"), locked (gray dye, red "✘ Mining 15 needed · 9/15")
Row 6  standard navigation
```

## Data (tables)
`tools` (item uuid, owner history, type, tier, level, xp, perks JSON, skin, stats JSON), `blueprints_found`, `skins_owned`, `starter_claims`, `satchels` (player, item, amount), `skills` (player, skill, xp, level), `depths_zones` (config), `player_tier_unlocks` (derived/cache).

## Release / test checklists (owner)
| Milestone | You check |
|---|---|
| M4.1 | Walk The Gate and wings; `/dungeon` teleports |
| M4.2 | Claim the starter kit; forge the next tier (progress kept); tool levels up; tool menu; a blueprint (staff-given) crafts a special tool; skin menu shows |
| M4.3 | Mine with an alt next to you: each sees their own ore; respawn after 10 s |
| M4.4 | Satchel fills, sell all, full warning |
| M4.5–M4.6 | Skill XP, level-up, tier unlock + locked gate message |
| M4.7–M4.8 | Forest logs; combat mobs, drops |
| M4.9 | Crystals in the action bar and `/bal`; ledger rows |

## Decided (2026-10-29)
- Ore and logs respawn after 10 s; broken blocks are fully personal.
- Omnitool removed: starter kit + Forge + living tools + blueprints + skins; all tradeable; no durability.

## Open questions (defaults apply unless changed)
- Perk slots at 5 / 15 / 30 / 45 + 1 from prestige; starter kit every 24 h (owner, 2026-10-29).

1. Starter kit pieces: tradeable via `/trade`, but **not listable on the AH** (otherwise the AH fills with free tools from alts). Default: as written.
