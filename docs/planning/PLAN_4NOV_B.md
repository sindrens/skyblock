# Plan: tester feedback of 4 Nov 2026 (Maarten, Cubed, 00:20–01:09)

This list comes on top of `PLAN_4NOV.md`. Some items here change that plan; the changes are marked **(changes plan A)**.
Items are grouped by system, not by message order. The numbers are the owner's.

---

## Owner answers (4 Nov, 09:40)
- Kept on leave: **credits, ranks, store purchases, achievements, cosmetics**. Everything else is wiped.
- Shards: no preference → default **personal per player**.
- Kick: no preference → default **leave, kick and disband all wipe** (with a 14-day staff restore).
- Milestones: no preference → default **the proposed list** (section F).

## Owner answers (4 Nov, 09:40), these override everything below
- **Only two shard types:** **Farming Shards** (from the harvester hoe) and **Mining Shards** (from mining). There
  are **no Timber or Combat shards**, and there will be **no island axe and no island sword** (they are removed).
  Combat is only PvP now; Depths/dungeon/timber are gone.
- **Every way of leaving wipes the profile:** leave, kick and disband.
- **Milestones:** the list is OK.
  - Island things count in the **island milestones**.
  - Sort milestones by where you earn them: **Farm**, **Mine**, **Island**, so grinding at /farm and /mine earns
    milestones.
- **/mine tier 1 is ALL coal** (no stone). Break enough coal to make the mine full **iron ore**, then the next ore,
  and so on. One ore type fills the whole mine per tier (like the farm crops).
- **Everything starts with diamond tools:** the island hoe and pickaxe start at diamond (the wood→netherite tier-ups
  are gone).
- **Clean up custom enchants:**
  - Remove the axe and sword enchants (no such island tools).
  - Hoe and pickaxe enchants must work on the island tools and must not copy upgrades (wide_reap vs Sweep, vein_rend
    vs Vein, swiftness vs hoe Speed, crystal_seeker vs Shard Finder: one survives, the other is removed). Default:
    the upgrade survives, and the enchant becomes a rare-find book with its own unique effect, or is removed.
  - Fishing and armor enchants: kept unless they are Depths/PvE-only (default; owner can change).

## A. Island = profile (14, 15, png "Slot locked", "Profiles can't share one")
The biggest change, and it touches almost everything, so it is built first, before plan A's phases 4–5.

**Rule:** a profile *is* one island membership. Everything you earn on a profile belongs to that island run: money,
inventory, ender chest, island tools, shards, skills, quests, collection, milestones and storages. **Leaving the island
(leave, kick, disband) wipes the profile.** You keep only account things: **credits (gems)**, ranks, store purchases,
cosmetics/tags, and playtime stats.

How it works:
1. `/is leave` (and the Leave button) shows one clear warning screen: *"Leaving wipes this profile: $X, N items, all
   tools, shards and skills. Only your credits and cosmetics stay."* Typing or clicking confirms it. Plan A's
   "no confirm screens" doesn't apply here, because this can't be undone.
2. Kick/disband: the members get the same wipe the next time they join (online: right away), with a chat summary of
   what was lost.
3. **The wipe is safe:** before the wipe, a snapshot of the profile is saved for 14 days, so staff can restore it with
   `/profiles restore <player> <slot>` (for mistakes and griefing kicks). It goes in the audit log.
4. After the wipe, the slot is an empty profile: you get the start flow again (new island or accept an invite).
5. **Joining an island** needs an empty profile. If your current one isn't empty, the invite says *"Use an empty profile
   slot or leave your island first"* with a button to switch.
6. **Fix "Slot locked":** a profile that already exists can always be switched to, even if your rank no longer gives
   that many slots. Only *creating* new profiles is limited by rank.
7. **Fix "Profiles can't share one":** that rule only stops two of your *current* profiles from being on the same
   island at once. After you leave (the profile is wiped), you can be invited back. The "was on this island" memory
   goes away.
