# Phase 4: Omnitool and The Depths

**Goal:** the main income loop (owner decision: The Depths ~50% of income). Players get the Omnitool, mine/chop/fight in tiered zones where everyone has their own blocks, fill their satchel, level skills, unlock tiers and earn tokens.

Depends on Phases 0–3 (sell, tokens). Design: `DESIGN.md` §5–6, `WORLD.md` §4, `REVIEW.md` A2, A3, B5, B6.

---

## M4.1 Depths layout (placeholder build by code)
- Owner decision: placeholders first. The plugin generates **The Gate** (hub) and three wings (Mine, Forest, Combat) with one zone per tier using the `WORLD.md` palettes: ore fields ~40×40, decoration walls, lighting, tier gates.
- Zones are defined in `depths.yml` (name, aspect, tier, region corners, ore/log field regions, spawn point, gate). Real builds later only change coordinates, never code.
- A **builder brief** (`docs/BUILDER_BRIEF.md`) describes sizes, palettes and what must stay a region (ore fields, gates, spawn pads).

## M4.2 Omnitool
- Given on first join (until the tutorial exists). **Soulbound:** can't drop, store in containers, put in item frames, trade, auction or lose on death; `/omnitool` re-gives it if lost.
- **Modes:** pickaxe in the mine/on stone, axe on logs, hoe on crops, sword vs. mobs, rod on water: switches automatically by zone and target block (no hotbar juggling).
- Right-click opens the **Omnitool menu**: level, stats (break speed, fortune, token bonus), upgrades (tokens), enchant slots (Phase 6), skin (cosmetic, later).
- **Level 1–10**, upgraded with tokens (+ requirements). Break speed via the player's `block_break_speed` attribute (no hacks, works for Bedrock).

## M4.3 Per-player blocks engine
- Ore/log blocks are real blocks in the world (all players see the full field). When a player breaks one: the break is handled by us, the player sees the **depleted block** (tier's depleted material) only for themselves, and after **10 s** it reappears **only for them**. Other players keep seeing their own state.
- Server-side the block never changes, so 300 players can mine the same field without conflicts. Chunk reloads resend the player's depleted states.
- Break validation: expected break time vs. actual (anti fast-break); Grim handles movement/reach.
- Respawn timers in a timing wheel (cheap at 300 players × 1 block/s).
- **Visibility setting** (everyone / island / none) hides other players in the zone (`/settings` + quick toggle item).

## M4.4 Satchel
- Drops go straight to the **satchel** (virtual storage per player), not the inventory. Capacity grows with tool level/skill.
- Satchel menu: contents by item, total value, **Sell all** (uses M3.4 prices), sell per item; full warning on action bar + sound; auto-sell toggle as a later perk.
- Shown on the Depths scoreboard (`Satchel 412/640`).

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
- **Shared** mobs per tier (visibility forced to everyone in combat zones), custom mob definitions in `mobs.yml` (health, damage, speed, drops, XP, tokens), spawn caps per zone, despawn when no players nearby, mini-boss placeholder timer (real bosses Phase 8).
- Mine/forest ambient mobs are per-player and harmless (REVIEW B6).

## M4.9 Drops and tokens
- Drop tables per tier (`depths.yml`): ore items → satchel; token chance; rare drops (geodes, key fragments) as placeholders for later phases.
- Tokens credited via `EconomyService` (ledger reason `depths_drop`), shown in the action bar.

---

## UI mockups

### Scoreboard: The Depths
```
  ✦ PHOENIX SKYBLOCK ✦
 ──────────────────
  ▸ Mine 40 Iron Ore 23/40

  Zone      Mine · Tier 3
  Satchel   412 / 640
  Tokens    ✦ 4,250
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
`omnitools` (player, level, stats JSON), `satchels` (player, item, amount), `skills` (player, skill, xp, level), `depths_zones` (config), `player_tier_unlocks` (derived/cache).

## Release / test checklists (owner)
| Milestone | You check |
|---|---|
| M4.1 | Walk The Gate and wings; `/dungeon` teleports |
| M4.2 | Omnitool can't be dropped/stored; modes switch; menu |
| M4.3 | Mine with an alt next to you: each sees their own ore; respawn after 10 s |
| M4.4 | Satchel fills, sell all, full warning |
| M4.5–M4.6 | Skill XP, level-up, tier unlock + locked gate message |
| M4.7–M4.8 | Forest logs; combat mobs, drops |
| M4.9 | Tokens in the action bar and `/bal`; ledger rows |

## Open questions (defaults apply unless changed)
1. Respawn time 10 s for ore and logs (owner said 10 s for ore). Same for logs?
2. Should players be able to **see** other players' broken blocks? Default **no** (fully personal fields).
3. Omnitool on first join or only after the tutorial (Phase 10)? Default: first join for now.
