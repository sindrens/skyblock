# Quality of life: what is missing or clunky (research, 30 Oct 2026)

Research doc. QUICK1 (30 Oct) built #1 (blocks), #2a and #10a, marked BUILT below; everything else is not built. Every idea waits for the owner's answer (JSON block at the end, ready for the one-click page).

How I worked: I read OWNER_PRINCIPLES, TEST_GUIDE, FEATURE_IDEAS, BACKLOG, PLANNING, DECISIONS_2026-10-30, OWNER_ANSWERS 1-3, QUESTIONS 2-3, STANDARDS, UI_STYLE, the phase and plan docs (5, 7, 8, 10, tutorial, ranks), the performance and security audits, `docs/commands/*.md`, and skimmed the modules (menus, chat, economy/sell, auction, box, essentials, islands, depths, hud). I then compared with Hypixel Skyblock, ManaCube, Complex, CosmicSky, SuperiorSkyblock2/BentoBox, EssentialsX, CMI, QuickShop, ChestSort and InvSee.

Effort: S = under half a day. M = 1 to 3 days. L = more than 3 days (same scale as `planning/PERFORMANCE.md`).
Status: **New** = nobody proposed it. **Approved, not built** = the owner already said yes, it is just not scheduled. **Planned** = in STANDARDS or a phase plan.

## 0. Rules every idea follows (from OWNER_PRINCIPLES)

- No new waiting. An idea may only add a short input guard (under 1 s) to stop misclicks.
- Overflow goes to `/box`. Nothing is dropped, nothing is deleted.
- Every idea has a `/settings` switch when it changes how the game behaves (per profile, like other settings).
- Bedrock path is a chest menu. No forms (answer 33), no long commands (answer 44). Chat clicks are a bonus, never the only way.
- Lores stay short. New text is English only. Every new command has full tab completion.
- Every item or money path is atomic and audited (see `planning/SECURITY_ANTI_EXPLOIT.md`).

## 0.1 What already exists (so nobody builds it twice)

`[item]` in chat with hover; mention highlight and sound; `/sell hand`, `/sell all`, sell chest with live total; shop quick-buy grid and Bedrock amount screen; AH search, sort, categories and collection bin; number picker with Min and Max; `/box` with join line and "Take all that fit"; Satchel with auto-pickup in the Depths; stacked items and mobs on islands; `/craft /ec /hat` for donors; `/settings` with 4 groups; Nether Star menu item; `/help` topics, help NPCs, sneak + right-click NPC help; scoreboard "Next:" note; island notes; island warps with categories and signs; invites menu; `/ignore`; `/afk`; wardrobe and loadouts; clickable `/tpa` Accept and Deny; staff `/back`, `/invsee`, `/vanish`, `/inspect`, `/logs`; kick, disband and reset backups with `/isadmin restore`.

## 0.2 Already decided by the owner (not re-proposed)

Declined: filter hoppers and hopper or block limits (O1, O2); island storage auto-collect, search, sort, deposit, compress, pages (ST1-ST6); referral rewards; lottery; confirm on quick-buys (answer 8); `/price` and price lines on inventory items; Bedrock forms (answer 33); hiding the dot in Bedrock names (answer 303); "AFK Comfort" mastery; respawn at island home (answer 160).

Approved but not built (the research below ranks them with the new ideas): island auto-pickup (O15), `/sort` (Q5), hide other players toggle (answer 51), shop search (answer 718), enchant book search (answer 562), `[island]` in chat (idea 68), friends and parties (63, 64), `/boosters` and `/thanks`, `/mail` (REVIEW), `/warp` and `/back` for players (STANDARDS 1.2), `/stats [player]`.

---

# 1. Top 30, ranked

Rank = player impact x how often it happens, divided by effort and risk. Cost to the owner: a "yes" on the question with the same number (QOL1 to QOL30 in the JSON).

