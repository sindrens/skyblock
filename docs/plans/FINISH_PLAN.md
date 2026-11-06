# Finish plan: everything half-done, done (7 Nov 2026)

Owner, 7 Nov 2026: "fix island level (new concept, recalculates by itself), leaderboards, holograms at spawn, NPCs all
around, the quests, everything that isn't fully completed ... fix ALL GUIs: professional design, lores, titles, colour
theme; simple, fun, rewarding." Principle 0: simple, fun, less is more. Source: four read-only audits (island level,
leaderboards/holograms/NPCs, quests/unfinished features, GUI consistency) of 7 Nov 2026.

Built in 4 batches, each released to the test server. Defaults below are used (owner can change every number).

## Batch A: spawn, NPCs, leaderboards, quests (what players see first)
1. **Spawn holograms**
   - Gatehouse portals that lead nowhere (Warzone, Outposts, Events) get honest signs and lead somewhere real:
     Events portal goes to /pve "The Arena", Outposts goes to /fish "The Pond", Warzone stays closed with
     "Closed". The Events board shows the live grind events (/farm /mine) instead of "Coming soon".
   - Remove the orphan Depths texts (`dungeon_board`, `/dungeon` in NPC help).
   - Vote Party board: plain "Vote every day: /vote".
2. **NPCs**
   - Crate Keeper (`crates`) and Tailor (`wardrobe`) get spots at spawn (Crate Yard and Market Square).
   - The Contract Board NPC opens /contracts instead of only printing help.
   - Depths-only roles (`dungeon`, `foreman`, `depths_*`) are removed from the defaults.
3. **Leaderboards (Hall of Heroes)**
   - `boss_damage` ("Coming soon", no data) becomes the **richest players** board (`baltop`).
   - Farming and Fishing boards get help texts like the others.
   - Empty boards say "Be the first!" instead of ten "#n -" rows.
4. **Quests**
   - Tutorial: the invite step no longer blocks solo players (inviting OR the step is skipped after 10 minutes
     of play in that chapter); the Crate Keeper step works once he stands at spawn.
   - Tool-level steps count a level you already have (no waiting for the next level-up).
   - Contracts: buying an island-tool enchant counts for "enchant" contracts; dead Depths types are removed from
     the defaults and the texts.
5. **Things that say "Coming soon" but are not coming**
   - Island upgrades that do nothing (Island Vault, Mob Loot, Lumberjack, XP Boost) are hidden; the Deep Pockets
     mastery (no effect) is hidden; Depths wording in masteries fixed.
   - "/credit pay (coming soon)", "Season 1 coming soon", the pass "Coming soon" tiles, "Conquest comes with
     events": removed or reworded to what exists.

## Batch B: island level, a new simple concept
- **Island level = island XP.** One bar. Everything members do on the island (and at /farm, /mine, /fish, /pve)
  gives island XP, the same sources the pillars use today (crops, ores, logs, spawner kills; coops, rank boosts and
  lava/fall farms give nothing). Level n needs a little more XP than the one before.
- **Updates live**: every gain is added at once (no scans, no Recalculate button, no 5-minute waits).
- **Rewarding**: every level puts money in the island bank and shows a title to online members; every 5 levels a
  bigger reward (crystals per member, crate key); every 10 a chat line for the server.
- **Island worth** (the blocks you place) stays as the "Value" tab of /is top only; it no longer mixes into the level.
- The old sources (bank, skills, achievements, collections, upgrades, block worth, pillars as 4 bars) stop counting.
  Mastery gates that use island level are re-checked against the new pace.
- `/is level` menu: the bar, "how to earn island XP", and the next 5 rewards.

## Batch C: GUI pass 2 (all menus)
- One click-hint style everywhere: `» CLICK to ...` (Right-click / Shift-click lines in the same form).
- Titles: Title Case nouns, `Feature » Screen`, no question titles (confirm screens say "Confirm" in the title and
  ask the question in the item).
- Every lore line within 36 characters (27 lines found), no raw colour tags (`<green>`, `<gray>`, `<dark_gray>`:
  named style tags), no empty lore on buttons, danger tiles through the shared danger item.
- 5-row menus (Guide tasks, Help topic) become 6 rows with the standard bottom bar.
- Pack-aware icons where an icon exists.

## Batch D: leftovers
- FX-05 (Sell Wand / Auto-Sell crash order), FX-09 (tutorial hint from the config), Kindle and Rowan quest chains
  that the plan promised, /fish and /pve contract types.

## Still the owner's call (not built until answered)
Pack v2 art direction, tool skins (Q-74), Sell Wand / Auto-Sell / Chunk Collector on the Black Market (Q-71),
/mine top tier emerald (Q-70).
