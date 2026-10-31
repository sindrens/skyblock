# Every menu: what to fix

**Status:** draft v1, 31 Oct 2026. Generated from `menus.json`: all 189 menus in `main` at update105, read class by class.
- Each menu lists its slot standard and the usability notes found in the code.
- The rule tags point to `GUI_DESIGN.md`:
  - **[§1.1]** 6-row bottom bar;
  - **[§1.2]** 3-row standard;
  - **[§1.4]** title;
  - **[§3]** states;
  - **[§4]** confirm;
  - **[§5]** plain click / Bedrock.
- Most of the [§1.x] tags are fixed at once by the framework change (GUI_DESIGN §9 step 1).

## GUI-1 handoff (framework done; left for GUI-4 because the menu is in another lane or needs a per-menu decision)
**GUI-BUGS (31 Oct) did:** TransactionsMenu Refresh (now 6), moderation 3-row double Back, trade Cancel (now 53) and the
money picker's own Back, WatchMenu (its `build()` now always draws the frame; the subclasses already did), hand-made
loading/error/empty items in economy/bank and moderation NotifyMenu, the DailyMenu tabs (one menu, in place) and the
permanent ban `twoStep()`. Still open for GUI-4: achievements tabs, locked looks, held-item "Right-click" wording, shop.
The framework (`common/menu`) now gives every menu the bottom bar, Back via the navigation stack, the 3-row Back 18 / Close 22, in-place tabs and pages, the two-step confirm and the shared state items. These menus still need a hand edit:
- **economy `TransactionsMenu`:** Refresh sits on 52, the Previous page slot; on page 2+ Previous covers it. Move Refresh to the top row or merge it into a filter.
- **moderation 3-row screens** (`SanctionMenus` ~292, `AppealMenus` ~379, `PunishMenus` ~773/1079/1252/1312): they put their own Back on 22, which covers the new Close (Back shows twice, at 18 and 22). Remove their own Back; the frame adds Back 18 and Close 22.
- **trade `TradeMenu`:** its Cancel on 45 replaces Back (GUI-BUGS: Back instead of Cancel at 45). `TradeMoneyMenu` sets its own Back on 18 (same slot as the frame's, harmless; can go).
- **economy `WatchMenu`:** the only menu without `frame()` (no bar at all).
- **Hand-made state items** in economy/bank/moderation (clock "loading", red glass "error-item", cobweb): switch to `service.loadingItem / errorItem(viewer, retry) / emptyItem` and `Menu.setLoading / setError / setEmpty`.
- **Tabs that open a new copy of the menu** (e.g. rewards `DailyMenu`, achievements tabs): they still close and reopen. Keep the tab in a field of one menu and call `redraw()` (or use `service.tab(...)`), so the tab changes in place.
- **Locked looks** differ per menu (gray dye, barrier, "Coming soon" lines): use `service.locked(viewer, name, howToUnlock)`.
- **Held items** still say "Right-click" (boosters, tag items, set tokens, tools, the menu item, crate key lore in `items.yml`): GUI_DESIGN §5 wants "» USE to ...".
- **Shop** `CategoryMenu` quick-buy click types are GUI-3 (shop v2).
- **moderation permanent ban** (GUI-BUGS adds its confirm): build it with `new ConfirmMenu(...).twoStep()` like disband and profile delete.

## GUI-4 status (31 Oct)
Notes marked **✅** below are done (by the framework in GUI-1, by GUI-BUGS/GUI-2, or by GUI-4). Framework-wide notes that
GUI-1 fixed for every menu at once and are not repeated per menu: **§1.1/§1.2 bottom bar** (45 Back / 48 ? / 49 Close /
50 Main menu, 3-row Back 18 / Close 22, so no 3-row dead ends), **Back via the navigation stack** (from the hub and
children), **? opens help with Back to the menu** (no more "Back from help goes to the Help root"), **title rule**
(max 2 parts, `Feature » Screen`), **confirm details on the middle item 13**, **in-place tabs and pages**.

**Done in GUI-4:**
- Pick lists instead of cyclers (G9, `common/menu/PickListMenu`): AH category + sort, island weather + time, island chat
  colours, warp category, wardrobe filter, `/transactions` filters, `/settings` multiple-choice settings, `/logs` time
  range + radius.
- Island permissions (U8): Strict / Normal / Open presets, the matrix behind Advanced (atomic save, audited).
- Scoreboard (U4): "| You" header, Next: line during the tutorial, Crystals/Gems only once you have some.
- Glossary (U6): Pity → Luck meter, bound → can't be traded, masteries out of player text.
- Hidden instead of grey: locked skills, Forge "More tiers later", island stats placeholders, unbuilt `/settings`
  switches, spawner Upgrades no longer clickable (no promise).
- Wording: held items "» USE", trade/help "Click", no "later update" texts; long messages shortened (box full, AFK
  ip-blocked, bank withdraw fee: also no more "interest is lost", R3-41).
- Key facts in names: island settings values, Depths locked tier requirement, island top board.
- Titles: enchant slot screen `<mode> » <tool>`, armor set screen `Armor » <set>`, island permissions `Permissions » Advanced`.
- Achievements tabs redraw in place (title follows), shared loading item; playtime milestones in reading order;
  boosters ? text; no barrier icon on non-Close buttons.

**Done in GUI-4b (31 Oct):**
- Pick lists instead of the last cyclers: staff box and staff crates profile switch, eco admin currency, eco Reasons
  period + sort, cosmetics editor rarity, tutorial stats period + players, island grid filter, `/admin` enum settings.
- Members bottom row: the standard bar plus Invites (46) and Invite (47); the Settings, Notes and Permissions
  shortcuts are gone (they are on `/is`). The `/is` menu itself stays as built (GUI_DESIGN §7.5).
- Bedrock typing: `MenuService.input` (common) opens a Floodgate form with the on-screen keyboard for Bedrock players
  and the chat prompt for Java (`menus.yml → bedrock-forms.enabled`). Used by island notes (add, change text), warp
  rename (new), "Adding warps" (sets a warp where you stand), loadout rename and the player picker search.
- Enchanter: the slot screen is skipped when only one slot fits the mode, and the tool list when you carry one tool;
  the book list shows the tool and slot on 4.
- Loadouts: presets in two plain rows, one "Edit a loadout" button with a pick list instead of an Edit book under
  each preset, lore previews the contents (one click still applies, owner LD5), pet placeholder hidden.
- Wardrobe set screen: one row per group, the one way to get the set on 31 (Redeem moved there, with a confirm),
  Merge 33, Trail 40, nothing on 31 once owned, Wear all / Take all off no longer look like dye toggles.
- Player picker: Search (47) and Show everyone (46) when the list is longer than one page.

**Done in GUI-4c (31 Oct):**
- `/top` screen (§7.6) behind the hub Top button (`menu.yml` action `top`, no new command: `/top` is the staff teleport):
  tabs Solo islands, Team islands, This week, Richest players, Top skills; podium 21-23, #4-#10 on 28-34, click an
  island to visit, Full island boards (51) opens `/is top`.
- Help topics on Bedrock: a click on a page opens a Floodgate text popup (Next page / Back); page titles stay the item
  names (`MenuService.showText`, `BedrockForms.sendText`).
- `/settings` in fewer rows: one tab per group (row 0, in place), the group's switches below, no second page; every
  switch name says On/Off (common `MenuService.toggle`).
- Economy admin paging: Top earners and Biggest page by 28 (84 rows by default), Reasons and Alerts show (page/pages).
- Island portal shows every invite (pick-list layout) and has a ? guide.
- AH staff remove: one Remove button → pick list of every reason → confirm (no more 5-reason limit).
- `/logs` results: a plain click opens the entry (details, Teleport, Punish, Show in chat, Bedrock details popup); page
  in the title.
- Staff/moderation: Inspect keeps Back for `/p`, `/logs` and `/reports`; reports tabs in the top row switch in place,
  Refresh (51), Claim/Unclaim refresh the report; confirms for lockdown start/end and confiscate.
- Typing on `MenuService.input` (Bedrock popups): AH search, `/admin` text values and list entries, tags editor
  (id, display, source), display formats (`keepCodes` keeps `&` codes).
- M10.14: the Black Market NPC help no longer says "Not open yet"; every NPC role text checked against what is built
  (test).

**Left (next GUI round):**
- Shop and `/sell` ("Add my inventory", Sell on 53): GUI-3 lane. Chat-link flows (TPA, invites, store/vote/Discord,
  help command suggestions): GUI-5.
- Owner decisions (OPEN_QUESTIONS GUI-4b/GUI-4c): `/p` button count (layout is the owner's punishment spec), a loadout
  apply confirm (LD5 says one click), Warps/Visit overlap in `/is`, offline/recent players in the player picker.
- Not on the GUI-4c list, still open per menu below: logs subjects typing (`/logs` Any player/Staff/Item/Island prompts),
  confiscate screen without the frame, `/inspect` freeze confirm and row shape, report reason confirm.

## achievements (5 menus)

### `achievements.personal`: Achievements
- **Rows:** 6
- **Opens from:** /achievements; /tags > Achievements
- **Rules to apply:** §1.1 add Back at 45
- **Notes:**
  - ✅ GUI-4 (tabs redraw in place with the title; position kept). Tabs sit at 3-5 here while other tabbed menus (rewards, cosmetics, progression top) use 2 and 6 or 2-6, so the tab row is not in a fixed place.
  - The six categories are laid out 5 + 1 (20-24 and 31) with the boosts only readable inside the head's lore.

### `achievements.island`: Achievements
- **Rows:** 6
- **Opens from:** /achievements > Island tab
- **Rules to apply:** §1.1 add Back at 45
- **Notes:**
  - Boost tiles (20, 21, 23, 24) and Top members (31) are display only while 22 is the one clickable tile between them.

### `achievements.history`: Achievements
- **Rows:** 6
- **Opens from:** /achievements > History tab
- **Rules to apply:** §1.1 add Back at 45
- **Notes:**
  - Shows at most 21 season books and has no paging, so older seasons drop off once there are many profiles and seasons.

### `achievements.category`: Achievements » <category>
- **Rows:** 6
- **Opens from:** Achievements > a category
- **Notes:**
  - Read-only list of up to 28 tiles per page; nothing in it is clickable (titles are equipped in /titles, not here).

### `achievements.island_category`: Achievements » Island achievements
- **Rows:** 6
- **Opens from:** Achievements > Island > Island achievements
- **Notes:** none found.

## auction (7 menus)

### `auction.browse`: Auction House
- **Rows:** 6
- **Opens from:** /ah, /auction, /auctionhouse
- **Rules to apply:** §5 make it one plain click
- **Notes:**
  - ✅ GUI-4 (pick lists). Category (46, 9 values) and Sort (47, 4 values) advance one value per left-click; going back needs right-click, which Bedrock touch players cannot do, so the category just before the current one takes 8 taps.
  - Header buttons sit in slots 2-6 above the list while the filters are at 46/47/51/52 on the bottom row: related controls are split, and Refresh at 6 is far from the list.
  - ✅ GUI-4 (falls back to the code default icon). The live auction.yml icon list has no enchant_books entry (the code default has ENCHANTED_BOOK), so that category shows the plain chest icon.
  - Search requires typing in chat; the window closes while you type.

### `auction.my_listings`: Auction House » My listings
- **Rows:** 6
- **Opens from:** Auction House > My listings
- **Notes:** none found.

### `auction.collection`: Auction House » Collection bin
- **Rows:** 6
- **Opens from:** Auction House > Collection bin
- **Notes:** none found.

### `auction.sell`: Auction House » Sell
- **Rows:** 3
- **Opens from:** Sell held item / /ah sell <price> (after typing the price)
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row dialog has no Back/Close/Help; the only way out is Cancel at 16 or Esc.
  - A click on a duration lists the item at once (this screen is the confirm); the price was typed in chat before, so a wrong price is only visible in the button lore.

### `auction.buy_confirm`: Auction House » Buy item
- **Rows:** 3
- **Opens from:** Auction House > click a listing
- **Rules to apply:** §1.2 add Back 18 / Close 22
- **Notes:** none found.

### `auction.cancel_confirm`: Auction House » Cancel listing
- **Rows:** 3
- **Opens from:** My listings > click a listing
- **Rules to apply:** §1.2 add Back 18 / Close 22
- **Notes:** none found.

### `auction.staff_listing`: Auction House » Staff
- **Rows:** 3
- **Opens from:** Auction House > click a listing as staff
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - ✅ GUI-4c (Remove → reason pick list → confirm). Remove buttons act on one click with no confirm (the item is only returned to the seller's bin).
  - ✅ GUI-4c (every reason; frame Back/Close). 3-row dialog has no Back/Close; slots 12-16 are the removal reasons from auction.yml, so a sixth reason would be dropped without notice.

## boosters (1 menus)

### `boosters.boosters`: Boosters
- **Rows:** 6
- **Opens from:** /boosters, /booster
- **Rules to apply:** §1.1 add Back at 45 · §4 confirm rule
- **Notes:**
  - ✅ GUI-4. Info text (48) says 'Top row: your boosters. Bottom rows: global boosters and the items you carry' but personal sits in row 1, row 2 is empty, global in row 3 and owned items in row 4.
  - ✅ GUI-BUGS (confirm). Clicking an owned booster item starts it and uses it up at once with no confirm; a mis-tap wastes a valuable (global) booster.

## box (4 menus)

### `box.box`: Item box
- **Rows:** 6
- **Opens from:** /box; chat link when your inventory was full
- **Notes:** none found.

### `box.staff_box`: Box of <player>
- **Rows:** 6
- **Opens from:** /box <player> (admin)
- **Rules to apply:** §5 pick menu instead of a cycler
- **Notes:**
  - ✅ GUI-4b (pick list). Profile switch (5) only cycles forward; with many profiles it takes several clicks to reach one.

### `box.staff_clear_confirm`: Box of <player> » Clear box
- **Rows:** 3
- **Opens from:** Staff box > Clear box
- **Rules to apply:** §1.2 add Back 18 / Close 22
- **Notes:** none found.

### `box.staff_remove_confirm`: Box of <player> » Remove item
- **Rows:** 3
- **Opens from:** Staff box > click a stack
- **Rules to apply:** §1.2 add Back 18 / Close 22
- **Notes:** none found.

## common (4 menus)

### `common.frame`: ▸ crumb » crumb
- **Rows:** 6
- **Opens from:** Every 6-row menu draws this frame itself; listed once so shared problems are recorded once
- **Notes:**
  - Info (48) and Help (50) both leave the menu and run /help; Back inside help goes to the Help root, never to the menu the player came from
  - Info and Help are two different buttons for nearly the same thing (both end in /help), costing two of the four nav slots
  - 3-row menus get none of Back/Info/Close/Help from frame(): every 3-row menu does its own Back (plain ARROW at 22 in islands, a different label in profiles) or has none (tutorial.where-next, npcs.help)
  - Back and Previous labels use the '»' glyph on a left-pointing action, '»' is also the click marker in lore (» CLICK) and the breadcrumb separator: three meanings for one symbol
  - Title shows '(n/m)' for pages but many menus are crowded because crumbs are dropped from the left at 32 characters

### `common.confirm`: Are you sure?
- **Rows:** 3
- **Opens from:** Any destructive action (disband, kick, leave, delete, reset, restore, ...)
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - The consequences are only in the lore of the green button (a tooltip); on Bedrock/touch the player must tap and hold to read it, and the centre item says 'See the details on the green button'
  - No Back, Close, Info or Help (3 rows), so there is no way to read more before deciding
  - Callers that pass no shown item (tutorial.confirm-skip / confirm-reset, admin) show a generic 'Please confirm' paper, so the item says nothing about what is being confirmed

### `common.number-picker`: Choose an amount
- **Rows:** 6
- **Opens from:** Amount choice for bank deposit/withdraw, shop, sell, trades etc. (any caller that needs a number)
- **Notes:**
  - Typing an exact amount needs the chat (keyboard), which is slow on Bedrock touch; the three -/+ sizes are the only keyboard-free path
  - The '+100' button shows a stack of 64 and '-100' too, so the count on the item does not match the label
  - No 'half' or 'all' shortcut except Minimum/Maximum
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `common.player-picker`: Choose a player
- **Rows:** 6
- **Opens from:** Any action that needs a player (trade, island invite, visit, ...)
- **Notes:**
  - Online players only: nobody can be chosen who is offline (no search, no recent players)
  - ✅ GUI-4b (Search 47 / Show everyone 46 when longer than a page). No search or filter; with 100+ players it is up to 4 pages of heads sorted by name
  - Heads show only the name; no island/rank hint to tell similar names apart
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

## cosmetics (7 menus)

### `cosmetics.cosmetics`: Titles & Tags
- **Rows:** 6
- **Opens from:** /tags, /tag, /titles, /title; /achievements link
- **Notes:**
  - 37 titles run over two pages; locked titles are mixed with owned ones with no 'owned only' filter or sort.

### `cosmetics.editor_list`: Titles & Tags » Edit titles
- **Rows:** 6
- **Opens from:** Titles & Tags > Edit titles & tags (staff)
- **Notes:**
  - Creating needs three chat prompts one after the other (id, look as MiniMessage or & codes, how to get it), awkward on Bedrock keyboards.

### `cosmetics.editor_entry`: Titles & Tags » Edit titles » <id>
- **Rows:** 6
- **Opens from:** Editor list > a title or tag (staff)
- **Rules to apply:** §5 pick menu instead of a cycler · §1.4 title max 2 parts
- **Notes:**
  - ✅ GUI-4b (pick list; rarity in the name). Rarity (21) steps forward through seven rarities one click at a time and has no reverse.

### `cosmetics.editor_delete_confirm`: Titles & Tags » Edit titles » <id> » Delete
- **Rows:** 3
- **Opens from:** Editor entry > Delete
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §1.4 title max 2 parts
- **Notes:** none found.

### `cosmetics.display`: Staff » Display
- **Rows:** 6
- **Opens from:** /display, /displayformat (staff)
- **Rules to apply:** §4 confirm rule
- **Notes:**
  - Every text format is edited by typing raw MiniMessage/& text in chat ('default' resets, 'empty' clears), hard on Bedrock keyboards; the preview is only shown on the save confirm.

### `cosmetics.display_confirm`: Staff » Display » Save display
- **Rows:** 3
- **Opens from:** Display > edit a format
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §1.4 title max 2 parts
- **Notes:** none found.

### `cosmetics.tag_redeem`: Redeem tag
- **Rows:** 3
- **Opens from:** Right-click a tag item
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §5 make it one plain click
- **Notes:**
  - ✅ GUI-4 (lore says USE). Opened by right-clicking the tag item; the item lore says 'Right-click to redeem', which does not describe the Bedrock action (use item).

## crates (4 menus)

### `crates.crates`: Crates
- **Rows:** 6
- **Opens from:** /crates
- **Rules to apply:** §1.1 add Back at 45 · §5 make it one plain click
- **Notes:**
  - ✅ GUI-4 (plain click opens the preview; the Java shortcut is only in ?). Opening a crate straight from this menu needs right-click (open 1) or shift-click (open bulk) on Java; Bedrock must go through the preview screen first.
  - ✅ GUI-4 (one action line). Lore lists three click actions per crate on Java and one on Bedrock, so the same button is explained differently by platform.

### `crates.preview`: Crates » <crate>
- **Rows:** 6
- **Opens from:** Crates menu > left-click / tap a crate, /crates <crate>
- **Rules to apply:** §5 make it one plain click
- **Notes:**
  - On Bedrock every open takes two taps (crate, then Open here) because the right/shift-click shortcuts of the crates menu do not exist.
  - The three Open buttons share one lime dye icon and differ only by name (Open 1 / Open <bulk> / Open all).

### `crates.result`: Crates » <crate> » Opened <count>
- **Rows:** 6
- **Opens from:** After a bulk open (Open <bulk> / Open all)
- **Rules to apply:** §1.4 title max 2 parts
- **Notes:**
  - 'And N more' sits in slot 44, a border column slot of the frame, and points to the chat for the rest.

### `crates.staff_crates`: Keys of <player>
- **Rows:** 6
- **Opens from:** /crates admin view <player> (staff)
- **Rules to apply:** §1.1 add Back at 45 · §5 pick menu instead of a cycler
- **Notes:**
  - ✅ GUI-4b (pick list). Profile switch (4) only cycles forward.

## depths (3 menus)

### `depths.dungeon`: The Depths » Mine / Forest / Combat (selected wing)
- **Rows:** 6
- **Opens from:** /dungeon
- **Rules to apply:** §1.1 add Back at 45
- **Notes:**
  - The three wing tabs replace the top border panes (slots 3-5) and the Satchel button sits in the nav row at slot 47 right next to Info (48); both deviate from the plain frame of UI_STYLE §5.1 (the code calls it the PHASE-4 mockup).
  - Tier layout changes with the tier count per wing (12 / 11 / 10 tiers give three different shapes), so the same tier number is in a different slot on each tab.
  - ✅ GUI-4 (requirement in the name). Locked tiers all look the same (gray dye) and clicking one only shows an action-bar line; the resource and the level needed are only in the lore.

### `depths.satchel`: Satchel
- **Rows:** 6
- **Opens from:** /dungeon > Satchel button (47)
- **Rules to apply:** §4 confirm rule
- **Notes:**
  - ✅ GUI-BUGS (confirm). Sell all and Upgrade (up to $6,400,000) are one-click with no confirmation, against UI_STYLE §5.2 ('anything above a money threshold').
  - Feature buttons use non-standard bottom-row slots 46, 47, 51, so Sell chest (46), Sell all (47) and Info (48) sit shoulder to shoulder and are easy to mix up.
  - Two different ways to sell (Sell all, Sell chest) plus per-item Sell inside the item menu; the Sell chest only handles inventory items, which is not obvious from the name.

### `depths.satchel-item`: Satchel » <item name>
- **Rows:** 3
- **Opens from:** Satchel > click a drop entry
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row menu has no Close button and no Info/Help (infoKey is not set and 3-row frames show none); the only exits are Back (10) or Esc.
  - Sell all N has no confirm even for stacks worth a lot, and sits between Back/item and Take buttons that look alike (gold ingot vs chest vs ender chest).
  - Sell is 'Sell all' only: there is no way to sell part of a stack (no number picker), and the Take buttons cannot pick an amount either.
  - Back sits at slot 10 (inside the frame) instead of the standard slot 45 used by the 6-row menus.

## economy (15 menus)

### `economy.sell`: Sell
- **Rows:** 6
- **Opens from:** /sell; Sell button of /shop and every shop category
- **Rules to apply:** §5 make it one plain click
- **Notes:**
  - Slot 53 is a second, identical Sell button; UI_STYLE 5.1 reserves 53 for Next page, so the menu has no consistent corner and two buttons do the same thing.
  - 'Sell inventory' (46) only moves items into the chest and does not sell; the name reads like a sell-everything button and needs a second click on Sell.
  - Info text (48) lists 'click, shift-click or drag' as the ways to fill the chest; shift-click and drag do not exist on Bedrock touch, which has only tap-and-place or the Sell inventory button.

### `economy.transactions`: Transactions
- **Rows:** 6
- **Opens from:** /transactions, /txs
- **Rules to apply:** §5 pick menu instead of a cycler
- **Notes:**
  - ✅ GUI-4 (pick lists for currency, type and time). Type filter (47) steps through every ledger reason one click at a time, forward only (no reverse, no list); reaching a reason can take 10+ clicks.
  - ✅ GUI-BUGS (Refresh moved to 6). Slot 52 Refresh sits next to the filters although the list also reloads on a filter change.

### `economy.bank`: Bank
- **Rows:** 6
- **Opens from:** /bank; Bank button of the island menu
- **Rules to apply:** §3 hide until built
- **Notes:**
  - The two halves are not mirrored: personal History is at 38 under Interest, island History is at 25 in the button row, and only the personal side has Bank tiers.
  - Deposit/Withdraw/History turn into grey 'locked' items for island roles without permission; the click does nothing and the reason is only in the lore.
  - The two halves use identical button names (Deposit, Withdraw); only the left/right position tells personal from island, and the Info text (48) relies on 'Left / Right' too.

### `economy.bank_deposit`: Bank » Deposit
- **Rows:** 6
- **Opens from:** Bank > Deposit button (personal or island)
- **Notes:** none found.

### `economy.bank_withdraw`: Bank » Withdraw
- **Rows:** 6
- **Opens from:** Bank > Withdraw button (personal or island)
- **Notes:** none found.

### `economy.bank_withdraw_confirm`: Bank » Withdraw » Are you sure?
- **Rows:** 3
- **Opens from:** Withdraw number picker > Confirm
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule · §1.4 title max 2 parts
- **Notes:**
  - ✅ GUI-1 (details on 13) + R3-41/GUI-4 (no interest loss text). The fee and 'this period's interest is lost' warning are only in the lore of the Confirm buttons; the centre item shows just 'You get: <net>'.

### `economy.bank_tiers`: Bank » Tiers
- **Rows:** 6
- **Opens from:** Bank > Bank tiers
- **Rules to apply:** §3 hide until built
- **Notes:**
  - 15 tier buttons in one grid; every tier shows 'Perks: None yet' because the perk list is empty, a placeholder line on all 15.
  - Locked tiers are clickable-looking items that do nothing; lore says 'Unlock the tier before first' (wording).

### `economy.bank_tier_confirm`: Bank » Tiers » Bank tier <n>
- **Rows:** 3
- **Opens from:** Bank tiers > next tier
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §1.4 title max 2 parts
- **Notes:** none found.

### `economy.bank_history`: Bank » History
- **Rows:** 6
- **Opens from:** Bank > History / Island bank history
- **Notes:** none found.

### `economy.admin_dashboard`: Economy
- **Rows:** 6
- **Opens from:** /eco admin (staff)
- **Rules to apply:** §1.1 add Back at 45 · §5 pick menu instead of a cycler
- **Notes:**
  - 15 tiles with long lore in one screen; seven are read-only and look the same as the eight clickable ones (only the lore says CLICK).
  - ✅ GUI-4b (pick list). Currency (31) cycles forward only.

### `economy.admin_reasons`: Economy » Reasons
- **Rows:** 6
- **Opens from:** Dashboard > Every reason / Sources / Sinks (staff)
- **Notes:**
  - ✅ GUI-4c (page in the title). Own paging code: the title shows no (page/pages) like other paged menus.

### `economy.admin_top`: Economy » Top earners
- **Rows:** 6
- **Opens from:** Dashboard > Top earners (staff)
- **Notes:**
  - ✅ GUI-4c (pages of 28, 84 by default). Shows at most 28 profiles, no paging.

### `economy.admin_transactions`: Economy » Biggest
- **Rows:** 6
- **Opens from:** Dashboard > Biggest transactions, Reasons > a reason, or a profile history (staff)
- **Notes:**
  - ✅ GUI-4c (pages of 28, 84 by default). Shows at most 28 rows with no paging.

### `economy.admin_alerts`: Economy » Alerts
- **Rows:** 6
- **Opens from:** Dashboard > Alerts, /eco admin <n> (staff)
- **Notes:**
  - ✅ GUI-4c (page in the title). Own paging code: the title shows no (page/pages).

### `economy.admin_alert`: Economy » Alert #<id>
- **Rows:** 6
- **Opens from:** Alerts > an alert (staff)
- **Rules to apply:** §4 confirm rule
- **Notes:**
  - Mark reviewed, False alarm and Open again change the alert with one click and no confirm.

## enchants (13 menus)

### `enchants.enchanter`: Enchanter
- **Rows:** 6
- **Opens from:** /enchanter
- **Rules to apply:** §1.1 add Back at 45 · §4 confirm rule
- **Notes:**
  - Five different flows (Apply, Upgrade, Combine, Remove, Armor) sit in one row, and the Combine screen itself sends players back to Upgrade for 'book onto a tool', so the split is not obvious.
  - Enchant guide uses KNOWLEDGE_BOOK, the same icon as the Help button in the nav row (slot 50); easy to confuse.
  - ✅ GUI-4b (slot screen skipped when one slot fits, tool list skipped with one tool). Every flow has 3-4 screens (tool, slot, book, confirm), so a single enchant takes about 5 clicks; the same Enchanter title is reused on the tool picker and the slot picker, so the player cannot tell which step they are on.
  - Armor button stays visible but dead when the wardrobe module is off (gray name, error sound only).

### `enchants.tool-pick`: Enchanter » Apply a book / Upgrade / Remove
- **Rows:** 6
- **Opens from:** Enchanter > Apply a book / Upgrade / Remove
- **Notes:**
  - ✅ GUI-4 (slot screen is `<mode> » <tool>`). Same title as the next screen (SlotMenu) and as the three modes' other steps; only the last crumb tells Apply / Upgrade / Remove.
  - Empty state tells players to get a tool at /forge but gives no button to go there.

### `enchants.slots`: Enchanter » Apply a book / Upgrade / Remove
- **Rows:** 6
- **Opens from:** Tool picker > click a tool
- **Rules to apply:** §3 hide until built
- **Notes:**
  - All five slots look clickable in every mode, but only the ones that fit the mode work (e.g. Apply: filled slots just beep); the mode is visible only in the last title crumb.
  - ✅ GUI-4. Same title as the tool picker before it, so the two steps are indistinguishable from the title.
  - Covered slots (2-slot enchants) are purple panes labeled 'Slot N'; clicking does nothing.

### `enchants.books`: Apply a book » Slot <n>
- **Rows:** 6
- **Opens from:** Enchanter > Apply a book > tool > empty slot
- **Notes:**
  - Books that do not fit are still shown as buttons that beep; with many books, the fitting ones are only grouped by sort order.
  - ✅ GUI-4b (the tool and slot on 4). Title crumb 'Apply a book' repeats, and 'Slot 3' is the only context; the tool being enchanted is not named anywhere in this menu.

### `enchants.upgrade`: Upgrade » <enchant name>
- **Rows:** 6
- **Opens from:** Enchanter > Upgrade > tool > filled slot
- **Notes:**
  - Only two buttons in a 6-row menu, placed far apart at 29 and 33 with a lot of empty space; a 3-row choice would do.
  - The 'with a book' button is disabled unless the book has exactly the same level as the enchant, which is only explained in its lore.

### `enchants.remove`: Remove » <enchant name>
- **Rows:** 6
- **Opens from:** Enchanter > Remove > tool > filled slot
- **Notes:**
  - When there is no scroll the button only says 'Buy one in the Book shop' with no link, so the player must back out three screens to buy it.
  - Two buttons in a 6-row menu, far apart (29, 33); same layout issue as Upgrade.

### `enchants.combine`: Enchanter » Combine books
- **Rows:** 6
- **Opens from:** Enchanter > Combine books
- **Notes:**
  - The Combine screen has a hint button that sends players to Upgrade, which shows that the two flows overlap in the player's head.
  - Hint button sits in the top border row (slot 4) with an XP-bottle icon that matches the Upgrade button on the hub, not a combine action.

### `enchants.shop`: Enchanter » Book shop
- **Rows:** 6
- **Opens from:** Enchanter > Book shop
- **Notes:**
  - Nine near-identical roll buttons (3 pools x 3 tiers) are told apart only by name and the BOOK / WRITABLE_BOOK / ENCHANTED_BOOK icon; the odds are 4-6 lore lines each.
  - Unavailable rolls (tier too low, none left today) turn into the same gray dye with the reason only in the lore.
  - Opening the shop waits for a database read (today's roll counts) with no Loading item, unlike UI_STYLE §5.4.
  - The Disenchant Scroll sits at the end of the first pool row (slot 16) and looks like part of the Mining row.

### `enchants.guide`: Enchant guide » Pickaxe / Axe / Hoe / Sword / Rod / Armor
- **Rows:** 6
- **Opens from:** Enchanter > Enchant guide
- **Notes:**
  - Six tabs fill slots 2-7 of the top row and replace the border and the right-hand orange glow pane, a deviation from the UI_STYLE §5.1 frame; switching tab re-opens the whole menu.
  - Entries are display-only items but look like buttons (no action line, nothing happens on click).
  - Opening the guide waits for a database read with no Loading item.

### `enchants.armor`: Enchanter » Armor
- **Rows:** 6
- **Opens from:** Enchanter > Armor
- **Notes:**
  - ✅ GUI-4 (set screen `Armor » <set>`). Title is exactly the same ('Enchanter » Armor') as the next screen (ArmorEntryMenu), which does not name the set.

### `enchants.armor-entry`: Enchanter » Armor
- **Rows:** 6
- **Opens from:** Enchanter > Armor > a set; also from the wardrobe set screen
- **Notes:**
  - ✅ GUI-4. The title does not name the set (identical to the set list), and the only place that does is the ARMOR_STAND icon in slot 16.
  - Dormant enchants (set level too low) show as a plain book with a yellow warning; they look like normal enchants at a glance.
  - Layout is not on the content grid: piece icons in column 2, slots across, set info in column 8 (about 15 buttons and 4 decorative items).

### `enchants.armor-enchant`: Armor » <enchant name>
- **Rows:** 6
- **Opens from:** Armor set screen > a filled slot
- **Notes:**
  - Four action buttons in a 6-row menu, grouped 28/29 and 33/34 with a gap in the middle; unlike the tool version (UpgradeMenu/RemoveMenu), which splits upgrade and remove into two screens.
  - Title shows only 'Armor » <enchant>', not the piece or the set.

### `enchants.armor-books`: Armor » Slot <n>
- **Rows:** 6
- **Opens from:** Armor set screen > an empty slot
- **Notes:**
  - Title shows only 'Armor » Slot 2'; the piece and set are not named, and the same book list as on the tool side (BookPickMenu) with different wording.

## islands (33 menus)

### `islands.island`: Island
- **Rows:** 6
- **Opens from:** /is, 'Island' in /menu
- **Rules to apply:** §1.1 add Back at 45 · §4 confirm rule · §1.1/§1.2 never a dead end · §3 hide until built
- **Notes:**
  - Opened with a command (also from the /menu hub, which closes itself first), so there is no parent: no Back button, the player has to reopen /menu
  - 15 actions + 3 nav: dense but within limits; Warps (13) and Visit islands (22) overlap, as do Members (11) and Invites (16) with the buttons in islands.members
  - Disband (leader) is one click from the main island screen, placed at 40 with no gap from the rest, though it deletes the island; it is only protected by a confirm
  - Disband is offered even when other members exist; the confirm text is the only warning
  - Visitors lock/unlock is one click with no confirm and teleports visitors out
  - Island chat (20) is a toggle that looks like a normal button (no on/off state in the name; the icon changes between OAK_SIGN and GLOW_ITEM_FRAME only)
  - Bank and Upgrades are muted placeholders when their module is off, but sit between live buttons
  - ✅ GUI-4b (Members lost Permissions, Settings, Notes; Invites stays there, /is stays as built per §7.5). Duplicate shortcuts: Members screen also has Permissions, Settings, Notes and Invites; same destination from two places

### `islands.members`: Island » Members
- **Rows:** 6
- **Opens from:** Members in /is menu
- **Notes:**
  - ✅ GUI-4b (bar + 46 Invites + 47 Invite). The bottom row is full (45-53 all used) with non-standard slots 46, 47, 51, 52 (UI_STYLE: only 45/48/49/50/53); Notes sits at the top at slot 6
  - Coop players cannot be added from the GUI (command only); 'Invite a player' lists online players without an island only
  - ✅ GUI-4b. Five shortcut buttons (Notes, Invites, Invite, Permissions, Settings) duplicate the island menu and crowd the member list
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.member-actions`: Members » <player>
- **Rows:** 3
- **Opens from:** Click a member in islands.members
- **Rules to apply:** §4 confirm rule · §1.1/§1.2 never a dead end
- **Notes:**
  - No Close/Info/Help: plain ARROW 'Back' at 22 (label has no glyph) instead of the framed 45 Back; inconsistent with the 6-row menus
  - Promote and demote apply immediately with no confirm; End coop has no confirm either
  - Slot 16 is Kick for other members, End coop for coops and Leave for yourself: one slot, three different (destructive) meanings

### `islands.permissions`: Island » Permissions » (n/6)
- **Rows:** 6
- **Opens from:** Permissions in /is menu, islands.members
- **Rules to apply:** §4 confirm rule · §1.4 title max 2 parts
- **Notes:**
  - ✅ GUI-4 (presets first, matrix behind Advanced). About 115 cells over 6 pages with only 4 permissions per page; the owner needs 20+ clicks and 5 page turns to review a role
  - ✅ GUI-4 (Strict / Normal / Open; Normal = reset to default). No presets, no 'copy from role', no 'reset to default'
  - Editable (concrete) and locked (glass) cells differ only in material; the legend is lore only
  - A flip is saved immediately with no confirm even for risky rights (Break, Containers, Bank withdraw, Spawners)
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.invites`: Island » Invites
- **Rows:** 6
- **Opens from:** Invites in /is menu, islands.members
- **Rules to apply:** §4 confirm rule
- **Notes:**
  - Received and sent invites share one list of identical heads; the player must read the lore to know which is which, and the click does a different thing for each (accept/deny screen vs cancel confirm)
  - Sent invites are only clickable for roles that may invite; for others the head is display-only with a 'locked' line
  - Refreshes itself while open (tick), which can swap the list under the cursor
  - Empty state 'No invites' gives no link to invite someone
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.invite-actions`: Invite » <island>
- **Rows:** 3
- **Opens from:** Click a received invite (portal choice / /is invites)
- **Rules to apply:** §4 confirm rule · §1.1/§1.2 never a dead end
- **Notes:**
  - Accept/Deny are single-slot buttons, unlike the 3-slot confirm targets in ConfirmMenu (10-12 / 14-16), so the mis-tap risk differs between the two screens
  - Plain ARROW 'Back' at 22 instead of the framed Back; no Close

### `islands.settings`: Island » Settings
- **Rows:** 6
- **Opens from:** Settings in /is menu, islands.members
- **Rules to apply:** §5 pick menu instead of a cycler
- **Notes:**
  - ✅ GUI-4 (pick lists; value in the name). Weather has 3 values and Time has 5; both cycle one step per click, so reaching the wanted value can take 4 clicks
  - On/off is shown only by the name colour and a lore line while the icon stays the same (only Weather changes its icon); /settings uses red/lime dye: two different toggle looks in the game
  - Visitor alerts duplicates the personal 'Visitor arrivals' in menus.settings
  - Slot 22 is left empty between Visitor fly (21) and Weather (23) to group the pickers, which breaks the 10-16 row logic of the first row
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.chat-colors`: Island » Chat colors
- **Rows:** 6
- **Opens from:** Chat colors in /is menu
- **Rules to apply:** §5 pick menu instead of a cycler
- **Notes:**
  - ✅ GUI-4 (pick list of the palette). Each role cycles one colour per click through an 8-colour palette (up to 7 clicks, each an asynchronous save); no list of colours to pick from
  - The roles sit at 19/21/23/25 (odd spacing) while the rest of the screen is empty; the screen is mostly empty space
  - No preview of how a chat line will look
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.visit`: Island » Visit islands
- **Rows:** 6
- **Opens from:** Visit islands in /is menu, /is visit
- **Notes:**
  - Five tabs and a Warps button share the top row (2-7), unlike any other menu(UI_STYLE puts nav only on the bottom row)
  - Rating and liking an island are command-only (/is rate, /is like); the menu has no buttons for them
  - The Visit <-> Warps chain stacks children, so Back needs several presses
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.warps`: Island » Warps
- **Rows:** 6
- **Opens from:** Warps in /menu hub (/is warp), Visit, Island menu
- **Notes:**
  - ✅ GUI-4b ("Adding warps" sets one where you stand; Bedrock form). New warps can only be made with /is setwarp or a sign; the menu only explains it
  - In own-island mode a click edits (leader/admin/mod) instead of teleporting; the same item does two different things depending on the viewer and the mode
  - Two non-standard bottom slots (47 Islands, 51 Your warps) next to the standard ones
  - The Visit <-> Warps child chain grows the back-stack
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.warp-edit`: Warps » <warp>
- **Rows:** 6
- **Opens from:** Click a warp in your own warps
- **Rules to apply:** §5 pick menu instead of a cycler
- **Notes:**
  - ✅ GUI-4b (Rename on 30; Delete moved to 40). No rename: a warp's name can only be changed by deleting and re-creating it
  - ✅ GUI-4 (pick list). Category cycles through all categories one click at a time
  - Public/Private reads as a plain button: the wording 'Public' / 'Private' is the state, not the action
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.warp-icons`: Warps » Icon
- **Rows:** 6
- **Opens from:** Icon in islands.warp-edit
- **Notes:**
  - Every icon item is named 'Use this icon', so the list cannot be scanned by name
  - The current icon is not marked
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.notes`: Island » Notes
- **Rows:** 6
- **Opens from:** Island notes in /is menu, islands.members
- **Notes:**
  - ✅ GUI-4b (Bedrock form popup). Adding or editing text needs chat typing (or a temporary written book), which is hard on Bedrock touch
  - Non-standard bottom row: 46 Open as book, 47 Add a note, 51 Clear done notes, 52 Deleted notes
  - Clicking a note opens another menu for done/edit/pin/delete; no quick tick from the list
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.note-actions`: Notes » <note>
- **Rows:** 3
- **Opens from:** Click a note in islands.notes
- **Rules to apply:** §1.1/§1.2 never a dead end
- **Notes:**
  - Plain ARROW 'Back' at 22, no Close/Info/Help; inconsistent with framed 6-row menus
  - ✅ GUI-4b (Bedrock form popup, old text filled in). Change text needs chat typing
  - Unpin (15) and Pin (14) are two slots for one idea

### `islands.notes-deleted`: Notes » Deleted
- **Rows:** 6
- **Opens from:** Deleted notes in islands.notes
- **Notes:**
  - Read-only: a deleted note cannot be restored from the list
  - Empty state shows 'Nothing deleted'
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.stats`: Island » Stats
- **Rows:** 6
- **Opens from:** Island stats in /is menu
- **Rules to apply:** §3 hide until built
- **Notes:**
  - ✅ GUI-4 (hidden). Several categories are 'Coming soon' placeholders on the same grid, in muted colour that looks similar to the live ones
  - Clickable and read-only items look the same
  - 'Your part' (40) is a head with the same look as other info items but is actually a drill-down
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.stats-members`: Stats » <category>
- **Rows:** 6
- **Opens from:** A stat in islands.stats
- **Notes:**
  - Players beyond the shown heads collapse into one '+N more' paper with no way to see them
  - ✅ GUI-4 (cobweb). Empty state 'Nothing yet' with a BARRIER icon (looks like an error)
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.portal-choice`: Island portal
- **Rows:** 3
- **Opens from:** Walking into the island portal with pending invites (no island yet)
- **Rules to apply:** §1.2 add Back 18 / Close 22
- **Notes:**
  - ✅ GUI-4c (every invite). Shows at most 3 invites; the rest are silently hidden
  - ✅ GUI-4c (frame Back/Close and a ? guide; Esc still does nothing). No Close/Back/Help and no 'decline all': the only exit is Esc
  - ✅ GUI-4c (? explains the choice). A new player who has just landed at the portal sees an unexplained choice with no tutorial hint

### `islands.slots`: Island grid
- **Rows:** 6
- **Opens from:** /isadmin slots (staff)
- **Rules to apply:** §5 pick menu instead of a cycler
- **Notes:**
  - Hundreds of slots paged 28 at a time with no search or jump-to-slot
  - ✅ GUI-4b (pick list with counts). Filter cycles one state per click
  - Non-standard bottom slots 47 and 51
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.kick-backups`: Kick backups
- **Rows:** 6
- **Opens from:** /isadmin kickbackups (staff)
- **Notes:**
  - The header says 'Kicked by' even for backups made on disband or reset
  - No filter by player or island
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `islands.confirm-create`: Create island
- **Rows:** 3
- **Opens from:** /is create, island portal
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-disband`: Disband island
- **Rows:** 3
- **Opens from:** Disband island in islands.island (leader)
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - The only confirm for deleting an island and all progress; no typed name or cooldown
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-leave`: Leave island
- **Rows:** 3
- **Opens from:** Leave island in islands.island (members)
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-kick`: Kick
- **Rows:** 3
- **Opens from:** Kick in islands.member-actions
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-transfer-1`: Transfer
- **Rows:** 3
- **Opens from:** Make Leader in islands.member-actions
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-transfer-2`: Transfer
- **Rows:** 3
- **Opens from:** After transfer step 1
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - Two confirms in a row look identical apart from the shown item
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-solo-invite`: Invite
- **Rows:** 3
- **Opens from:** Invite a player when the inviter has no island
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-cancel-invite`: Cancel invite
- **Rows:** 3
- **Opens from:** Click a head in islands.invites
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-delete-warp`: Delete warp
- **Rows:** 3
- **Opens from:** Delete in islands.warp-edit
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-delete-note`: Delete note
- **Rows:** 3
- **Opens from:** Delete in islands.note-actions
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-clear-notes`: Clear notes
- **Rows:** 3
- **Opens from:** Clear done notes in islands.notes
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-restore-backup`: Restore
- **Rows:** 3
- **Opens from:** Click a backup in islands.kick-backups (staff)
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `islands.confirm-admin`: Admin action
- **Rows:** 3
- **Opens from:** /isadmin delete | reset | kick | setowner
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - One generic confirm for four very different admin actions (delete, reset, kick, set owner)
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

## logs (3 menus)

### `logs.hub`: Staff » Logs
- **Rows:** 6
- **Opens from:** /logs (aliases /log, /auditlog) with no arguments; /logs <sub> shortcuts skip it
- **Rules to apply:** §5 pick menu instead of a cycler
- **Notes:**
  - ✅ GUI-4 (pick lists). Time range and Radius are cycle-on-click buttons: to go from 1d to 7d you may click through every option, and there is no way to step backwards.
  - Category picker is single-choice only (one category or all), although the filter itself can hold several.
  - Who-picking is spread over 7 buttons, 4 of which close the menu and need typing in chat (Any player, Staff member, Item, Island); typing on Bedrock is slow.
  - Staff member button (slot 12) simply disappears for non-staff viewers, leaving a gap in the row of subjects.

### `logs.category`: Logs » Category
- **Rows:** 6
- **Opens from:** Hub > Category button
- **Notes:**
  - Picking a category returns to the hub right away; there is no way to combine two categories (for example Economy + Trade).

### `logs.results`: Logs » <subject of the search>
- **Rows:** 6
- **Opens from:** Hub > SEARCH; /logs player|pay|trade|chat|commands|staff|island|item <...>
- **Rules to apply:** §5 make it one plain click · §4 confirm rule
- **Notes:**
  - ✅ GUI-4c (a plain click opens the entry screen). Right-click is the only way to read the full details of an entry that also has a left-click action (punish or teleport); touch players on Bedrock can't right-click.
  - ✅ GUI-4c (Teleport/Punish are named buttons on the entry screen). Left-click on an entry teleports the staff member instantly, or closes the menu and opens /p with the chat line as evidence, with no confirm; easy mis-click on a dense list of 28 look-alike entries.
  - Four non-standard feature buttons at 46, 47, 51, 52 (UI_STYLE reserves 45-53 for Back/Info/Close/Help/Next); Show in chat at 47 sits next to Change search and is easy to mix up.
  - Previous page replaces Back at slot 45 on page 2+, so to leave the results you must use Change search or Close.
  - ✅ GUI-4c (? text rewritten). The Info tooltip says left-click teleports but does not mention the new left-click-to-punish on chat lines, and the 'Right-click' hint is only shown in item lore when a left action exists.
  - ✅ GUI-4c (page in the title). Not a PagedMenu: the title has no (n/m) page indicator as UI_STYLE §5 asks (page is only in the summary book), and the title carries the subject, which can be a long island id or UUID.

## menus (11 menus)

### `menus.hub`: Menu
- **Rows:** 6
- **Opens from:** /menu, /m, hotbar Nether Star 'Menu · Right-click'
- **Rules to apply:** §5 make it one plain click · §1.1/§1.2 never a dead end · §3 hide until built
- **Notes:**
  - 28 destination buttons + head + 3 nav = more than 30 targets on one screen; far above the ~20 limit
  - ✅ GUI-2 (15-button hub). 6 of the 28 entries are 'Coming soon' placeholders (Black Market, Quests, Events, Pets, Collection, Leaderboards) taking prime slots
  - Placeholders keep their colourful icon (only the name turns grey) although the info text promises grey icons, so they look clickable
  - Settings opens from two places (head at slot 4 and slot 40) and Help from two places (slot 41 and frame slot 50); the head's purpose is unlabeled
  - Store, Vote, Discord and Website only print a clickable link in chat; Bedrock cannot click chat links so these four dead-end on Bedrock
  - Profiles sits alone at slot 43, apart from the other 'getting around' entries (37-42), and there is no Tutorial entry although /tutorial exists
  - Each click closes the menu, so every command-opened screen starts without a parent: no Back to /menu anywhere in the game (see islands.island, profiles.profiles, tutorial.tutorial)
  - ✅ GUI-2/GUI-4 (item says Click; dead essentials copy removed). Hotbar item is opened with a Right-click, which is a hold-to-use on Bedrock touch; /menu is the fallback

### `menus.settings`: Settings
- **Rows:** 6
- **Opens from:** /settings, slot 4 head and slot 40 in /menu, /help topics
- **Rules to apply:** §1.1/§1.2 never a dead end · §3 hide until built
- **Notes:**
  - ✅ GUI-4c (one tab per group, no second page). About 31 switches across 8 rows and 2 pages: too many for one list, and nothing is searchable
  - Page 2 lists Display (Scoreboard, Menu item, Tips, Particles, Players in The Depths, Skill XP bar, Booster bar, Wardrobe trail), Sounds (Menu sounds, Event sounds) and Gameplay (Island auto-pickup, Drop guard, Sell all skips hotbar); its category labels sit at 10/19/28/37 like page 1 but only the first of a multi-row category is labelled
  - ✅ GUI-4c (no paging any more). SettingsMenu does its own paging (not PagedMenu), so the title never shows the page '(1/2)' that every other paged menu shows (UI_STYLE 5.1); players do not know a second page exists until they see Next
  - ✅ GUI-4c (On/Off in the name). State is shown only by dye colour (red vs lime) and by the lore line 'Status: On/Off'; the dye colours are a hard distinction for colour-blind players, and there is no on/off wording in the item name
  - Name colours are inconsistent: most names are flame-yellow, some entries are white or have no colour tag
  - ✅ GUI-4 (unbuilt switches hidden). 'Players in The Depths' is a permanent Coming-soon placeholder inside the list
  - 'Particles' and 'Wardrobe trail' (Set trail) overlap in meaning
  - The Chat category mixes unrelated things: island pillar and task notices, the daily reminder, and the staff-only Island chat spy
  - Social category holds island visitor/note alerts that belong with island settings (islands.settings has Visitor alerts too)
  - Opened as a child of the hub only when started from /menu; /settings opens it with no Back
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `menus.help`: Help
- **Rows:** 6
- **Opens from:** /help, slot 41 in /menu, frame slot 50 of every menu, 'About this menu' of other menus
- **Rules to apply:** §3 hide until built
- **Notes:**
  - Help & guides (frame slot 50) on this very screen reopens Help, a no-op button
  - FAQ opens a topic directly while the other categories open a list: same-looking buttons, different behaviour
  - Combat & Events is a Coming-soon placeholder inside the category list
  - Categories have no live counts/previews, so the player cannot tell where an answer lives; no search
  - Back from topics returns to the Help root even when the player arrived from another menu's 'About this menu'
  - Icons repeat the ones used by features (IRON_PICKAXE = The Depths, EMERALD = Economy/Shop, GRASS_BLOCK = Islands/Hub Island/Staff Worlds)

### `menus.help-category`: Help » <category>
- **Rows:** 6
- **Opens from:** A category in /help
- **Notes:**
  - Several topics reuse the same icon (iron_pickaxe, armor_stand, nether_star), so the list is hard to scan by icon
  - Topics are listed without a one-line description of where they apply, only the summary in the lore
  - Info (48) and Help (50) on a help screen go to /help, which is already where the player is
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `menus.help-topic`: Help » <topic>
- **Rows:** 6
- **Opens from:** Topic in /help (and 'About this menu' of other menus)
- **Notes:**
  - ✅ GUI-4c (Bedrock: click a page for a text popup). The guide itself lives in the lore of non-clickable items: the instructions say 'Hover the pages', and hover is not available on Bedrock touch/controller
  - ✅ GUI-4 (14 pages in two rows + test). Pages beyond MAX_PAGES (7) are silently cut; long topics lose content
  - 'Show in chat' prints click events ([Take me there]) that Bedrock players cannot click; 'Take me there' in the menu does the same without chat
  - Back goes to the category, not to the menu from which 'About this menu' was opened
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `menus.help-commands`: Help » Commands
- **Rows:** 6
- **Opens from:** 'All commands' in /help, 'Commands' on a topic
- **Notes:**
  - Clicking a command closes the menu and prints a clickable chat suggestion: two steps, and the click event cannot be used on Bedrock
  - No category filter and no search; the full list is many pages (alphabetical)
  - Commands the player has no permission for may still be listed (staff entries use a different icon only)
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `menus.staff`: Staff hub
- **Rows:** 6
- **Opens from:** /staff
- **Rules to apply:** §1.1 add Back at 45 · §1.1/§1.2 never a dead end
- **Notes:**
  - Every tool except the Config editor closes the hub and opens its own screen with no Back to /staff (only /admin opens as a child)
  - Vanish (30) has no permission gate and no state shown (no on/off in the icon), so staff cannot see whether vanish is currently on
  - Config editor uses a COMPARATOR, the same icon as player Settings in /menu, so the two settings screens look alike
  - Entries the viewer may not use stay visible with a 'locked' line instead of being hidden, so staff see dead buttons
  - Slot 25 (NPCs) and 34 (Island grid) break the visual row pattern; Tutorial stats floats alone at slot 40

### `menus.admin`: Admin
- **Rows:** 6
- **Opens from:** /admin (/staff -> Config editor)
- **Notes:**
  - 47 config files in one alphabetical list over 2 pages: no grouping by module, no search
  - Files are named after their module with no description on the item, so a non-technical staff member cannot tell which file holds which setting
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `menus.admin-file`: Admin » <file>
- **Rows:** 6
- **Opens from:** A file in /admin
- **Rules to apply:** §4 confirm rule · §5 pick menu instead of a cycler
- **Notes:**
  - Up to 124 settings (5 pages) with no search or sub-grouping
  - Booleans flip immediately, with no confirm, even for risky settings
  - Text and decimal edits need typing in chat (slow on Bedrock); resetting to default is only possible by typing 'default' in that prompt
  - ✅ GUI-4b (pick list). Enum settings cycle one value per click (no list of choices)
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `menus.admin-list`: Admin » <file> » <setting>
- **Rows:** 6
- **Opens from:** A list-valued setting in /admin
- **Rules to apply:** §4 confirm rule · §1.4 title max 2 parts
- **Notes:**
  - Clicking an entry removes it (via confirm) - the most common action is destructive and the item reads as a plain row
  - Adding needs chat typing; '+ Add' sits at non-standard slot 51 (UI_STYLE puts nav at 45/48/49/50/53 only)
  - No edit or reorder of an existing entry; to change one the staff must remove and add
  - Empty list shows 'The list is empty' cobweb with no hint what values are valid
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `menus.confirm-remove-entry`: Remove entry
- **Rows:** 3
- **Opens from:** Click an entry in menus.admin-list
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

## moderation (22 menus)

### `moderation.punish`: Punish » <player> » (Dry run)
- **Rows:** 6
- **Opens from:** /p <player> (alias /punish); from a /logs chat line with that line as evidence; /p <player> --dry
- **Rules to apply:** §4 confirm rule · §1.1/§1.2 never a dead end · §1.4 title max 2 parts
- **Notes:**
  - Does not call frame(): gray filler everywhere, so there is no Back, no Info (48 is History) and no Help (50 is More reasons); infoKey 'punish.info' (which explains the four quarters) is never shown.
  - About 34 clickable buttons on one screen (24 reason buttons, 4 warns, 8 bottom-row buttons, counter, head), far past ~20; the four quarters (mutes top-left, bans top-right, warns bottom-left, small bottom-right) have no labels.
  - A single click applies the next ladder step at once, including bans and permanent bans (menu.confirm is false); the only safety nets are a 15 s duplicate check and a 2 minute Undo.
  - The player head is a hidden multi-purpose button: it undoes your last punishment, opens approvals (Approve/Deny) or opens suggestions (Apply/Dismiss), depending on state; nothing in its look says it is clickable.
  - Red stained-glass dividers (13, 22, 40) clash with UI_STYLE §5.1, where red is reserved for corner accents and errors; Silent and Dry run use gray/lime dye toggles instead of the lime/red 'Enabled/Disabled' pair.
  - Silent is off on every open, so a staff member who forgets it announces a ban publicly; Dry run is a mode switch in the same row as live actions (51, 52), a mis-click away from live punishing.

### `moderation.suggestion`: Punish » <player> » Suggestion
- **Rows:** 3
- **Opens from:** /p <player> > click the head or the warning counter while a warn-rule/API suggestion is pending
- **Rules to apply:** §4 confirm rule · §1.1/§1.2 never a dead end · §1.4 title max 2 parts
- **Notes:**
  - Reached only through a hidden click on the player head or on the warn counter in /p; there is no visible 'Suggestion' button.
  - 3-row menu: no Close and no Info/Help (6-row-only fixed slots); Back sits in the border row at 22 instead of 45.
  - Apply (e.g. a mute) happens at once with no confirm, and Dismiss resets the counter, which cannot be undone from here.

### `moderation.categories`: Punish » <player>
- **Rows:** 6
- **Opens from:** /p <player> > More reasons (50)
- **Notes:**
  - Same title as the /p menu ('Punish > <player>'), so the player cannot tell which screen they are on.
  - On the live config all 20 reasons are already placed on /p (More reasons says 0 not placed), so this extra hop (/p > More reasons > category > reason) only repeats them with the whole ladder shown.

### `moderation.reasons`: Punish » <player> » <category>
- **Rows:** 6
- **Opens from:** More reasons > a category
- **Rules to apply:** §4 confirm rule · §1.4 title max 2 parts
- **Notes:**
  - A click applies the punishment (including bans) with no confirm, the same as /p; the whole ladder is only in the lore.
  - Duplicates the reason buttons of /p; the extra detail it adds (the full ladder) is the part /p lacks.

### `moderation.history`: History » <player>
- **Rows:** 6
- **Opens from:** /history <player> (alias /punishments); /p > Punishment history (48); /p > More reasons > Punishment history; appeal screen
- **Notes:**
  - Status is shown only by a concrete versus terracotta shade of the same colour (active versus ended), which is hard to tell apart and impossible for colour-blind staff; the word is in the name.
  - Lore is 8-12 lines per entry; with 28 entries per page, scanning for one ban means hovering each item.

### `moderation.history-entry`: History » <player> » #<id>
- **Rows:** 3
- **Opens from:** History > click an entry
- **Rules to apply:** §1.1/§1.2 never a dead end · §1.4 title max 2 parts
- **Notes:**
  - 3-row menu: no Close and no Info/Help; Back at 22 in the bottom border row instead of 45.
  - Evidence is shown as tiny lore lines and then sent to chat to read, so the useful part of the screen is the chat, not the menu.

### `moderation.request`: Punish » <player> » Request
- **Rows:** 3
- **Opens from:** /p <player> > click the head while an approval request is pending (approvers only)
- **Rules to apply:** §4 confirm rule · §1.1/§1.2 never a dead end · §1.4 title max 2 parts
- **Notes:**
  - Reachable only through a hidden click on the player head in /p; there is no 'Requests' button, so approvers must know to look for the head's lore.
  - Approve applies a ban or mute (maybe permanent) at once with no extra confirm; 3-row menu has no Close or Info.

### `moderation.deny-request`: Request » <player> » Deny
- **Rows:** 3
- **Opens from:** Request > Deny; also the [Deny] chat button (no parent)
- **Rules to apply:** §4 confirm rule · §1.1/§1.2 never a dead end · §1.4 title max 2 parts
- **Notes:**
  - A preset click denies immediately, with no confirm; the requester is told the reason.
  - 3-row menu: no Close and no Info; if opened from chat, Back just closes it.

### `moderation.watchlist`: Watchlist
- **Rows:** 6
- **Opens from:** /watchlist
- **Rules to apply:** §1.1 add Back at 45
- **Notes:**
  - A click on a head opens the punish screen directly, with no way to remove the player from the watchlist here (that is /watch remove).

### `moderation.sanctions`: Punish » Sanctions
- **Rows:** 6
- **Opens from:** /p <player> > Sanctions (45); /psanction
- **Rules to apply:** §4 confirm rule
- **Notes:**
  - Lifting a freeze or trade block and restoring money or a confiscated item are single clicks on small list entries with no confirm.
  - Locked ('You can't use this' / 'Senior staff only'), unavailable and 'player offline' all show as the same gray dye or muted button; the reason is only in the lore.
  - Each new sanction takes 3 to 4 screens (Sanctions > length > reason; money adds a number picker), and the length/reason menus close and reopen.

### `moderation.sanction-choice`: <player> » Sanctions » <sanction kind>
- **Rows:** 3
- **Opens from:** Sanctions > Freeze / Block trading / Remove money / Confiscate (length, then reason)
- **Rules to apply:** §4 confirm rule · §1.1/§1.2 never a dead end · §1.4 title max 2 parts
- **Notes:**
  - Picking the final reason applies the sanction (freeze, trade block, money removal or confiscation) immediately, with no confirm and no summary of what will happen.
  - 3-row menu: no Close and no Info/Help; Back sits at slot 22.
  - The same screen is used for lengths and for reasons, so the title ('<player> > Sanctions > Freeze balance') does not say which one is being chosen.

### `moderation.confiscate`: <player> » Confiscate: inventory / Confiscate: ender chest
- **Rows:** 6
- **Opens from:** Sanctions > Confiscate item (target must be online)
- **Rules to apply:** §4 confirm rule
- **Notes:**
  - Does not call frame(): no border, no Info/Help, and the items sit in the first 41 slots so the layout is the player's inventory flipped, not the standard content grid.
  - ✅ GUI-4c (confirm after the reason; the stack is checked again). Confiscation happens with one reason click and no confirm; the stack is taken from a live inventory that changes every second.

### `moderation.appeals-queue`: Appeals
- **Rows:** 6
- **Opens from:** /appeals (staff queue); staff join reminder; back from a decision
- **Rules to apply:** §1.1 add Back at 45
- **Notes:** none found.

### `moderation.appeal`: Appeals » #<id> » <player>
- **Rows:** 6
- **Opens from:** Appeals queue > click an appeal
- **Rules to apply:** §1.4 title max 2 parts
- **Notes:**
  - Accept, Reduce and Deny sit in the lower half (38, 40, 42) and slot 40 changes meaning (Reduce, 'Close appeal' or 'Already handled'), so the middle button is not always a decision.
  - The full appeal text and the evidence can only be read in chat (button click), not in the menu, so staff decide in one place and read in another.
  - Two different 'no' states for slot 40 use the same gray dye ('Too short to reduce' versus 'Close appeal').

### `moderation.appeal-deny`: Appeals » #<id> » Deny
- **Rows:** 3
- **Opens from:** Appeal > Deny
- **Rules to apply:** §4 confirm rule · §1.1/§1.2 never a dead end · §1.4 title max 2 parts
- **Notes:**
  - A preset click denies the appeal immediately with no confirm; the decision is final and the player is notified.
  - 3-row menu: no Close and no Info; Back at 22.

### `moderation.bulk-pick`: Bulk punish » Players
- **Rows:** 6
- **Opens from:** /p <player> > Bulk punish (47)
- **Notes:**
  - Only online players can be picked here; offline players need /pbulk with names typed, and the empty state just says so.
  - Selection is toggled by clicking heads with no counter on the heads themselves, so with 28+ online players the summary at slot 4 is the only way to see who is picked.
  - Clear (47) and Next (51) are in non-standard slots next to Info (48) and Help (50).

### `moderation.bulk-reason`: Bulk punish » Reason
- **Rows:** 6
- **Opens from:** Bulk punish > Next: pick the reason
- **Notes:**
  - Unlike /p the list is a flat 24-entry list with no categories, so reasons from cheating, chat and gameplay are mixed.

### `moderation.bulk-result`: Bulk punish » Done
- **Rows:** 3
- **Opens from:** After confirming a bulk punishment
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - Undo all is one click with no confirm and sits next to the Close button (11 versus 15), with a lime 'Close' that looks like a confirmation.
  - A 3-row result screen with no Back/Info; the standard Close is replaced by a lime concrete 'Close' button.

### `moderation.ack`: Warning
- **Rows:** 3
- **Opens from:** Opens by itself for a Bedrock player who has a warning to confirm (the /ack command confirms without this menu)
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - The warning text is not on the screen: the only content is the sentence 'You got a warning from staff. Follow the rules to avoid more.'; the reason, the rules link and the staff name are in chat only, so a Bedrock player confirms without seeing why.
  - No Close or Back, and no Info or Help on a 3-row screen: to dismiss it without confirming the player has to press Esc, and the menu is not re-opened afterwards (only chat reminders).

### `moderation.lockdown`: Raid lockdown
- **Rows:** 3
- **Opens from:** /lockdown (no argument)
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - ✅ GUI-4c (confirm for start and end). Starting a join lockdown (keeps new players out) is one click with no confirm, and the 'active' button is red concrete that ends it with one click as well.
  - Only the default lengths (10m chat, 5m join) can be chosen here; any other length needs the /lockdown command typed.
  - ✅ GUI-1/GUI-4c (frame Back/Close; ? text added). A 3-row menu with no Close and no Back, and the Info button (infoKey) is not shown on 3 rows.

### `moderation.notify`: Staff alerts
- **Rows:** 4
- **Opens from:** /pnotify
- **Rules to apply:** §1.1/§1.2 never a dead end
- **Notes:**
  - A 4-row menu with no Close and no Back, and the Info button (infoKey) is not shown on 4 rows, so the 'Only types your rank gets are shown' text is never visible.
  - The toggle label is the alert name only (for example 'Raid'); the on/off state is told only by the dye colour, and the lore does not say what a click does.
  - The first slot (13) is used for the empty state, but the toggles start at 10, so the layout changes between the two cases; the empty case also does not say who to ask.

### `moderation.report-monthly`: Report » <month name, e.g. October>
- **Rows:** 6
- **Opens from:** /preport [year-month]; the [/preport] link in the monthly report message
- **Rules to apply:** §1.1/§1.2 never a dead end
- **Notes:**
  - Eight of the nine report tiles are information only, with all the numbers in the lore; the screen is a read-only dashboard and only the sign at 40 does something.
  - Slot 45 is 'previous month' and not Back, so the menu has no way back to a parent screen (only Close), and the arrow's name is just the month name ('September') with no 'Previous month' text.
  - The reasons, reoffence and staff lists are long lore lines on a single item, so with many reasons or staff the tooltip is cut off by the screen height; 'Show in chat' is the only complete view.

## npcs (2 menus)

### `npcs.help`: <npc name>
- **Rows:** 3
- **Opens from:** Right-click/tap an NPC as a Bedrock player (no clickable chat)
- **Rules to apply:** §1.2 add Back 18 / Close 22
- **Notes:**
  - The explanation is only in lore, which Bedrock touch hides unless the player taps and holds
  - Open buttons are at 15 and 16 (off-centre) with the info at 13; the pair is not symmetric
  - No Close, Back or Help on a 3-row menu
  - Exists only because Bedrock cannot click chat; the same 'no clickable chat' gap is still open in menus.hub (Store/Vote/Discord/Website)

### `npcs.list`: NPCs
- **Rows:** 6
- **Opens from:** /skyblock npcs, 'NPCs & Holograms' in /staff
- **Notes:**
  - The action line 'Click to teleport' does not use the standard '» CLICK to ...' format of UI_STYLE 4.1
  - No filter by type, world or role; hundreds of entries are one alphabetical list
  - Empty state 'Nothing placed' gives no hint how to place one
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

## perks (2 menus)

### `perks.freerank`: Pick your tag
- **Rows:** 3
- **Opens from:** /freerank (after the playtime requirement)
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - ✅ GUI-BUGS (confirm). A single tap picks the tag for good ('Yours forever') with no confirm.
  - 3-row dialog has no Back/Close/Help; only Esc closes it.

### `perks.trash`: Trash
- **Rows:** 4
- **Opens from:** /trash, /disposal (admin and owner only)
- **Rules to apply:** §4 confirm rule · §1.1/§1.2 never a dead end
- **Notes:**
  - ✅ GUI-BUGS (confirm + Close). Items are destroyed on any close (Esc, walking away, server stop) with no confirm and no undo; the only warning is the lore on the lava bucket.
  - No Close, Back or Help button (not the standard 6-row frame); the only way out is Esc.

## profiles (5 menus)

### `profiles.profiles`: Profiles
- **Rows:** 3
- **Opens from:** /profiles, 'Profiles' in /menu
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - Opened with a command (also from the /menu hub, which closes itself first), so there is no parent: no Back button, the player has to reopen /menu
  - 3-row menu with no Close/Info/Help, as for all 3-row menus
  - A rank-locked slot is a clickable button that only prints a chat error; buying a slot goes through a separate emerald confirm
  - Only 3 profile slots at 11/13/15; the screen offers no explanation of what a profile is

### `profiles.profile`: Profiles » <profile>
- **Rows:** 3
- **Opens from:** Click a profile in profiles.profiles
- **Rules to apply:** §3 hide until built
- **Notes:**
  - Back is a plain ARROW at slot 22 (frame label), whereas islands 3-row menus use 'Back' with no glyph
  - Rename needs chat typing
  - Deleting the active profile is a GRAY_DYE button that does nothing, the reason is only in lore
  - Slot 13 is the summary but slot 11 is empty; the layout does not centre the actions

### `profiles.icon`: Profiles » Icon
- **Rows:** 6
- **Opens from:** Change icon in profiles.profile
- **Notes:**
  - The item name is the action line '» CLICK to pick' rather than the icon's own name
  - Current icon not marked
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `profiles.confirm-buy-slot`: Buy slot
- **Rows:** 3
- **Opens from:** Locked slot in profiles.profiles
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `profiles.confirm-delete`: Delete profile
- **Rows:** 3
- **Opens from:** Delete profile in profiles.profile
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - Three steps for one delete: menu click, confirm, then typing the profile name in chat; hard on Bedrock
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

## progression (8 menus)

### `progression.upgrade`: Island » Upgrades
- **Rows:** 6
- **Opens from:** /is upgrade; Upgrades source button of /is level
- **Rules to apply:** §3 hide until built
- **Notes:**
  - ✅ GUI-BUGS (hidden). Masteries (6) sits in the tab row as a muted 'Coming soon' item that looks like a tab but does nothing; /is masteries does not exist yet either.
  - Each upgrade item carries a very long lore (level, now, next, task list, island level gate, cost, state); the same data is repeated in the detail screen.

### `progression.upgrade_detail`: Island » Upgrades » <upgrade>
- **Rows:** 6
- **Opens from:** Upgrades > an upgrade
- **Rules to apply:** §1.4 title max 2 parts
- **Notes:**
  - Of up to 22 tiles only the hand-in tasks and Buy (40) are clickable; the 14 level panes and the other task tiles are display only.

### `progression.upgrade_confirm`: Island » Upgrades » <upgrade> » Buy upgrade
- **Rows:** 3
- **Opens from:** Upgrade detail > Buy
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §1.4 title max 2 parts
- **Notes:** none found.

### `progression.level`: Island » Level
- **Rows:** 6
- **Opens from:** /is level; Island Level button of /is upgrade
- **Rules to apply:** §3 hide until built
- **Notes:**
  - Only Upgrades (11), Challenge Book (16) and Recalculate (24) can be clicked; the seven other sources and all five pillars are display-only tiles that look the same.
  - Placeholders sit in the grid: the Collections source and the Conquest pillar are muted 'Coming soon' tiles.

### `progression.top`: Island » Top
- **Rows:** 6
- **Opens from:** /is top
- **Rules to apply:** §3 hide until built
- **Notes:**
  - ✅ GUI-BUGS (hidden). Two of the four ranking tabs (Weekly gain, Pillars) are 'Coming soon' placeholders in the tab row.
  - ✅ GUI-4 ('Showing: Solo islands · switch to Team'). Slot 6 is named after the board you are on ('Solo islands') but a tap switches to the other one ('CLICK for team islands'), so the name reads like the opposite of the action.
  - No paging: only the top 28 islands are reachable.

### `progression.task_book`: Island » Level » Challenge Book
- **Rows:** 6
- **Opens from:** Challenge Book button of /is level or /is upgrade
- **Rules to apply:** §1.4 title max 2 parts
- **Notes:** none found.

### `progression.task_tier`: Island » Challenge Book » Tier <n>
- **Rows:** 6
- **Opens from:** Challenge Book > a tier
- **Rules to apply:** §1.4 title max 2 parts
- **Notes:**
  - Only unfinished hand-in tasks are clickable; the other tasks (up to 15 per tier) are display-only tiles styled the same, the lore says CLICK only on hand-in ones.

### `progression.task_deliver`: Island » Challenge Book » Tier <n> » Hand in
- **Rows:** 6
- **Opens from:** Tier > an unfinished hand-in task
- **Rules to apply:** §4 confirm rule · §1.4 title max 2 parts
- **Notes:**
  - One click hands in all needed items and uses them up, no confirm.

## rewards (2 menus)

### `rewards.daily`: Daily Rewards
- **Rows:** 6
- **Opens from:** /daily
- **Rules to apply:** §1.1 add Back at 45
- **Notes:**
  - ✅ GUI-BUGS (28-day window inside the frame). The 30 day tiles fill slots 9-35 and 39-41, overwriting the frame's border columns (9, 17, 18, 26, 27, 35); the frame is broken and day order wraps across the border, unlike the 7-wide grid used elsewhere.
  - 30 day tiles plus streak, summary and two tabs: 34 items on one screen (more than ~20).
  - Only today's tile is clickable; claimed, tomorrow and later days look like the same kind of tile, with the action only in the lore.

### `rewards.playtime`: Playtime
- **Rows:** 6
- **Opens from:** /daily > Playtime tab
- **Rules to apply:** §1.1 add Back at 45
- **Notes:**
  - ✅ GUI-4 (reading order, centred). Titles fill 28, 30, 32, 34 and then 29, 31, 33, so milestones are not in reading order once there are more than four.

## shop (3 menus)

### `shop.shop`: Shop
- **Rows:** 6
- **Opens from:** /shop, /menu > Shop
- **Rules to apply:** §1.1 add Back at 45
- **Notes:** none found.

### `shop.category`: Shop » <category>
- **Rows:** 6
- **Opens from:** Shop > a category, /shop <category>
- **Rules to apply:** §5 make it one plain click · §4 confirm rule
- **Notes:**
  - Java quick-buy: right-click (buy a stack) and shift-click (pick an amount) are the only ways to a bigger amount; left-click buys 1 immediately with no confirm (owner decision 8), so a mis-click spends money.
  - Lore shows three different click actions per item (left/right/shift on Java, tap on Bedrock): long lore on every one of up to 28 items per page.
  - Locked items (skill level) and 'Not for sale right now' items stay in the grid with only a lore line explaining why.

### `shop.buy_amount`: Shop » <category> » Buy <item>
- **Rows:** 6
- **Opens from:** Shop category > shift-click (Java) or tap (Bedrock) on an item
- **Rules to apply:** §4 confirm rule · §1.4 title max 2 parts
- **Notes:**
  - Cancel is at 37 and Back is also at 45: two ways back with different positions; UI_STYLE 5.1 only has Back at 45.
  - Buy executes immediately at 39-41, no confirm, even for large totals (owner decision 8).

## skills (1 menus)

### `skills.main`: Skills » <player> (when viewing someone else)
- **Rows:** 6
- **Opens from:** /skills; /skills <player> (needs permission to see others)
- **Rules to apply:** §1.1 add Back at 45 · §3 hide until built
- **Notes:**
  - All six items are read-only (no action line, nothing happens on click) yet each skill is a 14+ line lore wall: description, progress, XP, total, next unlock, how to earn XP.
  - Shows only the next unlock per skill; there is no list of all unlocks and no button to /dungeon, although the info text says leveling opens Depths tiers.
  - ✅ GUI-4 (hidden). Farming and Fishing take two of the five skill slots as 'Coming soon' placeholders that look like skills but do nothing.

## stacking (1 menus)

### `stacking.spawner`: Spawner
- **Rows:** 6
- **Opens from:** Right-click a stacked spawner with an empty hand, or sneak + right-click it
- **Rules to apply:** §1.1 add Back at 45 · §5 make it one plain click · §4 confirm rule · §3 hide until built
- **Notes:**
  - ✅ GUI-BUGS (confirm). Take all removes the spawner block for good (the whole stack goes to the inventory) with one click and no confirm, right next to the harmless Take 1.
  - ✅ GUI-4 (display only: limit and speed in the name, no promise). Upgrades (31) has no action line and no real action: a click prints 'coming soon' in chat; it sits between the Add and Take rows as if it were part of them.
  - Add 1 / Add all use lime dye and Take 1 uses red dye, the same pair that UI_STYLE §5 reserves for the Enabled / Disabled toggle state.
  - Only reachable by right-clicking the block with an empty hand or while sneaking (no command), which is awkward for Bedrock touch players.

## staff (5 menus)

### `staff.inspect`: Inspect <player>
- **Rows:** 6
- **Opens from:** /inspect <player>; right-click a player with the Inspect tool (blaze rod) in staff mode
- **Rules to apply:** §1.1 add Back at 45 · §4 confirm rule · §1.1/§1.2 never a dead end
- **Notes:**
  - ✅ GUI-4c (Punish, Logs and Reports keep Inspect as Back; inventory views still close). Back is inconsistent: Wardrobe, Item box and Tutorial open with Inspect as the parent (Back returns here), but Inventory, Ender chest, Teleport, Punish, Logs and Reports close the menu and run a command, so the staff member has to type /inspect again to return; Inspect itself has no Back.
  - Freeze is a one-click toggle with no confirm, sitting two slots from Teleport and next to Punish (33).
  - Slot 29 is Ender chest for online players and a grey 'Offline' item for offline ones, and the whole row 28-32 changes shape (Inventory, Teleport, Freeze disappear), so the muscle-memory positions move.
  - Feature buttons are scattered with gaps (22, 28-34, 40, 41) and the permission-gated ones appear and disappear (31, 40, 41) so the layout differs per rank.

### `staff.reports`: Reports » Open | Claimed | Closed | About <player>
- **Rows:** 6
- **Opens from:** /reports (and /reports open|claimed|closed); /reports player <name>; Inspect > Reports; staff-mode Reports tool
- **Rules to apply:** §1.1 add Back at 45 · §1.1/§1.2 never a dead end
- **Notes:**
  - ✅ GUI-4c (tabs on 3-5, in place). The Open/Claimed/Closed tabs are at 46, 47 and 51 in the nav row, next to Info (48) and Help (50), not in the standard nav slots, and not in the top row.
  - ✅ GUI-4c (tabs and Refresh stay in the same window; a report's Back returns to the list). No Back on the first page (it is opened by a command), and a clicked report replaces the list without a parent, so after Claim, Teleport, Inspect or Punish the staff member has to type /reports again to get the list back.
  - ✅ GUI-4c (the Closed tab says so). Closed shows only the last 100 reports (closed-shown in staff.yml) and the screen never says so; an older report can only be found through /reports player <name> (up to 200).

### `staff.report`: Reports » #<id>
- **Rows:** 6
- **Opens from:** Reports list > click a report
- **Rules to apply:** §1.1 add Back at 45 · §1.1/§1.2 never a dead end · §3 hide until built
- **Notes:**
  - No Back to the reports list (the menu is opened without a parent), and Claim, Unclaim, Teleport, Inspect and Punish all close the menu, so the staff member loses their place in the list.
  - ✅ GUI-4c (refreshes in place). Claim and Unclaim give feedback only in chat and close the menu; the screen is not refreshed to show the new state.
  - 'Close report' is a red concrete block (red is reserved for corner accents and errors in UI_STYLE) in the same row as Punish, and is the only way to finish a report.
  - Teleport on an offline player shows a grey 'Offline' item that looks like a button but does nothing.

### `staff.outcome`: Close #<id>
- **Rows:** 3
- **Opens from:** Report > Close report
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - A click closes the report and messages the reporter at once, with no confirm.
  - ✅ GUI-BUGS (lore says so). Invalid is the only outcome with a consequence for the reporter (it counts as a false report and can suggest a punishment), but its lore says only 'No rule was broken' and the item is a barrier, which looks like a Close button.
  - Punished and Warned only record the outcome; nothing checks that a punishment was actually given, so the staff member must have used /p first.
  - 3-row menu with no Back, no Close and no Info (nav is only set on 6 rows), although it is opened as a child of the report.

### `staff.report-reason`: Report <player>
- **Rows:** 6
- **Opens from:** /report <player> (without a reason)
- **Rules to apply:** §1.1 add Back at 45 · §4 confirm rule
- **Notes:**
  - A single click on a reason sends the report with no confirm, and a reporter has a 60 second cooldown and at most 3 open reports, so a mis-tap wastes one.
  - The reason list comes from staff.yml (up to 15 slots); the first 8 reasons get fixed icons and any extra reason reuses the name-tag icon.

## tools (4 menus)

### `tools.forge`: Forge » Pickaxes / Axes / Swords / Hoes / Rods
- **Rows:** 6
- **Opens from:** /forge; Blacksmith NPC; tool menu > Open the Forge
- **Rules to apply:** §1.1 add Back at 45
- **Notes:**
  - Five tool tabs replace the top border panes (slots 2-6) and Starter kit/Blueprints use non-standard slots 47 and 51; deviates from UI_STYLE §5.1.
  - ✅ GUI-4 (the 'More tiers later' tile is gone). Hoes and Rods tabs hold a single tier plus 'More tiers later', so two of the five tabs are almost empty for now.
  - Clicking Tier 1 of a tool you do not own silently claims the whole starter kit and closes the menu, which is not what the button name suggests.
  - A locked tier only gets a red mark in the name; which of tool, skill, materials or money is missing is buried in 8+ lore lines, and clicking it answers with a chat line.

### `tools.tool`: Tool
- **Rows:** 6
- **Opens from:** Shift + right-click a custom tool (setting-dependent); /tool with the tool in hand
- **Rules to apply:** §1.1 add Back at 45 · §5 make it one plain click · §1.1/§1.2 never a dead end
- **Notes:**
  - Opening needs shift + right-click while holding the tool (or /tool with it in hand); Bedrock touch players cannot sneak-use easily, and no hub menu links here.
  - Five perk slots (29-33) look like buttons but are display only; filled ones say 'Change it at the Enchanter' without a way to open it.
  - Prestige is the same slot for ready and not ready: a gray firework star that looks clickable and only plays an error sound.
  - Forge shortcut at slot 47 and no Back when opened by shift-click (no parent), so the only exit is Close.

### `tools.blueprints`: Forge » Blueprints
- **Rows:** 6
- **Opens from:** Forge > Blueprints button (51)
- **Rules to apply:** §4 confirm rule
- **Notes:**
  - A blueprint you cannot afford still opens the confirm dialog; the shortage only shows after you confirm, as a chat error.
  - The list only reads blueprints in the inventory; if you move one while the menu is open the entry becomes 'Unknown blueprint' with no clear next step.

### `tools.skins`: Tool » Skins
- **Rows:** 6
- **Opens from:** Tool menu > Skin button (40)
- **Notes:**
  - ✅ GUI-4 (bucket icon). Remove skin is a barrier item in the top border row (slot 4), the same icon as Close at 49, so the two are easily confused.
  - Not-owned skins show the same gray dye for every skin and no preview of the skin, so players cannot tell what they would be buying or earning.

## trade (4 menus)

### `trade.trade`: Trade » <player>
- **Rows:** 6
- **Opens from:** /trade <player> (after they accept)
- **Notes:**
  - ✅ GUI-BUGS. Info (48), Help (50) and Close (49) all close the window and so CANCEL the trade for both players (items go back); a stray tap on the standard nav ends the trade.
  - Items are offered by clicking items in your own inventory (the bottom half), not in this menu; only the 'Empty slot' lore and Info explain it.
  - ✅ GUI-BUGS (Cancel on 53). Slot 45 is Cancel trade (a destructive button) where every other menu has Back; UI_STYLE 5.1 reserves 45 for Back/Previous.

### `trade.money`: Trade » Money offer
- **Rows:** 3
- **Opens from:** Trade > Your money offer
- **Rules to apply:** §1.1/§1.2 never a dead end
- **Notes:**
  - Back is at slot 18 instead of 45 (UI_STYLE 5.1); the other 3-row dialogs have no Back at all.
  - 3-row menu has no Close or Help.

### `trade.money_picker`: Trade » Money offer » Offer <currency>
- **Rows:** 6
- **Opens from:** Trade > Money offer > a currency
- **Rules to apply:** §1.4 title max 2 parts
- **Notes:**
  - Info (48), Help (50) and Close (49) close the window and cancel the trade.

### `trade.player_picker`: Trade with
- **Rows:** 6
- **Opens from:** /trade without a name
- **Notes:** none found.

## tutorial (8 menus)

### `tutorial.tutorial`: Tutorial
- **Rows:** 6
- **Opens from:** /tutorial
- **Rules to apply:** §1.1 add Back at 45 · §1.1/§1.2 never a dead end
- **Notes:**
  - Opened with a command (also from the /menu hub, which closes itself first), so there is no parent: no Back button, the player has to reopen /menu
  - Slot 24 changes meaning with the player's state (Skip, Start, Where next?) and is the one that decides whether you progress
  - 'What unlocks when' is lore-only on a non-clickable door; the content that matters (which feature unlocks at which step) is only readable via tooltip
  - Take me there (20) and I'm stuck (21) are only drawn during an active step, leaving a different layout per state
  - No tutorial entry in /menu although the hub has an Island and Help entry
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `tutorial.steps`: Tutorial » Steps
- **Rows:** 6
- **Opens from:** Steps in tutorial.tutorial
- **Notes:**
  - Only the current step is clickable (Take me there); done and later steps look the same but do nothing
  - The step list is a status screen; the 'Take me there' action is hidden behind the one blaze-powder item
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `tutorial.optional`: Tutorial » Optional
- **Rows:** 6
- **Opens from:** Optional steps in tutorial.tutorial
- **Notes:**
  - A click closes the menu and highlights the target; the player loses the list and must reopen it
  - Done steps are green dye and not clickable, so they cannot be redone for a reminder
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `tutorial.where-next`: Where next?
- **Rows:** 3
- **Opens from:** 'Where next?' in tutorial.tutorial after finishing
- **Rules to apply:** §1.2 add Back 18 / Close 22
- **Notes:**
  - A child of TutorialMenu with no Back, Close or Help; Esc is the only exit (3-row dead end)
  - Three destinations only; Shop, Sell, Forge and Skills are not offered after the tutorial

### `tutorial.staff`: Tutorial » <player>
- **Rows:** 6
- **Opens from:** /tutorial admin <player> (staff)
- **Rules to apply:** §4 confirm rule
- **Notes:**
  - The Skip and Reset confirms show a generic 'Please confirm' paper instead of the player's name
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `tutorial.stats`: Tutorial » Stats
- **Rows:** 6
- **Opens from:** Tutorial stats in /staff, tutorial.staff (staff)
- **Rules to apply:** §5 pick menu instead of a cycler
- **Notes:**
  - Dense, numbers only in tooltips: 11 step cards + header + 2 summary cards + 3 controls in one screen, all read through lore
  - ✅ GUI-4b (pick lists). Period and client filters cycle one value per click
  - Step cards are display-only; no drill-down for where players drop off
  - Slots 48 'About this menu' and 50 'Help & guides' both close this menu and open /help; Back from help returns to the Help root, not to this menu (player loses their place)

### `tutorial.confirm-skip`: Skip tutorial
- **Rows:** 3
- **Opens from:** Skip tutorial in tutorial.staff
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - The shown item is null, so the default 'Please confirm' paper is shown with no player name
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

### `tutorial.confirm-reset`: Reset tutorial
- **Rows:** 3
- **Opens from:** Reset tutorial in tutorial.staff
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - The shown item is null, so the default 'Please confirm' paper is shown with no player name
  - 3-row confirm has no Back/Close/Help; Esc is the only other exit
  - What the action does is only in the lore of the green Confirm button; the shown item (13) just says 'See the details on the green button' unless it has its own lore

## wardrobe (12 menus)

### `wardrobe.main`: Wardrobe
- **Rows:** 6
- **Opens from:** /wardrobe (alias /sets); Loadouts > Wardrobe button
- **Rules to apply:** §5 pick menu instead of a cycler
- **Notes:**
  - ✅ GUI-4 (pick list). The Filter button (46) cycles through 9 values one click at a time and cannot go back; getting from 'All' to 'Other' takes 8 clicks.
  - Two buttons use the same ARMOR_STAND icon (Worn now at 47, Loadouts at 52) and the Chest and Loadouts live in non-standard slots 46, 47, 51, 52 next to Info/Help.

### `wardrobe.set`: Wardrobe » <set name>
- **Rows:** 6
- **Opens from:** Wardrobe > click a set; Enchanter armor tab links back here
- **Rules to apply:** §4 confirm rule
- **Notes:**
  - ✅ GUI-4b (rows: pieces · info · 29 Tome / 31 Get it / 33 Merge · 40 Trail). About 14 buttons in a layout that is not on the content grid (4 pieces on 10-13, actions at 15/16, 20/22/24, 29/31/33, 38/42); several only appear in some states, so slots jump between sets.
  - ✅ GUI-4b (Redeem asks first; Claim is free so no confirm, OPEN_QUESTIONS GUI-4b c). Claim (free Sigil set) and Redeem token run at once with no confirm, while Buy asks for one.
  - ✅ GUI-4b (owned shows nothing on 31, the status is on 4; locked/crate keep their own icons, no CLICK line). Buy, Claim, owned, crate-only and locked all share slot 31 and look the same shape, so players have to read the lore to tell 'press to buy' from 'info'.
  - ✅ GUI-4b (armor stand / hopper). Wear all (LIME_DYE) and Take all off (RED_DYE) use dye like toggles, but are one-shot buttons; same dye is used for 'owned' in slot 31.

### `wardrobe.chest`: Wardrobe » Chest
- **Rows:** 6
- **Opens from:** Wardrobe > Wardrobe Chest
- **Notes:**
  - Storing and taking out are separate screens (Store an item opens another list); items cannot be moved by clicking in the inventory directly.

### `wardrobe.store-pick`: Chest » Store an item
- **Rows:** 6
- **Opens from:** Wardrobe Chest > Store an item
- **Notes:** none found.

### `wardrobe.merge-pick`: Wardrobe » Merge
- **Rows:** 6
- **Opens from:** Set screen > Merge
- **Notes:** none found.

### `wardrobe.merge-look`: Pick a look
- **Rows:** 3
- **Opens from:** Merge > pick a partner
- **Rules to apply:** §1.2 add Back 18 / Close 22 · §4 confirm rule
- **Notes:**
  - Dead end: a 3-row menu has no Back or Close button (frame fixed nav is 6-row only), so players can only use Esc and lose the Merge flow.
  - Title is just 'Pick a look' with no set names, and the decision is permanent once the confirm is accepted.

### `wardrobe.loadouts`: Wardrobe » Loadouts
- **Rows:** 6
- **Opens from:** /loadout; Wardrobe > Loadouts (52)
- **Rules to apply:** §4 confirm rule · §3 hide until built
- **Notes:**
  - ✅ GUI-4b (8 presets on 10-16 / 19-25, one Edit a loadout button with a pick list). Up to 16 loadout buttons (8 presets + 8 Edit books) in a checkerboard of 10/12/14/16 and 19/21/23/25 that is not the standard content grid.
  - ✅ GUI-4b (preview in the lore; one click stays, owner LD5; confirm = OPEN_QUESTIONS GUI-4b b). Clicking a loadout swaps armor and hotbar tools immediately, and the old tools go to the chest, with no confirm and no preview of what changes.
  - ✅ GUI-4b. Edit is a separate WRITABLE_BOOK button under every preset, so the preset icon and Edit book must be told apart by position.
  - ✅ GUI-4b (no confirm by GUI_DESIGN §4: nothing is replaced). Empty places are lime glass panes that save the current gear on one click, with no confirm.
  - ✅ GUI-4b (hidden until pets exist). Pet button (47) is a placeholder 'Coming soon' item that does nothing.

### `wardrobe.loadout-edit`: Loadouts » <loadout name>
- **Rows:** 6
- **Opens from:** Loadouts > Edit button under a loadout
- **Notes:**
  - ✅ GUI-4b (Bedrock form popup, old name filled in). Rename closes the menu and needs typing in chat (one word, max length), which is slow on touch.

### `wardrobe.loadout-icon`: Loadouts » Icon
- **Rows:** 6
- **Opens from:** Loadout edit > Icon
- **Notes:** none found.

### `wardrobe.staff`: Wardrobe of <player>
- **Rows:** 6
- **Opens from:** /inspect > Wardrobe button (staff with wardrobe staff permission)
- **Notes:**
  - Four admin give buttons squeezed into slots 46, 47, 51, 52 around Info/Close/Help; the two tome buttons share the BOOK icon and are 6 slots apart.
  - Non-admin staff see the same list but entries are inert with no hint why.

### `wardrobe.staff-entry`: Wardrobe of <player> » <set name>
- **Rows:** 3
- **Opens from:** Staff wardrobe > click an entry (admin)
- **Rules to apply:** §1.1/§1.2 never a dead end
- **Notes:**
  - Dead end: 3-row menu with no Back or Close; the staff member can only press Esc.
  - ✅ GUI-BUGS. With the live max level of 40 the level list becomes 0, 1, 10, 25, 40, 39, 40, so 'Set to Lv 40' appears twice (slots 14 and 16) and out of order.
  - ✅ GUI-4 (lava bucket). Remove set is a barrier item in the bottom border row of a 3-row frame (slot 22), the same icon as Close elsewhere.

### `wardrobe.staff-pick`: Wardrobe of <player> » Give set / Give token
- **Rows:** 6
- **Opens from:** Staff wardrobe > Give a set / Give a token (admin)
- **Notes:**
  - No Info button (infoKey is not set), unlike every other 6-row menu in this module.