| # | Idea | Area | Effort | New command? | Risk | Status |
|---|---|---|---|---|---|---|
| 1 | Island auto-pickup (overflow to /box) | Inventory | M | No | Med (dupe, box rows) | **Built for blocks (QUICK1)**, overflow stays on the ground until Box 2.0; mobs open |
| 2 | Sell protection: keep list, hotbar skipped | Selling | S-M | No | Low | **Hotbar skip built (QUICK1)**; keep list open |
| 3 | Inbox: "While you were away" + bell | Notifications | M-L | No (alias optional) | Low | New |
| 4 | Sell undo and "Recently sold" | Selling | M | No (sub-command optional) | Med (economy) | New |
| 5 | Places menu and "Guide me" arrow | Navigation | M | `/warp` (planned) | Low | Planned |
| 6 | Player picker when a name is missing | Menus, Bedrock | M | No | Low | New |
| 7 | `/back` and "Last place" for everyone | Navigation | S-M | `/back` (planned) | Low-Med | Planned |
| 8 | Search button in long menus (shop first) | Menus | M | No | Low | Approved for shop (718), rest new |
| 9 | Help that finds things (did-you-mean, `/help` search) | Help | S then M | No | Low | New |
| 10 | Drop protection and item safety net | Anti-frustration | S then M | No | Low-Med | **Drop guard (a) built (QUICK1)**; (b) open |
| 11 | Player card (click a name) | Social | M | `/stats` (planned) | Low | New |
| 12 | Auto-refill blocks | Inventory | S | No | Low | New |
| 13 | Smart AH listing (price hint, relist, history) | Buying, selling | M | No | Low-Med | New |
| 14 | Hide players toggle | Comfort, FPS | M | No | Med (perf) | Approved, not built (51) |
| 15 | Bedrock request menus and inline `[item]` | Bedrock | M | No | Low | New |
| 16 | Staff click-through (chat line to /p, item to log) | Staff | S-M | No | Low | New |
| 17 | Inventory snapshots with staff restore | Staff | M | No | Med (restore dupes) | New |
| 18 | `/sort` for inventory, /ec and /box | Inventory | S-M | `/sort` (approved Q5) | Low | Approved, not built |
| 19 | Recent and pinned features in /menu | Menus | S-M | No | Low | New |
| 20 | Recent and favorites in visit browser and warps | Navigation | S-M | No | Low | New |
| 21 | Accessibility pack (colorblind, compact lore, calm mode) | Accessibility | M | No | Low | New |
| 22 | Do Not Disturb and AFK auto-reply | Settings | S | No | Low | New |
| 23 | Void rescue on your island | Anti-frustration | S-M | No | Low | New |
| 24 | `/mail` (text only) | Social | M | `/mail` (planned) | Low-Med | Planned |
| 25 | Box 2.0: grouped, searchable, capped | Storage | M | No | Low | New |
| 26 | Auto loadout by place | Tools | M | No | Med (item swap) | New |
| 27 | Right-click harvest and replant | Farming | M | No | Low-Med | New (Phase 7) |
| 28 | Island block rollback for staff | Staff, islands | L | `/isadmin rollback` | High | New |
| 29 | Quick chat menu for phones | Bedrock, chat | S | No | Low | New |
| 30 | Quick-stack into nearby chests | Storage | M | Yes or menu button | Med | New (owner declined a close cousin) |

---

# 2. Details per idea

## 1. Island auto-pickup (overflow to /box)
- **What:** Blocks you break and mobs you kill on your island put their drops straight into your inventory. What does not fit goes to `/box`. Setting `Auto-pickup` in `/settings` (Off, Island, Always), default On. Visitors only pick up what the role permission allows.
- **Why:** The Depths already does this with the Satchel. On the island players still chase cobble and crops on the ground. Phones miss items. Every big skyblock server has it.
- **Fit:** No waiting, nothing lost, no new command. The owner said yes in O15.
- **Effort and risk:** M. Cancel the vanilla drop and add the stack in one step (`BlockDropItemEvent`, mob drop event), or an item is duplicated. Box rows must be merged per item type, or one macro fills the DB (F-09 in the security audit). Stacked mobs already scale drops in the stacking module, so reuse that. Phase 7 collectors only catch what stays on the ground, so document the order.

## 2. Sell protection: keep list, hotbar skipped
- **What:** `/sell all` and the Sell inventory button sell every sellable stack in hotbar and inventory, only tools and weapons stay (`SellFeature` lines about 424 and 695). So seeds, food and building blocks you carry are sold. Add: (a) setting "Sell all skips hotbar", default On; (b) a Keep tab in `/sell`: click an item type to keep it (up to 20, per profile). Bulk sells skip kept items. Putting an item into the sell chest by hand still sells it.
- **Why:** Selling your seeds or your build blocks by accident is the most common sell mistake. It costs a trip to `/shop`.
- **Fit:** Nothing lost, clean lore (one line "Kept" on the picker item only), no command.
- **Effort and risk:** S for (a), M for (b). Low risk: it is a filter in front of an existing path.

## 3. Inbox: "While you were away" and the bell
- **What:** One `Notices` service in `common`. Modules post a notice (type, count, text, target menu). On join and on profile switch the player gets at most 3 lines, for example `While away: +$48k from 6 AH sales | 12 items in /box | Interest +$2.1k | Daily reward ready`. A bell button in `/menu` shows the unread count and opens an Inbox menu grouped by type; a click goes to the right menu (Box, AH bin, Bank, Daily). Per-category switches in `/settings`. Notices are counters with a 14-day life, not item holders.
- **Why:** Today the box, AH and tutorial each print their own line. Phase 7 (fuel low, collector full), Phase 9 (events) and Phase 10 (daily, quests, boosters) will add many more. Join chat would become spam. Bedrock cannot rely on chat clicks, so the bell menu is the reliable path.
- **Fit:** Help everywhere, compact, Bedrock-first, nothing lost (items stay in Box and the AH bin, the inbox only points to them).
- **Effort and risk:** M for the service, S per module to post notices. Low risk. Keep it aggregated to avoid DB growth. No new command; `/inbox` as an alias only if the owner wants one.

