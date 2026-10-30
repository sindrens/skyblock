# Item mailbox (`modules/box`)

Owner answer 7: **items are never dropped on the ground** when a player's inventory is full. Whatever does not fit waits in the player's **item box** (per profile) until they take it out. Settings: `box.yml` (all editable in `/admin`); texts: `messages/box.yml`; data: `db/box`; emergency spool: `plugins/PhoenixSkyblock/box/spool*.txt`. Reload: `/skyblock reload box`. Help: `/help box`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/box` | Opens your item box: the stacks waiting for your active profile, oldest first, 28 per page. Click a stack to take it, or **Take all that fit** | `skyblock.box.use` | ✅ |

Staff (no command): **`/inspect <player>` → Box** (chest minecart icon, admins only, `skyblock.box.admin`) shows that player's box per profile (head button switches profile). Click a stack → confirm → it is removed; **Clear box** → confirm → every stack of that profile is removed. Removed stacks are **moved to the `box_cleared` table**, never deleted, so they can be restored by hand. Audited (`box_view`, `box_remove`, `box_clear`, category `staff`).

## What goes into the box
Every module hands items through `ItemBox` (common, `net.skybornmc.skyblock.common.box`) instead of dropping them:
- **Sell chest** returns (replaces the old ender chest / feet overflow), **shop** buys that overflow, **Forge** items, **Enchanter** returns, **wardrobe** and **loadout** returns, **trade** deliveries, **auction** collection overflow, **staff give** commands (`/give`-style items, heads, tools, blueprints, skins, spawners, money notes, tags), restored sanction items.
- Later: crate rewards, gifts and AFK pool keys use the same service (`BoxSource.CRATE`, `GIFT`, `AFK`).
- While a player switches profile or is in staff mode, items go to the box instead of the inventory.

## Rules
- **Join notice:** `notify.join-delay-ticks` (60) after joining or switching profile, a non-empty box shows `You have N item(s) in /box` in chat (click to open) and on the action bar.
- **Deliveries while online:** chat line + action bar + sound (`notify.on-delivery`, `notify.action-bar`); an open `/box` refreshes.
- **Hard safety cap (SECFIX1 F-09)** `cap.hard-max-entries` (5,000 stacks, owner level): from here actions that would add to the box and can simply not happen call `ItemBox#refuses(player)` first and are refused with `cap.hard` ("Your /box is too full ... nothing was taken"); today: breaking/taking a stacked spawner. Items that already left somewhere are still stored (never lost) and an `box_over_hard_cap` audit row plus a console warning tell staff. The satchel needs no extra cap: one row per item type, bounded by its capacity, and a full satchel stops the break.
- Taking items out saves the player file right after the items were given (SECFIX1 F-01c), so a crash can't lose them.
- **Cap** `cap.max-entries` (500 stacks per profile, owner level): from `cap.warn-percent` (90%) the player is warned; at the cap the box is "full". **Nothing is ever deleted or refused because of the cap**: returns are still stored, the player is told the oldest items are listed first, and an `box_over_cap` audit row is written. Shop buys already charge only what fits the inventory, so they never fill the box on purpose; `ItemBox#full(profileKey)` lets future voluntary paths (gifts you ask for, claim buttons) refuse while the box is full.
- **Lore** of each stack: where it came from (`sources.<key>` in messages) and when it arrived (date + "ago").
- **Profiles:** a profile with items in its box can't be deleted (switch to it and take them out first).

## Storage, safety and audit
- Table `box_items (entry_id, profile_key, item_data, material, amount, item_name, source_key, created_at, delivery_ref)`, archive `box_cleared` (admin removals).
- **Deliver** = one transaction for all stacks of one call (all or nothing), retried `database.retries` (3) times, then appended to the spool file (fsync) and imported every `spool.import-seconds` (60) and on start. Each spooled stack has a unique `delivery_ref`, so an import repeated after a crash never doubles it.
- **Take** = remove-then-give: what fits is planned first, the rows are deleted in one transaction (`DELETE ... WHERE entry_id = ? AND profile_key = ?` must hit one row), then exactly those stacks are given. Anything that no longer fits, or everything when the player left/switched meanwhile, goes **back into the box** with its original source and time. A double click or a parallel take can never give a stack twice. One take per profile at a time, `menu.click-cooldown-ms` (250) between clicks.
- Audit (category `item`): `box_deliver` (source, item list, ids, count, spooled), `box_take` (ids, items, returned), `box_take_returned`, `box_over_cap`, `box_over_hard_cap`, `box_spool_import`.

Permissions: `skyblock.box.use` (default true), `skyblock.box.admin` (admin group, `server/permissions/box.txt`).