8. **Anti-abuse:** a wiped profile's money can't be moved out in the last minute without limits, because the
   anti-boost caps from plan A apply to receivers. The audit log shows large `/pay`s within 10 min before a leave.

## B. Island tools belong to the island (9, 15, 3, 4, 10, 11) **(changes plan A phase 5)**
- **Island tools are shared island property.** Levels, upgrades and prestige are stored per island and tool type, not
  on the item. Every member holds the same hoe/pickaxe/axe/sword "handle"; the item reads the island's data. Leaving
  removes the tool (part of the wipe). A tool item that isn't on your island turns back into a plain item.
- **Shard types (3, 4):** 🌾 *Harvest Shards* (hoe), ⛏ *Mining Shards* (pickaxe), 🪓 *Timber Shards* (axe), ⚔ *Combat
  Shards* (sword). Each tool's upgrades cost only its own shard. Grinding one thing doesn't upgrade every tool. Shards
  are personal (per profile). Any member can spend them on the island's tool. Old shards are split by what earned
  them where we know it (hoe-earned → Harvest). Anything left goes to Harvest Shards.
- **Prestige (9):** at max tool level, the island can prestige that tool (owner/co-owner only, with a confirm
  screen). It resets that tool completely (level and every upgrade) and **resets that shard type to 0 for all
  members**. In return the island keeps a permanent **+5% progress per prestige** on that tool: more XP and more shards
  of its type, upgrades 5% cheaper. Prestige ★ in the name, title + sound for every online member.
- **Row is broken (10) → replaced by "Sweep" (11):** a *chance* per crop to also harvest nearby ripe crops. It is
  locked until prestige 1 and gets better every prestige:

  | Prestige | Extra crops | Chance at max level |
  |---|---|---|
  | 1 | 2 | 10% |
  | 2 | 3 | 15% |
  | 3 | 4 | 20% |
  | 5+ | 6 | 30% |

  It has 100 levels of chance inside each prestige band. Swept crops give normal drops and XP, but no extra rare-find
  rolls (no doubling). The pickaxe gets the same idea later ("Vein": 2 nearby ores), also from prestige 1.
- **Speed when holding the hoe (2):** Speed I while holding the island hoe at /farm and on the island. "Swiftness"
  upgrade: Speed II at prestige 2. The setting is `hoe.speed-level`.
- **Farming level (5):** the farming skill gives **+0.5% extra crops per level** (max +25% at 50), shown in /skills. The
  same for mining → extra ores.
- **Tool menu on right-click in the air only (7):** right-click in the air opens the menu, and right-click on a block
  works like vanilla (tilling works). Shift is no longer needed. Bedrock players (where right-click in the air may not
  fire) also get `/tool`, and the hotbar menu has a "Tools" button.

## C. Farm and mine fixes (1, 6)
- **Wheat shows up after carrots (1) and right-click turns a crop into wheat (6):** the per-player view is only sent
  once, and when the server re-sends the real block (regrow, a click, a chunk resend) the client sees the real wheat.
  The fix: (a) when a crop regrows, send that block's *personal* view to every nearby player; (b) cancel right-click
  on farm crops and re-send the personal view; (c) re-send the view on chunk load / teleport / respawn in the grind
  world; (d) one "view cache" per player so this is cheap.

## D. Islands
- **Async island recalculation (8):** take chunk snapshots on the main thread (spread over ticks), count the blocks
  async, and write back on the main thread. A `/is recalc` cooldown, and a progress line in the action bar.
- **Island upgrade prices (12):** more tiers on each upgrade (up to 15 on the big ones: size, generator, member
  slots, crop growth, spawner cap, hopper cap). The first tiers are cheap enough to buy in your first hour, then
  costs grow smoothly (×1.6–1.9 per tier). Today a few tiers jump straight from cheap to very expensive. The full
  table goes in `upgrades.yml`, and the migration keeps bought tiers.
