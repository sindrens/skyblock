# Full plan: store, ranks, cosmetics, boosters, masteries, Black Market (7 Nov 2026)

Sources: `DECISIONS_STORE_RANKS_7NOV.html` (round 1) and `STORE_SUGGESTIONS.html` (round 2) answers of 7 Nov 2026, the
inspiration screenshots (`docs/planning/INSPIRATION_6NOV.md`: rank tiers with 3-4 bullet perks, badges POPULAR /
BEST VALUE / NEW, one short line per tile + "Click", "What's inside" buttons, no 8-line tooltips), Principle 0.
Owner: "please full plan and implement" and "I need EVERYTHING that's planned fully implemented, resource pack as well,
take your time, do it properly". Two points had no clear answer; their defaults are written where they apply (Fire
tools from an earned Monthly Crate; the Shard Finder numbers in section 8). Everything is built in the order below,
one release at a time.

## The rules everything follows
- **Tebex sells only credit packs.** Ranks and everything else are bought in game with `/credit shop` (`/store`).
- **Bought vs earned credits:** ranks, gifts and `/credit pay` need bought credits; everything else takes earned
  credits first, then bought.
- **Mojang-safe:** money buys convenience, looks and server-wide boosts. Personal and island boosters are bought with
  in-game money or crystals, never credits. Cosmetic perks (speed …) work only where it is not competitive (your island,
  spawn, hub) **and** only on cosmetics earned in game (round 2, answers 18 + 19: both).
- **Tradeable:** cosmetics bought with credits can be withdrawn as an item and traded / sold on the auction house;
  ranks, boosters, the pass, slots and tokens stay on the account.
- **Compound on every upgrade** (round 2, 23-24): every level costs a bit more than the last and gives a bit more, and
  every 50 levels the compound rate of that upgrade steps up a little. Numbers: section 8.

## 1. update167: the credit shop
**Commands:** `/credit shop` (aliases `/store`, `/creditshop`; the `/menu` Store button goes live by itself),
`/credit pay <player> <amount>`, `/credit history`, `/credit gift <player>` (opens the shop in gift mode).

**Menu (6 rows, tool-menu style):** top row = tabs **Ranks · Boosters · Cosmetics · Extras**, slot 4 = your wallet
("Bought 1,200 · Earned 35"). Tiles: bold name, one line, price, a badge where it fits (POPULAR, BEST VALUE, NEW,
OWNED). Every buy opens the confirm screen (item in the middle, price, which credits pay, what you have after).

| Tab | What is sold | Price (credits) |
|---|---|---|
| Ranks | Hatchling, Fledgling, Firewing, Flamecrest, Sunfeather, Pyrelord, Immortal; 3-4 perk bullets each; "Your rank" / "Upgrade: pay 1,500" | 500 / 1,000 / 2,000 / 3,000 / 4,500 / 6,500 / 10,000, minus the rank you have (bought only) |
| Boosters | Server-wide: Sell +50%, Shards x2 (tool XP x2 type), Skill XP +50%, Crystals +50%; 30 or 60 min; your name in the boss bar | 300 (30 min) / 500 (60 min) |
| Cosmetics | Tool skins (moved here), the credit wardrobe sets (moved here), cosmetic crate key (from update172), chat/name colours (update172) | as today; key 150 |
| Extras | Premium battle pass (this season), pass tier skip, streak save, profile slot 2 / 3, island rename token, nick colour token, extra `/pv` vault (update168), extra loadout preset, Island Charter | 1,000 / 100 / 100 / 500 / 1,500 / 100 / 50 / 500 / 300 / 250-750 |

- **Rank buy:** LuckPerms group added through `core.ranks().addGroup` (the account: every profile), the old donor
  group removed (no double groups), audit `credit_shop.rank`, server line "Steve is now **Firewing**! Type gg",
  title for the buyer. An upgrade charges only the difference to the highest donor rank you own. Won ranks
  (Torchbearer …) don't lower the price.
- **gg (round 2, 42 "everyone"):** for 30 s after a rank or credit-pack purchase, everyone who types "gg" gets 1 earned
  credit, once per purchase each, no daily limit, the buyer too.
- **`/credit pay`:** bought credits only, confirm screen, both get a line + a [Receipt] click, refused when the sender
  is frozen or trade-blocked, not to yourself.
