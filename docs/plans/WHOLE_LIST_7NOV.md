# Plan: the whole remaining list (7 Nov 2026)

Owner, 7 Nov 2026: "full plan and implementations of this whole list". Answers before starting: Black Market gets a new
offer list with mixed costs (money, crystals, credits by relevance); list everything that is disabled and make it
active and fully working; emerald is the last /mine ore; a /shop click opens a buy screen; ranks and Discord get a
decision page first. Source: PROJECT_STATUS_7NOV.md and the disabled-features audit of 7 Nov (section 6 below).

## Batch 1 (update160): decisions already made + switch on what is finished
1. **/mine:** emerald is the last ore (after diamond), sellable from /mine.
2. **/shop:** a click opens the buy screen (amount buttons 1 / 8 / 16 / 32 / 64 and the price), also on Java.
3. **Black Market, new offer list** (prices by relevance; one currency per offer, new `cost=money|crystals` field):
   - Fixed (always there): Disenchant Scroll (8 crystals), premium spawners Iron Golem / Wither Skeleton / Evoker
     ($200M each, 1 a day), Island Charter (crystals or credits, as now).
   - Headline: Phoenix Regalia (400 crystals), Voidforged token (250 crystals), Mythic book (200 crystals).
   - Rare: Coinmaster token ($40M), Auto-Sell Chest ($25M), Chunk Collector ($20M), Eclipse key (75 crystals), Legendary
     book (60), Phoenix key (50), Greater Wardrobe Tome (100).
   - Common: Elytra ($5M), Sell Wand ($2.5M), Black Market tag (60 crystals), Wardrobe Tome (30 crystals).
   - Blueprint (never built, no item) is removed from the list. The menu shows your money too.
4. **Switched on (finished, only flagged off):** Mob Loot and XP Boost island upgrades (their hooks exist), the Angler
   achievement (fishing exists), custom island generators (the Generator upgrade goes live with them).
5. **Small finishes:** the Angler mastery (more /fish drops with the rod), KOTH pays **2 Conqueror** keys (was 5 Ember)
   and the Conqueror crate is shown in /crates, the Gatehouse portals to /fish and /pve open (the iron bars come out,
   particles on), `/weekly` and `/monthly` rewards and the Rewards menu on, the "/credit pay (coming soon)" text goes.
6. **Done with it:** Mob Loot steps match the upgrade (+12% a level), the "Depths" upgrade tab is called "Grinding" (Mine
   Fortune, XP Boost), island generator emeralds sell like /mine emeralds.

## Batch 2 (update161): quest chains and level gates
- **Rowan** (6 steps: talk to Rowan, claim your island, visit /farm, harvest 20 wheat, sell, come back: key + title)
  and **Kindle** (8 steps: place a spawner, 50 island crops, 50 generator ores, island size 2, island bank deposit,
  first island upgrade, island level 2, check /is top: key + title), as tutorial chapters (one engine).
- Mastery `island-level` gates re-checked against the island XP pace (level 10 ≈ 1 h alone).
- **Built (update161):** Rowan's 6 steps already are the quest book's first chapters (Welcome: Rowan, /menu, /is; The Farm:
  /farm, harvest, sell), so they stay as they are. **Kindle's Island** is a new chapter at the end of the book (8 steps:
  place a spawner, 50 island crops, 50 generator blocks, a bigger island, $10,000 into the island bank, one more island
  upgrade, island level 2, /is top). Reward: $2M, 50 crystals, a Blaze key and the new **Kindled** title. Two new step types
  (`island_level`, `island_break`), a `title <id>` reward, and new bundled chapters are added to a live tutorial.yml once.
- **Gates checked:** island XP grows about as n^2.5 in total, so level 20 ≈ 5-6 h alone, level 50 ≈ 2 days alone (much less
  with a team). Wings (20), Homestead (20/40), Treasury (15/35/60) and the Area Masteries (2 to 50) fit that pace: kept.
  Angler still asks for Farming pillar levels (there is no fishing pillar); noted for the balance pass.

## Batch 3 (update162): menus and fixes
- The rest of the menus in the tool style (rows, headers, click hints), Title Case on every title, 5-row menus to 6.
- FX-05 (Sell Wand / Auto-Sell order), FX-09 (tutorial hint from the config), island visit list via the visibility
  service, ROADMAP / BACKLOG / planned commands brought up to date.
- **Built (update162):** 239 menu lore rows got the tool-style bar (`| Label: value`) in 21 menus (islands, bank, sell,
  progression, rewards, crates, boosters, trade, wardrobe, profiles ...); 64 menu titles are Title Case. FX-09: the carrots
  hint no longer says "3,000" (the number lives in essentials.yml). The visit list already used the visibility service.
  ROADMAP status and BACKLOG updated. **FX-05 stays open on purpose:** a chest's contents can only be made crash-safe with a
  world save per sale (too heavy); the risk is a hard crash inside the half second of a Sell Wand sale. 5-row menus stay
  5 rows (your 2 Nov "smaller GUIs" rule wins).

## Batch 4 (update163): Island Vault
- `/is storage`: shared island storage (rows from the Island Vault upgrade), task hand-in from it, the Vault upgrade
  live.

## Batch 5 (after the decision page): ranks and Discord
- Rank perks from the answers (7 ranks are decided: Hatchling to Immortal), Tebex rank packages with "pay the
  difference", the credit shop (`/credit shop`, streak save), Discord step 1 (webhooks: events, punishments, store).

## Later (needs the builder or is a phase of its own)
Envoys, duels and the PvP zone (after the Warzone build), seasons (reset tool, carry-over, Final Week), balance pass +
300-player bot test + closed beta, hub, web map, CoreProtect (when it supports 26.3), Nether/End.

## 6. Disabled features: what happens to each
| Feature | Verdict | Plan |
|---|---|---|
| Mob Loot upgrade, XP Boost upgrade | finished, flag off | on (batch 1) |
| Custom generators (+ Generator upgrade) | finished, off | on (batch 1) |
| Angler achievement | finished, off | on (batch 1) |
| Angler mastery | needs a hook | built (batch 1) |
| Conqueror crate | needs KOTH keys | built (batch 1) |
| Gatehouse portals Outposts/Events | closed | open to /fish and /pve (batch 1) |
| Rewards menu, /weekly, /monthly | off until release | on (batch 1) |
| Blueprint offer | no item exists | removed (batch 1) |
| Island Vault, task hand-in | not built | built (batch 4) |
| Streak save, /credit pay, /credit shop | needs the credit shop | batch 5 |
| Swift Hands, Beast Tamer masteries | need new hooks | decision page |
| Island Fuel (+ Fuel Tank, Fuel Efficiency) | islands start asleep | decision page |
| Production farming (custom crops) | overlaps /farm | decision page |
| Vote receiver | needs the vote sites + an open port | owner (site accounts) |
| Lumberjack, Timber, Deep Pockets, Crystal Rush, Lucky Strike, Spawner Stack Limit, key fragments, old sell model, old Depths enchants, 13 retired achievements | obsolete (Depths, satchel, old tools) | stay off; Lumberjack hidden |
| Discord webhooks, network punishments | later | batch 5 / later |