- **Island icons in the island menu (16):** a clear vanilla icon for every button (members: player head; upgrades:
  beacon; bank: gold block; warps: ender pearl; settings: comparator; level: experience bottle; visitors: oak door;
  tools: the island hoe), with lore in the mockup format.
- **Crops on the island not growing as intended (18a):** look into IslandGrowth (paused chunks, the 2 ms cap, stage
  steps). The test is a 60×60 field that has to be fully grown in about the configured time.
- **Spawner mobs (13):** use Paper's `Mob#setAware(false)` instead of `setAI(false)`. The mob has no AI, doesn't walk,
  doesn't fly or jump by itself, but **has gravity and can be pushed** by players and water. Guardians and other
  fish-likes are kept still on land (no flopping). Blazes/ghasts/phantoms don't float upward. Mobs that are already
  spawned are fixed when their chunk loads.

## E. Economy and social
- **No bank withdrawal fee (20):** `withdraw.fee-percent` and the mastery fees are set to 0 (ValueChange). The fee text
  is removed from the menu.
- **Friend list:** `/friends` (GUI): add, accept, remove, list with online status, "visit island" and "message"
  buttons, and a friend-joined notice (can be turned off in /settings). Friends of friends aren't shown. Limit 50,
  more by rank. It lives in the social module.
- **/fly (17):** players with `skyblock.fly` (rank) can toggle with `/fly` on islands where they are a member, or on a
  visited island when its owner allows it (island setting). It's turned off at spawn, /farm, /mine and PvP. Flying
  stops when you leave the allowed area, with fall-damage protection.

## F. Feedback and noise (18b, 19, 21)
- **Money/shard messages (18b):** one tidy action-bar line, e.g. `+$1,240  ·  +12 🌾 Harvest Shards`, batched every
  3 s, using the colors from plan A. No more mini bossbar text with raw numbers.
- **/milestones (19): only useful things, and all visible.** Proposed list (owner, please check):
  - Crops: wheat, carrots, potatoes, beetroot, sugar cane, melon, pumpkin, cactus, cocoa, nether wart, sweet berries,
    bamboo
  - Mining (natural/generator only, never placed blocks): cobblestone, stone, coal, copper, iron, gold, redstone,
    lapis, diamond, emerald, obsidian
  - Logs: oak, birch, spruce, jungle, acacia, dark oak, mangrove, cherry
  - Spawner/island mobs killed: zombie, skeleton, spider, creeper, cow, pig, chicken, sheep, blaze, iron golem
  - Other: fish caught.

  Every milestone shows (locked ones show "0 / 100"), and there are no hidden ones. The tier steps follow the "ups and
  downs" curve from plan A.
- **/achievements and sounds (21):** small achievements show only a toast (no chat, no broadcast). Big ones (island
  level 100, prestige) send one chat line. Broadcasts only for server-firsts. A sound pass: one short, quiet sound set
  (UI click, success, error, reward, level-up). The loud and irrelevant ones are removed (anvil, wither, dragon,
  levelup spam on every small thing). Every sound is in config and can be turned off in /settings.

---

## Order (one at a time, each with build + tests + push + checklist)
0. Running: Depths/tutorial deletion + start flow (Rowan) + fixing the 4 failing tests.
1. **Quick fixes:** farm view bug (C), spawner mobs (D), bank fee (E), right-click-air tool menu (B7), /fly (E),
   Slot locked / share-one fixes (A6, A7), crops on the island (D).
2. **Island = profile** (A), with snapshot/restore.
3. **Island-owned tools + shard types + prestige + Sweep + speed** (B). This replaces plan A phase 5's tool part.
4. Plan A phases 1–3 (start safety, HUD/tab, selling).
5. Island upgrades table (D12) + async recalc (D8) + island icons (D16).
6. Plan A phase 4 (storages), then friends (E), then milestones/achievements/sounds (F).
7. Plan A phases 6–8 (bottom bar/lore, "Coming soon" items, daily reward).