- **`/credit history`:** the last 50 credit lines (store, earned, spent, paid, received, chargeback), paged menu.
- **Gift:** pick a player (typing, Bedrock-safe), then buy a rank (their price), the pass, a booster or a cosmetic for
  them; bought credits only; they get a message + the item in `/box`.
- **Chargeback** (`/credit chargeback`, existing): takes back the bought balance; what is still owed removes the ranks
  bought with credits since that purchase and bans until settled (`/eco revert` lifts it).
- **Eclipse key for credits: removed** (keys are earned only; config `eclipse.store-purchase.enabled` → false).
- **Tradeable cosmetics:** tool skins and tags bought with credits get a "Withdraw as item" button (one-time
  redeemable item, tradeable, AH allowed); redeeming binds it to the new owner.
- **Owner part:** Tebex webstore with the 4 credit packs (500 = 5, 1,000 = 10, 2,700 = 25, 14,000 = 130), each runs
  `credit grant {id} <package> {transaction}`.
- Tests: rank price difference, bought-only rule, gg once per purchase, pay guards, menu message keys, config.

## 2. update168: rank perks part 2 and cosmetic points
- **`/pv [1-n]`:** private vaults per account (Hatchling 1, Fledgling 1, Firewing 2, Flamecrest 2, Sunfeather 3,
  Pyrelord 3, Immortal 4, + bought extra vaults), 27 slots, saved like the Island Vault; vaults above your number become
  take-out only.
- **Join when full:** Firewing and up can join a full server (up to +10% slots); the rest see "Server full, ranks can
  still join".
- **`/reclaim` (round 2, 20):** the monthly rank chest, claimed once a month per account with `/reclaim`:
  cosmetic points (Hatchling 100, Fledgling 150, Firewing 250, Flamecrest 350, Sunfeather 500, Pyrelord 700,
  Immortal 1,000) + that month's rank tag.
- **Cosmetic points:** a new account currency (shown in `/credit shop` and `/cosmetics`), also earned from playtime
  (10 an hour), achievements, quests and votes (2 a vote). Spent in update172.

## 3. update169: boosters v2 and KOTH
- **Island boosters (new kind):** the whole island (every member, every area): sell, skill XP, tool XP, crystals,
  shards. One running per type per island, a queue of 3. Shown in `/boosters` and the island menu.
- **Buy personal and island boosters** with in-game money and crystals in `/boosters` → Shop (prices compound with
  strength and time) and in the Black Market; never credits.
- **Server-wide boosters** for credits (update167) and sometimes from events.
- **KOTH prize:** 2 Conqueror keys + 500 crystals + an island booster (sell +50%, 30 min) for the winning island.

## 4. update170: island masteries (all members) and Island Fuel removed
Every mastery: compound price and effect, the compound steps up every 50 levels (section 8), bought with island bank
money in `/is masteries`.

| Mastery | Effect for every island member |
|---|---|
| Swift Hands | faster block breaking everywhere (Haste-like, capped so blocks never break in 0 ticks unless they already do) |
| Beast Tamer | more animal drops and faster breeding on the island |
| Green Thumb | +% crops at /farm |
| Prospector | +% ores at /mine |
| Deep Waters | +% catches at /fish |
| Monster Hunter | +% money and loot at /pve |
| Shard Sense | +% shards in every area |
| Quick Learner | +% tool XP |
| Booster Bank | island boosters last longer |
| Team Spirit | +5% everything when 2+ members are in the same area |

Island Fuel, the Fuel Tank upgrade and Fuel Efficiency leave every menu and code path (stored fuel kept, unused).