## 4. Sell undo and "Recently sold"
- **What:** Each sale (sell chest, `/sell hand`, `/sell all`, Satchel sell all, Sell inventory) is kept for 30 seconds (config) with an item snapshot. The sale line gets an [Undo] button on Java, and a "Recently sold" tab in `/sell` holds the last 5 sales for everyone, including Bedrock. Undo takes back exactly the money paid, returns the items (inventory, then `/box`), and reverses the price impact as a delta.
- **Why:** Misclicks happen with big stacks and the Hot Item. A 30-second safety net costs the economy nothing.
- **Fit:** "Nothing is lost by accident", no delay (the sale itself is still instant), no new command.
- **Effort and risk:** M. One DB transaction for the money and the record, idempotent per sale id. If the player already spent the money, undo says so and does nothing. Stats and achievements (`items_sold`, `money_earned`) count when the window ends, so undo cannot farm them. Reverse the price delta, never set the old price (another player may have sold in between). Sub-command `/sell undo <id>` is only a helper for the chat button; skip it if the owner prefers no new sub-commands.

## 5. Places menu and "Guide me" arrow
- **What:** `/warp` (planned in STANDARDS 1.2) opens "Places": spawn districts, services (Bank, Shop, Auction, Forge, Enchanter, Black Market, Hall of Heroes, Tavern), the Depths gate, events. Icons and positions come from the stored build points. Click = instant teleport. Second button "Guide me": a particle trail and a boss bar with distance to the NPC, using the tutorial guide engine (TUTORIAL 9.3). The Warzone is not listed (portal only). The island warp browser stays at `/is warp` and gets a tab here.
- **Why:** Spawn has seven districts and many NPCs. New players walk around lost, veterans want a fast hop to the Forge. Hypixel and Complex both have a "Travel" menu.
- **Fit:** Help everywhere, instant teleports, menus only on Bedrock.
- **Effort and risk:** M. Low risk: reuse the teleport service (combat warmup, tier locks, island bans apply). Needs owner OK for `/warp` and `/warps`.

## 6. Player picker when a name is missing
- **What:** `/tpa`, `/tpahere`, `/trade`, `/msg`, `/pay`, `/ignore`, `/is invite`, `/is coop`, `/is visit` open a picker if no player is typed: online heads, search button, sorted nearby / friends / island members first depending on the command. Picking a head runs the command; `/msg` and `/pay` then ask for text or amount with the existing chat prompt and number picker. One class `PlayerPicker` in `common/menu`.
- **Why:** Bedrock names start with a dot and phone typing is slow. Answer 44 says no feature needs long commands.
- **Fit:** Direct hit on principle 5. No new command (changes the no-argument behaviour only).
- **Effort and risk:** M. Page 28 heads at a time, cache skins, respect vanish (`canSee`) and `/ignore`. Low risk.

## 7. `/back` and "Last place" for everyone
- **What:** `/back` returns to where you stood before your last teleport (`/spawn`, tpa, home, visit, warp, dungeon, portal). It keeps the last 3 places in memory. A "Last place" button in `/dungeon` returns to your last Depths tier. It never goes to a death point (you respawn at spawn, PvE keeps your inventory).
- **Why:** Teleports are instant, so a misclick on `/spawn` in the middle of a mine costs real time. STANDARDS already lists `/back` for players. Today `/back` is staff only (`skyblock.staff.back`).
- **Fit:** No delay, help everywhere.
- **Effort and risk:** S-M. Re-run every guard on the way back: island ban and lock, tier gates, tutorial locks, warzone portal rule, combat warmup. Staff keep the death point. Needs the owner to approve the name for players.

## 8. Search button in long menus
- **What:** A Search button in the menu frame (name tag, chat prompt like the AH search, works on Bedrock). First `/shop` (answer 718, not built), then `/enchanter` books (562), `/wardrobe`, `/is visit` (island or owner name), `/tags`, `/is warp`, `/box`, `/logs` for staff. Match name, category words and a few aliases ("lamp" finds "redstone lamp").
- **Why:** The shop has 332 items in 8 categories. Searching beats paging, most of all on a phone.
- **Fit:** Help everywhere. No command.
- **Effort and risk:** M once for the framework, then S per menu. Do the filter on a cached name index, not by building lore for every item.

## 9. Help that finds things
- **What:** (a) Unknown command: "Unknown command. Did you mean /home?" using the closest command the player may use, plus a table of names from other servers (`/kit`, `/homes`, `/pv`, `/warps`, `/tpall`) mapped to our command or `/help <topic>`. Java gets a click; Bedrock gets the plain text. (b) `/help <free text>` searches topic titles and a keyword list per topic in `help.yml` and shows a result menu.
- **Why:** Players come from other servers with muscle memory. "Unknown or incomplete command" is a dead end.
- **Fit:** Help everywhere, English, no command (it extends the existing `/help` argument).
- **Effort and risk:** S for (a) (Paper has `UnknownCommandEvent`; verify on 26.3), M for (b). Only suggest commands the player has permission for, so staff commands never leak.

## 10. Drop protection and item safety net
- **What:** (a) Custom and unique items (tools, enchant books, wardrobe tokens, spawners, keys, money notes) cannot be thrown out by accident. The first drop is refused with "Sneak + drop to throw this away", or switch it off in `/settings`. Same for clicking outside the inventory with such an item on the cursor. (b) A tagged valuable item that would be destroyed (void, lava, fire, cactus, despawn timer) goes to its owner's `/box` instead. `ProfileGuards.tagDrop` already tags dropped items with the profile.
- **Why:** The owner's rule is "nothing is lost by accident". Today a missed click outside a menu can drop a Lv 40 tool.
- **Fit:** Principle 3. No command.
- **Effort and risk:** S for (a), M for (b). For (b) only touch tagged custom items, never every dropped stack (item entities are very frequent). The box return must go only to the tagged profile, so it cannot move value between profiles.

