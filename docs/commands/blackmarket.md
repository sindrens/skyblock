# Black Market (`modules/blackmarket`)

The Black Market (PHASE-10-REWARDS.md **M10.11**, P10-34 to P10-38, P10-41, REVIEW3 F6). Always open; the stock **rotates**. Settings: `blackmarket.yml`; texts and offer names: `messages/blackmarket.yml`. Reload: `/skyblock reload blackmarket`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/blackmarket` | Opens the Black Market (6 rows): slot 4 the restock clock, row 2 the **headline** (shared stock "Stock 18/25"), row 3 the rare offers, row 4 the common offers, row 5 the **fixed row** (Island Charter for shards, Island Charter for gems, Disenchant Scroll), slot 46 your shards and gems. Each offer shows its price, your buys this restock (or month/season) and whether it is bound; a tap opens a confirm screen with the price on the middle item (Bedrock-safe). The Black Market NPC in the Market Square alley opens it too (sneak-click keeps its help text). No arguments | `skyblock.blackmarket.use` (everyone) | ✅ |
| `/blackmarket admin reroll` | Picks new offers for the current rotation now (asks to confirm, announced like a restock; limits per restock keep counting by offer) | `skyblock.blackmarket.admin` (admin) | ✅ |
| `/blackmarket admin give <player> <offer>` | Gives an offer's reward to the player's active profile for free (a buy row with price 0, handed out like a buy, offline = `/box`; never counts toward limits). Tab completes players and live offer ids | `skyblock.blackmarket.admin` (admin) | ✅ |
| `/blackmarket admin view <player>` | The player's last buys (`admin.history-lines`, 15): time, offer, reward, price and hand-out state (`delivered`, `pending`, `refunded`, `review` ...). Works from the console | `skyblock.blackmarket.view` (mod and up) | ✅ |

Staff see everything in `/logs`, category **blackmarket**: `buy`, `buy_delivered`, `buy_failed`, `buy_refunded`, `buy_review`, `rotation`, `reroll`, `staff_give`, `view`. Payments are economy ledger rows `black_market` (refunds `black_market_refund`).

**Rotation (P10-35)**
- Restocks at `rotation.times` (00:00 and 12:00) in `rotation.zone` (Europe/Oslo): 1 headline, 3 rare and 4 common offers picked by weight from the live offers, without repeats. A DST day keeps the local times (a 23 or 25 hour window); a time inside the spring gap starts right after it.
- The picks of a rotation are stored once (`black_market_rotation`), so a restart or a config edit never re-rolls a running rotation. A chat line tells everyone the new headline (`rotation.announce`).
- Offers whose system is not built yet (chunk collector, auto-sell chest, blueprint, sell wand, wardrobe tome) are `coming-soon` and never rolled; an offer whose module is off (crates, wardrobe, enchants, stacking, cosmetics) is left out of new rotations. With the defaults only 2 commons are live (Black Market tag, elytra), so the common row shows 2.

**Offers (P10-36, `blackmarket.yml → offers`, one line each)**
`<id>: slot=<headline|rare|common|fixed> price=<shards> weight=<n> reward=<kind>[:<value>] [amount=<n>] [level=<n>] [limit=<n>/<rotation|day|month|season>] [icon=<item>]`. Kinds: `set-token:<set>`, `enchant-book:<rarity>` (a random enabled enchant of that rarity, chosen at the buy), `scroll`, `key:<crate>`, `spawner:<tier>` (a random type of that tier), `item:<vanilla item>`, `tag:<tag>`, `coming-soon`.

| Offer | Slot | Shards | Limit |
|---|---|---|---|
| Disenchant Scroll | fixed | 8 | 10 per restock |
| Phoenix Regalia Set Token | headline | 400 | 1 per season |
| Voidforged Plate Set Token | headline | 250 | 1 per restock |
| Mythic enchant book | headline | 200 | 1 per restock |
| Coinmaster Silks Set Token | rare | 120 | 1 per restock |
| Eclipse key | rare | 75 | 1 per month |
| Legendary enchant book | rare | 60 | 1 per restock |
| Phoenix key | rare | 50 | 2 per restock |
| Tier 5 spawner | rare | 40 | 1 per restock |
| Black Market tag | common | 60 | once (account-wide) |
| Elytra | common | 30 | 1 per restock |

**Limits and stock (P10-37)**: limits count **per account** (all profiles together). The headline has a **server-wide stock** of 25 per rotation (`rotation.headline-stock`). Both are checked inside the buy transaction, so two clicks at once can never pass them.

**Island Charter (P10-38, owner 157/171, N2-2, P5-14, REVIEW3 F6)**
- Island sizes above the normal maximum: **11x11 150 shards / 250 gems, 13x13 300 / 500, 15x15 450 / 750** (`charter.steps`). One step at a time, and only after the island reached **9x9** with normal `/is upgrade` (`charter.from-size`). Never above `islands.yml size.max-chunks`.
- Paid by the buyer (shards of the active profile, or account gems). Needs the island permission to buy upgrades (leader, by default also Admin). Each size can be bought once per island per season (`season-id`).
- The confirm screen warns that the size lasts **this season only**. The border grows right after the buy; every online member is told.

**One transaction per buy**: currency out, stock and limits counted and a `black_market_buys` row written together (idempotency key per buy). Then the hand-out: items to the inventory, the rest to `/box` (offline: straight to the box), keys to `/crates`, the tag to `/tags`, the charter to the island border. Items and keys are **bound to the profile** (`items.bound`; keys always, `blackmarket` is not a tradeable key source). If a hand-out can never happen (e.g. the island was disbanded) the price is given back once. A crash during a hand-out marks it `review` for staff: lost at worst, never doubled.

**Placeholders**: `blackmarket_restock` (time to the next restock), `blackmarket_headline` (plain name), `blackmarket_headline_stock` (shared stock left).
