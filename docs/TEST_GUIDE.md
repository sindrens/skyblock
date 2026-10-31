# Phoenix Skyblock · feature and test guide

_Written 30 October 2026 after update83, refreshed after update92 and again on 31 October after update118 plus the next batch (M7.1, GUI-4c, DV2 spike, M7.2, QOL3). The server runs all of this now (38 modules)._

How to use this guide:
- Each area has its features. Every feature has: what it does, its commands, and what to test.
- Test on Java first, then on Bedrock where it says so.
- The questions for the older features are on the **Decisions page** (one tap: Suggested / No / Other; link in [BACKLOG.md](BACKLOG.md)). Start with the ones marked "before launch". Features built since update92 were built with the safest default; their open points are in [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → "Waiting for owner" (each section names its entry).
- Staff commands need the right rank. "admin" = only admins.
- **OFF by default** marks a feature that is built but switched off. The section says which setting turns it on.

**Not built yet / needs planning:** everything that is not in this guide is listed in [BACKLOG.md](BACKLOG.md) (by area). Every plan and phase, its status, its open questions and the next build step are in one table in [PLANNING.md](PLANNING.md).

## What changed since the last guide (update92)

New features (each has its own section below):
- **Tutorial** for new players (11 steps from the island portal, cameras, Pip the parrot, optional steps, features unlock step by step).
- **Island progression:** pillars, dynamic island level, challenge book (180 tasks), `/is upgrade`, `/is masteries`, `/is top` leaderboards and the Top screen.
- **Rewards:** crates (menu + physical crate yard + key items), boosters and `/thanks`, `/daily` calendar + playtime rewards, `/vote` (receiver OFF by default), `/quests`, `/pass` battle pass, `/collection`, `/blackmarket`, `/achievements` as its own command.
- **Island production (OFF by default):** Island Fuel and custom island generators.
- **Depths v2 spike (OFF by default):** the mine tier-1 field shows coal / copper / iron per player.
- **Menus:** one menu frame everywhere, new `/menu` hub (15 buttons) with You, Travel and Rewards, Shop v2, Top screen, `/settings` in tabs, Bedrock popups instead of clickable chat.
- **Hoppers:** 64 items per move and a per-chunk hopper counter with a staff alert.
- **Quality of life:** island auto-pickup, drop guard, auto-refill blocks, "Did you mean", AFK notice on private messages.

Changed or removed behaviour (check these, the old guide said something else):
- **Right-click is now "Click":** NPCs, holograms, crates and the menu item react to both mouse buttons (and a Bedrock tap). A plain click opens the feature; sneak-click shows the help.
- **One welcome:** a new player gets only the tutorial's welcome (title + chat block); the privacy line comes 45 s later.
- **`/menu`** has 15 buttons in 4 groups (Play, Money, Power, More); unbuilt features are hidden, not "Coming soon". The old Warps menu is now **Travel**.
- **Shop v2:** 9 tabs, a buy page (Buy 1/16/64, Fill inventory, Type an amount). Left = 1 / right = 64 is gone. Undo last buy (15 s) and a second click for big buys replace the old confirm screen.
- **`/trash`:** Destroy asks "Destroy N items?" first; closing the menu gives the items back.
- **`/trade`:** ?, Close and Esc no longer cancel the trade; Cancel trade is on slot 53.
- **Profile delete and island disband:** a two-step confirm (no typing the name). Disband shows the exact amounts you lose.
- **Full satchel:** mining goes on; drops go to your inventory, then to `/box`. Mining only stops when `/box` is at its hard cap (5,000 stacks).
- **Combat XP** only comes from Depths combat zones. Mini-bosses scale with the number of fighters.
- **Money rules:** crystals can never be moved; sending money needs $10k you earned yourself (not to island members); `/pay`, `/withdraw`, `/ah` and `/trade` unlock through the tutorial; notes max 20 per minute; the bank fee is the only withdraw rule (no lost interest).
- **Bank tiers rebalanced:** personal tier 10 = $10B (was $195.3B), island bank level 10 = $50B.
- **Wardrobe:** worn set pieces come off with a click (the old guide said every click must fail).
- **`/fly`** only toggles your own flight and only in staff or build mode; **`/s`** is staff mode and **`/p`** is punish.
- **Helpers** no longer see private messages in `/logs` (mods and up still do).
- **Spawners** give less the more of one tier an island owns (100% → 10%).
- **AFK pool:** players inside are hidden from each other; non-earners go home after 60 s; keys and the sell booster are delivered now.

## Release history since update83

| Update | What |
|---|---|
| update84 | New builds pasted: spawn base v1 update, 7 spawn districts, iron mine, birch forest and the crypt. |
| update85 | Resource pack art brief written (docs/plans/RESOURCE_PACK_ART_BRIEF.md); the Java pack will be forced (accept before joining). Restarts no longer flood the console with LuckPerms lines. |
| update86 | Banks (BANK2): interest every 8 h (00/08/16 UTC) at 0.6% personal / 1.0% island; 2% withdrawal fee that island masteries will lower to 1.5 / 1.0 / 0.5%; next payout countdown in `/bank`. |
| update87 | `/box` item mailbox: items that don't fit are never dropped; they wait per profile in `/box` (sell chest, shop, Forge, Enchanter, wardrobe, trade, auction, staff gives). |
| update88 | Your round 1 answers (ANS1): no join or creation cooldowns, start money on every profile, the 30-minute new-account limit is gone, team = 4 + leader, `/is setmyhome` removed, kick backups + `/isadmin restore`, disband only without members (confirm button) and it deletes the island bank, always respawn at spawn, level line hidden, normal size cap 9x9, profile delete wipes auction listings. Builds re-pasted after a grief. |
| update89 | NPCs, holograms and leaderboards from the builds (NPCHOLO): right-click opens, sneak + right-click explains, top-10 boards, `/skyblock npcs`. |
| update90 | Portals (PORTALS): Depths <-> spawn portals, the gatehouse island portal creates your island on first use and always takes you home; warzone only through its portal. |
| update91 | Your round 2 answers (ANS2): teleports instant (3 s only in combat), visit browser Discover tab, rating needs 1 h + max 3 accounts per IP, `/isadmin hide`, disband/reset resets member profiles with backups, `/p` Silent button, ban screen with time left and appeal date, English word lists and filter on signs/books/anvils/warps/nicks, rules 7-9, `/home` = island home and staff use `/shome`. Region protection in the Depths, spawn and warzone (DEPTHSPROT). |
| update92 | ANS3: `/ignore` also blocks tpa, trades and island invites and can hide chat; creative only for builders/admins in spawn and the Depths; staff items can't be sold; god/fly/heal only in staff or build mode; big staff transfers alert admins; `/ec` and `/craft` blocked in the warzone, boss arena and 10 s after combat; a banned leader's island is closed to visitors. |
| update93 | Tutorial TU1-TU4 (11 steps, locks, starter chest, finish reward). |
| update94 | FIXES1 (tester feedback): `/fly`, `/s` = staff mode, `/p` = punish, the menu item always stays, worn wardrobe pieces come off with a click, tab ranks within ~1 s, report book, usage lines. |
| update95 | SECFIX1: crash-safe item saves everywhere, `/box` hard cap 5,000, Depths respawn timers survive relogs, frozen instead of deleted spawners. |
| update96 | QUICK1 (island auto-pickup, drop guard, `/sell all` skips the hotbar, Gameplay settings) and island pillars (M5.1). |
| update97-98 | Crates core (M10.1) and staff `/crates admin` (SMALL2). |
| update99-100 | Tutorial TU5-TU8 (cameras, Pip, optional steps, stats) and the dynamic island level (M5.2). |
| update101-104 | Challenge book (M5.3), boosters + `/thanks` (M10.2), `/achievements` tabs and boosts (AC1-2), performance fixes. |
| update105 | Island upgrades (M5.4), `/daily` calendar and playtime rewards (M10.6). |
| update106-107 | UX3 (both clicks work, "Click" wording, bank tiers rebalanced, Warps menu) and REV3-FIX (full satchel → `/box`, DMs hidden from helpers, decision ledger). |
| update108 | VIS: "Show players" setting, AFK pool hides players and sends non-earners home. |
| update109 | ECON-R3 (money rules), GUI-1 (menu frame), DEPTHS-R3 (boss scaling, key fragments, Combat XP only in Depths), hoppers. |
| update110 | GUI-2 (`/menu` hub, You, Travel, Rewards), GUI-BUGS (safety confirms, one welcome), ISL-R3 (island rules). |
| update111 | STAFF-R3 (DMs as report evidence, `/msg` filter, island creative for owner/admin), Shop v2 (GUI-3). |
| update112 | GUI-4 (pick lists, per-menu fixes), GUI-5 (Bedrock popups), TUT-FIX (cameras can't trap you). |
| update113-114 | Physical crates at the crate yard (M10.3), masteries (M5.5), key items (M10.4), leaderboards (M5.7). |
| update115-116 | Voting (M10.5), quests (M10.7), collection log (M10.10), battle pass (M10.8). |
| update117-118 | Black Market (M10.11), spawner diminishing output (SPAWN-F2), GUI-4b (staff pick lists, Bedrock text popups). |
| next batch (119) | Island Fuel (M7.1, OFF), GUI-4c (Top screen, settings tabs, staff screens), Depths v2 spike (OFF), custom generators (M7.2, OFF), QOL3 (auto-refill, "Did you mean", AFK notice). Not released yet when this guide was written. |

Plans written in the same time (no game change): Phase 8 PvE (mobs, bosses, pets), Depths v2. See [PLANNING.md](PLANNING.md). Updates 79-83: see CHANGELOG.md.

Still building (not in this guide yet): **M7.3 farming** (crops, growth, Farming skill; also behind the production switch) and **M8.1 mob framework v2**. E6 (hoe and rod enchants) waits for farming and fishing. STORE2 waits for the Island Vault upgrade.

## Switched OFF by default (how to turn each on)

| Feature | Setting | Turn it on |
|---|---|---|
| Island Fuel | `production.yml → fuel.enabled: false` | Set `true`, then `/skyblock reload production` (or restart). See [Island Fuel](#island-fuel-off-by-default). |
| Custom island generators | `generators.yml → enabled: false` | Set `true`, then `/skyblock reload production`. See [Custom island generators](#custom-island-generators-off-by-default). |
| Depths v2 tier palette (spike) | `depths.yml → palette.enabled: false` | Set `true`, then `/skyblock reload depths`. Follow [DV2_SPIKE_TEST.md](phases/DV2_SPIKE_TEST.md). |
| Vote receiver | `votes.yml → receiver.enabled: false` | Register the server on the vote sites first. Steps in [Voting](#voting-vote). |
| Farming (M7.3) | not merged yet | Comes behind the same production switch. |
| Unbuilt hub buttons | `navigation.yml → hub.show-unbuilt: false` | `true` shows them as "Coming soon" (test server only). |

## Test these first (most changed since update92)

1. Tutorial: with a fresh account (or ask staff for `/tutorial admin reset <you>`) join, watch the spawn camera, walk into the island portal and play all 11 steps. Locked features say which step opens them. Finish: $2,500 + 25 crystals and the Firstflight title.
2. `/menu`: 15 buttons in 4 groups; your head opens You; Travel takes you places; Rewards glows when something is ready and claims it in one click; Top opens the Top screen.
3. Click (left or right) the Guide, Banker, Crate Keeper, Daily Rewards, Quest Master and Voting NPCs: each opens its menu at once. Sneak-click shows the help.
4. Crate yard: click a crate with a key (opens 1 with the floating reward), sneak-click (rewards), click without a key (how to get keys). Then `/crates` → preview → Withdraw 1 key (tradeable keys only).
5. `/is level`, the Challenge Book, `/is upgrade` (buy Island Size from the island bank), `/is masteries`, `/is top`.
6. `/daily` (claim today), `/quests`, `/pass` (claim a level), `/collection`, `/achievements`, `/boosters`, `/blackmarket`.
7. `/shop`: tabs, buy page, Undo last buy within 15 s, a buy over half your money needs a second click.
8. `/settings`: tabs Chat, Social, Display, Sounds, Gameplay; try "Show players" in the Depths.
9. Money: `/pay` with a fresh account (locked until tutorial step 11, then needs $10k earned), `/withdraw` 21 notes in a minute (the 21st is refused).
10. Depths: fill your satchel and keep mining: drops go to your inventory, then `/box`. Fight a mini-boss with a friend and read the boss bar (scaled health).
11. Island Fuel and generators: switch them on (table above) and test their sections. Switch them off again after the test if you don't want them live yet.
12. Bedrock: tutorial cameras (cut-and-fade), Pip, the hub, a TPA request (popup with buttons), shop buy page (number picker), crate yard tap, `/settings` tabs.

## Contents

- [Look & feel](#look--feel)
- [Tutorial](#tutorial)
- [Profiles](#profiles)
- [Islands](#islands)
- [Island progression](#island-progression)
- [Island production](#island-production)
- [Social & chat](#social--chat)
- [Teleports & essentials](#teleports--essentials)
- [Ranks, perks & cosmetics](#ranks-perks--cosmetics)
- [Worlds & builds](#worlds--builds)
- [Staff & punishments](#staff--punishments)
- [Economy](#economy)
- [Rewards](#rewards)
- [The Depths](#the-depths)
- [Tools, skills & enchants](#tools-skills--enchants)
- [Wardrobe & sets](#wardrobe--sets)
- [Stacking & spawners](#stacking--spawners)
- [AFK pool](#afk-pool)

## Look & feel

### Tab list and nametags

The tab list shows a SKYBORNMC header, the online count and season, and every player as 'Title Rank Name (Tag) [AFK]', sorted by rank. Nametags above heads show the rank prefix. Staff also see server TPS and their ping at the bottom.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Press Tab to open the list. Staff see TPS and ping with the staff tab permission. |

**Test:**

1. Press Tab (Java) or open the player list (Bedrock). Check header, online count, season text and footer address.
2. Check the order: owner first, then admin, mod, helper, builder, ranks, default; same rank sorted A to Z.
3. Equip a title and a tag with /tags. They appear in the tab list within a second.
4. Go AFK with /afk. [AFK] appears next to your name.
5. Ask staff to change your rank. The tab entry and the nametag change within about a second.
6. As staff check the TPS (green/orange/red) and ping line at the bottom.
7. Bedrock: check the symbols (arrows, dots) do not show as boxes.

_Questions for this feature: 8 on the Decisions page (Feature = "Tab list and nametags")._

### Main menu (/menu), menu item and /help

**Changed (GUI-2, GUI-4c):** /menu is a clean hub with **15 buttons** in four groups: your head (opens the **You** menu), Play (Home, Island, The Depths, Travel), Money (Sell, Shop, Bank, Auction House), Power (Forge, Skills, Enchanter, Wardrobe) and More (Rewards, Top). Features that are not built are **hidden** (no more 'Coming soon'). **You** has Profiles, Titles & Tags, Achievements, Settings, Store, Rules & links (and Quests, Collection). **Travel** replaces the old Warps menu: Spawn, My Island, Depths Gate, Crate Yard, Market, Forge Quarter, Visit Islands. **Rewards** glows gold with the number of things ready (daily reward, pass levels, waiting votes) and claims them in one click; with nothing ready it opens /daily. **Top** opens the Top screen (see Island leaderboards). Every player has a Nether Star in hotbar slot 9 ("Menu · Click"); it can never be lost. /help explains the server with categories, topics and command lists, and can take you to places.

| Command | Who | What |
|---|---|---|
| `/menu (/gui)` | player | Open the main hub. |
| `/help [topic\|category\|commands] (/guide)` | player | Open the help menu or a topic. Every menu's ? button opens its help topic. |
| `/staff (/staffmenu)` | helper | Staff hub: display editor, config editor, titles & tags, staff tools, tutorial stats. |
| `/admin (/config)` | admin | In-game config editor for every module's settings (logged). Bedrock types values in a popup. |

**Test:**

1. Join with a fresh account. Check there is a Nether Star in slot 9. Click it with either mouse button (Java) or tap it (Bedrock): the menu opens with a real title (not "Large Chest").
2. Open /menu: 15 buttons in four coloured rows with space between. Click every button; each opens its screen and **Back** returns to the hub.
3. Click your head: the You menu. Open Profiles, Titles & Tags, Achievements, Settings; Store and Rules print their links.
4. Open Travel: try Spawn, Crate Yard, Market and Depths Gate. A place whose build is missing is hidden.
5. Leave a daily reward unclaimed: Rewards glows with a number. Click it: the reward is claimed and one line says what you got.
6. Try to drop, move, store or lose the Nether Star (death, chest, hopper): it always comes back. Turn the menu item off in /settings: it disappears; on again: it returns.
7. Type /help. Open a topic, click 'Take me there' and 'Show in chat'. Check the command lists only show commands you may use.
8. As staff open /staff and /admin: change a harmless setting and check it appears in /logs under config.
9. Bedrock: open /menu with the item; all names readable; /help pages show their text in the lore.

_Questions for this feature: 9 on the Decisions page (Feature = "Main menu (/menu), menu item and /help")._

### Personal settings (/settings)

**Changed (GUI-4c):** /settings now has one **tab per group** in the top row: Chat, Social, Display, Sounds and **Gameplay**. Each switch name says On or Off. Players can switch: private messages, mention sound, join messages, chat colours, timestamps, daily reward reminder, teleport requests, trade requests, island invites, AFK messages, hide ignored chat (on by default), scoreboard, menu item, particles, tips, global booster bar, pillar notices, menu sounds and event sounds. **Show players** (Display) is a choice: Everyone / Friends + island / Nobody; it only works in the Depths and the AFK pool, and staff are always visible. **Gameplay**: island auto-pickup, drop guard, auto-refill blocks (on), fast crate opening (off), shop Undo last buy (on), shop second click for big buys (on). Changes apply immediately.

| Command | Who | What |
|---|---|---|
| `/settings [setting] [on\|off\|toggle\|<choice>] (/options /prefs)` | player | Open the settings menu or change one setting by name, e.g. `/settings show-players nobody`. |

**Test:**

1. Open /settings. Click each tab: the switches change in place (no flicker). Turn off 'Scoreboard': it disappears at once. Turn it on again.
2. In the Depths with a friend near you: set Show players to Nobody: the friend disappears. Friends + island: only island members show. Staff stay visible.
3. Turn off 'Private messages': another account's /msg to you is blocked. Staff can still reach you.
4. Turn off 'TPA requests', 'Trade requests' and 'Island invites' and test each from a second account.
5. Turn on 'Chat timestamps': times appear before chat lines.
6. Turn off 'Menu sounds' and 'Event sounds' and check menus are silent.
7. Switch to profile 2 and check the settings are separate per profile.
8. Type /settings scoreboard off (chat form) and use Tab to see all names.
9. Bedrock: open the menu and tap toggles.

_Questions for this feature: 8 on the Decisions page (Feature = "Personal settings (/settings)")._

### Server list MOTD and join flow

The server list shows an animated SKYBORNMC / PHOENIX SKYBLOCK message in two lines, with hover text about the status and versions. The server is whitelisted for testing. When a player joins they land at spawn and new players get a broadcast with their join number. **Changed (GUI-BUGS): one welcome.** While the tutorial runs, its title and chat block are the only welcome (one next step: the island portal); the join flow's own welcome title and lines only show when the tutorial is off. The privacy line comes 45 seconds later.

| Command | Who | What |
|---|---|---|
| `(no command)` | admin | MOTD is edited in motd.yml or with /admin. The whitelist is managed with the normal whitelist command. |

**Test:**

1. Add the server in Minecraft Java and Bedrock. Check the two MOTD lines, animated gradient and hover text.
2. Check the player count and version text. Check the icon.
3. Join with a new account (whitelisted): broadcast 'Welcome <name> to Phoenix Skyblock (#N)' and only **one** welcome: the tutorial's title and chat block (no second welcome title or extra lines). After about 45 s: the line that chat and commands are logged for 30 days with the privacy link.
4. Leave and join again: 'Welcome back' title.
5. Try a non-whitelisted account: you should see a clear message that the server is in testing.
6. Join with an old Java version (1.20) and a new one (1.21.5+): old gets a friendly kick message, new joins.
7. Check join/quit lines: only ranked players have them.

_Questions for this feature: 10 on the Decisions page (Feature = "Server list MOTD and join flow")._

### Scoreboards (sidebar)

Each player sees a sidebar that changes with where they are: spawn, their own island, someone else's island, The Depths (gate, mine, forest, combat) and the warzone. It shows name and rank, money, crystals, gems and, on the island, the next to-do note. **Changed:** a blank line under the title (FIXES1); a "You" header and a **Next** line (tutorial step or to-do note); Crystals and Gems lines only once you own some (GUI-4); the **island level line is back** (M5.2). There is no fuel line yet (the island layout is full). Players can turn the scoreboard off in /settings.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Turn the scoreboard on/off with /settings scoreboard. Layouts are edited in messages/hud.yml. |

**Test:**

1. Join and look at the scoreboard at spawn: blank line under the title, "You" (name, rank, money; crystals and gems only once owned), Island with the island level, and the Next line during the tutorial.
2. Go to your island: layout changes, and a 'Next:' note line appears when you have an open island note and the setting is on.
3. Visit someone else's island: 'Visiting: <island>' is shown.
4. Go to The Depths gate, mine, forest and combat zones: each has its own layout.
5. Teleport quickly between places: no flicker.
6. Switch profile: money, crystals and name change at once.
7. Turn the scoreboard off in /settings and on again.
8. Bedrock: check all lines fit, symbols are visible and nothing is cut at the edge.

_Questions for this feature: 9 on the Decisions page (Feature = "Scoreboards (sidebar)")._

### Resource pack plan

The plan adds a resource pack for Java and Bedrock with custom item looks, menu backgrounds, font icons, sounds, HUD bars, crate animations and a custom border. Decided: the Java pack is **forced** (players accept it before they join) and **you draw the art** from the art brief (docs/plans/RESOURCE_PACK_ART_BRIEF.md, every texture listed). Everything still works without the pack (vanilla fallback for Bedrock and failed downloads). Nothing is built yet: RP1 starts when the first art arrives.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | The pack is sent before joining; there is no on/off toggle while it is forced. |
| `/skyblock reload` | admin | Planned: push a new pack version to players |

**Test:**

1. Nothing to test in game yet (planning only).
2. Read docs/plans/RESOURCE_PACK.md section 0 and 17 and the art brief.
3. Answer the remaining RP-D questions on the Decisions page (feature "Resource pack plan").
4. Draw the 5 pilot pieces from the art brief first, so they can be checked in game before you draw the rest.
5. Check that the plan says every menu stays usable with the pack off, and tell the developer if you disagree.

_Questions for this feature: 24 on the Decisions page (Feature = "Resource pack plan")._

### Look and feel V2 (colors, lore, menus, scoreboard)

The whole server uses one style: brand red, ember, aqua crystals and green gems, short messages with a mark, lore with a red bar and bold yellow action lines, and the same menu layout everywhere. Bedrock gets safe color and symbol fallbacks. The scoreboard changes by place (island, Depths, spawn). The sketches and canvas V2 boards are approved.

| Command | Who | What |
|---|---|---|
| `/settings` | player | Toggles for sounds, particles, action bar XP, tips, scoreboard |
| `/skyblock reload core` | admin | Reload colors from style.yml |

**Test:**

1. Open some menus (shop, sell chest, satchel, forge, wardrobe) and compare layout and colors.
2. Read a tool or book lore: red bar headers, yellow action lines, rarity footer.
3. Check the scoreboard at spawn, island, Depths and while visiting another island.
4. Open /settings and toggle sounds, particles, tips and see the effect.
5. Compare Java and Bedrock for colors and symbols (arrows, hearts, crystals).
6. Trigger an error message and a success message and check the mark and color.
7. Look at chat prefix, tab list and the join/quit lines.

_Questions for this feature: 11 on the Decisions page (Feature = "Look and feel V2 (colors, lore, menus, scoreboard)")._

### Menu frame (every menu) — new

Every menu now uses one frame (GUI-1, GUI-4, GUI-4b, GUI-4c). Bottom bar: 45 Back, 48 ? (help for this screen), 49 Close, 50 Main menu, 52/53 pages; small 3-row menus have Back on 18 and Close on 22. **Back** always returns to the screen you came from (also from /menu) and only shows when there is somewhere to go back to. Titles read `Feature » Screen`. Tabs and pages switch **in place** (no flicker). Loading, error, empty and locked tiles look the same everywhere. A **confirm screen** shows the details on the middle item; disband and profile delete use a **two-step** confirm. Both mouse buttons work on every button (Java-only shortcuts like shift-click are only named in the ? guide). One-way "cyclers" became **pick lists** (AH sort, island weather/time/colours, warp category, transactions, logs, staff filters). Places where you type (notes, warp names, loadout names, AH search, player search, `/admin` values, `/logs` searches) open a **text popup on Bedrock**.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Works in every menu. |
| `menus.yml → title.update-in-place` | admin | Set `false` if a Bedrock player sees an old title after a tab or page change (reopens the window instead). |

**Test:**

1. Open /menu → Island → Upgrades → an upgrade. Press Back three times: you step back the same way to the hub.
2. Open /shop by command: no Back button (nowhere to go back to), but Close and Main menu.
3. Click ? in a few menus: the help topic opens while the menu waits; Back returns.
4. Switch tabs in /shop, /settings and /quests: the window stays, only the content and title change.
5. Open a pick list (AH sort, `/is settings` weather): all choices are shown, click one.
6. Type in a popup on Bedrock: `/is notes` → Add note, AH search, warp rename.
7. Bedrock: check an old title never stays after a tab change. If it does, tell the developer (setting above).

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → GUI-1, GUI-4, GUI-4b, GUI-4c._

### Bedrock popups instead of clickable chat — new

Bedrock can't click chat text. Wherever Java players click a chat line, Bedrock players now also get a **small popup with buttons** (GUI-5): TPA and TPA-here requests, island invites, trade requests, the `/discord /store /vote /website /rules` links, picking a command in /help, the tutorial "I'm stuck" skip offer, and for staff the `/eco` Allow/Deny alert, report alerts (Teleport / Inspect / Claim) and hopper alerts (Teleport). Popups wait behind open menus and show "Expired" when the request ended. The chat line is still sent.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Bedrock only. Java keeps the clickable chat. |

**Test:**

1. Bedrock player B, Java player A: A types `/tpa B`. B gets a popup with Accept / Deny. Accept works.
2. Same with `/is invite B` and `/trade B`.
3. B types `/discord` and `/vote`: a popup with the address; the button sends it as plain text in chat.
4. B opens /menu, then A sends a TPA: the popup waits until B closes the menu. Let a request expire (60 s): the popup says Expired.
5. Staff on Bedrock: get a report alert and press Teleport.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → GUI-5._

### Quality of life: auto-pickup, drop guard, auto-refill, "Did you mean" — new

Small helpers (QUICK1, QOL3). **Island auto-pickup:** blocks you break on your own island go straight into your inventory (with a full inventory the rest stays on the ground). **Drop guard:** a custom or unique item (tools, books, keys, notes) or a spawner needs a second drop or sneak + drop. **`/sell all`** skips your hotbar. **Auto-refill blocks:** placing the last block of a hotbar or off-hand stack moves the next stack of the same block in from your backpack. **Did you mean:** a mistyped command suggests the closest command you may use (Java: click it to fill the chat box). **AFK notice:** a private message to an AFK player tells you how long they have been AFK (once a minute). Auto-pickup, drop guard, sell-all and auto-refill each have a switch in `/settings` → Gameplay.

| Command | Who | What |
|---|---|---|
| `/settings` → Gameplay | player | Auto-pickup, drop guard, sell-all skips hotbar, auto-refill (all on by default). |

**Test:**

1. On your island break 10 cobblestone: it goes into your inventory, nothing on the ground.
2. Hold your starter pickaxe and press Q once: it stays, with a message. Press Q again (or sneak + Q): it drops.
3. `/sell all` with wheat in the hotbar and in the backpack: only the backpack wheat is sold.
4. Hold 3 stone in the hotbar and 64 stone in the backpack. Place 3 blocks: the hotbar slot refills. Turn auto-refill off in /settings and try again.
5. Type `/shpo`: "Did you mean /shop?". Type a command you may not use with a typo: it is never suggested.
6. Put an alt on /afk and send it a /msg: you get "<name> has been AFK for N min".
7. Bedrock: auto-refill and the drop guard work the same with taps.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → QUICK1, QOL3._

## Tutorial

### Join tutorial (11 steps) — new

The first thing a new player does. The **spawn camera** plays on the first join, then a flame trail and a boss bar compass lead to the **island portal** in the Gatehouse. 11 short steps (about 3 minutes): portal, arrive on your island (island camera), open the **starter chest**, plant 3 wheat, open the island menu, go to Mine Tier 1 (Depths camera), mine 15 times, sell something, open the Forge, click any NPC, put money in the bank. Features **unlock step by step**: island team/visit/warps after step 4, the Depths after step 5, `/ah` after step 8, `/pay` and `/withdraw` after step 11, `/trade`, the warzone portal and new profiles after the finish. Finishing pays **$2,500 + 25 crystals** and the title **Firstflight**, once per account (finishing on one profile opens every profile). **Pip**, a small red parrot only you see, flies to the target and says one line per step. Guides: boss bar compass, action bar hint, scoreboard Next line, particles and a hologram over the target only you see. After the tutorial, **9 optional steps** (generator, spawn tour, settings, skills, team, upgrades, Enchanter, wardrobe, warzone) pay 5 crystals each, with a first-visit tip (turn tips off in `/settings`). The **starter chest** holds seeds, bone meal, saplings, a lava bucket, bread, a crafting table, a chest, the **Phoenix Field Guide** book and the Forge starter kit. A player with island invites gets a choice at the portal: **Create my own island** or **Join <island>**. Players who were here before the tutorial count as finished (no reward). Changed after testing (TUT-FIX): a camera can never trap you; any move, jump, sneak or command skips it.

| Command | Who | What |
|---|---|---|
| `/tutorial` | player | Tutorial menu: progress, Take me there, I'm stuck (after 3 presses some steps can be skipped), Steps, Watch again, Optional steps, What unlocks when; Where next? when done. The Guide NPC at spawn opens it too. |
| `/tutorial admin status <player>` | helper | Step, state, done steps, reward paid. |
| `/tutorial admin skip <player>` | mod | Finish for a player, **no reward**; every lock opens (confirm). |
| `/tutorial admin reset <player>` | mod | Back to the start, locks return, never a second reward (confirm). |
| `/tutorial admin step <player> <step>` | mod | Move a player to a step. |
| `/tutorial admin stats` | mod | Stats per step (reached, done, where players stop, time, I'm stuck presses); 7/30 days, All/Java/Bedrock. Also in /staff and `/inspect → Tutorial`. |

**Test:**

1. New account (or `/tutorial admin reset <you>` and rejoin): the spawn camera plays (Java smooth, Bedrock cut-and-fade). Sneak: it skips.
2. Follow the flame trail and compass to the island portal. Island camera, then "Welcome home". Move or jump during the camera: it ends at once and you can walk (no relog needed).
3. Open the starter chest and take the pickaxe. Check the Field Guide book and the 5 kit tools.
4. Try `/is invite`, `/dungeon`, `/ah`, `/pay`, `/trade` too early: each says "Unlocks at tutorial step N" (chat at most every 10 s).
5. Plant 3 wheat, open /menu (the Menu step completes), go to the Depths, mine 15, sell, open the Forge, click an NPC, deposit in /bank. Each step shows a title and Pip's line.
6. Finish: title, chat summary, $2,500 + 25 crystals, the Firstflight title, the Where next? menu. Reset as staff and finish again: no second reward.
7. Quit in the middle of step 7 and rejoin: "Welcome back" and a teleport to Mine Tier 1 after 2 s (sneak to stay).
8. Press I'm stuck 3 times on the Depths step: the skip button shows. On the sell or bank step it never does.
9. After finishing, open /skills: the optional-step tip and 5 crystals. Turn Tips off in /settings.
10. Get an island invite on a fresh account, then walk into the portal: Create / Join menu.
11. Staff: `/tutorial admin stats`, `/inspect <player>` → Tutorial (book), Skip and Reset.
12. Bedrock: cameras use cut-and-fade with titles; Pip and the target hologram are visible; the boss bar compass reads well; the I'm stuck popup works.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → TUT1, TUT2, TUT-FIX, PLANTUT (plan: docs/plans/TUTORIAL.md)._

## Profiles

### Profiles (up to 3 islands per player)

Every player can have up to three profiles. A profile is one island plus its own money, crystals, items, ender chest, skills, `/box` and progress. Gems, rank, titles and cosmetics are shared between all profiles. Every profile gets the start money. You switch with /profiles, and nothing can ever be moved between your own profiles. Deleting a profile deletes everything on it, auction listings and collection bin included (a profile with items in `/box` can't be deleted until you take them out). New since update92: crates keys, boosters, daily calendar, quests, pass progress, collection log and fuel cells are per profile too; titles and the premium pass stay with the account. A 2nd or 3rd profile opens after the tutorial.

| Command | Who | What |
|---|---|---|
| `/profiles (/profile)` | player | Open the profile menu: switch, create, rename, change icon, buy slot 2 or 3, delete a profile. |

**Test:**

1. Type /profiles. Profile 1 is yours and active. Slots 2 and 3 are locked and show a price (500 and 1,500 gems) or the rank needed.
2. Ask staff to give you gems, then buy slot 2. Check the gems go down only once and the slot opens.
3. Click the empty slot and create a profile. You land at spawn with an empty inventory, the start money ($1,000) and no island. Gems and rank stay the same.
4. Run /is on profile 2 and make an island, then put a stone block in your inventory. Switch back to profile 1: your old items, money and island home are back and you land at your old island.
5. Rename the profile (you type the new name in chat) and change its icon. Relog right after a switch: you must come back on the same profile.
6. Try to cheat on purpose: drop an item on profile 1, switch to profile 2 and try to pick it up. It must be refused. Also try to cash a money note made on the other profile and to buy your own other profile's /ah listing. All must be refused.
7. Try to visit or join the island of your other profile (use an alt to invite it). It must be refused.
8. Put an item on profile 2's `/ah` and delete profile 2: **changed (GUI-1):** a two-step confirm ("Last check: this can't be undone", buttons swap places), no typing. The listing is gone too. You cannot delete the profile you are playing on, nor one with items in `/box`.
9. Try to create a 2nd profile before finishing the tutorial: "Unlocks when the tutorial is done".
10. Bedrock: do all of the above by tapping. Check that renaming works on a phone (text popup).

_Questions for this feature: 14 on the Decisions page (Feature = "Profiles (up to 3 islands per player)")._

## Islands

### Island creation, starter island and homes

A new player lands at spawn and walks into the **island portal** in the gatehouse: the island is created right away (no confirm screen) and they are brought there; later the same portal always takes them home. Typing /is without an island still opens the 'Create your island' screen. Once you have an island, /is opens the island menu and /is home (or just /home) takes you back, instantly. Leaders, Admins and Mods can move the island home. There is no creation cooldown, and players always respawn at spawn. **New:** the starter chest is the tutorial's custom chest (seeds, bone meal, saplings, lava bucket, bread, crafting table, chest, the Phoenix Field Guide and the Forge starter kit); a player with invites gets a Create / Join choice at the portal (it lists every invite). The **Homestead** mastery gives extra **shared island homes** with a name (`/is home|sethome|delhome <name>`, new island permission Homes).

| Command | Who | What |
|---|---|---|
| `Island portal (gatehouse at spawn)` | player | No island on this profile: creates it and takes you there. With an island: instant teleport home (3 s while in combat). |
| `/is` | player | No island: open the 'Create your island' screen. With an island: open the island menu. |
| `/is home (/is go, /home)` | player | Teleport to the island home (instant; 3 s while in combat). |
| `/is sethome (/sethome)` | player | Set the island home where you stand (Leader, Admin and Mod). |
| `/is home\|sethome\|delhome <name>` | player | Shared named island homes from the Homestead mastery (island permission Homes). |

**Test:**

1. Use a fresh account. Join and walk from the spawn point to the gatehouse island portal. Step in: a 'Creating your island' title, the starter island appears and you land on it with a starter chest. Open the chest and check the contents.
2. Type /spawn, walk into the island portal again: you go straight home, no new island.
3. Make a new profile with /profiles and use the portal again: that profile gets its own island the same way.
4. On a second fresh account type /is instead: you see the 'Create your island' confirm screen. Confirm.
5. Type /spawn, then /home. You land on the island home at once (no countdown).
6. Stand somewhere else on the island and type /sethome, then /is home from spawn. You land at the new spot. Try to set a home outside the island border or in the air: refused.
7. Try /is setmyhome: it no longer exists (tab completion does not show it).
8. Jump off the island into the void. You should land back on your island home without damage.
9. Die (ask staff to /kill you), also with a bed next to you: you always respawn at spawn and keep your inventory.
10. Hit a mob, then type /is home within 10 seconds: you wait 3 seconds.
11. With the Homestead mastery (or staff help): `/is sethome farm`, then `/is home farm` from spawn as another member. `/is delhome farm`. Take the Homes permission from Member: refused.
12. Bedrock: walk into the portal, use the create screen and the island menu by tapping; check the title and the chest are readable.

_Questions for this feature: 10 on the Decisions page (Feature = "Island creation, starter island and homes")._

### Island team: invites, roles, coop and permissions

Leaders can invite players to form a team island (4 members plus the leader, 8 plus the leader with upgrades). Roles are Leader, Admin, Mod, Member and Coop (temporary trust). A permission screen lets the leader decide what each role may do (break, place, containers, spawners and more). Members can leave and the leader can transfer the island. There is no join cooldown (only 3 island joins per season). A player you `/ignore` can't invite you. When a member is kicked, a backup of their role, money and (if online) inventory is kept, and an admin can restore it. **New (ISL-R3):** a member slot freed by a leave or kick is **locked for 48 hours** before a new invite can use it (staff removals and members inactive 21+ days don't lock). **New (GUI-4):** the permission screen has presets **Strict / Normal / Open** and an Advanced view; new permissions **Upgrades** (leader always, Admin by default) and **Homes**. Inviting, coop, visiting and warps open at tutorial step 4.

| Command | Who | What |
|---|---|---|
| `/is invite <player>` | player | Invite an online player (Leader, Admin, Mod). The first invite makes the island a team island forever. |
| `/is accept [player]` | player | Accept your newest invite, or the one from that island. |
| `/is deny [player]` | player | Decline an invite. |
| `/is invites` | player | Menu of invites you got and invites your island sent (cancel them here). |
| `/is members` | player | Member list with roles and actions (promote, demote, kick, transfer, permissions). |
| `/is kick <player>` | player | Kick a lower role (Leader > Admin > Mod > Member). |
| `/is leave` | player | Leave your island (confirm). The leader must transfer first. |
| `/is promote <player> / /is demote <player>` | player | Move a member up or down one role. |
| `/is transfer <player>` | player | Leader only: give the island to a member. The old leader becomes Admin. |
| `/is coop <player> / /is uncoop <player>` | player | Give or remove temporary trust for an online player (max 4). |
| `/isadmin addmember <island> <player>` | admin | Staff: add a member without an invite. |
| `/isadmin setowner <island> <member>` | admin | Staff: make a member the leader. |
| `/isadmin restore <player>` | admin | Staff: open the player's kick and reset backups and restore one (rejoins with the old role, money topped up, items into an empty inventory). |

**Test:**

1. With two accounts: account A has an island. A types /is invite B. A gets a warning that the island becomes a team island forever. Confirm.
2. B types /is (or clicks the invite). B accepts. Both see messages. Check B appears in /is members with role Member.
3. Open the permissions screen from /is members. As A, turn off Break for Member. B should no longer be able to break blocks on the island.
4. A promotes B to Mod, then to Admin. Check what B can now do (invite, kick a Member, change settings).
5. A uses /is transfer B. B becomes Leader and A becomes Admin.
6. Coop: A types /is coop on a third account C. C can build; when A and all Mods log out, C is no longer trusted.
7. B types /is leave and confirms, then joins another island right away: allowed (no cooldown). A fourth join in one season is refused.
8. Fill the island with the leader + 4 members and invite one more: refused (island full). The member list shows the count.
9. Kick a member who is online with items and money. As admin type /isadmin restore <member>: the backup is listed; click it and confirm. The member is back with the old role, money topped up, items given back only if their inventory is empty.
10. Let C /ignore A, then A invites C: A gets the same answer as when invites are off.
11. Kick B, then invite D at once: refused with a clear message that the slot is locked for 48 h.
12. Open permissions: pick Strict, Normal and Open and check Member's rights change; open Advanced for single switches.
13. Bedrock: open /is members, tap a head, check the action buttons are readable; open the /isadmin restore menu by tapping.

_Questions for this feature: 12 on the Decisions page (Feature = "Island team: invites, roles, coop and permissions")._

### Visiting: browser, lock, ban, ratings and likes

Players can visit other islands from a browser or by name, instantly (3 seconds only while in combat). The browser opens on **Discover**: every island in random order, favouring islands with more playtime; the other tabs are Most visited, Top rated, Most liked and Newest. There is no visitor limit. Leaders, Admins and Mods can lock the island, send visitors away and ban players. Visitors can rate an island 1 to 5 stars once per season and like it once a day; both need 1 hour of playtime and at most 3 accounts from one address per island. An island whose leader is banned is closed to visitors. Staff can hide an island from the browser. **New (ISL-R3):** **Top rated** only lists islands rated from at least 5 different addresses and uses a weighted average (a few 5-star votes no longer win). Players hidden by the AFK pool or "Show players" can still be visited by name. Visiting opens at tutorial step 4.

| Command | Who | What |
|---|---|---|
| `/is visit [player]` | player | Open the visit browser (Discover tab first), or go to a player's island (instant; 3 s in combat). |
| `/is lock / /is unlock` | player | Leader/Admin/Mod: only members and coop players can enter a locked island. |
| `/is expel <player>` | player | Send a visitor on your island to spawn. |
| `/is ban <player> / /is unban <player>` | player | Ban or unban a visitor from entering your island (max 100). |
| `/is rate <player> <1-5>` | player | Rate an island you have visited. |
| `/is like <player>` | player | Like an island you have visited (once a day). |
| `/is info [player]` | player | Show info about your island or another player's island in chat. |
| `/isadmin hide <island> [note] / /isadmin unhide <island>` | mod | Staff: hide an island from the visit browser (still visitable by name) or show it again. |

**Test:**

1. With two accounts: B types /is visit and picks A's island in the browser (or /is visit A). B lands on A's island at once and A sees a visitor message.
2. As B try to break a block. It must be refused (visitors can only look by default).
3. A types /is lock. B (on the island) is sent to spawn. B tries /is visit A again: refused. A types /is unlock.
4. A bans B (/is ban B). B cannot enter by /is visit, /tpa, ender pearl or by relogging on the island. A unbans B.
5. B types /is rate A 5 and /is like A. Try /is like A again the same day: refused. Try to rate your own island: refused.
6. Try /is rate on an island you never visited, and with an account under 1 hour of playtime: both refused. A 4th account from the same address: refused.
7. Open the browser: Discover is first and the order changes each time you open it. Check the other four tabs. Locked islands should not show up.
8. As mod: /isadmin hide <A's island> test. It disappears from the browser but /is visit A still works. /isadmin unhide brings it back.
9. Ban A's leader (test account) with /p: visitors on A's island are sent to spawn and it is gone from the browser; A's members still play. Unban: it opens again within 2 minutes.
10. Hit a mob, then /is visit A: you wait 3 seconds and moving cancels it.
11. Rate a new island 5 stars from 2 accounts: it does not show in Top rated until 5 different addresses rated it.
12. A stands in the AFK pool (hidden): B can still `/is visit A` by name.
13. Bedrock: open the browser and tap an island.

_Questions for this feature: 12 on the Decisions page (Feature = "Visiting: browser, lock, ban, ratings and likes")._

### Island warps and warp signs

Every island gets 3 warps (more with the Warp Slots upgrade in `/is upgrade`) in five categories: shops, farms, parkour, builds, other. Warps can be public or private and have an icon. Players can make a warp by writing [warp] on a sign. Other players find warps in the warp browser. **New (GUI-4, GUI-4b):** the category is a pick list, warps can be renamed in the menu (Bedrock: text popup).

| Command | Who | What |
|---|---|---|
| `/is setwarp <name> [category]` | player | Leader/Admin/Mod: set a warp where you stand. |
| `/is delwarp <name>` | player | Delete one of your island's warps. |
| `/is warp [player] [name]` | player | Open the warp browser or go to a warp (instant; 3 s in combat). |

**Test:**

1. On your island type /is setwarp farm farms. Check it appears in /is warp under Your warps.
2. Open the warp, change the icon, make it public and private. Check as another player that private warps are hidden.
3. As another player: /is warp, pick a category and use the warp. You arrive at once. Then try to set a warp whose name has a swear word: refused by the chat filter.
4. Place a sign, write [warp] on line 1 and a name on line 2. The sign turns into a warp sign and uses one slot.
5. Try a 4th warp: refused. Break the sign: the warp is removed.
6. Set a warp with the same name again: it moves instead of making a second one.
7. Bedrock: open the browser and tap warps. Also try the [warp] sign on a phone.

_Questions for this feature: 9 on the Decisions page (Feature = "Island warps and warp signs")._

### Island settings, weather, time and island fly

Each island has settings the leader (and anyone allowed) can change: mob spawning, animal spawning, fire spread, explosions, leaf decay, crop trampling, visitor item pickup, visitor alerts, visitor fly, weather and time of day. Weather and time are shown only to players on that island (pick lists since GUI-4). Island fly lets people fly inside the island border when the island or player has the right; the **Wings** mastery gives island fly to everyone on the island (visitors only with the visitor-fly setting).

| Command | Who | What |
|---|---|---|
| `/is settings [setting] [on\|off\|value]` | player | Open the island settings menu, or change one setting right away. |

**Test:**

1. Type /is settings. Check the defaults: mob spawning off, animal spawning on, fire spread off, explosions off, leaf decay on, crop trampling off, visitor pickup off, alerts on, weather sunny, time day.
2. Set weather to rain and time to night. You (and everyone on your island) see it; someone at spawn does not.
3. Put TNT on the island and light it with explosions off: blocks stay. Turn explosions on and test again on a spare spot.
4. Turn crop trampling on, jump on farmland, then off again.
5. As a visitor with another account: try to pick up a dropped item (off by default). Then allow it in the matrix and try again.
6. Island fly: ask staff for skyblock.island.fly. Double-tap jump to fly inside the border. At the border you cannot go further. Take the permission away and check you get a soft landing.
7. Bedrock: open the settings menu and tap each setting. Check weather/time cycle values on tap.

_Questions for this feature: 10 on the Decisions page (Feature = "Island settings, weather, time and island fly")._

### Island notes (shared to-do list)

Each island has one shared notes list (max 50 notes, 200 characters each). Members add notes, tick them done, edit, pin them as floating text in the world and delete them. Staff can read any island's notes. Deleted notes are kept 7 days for staff.

| Command | Who | What |
|---|---|---|
| `/is notes [island]` | player | Open your island's notes. Staff can add an island name to read another island's notes. |

**Test:**

1. Type /is notes. Click Add note, type a note in chat. It shows in the list with your name and the date.
2. Click a note to tick it done. Click it again to reopen. Edit its text.
3. Try a 201-character note, a link and a bad word: all refused.
4. Pin a note: a floating text appears where you stand. Relog and restart: no ghost copies. Tick it done: the text disappears.
5. Open the book view: edit a page and close the book. The note is saved.
6. With a second member account: it gets 'added a note'. A Member can tick notes done but only delete their own notes.
7. As a staff member: /is notes <island>. Read only; check it is logged in /logs staff.
8. Bedrock: add a note (chat typing) and open the book view.

_Questions for this feature: 9 on the Decisions page (Feature = "Island notes (shared to-do list)")._

### Island stats

The stats screen shows what your island and each member did: blocks mined, logs chopped, crops harvested, blocks placed, generator output, mobs killed, items sold and money earned, visitors and time on the island. It shows today, this week, last week and all time, with each member's share. Blocks you just placed do not count when broken again.

| Command | Who | What |
|---|---|---|
| `/is stats` | player | Open the island stats menu (also from the island menu). |
| `/isadmin stats view <island>` | helper | Staff: open any island's stats read only. |
| `/isadmin stats reset <island>` | admin | Staff: delete all stats of an island (confirm). |

**Test:**

1. Mine a few blocks of generator stone, chop a tree, kill a mob and sell some items. Reopen /is stats: the numbers go up.
2. Place a block and break it again: the mined count should not change.
3. Click a category to see each member's share (use a second member account).
4. Have a visitor come twice on the same day: the unique visitor count only goes up once.
5. As staff: /isadmin stats view <island>. Check it is read only and logged.
6. As admin: /isadmin stats reset <island> and confirm. Everything resets.
7. Bedrock: tap each category and check the percent bars are readable.

_Questions for this feature: 8 on the Decisions page (Feature = "Island stats")._

### Island chat

Members of an island can talk privately to each other in island chat. Messages are coloured by role and the leader can change the role colours. Staff can turn on a spy toggle. The word filter, mute and rate limit still apply.

| Command | Who | What |
|---|---|---|
| `/is chat [message]` | player | Without text: switch your chat to island chat and back. With text: send one message to your island. |

**Test:**

1. Type /is chat. All your next messages go only to your island. Type /is chat again to go back to public chat.
2. Type /is chat hello. Only members see it, colored by role.
3. With a coop player and a visitor: they must not see island chat.
4. As leader open Chat colors from /is and change a role color. Members see the new color. Members cannot change it.
5. Try a bad word or a link in island chat: blocked. A muted player cannot use it.
6. As staff turn on 'Island chat spy' in /settings and check you see island chat. Check /mutechat does not stop island chat.

_Questions for this feature: 9 on the Decisions page (Feature = "Island chat")._

### Disband, remove and staff island tools

The leader can disband the island only when nobody else is a member (transfer and leave first). **Changed (GUI-1, ISL-R3):** disband is a **two-step confirm** and both screens show exactly what you lose (island bank money, money on this profile, items). The island is wiped, its space is reused and a new island can be made at once. A disband or a staff reset also resets every member profile of that island (money, and inventory + ender chest of members online on that profile) and deletes the island bank money; staff keep a backup of everything and can restore it with /isadmin restore. Staff have /isadmin tools to look up, teleport, delete, reset, resize, hide, add members and change the owner.

| Command | Who | What |
|---|---|---|
| `/is disband` | player | Leader only, only without other members: two confirm screens with the real amounts. Removes the island, resets this profile (backup kept) and deletes the island bank money. Its spawners are cleared too (staff can restore frozen spawners in /inspect). |
| `/is bypass` | admin | Staff: ignore island protection for this session (logged). |
| `/isadmin info <island>` | helper | Look up an island: owner, members, size, slot, created. |
| `/isadmin tp <island>` | helper | Teleport to an island. |
| `/isadmin delete <island>` | admin | Remove an island for good. |
| `/isadmin reset <island>` | admin | Remove an island and give its leader a fresh one; every member profile is reset and the bank money deleted, with restorable backups. |
| `/isadmin restore <player>` | admin | Restore a kick, disband or reset backup (money topped up, island bank money into the purse, items into an empty inventory). |
| `/isadmin setsize <island> <size>` | admin | Set the border width in chunks. |
| `/isadmin kick <player>` | admin | Remove a member or coop player from an island. |
| `/isadmin slots` | admin | Grid overview of all island slots. |
| `/isadmin teleport-slot <slot>` | admin | Teleport to a slot's center. |

**Test:**

1. On a team island, as leader type /is disband: refused while there are other members.
2. On a solo test island put some money in the island bank and keep money and items on you. Type /is disband: the first screen lists the island bank money, your money and items you lose; confirm, then the second "last check" screen (buttons swapped); confirm again (no typing). The island is wiped, your money, inventory and ender chest on this profile are reset, and the island bank money is gone. You can create a new island at once (no cooldown).
3. As admin type /isadmin restore <you>: the disband backup is listed. Restore it: money topped up, the old island bank money paid into your purse, items back into your (empty) inventory.
4. As staff use /isadmin info on your friend's island. Teleport with /isadmin tp.
5. As admin run /isadmin setsize <island> 5. The border changes. Normal upgrades will stop at 9x9.
6. Run /isadmin reset on a test team island. The leader gets a new starter island, other members are removed, and every member profile is reset with a backup.
7. Run /isadmin slots to see the grid. Check cleared slots become free again and are reused.
8. Check /logs island for all of these actions.

_Questions for this feature: 10 on the Decisions page (Feature = "Disband, remove and staff island tools")._

## Island progression

Changed: the old section "Island level, pillars and upgrades (planned, not built yet)" is replaced by the sections below. Everything here is built (Phase 5: M5.1-M5.5, M5.7). Paid island sizes above 9x9 are sold in the [Black Market](#black-market-blackmarket).

### Island pillars — new

Four pillars grow on every island: **Mining, Foraging, Farming, Combat** (Conquest comes with conquest). They grow from the **base skill XP** of island members (before boosts) plus island work: ripe crops (Farming), natural logs (Foraging) and spawner mobs you kill yourself (Combat). Coop players, visitors, boosts, stack kills and AFK kills never count. Each level needs 12% more XP (250 x 1.12^level). Keeping all pillars within 10 levels of the highest gives a **balance bonus** up to +10%. When all pillars reach 10, 25, 40 and 60, every member gets crystals once (**synergy milestones**). Each pillar level is 1 island level point.

| Command | Who | What |
|---|---|---|
| `/is level` → pillar row | player | Pillar levels, XP and the balance bonus. |
| `/settings` → pillar notices | player | Hide the pillar level-up notices. |

**Test:**

1. Mine in the Depths and chop logs as a member: open `/is level`, the Mining and Foraging pillars gain XP.
2. Kill spawner mobs on your island with your own hits: Combat grows. Let them die in lava: nothing.
3. A coop player and a visitor do the same: no pillar XP.
4. Ask staff to raise your skills: when every pillar passes 10, each member gets the milestone crystals once (chat, title, sound).
5. Turn pillar notices off in /settings: no more level-up lines.
6. Bedrock: read the pillar row on a phone.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M5-1._

### Island level and the Challenge Book — new

The island level comes from **8 sources** with caps: pillars (1 point per level), upgrades and masteries, island achievements (max 10%), collections (max 5%), members' skills (max 8%), Depths progress (max 6%), island bank (lowest balance of the last 24 h, log scale, max 5%) and block worth (square root, max 20%). Level = points ÷ 10. Members count for skills and Depths after 3 days; coop players never count; money alone can't buy the level. Block worth updates live and with a nightly rescan; the **Recalculate** button (members, once per 10 min) rescans now. A new season-high level gives members a title and sound; every 10th level is announced in chat. The **Challenge Book** (button in `/is level`) has **180 island tasks in 12 tiers** (mine, chop, farm, kill, craft, sell, earn, deposit, deliver, skill/pillar/island level). Progress is shared by all members from the island's start; each task pays crystals and pillar XP once and unlocks upgrade and mastery levels. Deliver tasks take items from your inventory.

| Command | Who | What |
|---|---|---|
| `/is level` | player | Level, progress, board place, points per source with its cap, Recalculate, pillar row, Challenge Book. Also from the island item in the island menu. |

**Test:**

1. Open `/is level`: every source shows its points and its cap ("Cap: 86").
2. Place valuable blocks, wait a few seconds, press Recalculate: block worth goes up. Press it again at once: refused (10 min).
3. Put $1M in the island bank: the bank source stays 0 until the money was there 24 h.
4. A brand-new member's skills count after 3 days, not before.
5. Open the Challenge Book: chapters per tier, shared progress. Mine some coal: the mine task moves for every member.
6. Hand in a deliver task from your inventory. Finish a task: every member gets the crystals once.
7. The scoreboard island line shows the level.
8. Bedrock: open the book, tap a chapter, use the hand-in screen.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M5-2, M5-3._

### Island upgrades (/is upgrade) — new

Upgrades are bought with **island bank money** after their Challenge Book tasks are done. Tabs: Island, Economy, Depths, Production, Masteries. Live now: **Island Size** (5x5, 7x7, 9x9), **Member Slots** (up to 8 + leader), **Warp Slots**, **Entity Limits**, **Bank Capacity** and **Bank Interest** (real amounts shown), **Sell Multiplier** (+5% per level, inside the +300% cap), **Spawner Stack Limit** (16 to 512) and **Spawner Rate** (+12% per level). **Generator** and **Fuel Tank** are live only while their production switch is on. The rest (Island Vault, Mob Loot, Crop Growth, Harvest Yield, Mine Fortune, Lumberjack, XP Boost) show "Coming soon". Only the leader (and Admins by default) may buy: island permission **Upgrades**. Payment and level are saved together.

| Command | Who | What |
|---|---|---|
| `/is upgrade` | player | Upgrades menu; click an upgrade for its levels, tasks, hand-ins and Buy (confirm). Also the Upgrades button in the island menu. |

**Test:**

1. Open `/is upgrade`: five tabs; each upgrade shows level, now/next effect, tasks and price.
2. Finish the tasks for Island Size level 1 (or ask staff), put the money in the island bank and buy: the border grows at once and every online member gets a line.
3. Try to buy with too little bank money: refused. Click Buy twice fast: only one level.
4. As a Member without the Upgrades permission: you can look but not buy.
5. Buy Member Slots and invite one more member. Buy Bank Capacity and check `/bank`.
6. Check Generator and Fuel Tank say "Coming soon" while production is off, and become buyable when it is on.
7. Bedrock: buy an upgrade by tapping; the confirm shows the price on the middle item.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M5-4._

### Island masteries (/is masteries) — new

Masteries are the Masteries tab of `/is upgrade`, paid from the island bank and gated by a pillar or island level plus tasks. Shown now: **Warrior** (+4% PvE damage per rank), **Deep Pockets** (+10% satchel per rank), **Crystal Rush** (+3% crystals), **Lucky Strike** (+4% rare finds), **Homestead** (+1 shared island home per rank), **Wings** (island fly for everyone on the island), **Treasury** (bank fee 2% → 1.5% → 1.0% → 0.5%), **Market Contacts** (+2% sell per level, 20 levels, after Sell Multiplier is maxed) and endless **Ascension** once everything is maxed. **Fuel Efficiency** shows only while Island Fuel is on. Hidden until their feature exists: Timber, Swift Hands, Beast Tamer, Angler.

| Command | Who | What |
|---|---|---|
| `/is masteries` | player | Opens `/is upgrade` on the Masteries tab. |

**Test:**

1. Open `/is masteries`: each mastery shows its gate (pillar or island level), tasks and price.
2. Buy Treasury rank 1 (staff can help with levels and money): `/bank` withdraw now shows a 1.5% fee.
3. Buy Homestead: test named homes (see Island creation).
4. Buy Wings: everyone on the island can fly inside the border; a visitor only with the visitor-fly setting.
5. Buy Warrior: hit a mob and compare damage; hit a player in the warzone: no bonus.
6. Bedrock: buy a rank; the confirm shows the details.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M5-5._

### Island leaderboards (/is top) and the Top screen — new

`/is top` has tabs **Level**, **Weekly gain** (level points or pillar levels gained since Monday 00:00 Oslo), **Value** (block worth) and **Pillars** (pick one), each with a Solo/Team pick list, places 1-28 and your own place. Staff islands are hidden. The final weekly top 10 is saved every week (weekly rewards are not paid yet). In the Hall of Heroes the island level boards and weekly boards switch Solo/Team every 10 s. **New (GUI-4c): the Top screen** behind the hub's Top button: tabs Solo islands, Team islands, This week, Richest players and Top skills, with a podium for #1-#3 and #4-#10 below; click an island to visit it; `/is top` is one click away.

| Command | Who | What |
|---|---|---|
| `/is top` | player | Island leaderboards. |
| `/menu` → Top | player | The Top screen. |

**Test:**

1. Open `/is top`: switch tabs and Solo/Team; your island shows its place.
2. Gain island level points: Weekly gain moves (boards refresh every 5 minutes).
3. As staff (helper+): your islands are not on the boards.
4. `/menu` → Top: five tabs, podium, click an island to visit.
5. Hall of Heroes: island boards switch Solo/Team every 10 s.
6. Bedrock: tabs and the podium read well; key facts are in the item names.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M5-7, GUI-4c._

## Island production

Phase 7 (plan: docs/phases/PHASE-7-PRODUCTION.md). Everything here is **OFF by default** and changes nothing while off. **Farming (M7.3)** is being built now and is not in this build; it comes behind the same switch.

### Island Fuel (OFF by default)

**Turn on:** `production.yml → fuel.enabled: true`, then `/skyblock reload production` (or restart). Turn off the same way.

Islands run on fuel. An island is **Active** while a member plays on it (break, place, hit, harvest, use a menu, chat, or walk 5 blocks in the last 15 minutes) and earns **+1 minute of fuel per minute** (per island, not per member). With nobody active it is **Fueled** and burns the tank. With an empty tank it is **Asleep**: spawners stop and crops don't grow. A member's action wakes it at once. Coop players, visitors, the Depths and spawn never earn fuel. **Tank:** 12 h + 2 h per **Fuel Tank** upgrade level. **Fuel Efficiency** (mastery + island achievements) lowers the burn, max +40%. **Fuel Cells** (1 h / 3 h / 6 h, bound) come from the daily calendar (days 4, 12, 23) and crates; use one on your own island, only if it fits completely. The **AFK pool**'s banked fuel goes into the tank as soon as fuel is on. While off: spawners and crops work as before, the calendar and crates pay their crystal fallback, Fuel Tank says "Coming soon" and Fuel Efficiency is hidden.

| Command | Who | What |
|---|---|---|
| `/is` → fuel gauge (slot 28) | player | State, fuel / tank, burn rate, how to get fuel. There is no `/is production` command yet (needs your OK). |
| `USE a Fuel Cell` | player | Add its fuel to your island (stand on your own island). |
| placeholder `island_fuel` | admin | "Active", "7h 20m" or "Asleep" for a scoreboard line (not on the default scoreboard). |

**Test:**

1. Switch it on. Open `/is`: the gauge shows Active while you play.
2. Leave your island (go to spawn) and stand still: after 15 minutes it is Fueled and the tank goes down.
3. Ask staff for a short tank (or wait it out): the island falls asleep, online members get one line, spawners stop even when you stand next to them, crops don't grow. Rejoin: "ran out of fuel 2h ago".
4. Break a block on the island: Active again at once.
5. A coop player and a visitor play on the island: it does not earn fuel.
6. Claim a fuel day in `/daily` (or a crate fuel reward): you get a Fuel Cell. Use it on your island; use a 6 h cell on an almost full tank: "room for 2h 10m", the cell is kept. Try to trade the cell: refused.
7. Earn fuel in the AFK pool with fuel off, then switch it on: the banked fuel arrives in the tank.
8. Buy Fuel Tank in `/is upgrade` (Production tab) and check the tank grows by 2 h.
9. Switch it off: spawners and crops work as before.
10. Bedrock: the gauge and the cell use (long-press) work.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M7-1, PLAN7._

### Custom island generators (OFF by default)

**Turn on:** `generators.yml → enabled: true`, then `/skyblock reload production`. Staff test shortcut: `generators.yml → test.level-override` (-1 = off) gives every island that Generator level.

A lava + water generator on an island that forms cobblestone or stone now forms a block rolled from the island's **Generator level** (0-10, the Generator upgrade in `/is upgrade` → Production, live while generators are on). Level 1 = 90% cobble + 10% coal; level 10 = 40% cobble, 6% of each vanilla ore, 7% **Ashstone** and 5% **Emberite**. Ashstone ($180) and Emberite ($396) are custom ores that always drop exactly one custom item (no Fortune), sell with `/sell` and can't be placed on islands. Generator-made blocks give **Mining pillar XP** at 25% of the matching Depths ore (members on their own island only) and Mining books at 25%; no Mining skill XP. A lag guard allows 40 rolls per second per island. With Island Fuel on, an **Asleep** island forms plain cobblestone. Basalt and obsidian stay vanilla.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Build a cobble generator on your island. |
| `/is upgrade` → Production → Generator | player | Raise the Generator level (island bank money + tasks). |

**Test:**

1. Switch it on. Build a cobble generator; with Generator level 0 it forms plain cobble.
2. Set `test.level-override: 10` and reload: ores, Ashstone and Emberite appear. Mine them; `/sell` the Ashstone and Emberite.
3. Mine Emberite with Fortune and Silk Touch: always exactly one Emberite item. Try to place Ashstone or Emberite on your island: refused.
4. Check `/is level`: the Mining pillar grows from generator ores. A coop player mining the same blocks: no pillar XP.
5. With Island Fuel on and the island Asleep: plain cobble.
6. Reset the override to -1, buy Generator level 1 in `/is upgrade` and check about 1 in 10 blocks is coal.
7. Switch it off: plain cobble again.
8. Bedrock: the custom ore blocks (gilded blackstone, nether gold ore) look right and drop the right item.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M7-2._

## Social & chat

### Social: private messages, ignore, AFK and info commands

Players can send private messages, reply, ignore someone, go AFK, see who is online by rank and look up playtime, ping and last seen. Ignoring a player blocks their private messages, /tpa and /tpahere, trade requests and island invites (they get the same answer as "requests off", so they never learn it), and the /settings option "Hide ignored chat" (on by default) hides their public chat for you. Staff can't be ignored. There are clickable link commands (/discord, /store, /vote, /website) and /rules with rules 1-9 (7: English only in public chat, 8: no alt abuse, 9: no macros or AFK farming outside the AFK pool) and a "logged for 30 days" line. Mods and up can read private messages (social spy), staff look up a player with /whois and admins send server announcements. **New (STAFF-R3):** private messages block links, IP addresses and phone numbers; players still in the tutorial only get private messages from staff or from people they messaged first. A message to an AFK player tells you how long they have been AFK (QOL3). `/vote` is now a menu (see Voting). On Bedrock the links open a popup.

| Command | Who | What |
|---|---|---|
| `/msg <player> <message> (/w /tell /m /pm)` | player | Send a private message (clickable names, rate limited, logged 30 days). |
| `/r <message> (/reply)` | player | Reply to the last private message. |
| `/ignore <player> / /ignore list / /unignore <player>` | player | Ignore (or stop ignoring) a player: no messages, tpa, trade requests or island invites from them; their chat hidden if "Hide ignored chat" is on. Staff cannot be ignored. |
| `/afk` | player | Toggle AFK. It also turns on by itself after 5 minutes without activity. |
| `/list (/online /who)` | player | Online players grouped by rank. |
| `/ping [player]` | player | Connection delay. |
| `/playtime [player] (/pt)` | player | Total play time. |
| `/seen <player>` | player | When a player was last online and first joined. |
| `/discord /store /website /rules` | player | Clickable links (Bedrock: popup) and the server rules. `/vote` moved to the rewards module and opens the vote menu. |
| `/socialspy` | mod | See everyone's private messages (mods and up, not helpers). |
| `/whois <player>` | helper | UUID, rank, platform, times, location, names. IP and alt accounts for mods. |
| `/broadcast <message> (/bc /announce)` | admin | Server announcement with title and sound. |

**Test:**

1. With two accounts: A types /msg B hello. B sees a clickable name and answers with /r hi.
2. Send 6 messages in a row quickly. The rate limit should slow you down.
3. B types /ignore A. A tries /msg B, /tpa B, /trade B and (as island leader) /is invite B: all refused with the normal "turned off" answer. A's public chat is hidden for B. B turns off "Hide ignored chat" in /settings: A's chat shows again. Check that a staff member can still message B and can't be ignored.
4. Stand still 5 minutes (or use /afk). The tab list shows [AFK] and a chat line says you are AFK. Move to come back.
5. Type /list, /ping, /playtime, /seen <name>. Check a vanished staff member does not appear in /list.
6. Type /rules: rules 1-9 and the privacy line. Type /discord, /store, /website and click the links. Check the web addresses are the right ones.
7. As a helper try /socialspy: refused. As mod turn it on and send a private message with the other accounts. You should see it.
8. As admin: /broadcast <gradient>Hello</gradient>. Everyone sees a chat line, title and sound.
9. /msg a friend a link, an IP address and a phone number: all blocked.
10. A brand-new account still in the tutorial: a normal player's /msg to it is refused; staff can message it; once it messaged you first, you can answer.
11. Bedrock: click a name in chat to reply, and use /rules links (popup). Check clickable text works on a phone.

_Questions for this feature: 18 on the Decisions page (Feature = "Social: private messages, ignore, AFK and info commands")._

### Chat: format, filters, mentions and staff chat

Public chat shows title, rank, name, tag and message. The chat filter blocks spam, repeats, too many capital letters, links, IP addresses and phone numbers, and uses an English-only word list: mild swearing becomes ***, slurs and threats are blocked and staff are alerted. The same filter checks signs, book text, anvil names, island warp names, island notes and nicknames. Public chat is English only (rule 7, enforced by staff, no automatic detection); private messages may be any language. The name hover card shows only rank, island and playtime. Players can show their held item with [item] and are highlighted when someone mentions them. Staff have staff chat and chat controls (clear, lock, slow mode).

| Command | Who | What |
|---|---|---|
| `/chat clear (/clearchat /cc)` | helper | Clear the chat for everyone (staff keep theirs). |
| `/chat lock (/mutechat /lockchat)` | helper | Lock or unlock the chat. |
| `/chat slow <seconds>` | helper | Slow mode (0 = off). |
| `/sc [message] (/staffchat)` | helper | Staff chat. Without text it switches staff-chat mode. |

**Test:**

1. Send a message. Check the format: title, rank, name, (tag), arrow, text. Hover your name to see the card (rank, island, playtime, joined date).
2. Send 5 messages in 5 seconds: you are slowed. Repeat the same message: blocked.
3. Type a message in ALL CAPS (6+ letters): blocked. Post a link to an unknown website, an IP address and a phone number: all blocked. A link to skybornmc.net or youtube.com is allowed.
4. Hold an item and type [item]. Everyone sees it with a hover tooltip. Try again within 10 seconds: cooldown.
5. Another player types your name or @name: your name lights up and you hear a sound (can be turned off in /settings).
6. As a staff member try &c colors in chat. As a normal player they are not used.
7. As staff run /chat slow 10, /mutechat, /clearchat. A normal player is blocked; staff are not.
8. With a brand-new account: you cannot chat for the first 30 seconds on the first day.
9. Type a mild swear word: it shows as ***. Type a slur from the block list (on a test account): the message is blocked and staff get an alert.
10. Write a swear word on a sign, in a book and as an anvil name: the edit is undone. Try it as a nickname with /nick: refused.
11. Bedrock: send chat and check hover and clicks. Check that [item] works from a phone, and the sign filter on Bedrock.

_Questions for this feature: 16 on the Decisions page (Feature = "Chat: format, filters, mentions and staff chat")._

## Teleports & essentials

### Teleports: /spawn and /tpa

Every player teleport is **instant**: /spawn, /tpa, /home, /is visit, /is warp, /dungeon and portals. Only while you are in combat (you hit or were hit in the last seconds) or inside a combat zone (the warzone, the Depths boss arena and combat tiers) you stand still for 3 seconds first; they are never blocked. Requests expire after 60 seconds. The warzone can only be entered through its portal. All warmups and cooldowns are set in one file (teleport.yml).

| Command | Who | What |
|---|---|---|
| `/spawn` | player | Teleport to spawn (instant, 5 second cooldown; 3 s in combat). |
| `/tpa <player>` | player | Ask to teleport to a player. |
| `/tpahere <player>` | player | Ask a player to teleport to you. |
| `/tpaccept [player] (/tpyes)` | player | Accept a request. |
| `/tpdeny [player] (/tpno)` | player | Decline a request. |
| `/tpcancel` | player | Cancel your own request. |

**Test:**

1. Type /spawn from your island. You land at spawn at once. Type it again right away: 5 second cooldown.
2. With two accounts: A types /tpa B. B sees clickable [Accept] [Deny] (Bedrock: a popup with the same buttons). B clicks Accept. A arrives at once.
3. A hits a mob, then B accepts a new request: A waits 3 seconds. Move more than 1 block during the 3 seconds: cancelled. Do it again but take damage: cancelled.
4. Send /tpa and wait 60 seconds: it expires. Send many requests quickly: the 15 second request cooldown stops you.
5. Turn off 'TPA requests' in B's /settings. A's request is refused. B /ignores A: same answer.
6. Lock B's island or ban A from it, then /tpa B while B is on it: refused.
7. Stand in a Depths combat tier and type /spawn: you wait 3 seconds, never refused. /tpa to a player in the warzone: refused (warzone only through its portal).
8. Bedrock: press Accept in the popup; it waits behind an open menu and says Expired after 60 s.

_Questions for this feature: 9 on the Decisions page (Feature = "Teleports: /spawn and /tpa")._

### Staff essentials (teleport, game mode, fly, vanish, inventory, items)

Staff get tools to move around and help: teleport to players or coordinates, set game mode and speed, fly, heal and feed, vanish, look in (and with admin, edit) inventories and ender chests, give vanilla items, set time and weather, and keep up to 5 staff homes (/shome; /home is the island home). Staff play normally on the same account with their powers off: creative mode only for builders and admins and only in spawn and the Depths (mods use spectator); god mode, /fly and /heal only while in staff mode or /build mode. Items from /give, /i, /more, /skull and the creative inventory are hidden **staff items**: they can't be sold, traded or auctioned. Pay or trades of $5M or more to or from a staff account alert the admins. Every staff action is written to the log. **Changed (FIXES1):** `/fly` only toggles your own flight and says the real result once. **New (STAFF-R3):** the owner and admins in staff mode (`/s`) get island bypass and creative on islands; their block changes are audited in batches and creative items are staff-tagged.

| Command | Who | What |
|---|---|---|
| `/tp <player> [target]` | helper | Teleport to a player (mods can teleport others). |
| `/tphere <player>` | mod | Bring a player to you. |
| `/tppos <x> <y> <z> [world]` | builder | Teleport to coordinates. |
| `/top` | builder | Go to the highest block above you. |
| `/back` | mod | Return to the previous location or death point. |
| `/shome [name] /setshome [name] /delshome <name>` | mod | Staff homes (max 5). |
| `/gm <c\|s\|sp\|a> [player] (/gmc /gms /gmsp /gma)` | mod | Game mode. Creative: builders and admins only, only in spawn and the Depths. Mods: survival, spectator, adventure. |
| `/fly [player] /speed <1-10> [fly\|walk]` | builder | Fly (only in staff mode or /build mode, target too) and speed. |
| `/god [player] /heal [player] /feed [player]` | mod | No damage and hunger; heal (both only in staff or build mode); feed. |
| `/clear [player] (/ci)` | mod | Clear an inventory (confirm for others, contents logged). |
| `/invsee <player> /ecsee <player>` | helper | View inventory or ender chest. Admins can edit (every change logged). |
| `/vanish [player] (/v)` | helper | Become invisible, silent join/quit, stays after rejoin. |
| `/i <item> [amount] /give <player> <item> [amount]` | builder | Give vanilla items (max 2,304); they are always staff items (unsellable). |
| `/time <day\|noon\|night\|midnight\|ticks> [world] /weather <clear\|rain\|thunder> [world]` | builder | Set time and weather. |
| `/skull <player> /more` | admin | Player head; fill the held stack. |
| `/build` | builder | Turn on build mode in protected worlds such as spawn. |
| `/setspawn` | admin | Set the spawn point where you stand. |

**Test:**

1. As helper: /vanish, then check /list and tab do not show you. Relog: you are still vanished. Check no join/quit message.
2. As helper: /tp to a player, /invsee and /ecsee (view only). Try to move an item: refused. As admin: edit and check every change appears in /logs.
3. As mod outside staff mode: /fly, /god and /heal are refused. Turn on /s (alias /staffmode): they work. Leave staff mode: god and fly switch off within a second. /gm c as mod: refused (mods use /gm sp).
4. As builder: /gm c at spawn works; go to your island: you are switched to survival. Take an item from the creative inventory and try /sell, /ah sell and /trade with it: all refused (staff item).
5. As mod: /clear <player>: you must repeat the command within 10 seconds to confirm.
6. As builder: /i diamond 64 and /give. Try to sell the diamonds: refused. Then check /logs staff shows what you gave.
7. As admin: /time noon and /weather clear in the spawn world. Check the result (islands use their own per-island weather and time).
8. As staff: /setshome base, /shome base, /delshome base. Try to set a 6th: refused. /home takes a staff member to their island like everyone else.
9. Pay $5,000,000 from a test account to a staff account: admins get a "Staff transfer" alert.
10. As builder: /build in spawn lets you break blocks; without it you cannot.
11. As admin in /s on a player's island: you can build in creative; check /logs shows the block changes and that a creative item can't be sold.

_Questions for this feature: 13 on the Decisions page (Feature = "Staff essentials (teleport, game mode, fly, vanish, inventory, items)")._

## Ranks, perks & cosmetics

### Titles and tags

A title is earned (achievements, events, first join) and shows before your rank. A tag is owned (crates, store) and shows after your name in small brackets. You wear one title and one tag at a time. Tags can be turned into tradeable items. Staff can edit the whole list in game.

| Command | Who | What |
|---|---|---|
| `/tags (/tag) and /titles (/title)` | player | One menu with two tabs: equip a title and a tag, see how to earn locked ones, preview your chat line. Achievements button. |
| `/tags give\|take <player> <tag> and /titles give\|take <player> <title>` | mod | Give or remove a tag or title (logged). |
| `/tags item <tag> [amount] [player]` | admin | Make unique, tradeable tag items (USE to redeem). |
| `/display (/displayformat)` | admin | Edit chat, tab and nametag formats with a live preview. |

**Test:**

1. Join with a new account. You should have the title 'Newcomer'. Open /tags and look at both tabs.
2. Equip a title and a tag. Check chat and tab show them. Take them off.
3. Open a locked title: it shows how to earn it. A secret title shows as ???.
4. As admin: /tags item phoenix 1 <player>. The player USEs the item, confirms, and the tag is theirs. Try to sell the item on /ah before redeeming.
5. As mod: /titles give <player> founder, then /titles take. Check it is in /logs.
6. Try to give the automatic title 'honorable' to someone: refused.
7. As admin click Edit in /tags, create a new tag, edit its colors and delete it.
8. As admin open /display and change the chat format with the live preview.
9. Bedrock: open /tags and tap through both tabs; check gradient names are readable.

_Questions for this feature: 12 on the Decisions page (Feature = "Titles and tags")._

### Achievements

There are 30 achievements in six groups (skills, island, combat, economy, gear, exploring) with tiers; four are secret. Progress is saved per profile. The last tier gives a title that every profile of your account can use. You get a screen title, chat line and sound when you unlock a tier.

| Command | Who | What |
|---|---|---|
| `/achievements` | player | **New (AC1-2):** the achievements menu with tabs Personal, Island and History. Also `/tags` → Achievements and /menu → You → Achievements. |

**New (AC1-2):** every tier gives **boost points** (1 / 2 / 3 / 5 / 8). Points of a category give a small capped personal boost: Skills → skill XP (max +10%), Gear → tool XP (+10%), Combat → mob damage (+8%, never PvP), Exploring → rare finds (+8%), Island → crystals (+6%), Economy → up to +2 auction slots. Never sell or money. **Island achievements** are shared by members and give island XP and island level points. The **History** tab keeps one book per finished season.

**Test:**

1. Type /achievements. Check the Personal tab with six categories, boost points and each boost now / cap.
2. Play for 10 minutes: 'First Steps' unlocks with a screen title, chat line and sound.
3. Sell something with /sell: 'First Sale' unlocks.
4. Check a secret achievement shows ??? with a grey icon.
5. Switch to profile 2: progress starts at zero. Switch back: it returns.
6. Finish the last tier of an achievement with a reward title (ask staff to set your skill level). The title appears in /titles and is usable on profile 2 as well.
7. Join for the first time after an update: you should see one toast with up to 3 lines and '+N more'.
8. Unlock a few tiers in Economy: at 12 points `/ah` gives you one more listing slot.
9. Open the Island tab: island points, the island boosts and the top 5 members. Open History (empty until a season ends).
10. Bedrock: check progress bars and text fit on a phone; the tabs switch by tapping.

_Questions for this feature: 11 on the Decisions page (Feature = "Achievements")._

### Rank perks: /craft, /ec, /hat, /nick, /trash and staff tools

Donor ranks will get small convenience commands: crafting table anywhere, ender chest anywhere, wear an item as a hat and a custom nickname (with ~ marker). /craft and /ec don't work in the warzone, the Depths boss arena or for 10 seconds after combat (for everyone, staff too). The trash menu is for admins and the owner only. Staff also have /repair and /realname, and the owner has /sudo. Right now only staff and ops have them until the donor ranks exist (rank perks are planned in docs/plans/RANKS_TITLES_ACHIEVEMENTS.md).

| Command | Who | What |
|---|---|---|
| `/craft (/workbench /wb)` | donor | Open a crafting table anywhere (not in the warzone, boss arena or 10 s after combat). |
| `/ec (/enderchest)` | donor | Open your ender chest anywhere (same limits as /craft). |
| `/hat` | donor | Wear the held item as a hat. |
| `/nick <nickname\|off>` | donor | Set or remove a nickname (3-16 characters, letters/numbers/_, shown with ~; checked by the chat filter). |
| `/trash (/disposal)` | admin | Admins and owner only. **Changed (GUI-BUGS):** put items in and press Destroy: it asks "Destroy N items?" first. Close (button or Esc) gives the items back. |
| `/setnick <player> <nickname\|off>` | mod | Set or remove another player's nickname. |
| `/realname <nickname> (/whonick)` | helper | Find the real account behind a nickname. |
| `/repair [hand\|all] [player] (/fix)` | mod | Repair vanilla item durability. |
| `/sudo <player> <command\|c:message>` | admin | Owner only: run a command or chat as a player (audited). |

**Test:**

1. Give yourself the perk with LuckPerms (for example lp user <you> permission set skyblock.perks.craft true) and test /craft, /ec, /hat. As a mod try /trash: refused (admins only).
2. Hit a mob and type /ec within 10 seconds: refused. Try /craft in the Depths boss arena: refused. In a normal Depths combat tier outside combat it works.
3. /nick Phoenix2: you get a ~ in front, the tab and chat show it, and /realname Phoenix2 shows your real name to staff.
4. Try a reserved nickname (admin, owner, staff), another player's name, and a nickname within 60 seconds of the last change. All refused.
5. Remove the nick permission: the nickname is reset when the rank that allowed it is lost.
6. Put a custom item (a tool or enchant book) in /trash and close it: the item comes back (nothing destroyed). Put it in again and press Destroy: "Destroy N items?"; confirm. The item is gone and logged.
7. As mod: /repair hand on a damaged vanilla pickaxe. Custom tools have no durability.
8. As owner: /sudo <player> c:hello. Check the audit log.
9. Bedrock: open /trash and /ec and check taps work.

_Questions for this feature: 12 on the Decisions page (Feature = "Rank perks: /craft, /ec, /hat, /nick, /trash and staff tools")._

### Ranks, the free Ember rank and staff ranks

Staff ranks exist now: Builder, Helper, Moderator, Admin, Owner, with colors and prefixes. A free rank called Ember can be claimed with /freerank after 30 minutes of total playtime and gives a colored name and a choice of one of three tags. Five paid donor ranks (Squire, Knight, Paladin, Warlord, Monarch) are planned with the store.

| Command | Who | What |
|---|---|---|
| `/freerank` | player | After 30 minutes of playtime claim the free Ember rank once and pick 1 of 3 tags. |
| `lp user <name> parent add <rank> (console / owner)` | admin | Staff ranks are handed out by the owner in LuckPerms; there is no in-game rank command. |

**Test:**

1. Use a new account. Type /freerank before 30 minutes: you get a message with the time left.
2. After 30 minutes (or ask staff to raise your playtime) type /freerank. You get the Ember rank and a menu with three tags (ember, spark, ash). Pick one: a confirm screen shows the tag on the middle item (new, GUI-BUGS).
3. Type /freerank again: 'already claimed'. Relog: rank and tag stay.
4. Check chat and tab show the [Ember] prefix in its color. Check you do not get a join message (only ranks with that permission do).
5. Ask the owner to give a test account the helper rank: check prefix, color, staff chat and tab sorting.
6. Check /profiles shows slot 2 locked until you have 500 gems or Knight (when donor ranks exist).

_Questions for this feature: 11 on the Decisions page (Feature = "Ranks, the free Ember rank and staff ranks")._

## Worlds & builds

### Worlds, spawn and builds

The server has five empty (void) worlds: spawn, islands, outposts, The Depths and the warzone. Spawn is a floating medieval castle with protection (no building, no damage, no hunger). Admins can paste the builders' finished builds into the right worlds and wire their spawn points and zones. Builders use build mode to work at spawn.

| Command | Who | What |
|---|---|---|
| `/spawn` | player | Go to spawn. |
| `/build` | builder | Turn build mode on or off in protected worlds such as spawn. |
| `/setspawn` | admin | Set the spawn point where you stand. |
| `/skyblock worlds` | admin | List worlds, players and load. |
| `/skyblock tp <world>` | admin | Teleport to a world's spawn. |
| `/skyblock builds list\|info <id>` | admin | Show every finished build and its state. |
| `/skyblock builds paste <id> / paste-all / apply <id>` | admin | Paste a build with FastAsyncWorldEdit (preview + confirm) and wire its metadata. Back up the world first. |

**Test:**

1. Type /spawn. Look around the castle. Try to break and place a block: refused. Check you take no damage and do not get hungry.
2. Jump off the edge: you are teleported back to spawn (below Y 0). Check there is no fall damage at spawn.
3. Open doors, buttons, levers, signs, an ender chest and a crafting table at spawn: they work. Other chests do not.
4. As builder: /build, then place and break blocks. /build again to turn it off.
5. As admin: /skyblock worlds and /skyblock tp islands, depths, warzone. Check each world looks right (time of day, border).
6. As admin: /skyblock builds list and /skyblock builds info spawn_base_v1. Only paste on the test server after a backup.
7. Check the border: walk or fly toward the edge of spawn (1,000 blocks wide) and The Depths (2,000).
8. Bedrock: walk through the castle and check doors/buttons work with taps.

_Questions for this feature: 13 on the Decisions page (Feature = "Worlds, spawn and builds")._

### NPCs, holograms and leaderboards

Every NPC pad, hologram and leaderboard spot from the builds has its NPC or text (spawn and the Depths). **Changed (UX3, GUI-BUGS): "Right-click" is now "Click".** On Paper 26 only one click event reached NPCs, so left-click alone worked; now **both mouse buttons and a Bedrock tap** work, and every text says "Click". A plain click **opens the feature** and shows the short help once per session; **sneak-click** shows the help text. NPCs and what they open: Banker → /bank, Blacksmith and Quartermaster → /forge, Enchanter, Shop, Sell, Auction, Depths guide → /dungeon, **Guide → /tutorial**, **Crate Keeper → /crates**, **Daily Rewards → /daily**, **Quest Master → /quests**, **Voting (Tavern) → /vote**, **Black Market → /blackmarket**. Help-only NPCs (Info, Mine Foreman) show help with an [Open] button. During the tutorial NPCs say a short line for your step. Holograms with a help text can be clicked. Leaderboards show the top 10 (#1 / #2 / #3 in their own colors) for the five skills, money, **island level** (solo/team) and the **weekly island boards** (switch Solo/Team every 10 s), refreshed every 5 minutes. The crate yard has a **key board** and the Tavern a **vote party** board. NPCs are player-shaped mannequins without skins.

| Command | Who | What |
|---|---|---|
| `Click / sneak-click an NPC` | player | Open its feature / read its help (Bedrock gets a small menu with the text and an Open button). |
| `/skyblock npcs [list]` | admin | Menu of every NPC, hologram and leaderboard; click one to teleport to it. Also in the /staff hub. |
| `/skyblock npcs tp <id>` | admin | Teleport in front of one. |
| `/skyblock npcs respawn` | admin | Re-read the build points and respawn everything (after applying a build). |

**Test:**

1. Walk through spawn: every pad has an NPC with a name and a 'Click' line (no text says 'Right-click' any more); heads turn to you when you come close.
2. Left-click, then right-click the Banker, Blacksmith, Enchanter, Shop and Auction NPCs: both open the menu. The first click also prints the help once per session.
3. Sneak-click the same NPCs: you get the help text with an [Open] button instead.
4. Click the Guide (opens /tutorial), Crate Keeper (/crates), Daily Rewards (/daily), Quest Master (/quests), Voting NPC in the Tavern (/vote) and the Black Market NPC (/blackmarket). Click a Mine Foreman in the Depths: help text with [Open].
5. Hit an NPC: it acts like a click and takes no damage. Try to push, burn or trade with it: nothing happens.
6. Look at the leaderboards in the Hall of Heroes: top 10 with colored #1-#3, 'Updates every 5 min', island level boards and weekly boards switching Solo/Team. Earn some skill XP and check it moves within 5 minutes.
7. Click a hologram that has help: the help text appears.
8. As admin: /skyblock npcs, click an entry to teleport; /skyblock npcs respawn and check nothing is doubled.
9. Bedrock: check the NPCs are visible (mannequins on Geyser), tap one, and read the Bedrock help menu.

_Questions for this feature: 6 on the Decisions page (Feature = "NPCs, holograms and leaderboards")._

### Portals and the island portal

Portals in the builds now work: step in and you travel. The gatehouse at spawn has the **island portal** (creates your island on first use, then always takes you home) and the Depths portal; The Gate in the Depths has the portal back to spawn. Travel is instant; while in combat (or inside the warzone) you wait 3 seconds inside the portal. Frozen players can't use portals. The warzone portal says it isn't built yet, and the outposts and events portals stay closed with bars until those features exist. The warzone can only be entered through its portal. **New:** the island portal starts the tutorial for new players and shows the Create / Join choice when you have invites; the Depths portal opens at tutorial step 5, the warzone, outposts and events portals after the tutorial.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Walk into a portal. |
| `/skyblock builds apply <id>` | admin | Re-applying a build also lists its portals and their targets. |

**Test:**

1. At spawn walk into the Depths portal in the gatehouse: you land on the Depths arrival pad with particles at the portal.
2. Walk into the portal at The Gate: you land in front of the gatehouse at spawn, facing the passage. You don't bounce straight back.
3. Walk into the island portal: your island (made on the first visit). See the island creation section for the first-visit test.
4. Hit a mob, then step into a portal: you wait 3 seconds; step out and it is cancelled.
5. Ask staff to freeze you and walk into a portal: refused and pushed back out.
6. Try the warzone, outposts and events portals: 'isn't built yet' / 'isn't open yet', pushed back, one message at a time.
7. Bedrock: walk through each portal and check the messages are readable.

_Questions for this feature: 2 on the Decisions page (Feature = "Portals and the island portal")._

### Region protection (spawn, the Depths, warzone)

Spawn, the Depths (outside the ore and log fields) and the warzone are protected: no building, no breaking, no containers, no workstations, no fire, no item frames or armor stands, and explosions don't break blocks. Doors, buttons, levers, pressure plates, fence gates, ender chests, crafting tables and signs still work; trapdoors and other decoration don't. The arrival and exit pads in the Depths are safe zones where mobs can't target or hurt you (the graveyard's arrival and exit path also stop you hurting mobs). Ender pearls and wind charges are blocked in combat zones and the boss arena. In the Forge Quarter at spawn the anvils, enchanting table and chests are decoration: talk to the NPCs instead. Builders bypass the protection only in creative mode. New builds only need a region kind.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Protection works by itself; a short action bar line says why something is refused. |
| `/build` | builder | Build mode at spawn (builders also need creative mode to bypass the region flags). |
| `/depths debug [player]` | admin | Shows the regions and refused flags where a player stands. |

**Test:**

1. At spawn and in the Depths (outside ore fields) try to break and place a block, open a chest, use an anvil, light a fire, rotate an item frame: all refused with a short action bar line.
2. Open a door, press a button, use an ender chest and a crafting table: allowed. Click a trapdoor: refused.
3. In the Forge Quarter click the anvil or enchanting table: 'This one is decoration. Talk to the NPCs instead.'
4. In the Depths stand on an arrival pad near mobs: they don't target or hurt you. In the graveyard's arrival area you can't hit mobs either.
5. In a combat zone throw an ender pearl and a wind charge: refused. Throw a pearl into a combat zone from outside: refused where it lands.
6. As builder in survival: still refused. Switch to creative (spawn or Depths): you can build.
7. As admin: /depths debug while standing in a safe zone; check it lists the region and flags.
8. Bedrock: try the same with taps (doors, trapdoor, anvil).

_Questions for this feature: 5 on the Decisions page (Feature = "Region protection (spawn, Depths, warzone)")._

## Staff & punishments

### Staff mode, freeze, inspect and player reports

Staff mode swaps a staff member's inventory for moderation tools (inspect, freeze, random player, logs, vanish, punish, reports). Freeze holds a player for a check: they cannot move, build, use commands or take damage, and their chat goes only to staff. Players can report others with /report, and staff claim, handle and close reports. **New since update92:** `/s` is staff mode and `/p` is punish (FIXES1); the staff report tool is a normal book that no longer opens a book screen. `/report` now **asks to confirm** (reason + target, QOL3), has the new reasons **Scamming, Threats, Griefing**, and attaches the reported player's **last 10 private messages** as evidence (mods and up can read them; kept 180 days, STAFF-R3). `/reports` has tabs in the top row and a Refresh button (GUI-4c). `/inspect` keeps Back, asks to confirm freeze/unfreeze and keeps its tool row in place (QOL3); it also shows the **Tutorial** book, the player's `/box` and a **restore button for frozen spawners** of disbanded or reset islands (REV3-FIX).

| Command | Who | What |
|---|---|---|
| `/s (/staffmode /sm /mod)` | helper | Turn staff mode on or off. |
| `/freeze <player> (/ss)  /unfreeze <player>` | helper | Freeze or unfreeze a player. |
| `/inspect <player> (/check)` | helper | Open a player card: rank, playtime, platform, ping, location, inventory, ender chest, punish, logs. |
| `/report <player> [reason]` | player | Report a player with a reason picker or typed text. |
| `/reports [open\|claimed\|closed]  /reports claim\|unclaim\|close <id> <outcome>` | helper | Report list, claim and close with an outcome. |

**Test:**

1. As helper: /s. Your inventory is swapped for the staff hotbar and you are vanished with night vision. Leave and check your items return exactly.
2. In staff mode right-click a player with the blaze rod (Inspect) and with the breeze rod (Punish). On Bedrock tap the player.
3. Freeze a test account: it cannot move, build, use commands, take damage or chat publicly; staff see its chat. Relog while frozen: still frozen. Unfreeze: 10 seconds without fall damage.
4. Ban the frozen account: the freeze is cleared.
5. As a normal player: /report <name> hacking. Pick a reason. Report again within 60 seconds: refused. Make 4 open reports: the 4th is refused.
6. As staff: /reports, claim, close with 'punished' or 'invalid'. The reporter is told the result (also later on join).
7. Close 3 reports from the same player as 'invalid': staff get a suggestion for the report-abuse reason.
8. Crash your client while in staff mode (kill the game) and join again: your inventory is restored.
9. As a player: /report <alt> → pick Scamming: a confirm screen shows reason and target. Have the alt send you 3 /msg first: as mod open the report and read the attached DMs. As helper: the DMs are not shown.
10. As staff: /reports, switch tabs (in place), press Refresh. /inspect <player> → Freeze asks to confirm; Back returns from every sub-screen.
11. Disband a test island that had spawners: as admin open /inspect <leader> and restore the frozen spawners.
12. Bedrock: /report reason picker and confirm by tapping; staff report alerts come as a popup (Teleport / Inspect / Claim).

_Questions for this feature: 11 on the Decisions page (Feature = "Staff mode, freeze, inspect and player reports")._

### Punishments (/p menu, ladders and warnings)

Staff open one menu with /p <player> and click a reason. Each reason has a ladder (for example warn, mute 1h, mute 1d). The system picks the next step from the player's history, keeps a warning counter, shows a 2-minute [Undo], protects against double punishing, saves chat evidence, and asks an admin to approve big punishments. Bans and mutes are announced publicly (rule number only, no staff name) unless the staff member uses -s or the **Silent** button in /p (off every time the menu opens). Players get a clear ban or mute screen with an ID, the rule, the time left, when an appeal is possible (permanent bans after 30 days, cheating after 90 days, temporary bans right away) and a link to appeal. **New (GUI-BUGS, GUI-4c):** /p has the standard frame, **every ban asks to confirm** with the details and a **permanent ban asks twice**; starting or ending a lockdown and confiscating an item also confirm.

| Command | Who | What |
|---|---|---|
| `/p <player> (/punish)` | helper | Open the single-screen punishment menu. |
| `/p <player> <reason-id> [-s] [-ip] [-force]` | helper | Apply a reason directly (-s silent, -ip IP ban, -force skip the double-click protection). |
| `/ban /tempban /ipban /mute /tempmute /warn /kick` | helper | Direct commands (helpers: warn, kick, mute; mods: ban; admins: IP ban). |
| `/unban /unmute /unwarn` | mod | Lift a punishment (reason required). |
| `/warns <player>` | helper | Warn counter and waiting suggestions with [Apply] / [Dismiss]. |
| `/history <player> (/punishments)` | helper | All punishments with who, why and evidence. |
| `/alts <player>` | mod | Accounts sharing an IP. |
| `/pstats [period]  /pstats <staff>  /pstats top` | helper | Staff punishment numbers. |
| `/mypunishments  /ack` | player | See your own mute and warnings; confirm a warning. |

**Test:**

1. As helper: /p <test account> and look at the layout: mutes top-left, warn buttons bottom-left, bans top-right, small stuff bottom-right, head and warn counter in the middle.
2. Click Toxicity: the player gets a warning, a title and chat line. Click [Undo] within 2 minutes. After 2 minutes the Undo is gone.
3. Warn the same player twice: staff get a suggestion 'mute 1h' with [Apply] / [Dismiss]. Apply it.
4. Have two staff punish the same player within 15 seconds: the second is blocked unless they click again.
5. As mod: /p <player> hacking. A 30-day ban is a request for an admin ([Approve]/[Deny]); a 7-day ban is applied.
6. As the punished test account: read the ban screen (ID, rule, time left, 'Appeal: from <date>' for a permanent ban, 'possible now' for a temporary one, appeal link), then /mypunishments and /ack. Try /appeal on a permanent ban before 30 days: refused.
7. Open /p, click Silent, then a mute: no public line. Reopen /p: Silent is off again. /p <player> <reason> -s does the same from chat.
8. As staff: /history <player> and click an entry to see the evidence lines. IPs must not show.
9. Try to punish an admin: refused (exempt).
10. Click a temporary ban: a confirm screen with the details. Click a permanent ban: two confirm screens.
11. Bedrock: open /p with a tap and check slots, icons and Undo on the head.

_Questions for this feature: 11 on the Decisions page (Feature = "Punishments (/p menu, ladders and warnings)")._

### Punishment safeguards: appeals, lockdown, watchlist, guard and reports

Players can appeal bans and mutes in game. Staff can start a chat or join lockdown during a raid (only new accounts are affected), keep a private watchlist, add private notes, punish many players at once, and check each other: a guard limits how many punishments one staff member can give in 5 minutes, and admins can roll back a staff member's recent punishments. A monthly report shows the numbers.

| Command | Who | What |
|---|---|---|
| `/appeal [id] <text>` | player | Appeal your mute or ban (20 to 500 characters). |
| `/appeals` | mod | Staff queue: read, Accept / Reduce / Deny. |
| `/lockdown [chat\|join\|off\|status]` | helper | Raid lockdown for new accounts only. |
| `/watch <player> [reason]  /watch remove <player>  /watchlist` | helper | Private watchlist; staff get a line when the player joins. |
| `/pnote <player> [text]  /pnote remove <id>` | helper | Private staff notes. |
| `/pbulk <reason> <players...>` | mod | Punish up to 20 players at once with a confirm. |
| `/psuggest <player> <reason> [details]` | helper | Suggest a punishment (console, anticheat, staff); never applied automatically. |
| `/pnotify [type] [on\|off]` | helper | Choose which staff alerts you receive. |
| `/pguard allow\|lock\|unlock\|status` | admin | Lift a block, lock or unlock a staff member's punishments. |
| `/prollback <staff> <period> [confirm]` | admin | Undo a staff member's recent punishments (preview first). |
| `/ppurge <player> [preview\|confirm]` | admin | Owner: delete a player's personal data on request. |
| `/preport [month]  /psanction restore <id>` | admin | Monthly punishment report; give back a sanction. |

**Test:**

1. As a muted test account: /appeal and read what can be appealed. Send an appeal text under 20 characters (refused), then a proper one. Staff see it in /appeals and Accept, Reduce or Deny with a reason.
2. Start /lockdown chat 5m. A brand-new account cannot chat or /msg; an older account, staff, the free rank and donor ranks can. /lockdown off ends it.
3. Start /lockdown join 2m: an account that never joined cannot join (friendly 'server is busy' screen), returning players can.
4. /watch <player> reason, then have that player join: staff see one line. The player is never told. /watchlist shows the list.
5. /pnote <player> text; /pnote <player> shows notes with [Remove].
6. /pbulk spam <player1> <player2>: a confirm screen with each player's step, then [Undo all].
7. As helper punish 6 players in 5 minutes: the guard blocks you after the limit. An admin runs /pguard allow <name>.
8. As admin: /prollback <staff> 1h shows a preview; confirm to undo.
9. Check /preport lists last month with no player names.

_Questions for this feature: 8 on the Decisions page (Feature = "Punishment safeguards: appeals, lockdown, watchlist, guard and reports")._

### Staff logs (/logs)

Everything players and staff do is written to a log: chat, commands, money, trades, auctions, island actions, item life, punishments, staff actions and logins. Staff search it with /logs by player, time, category, near me, island or item, and can click a chat line to punish. Admins can export to CSV. **Changed:** private messages have their own category **dm**: helpers no longer see them, mods and up do (REV3-FIX). A plain click on a result opens the **entry screen** with details, Teleport, Punish (chat lines), Show in chat and a Bedrock details popup; the page number is in the title (GUI-4c). The player, item and island searches open a **text popup on Bedrock** (QOL3). New categories from the new modules: tutorial, crates, boosters, rewards, quests, pass, collection, blackmarket, production.

| Command | Who | What |
|---|---|---|
| `/logs` | helper | Open the log viewer: pick who, category and time range. |
| `/logs player <name> [category] [time]` | helper | Everything a player did. |
| `/logs pay\|economy <name> [other]` | mod | Money between players. |
| `/logs trade\|chat\|commands <name> [time]` | helper | Trades, chat or commands of a player. |
| `/logs island <name\|id> [time]  /logs item <uuid>  /logs near [radius] [time]` | mod | Island actions, life of a unique item, actions near you. |
| `/logs staff <name> [time]` | admin | Everything a staff member did. |
| `/logs export <name\|last> [category] [time]` | admin | CSV file to the exports folder (logged). |
| `/logs page <n>` | helper | Next pages in chat (console, Bedrock). |

**Test:**

1. Do a few things: chat, /pay a friend, use a command. As helper /logs player <you> chat 1h: your lines show. Money lines need mod.
2. Click a chat line of a test account: the punish menu opens with that line as evidence.
3. /logs near 30 1h while standing where a friend placed and broke blocks. Check results and click to teleport (mods).
4. Look up a unique item: /logs item <uuid> shows its whole life (made, traded, sold).
5. As admin: /logs export <name> and check the CSV in plugins/PhoenixSkyblock/exports.
6. Check that a helper cannot see IPs, economy lines or private messages (dm), and a mod cannot see staff logs. A mod sees dm lines.
7. Click a result: the entry screen opens (no instant teleport); try Teleport and Show in chat.
8. Bedrock: open the viewer by tap, search a player in the popup, or use the chat output (/logs page).

_Questions for this feature: 9 on the Decisions page (Feature = "Staff logs (/logs)")._

## Economy

### Balances, /pay and /baltop

Every player has four kinds of money: money ($), island crystals, gems and shards. Players can check balances, pay each other (money and shards only), see the richest list and read their own history of every change. Money, crystals and shards belong to the active profile. Gems belong to the player. There is no new-account limit. **New (tutorial, ECON-R3):** `/pay` opens at tutorial step 11. A profile can only send money after it **earned $10k itself** (selling, rewards, interest, AFK pool; money from players, staff, notes and refunds never counts), except to members of its own island; the message says how much more to earn. **Crystals can never be moved** in any way. `/eco` can't target your own account or any account on your address; a change above $1T (crystals 1B, gems 1M, shards 1B) waits for an owner/admin **Allow** in the alert (10 min).

| Command | Who | What |
|---|---|---|
| `/bal [player]` | player | Show all four balances of you or another player (online or offline) |
| `/baltop [currency]` | player | Richest players list (top 10), refreshed every 30 seconds |
| `/pay <player> <amount> [currency]` | player | Send money or shards. Accepts 1.5k, 2M, 3B. Asks to confirm from $1M |
| `/transactions` | player | Menu with your own money history, filter by type and time |
| `/eco give\|take\|set\|reset <player> <currency> <amount> <reason>` | admin | Staff: change a balance. Always logged and shown to staff |

**Test:**

1. Type /bal. You should see money $1,000 and 0 crystals, gems, shards on a new account.
2. Type /bal <other player>. Check it shows their balances.
3. With a brand-new second account try /pay <friend> 100: "Unlocks at tutorial step 11". Finish the tutorial and try again: "earn $X more first". Pay an island member instead: allowed. Sell items until you earned $10k: now paying anyone works.
4. Type /pay <friend> 500. Check both sides get a message and /bal changes.
5. Type /pay <friend> 2M. A confirm screen should appear. Click confirm, then repeat and let it time out (30 s).
6. Try /pay <friend> 5 (below the $10 minimum), /pay yourself, and /pay with more than you own. Each should give a clear error.
7. Open /transactions and filter by Pay, then by 24h. Your payments should be listed.
8. Type /baltop and /baltop shards. Then as admin /eco give <alt> money 1000 test and wait 30 s for the list to move.
9. As admin try /eco give <you> money 1000 test and on an alt from your own address: both refused. /eco give <alt> money 2T test: an alert with Allow / Deny; another admin (or the owner) allows it.
10. Do the same on a Bedrock phone: type amounts like 1.5k and use the confirm screen by tapping; the /eco Allow alert comes as a popup.

_Questions for this feature: 15 on the Decisions page (Feature = "Balances, /pay and /baltop")._

### Money notes (/withdraw)

A player can turn money into a paper note item and give it to someone else, or cash it later by using it (USE). Each note is one entry in the database, so copies are caught and taken away. Notes are between $100 and $1T and not allowed in the auction house. **Changed (ECON-R3):** notes can also be made from **shards** (never crystals); at most **20 notes per minute** (the 10-second cooldown is gone); the $10k-earned rule applies (no island exception); cashed notes are purged after 90 days. `/withdraw` opens at tutorial step 11.

| Command | Who | What |
|---|---|---|
| `/withdraw <amount> [currency]` | player | Turn money or shards into a note item (needs a free slot; no playtime limit) |
| `USE a note (right-click / Bedrock long-press)` | player | Cash the note back into your purse |
| `/eco note <player> <amount> <reason>` | admin | Staff: give an online player a note without taking money |

**Test:**

1. Type /withdraw 5000. Check your money drops by $5,000 and a note item appears with the amount and date.
2. Click (USE) the note. Check the money comes back and the note disappears.
3. Try /withdraw 50 (under $100): refused. Make 21 notes of $100 within one minute: the 21st is refused. Try /withdraw 10 crystals: refused (crystals can't be moved).
4. Fill your inventory and try /withdraw. It must refuse before taking any money.
5. Give a note to a second account and let them cash it.
6. As admin, try to get a duplicate note (staff give command / creative copy). The copy should be taken and staff alerted.
7. Drop a note in lava or let it burn if you can. See what happens to the money.
8. Try to craft with a note, sell it with /sell, and list it in /ah. All should be refused.
9. Check /transactions shows 'Note' entries for both withdraw and cash.

_Questions for this feature: 12 on the Decisions page (Feature = "Money notes (/withdraw)")._

### Bank (personal and island)

Each profile has a personal bank with 10 tiers. Each island has its own bank that the team shares. Banks keep money safe and pay interest every 8 hours (0.6% personal, 1.0% island per payout). Taking money out costs a 2% fee; the **Treasury** mastery lowers it to 1.5 / 1.0 / 0.5% (built now, `/is masteries`). **Changed (ECON-R3):** the fee is the only withdraw rule: no free 10 minutes after a payout and no lost interest. **Changed (UX3):** tier prices rebalanced: personal tier 10 costs $10B (was $195.3B), island bank level 10 $50B (was $1T), interest stays under 8%; island bank capacity and interest are bought in `/is upgrade` with the real amounts shown. The island leader decides who may deposit, withdraw and see history. When an island is disbanded or reset, its bank money is deleted (staff keep a backup).

| Command | Who | What |
|---|---|---|
| `/bank` | player | Open both banks: balance, deposit, withdraw, tiers, interest, history |
| `Island menu > Bank` | player | Same screen from the island menu |

**Test:**

1. Type /bank. Check you see a personal bank (tier 1, $100k) and the island bank side by side.
2. Deposit $500. Check purse goes down and bank up. Try depositing more than the free room.
3. Withdraw $100. The Withdraw button shows 'Fee: 2%' and the confirm screen shows amount, fee and what you get.
4. Hover the Interest clock: '| Interest every 8h', rate, cap and a live countdown to the next payout.
5. Buy tier 2 ($10k). Check the capacity and the payout cap change.
6. Wait for the next payout time (every 8 hours from midnight UTC: 01:00, 09:00, 17:00 Oslo winter time) or ask staff to shorten it. Check the interest line in history and that money arrived.
7. Withdraw within 10 minutes after a payout: the 2% fee still applies (changed). With Treasury rank 1 on your island the fee is 1.5%.
8. Hover the tier buttons: tier 10 costs $10B. In `/is upgrade` the Bank Capacity levels show real amounts.
9. As leader, open /is permissions and remove Bank deposit from Member. Test with an alt member.
10. Try to deposit into another own profile's bank. It must be refused.
11. Check the island bank daily limits: Member can only take 10% of capacity per day.
12. Test on Bedrock: the number picker and confirm screens by tapping.

_Questions for this feature: 16 on the Decisions page (Feature = "Bank (personal and island)")._

### Interest (bank payouts)

Every 8 hours the server pays interest on the lowest balance each bank had during that period. Interest is paid even while players are offline, but only if the profile played at least 2 hours in the last 7 days (**changed:** minutes in the AFK pool don't count for this). Each tier caps how much one payout can be. The interest is new money, so it is also watched on the dashboard.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Interest is automatic. See rate, cap and next payout in /bank |

**Test:**

1. Put money in the personal bank and check /bank shows the rate, the cap and the time of the next payout.
2. Deposit just before a payout. Check you only get interest on the amount that was there during the whole period.
3. Check an account that has played less than 2 hours in 7 days earns nothing (staff can lower playtime in the database or use an alt).
4. Stay offline over a payout and log in. The interest should be waiting in the bank history.
5. Hit the payout cap: put more than the cap would pay on and check the payout stops at the cap.
6. Withdraw with a fee: that period's interest is still paid (changed: no forfeit).
7. An account that only stood in the AFK pool for 2 hours this week earns no interest.
8. Open /eco admin as staff and check the interest line on the dashboard.

_Questions for this feature: 12 on the Decisions page (Feature = "Interest (bank payouts)")._

### Sell chest (/sell)

A chest menu where players drop items and press Sell. It shows the live total, the player's sell bonus and what they will get. There is also a button to sell everything sellable in the inventory. Selling is also reachable from the satchel and the shop. /sell hand and /sell all are quick versions. Items not on the price list cannot be sold.

| Command | Who | What |
|---|---|---|
| `/sell` | player | Open the sell chest |
| `/sell hand` | player | Sell the stack in your hand (2 s cooldown) |
| `/sell all` | player | Sell every sellable stack in your inventory; **changed (QUICK1):** the hotbar is skipped (setting in /settings → Gameplay) (2 s cooldown) |
| `Sell inventory (button)` | player | Moves all sellable stacks into the chest and sells (skips the hotbar while "sell-all skips hotbar" is on) |

**Test:**

1. Type /sell. Put cobblestone in the chest. The total should update every second.
2. Click the green Sell button. Check money and the sale line in chat or action bar.
3. Close the chest with items in it. They must all come back to the inventory; with a full inventory the rest goes to /box (never dropped).
4. Press Sell inventory with a pickaxe and a sword in the hotbar. They must stay.
5. Try to sell a tool, an enchant book, a money note and a wardrobe piece. All must be refused.
6. Sell a big stack worth over $100,000. A chat line with a hover breakdown should appear.
7. Use /sell hand with 64 wheat, then /sell all with mixed items.
8. Open /sell and run /profiles switch. Items must come back, never lost.
9. Try shift-click, drag, number keys and double-click in the chest on Java. Check nothing duplicates.
10. On Bedrock, tap items into the chest and press Sell.

_Questions for this feature: 11 on the Decisions page (Feature = "Sell chest (/sell)")._

### Dynamic sell prices, Hot Item and the +300% cap

Sell prices move between 40% and 150% of the base price. Selling an item lowers its price a little, and the price slowly recovers (2% of the gap every 10 minutes). Items nobody sells drift up. One Hot Item per day pays 25% more. All sell bonuses (rank, upgrades, sets, boosters, enchants) are added and capped at +300%.

| Command | Who | What |
|---|---|---|
| `/sell (menu)` | player | Shows today's price, trend (Rising/Falling/Steady) and Hot Item |
| `/eco admin` | mod | Staff dashboard: sell price index and sell volume |

**Test:**

1. Open /sell and note the price of one item and the Hot Item.
2. Sell 1,000 of one item. Reopen the chest and check the price fell slightly and shows Falling.
3. Wait 10 minutes and check the price moved back up a little.
4. Sell the Hot Item and check you get +25% compared to the same item yesterday.
5. Check the sell bonus breakdown shows each source and that the total never goes over +300%.
6. Restart the server (ask staff) and check prices are kept.
7. Compare the price shown for two different players with different bonuses.

_Questions for this feature: 10 on the Decisions page (Feature = "Dynamic sell prices, Hot Item and the +300% cap")._

### Shop v2 (/shop) — changed

**Changed (GUI-3, replaces the old quick-buy shop):** the builder shop has **9 tabs on every page** (Essentials, Farming, Wood, Stone & Earth, Special, Colors, Decoration, Redstone, Spawners) and about 253 items. Click any item (Java or Bedrock) to open its **buy page**: Buy 1, Buy 16, Buy 64, Fill inventory and Type an amount (Java chat, `1.5k` works; Bedrock number picker). Java shift-click on a grid item buys one stack at once. The old left = 1 / right = 64 is gone, and the old confirm screen above $100,000 is gone. Instead: **Undo last buy** (slot 47, 15 s, full refund once while every bought item is still in your inventory) and a **second click for big buys** (a buy over 50% of your money turns orange: "click again"). Both can be switched off in `/settings` → Gameplay. Owner prices: hopper $2,500, bone meal $30; the ender chest, dyes, coloured wool, most flowers and job blocks are gone. The **Spawners** tab sells tier 1 ($117,000) and tier 2 ($213,000) spawners with "Next works at X%" and a payback time. The shop never buys back; Sell (slot 51) opens the sell chest. Prices are still a draft for you to approve.

| Command | Who | What |
|---|---|---|
| `/shop [tab]` | player | Open the shop on your last tab, or one tab (`essentials`, `farming`, `wood`, `stone-earth`, `special`, `colors`, `decoration`, `redstone`, `spawners`). Old names (`utility`, `building-blocks` ...) still work. |
| `Sell (slot 51)` | player | Opens the sell chest from inside the shop. |

**Test:**

1. Type /shop: tabs in the top row, the current one glints. Click tabs: they switch in place, title `Shop » <Tab>`.
2. Click torches: the buy page. Buy 1, Buy 16, Buy 64, Fill inventory. Each shows `✔ +64 Torch -$256 · $... left` in the action bar; the page stays open.
3. Type an amount: `1.5k` in chat (Java) or the number picker (Bedrock).
4. Java: shift-click a grid item: one stack bought.
5. Buy something, then press Undo last buy within 15 s: full refund, items taken back. Place one of the items first: Undo is refused.
6. With $10,000, buy for $6,000: the button turns orange, "click again". Wait 5 s: it disarms.
7. Turn both off in /settings → Gameplay and repeat 5 and 6.
8. Fill your inventory and buy: you only pay for what fits; nothing is dropped.
9. Spawners tab: check prices, "Next works at X%" and the payback line.
10. Try buying with too little money and try to sell something to the shop: clear messages.
11. Bedrock: tap an item, use the buy page buttons and the number picker; check names fit.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → GUI-3 (design: docs/design/SHOP_DESIGN.md)._

### Auction house (/ah)

Players list items at a fixed price. Buyers browse by category, search and sort. The seller pays a 1% fee when listing and 5% tax when the item sells. Players get 5 listings (more with ranks and up to +2 from Economy achievements). Money goes to the seller even when offline. Expired or cancelled items wait in a collection bin. Staff can remove listings. **New:** `/ah` opens at tutorial step 8; sort is a pick list; search is a text popup on Bedrock; staff remove picks a reason from a list with a confirm (GUI-4c); tradeable crate key items can be listed (category Special).

| Command | Who | What |
|---|---|---|
| `/ah (/auction, /auctionhouse)` | player | Browse, search, sort, list, cancel, collection bin |
| `/ah sell <price> [12h\|24h\|48h]` | player | List the item in your main hand |
| `Remove listing (menu button)` | mod | Staff: remove a listing with a reason, item goes to the seller's bin |

**Test:**

1. Hold an item and type /ah sell 1500. Check the dialog shows fee, tax and what you receive. Pick 24h.
2. Open /ah with another account. Search the item name. Buy it. Check both balances.
3. Check the seller gets a chat message (online) or on the next join (offline).
4. Cancel a listing and check the item goes back to the collection bin and the 1% fee is not refunded.
5. Let a short listing expire (ask staff to shorten). Check the item is in the bin.
6. List 6 items. The 6th should be refused (5 slots).
7. Try to list a money note, a starter tool, a filled shulker box. All should be refused.
8. Try to buy your own listing and a listing from your other profile. Both refused.
9. As staff remove a listing and check the seller sees the reason.
10. Withdraw a tradeable crate key (see Crates) and list it: it shows under Special.
11. Test on Bedrock: search popup, sort pick list and the buy confirm.

_Questions for this feature: 14 on the Decisions page (Feature = "Auction house (/ah)")._

### Trading (/trade)

Two players near each other can trade items and money (never crystals) in a safe window. Each side adds items and money, both press ready, a 3 second countdown runs, then the swap happens in one step. Anything that changes cancels the countdown. If a player has no room, the items wait and arrive later. **Changed (GUI-BUGS):** ?, help, Close, Main menu and Esc never cancel the trade; **Cancel trade is on slot 53**; a trade only ends after 60 s with the window closed. **New (ECON-R3):** each side shows **value hints** (sell price, shop price, AH median when 5+ independent sellers and buyers exist); a detected dupe of a unique item gives a 24 h trade block. `/trade` opens after the tutorial.

| Command | Who | What |
|---|---|---|
| `/trade [player]` | player | Ask a nearby player to trade, or open your open trade |
| `/trade accept [player]` | player | Accept a request |
| `/trade deny [player]` | player | Deny a request |

**Test:**

1. Stand next to a second player and type /trade <name>. They get a clickable request.
2. Accept. Each side adds a few items by tapping them in the inventory. Take one back.
3. Add money with the gold ingot. Check shards can be added but crystals cannot.
4. Press ready on both sides. Change an offer during the countdown and check it resets.
5. Complete a trade and check both inventories and balances.
6. Fill one inventory and complete. Items should wait and arrive when there is room.
7. Walk more than 24 blocks away, take damage, or log out mid-trade. The trade must cancel and return items.
8. Trade money with a brand-new account: allowed (no new-account limit). Try a staff item (from /give): refused.
9. Try to trade with your own other profile, or with a player who /ignores you: both refused.
10. In a trade press ?, then Back; press Close and reopen with /trade within 60 s: the trade is still there. Cancel on slot 53 ends it.
11. Put a diamond and an enchant book in: check the value hint lines.
12. Bedrock: the trade request popup, tapping items in, and the money picker.

_Questions for this feature: 11 on the Decisions page (Feature = "Trading (/trade)")._

### Item box (/box)

Items are never dropped on the ground. Whatever does not fit your inventory waits in your **item box**, one per profile, until you take it out: sell chest returns, shop buys, Forge and Enchanter items, wardrobe and loadout returns, trade deliveries, auction collection overflow and staff gives. Now also crates, key items, boosters, daily/playtime/pass/quest/collection rewards, Black Market buys and Depths drops when the satchel and inventory are full. You get a clickable line when you join (or switch profile) with items waiting. Above 500 stacks you are warned. **Changed (SECFIX1):** a hard cap of **5,000 stacks**: at the cap, things that would add to the box (crate opens, reward claims, Depths mining) are refused until you take items out; nothing is deleted. Admins can view and remove items from a player's box in /inspect (removed items are kept for staff).

| Command | Who | What |
|---|---|---|
| `/box` | player | Open your item box: oldest first, click a stack to take it, or 'Take all that fit'. |
| `/inspect <player> → Box` | admin | View a player's box per profile; remove one stack or clear it (confirm, logged). |

**Test:**

1. Fill your inventory completely. Buy 64 stone in /shop and close a sell chest with items inside: the extra items go to /box with a chat line, action bar and sound.
2. Type /box. Each stack shows where it came from and when. Click one stack, then 'Take all that fit'. With a full inventory only what fits is taken; the rest stays.
3. Leave and join: 'You have N item(s) in /box' (click to open).
4. Switch profile: the other profile has its own box. Try to delete a profile that has items in its box: refused.
5. Double-click and spam-click a stack: you never get it twice.
6. As admin: /inspect <player> → Box, switch profile with the head, remove a stack and clear the box (confirms). Check /logs.
7. Bedrock: open /box by command and from the join line, tap stacks and 'Take all that fit'.

_Questions for this feature: 3 on the Decisions page (Feature = "Item box (/box)"), plus 2 under "From your answers"._

### Economy dashboard and alerts (/eco admin)

Staff (moderators and up) see a dashboard of how much money is created and removed, the sink ratio, an inflation badge (OK, Watch, ACT), top earners and biggest transactions. Alerts fire for strange events: very high earning, big or repeated transfers, many notes cashed, a huge sell spike, or money appearing without a record. Alerts never punish by themselves. **New:** `/eco` changes above $1T come as an alert with **Allow / Deny** for the owner and admins (Bedrock: popup); the dashboard pages (GUI-4c).

| Command | Who | What |
|---|---|---|
| `/eco admin` | mod | Open the economy dashboard |
| `/eco admin <alert number>` | mod | Open one alert with evidence and a Suggest punishment button |
| `/eco give\|take\|set\|reset <player> <currency> <amount> <reason>` | admin | Change balances (logged, staff are told) |
| `/eco note <player> <amount> <reason>` | admin | Give a staff note |

**Test:**

1. As mod type /eco admin. Check the money supply, created vs removed, and the badge.
2. Open a section (sources, sinks, top earners) and check the numbers match what you did.
3. Use /eco give <alt> money 60000000 test as admin. A big-transfer or earn alert should fire in about a minute.
4. Pay yourself back and forth five times with similar amounts to trigger the repeat-transfer alert.
5. Cash 10 money notes within an hour to trigger the notes alert.
6. Click [Open] on the alert chat line and the [Punish] button (do not apply).
7. Check helpers cannot open the dashboard.

_Questions for this feature: 11 on the Decisions page (Feature = "Economy dashboard and alerts (/eco admin)")._

## Rewards

All new since update92 (Phase 10). Keys, rewards and progress are **per profile** unless it says account. Every claim is crash-safe: a reward can at worst be lost (staff see it as "review" in /logs), never paid twice.

### Crates and keys (/crates) — new

Keys are earned only and live per profile and crate as **bound** keys (votes, AFK pool, daily calendar, playtime, tutorial: never traded) and **tradeable** keys (quests, battle pass, fragments, events). Opening uses bound keys first, and loot from a bound key is bound too. Crates: Ember, Cinder, Blaze, Inferno, Phoenix, Eclipse (Conqueror shows "Coming soon"). Each crate has **pity** (a top reward is forced after N opens without one) and duplicate cosmetics re-roll. Money and crystal pouches grow with your tier. **Crate yard (M10.3):** the crates stand on pedestals as real blocks with a hologram; **click** opens 1 with an in-world show (rarity particles, the reward floats up, firework for a ★ reward), **sneak-click** shows the rewards; without a key a click shows the rewards and how to get keys. The **key board** lists how to get keys and the top-reward chance of every crate. ★ wins of Inferno and up are one line in global chat. **Key items (M10.4):** in a crate's preview, **Withdraw 1 key** or **Withdraw an amount** turns tradeable keys into unique key items that trade on /trade and the AH; USE one to put the key back on your profile (not on another profile of your own account). Copies are taken and reported. **Key fragments (DEPTHS-R3):** mine/forest tiers 11+ drop fragments (about 1 per 3.3 h); 3 make a tradeable Phoenix key with the **Key Fragments** button in /crates.

| Command | Who | What |
|---|---|---|
| `/crates [crate]` | player | Keys and every crate (pity, top odds). Java: left = preview, right = open 1 (spinner), shift = open 10. Bedrock: tap = preview with buttons. Preview: Open 1 / 10 / all, Withdraw keys. The Crate Keeper NPC and /menu open it too. |
| `Click / sneak-click a crate at the crate yard` | player | Open 1 / view rewards. |
| `/settings` → Gameplay → Fast crate opening | player | Skip the spinner and build-up (off by default). |
| `/crates admin give <player> <crate> <amount> [bound\|tradeable]` | admin | Add keys (default bound; confirm above 64; console OK). |
| `/crates admin take <player> <crate> <amount>` | admin | Take keys (bound first, all or nothing). |
| `/crates admin view <player>` | admin | Keys and pity per crate and profile. |

**Test:**

1. As admin: `/crates admin give <you> ember 5` and `/crates admin give <you> cinder 3 tradeable`. Open /crates: the counts show bound and tradeable.
2. Preview Ember: every reward shows `★ Name (xN) | chance%`; pouches show what they pay at your tier.
3. Open 1 (spinner, click to skip). Open 10: a summary screen and one chat summary. Check `/box` takes what doesn't fit.
4. Crate yard: click a crate with a key, watch the floating reward; sneak-click: rewards; click a crate without a key: how to get keys. Fast crate opening on: no build-up.
5. Cinder preview → Withdraw 1 key: a key item. Trade it to an alt; the alt USEs it: +1 tradeable Cinder key. USE one on your own 2nd profile: refused. Try Withdraw on Ember (bound): "No tradeable keys".
6. Open a bound key: a won item can't be traded or put on the AH.
7. Open many keys of one crate and watch the pity counter (`Pity 7/23`) force a top reward.
8. As admin: `/crates admin take` more than the player has: nothing taken, the count is shown. `/crates admin view` and switch profile.
9. With 3 fragments (staff give or tier 11+), press Key Fragments: 1 tradeable Phoenix key.
10. Bedrock: crates are visible blocks at the yard; tap opens; the preview buttons and the amount picker work; the floating item may not show, but the chat line and title do.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M10-1, M10-3, M10-4, SMALL2, DEPTHS-R3._

### Boosters and /thanks — new

**Personal boosters** (Sell, Skill XP, Tool XP, Crystals) are bound items from crates, the daily calendar and the pass. USE one or click it in /boosters to start. The same strength adds time up to 3 h; another strength of a running type waits. Time only counts while you are online on that profile. **Global boosters** (Phoenix/Eclipse crates, later the store) help everyone online; one per type runs, more wait in a queue of 5; a boss bar shows type, time left, the starter and `/thanks`. **Thanks:** 1 gem to the starter per player per booster (never your own, not from the same address, max 100 gems per booster); typing "thanks", "ty" or "thx" in chat works too. All boosts stay inside the bonus caps (sell max +300%). The AFK pool's sell booster is banked here now and starts by itself when you leave the pool.

| Command | Who | What |
|---|---|---|
| `/boosters (/booster)` | player | Your personal boosters (running, time left, banked), the global boosters and queue, booster items in your inventory. |
| `/thanks (/thank)` | player | Thank every running global booster you have not thanked yet. |
| `/settings` → Global booster bar | player | Hide the global booster boss bar. |

**Test:**

1. Get a personal Sell booster (crate or daily) and USE it: a confirm with the details, then it runs. /boosters shows time left. Sell something: the bonus is in the sell breakdown.
2. USE a second one of the same strength: time adds. Go past 3 h: refused, item kept. Another strength: refused until the first ends.
3. Log out for 10 minutes: the time did not go down.
4. Start a global booster on account A: the boss bar shows for everyone. B types `/thanks`: A gets 1 gem. B thanks again: nothing. A thanks his own: nothing.
5. Start a second global booster of the same type: it waits in the queue.
6. Try to trade or AH a booster item: refused (bound).
7. Bedrock: the boss bar is readable; USE (long-press) starts a booster.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M10-2._

### Daily rewards and playtime (/daily) — new

A **30-day calendar per profile**: one claim per day, reset at 00:00 Oslo. Missing a day never sends you back (the day only moves when you claim). Rewards: money (scaled to your tier), crystal pouches, bound keys (days 3, 7, 10, 14, 17, 21, 24, 28 Inferno, 30 Eclipse), boosters, an enchant book and Disenchant Scrolls. Fuel days (4, 12, 23) give Fuel Cells while Island Fuel is on, otherwise crystals; Wardrobe Tomes pay crystals for now. **Streak:** 10 gems every 7 days in a row; titles Devoted (30) and Unbroken (100). **Playtime tab:** only active time counts (not AFK, not in the AFK pool): every hour a crystal pouch and 20 pass XP, every 10 hours a bound Cinder key, titles at 50/100/250/500 h. A join reminder tells you when today's reward is open (switch in /settings → Chat). The /menu **Rewards** button claims today's reward in one click.

| Command | Who | What |
|---|---|---|
| `/daily` | player | Calendar tab (claim today, streak, reset countdown) and Playtime tab. The Daily Rewards NPC opens it too. |

**Test:**

1. Open /daily: today glows "Click Claim". Claim: one line with the rewards; keys show in /crates, items in your inventory or /box.
2. Click again: already claimed. Rejoin: the reminder is gone.
3. Switch profile: profile 2 has its own calendar.
4. Look at day 4 with Island Fuel off ("pays ◆ N for now") and on (Fuel Cell).
5. Playtime tab: play actively for an hour (or ask staff), then check the hourly reward. Stand in the AFK pool: playtime does not count.
6. Leave a reward open and press the /menu Rewards button: claimed.
7. Turn the daily reminder off in /settings and rejoin.
8. Bedrock: tap days, switch tabs, the calendar fits.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M10-6._

### Voting (/vote)

**New. The vote receiver is OFF by default.** `/vote` is a menu now: one button per site (PlanetMinecraft, Minecraft-MP, MinecraftServers.org, TopG, Minecraft-Server-List; click sends the link, Bedrock gets a popup), All vote sites (skybornmc.net/vote), your vote streak and the vote party. Per vote: **1 bound Ember key + 3 gems + 25 pass XP**, once per site per account per 24 h, max 3 accounts per address per day, paid to the profile you are on. Offline votes wait for your next join (Bedrock names with the `.` prefix are matched). **Streak** (days in a row): Cinder at 7, Blaze at 14, Cinder at 21, Inferno at 30. **Vote party:** every 500 votes, everyone online who voted today, finished the tutorial and is not AFK gets a bound Blaze key; progress on the Tavern board.

**The receiver is OFF** (`votes.yml → receiver.enabled: false`) until the server is registered on the sites. To switch it on:
1. Start the server once (the keys are made on the first start, even while off): `plugins/PhoenixSkyblock/votifier/rsa/public.key` and `plugins/PhoenixSkyblock/votifier/tokens.txt` (one token per site). Never paste these in Discord or a config file.
2. Open TCP port **8192** in the VPS firewall: `sudo ufw allow 8192/tcp` (the setup script only opens 25565 and 19132). Change `receiver.port` if you want another port.
3. Register the server on each site with host `play.skybornmc.net`, port 8192, and the public key (Votifier v1 sites) or that site's token from `tokens.txt` (NuVotifier v2 sites).
4. Replace each site's link in `votes.yml → sites` with your server's vote page on that site.
5. Set `votes.yml → receiver.enabled: true` and `/skyblock reload rewards` (or restart). The console says the receiver listens on the port.
6. Press each site's own "test vote" button. If the console logs "unknown service", add that name to the site's line in `sites`.

| Command | Who | What |
|---|---|---|
| `/vote` | player | Vote menu. The Voting NPC in the Tavern opens it too. There is no staff test command (needs your OK). |

**Test:**

1. Open /vote: five sites, streak and party progress. Click a site: the link in chat (Java) or a popup (Bedrock).
2. With the receiver on: vote on one site (or its test button): 1 Ember key + 3 gems arrive with one line. Vote again on the same site within 24 h: no reward.
3. Vote while offline: the reward comes at your next join, on the profile you join on.
4. Vote on two days in a row and check the streak in /vote.
5. Check /logs (category rewards) shows the vote.
6. Bedrock: vote with a Bedrock name: it is matched to the `.Name` player.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M10-5._

### Quests (/quests) — new

Three boards, switched by tabs. **Daily:** 3 quests a day (new at 00:00 Oslo), about 12 minutes each, each pays 3 minutes of income at your tier, crystals and 100 pass XP; all 3 = 1 **tradeable Cinder key**; 1 free swap per day. **Weekly:** 5 quests (new Monday), about 45 minutes each, 8 minutes of income, crystals and 400 pass XP; all 5 = 1 tradeable Blaze key. **Island missions:** 3 per island per week, shared by members (never coop); members with 5%+ of a mission get crystals and 150 pass XP. Quests only use content you have unlocked (Depths tiers, selling, tool levels, forge, enchants, visits, likes), and targets scale with your tier. Progress comes from server events only; finished quests pay themselves once.

| Command | Who | What |
|---|---|---|
| `/quests` | player | Daily, Weekly and Island tabs; click an open daily quest to swap it (confirm). Quest Master NPC and /menu → You → Quests open it too. |
| `/quests admin view <player>` | admin | Chat summary of a player's quests. |
| `/quests admin reset <player> <daily\|weekly\|island>` | admin | Roll new quests for that board. |
| `/quests admin complete <player> <daily\|weekly\|island> <quest>` | admin | Finish one quest now (normal rewards follow). |

**Test:**

1. Open /quests: three daily quests with goal, Depths tier, progress bar and rewards.
2. Work on one: the action bar shows every 25%. Finish it: title, chat line, rewards paid once.
3. Swap a daily quest (confirm). Try a second swap the same day: refused.
4. As admin finish all 3 with `/quests admin complete`: 1 tradeable Cinder key arrives in /crates.
5. Island tab with two members: both add to a mission; both get the helper reward.
6. A new player who has not opened the Depths gets no mine/chop/kill quests.
7. Bedrock: tabs, the swap confirm and progress bars read well.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M10-7._

### Battle pass (/pass) — new

**100 levels x 800 pass XP** per season, plus a bonus step every 2,000 XP after level 100. Pass XP comes from quests (100 / 400 / 150), votes (25), the daily calendar (50) and active playtime (20 per hour); while you are 15+ levels behind, XP counts 1.5x. **Free track** (per profile): a small reward every level, keys at milestones (Ember, Cinder, Blaze at 25 and 75, Inferno at 50, Phoenix at 100; pass keys are tradeable), boosters at 12/37/62/87, titles Season Climber (50) and Season Champion (100). **Premium track** (per account): **cosmetics only** (a title every 5 levels), no extra rerolls; set by staff now, by the store later. Claim by clicking a level or Claim all; the /menu Rewards button claims ready levels too.

| Command | Who | What |
|---|---|---|
| `/pass` | player | 7 levels per page: level line, free row, premium row; Claim all on 46. |
| `/pass admin view <player>` | admin | Level, XP, claimed tiers, premium. |
| `/pass admin xp <player> <xp>` | admin | Give pass XP (no catch-up bonus). |
| `/pass admin reset <player>` | admin | XP to 0; claimed tiers stay claimed. |
| `/pass admin premium <player> <on\|off>` | admin | Premium on or off for the account this season. |

**Test:**

1. Open /pass: level, XP, season and days left in slot 4.
2. Finish a daily quest: +100 pass XP. `/pass admin xp <you> 4000`: 5 levels ready, they glow.
3. Click a level: claimed. Claim all: the rest. Level 5 gives 2 tradeable Ember keys.
4. Press the /menu Rewards button with a ready level: it is claimed.
5. `/pass admin premium <you> on`: premium titles become claimable; a premium title goes to the account (check on profile 2: not claimable again).
6. `/pass admin reset <you>`: XP 0, nothing can be claimed twice.
7. Bedrock: names start with the key fact (`Level 12 · Click to claim`); page buttons work.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M10-8._

### Collection log (/collection) — new

A per-profile log of items you **found yourself**: the 12 Depths mine drops, 11 logs, 10 mob drops and the star rewards of crates. Trades, AH buys, /pay and staff gives never count. Locked entries show "??? · how to get it". Every 25% of a page gives +1 island level point; a full page gives its title (Deep Miner, Woodwarden, Monster Hunter, Lucky Hand) and a bound Blaze key; the whole log gives **Keeper of Ashes** and a bound Phoenix key. Paid once.

| Command | Who | What |
|---|---|---|
| `/collection` | player | Tabs Mine, Forest, Combat, Crates (found/total in the tab name). Also /menu → You → Collection. |

**Test:**

1. Open /collection on a fresh profile: everything locked with a hint in the name.
2. Mine coal in Mine tier 1: "New in your collection: Coal (Mine 1/12)", a soft sound. The entry shows when and where.
3. Buy an item on the AH that you never found: it does not unlock.
4. Reach 25% of a page (staff can help with skills): +1 island level point in `/is level` (Collections source).
5. Profile 2 has its own empty log.
6. Bedrock: tap a locked entry: the hint repeats in the action bar.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M10-10._

### Black Market (/blackmarket)

**New.** A rotating shop paid in **shards**. Restocks at 00:00 and 12:00 Oslo: 1 headline (server-wide stock of 25), 3 rare and 4 common offers (only offers whose feature exists; with the defaults the common row shows 2). The fixed row has the **Island Charter** and the Disenchant Scroll. Limits count per account. Items and keys are bound to the profile and go to the inventory or /box. **Island Charter:** island sizes above 9x9 for this season only: 11x11 (150 shards / 250 gems), 13x13 (300 / 500), 15x15 (450 / 750), one step at a time, only after 9x9 from `/is upgrade`, buyer needs the Upgrades island permission.

| Command | Who | What |
|---|---|---|
| `/blackmarket` | player | The Black Market (restock clock, headline, rare, common, fixed row, your shards and gems). Click an offer: confirm with the price. The Black Market NPC opens it too. |
| `/blackmarket admin reroll` | admin | New offers now (confirm, announced). |
| `/blackmarket admin give <player> <offer>` | admin | Give an offer for free. |
| `/blackmarket admin view <player>` | mod | A player's last buys and their hand-out state. |

**Test:**

1. Open /blackmarket: the restock clock and offers with price, your buys and "bound".
2. Ask staff for shards (`/eco give <you> shards 500 test`) and buy the Disenchant Scroll: confirm, shards go down, the scroll arrives.
3. Buy the same limited offer again: refused with the limit.
4. With a 9x9 island buy the 11x11 Charter: the border grows, members are told, the confirm said "this season only". Try 13x13 first on a 7x7 island: refused.
5. As admin: reroll (a chat line for everyone), give an offer, view a player's buys.
6. Bedrock: offers and the confirm by tapping.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → M10-11._

## The Depths

### The Depths: zones and tier locks

The Depths is the main money world. It has three wings (Mine with 12 tiers, Forest with 11, Combat with 10) around a hub called The Gate. Each tier needs a skill level (0, 5, 10 ... 66). A locked tier shows iron bars only to you and pushes you back. Unlocked tiers are reached with /dungeon instantly (3 seconds only while in combat or in a combat tier), or through the portal at spawn. The Depths are protected outside the ore and log fields (see Region protection). **New:** the Depths open at tutorial step 5; "Show players" in /settings can hide other players here.

| Command | Who | What |
|---|---|---|
| `/dungeon` | player | Tier menu with tabs Mine, Forest, Combat. Click an unlocked tier to teleport (instant; 3 s in combat or from a combat tier) |
| `/depths tp <zone>` | builder | Staff: teleport to The Gate, a tier or the boss arena, ignoring locks |
| `/depths generate [zone\|all]` | admin | Staff: build the placeholder layout |
| `/depths debug [player]` | admin | Staff: show region, timers, flags |

**Test:**

1. Type /dungeon. Check the three tabs and that locked tiers show 'Mining 5 needed' with a progress bar.
2. Click tier 1 of the Mine: you arrive at once. From a combat tier, click a Mine tier: you wait 3 seconds; move during it and see it cancel.
3. Walk to the gate of tier 2 without the level. Bars should appear and you should be pushed back.
4. Ask staff to /skills set you mining 5 and see the tier 2 gate open with a title and sound.
5. Ask staff to lower your level while you stand in tier 2. You should be sent to The Gate.
6. Try to break a normal block or place a block in a zone. It must be refused.
7. Try ender pearls into a locked tier. They must not get you in.
8. Test on Bedrock: the /dungeon menu and the 3 second countdown from a combat tier.

_Questions for this feature: 11 on the Decisions page (Feature = "The Depths: zones and tier locks")._

### The Depths: ore, logs and drops

In the Mine and Forest each block gives a custom drop (coal, copper, iron ... voidstone; oak, birch, spruce ... warped logs) that goes straight into your satchel. Ores are personal: only you see them and they come back after about 10 seconds. Blocks also give crystals now and then, and very rarely a book or a blueprint. Higher tiers give more valuable drops. **New:** first finds go into your collection log; mine and forest tiers 11+ drop **key fragments** (about 1 per 3.3 h; 3 = a Phoenix key in /crates); blocks count for quests and island tasks.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Mine and chop in the Depths with a custom tool. Open /dungeon > Satchel to see drops |

**Test:**

1. Go to Mine tier 1 with your starter pickaxe and break ore blocks. Check coal drops go to the satchel, with an action bar line.
2. Check the block turns to tuff and comes back after about 10 seconds. A second player should not see your broken block.
3. Break ore with a plain vanilla pickaxe and see if it gives anything.
4. Watch for crystals: the action bar shows '+N crystals' every few seconds.
5. Chop logs in the Forest wing. Check logs go to the satchel. Leaves must not be breakable.
6. Try a bigger tier with a too-low tool and compare the break time.
7. Break blocks quickly with an auto-clicker style (ask staff) and see the fast-break check ignore them silently.
8. Check the scoreboard shows the satchel fill.

_Questions for this feature: 12 on the Decisions page (Feature = "The Depths: ore, logs and drops")._

### The Depths: combat zones, mobs and mini-bosses

The Combat wing has 10 tiers (Graveyard to Warlord's Citadel). Mobs spawn while players are in the zone: 8 plus 2 per player, max 30 per zone. A mini-boss appears every 10 minutes in each zone with 15 times health and better loot. Kills give combat XP, drops into the satchel, crystals and vanilla XP. **Changed (DEPTHS-R3):** a mini-boss gets **+25% health per extra fighter** (max 10, shown in the boss bar) and pays the **full reward to everyone** with 10% of the damage, a hit in the last 90 s, not AFK, max 3 accounts per address, exactly once. **Combat XP now only comes from Depths combat zones** (no Combat XP from island or warzone mobs); Soul Reaper gives sword XP instead of Combat XP. When the satchel is full, kill drops go to your inventory, then `/box`.

| Command | Who | What |
|---|---|---|
| `/dungeon (Combat tab)` | player | Go to a combat tier |
| `/depths debug` | admin | Staff: see combat mobs and the mini-boss timer |

**Test:**

1. Open /dungeon, Combat, tier 1 (Graveyard). Check mobs spawn and you can kill them with your starter sword.
2. Check drops go to the satchel and crystals show on the action bar.
3. Wait for the mini-boss (about 10 minutes with players in the zone). Check the boss bar and the chat line.
4. Kill the mini-boss with a friend: the boss bar shows more health for two fighters. Both get the full reward if both dealt 10% or more and hit it in the last 90 s. A third account from the same address that only stood there gets nothing.
5. Kill a mob on your island or in the warzone: no Combat XP.
6. Die to a mob. Check you keep your inventory and respawn where expected.
7. Leave the zone and wait 30 s. Mobs should disappear.
8. Test with a bow, a trident and a crossbow and see how they compare to a sword.
9. On Bedrock check the boss bar and hits.

_Questions for this feature: 12 on the Decisions page (Feature = "The Depths: combat zones, mobs and mini-bosses")._

### Satchel (Depths storage)

A virtual bag that holds everything dropped in the Depths. It starts at 256 items and grows with Mining and Foraging levels and 5 money upgrades (up to 100,000). From the satchel you can sell all, sell one item type, take items out to trade, and the Forge uses what is inside. **Changed (REV3-FIX):** a full satchel no longer stops mining: drops (mining and kills) go to your inventory, and what does not fit goes to `/box` a few seconds later (warning on the action bar every 3 s). Mining only stops when `/box` is at its hard cap (5,000 stacks). The **Deep Pockets** mastery adds +10% capacity per rank. Sell all and Upgrade now ask to confirm with the details (GUI-BUGS).

| Command | Who | What |
|---|---|---|
| `/dungeon > Satchel (button)` | player | Open the satchel (also in /menu > The Depths) |
| `Sell all (button)` | player | Sell everything in the satchel |
| `Take out (click item)` | player | Move one stack or all that fit into your inventory |
| `Upgrade (button)` | player | Buy more space with money |

**Test:**

1. Mine some blocks in the Depths and open the satchel. Check the amounts, price and value.
2. Click Sell all: a confirm with the total; confirm. Check the money and the sound.
3. Take a stack of coal out into your inventory. Check it shows as an item and can be traded.
4. Buy the first upgrade ($25,000) and check the capacity grows by 256.
5. Fill the satchel to full and keep mining (changed): blocks still break, drops go to your inventory; fill the inventory too: a few seconds later the rest arrives in `/box` with one line. The warning shows at most every 3 s.
6. Check the scoreboard shows 'Satchel N / M'.
7. Switch profile and check each profile has its own satchel.
8. On Bedrock, tap items and Sell all.

_Questions for this feature: 10 on the Decisions page (Feature = "Satchel (Depths storage)")._

### Depths v2 tier palette (spike, OFF by default)

**Turn on:** `depths.yml → palette.enabled: true`, then `/skyblock reload depths`. The console prints "Depths palette (spike) on: mine_t01 shows [...]". Turn off the same way. Full test script with 2 Java players and 1 Bedrock player: [docs/phases/DV2_SPIKE_TEST.md](phases/DV2_SPIKE_TEST.md).

A test of the Depths v2 idea (DV-9) before the full build: in the **Mine tier-1 coal field** every player sees the ore of their own tier at the same blocks: **coal** (Mining 0), **copper** (Mining 5), **iron** (Mining 10). On a level-up the field swaps for that player only, nearest blocks first, with a title ("Copper unlocked!"), a sound and a particle wave. Breaking gives the **shown** tier's drops, XP, tool XP, quest and task counts, and the break time of the shown block. Bedrock gets at most 300 blocks per tick so phones don't stutter. Combat zones never get a palette. Staff can show a player a lower open tier.

| Command | Who | What |
|---|---|---|
| `/depths palette` | admin | Status: pilot zone, tiers, the shown tier and level, swap counts and times. |
| `/depths palette <auto\|1\|2\|3> [player]` | admin | Show a lower open tier (clamped to what the Mining level opens); `auto` = by level. Reset on quit and profile switch. |

**Test (short version, the full script is in DV2_SPIKE_TEST.md):**

1. Switch it on. `/skills set <A> mining 0` for A, B and C (Bedrock); all stand in the mine_t01 field: everyone sees coal.
2. `/skills set A mining 5`: A's field turns to copper with title, sound and particles; B and C still see coal. Then mining 10: iron.
3. A mines: raw iron in the satchel and iron XP. B mines a block A sees as iron: B gets coal.
4. A mines iron with a wooden pickaxe: slow like vanilla, no fast-break alert.
5. `/depths palette 1 A`, then `/depths palette auto A`: a quiet swap back and forth.
6. Relog, walk away and back, switch profile: the right tier each time (after a relog coal may show for up to about 1 s).
7. Bedrock C: `/skills set C mining 10`: the swap rolls out over a few ticks, no stutter, no ghost blocks, breaking takes the right time.
8. Switch it off: everyone sees real coal again.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → DV2-SPIKE (plan: docs/phases/PHASE-4-DEPTHS-V2.md)._

## Tools, skills & enchants

### Forge and living tools

Every player gets a free starter kit (pickaxe, axe, hoe, sword, rod) from the Quartermaster once per 24 hours. At the Forge a tool can be upgraded to the next tier for money and Depths drops. Tools gain levels (1 to 50) from use, open perk slots at levels 5, 15, 30 and 45, can prestige once per season, and are kept on death. Tools have no durability and can be traded.

| Command | Who | What |
|---|---|---|
| `/forge [type]` | player | Open the Forge: tool tabs, tiers, starter kit |
| `/tool` | player | Open the tool menu of the custom tool in your hand (also shift+right-click) |
| `/tool give <player> <tool> [tier]` | admin | Staff: give a custom tool |

**Test:**

1. Type /forge and open the Starter kit. Claim it. Try again and see the 24 hour cooldown.
2. Mine in the Depths and watch the tool XP and level in the lore. Reach level 5 (slot 1 opens).
3. Type /tool with the pickaxe in hand and check stats and slots.
4. Collect the ore for the next tier and forge it. Check level, perks and XP are kept.
5. Try to rename, put in an anvil, grindstone or crafting grid. All must be refused.
6. Die with the tool and check it stays in the inventory.
7. Trade the tool to a friend and check they can use it.
8. Drop the tool and try to throw it into lava.
9. On Bedrock, use /tool instead of shift+right-click.

_Questions for this feature: 10 on the Decisions page (Feature = "Forge and living tools")._

### Blueprints and skins

Blueprints are rare drops (about 1 in 15,000 to 20,000 blocks or 1 in 1,500 kills). With money and materials at the Forge a blueprint turns into a special tool with one ability: auto-smelt, swift, scholar (more tool XP) or lifesteal. Skins are looks for tools (Phoenix Flame, Frostbite, Royal Gold) kept across seasons. Staff can give both.

| Command | Who | What |
|---|---|---|
| `/blueprint give <player> <blueprint>` | admin | Staff: give a blueprint item |
| `/skin give <player> <skin>` | admin | Staff: add a skin to the collection |
| `Tool menu > Skins` | player | Pick a skin for your tool |

**Test:**

1. Ask staff for /blueprint give <you> sage_pickaxe. Open /forge and craft it. Check the new tool at level 1 with the ability.
2. Check the blueprint is used up and cannot be used a second time.
3. Try to list the blueprint in /ah and /trade it.
4. Ask staff for /skin give <you> frostbite. Open /tool > Skins and apply it.
5. Forge the tool to the next tier and check the skin stays.
6. Trade the tool to a friend and check the skin goes with it.
7. Play 2 hours in the Depths and count how many blueprints drop (probably none; that is the point).
8. Check Bedrock shows the skin name even without the resource pack.

_Questions for this feature: 10 on the Decisions page (Feature = "Blueprints and skins")._

### Skills (/skills)

Three skills exist now: Mining, Foraging and Combat (Farming comes with M7.3, Fishing later). XP comes from the Depths; **changed (DEPTHS-R3):** Combat XP only from Depths combat zones. Generator ores on islands give no Mining skill XP (they feed the Mining pillar). New: base skill XP also feeds the island pillars; skill XP boosters and achievement boosts add to your gains (inside the caps). Levels go up to 150, and the first 66 unlock Depths tiers. Skills are per profile and reset each season. Anyone can look at another player's skills.

| Command | Who | What |
|---|---|---|
| `/skills [player]` | player | Open the skills menu for you or another player |
| `/skills set <player> <skill> <levels>` | admin | Staff: set a level |
| `/skills add <player> <skill> <levels>` | admin | Staff: add levels |

**Test:**

1. Type /skills. Check three skills active (Farming and Fishing are not built yet).
2. Mine a few blocks. The action bar should show '+N Mining XP'. Turn it off in /settings and check it stops.
3. Level up. Check the title, chat line with the next unlock and sound.
4. Open /skills <friend> to see their skills.
5. As admin: /skills set <you> mining 20, then /skills add <you> combat 5. Check the tiers unlock.
6. Kill a mob from a spawner on your island. It should give no Combat XP.
7. Break an island generator block and check you get no Mining XP.
8. Switch profile and check the skills are different.

_Questions for this feature: 10 on the Decisions page (Feature = "Skills (/skills)")._

### Enchants and the Enchanter (/enchanter)

There are 48 custom enchants (mining, forestry, farming, combat, fishing, armor) with rarities from Common to Mythic. You find books by mining, chopping and killing, or roll them at the Enchanter for crystals. Books unlock an enchant on a tool slot, crystals raise its level. Enchants can be upgraded, combined, removed with a scroll or shattered for 20% back. Books are unique items and tradeable. **Changed (GUI-4b):** a shorter flow: no slot screen when only one slot fits. **New:** books also come from crates, the daily calendar and the Black Market (bound there); with generators on, island generator ores roll Mining books at 25%.

| Command | Who | What |
|---|---|---|
| `/enchanter` | player | Apply, upgrade, combine, remove books, Book shop rolls, guide |
| `Book shop rolls` | player | Buy Apprentice (500), Adept (4,000) or Master (25,000) crystal rolls |
| `Disenchant Scroll` | player | Buy for 2,500 crystals, removes an enchant and keeps the book |

**Test:**

1. Type /enchanter and open the guide. Look at the list and rarities.
2. Buy an Apprentice Roll (500 crystals) and check a level I book arrives.
3. Apply the book to a tool slot (needs tool level 5 for slot 1). With only one free slot it goes straight on (no slot screen).
4. Upgrade the enchant with crystals, then try combining two equal books.
5. Remove the enchant without a scroll and check the 20% crystal refund.
6. Buy a scroll and remove with it. The book must come back.
7. Try to put a book on a vanilla tool or an anvil. Both refused.
8. Trade a book to a friend and list one in /ah.
9. Check a copy of a book (staff test) is taken away when used.

_Questions for this feature: 10 on the Decisions page (Feature = "Enchants and the Enchanter (/enchanter)")._

## Wardrobe & sets

### Wardrobe and armor sets (/wardrobe)

There are 16 sets (5 starter sets for $20k, then sets bought with money, crystals or gems, and top sets only from crates). Wearing 2 or 4 pieces gives bonuses like sell price, drops, speed or damage reduction. Sets level from 0 to 50 by doing what they boost, and the bonus grows from 0x to 2x. Two max-level sets can be merged. Set armor is locked to your body and cannot be dropped or copied. A Wardrobe Chest holds tools and books. **Changed (FIXES1):** a click on a worn set piece takes it off (it goes back to the wardrobe). The set screen has a tidier layout (GUI-4b).

| Command | Who | What |
|---|---|---|
| `/wardrobe (/sets)` | player | Open all sets, wear or take off pieces, buy, level, merge, tomes |
| `Set screen > Wear all / Take all off` | player | Put on or off a full set |
| `Redeem a Set Token (right-click)` | player | Unlock a set from a crate token |

**Test:**

1. Type /wardrobe. Check the filter tabs and that owned sets show first.
2. Buy a $20k starter set. Check the confirm screen (always asks).
3. Wear all pieces. Try to drop, shift-click, swap or put them in a chest: all must fail. Click a worn piece: it comes off (changed) and is not in your inventory.
4. Wear 2 pieces only and check the 2-piece bonus line; mine until the action bar shows set XP.
5. Take off the set and check pieces vanish (not in inventory).
6. Swap profile and check sets are per profile.
7. Try to wear a set in the warzone (blocked) and check it counts as 1x there.
8. Ask staff for a Wardrobe Tome and a Set Token and use both.
9. Test merging with staff help at level 50: check the result is level 0 with both bonuses.

_Questions for this feature: 11 on the Decisions page (Feature = "Wardrobe and armor sets (/wardrobe)")._

### Loadouts (/loadout)

A loadout is a saved preset of set pieces, a pet and the custom tools in your hotbar. One click swaps the whole kit, for example from Mining to Combat. Pieces and tools that go out wait in the Wardrobe Chest. Everyone has 2 presets; ranks and the store give more (max 8). Swaps are blocked in combat and in the warzone and have a 2 second cooldown.

| Command | Who | What |
|---|---|---|
| `/loadout` | player | Open the loadout menu |
| `/loadout <name>` | player | Apply a preset by its one-word name |

**Test:**

1. Wear a set and hold a pickaxe in the hotbar. Open /loadout and save current into an empty slot, name it Mining.
2. Take off the set, move the pickaxe away, then type /loadout Mining. Everything should be back.
3. Create a second preset Combat with a sword and a different set and swap between them.
4. Rename, change icon and delete a preset (with confirm).
5. Get hit by a mob and try to swap within 10 seconds. It must refuse.
6. Sell or trade a tool that is in a preset, then apply it. The rest should still swap and list what is missing.
7. Try a 3rd preset on a free account (should say locked).
8. Check names with spaces turn into dashes. Rename on Bedrock: a text popup (GUI-4b).

_Questions for this feature: 10 on the Decisions page (Feature = "Loadouts (/loadout)")._

## Stacking & spawners

### Stacking: mobs, items, entity limits and dark crops

On islands, mobs of the same kind within 5 blocks join one stack (up to 1,024), shown as 'Zombie x45'. Stacked spawner mobs do not move. Dropped items merge into one with a counter. Islands have limits for armor stands, item frames, minecarts, boats, villagers and display entities. Crops also grow in the dark. A weapon without a custom enchant kills one mob of a stack per hit. **New (ISL-R3):** at most **40 mob stacks per chunk** in total (on top of 8 per type). With Island Fuel on, an Asleep island grows no crops.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Everything works automatically on islands. See /help stacking |
| `/spawner give <player> <type> [amount]` | admin | Staff: give spawner items |

**Test:**

1. On your island let several chickens or pigs stand close. Check they merge into one named stack.
2. Hit the stack with a normal sword. Check it kills one at a time and drops scale.
3. Feed a cow stack wheat and check babies = number of food items.
4. Drop many items on the ground and see them merge with a count. Walk over to pick up.
5. Put a lot of item frames or armor stands until the limit message appears.
6. Plant wheat in a dark room and watch it grow slowly.
7. Let a stack fall into lava or the void and check the whole stack dies.
8. Try name-tagging, leading and riding a stacked mob. Each should be refused.
9. Shear a sheep stack.
10. Spawn many kinds of mobs in one chunk: new stacks stop at 40 per chunk.

_Questions for this feature: 10 on the Decisions page (Feature = "Stacking: mobs, items, entity limits and dark crops")._

### Spawners (stackable)

Spawner items come in 5 tiers (pigs and cows up to blaze, iron golem, evoker). They can only be placed on islands. One block holds 8 spawners and can grow to 512 with the Spawner Stack Limit upgrade. Right-click with another spawner of the same type to add. Break = take one, sneak-break = take all. Mobs spawn straight into stacks, with no light or space needed, only when a player is within 16 blocks. **New (SPAWN-F2):** an island's spawners of one tier give less the more it owns: spawners 1-16 at 100%, 17-32 at 60%, 33-64 at 35%, 65-128 at 20%, 129+ at 10% (counted across all blocks). The spawner menu shows it, and /shop shows "Next works at X%" and a payback time. **New:** the Spawner Stack Limit (16-512) and Spawner Rate (+12% per level) upgrades in `/is upgrade`; Take all asks to confirm (GUI-BUGS); spawners of a disbanded or reset island are frozen and staff can restore them (REV3-FIX). With Island Fuel on, spawners stop on an Asleep island.

| Command | Who | What |
|---|---|---|
| `Right-click spawner block` | player | Open the spawner menu (type, tier, stack, mobs per minute, add/take) |
| `Sneak-right-click spawner block` | player | Open the menu even with a block in hand |
| `/spawner give <player> <type> [amount]` | admin | Staff: give spawners (1 to 2,304) |

**Test:**

1. Ask staff to /spawner give you pig 10. Place one on your island.
2. Right-click with another pig spawner to add. Check the stack and the menu numbers.
3. Break it to take one back; sneak-break to take all.
4. Stand near it for a minute and check mobs appear as a stack.
5. Walk more than 16 blocks away and check it stops.
6. Try placing a spawner at spawn or in the Depths (must fail).
7. Try changing the spawner with a spawn egg (must fail).
8. Place a TNT next to it and check the spawners survive.
9. Kill the stack, sell the loot, and compare money per minute with your other income.
10. Place 20 pig spawners (staff give): the menu shows spawners 17-20 working at 60%. Check the /shop Spawners tab says "Next works at 60%".
11. Take all from a stack: a confirm with the amount.
12. Bedrock: the spawner menu and the Take all confirm by tapping.

_Questions for this feature: 11 on the Decisions page (Feature = "Spawners (stackable)"). New points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → SPAWN-F2._

### Hoppers — new

Hoppers move **64 items every 8 ticks** (F12; vanilla item sorters no longer work). Each chunk counts its hoppers (cached, no tick scans); above **64 hoppers in one chunk** staff get a rate-limited alert with the island, the leader and a teleport (Java: clickable; Bedrock: popup). `/isadmin info` shows the hopper counts.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Hoppers work by themselves. |
| `/isadmin info <island>` | helper | Shows the island's hopper counts. |

**Test:**

1. Put 640 cobblestone in a chest with a hopper under it: a full stack moves each time (much faster than vanilla).
2. Build a vanilla item sorter: it no longer sorts (expected).
3. Place 65 hoppers in one chunk: staff online get one alert with a teleport. Place more: no spam.
4. `/isadmin info <island>`: the hopper counts.
5. Bedrock: staff get the alert as a popup with Teleport.

_Open points: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) → HOPPER._

## AFK pool

### AFK pool

A small area at spawn where standing still pays. Every 15 minutes inside gives money, island fuel, and sometimes a key or a sell booster. The longer you stay, the bigger the reward (up to 180% after 6 hours). Maximum 8 hours per account per day. One account per IP can earn at a time (staff can whitelist a family on one connection). There is no command. It opens when the afk_pool area exists. **Changed (VIS, M10.1, M10.2, M7.1):** players inside are **hidden from each other** (they can still message and trade); there is **no idle kick**: a player who can't earn (daily cap, or another account on the connection earns) is sent to their island after 60 s. Keys (1 h Ember, 3 h Ember, 6 h Cinder, 8 h Cinder) are now delivered to /crates. The sell booster is banked in the boosters module and starts when you leave. Fuel is banked and goes into the island tank when Island Fuel is on. Time in the pool never counts for playtime rewards or bank interest.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Stand in the pool. See the action bar and the AFK line on the spawn scoreboard |
| `/help afk` | player | Help page for the pool |

**Test:**

1. Ask staff to open the pool (add the afk_pool region or set the box in the config).
2. Stand inside and watch the action bar countdown and the scoreboard AFK line.
3. Wait 15 minutes. Check money arrives and the chat line.
4. Leave and return. The session bonus should reset; the daily total stays.
5. Use a second account on the same IP. It shows 'Paused: another account is earning', then "Back to your island in 60s" and is sent home (no kick).
6. Stay 1 hour: the first Ember key arrives in /crates (bound).
7. Check the sell booster starts only after you step out of the pool (see /boosters).
8. With a friend in the pool: you can't see each other, but /msg and /trade still work. Staff (not vanished) stay visible.
9. With Island Fuel on: the banked fuel arrives in your island tank.
10. On Bedrock check the action bar and whether two Bedrock players on one network are paused.

_Questions for this feature: 13 on the Decisions page (Feature = "AFK pool")._

