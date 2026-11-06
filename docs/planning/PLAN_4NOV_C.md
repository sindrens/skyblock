# Plan: Cubed feedback 4 Nov 11:39–11:47 and the PvE direction (owner, 14:38)

This comes on top of PLAN_4NOV.md (A), PLAN_4NOV_B.md (B) and PLAN_TUTORIAL_CHAT.md (T).

## Already handed to the running work
- **Tools rework (B):**
  - Tool max level is **50**. Perks unlock at levels 5/10/15/25/30/35/45, and more perks are added.
  - **Magnet is on by default**, and it is removed from the GUI.
  - The tool GUI is cleaned up.
  - More shards by default, much higher prices, and tracks of 500 or 1000 levels.
  - **Island farm/mine boosts are far stronger than tool perks** and follow the island you are on.
  - The /farm and /mine money bonus is **rarer and grows with your progress**.
  - **No wheat flash** when a crop respawns.
  - **A clear message for every failure** in the tool and upgrade flow.
- **Island = profile (A):**
  - /farm and /mine progress is **per profile**, so switching profile switches progress.
  - **New profiles start on an island** right away.

## 1. A better /help (Cubed 11:47, item 7)
Today /help is a list of theory topics. The new /help is **"What do you want to do?"**: one menu of goals, each a
button that *does* something.
- **Make money:** short how-to ("Harvest at /farm, sell with /sell"), with Take me there (/farm) and Open shop.
- **Upgrade my tools:** opens the tool menu of the tool in hand.
- **Grow my island:** island upgrades, level, Kindle.
- **Play with friends:** /friends, invite, visit.
- **Find my way:** opens /tutorial at the current step.
- **Rules & support:** rules, /discord, report.

Each card is at most 3 lines in the mockup format with 1–2 action buttons. The old topics stay as the "All topics"
page at the end, kept short. /help and /tutorial share the same "next step" logic, so they never disagree.

## 2. Kindle (items 8, 10)
- **Spawn delay:** Kindle appears a few seconds after arriving.
  - Cause: he only spawns on the player-on-island check after the chunk loads.
  - Fix: spawn him when the island chunk loads for an arriving member (`ChunkLoad` plus teleport arrival) and right
    after island creation.
- **"Next step" says only "next" in chat:**
  - Cause: the button runs a placeholder command with an empty objective.
  - Fix: the button shows the real next step (shared with /tutorial and /help) and clicking it *does* the step
    (teleports or opens the right menu).
- **Cleaner, more relevant GUI** (vanilla, spacious, mockup lore): island level and progress, island upgrades,
  island tasks, milestones (Island), the island hoe and pickaxe, Farm and Mine shortcuts, next step. Leftover Depths
  and forge buttons are removed.

## 3. Shop and menu design (item 9)
- No custom icons anywhere: shop tabs, shop items and the /menu hub use **vanilla items** that match their meaning.
- **More air:** menus get one empty row or column between groups, groups are centered, no more than 7 items per row,
  and no crowded 5×9 grids. Paging is used instead of filling every slot. One shared layout helper (the bottom bar
  from plan A) keeps them the same.

## 4. More NPCs at spawn, working together (item 11), extends plan T
- Friendly-named NPCs at spawn:
  - Rowan (guide, start of the tutorial)
  - Bramble (Farm Merchant, also at /farm)
  - Flint (Mine Merchant, also at /mine)
  - Juniper (Shopkeeper)
  - The Banker
  - Ollie (Auctioneer)
  - Contract Board (daily/weekly contracts)
  - Tinker (tool upgrades and prestige info)
  - Sage (skills)
  - The Crate Keeper
- **Synergy:** every NPC's dialog points to the next NPC in the tutorial chapter (Juniper: *"Got coins? The Banker keeps
  them safe."*). Each one gives a small hint when clicked outside the tutorial, then opens its menu.
- **Always know what to do:** the boss bar, the scoreboard "Next" line, Kindle's next step, /help "Find my way" and
  /tutorial all show the **same** next step.
- **Optional:** the tutorial can be skipped any time. Rewards are useful (shards, a key, a tag) but nothing a
  non-tutorial player can't get. Each chapter shows its reward up front.
- **Always up to date:** a unit test checks that every tutorial step points at a command, NPC role and event that
  exist. It fails the build when a feature is removed (no more steps pointing at the Depths).

## 5. PvE instead of dungeons (owner 14:38)
The owner shared an inspiration folder (Google Drive "inspo"), but it **can't be opened from here** because Drive
lists files with JavaScript. **The pictures need to be attached in the chat** before the PvE is planned in detail.

What we know so far:
- **No dungeons.**
- **PvP** stays in combat.
- **PvE** is new.

Starting proposal, to check against the pictures:
- A PvE zone in the grind world, next to /farm and /mine (`/pve`), with mob tiers per player like the farm and mine:
  zombies, then skeletons, then spiders, then blazes, and so on.
- **Combat Shards** for a PvE sword upgrade track.

Note: the owner said "no sword" earlier (09:40). If PvE comes, this needs his confirmation.

## 6. Island modes (screenshot "Casual Island")
The screenshot shows an island *difficulty mode*:
- **Casual:** easier island checkpoints, but not on Island Top.
- **Normal/Competitive:** on the leaderboards.

The choice can't be undone, and per-member scaling is +25%. Proposal: choose it at island creation (Rowan's start
menu). Casual uses 0.6× costs on island upgrades and tasks, and stays out of /is top. **The owner needs to confirm
this.**

## Order
1. Running: island = profile, tools rework.
2. Next: /help + Kindle + shop/menu design (sections 1–3).
3. Then: the shared action bar (T1), tutorial + NPCs + Contracts (T3 + section 4).
4. PvE and island modes once the owner has answered.
5. The remaining parts of plan A: start safety and anti-boost, tab list and scoreboard, selling and storages, the
   bottom bar and lore, "Coming soon" items, daily reward, friends, milestones/achievements/sounds.

## Owner 4 Nov 19:39 (priority: the start experience first)
- **Join on your island:** rejoining or reconnecting puts you on your island (`join.location: island`).
- **/start has pages** that show the current chapter and what's next (part of the tutorial).
- **Island tools work at /farm and /mine.** Loot fills the **inventory first**, then the tool's storage. The storage
  can be seen and used **inside the tool's right-click menu**.
- **Tool items use the mockup lore:**
  - name in the rarity color, bold
  - "Tool · Prestige ★N"
  - a level bar
  - a `| Stats` section and a `| Perks` section (with "Locked · Lv 25")
  - `» CLICK` last
  - a rarity footer
- **NPC skins:** a placeholder skin per NPC (vanilla default skins), changeable in config.
- **Paused on purpose:** anti-boosting/guide gate (plan A phase 1) and PvE. They are planned, not implemented, until the
  start experience is perfect.