## 5. update171: Black Market v8
Fixed row: Disenchant Scroll, the T5 spawners, Island Charter. Headline: Regalia, Voidforged token, Mythic book,
**Mystery Box** (crystals: a key, a booster, a shard bundle or a cosmetic). Rare: Coinmaster token, Auto-Sell Chest,
Chunk Collector, Eclipse key, Legendary book, Phoenix key, Greater Tome, **island boosters** (crystals), **upgrade
voucher** (-20% off the island's next upgrade), **Black Market skins** (tool looks, crystals). Common: Sell Wand,
Black Market tag, **shard bundles** (farming / mining / fishing / combat shards for money), **personal boosters**
(money), **Lucky Clover** and **Fertilizer** (the hoe items). Out: Elytra, the small Wardrobe Tome.
- Lucky Clover: right-click → 60 s of x2 enchant chances (also a 1 in 20,000 hoe drop at /farm, tradeable).
- Fertilizer: right-click an island crop → the 5x5 around it is ripe (own island only; also from Harvest Basket).

## 6. update172: Cosmetics v2 (your designs plug in as they come)
- **Types:** break effects (hoe/pickaxe/rod/sword), particle trails, auras, kill effects, island cosmetics (border
  colour, island name colour, a visitor welcome hologram, island particles), chat and name colours.
- **Levels 1-10, upgraded with cosmetic points:** more particles, colours and a sound at higher levels, a perk at the top
  levels on earned cosmetics only, and the perk only works on your island, at spawn and in the hub (speed, jump, no fall
  damage, …).
- **Cosmetic crate:** only cosmetics; keys for 150 credits and sometimes earned.
- **Chat and name colours:** clean gradients; the nicer fonts and icons need the resource pack (section 9).
- `/cosmetics` menu: tabs per type, equip / upgrade / withdraw.

## 7. update173: Fire tools and more skins
- **Fire Hoe, Fire Pickaxe, Fire Rod, Fire Sword:** a skin plus x1.5 on every boost of that tool (shards, drops, money,
  XP, procs). From the **Monthly Crate**, earned only (top of the monthly playtime reward, monthly top voters, KOTH weekly winner),
not from `/reclaim` (a paid source would break Mojang's rule).
- 10 new tool skins (looks only) in the credit shop, Black Market skins (crystals).

## 8. Compound on everything + Shard Finder
Owner: "compound upgrades on everything", Shard Finder **+1,000 levels** with a compound **25% lower than now**
(3% → 2.25%), and **every 50 levels a slightly higher compound**. The problem: compound over 1,000+ levels explodes.

| Shard Finder (x shards a block) | lv 250 | lv 500 | lv 750 | lv 1,000 | lv 1,250 |
|---|---|---|---|---|---|
| 2.25% flat | 261 | 67,900 | 17.7 million | 4.6 billion | 1.2 trillion |
| 2.25% + a step every 50 levels (+0.05 pt) | 333 | 205,000 | 231 million | 477 billion | 1.8 quadrillion |
| **Built (default): 2.25% compound up to level 250, then +1% of that value a level; every 50 levels +10% on top (replaces the +10% every 25)** | 420 | 2,370 | 6,540 | 14,900 | 31,000 |

A finder that gives billions of shards makes every enchant price meaningless (enchants max at ~20,000x level 1).

## 9. update174: resource pack v2 (owner: "resource pack as well")
- **Delivery:** the plugin hosts the pack itself (the v1 host on its own port, tag `pack-v1-final`), sent on join with
  a prompt ("Required for the best look"), hash checked; declined = vanilla look, nothing breaks. Bedrock plays without
  it (Geyser), every text keeps a plain fallback.
- **Contents (first version, made by code, your art replaces it file by file):** a font with rank badges (7 ranks +
  free + staff), currency icons (money, crystals, credits, shards x4, cosmetic points), clean menu icons (tabs, Back,
  Close, Info), Fire tool models (custom model data on the 4 Fire tools), break effect and cosmetic icons.
- **Chat and name colours:** the clean gradients + the rank badge from the font.
- `docs/PACK_ART.md`: every file name, size and what it shows, so you can draw them later.

## 10. Later, own plans
- **/farm fun** (hoe plan part 2: pick your crop, Hot Crop, Fresh Field, Crop Golem, Combo Fever, Top Harvesters).
- **Minions** (to plan today): farmer minion plants and harvests its own crops on the island, upgrades make it worth
  more.
- **Discord** (one release, later): webhooks, `/discord link` + rank role, tickets and appeals bot.

## Order
167 credit shop → 168 /pv, join full, /reclaim, cosmetic points → 169 boosters v2 + KOTH → 170 masteries + Fuel out →
171 Black Market v8 → 172 Cosmetics v2 → 173 Fire tools + skins → 174 resource pack v2 → compound pass (section 8).
Each release: build + tests, deploy, log check, morning report line, CHANGELOG.