## 11. Player card
- **What:** Click a name in chat (today it fills `/msg`) or sneak + right-click a player with an empty hand: a small menu with head, rank, island name, playtime and buttons Message, Trade, TPA, Visit island, Friend (when built), Ignore, Report. Staff also get Inspect and Punish. The planned `/stats [player]` is the command form.
- **Why:** Bedrock cannot hover, so the name hover card is invisible to those players. One menu replaces six commands.
- **Fit:** Help everywhere, Bedrock first. Shows nothing new (the hover card has rank, island, playtime).
- **Effort and risk:** M. Rate-limit opening. Respect `/ignore` (hide the Message button) and vanish.

## 12. Auto-refill blocks
- **What:** When the last block in a hotbar stack is placed, the next stack of the same item moves into that slot. Setting `Auto-refill`, default On. Plain items only (skip items with a unique id).
- **Why:** Islands are built by hand. Every mod pack has it, and it is the cheapest comfort for builders and phone players.
- **Fit:** No delay, no command.
- **Effort and risk:** S. Do it one tick after the placement and re-check that the slot is empty before moving, or a stack can be copied.

## 13. Smart AH listing
- **What:** The `/ah sell` dialog shows "Lowest now: $X (3 listings)" and "7-day median sale: $Y", with buttons "Match lowest" and "Undercut 1%". Expired and cancelled items in the bin get "List again" with the same price. A "My sales" tab shows the last 50 sales (item, price, tax, time). Later: "Watch this item" with a notice in the inbox.
- **Why:** Pricing blind is the biggest friction in any auction house. Hypixel Bazaar and AH both show it.
- **Fit:** The owner banned `/price` and price lines on inventory items. This is market data inside the AH only, not item lore. Ask first.
- **Effort and risk:** M. Use the median and ignore sales between same-IP accounts so wash trades cannot move the hint. Pure read, no money path change.

## 14. Hide players toggle
- **What:** Setting `Players: All / Friends and island / None`, plus a one-click switch at spawn (spawn NPC or hologram and a button in `/menu`). Staff always stay visible. Chat and tab are not affected.
- **Why:** 300 players at spawn drops FPS on phones and weak PCs. The owner said yes in answer 51.
- **Fit:** Comfort and performance, no command.
- **Effort and risk:** M. Use `hidePlayer` only for players who opt in, so the cost is not 300 x 300. Must not unhide vanished staff (`VanishService` uses the same call): keep one visibility owner.

## 15. Bedrock request menus and inline `[item]`
- **What:** For Bedrock players (`BedrockCheck`), an incoming `/tpa`, trade request, island invite or party invite opens a small 3-row Accept / Deny chest menu (or shows an action-bar line and a plain "/tpaccept" line if they are in another menu). The `[item]` token shows 1-2 key stats inline for Bedrock viewers (for example `[Iron Pickaxe Lv 12 Fortune III]`) because hover does not work. Setting `Request popups`, default On for Bedrock and Off for Java.
- **Why:** Bedrock chat has no hover and (to verify on a real client) no clickable text. The test guide already says "use /tpaccept if tapping does not work". Chest menus follow answer 33.
- **Fit:** Bedrock never needs long commands.
- **Effort and risk:** M. Ignore clicks for the first 600 ms so a misclick on a popup cannot accept a trade. That is a guard, not a delay to the action. Queue popups, never stack menus.

## 16. Staff click-through
- **What:** For staff only: every public chat line gets a small red `x`. Click = `/p <player>` open with that line already attached as evidence (Evidence exists, 12.4). In `/invsee` and `/inspect`, clicking an item opens its history (`/logs item <uuid>`); in `/logs`, clicking a player opens `/inspect`. Reports open `/p` with the report text attached.
- **Why:** Mods punish from chat lines all day. Typing names costs seconds and invites typos.
- **Fit:** Staff-friendly rule in CLAUDE.md. No command.
- **Effort and risk:** S-M. Add the component only when the viewer has staff permission (chat render cost is a known risk, P-05).

## 17. Inventory snapshots with staff restore
- **What:** Automatic snapshot of inventory, ender chest and purse per profile on quit, profile switch, every 30 minutes and before big staff actions. Keep the last 5 for 14 days, compressed in the DB. `/inspect` gets a Snapshots tab: view, compare with now, restore selected stacks into `/box` (never overwrite). Every restore is audited; large ones alert admins (ANS3 alert exists).
- **Why:** "My items vanished" is the first ticket after a crash or bug (security audit F-01c). Today only kicks, disbands and resets have backups.
- **Fit:** Nothing lost. Staff-friendly.
- **Effort and risk:** M. Restore is a dupe vector. Restore only stacks that are missing now, skip unique-id items that exist elsewhere (check the item ledger), and restore into `/box` with a staff flag. No command.

## 18. `/sort`
- **What:** `/sort` sorts the main inventory (not hotbar or armor) by group and name. A Sort button in `/ec`, `/pv` and the `/box` page. No middle-click needed, so it works on Bedrock.
- **Why:** The owner approved it in Q5. Players hate sorting by hand.
- **Fit:** No delay. Island storage sort stays declined (ST1-ST6); this is for your own inventory and personal vaults.
- **Effort and risk:** S-M. Rebuild the slot array and set it in one call. Keep staff-item and unique-id tags unchanged.

## 19. Recent and pinned features in /menu
- **What:** Free hub slots 46, 47, 51 and 52 show your last 4 used features ("Recent"). Right-click a feature to pin it (up to 4 pinned, shown first). Shift + right-click on the Nether Star opens the last menu you used.
- **Why:** The hub has 28 buttons. After day 2 players use the same 4. Two clicks become one.
- **Fit:** Two-click rule becomes a one-click rule. Needs a tap alternative to right-click on Bedrock (a "Pin" toggle in the item's lore action line).
- **Effort and risk:** S-M. Per profile, tiny data.

## 20. Recent and favorites in the visit browser and warps
- **What:** Two new tabs in `/is visit`: Recent (last 10 islands you visited) and Favorites (up to 30, star button in the island card). Same in the warp browser. Friends' islands join them when friends exist.
- **Why:** Players return to the same shops and farms. Discover is random, so finding a favorite island takes many clicks.
- **Fit:** Visiting is meant to drive playtime and trade (principle 10).
- **Effort and risk:** S-M. Low risk. Hide locked or banned islands as the browser already does.

## 21. Accessibility pack
- **What:** A "Display" group in `/settings`: (a) Colorblind palette: success/error, buy/sell and rarity use a blue-orange-yellow set and always carry a symbol (`✔ ✘ ▲ ▼`) as well as color; (b) Compact lore: only values and the action line, no description lines; (c) Calm mode: no full-screen titles, fewer particles, no sound spam; (d) larger click targets on Bedrock menus where possible.
- **Why:** The V2 palette leans on red and green (buy price green, sell price red; lime and red dye toggles). UI_STYLE already says error red and brand red must be told apart by shape. Compact lore is the owner's principle 5, offered as a switch for players who want it smaller still.
- **Fit:** Direct.
- **Effort and risk:** M. Palette lives in `style.yml`; chat is already built per viewer, but `LoreBuilder` takes no viewer yet. Check that every pair keeps contrast and a Bedrock legacy fallback.

## 22. Do Not Disturb and AFK auto-reply
- **What:** One switch `Do not disturb`: turns off private messages, tpa, trade requests, island invites, mention sounds, event sounds and announcement titles, and remembers the old values to restore them. Someone who messages you gets "Steve is AFK (12 min)" or "Steve has Do Not Disturb on" instead of silence. Staff can always reach you.
- **Why:** Players who stream, trade or farm AFK want quiet. Now they flip 7 toggles.
- **Fit:** Settings only. No command (`/dnd` alias if the owner wants).
- **Effort and risk:** S. Low.

## 23. Void rescue on your island
- **What:** Falling below the island floor on your own island or outpost teleports you to the island home, no damage, message "Saved from the void". Island setting `Void rescue`, default On. Visitors go to the island's visitor spot. The Depths and the Warzone are unchanged.
- **Why:** BentoBox and SuperiorSkyblock both ship this. Skyblock means lots of building over void.
- **Fit:** PvE death already costs nothing but a trip to spawn (answer 76). This saves the trip. It does not change respawn rules (answer 160).
- **Effort and risk:** S-M. Reset fall distance and velocity. Ignore while in combat (no escaping a fight).

## 24. `/mail` (text only)
- **What:** `/mail send <player> <text>` and a Mail menu. Text only, no items. Rate limit 1 per 10 s, 20 per target, chat filter applies, `/ignore` blocks it, logged 30 days. Unread count goes to the inbox (#3).
- **Why:** Team coordination with offline members; staff-to-player notices. It is in REVIEW and `planned.md` already.
- **Fit:** English only (filter), no item path so no dupes and no profile value transfer.
- **Effort and risk:** M. Low. Block mail between your own profiles' accounts is not needed (text only).

## 25. Box 2.0
- **What:** Group identical stackable items into one row with a count ("Cobblestone x 4,200"), "Take all of this type", search (#8), sort by newest or type, counter in the inbox bell and on the scoreboard when over 0. Rows are capped per profile (F-09 fix); unique-id items keep their own row.
- **Why:** After auto-pickup (#1) the box will hold far more items. 500 rows of single stacks is unusable.
- **Fit:** Nothing lost; the box is never refused, it just merges.
- **Effort and risk:** M. Merging must keep the origin line for staff, and must never merge items with different tags.

## 26. Auto loadout by place
- **What:** In the loadout menu each preset can be set to apply when you enter Island, Mine, Forest or Combat. One swap per place change and at most once per 10 s so the gate edge cannot ping-pong. Respects "not in combat or Warzone" (L4).
- **Why:** Loadouts exist, but players forget to switch and mine with the farming set. With Phase 7 (hoe and rod) it gets worse.
- **Fit:** No busywork. Default Off.
- **Effort and risk:** M. The wardrobe swap code is atomic already; the risk is the trigger. Test with lag at zone borders.

## 27. Right-click harvest and replant
- **What:** On your island, right-click a ripe crop with an empty hand: harvest and replant (uses one dropped seed). Island flag `Right-click harvest`, default On. Farming XP and set XP still need a hoe break (PD11), so this is comfort, not a XP shortcut.
- **Why:** Standard on modern servers and the best touch-screen farming. The owner also approved the harvester hoe (V6), which is the progression version.
- **Fit:** No command. Schedule with Phase 7 M7.3.
- **Effort and risk:** M. Hook into the crop engine, never vanilla growth; skip stacked-crop tags.

## 28. Island block rollback for staff
- **What:** Staff-only. Daily block snapshot per island (blocks and signs only, kept 3 days) and/or an own block-change log, so staff can restore a griefed island or undo "what player X broke in the last hour". Containers, spawners and collectors are never restored (dupe risk). Shows a diff and asks for confirm.
- **Why:** Griefing by a trusted or kicked member is the top island complaint, and CoreProtect waits for a 26.3 build (LATER).
- **Fit:** Nothing lost, staff-friendly. Related to idea 84 (island backup, yes).
- **Effort and risk:** L. FAWE async at low traffic, storage per island, restoring also removes legit builds made since. Command `/isadmin rollback <island> [player] [time]`.

## 29. Quick chat menu for phones
- **What:** A Quick chat button in `/menu` with about 12 English phrases (Hi!, Thanks!, GG, Need help, Looking for a team, Anyone want to trade?). A phrase goes through the normal chat pipeline (filter, mute, rate limit).
- **Why:** Typing on a phone is slow. It also gives players who speak another language a safe way to follow the English-only rule (rule 7).
- **Fit:** Direct. `Thanks!` can also trigger the `/thanks` booster reply (RW2). Rewards keep their own daily limits.
- **Effort and risk:** S. Low.

## 30. Quick-stack into nearby chests
- **What:** A "Stack to nearby" button (or `/qs`): for each chest within 8 blocks (max 10) where your role may open containers, move items you carry that the chest already holds. Never adds new item types to a chest.
- **Why:** Terraria, Hypixel and most mods have it. It saves minutes of clicking in a base with 20 chests.
- **Fit:** The owner declined deposit for the Island Vault (ST). Ask again, because this is only for normal chests and may conflict with the Vault being a paid upgrade.
- **Effort and risk:** M. Check the role's container permission, write container logs for the island log, move atomically per stack.

---

# 3. Also considered, not in the top 30

| Idea | Why not now |
|---|---|
| `[island]` and `[stats]` chat tokens | Approved (68). Build with the player card (#11). |
| Friends: auto-accept tpa from friends, "go to friend" | Approved (63). Add when friends exist. |
| `/compact` (9 ingots to a block) | The owner declined compress for storage. Ask later if wanted. |
| `/find <item>` container finder | L, scans chunks, lag risk. Revisit after Phase 7. |
| Open shulker box from the inventory | Long dupe history on every server. Filled shulkers are already untradeable. Skip. |
| Scoreboard line picker | Good later. Wait until Phase 5 and 7 lines exist. |
| Event reminders with opt-in per event | Fold into the Inbox (#3) when Phase 9 arrives. |
| Quest tracker pinned to the scoreboard | Belongs to Phase 10 quests. |
| Auto-sweep purse to bank | The 2% withdraw fee would punish it. Skip. |
| Staff "reverse transaction" button | Conflicts with the security finding F-04 (no second approver). Needs that fixed first. |
| Emoji shortcuts (`:heart:`) | Only a short Bedrock-safe symbol list works. Low value. |
| F key opens menu | F is used for offhand. Today it is only blocked while holding the star. Skip. |

---

# 4. Quick wins: buildable now, no new command, small, low risk

1. **Sell all skips hotbar** (#2a): one filter and one setting. **BUILT (QUICK1):** setting `sell-skip-hotbar` (Gameplay, default on) for `/sell all` and "Sell inventory"; `/sell hand` and the chest by hand still sell.
2. **Did-you-mean on unknown commands** (#9a).
3. **Drop protection for custom items** (#10a). **BUILT (QUICK1):** setting `drop-guard` (default on); second drop within 3 s or sneak + drop throws it. (#10b, void/lava rescue to /box: not built.)
4. **Auto-refill blocks** (#12).
5. **Do Not Disturb and AFK auto-reply** (#22).
6. **Staff red `x` on chat lines to open `/p` with evidence** (#16 step 1).
7. **Quick chat menu** (#29).
8. **Recent row in `/menu`** using free slots 46, 47, 51, 52 (#19 step 1).
9. **Recent tab in the visit browser** (#20 step 1).
10. **Shop search button** (#8 step 1, already approved as 718).
11. **One combined join line** for Box, AH offline sales and interest (#3 step 1). It uses the existing messages and moves into the Inbox later.

Suggested build order after the quick wins: batch A = #1, #2b, #4 (sell side); batch B = #3, #25, #6 (flow and boxes); batch C = #5, #7, #11, #15 (navigation, social, Bedrock); batch D = #13, #17, #21, #26 (depth). Idea #28 is its own project.

---

# 5. Questions for the owner

Fields: `q` question, `s` suggested answer, `why` one-line reason, `p` 1 = important, 0 = detail. One question per idea that needs a decision (QOL1 matches idea 1 and so on). QOL31 asks you to test Bedrock chat once.

```json
[
  {"q": "QOL1: Island auto-pickup: blocks and mob drops on your island go to your inventory, overflow to /box, switch in /settings, default On?", "s": "Yes", "why": "You said yes in O15. It ends the item chase and nothing is ever dropped.", "p": 1},
  {"q": "QOL2: /sell all and Sell inventory skip the hotbar by default, and a Keep tab lets players protect item types (up to 20) from bulk selling?", "s": "Yes", "why": "Stops selling your seeds and build blocks by accident; selling by hand still works.", "p": 1},
  {"q": "QOL3: One Inbox: a short 'While you were away' summary on join and a bell in /menu with unread counts, instead of each system printing its own line?", "s": "Yes", "why": "Phases 7, 9 and 10 add many alerts; one place keeps chat clean and works on Bedrock.", "p": 1},
  {"q": "QOL4: Sell undo: every sale can be reversed for 30 seconds, with a Recently sold tab in /sell (last 5 sales)?", "s": "Yes", "why": "A safety net for misclicks that costs the economy nothing when done in one transaction.", "p": 1},
  {"q": "QOL5: Approve /warp (and /warps) as a Places menu with instant teleports to spawn districts and services, plus a Guide me arrow?", "s": "Yes", "why": "Spawn is big; new players get lost and veterans want a fast hop. It is already planned in STANDARDS.", "p": 1},
  {"q": "QOL6: When a command needs a player and none is typed, open a player picker menu (tpa, trade, msg, pay, ignore, invite, coop, visit)?", "s": "Yes", "why": "Bedrock names start with a dot and phone typing is slow; follows your answer 44.", "p": 1},
  {"q": "QOL7: Give every player /back (return to where you stood before your last teleport, never to a death point)?", "s": "Yes", "why": "Teleports are instant, so a misclick is costly. All teleport guards run again on the way back.", "p": 1},
  {"q": "QOL8: Add a Search button to long menus (shop first, then enchanter, wardrobe, visit browser, titles, warps, box)?", "s": "Yes", "why": "You approved shop search (718); the same button helps every long list.", "p": 1},
  {"q": "QOL9: Unknown commands show 'Did you mean...' and /help <words> searches help topics?", "s": "Yes", "why": "Players from other servers type /kit, /homes or /pv; today they hit a dead end.", "p": 1},
  {"q": "QOL10: Custom and unique items (tools, books, spawners, keys, notes) can't be dropped by accident, and tagged valuables that would burn or fall into the void go to the owner's /box?", "s": "Yes", "why": "Fits 'nothing is lost by accident'. A switch in /settings turns the drop guard off.", "p": 1},
  {"q": "QOL11: Click a name in chat (or sneak + right-click a player) to open a Player card with Message, Trade, TPA, Visit, Ignore, Report, and use /stats [player] as its command?", "s": "Yes", "why": "Bedrock can't hover, so one menu replaces six commands; /stats is already planned.", "p": 1},
  {"q": "QOL12: Auto-refill: when the last block in a hotbar stack is placed, the next stack moves in (setting, default On)?", "s": "Yes", "why": "Cheap comfort for builders and phone players; plain items only.", "p": 0},
  {"q": "QOL13: Show market hints inside the AH sell dialog (lowest listing, 7-day median) plus List again and a My sales tab?", "s": "Yes", "why": "You banned /price and price lines on items; this is AH market data only and fixes blind pricing.", "p": 0},
  {"q": "QOL14: Build the Hide players toggle (All / Friends and island / None) with a one-click switch at spawn?", "s": "Yes", "why": "You said yes in answer 51; 300 players at spawn hurts FPS on phones.", "p": 1},
  {"q": "QOL15: Bedrock players get a small Accept / Deny chest menu for tpa, trade and invites, and [item] shows key stats inline?", "s": "Yes", "why": "Bedrock has no hover and may not click chat; chest menus follow your answer 33 (no forms).", "p": 1},
  {"q": "QOL16: Staff see a small x on chat lines that opens /p with the line attached as evidence, and items/players in /invsee and /logs are clickable?", "s": "Yes", "why": "Saves seconds on every punishment; shown to staff only so chat cost stays low.", "p": 0},
  {"q": "QOL17: Automatic inventory snapshots (last 5 per profile, 14 days) that staff can restore into /box from /inspect?", "s": "Yes", "why": "Answers 'my items vanished' tickets in a minute. Restore only missing stacks and audit it.", "p": 1},
  {"q": "QOL18: Build /sort (approved in Q5) for your own inventory and a Sort button in /ec, /pv and /box, but not in the island Vault?", "s": "Yes", "why": "Matches your Q5 answer and your ST decision to keep the Vault without sort.", "p": 0},
  {"q": "QOL19: /menu gets a Recent row (last 4 features) and up to 4 pinned features?", "s": "Yes", "why": "Players use the same few buttons; two clicks become one.", "p": 0},
  {"q": "QOL20: Add Recent and Favorites tabs to the visit browser and the warp browser?", "s": "Yes", "why": "Discover is random; players want to return to the same shops and farms.", "p": 0},
  {"q": "QOL21: Add an Accessibility group in /settings: colorblind palette with symbols, compact lore mode, calm mode (no flashing titles, fewer particles)?", "s": "Yes", "why": "The palette leans on red and green; compact lore matches your principle 5.", "p": 0},
  {"q": "QOL22: Add a Do Not Disturb switch and tell people who message an AFK or DND player why they get no answer?", "s": "Yes", "why": "One switch replaces seven; senders stop wondering.", "p": 0},
  {"q": "QOL23: Void rescue on your own island: teleport to island home with no damage (island setting, default On)?", "s": "Yes", "why": "Common on skyblock servers; it only saves a trip because PvE death costs nothing.", "p": 0},
  {"q": "QOL24: Approve /mail as text only (no items), rate limited, filtered and logged?", "s": "Yes", "why": "Offline coordination for teams; no item path means no dupes or profile value transfer.", "p": 0},
  {"q": "QOL25: Box 2.0: merge identical items in /box, add 'take all of this type', search and a row cap?", "s": "Yes", "why": "Needed once auto-pickup sends more items to /box; also closes the box-spam risk F-09.", "p": 1},
  {"q": "QOL26: Loadouts can auto-apply when you enter Island, Mine, Forest or Combat (default Off)?", "s": "Yes", "why": "Players forget to switch; safe because it never swaps in combat or the Warzone.", "p": 0},
  {"q": "QOL27: Right-click an empty hand on a ripe crop to harvest and replant on your island (flag, default On, no Farming XP without a hoe)?", "s": "Yes", "why": "Best touch-screen farming; XP rule PD11 stays intact.", "p": 0},
  {"q": "QOL28: Build a staff-only island block rollback (daily snapshots or a block log; containers never restored)?", "s": "Yes", "why": "Griefing by trusted members is the top island complaint and CoreProtect isn't ready for 26.3.", "p": 0},
  {"q": "QOL29: Add a Quick chat menu with about 12 English phrases for phone players?", "s": "Yes", "why": "Phone typing is slow and it helps non-English players follow rule 7.", "p": 0},
  {"q": "QOL30: Add Quick-stack into nearby chests (button or /qs) for normal chests only?", "s": "No", "why": "You declined deposit for the Vault; this could undercut it. Say yes only if you want it for normal chests.", "p": 0},
  {"q": "QOL31: Before QOL15 and QOL3 are built, test on your Bedrock device whether tapping the [Accept] text in chat works (TEST_GUIDE tpa test 8). Does it work?", "s": "No", "why": "If it does not, chat-click features need a menu path; I assume it does not.", "p": 1}
]
```

---

# QOL3 status (31 Oct 2026)

Built the quick wins from §4 that are small, safe, need no new command and no owner decision, preferring what players feel every session. Every new behaviour has a switch (setting or config). Defaults and the questions they raise are in `docs/OPEN_QUESTIONS.md` → QOL3.

**Built**
| # | What | Where | Switch |
|---|---|---|---|
| 12 | **Auto-refill blocks**: placing the last block of a hotbar or off-hand stack moves the next plain stack of the same block in from the backpack (slots 9-35), one tick later, in one step (no dupe window); never custom/unique items or spawners, never creative | essentials `AutoRefill` | `/settings` → Gameplay → Auto-refill blocks (default on); `essentials.yml → auto-refill.enabled` |
| 9a | **"Did you mean"**: an unknown command suggests the closest command the player may run (max 2 typos, swaps count as 1, short words 1); Java can click it into the chat box, Bedrock reads it; no match = "Unknown command /x. See /help." | essentials `CommandHints` (Paper `UnknownCommandEvent`, checked on the 26.3 API) | `essentials.yml → command-hints.enabled / max-distance` |
| 22 (AFK part) | **AFK notice on private messages**: "Steve is AFK (12m), so the answer may take a while", once a minute per pair, only for delivered messages | social | `social.yml → dm.afk-notice-seconds` (0 = off) |

**Skipped, and why**
| # | Why not now |
|---|---|
| 2b Keep list, 4 sell undo, 3 / 11 inbox and combined join line | Medium size and touch the economy and several join paths; batch A/B in §4 |
| 8 / 10 Shop search | `docs/design/SHOP_DESIGN.md` §5 reverses answer 718 ("Not at launch") |
| 16 Staff red x on chat lines | Staff-only and adds a component to every chat line (chat render cost P-05); `/logs` already punishes from a line (P5 13.11) |
| 19 Recent row in `/menu`, 29 Quick chat | Need hub slots; the hub's 15 buttons are fixed by GUI_DESIGN §7.1 and the art brief (owner design) |
| 20 Recent tab in the visit browser | Needs a new per-profile table and a browser tab (S-M); not a quick win |
| 22 Do Not Disturb switch | One switch over 7 systems (msg, tpa, trade, invites, mentions, sounds, titles) in 5 modules; asked in OPEN_QUESTIONS QOL3 (e) |
| 9a name table from other servers | Needs the owner to say where `/kit`, `/homes`, `/pv`, `/warps` should lead |
| 5, 7, 13, 30 | Marked as needing the owner (`/warp`, `/back` for players, AH market data, quick-stack) |
| 1 (mobs), 10b, 23, 25, 17, 28 and the rest | Medium or large (M/L) or wait for Box 2.0 / Phase 7 |

The menu leftovers in the same item (MENU_FIXES "Left" after GUI-4c: `/logs` typing on `MenuService.input`, confiscate frame, `/inspect` freeze confirm and row shape, `/report` confirm) are marked ✅ QOL3 in `docs/design/MENU_FIXES.md`.
