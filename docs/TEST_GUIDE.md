# Phoenix Skyblock · feature and test guide

_Written 30 October 2026 after update83. The server runs all of this now (26 modules)._

How to use this guide:
- Each area has its features. Every feature has: what it does, its commands, and what to test.
- Test on Java first, then on Bedrock where it says so.
- The questions for every feature are in the spreadsheet **Phoenix_Skyblock_questions.xlsx** (1,037 questions). Start with Priority = "before launch".
- Staff commands need the right rank. "admin" = only admins.

## What is new since you went to sleep

| Update | What |
|---|---|
| update79 | Armor enchants on wardrobe set pieces (E5): new Armor tab in `/enchanter`. |
| update80 | Monthly punishment report `/preport` and the automatic "Honorable" title (P10). The punishment system is now complete. |
| update81 | `/settings` toggles that work now: join messages, teleport requests, scoreboard, island invites, particles, menu sounds, event sounds (F1). Balance tuning for enchants and sets; combat book drops made rarer on the live server (TUNE). |
| update82 | The new V2 look: brighter colors, bold names, red `| Header` sections, green Buy / red Sell prices, bold `» CLICK` lines, new scoreboards (STYLE2). Resource pack plan written (docs/plans/RESOURCE_PACK.md). |
| update83 | `/sell` is now a sell chest; `/shop` is a quick-buy grid (left 1, right 64, shift = amount); satchel has a Sell chest button (SELL2). AFK pool module (closed until you place an `afk_pool` region). |

Still waiting: E6 (hoe and rod enchants) needs Phase 7 (farming and fishing), which has no plan yet. STORE2 needs island storage, which does not exist yet. Both are questions in the sheet.

## Test these first (most changed tonight)

1. `/sell`: put items in, Sell inventory, Sell, close with items inside (they come back), kill the server with items inside and rejoin.
1. `/shop`: left-click buys 1, right-click 64, shift-click opens the amount screen; buy 64 with only 40 free slots.
1. Look at tooltips: a forge tool, an enchant book, a set piece, a money note, shop items. Bold names, red `| Header` sections, bold yellow `» CLICK` lines.
1. Scoreboards on your island, at spawn and in The Depths: gradient title, `| Profile` / `| Island` sections.
1. `/settings`: switch off scoreboard (it disappears at once), menu sounds, teleport requests.
1. `/enchanter` → Armor tab: put a book on a set piece you own.
1. `/preport` (admin): last month's punishment report.
1. AFK pool: add the `afk_pool` region to a build (or the box in `afk.yml`), stand in it, watch the action bar.
1. Bedrock: open `/sell`, `/shop` (tap opens the amount screen), and check that all symbols show.

## Contents

- [Look & feel](#look--feel)
- [Profiles](#profiles)
- [Islands](#islands)
- [Social & chat](#social--chat)
- [Teleports & essentials](#teleports--essentials)
- [Ranks, perks & cosmetics](#ranks-perks--cosmetics)
- [Worlds & builds](#worlds--builds)
- [Staff & punishments](#staff--punishments)
- [Economy](#economy)
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

_Questions for this feature: 8 in the sheet (Feature = "Tab list and nametags")._

### Main menu (/menu), menu item and /help

/menu opens a hub with every feature as a button; features that do not exist yet are shown as 'Coming soon'. Every player has a Nether Star in hotbar slot 9 that opens the menu. /help explains the server with categories, topics and command lists, and can take you to places.

| Command | Who | What |
|---|---|---|
| `/menu (/gui)` | player | Open the main hub with every feature. |
| `/help [topic\|category\|commands] (/guide)` | player | Open the help menu or a topic. |
| `/staff (/staffmenu)` | helper | Staff hub: display editor, config editor, titles & tags, staff tools. |
| `/admin (/config)` | admin | In-game config editor for every module's settings (logged). |

**Test:**

1. Join with a fresh account. Check there is a Nether Star in slot 9. Right-click (Java) or tap (Bedrock) it: the menu opens.
2. Open /menu and click every button. Island, Depths, Shop, Sell, Bank, Auction, Wardrobe, Enchanter, Warps, Titles, Store, Vote, Discord, Forge, Profiles, Spawn, Rules, Settings, Help, Website should open. 'Coming soon' ones should be locked.
3. Try to drop or move the Nether Star: it should be protected.
4. Turn the menu item off in /settings. It disappears; turn it on and it returns.
5. Type /help. Open a topic, click 'Take me there' and 'Show in chat'. Check the command lists only show commands you may use.
6. As staff open /staff and /admin: change a harmless setting and check it appears in /logs under config.
7. Bedrock: open /menu with the item and check all icons and names are readable.

_Questions for this feature: 9 in the sheet (Feature = "Main menu (/menu), menu item and /help")._

### Personal settings (/settings)

Players can switch personal options in four groups (Chat, Social, Display, Sounds): private messages, mention sound, join messages, chat colours, timestamps, teleport requests, trade requests, island invites, AFK messages, scoreboard, menu item, particles, menu sounds and event sounds. Changes apply immediately.

| Command | Who | What |
|---|---|---|
| `/settings [setting] [on\|off\|toggle] (/options /prefs)` | player | Open the settings menu or change one setting by name. |

**Test:**

1. Open /settings. Turn off 'Scoreboard': it disappears at once. Turn it on again.
2. Turn off 'Private messages': another account's /msg to you is blocked. Staff can still reach you.
3. Turn off 'TPA requests', 'Trade requests' and 'Island invites' and test each from a second account.
4. Turn on 'Chat timestamps': times appear before chat lines.
5. Turn off 'Menu sounds' and 'Event sounds' and check menus are silent.
6. Switch to profile 2 and check the settings are separate per profile.
7. Type /settings scoreboard off (chat form) and use Tab to see all names.
8. Bedrock: open the menu and tap toggles.

_Questions for this feature: 8 in the sheet (Feature = "Personal settings (/settings)")._

### Server list MOTD and join flow

The server list shows an animated SKYBORNMC / PHOENIX SKYBLOCK message in two lines, with hover text about the status and versions. The server is whitelisted for testing. When a player joins they land at spawn, new players get a welcome title, a broadcast with their join number and welcome lines.

| Command | Who | What |
|---|---|---|
| `(no command)` | admin | MOTD is edited in motd.yml or with /admin. The whitelist is managed with the normal whitelist command. |

**Test:**

1. Add the server in Minecraft Java and Bedrock. Check the two MOTD lines, animated gradient and hover text.
2. Check the player count and version text. Check the icon.
3. Join with a new account (whitelisted): welcome title, broadcast 'Welcome <name> to Phoenix Skyblock (#N)', welcome lines with clickable /menu and /help.
4. Leave and join again: 'Welcome back' title.
5. Try a non-whitelisted account: you should see a clear message that the server is in testing.
6. Join with an old Java version (1.20) and a new one (1.21.5+): old gets a friendly kick message, new joins.
7. Check join/quit lines: only ranked players have them.

_Questions for this feature: 10 in the sheet (Feature = "Server list MOTD and join flow")._

### Scoreboards (sidebar)

Each player sees a sidebar that changes with where they are: spawn, their own island, someone else's island, The Depths (gate, mine, forest, combat) and the warzone. It shows name and rank, money, crystals, gems, island level and, on the island, the next to-do note. Players can turn the scoreboard off in /settings.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Turn the scoreboard on/off with /settings scoreboard. Layouts are edited in messages/hud.yml. |

**Test:**

1. Join and look at the scoreboard at spawn: Profile (name, rank, money, crystals, gems) and Island (level).
2. Go to your island: layout changes, and a 'Next:' note line appears when you have an open island note and the setting is on.
3. Visit someone else's island: 'Visiting: <island>' is shown.
4. Go to The Depths gate, mine, forest and combat zones: each has its own layout.
5. Teleport quickly between places: no flicker.
6. Switch profile: money, crystals and name change at once.
7. Turn the scoreboard off in /settings and on again.
8. Bedrock: check all lines fit, symbols are visible and nothing is cut at the edge.

_Questions for this feature: 9 in the sheet (Feature = "Scoreboards (sidebar)")._

### Resource pack plan

The plan adds a resource pack for Java and Bedrock with custom item looks, menu backgrounds, font icons, sounds, HUD bars, crate animations and a custom border. Everything works without the pack (vanilla fallback). It is optional at first. Nothing is built yet. This sheet only adds questions beyond the RP-D1 to RP-D17 list.

| Command | Who | What |
|---|---|---|
| `/settings > Resource pack` | player | Planned toggle to turn the pack on or off |
| `/skyblock reload` | admin | Planned: push a new pack version to players |

**Test:**

1. Nothing to test in game yet (planning only).
2. Read docs/plans/RESOURCE_PACK.md section 0 and 17 and answer RP-D1 to RP-D17.
3. Look at the UI sketches in the design artifact and decide the art direction.
4. Check that the plan says every menu stays usable with the pack off, and tell the developer if you disagree.
5. List which art you can make yourself and which you need to buy (RP-D7).

_Questions for this feature: 10 in the sheet (Feature = "Resource pack plan")._

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

_Questions for this feature: 10 in the sheet (Feature = "Look and feel V2 (colors, lore, menus, scoreboard)")._

## Profiles

### Profiles (up to 3 islands per player)

Every player can have up to three profiles. A profile is one island plus its own money, crystals, items, ender chest, skills and progress. Gems, rank, titles and cosmetics are shared between all profiles. You switch with /profiles, and nothing can ever be moved between your own profiles.

| Command | Who | What |
|---|---|---|
| `/profiles (/profile)` | player | Open the profile menu: switch, create, rename, change icon, buy slot 2 or 3, delete a profile. |

**Test:**

1. Type /profiles. Profile 1 is yours and active. Slots 2 and 3 are locked and show a price (500 and 1,500 gems) or the rank needed.
2. Ask staff to give you gems, then buy slot 2. Check the gems go down only once and the slot opens.
3. Click the empty slot and create a profile. You land at spawn with an empty inventory, $0 and no island. Gems and rank stay the same.
4. Run /is on profile 2 and make an island, then put a stone block in your inventory. Switch back to profile 1: your old items, money and island home are back and you land at your old island.
5. Rename the profile (you type the new name in chat) and change its icon. Relog right after a switch: you must come back on the same profile.
6. Try to cheat on purpose: drop an item on profile 1, switch to profile 2 and try to pick it up. It must be refused. Also try to cash a money note made on the other profile and to buy your own other profile's /ah listing. All must be refused.
7. Try to visit or join the island of your other profile (use an alt to invite it). It must be refused.
8. Delete profile 2: you get a confirm screen and must type the profile name. You cannot delete the profile you are playing on.
9. Bedrock: do all of the above by tapping. Check that renaming (typing in chat) works on a phone.

_Questions for this feature: 14 in the sheet (Feature = "Profiles (up to 3 islands per player)")._

## Islands

### Island creation, starter island and homes

A new player lands at spawn and types /is to create an island. The starter island is built for them with a starter chest, and they are sent there. Once you have an island, /is opens the island menu and /is home takes you back. Leaders can move the island home and every member gets one personal home.

| Command | Who | What |
|---|---|---|
| `/is` | player | No island: open the 'Create your island' screen. With an island: open the island menu. |
| `/is home [home name]` | player | Teleport to the island home, or to one of your personal homes. |
| `/is go` | player | Same as /is home. |
| `/is sethome` | player | Set the island home where you stand (Leader, Admin and Mod). |
| `/is setmyhome [name]` | player | Set your own personal home on the island (1 per member). Same name again moves it. |

**Test:**

1. Use a fresh account. Join, then type /is. You should see a 'Create your island' confirm screen. Confirm.
2. You get a title and sound, the starter island appears under you and a starter chest is on it. Open the chest and check the contents.
3. Type /spawn, then /is home. You should land on the island home.
4. Stand somewhere else on the island and type /is sethome, then /is home from spawn. You land at the new spot. Try to set a home outside the island border or in the air: refused.
5. Type /is setmyhome base, walk away, then /is home base. You return to your personal home.
6. Jump off the island into the void. You should land back on your island home without damage.
7. Die (ask staff to /kill you). Check where you respawn and that you keep your inventory.
8. Bedrock: do the create screen and the island menu by tapping; check the title and the chest are readable.

_Questions for this feature: 10 in the sheet (Feature = "Island creation, starter island and homes")._

### Island team: invites, roles, coop and permissions

Leaders can invite players to form a team island (4 members including the leader, 8 with upgrades). Roles are Leader, Admin, Mod, Member and Coop (temporary trust). A permission screen lets the leader decide what each role may do (break, place, containers, spawners and more). Members can leave and the leader can transfer the island.

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

**Test:**

1. With two accounts: account A has an island. A types /is invite B. A gets a warning that the island becomes a team island forever. Confirm.
2. B types /is (or clicks the invite). B accepts. Both see messages. Check B appears in /is members with role Member.
3. Open the permissions screen from /is members. As A, turn off Break for Member. B should no longer be able to break blocks on the island.
4. A promotes B to Mod, then to Admin. Check what B can now do (invite, kick a Member, change settings).
5. A uses /is transfer B. B becomes Leader and A becomes Admin.
6. Coop: A types /is coop on a third account C. C can build; when A and all Mods log out, C is no longer trusted.
7. B types /is leave and confirms. Then try to join another island within 24 hours: it must be refused (join cooldown). A fourth join in a season is also refused.
8. Try to invite a fifth player on a 4-member island: refused (island full).
9. Bedrock: open /is members, tap a head, check the action buttons are readable.

_Questions for this feature: 11 in the sheet (Feature = "Island team: invites, roles, coop and permissions")._

### Visiting: browser, lock, ban, ratings and likes

Players can visit other islands from a browser (most visited, top rated, most liked, newest) or by name. Leaders, Admins and Mods can lock the island, send visitors away and ban players. Visitors can rate an island 1 to 5 stars once per season and like it once a day.

| Command | Who | What |
|---|---|---|
| `/is visit [player]` | player | Open the visit browser, or go to a player's island (3 second warmup). |
| `/is lock / /is unlock` | player | Leader/Admin/Mod: only members and coop players can enter a locked island. |
| `/is expel <player>` | player | Send a visitor on your island to spawn. |
| `/is ban <player> / /is unban <player>` | player | Ban or unban a visitor from entering your island (max 100). |
| `/is rate <player> <1-5>` | player | Rate an island you have visited. |
| `/is like <player>` | player | Like an island you have visited (once a day). |
| `/is info [player]` | player | Show info about your island or another player's island in chat. |

**Test:**

1. With two accounts: B types /is visit and picks A's island in the browser (or /is visit A). Stand still for 3 seconds. B lands on A's island and A sees a visitor message.
2. As B try to break a block. It must be refused (visitors can only look by default).
3. A types /is lock. B (on the island) is sent to spawn. B tries /is visit A again: refused. A types /is unlock.
4. A bans B (/is ban B). B cannot enter by /is visit, /tpa, ender pearl or by relogging on the island. A unbans B.
5. B types /is rate A 5 and /is like A. Try /is like A again the same day: refused. Try to rate your own island: refused.
6. Try /is rate on an island you never visited: refused.
7. Open the browser and check the four lists. Locked islands should not show up.
8. Bedrock: open the browser and tap an island. Check the 3 second warmup message and that moving cancels it.

_Questions for this feature: 12 in the sheet (Feature = "Visiting: browser, lock, ban, ratings and likes")._

### Island warps and warp signs

Every island gets 3 warps (up to 10 with a later upgrade) in five categories: shops, farms, parkour, builds, other. Warps can be public or private and have an icon. Players can make a warp by writing [warp] on a sign. Other players find warps in the warp browser.

| Command | Who | What |
|---|---|---|
| `/is setwarp <name> [category]` | player | Leader/Admin/Mod: set a warp where you stand. |
| `/is delwarp <name>` | player | Delete one of your island's warps. |
| `/is warp [player] [name]` | player | Open the warp browser or go to a warp (3 second warmup). |

**Test:**

1. On your island type /is setwarp farm farms. Check it appears in /is warp under Your warps.
2. Open the warp, change the icon, make it public and private. Check as another player that private warps are hidden.
3. As another player: /is warp, pick a category and use the warp. You wait 3 seconds, then arrive.
4. Place a sign, write [warp] on line 1 and a name on line 2. The sign turns into a warp sign and uses one slot.
5. Try a 4th warp: refused. Break the sign: the warp is removed.
6. Set a warp with the same name again: it moves instead of making a second one.
7. Bedrock: open the browser and tap warps. Also try the [warp] sign on a phone.

_Questions for this feature: 9 in the sheet (Feature = "Island warps and warp signs")._

### Island settings, weather, time and island fly

Each island has settings the leader (and anyone allowed) can change: mob spawning, animal spawning, fire spread, explosions, leaf decay, crop trampling, visitor item pickup, visitor alerts, visitor fly, weather and time of day. Weather and time are shown only to players on that island. Island fly lets people fly inside the island border when the island or player has the right.

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

_Questions for this feature: 10 in the sheet (Feature = "Island settings, weather, time and island fly")._

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

_Questions for this feature: 9 in the sheet (Feature = "Island notes (shared to-do list)")._

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

_Questions for this feature: 8 in the sheet (Feature = "Island stats")._

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

_Questions for this feature: 9 in the sheet (Feature = "Island chat")._

### Disband, remove and staff island tools

The leader can disband the island (confirm and type the word 'disband'). The island is wiped and its space is reused. Staff have /isadmin tools to look up, teleport, delete, reset, resize, add members and change the owner.

| Command | Who | What |
|---|---|---|
| `/is disband` | player | Leader only: remove the island for good after a confirm screen and typing 'disband'. |
| `/is bypass` | admin | Staff: ignore island protection for this session (logged). |
| `/isadmin info <island>` | helper | Look up an island: owner, members, size, slot, created. |
| `/isadmin tp <island>` | helper | Teleport to an island. |
| `/isadmin delete <island>` | admin | Remove an island for good. |
| `/isadmin reset <island>` | admin | Remove an island and give its leader a fresh one. |
| `/isadmin setsize <island> <size>` | admin | Set the border width in chunks. |
| `/isadmin kick <player>` | admin | Remove a member or coop player from an island. |
| `/isadmin slots` | admin | Grid overview of all island slots. |
| `/isadmin teleport-slot <slot>` | admin | Teleport to a slot's center. |

**Test:**

1. On a test island put a chest with an item and some money in the island bank. Type /is disband. Read the confirm screen, type disband. The island is wiped and you can create a new one at once (no cooldown).
2. Check the leader gets the island bank money.
3. As staff use /isadmin info on your friend's island. Teleport with /isadmin tp.
4. As admin run /isadmin setsize <island> 5. The border changes.
5. Run /isadmin reset on a test island. The leader gets a new starter island, other members are removed.
6. Run /isadmin slots to see the grid. Check cleared slots become free again and are reused.
7. Check /logs island for all of these actions.

_Questions for this feature: 7 in the sheet (Feature = "Disband, remove and staff island tools")._

### Island level, pillars and upgrades (planned, not built yet)

Island level, the five pillars (Mining, Foraging, Farming, Combat, Conquest) and the upgrades menu (/is upgrade) are the next big island phase. Buttons for them already show 'Coming soon' in the island menu. This page only collects the decisions that are still open before the build starts.

| Command | Who | What |
|---|---|---|
| `/is upgrade (planned, same as /is masteries)` | player | Planned: island upgrades with tasks and money costs. Not available yet. |

**Test:**

1. Open /is: check Upgrades, Bank and Level show as coming soon or '-' where they are not built.
2. Check the scoreboard island line shows 'Level: -' for now.
3. Check /is info shows 'Level (Phase 5)' or a dash instead of a number.
4. Check the mastery names (Timber, Swift Hands, Warrior, Deep Pockets, Market Contacts, Crystal Rush, Lucky Strike, Fuel Efficiency, Beast Tamer, Angler, Homestead, Wings) are listed in the plan and read well to you.

_Questions for this feature: 9 in the sheet (Feature = "Island level, pillars and upgrades (planned, not built yet)")._

## Social & chat

### Social: private messages, ignore, AFK and info commands

Players can send private messages, reply, ignore someone's private messages, go AFK, see who is online by rank and look up playtime, ping and last seen. There are clickable link commands (/discord, /store, /vote, /website) and /rules. Staff can read private messages (social spy), look up a player with /whois and send server announcements.

| Command | Who | What |
|---|---|---|
| `/msg <player> <message> (/w /tell /m /pm)` | player | Send a private message (clickable names, rate limited, logged 30 days). |
| `/r <message> (/reply)` | player | Reply to the last private message. |
| `/ignore <player> / /ignore list / /unignore <player>` | player | Block (or unblock) private messages from a player. Staff cannot be ignored. |
| `/afk` | player | Toggle AFK. It also turns on by itself after 5 minutes without activity. |
| `/list (/online /who)` | player | Online players grouped by rank. |
| `/ping [player]` | player | Connection delay. |
| `/playtime [player] (/pt)` | player | Total play time. |
| `/seen <player>` | player | When a player was last online and first joined. |
| `/discord /store /vote /website /rules` | player | Clickable links and the server rules. |
| `/socialspy` | helper | See everyone's private messages. |
| `/whois <player>` | helper | UUID, rank, platform, times, location, names. IP and alt accounts for mods. |
| `/broadcast <message> (/bc /announce)` | admin | Server announcement with title and sound. |

**Test:**

1. With two accounts: A types /msg B hello. B sees a clickable name and answers with /r hi.
2. Send 6 messages in a row quickly. The rate limit should slow you down.
3. B types /ignore A. A tries /msg B: blocked. Check that a staff member can still message B.
4. Stand still 5 minutes (or use /afk). The tab list shows [AFK] and a chat line says you are AFK. Move to come back.
5. Type /list, /ping, /playtime, /seen <name>. Check a vanished staff member does not appear in /list.
6. Type /rules, /discord, /store, /vote, /website and click the links. Check the web addresses are the right ones.
7. As staff turn on /socialspy and send a private message with the other accounts. You should see it.
8. As admin: /broadcast <gradient>Hello</gradient>. Everyone sees a chat line, title and sound.
9. Bedrock: click a name in chat to reply, and use /rules links. Check clickable text works on a phone.

_Questions for this feature: 15 in the sheet (Feature = "Social: private messages, ignore, AFK and info commands")._

### Chat: format, filters, mentions and staff chat

Public chat shows title, rank, name, tag and message. The chat filter blocks spam, repeats, too many capital letters, links, IP addresses and phone numbers. Players can show their held item with [item] and are highlighted when someone mentions them. Staff have staff chat and chat controls (clear, lock, slow mode).

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
9. Bedrock: send chat and check hover and clicks. Check that [item] works from a phone.

_Questions for this feature: 15 in the sheet (Feature = "Chat: format, filters, mentions and staff chat")._

## Teleports & essentials

### Teleports: /spawn and /tpa

Players can go to spawn at once and can ask other players to teleport to them (or ask to teleport to someone). Requests expire after 60 seconds and teleports need the player to stand still for 3 seconds. All warmups and cooldowns are set in one file (teleport.yml).

| Command | Who | What |
|---|---|---|
| `/spawn` | player | Teleport to spawn (instant, 5 second cooldown). |
| `/tpa <player>` | player | Ask to teleport to a player. |
| `/tpahere <player>` | player | Ask a player to teleport to you. |
| `/tpaccept [player] (/tpyes)` | player | Accept a request. |
| `/tpdeny [player] (/tpno)` | player | Decline a request. |
| `/tpcancel` | player | Cancel your own request. |

**Test:**

1. Type /spawn from your island. You land at spawn at once. Type it again right away: 5 second cooldown.
2. With two accounts: A types /tpa B. B sees clickable [Accept] [Deny]. B clicks Accept. A waits 3 seconds without moving and arrives.
3. Repeat and move more than 1 block during the 3 seconds: the teleport is cancelled. Do it again but take damage: cancelled.
4. Send /tpa and wait 60 seconds: it expires. Send many requests quickly: the 15 second request cooldown stops you.
5. Turn off 'TPA requests' in B's /settings. A's request is refused.
6. Lock B's island or ban A from it, then /tpa B while B is on it: refused.
7. Try /tpa to a player in The Depths and in the warzone (when it exists). Check what happens.
8. Bedrock: tap [Accept] in chat, or use /tpaccept if tapping does not work.

_Questions for this feature: 9 in the sheet (Feature = "Teleports: /spawn and /tpa")._

### Staff essentials (teleport, game mode, fly, vanish, inventory, items)

Staff get tools to move around and help: teleport to players or coordinates, set game mode and speed, fly, heal and feed, vanish, look in (and with admin, edit) inventories and ender chests, give vanilla items, set time and weather, and keep up to 5 staff homes. Every staff action is written to the log.

| Command | Who | What |
|---|---|---|
| `/tp <player> [target]` | helper | Teleport to a player (mods can teleport others). |
| `/tphere <player>` | mod | Bring a player to you. |
| `/tppos <x> <y> <z> [world]` | builder | Teleport to coordinates. |
| `/top` | builder | Go to the highest block above you. |
| `/back` | mod | Return to the previous location or death point. |
| `/home [name] /sethome [name] /delhome <name>` | mod | Staff homes (max 5). |
| `/gm <c\|s\|sp\|a> [player] (/gmc /gms /gmsp /gma)` | mod | Game mode (builders: creative and survival only). |
| `/fly [player] /speed <1-10> [fly\|walk]` | builder | Fly and speed. |
| `/god [player] /heal [player] /feed [player]` | mod | No damage and hunger; heal; feed. |
| `/clear [player] (/ci)` | mod | Clear an inventory (confirm for others, contents logged). |
| `/invsee <player> /ecsee <player>` | helper | View inventory or ender chest. Admins can edit (every change logged). |
| `/vanish [player] (/v)` | helper | Become invisible, silent join/quit, stays after rejoin. |
| `/i <item> [amount] /give <player> <item> [amount]` | builder | Give vanilla items (max 2,304). |
| `/time <day\|noon\|night\|midnight\|ticks> [world] /weather <clear\|rain\|thunder> [world]` | builder | Set time and weather. |
| `/skull <player> /more` | admin | Player head; fill the held stack. |
| `/build` | builder | Turn on build mode in protected worlds such as spawn. |
| `/setspawn` | admin | Set the spawn point where you stand. |

**Test:**

1. As helper: /vanish, then check /list and tab do not show you. Relog: you are still vanished. Check no join/quit message.
2. As helper: /tp to a player, /invsee and /ecsee (view only). Try to move an item: refused. As admin: edit and check every change appears in /logs.
3. As mod: /gm sp, /fly, /heal, /feed, /god. Check each has its permission (builders only get creative/survival).
4. As mod: /clear <player>: you must repeat the command within 10 seconds to confirm.
5. As builder: /i diamond 64 and /give. Then check /logs staff shows what you gave.
6. As admin: /time noon and /weather clear in the spawn world. Check the result (islands use their own per-island weather and time).
7. As staff: /sethome base, /home base, /delhome base. Try to set a 6th: refused.
8. As builder: /build in spawn lets you break blocks; without it you cannot.

_Questions for this feature: 10 in the sheet (Feature = "Staff essentials (teleport, game mode, fly, vanish, inventory, items)")._

## Ranks, perks & cosmetics

### Titles and tags

A title is earned (achievements, events, first join) and shows before your rank. A tag is owned (crates, store) and shows after your name in small brackets. You wear one title and one tag at a time. Tags can be turned into tradeable items. Staff can edit the whole list in game.

| Command | Who | What |
|---|---|---|
| `/tags (/tag) and /titles (/title)` | player | One menu with two tabs: equip a title and a tag, see how to earn locked ones, preview your chat line. Achievements button. |
| `/tags give\|take <player> <tag> and /titles give\|take <player> <title>` | mod | Give or remove a tag or title (logged). |
| `/tags item <tag> [amount] [player]` | admin | Make unique, tradeable tag items (right-click to redeem). |
| `/display (/displayformat)` | admin | Edit chat, tab and nametag formats with a live preview. |

**Test:**

1. Join with a new account. You should have the title 'Newcomer'. Open /tags and look at both tabs.
2. Equip a title and a tag. Check chat and tab show them. Take them off.
3. Open a locked title: it shows how to earn it. A secret title shows as ???.
4. As admin: /tags item phoenix 1 <player>. The player right-clicks the item, confirms, and the tag is theirs. Try to sell the item on /ah before redeeming.
5. As mod: /titles give <player> founder, then /titles take. Check it is in /logs.
6. Try to give the automatic title 'honorable' to someone: refused.
7. As admin click Edit in /tags, create a new tag, edit its colors and delete it.
8. As admin open /display and change the chat format with the live preview.
9. Bedrock: open /tags and tap through both tabs; check gradient names are readable.

_Questions for this feature: 12 in the sheet (Feature = "Titles and tags")._

### Achievements

There are 30 achievements in six groups (skills, island, combat, economy, gear, exploring) with tiers; four are secret. Progress is saved per profile. The last tier gives a title that every profile of your account can use. You get a screen title, chat line and sound when you unlock a tier.

| Command | Who | What |
|---|---|---|
| `/tags -> Achievements button (or /help achievements)` | player | Open the achievements menu (there is no separate /achievements command yet). |

**Test:**

1. Open /tags and click Achievements. Check six categories with progress bars.
2. Play for 10 minutes: 'First Steps' unlocks with a screen title, chat line and sound.
3. Sell something with /sell: 'First Sale' unlocks.
4. Check a secret achievement shows ??? with a grey icon.
5. Switch to profile 2: progress starts at zero. Switch back: it returns.
6. Finish the last tier of an achievement with a reward title (ask staff to set your skill level). The title appears in /titles and is usable on profile 2 as well.
7. Join for the first time after an update: you should see one toast with up to 3 lines and '+N more'.
8. Bedrock: check progress bars and text fit on a phone.

_Questions for this feature: 11 in the sheet (Feature = "Achievements")._

### Rank perks: /craft, /ec, /hat, /nick, /trash and staff tools

Donor ranks will get small convenience commands: crafting table anywhere, ender chest anywhere, wear an item as a hat, a custom nickname (with ~ marker), and a trash menu. Staff also have /repair and /realname, and the owner has /sudo. Right now only staff and ops have them until the donor ranks exist.

| Command | Who | What |
|---|---|---|
| `/craft (/workbench /wb)` | donor | Open a crafting table anywhere. |
| `/ec (/enderchest)` | donor | Open your ender chest anywhere. |
| `/hat` | donor | Wear the held item as a hat. |
| `/nick <nickname\|off>` | donor | Set or remove a nickname (3-16 characters, letters/numbers/_, shown with ~). |
| `/trash (/disposal)` | donor | Menu where items you leave are destroyed on close. |
| `/setnick <player> <nickname\|off>` | mod | Set or remove another player's nickname. |
| `/realname <nickname> (/whonick)` | helper | Find the real account behind a nickname. |
| `/repair [hand\|all] [player] (/fix)` | mod | Repair vanilla item durability. |
| `/sudo <player> <command\|c:message>` | admin | Owner only: run a command or chat as a player (audited). |

**Test:**

1. Give yourself the perk with LuckPerms (for example lp user <you> permission set skyblock.perks.craft true) and test /craft, /ec, /hat, /trash.
2. /nick Phoenix2: you get a ~ in front, the tab and chat show it, and /realname Phoenix2 shows your real name to staff.
3. Try a reserved nickname (admin, owner, staff), another player's name, and a nickname within 60 seconds of the last change. All refused.
4. Remove the nick permission: the nickname is reset when the rank that allowed it is lost.
5. Put a custom item (a tool or enchant book) in /trash and close it: check the warning in chat and that the item is gone and logged.
6. As mod: /repair hand on a damaged vanilla pickaxe. Custom tools have no durability.
7. As owner: /sudo <player> c:hello. Check the audit log.
8. Bedrock: open /trash and /ec and check taps work.

_Questions for this feature: 11 in the sheet (Feature = "Rank perks: /craft, /ec, /hat, /nick, /trash and staff tools")._

### Ranks, the free Ember rank and staff ranks

Staff ranks exist now: Builder, Helper, Moderator, Admin, Owner, with colors and prefixes. A free rank called Ember can be claimed with /freerank after 30 minutes of total playtime and gives a colored name and a choice of one of three tags. Five paid donor ranks (Squire, Knight, Paladin, Warlord, Monarch) are planned with the store.

| Command | Who | What |
|---|---|---|
| `/freerank` | player | After 30 minutes of playtime claim the free Ember rank once and pick 1 of 3 tags. |
| `lp user <name> parent add <rank> (console / owner)` | admin | Staff ranks are handed out by the owner in LuckPerms; there is no in-game rank command. |

**Test:**

1. Use a new account. Type /freerank before 30 minutes: you get a message with the time left.
2. After 30 minutes (or ask staff to raise your playtime) type /freerank. You get the Ember rank and a menu with three tags (ember, spark, ash). Pick one.
3. Type /freerank again: 'already claimed'. Relog: rank and tag stay.
4. Check chat and tab show the [Ember] prefix in its color. Check you do not get a join message (only ranks with that permission do).
5. Ask the owner to give a test account the helper rank: check prefix, color, staff chat and tab sorting.
6. Check /profiles shows slot 2 locked until you have 500 gems or Knight (when donor ranks exist).

_Questions for this feature: 11 in the sheet (Feature = "Ranks, the free Ember rank and staff ranks")._

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

_Questions for this feature: 13 in the sheet (Feature = "Worlds, spawn and builds")._

## Staff & punishments

### Staff mode, freeze, inspect and player reports

Staff mode swaps a staff member's inventory for moderation tools (inspect, freeze, random player, logs, vanish, punish, reports). Freeze holds a player for a check: they cannot move, build, use commands or take damage, and their chat goes only to staff. Players can report others with /report, and staff claim, handle and close reports.

| Command | Who | What |
|---|---|---|
| `/staffmode (/s /sm /mod)` | helper | Turn staff mode on or off. |
| `/freeze <player> (/ss)  /unfreeze <player>` | helper | Freeze or unfreeze a player. |
| `/inspect <player> (/check)` | helper | Open a player card: rank, playtime, platform, ping, location, inventory, ender chest, punish, logs. |
| `/report <player> [reason]` | player | Report a player with a reason picker or typed text. |
| `/reports [open\|claimed\|closed]  /reports claim\|unclaim\|close <id> <outcome>` | helper | Report list, claim and close with an outcome. |

**Test:**

1. As helper: /staffmode. Your inventory is swapped for the staff hotbar and you are vanished with night vision. Leave and check your items return exactly.
2. In staff mode right-click a player with the blaze rod (Inspect) and with the breeze rod (Punish). On Bedrock tap the player.
3. Freeze a test account: it cannot move, build, use commands, take damage or chat publicly; staff see its chat. Relog while frozen: still frozen. Unfreeze: 10 seconds without fall damage.
4. Ban the frozen account: the freeze is cleared.
5. As a normal player: /report <name> hacking. Pick a reason. Report again within 60 seconds: refused. Make 4 open reports: the 4th is refused.
6. As staff: /reports, claim, close with 'punished' or 'invalid'. The reporter is told the result (also later on join).
7. Close 3 reports from the same player as 'invalid': staff get a suggestion for the report-abuse reason.
8. Crash your client while in staff mode (kill the game) and join again: your inventory is restored.

_Questions for this feature: 11 in the sheet (Feature = "Staff mode, freeze, inspect and player reports")._

### Punishments (/p menu, ladders and warnings)

Staff open one menu with /p <player> and click a reason. Each reason has a ladder (for example warn, mute 1h, mute 1d). The system picks the next step from the player's history, keeps a warning counter, shows a 2-minute [Undo], protects against double punishing, saves chat evidence, and asks an admin to approve big punishments. Players get a clear ban or mute screen with an ID and a link to appeal.

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
6. As the punished test account: read the ban screen (ID, appeal link), then /mypunishments and /ack.
7. As staff: /history <player> and click an entry to see the evidence lines. IPs must not show.
8. Try to punish an admin: refused (exempt).
9. Bedrock: open /p with a tap and check slots, icons and Undo on the head.

_Questions for this feature: 10 in the sheet (Feature = "Punishments (/p menu, ladders and warnings)")._

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
2. Start /lockdown chat 5m. A brand-new account cannot chat or /msg; an older or ranked account can. /lockdown off ends it.
3. Start /lockdown join 2m: an account that never joined cannot join (friendly 'server is busy' screen), returning players can.
4. /watch <player> reason, then have that player join: staff see one line. The player is never told. /watchlist shows the list.
5. /pnote <player> text; /pnote <player> shows notes with [Remove].
6. /pbulk spam <player1> <player2>: a confirm screen with each player's step, then [Undo all].
7. As helper punish 6 players in 5 minutes: the guard blocks you after the limit. An admin runs /pguard allow <name>.
8. As admin: /prollback <staff> 1h shows a preview; confirm to undo.
9. Check /preport lists last month with no player names.

_Questions for this feature: 8 in the sheet (Feature = "Punishment safeguards: appeals, lockdown, watchlist, guard and reports")._

### Staff logs (/logs)

Everything players and staff do is written to a log: chat, commands, money, trades, auctions, island actions, item life, punishments, staff actions and logins. Staff search it with /logs by player, time, category, near me, island or item, and can click a chat line to punish. Admins can export to CSV.

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
6. Check that a helper cannot see IPs or economy lines, and a mod cannot see staff logs.
7. Bedrock: open the viewer by tap, or use the chat output (/logs page).

_Questions for this feature: 9 in the sheet (Feature = "Staff logs (/logs)")._

## Economy

### Balances, /pay and /baltop

Every player has four kinds of money: money ($), island crystals, gems and shards. Players can check balances, pay each other (money and shards only), see the richest list and read their own history of every change. Money, crystals and shards belong to the active profile. Gems belong to the player. New accounts need 30 minutes of play before they can pay.

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
3. With a second account that has played less than 30 minutes, try /pay. It should be refused with the reason.
4. With an old account type /pay <friend> 500. Check both sides get a message and /bal changes.
5. Type /pay <friend> 2M. A confirm screen should appear. Click confirm, then repeat and let it time out (30 s).
6. Try /pay <friend> 5 (below the $10 minimum), /pay yourself, and /pay with more than you own. Each should give a clear error.
7. Open /transactions and filter by Pay, then by 24h. Your payments should be listed.
8. Type /baltop and /baltop shards. Then /eco give <you> money 1000 test as admin and wait 30 s for the list to move.
9. Do the same on a Bedrock phone: type amounts like 1.5k and use the confirm screen by tapping.

_Questions for this feature: 15 in the sheet (Feature = "Balances, /pay and /baltop")._

### Money notes (/withdraw)

A player can turn money into a paper note item and give it to someone else, or cash it later by right-clicking. Each note is one entry in the database, so copies are caught and taken away. Notes are money only, between $100 and $1T, and not allowed in the auction house.

| Command | Who | What |
|---|---|---|
| `/withdraw <amount> [currency]` | player | Turn money into a note item (needs a free slot and 30 minutes of playtime) |
| `Right-click a note` | player | Cash the note back into your purse |
| `/eco note <player> <amount> <reason>` | admin | Staff: give an online player a note without taking money |

**Test:**

1. Type /withdraw 5000. Check your money drops by $5,000 and a note item appears with the amount and date.
2. Right-click the note. Check the money comes back and the note disappears.
3. Try /withdraw 50 (under $100) and /withdraw twice within 10 seconds. Both should be refused clearly.
4. Fill your inventory and try /withdraw. It must refuse before taking any money.
5. Give a note to a second account and let them cash it.
6. As admin, try to get a duplicate note (staff give command / creative copy). The copy should be taken and staff alerted.
7. Drop a note in lava or let it burn if you can. See what happens to the money.
8. Try to craft with a note, sell it with /sell, and list it in /ah. All should be refused.
9. Check /transactions shows 'Note' entries for both withdraw and cash.

_Questions for this feature: 12 in the sheet (Feature = "Money notes (/withdraw)")._

### Bank (personal and island)

Each profile has a personal bank with 10 tiers. Each island has its own bank that the team shares. Banks keep money safe and pay interest every 8 hours. Taking money out costs a 2% fee (island masteries will lower it), except right after a payout. The island leader decides who may deposit, withdraw and see history.

| Command | Who | What |
|---|---|---|
| `/bank` | player | Open both banks: balance, deposit, withdraw, tiers, interest, history |
| `Island menu > Bank` | player | Same screen from the island menu |

**Test:**

1. Type /bank. Check you see a personal bank (tier 1, $100k) and the island bank side by side.
2. Deposit $500. Check purse goes down and bank up. Try depositing more than the free room.
3. Withdraw $100. The confirm screen should show the 2% fee and what you get.
4. Buy tier 2 ($10k). Check the capacity and the payout cap change.
5. Wait for the next payout time (every 8 hours from midnight UTC: 01:00, 09:00, 17:00 Oslo winter time) or ask staff to shorten it. Check the interest line in history and that money arrived.
6. Withdraw within 10 minutes after a payout. It should be free.
7. As leader, open /is permissions and remove Bank deposit from Member. Test with an alt member.
8. Try to deposit into another own profile's bank. It must be refused.
9. Check the island bank daily limits: Member can only take 10% of capacity per day.
10. Test on Bedrock: the number picker and confirm screens by tapping.

_Questions for this feature: 14 in the sheet (Feature = "Bank (personal and island)")._

### Interest (bank payouts)

Every 8 hours the server pays interest on the lowest balance each bank had during that period. Interest is paid even while players are offline, but only if the profile played at least 2 hours in the last 7 days. Each tier caps how much one payout can be. The interest is new money, so it is also watched on the dashboard.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Interest is automatic. See rate, cap and next payout in /bank |

**Test:**

1. Put money in the personal bank and check /bank shows the rate, the cap and the time of the next payout.
2. Deposit just before a payout. Check you only get interest on the amount that was there during the whole period.
3. Check an account that has played less than 2 hours in 7 days earns nothing (staff can lower playtime in the database or use an alt).
4. Stay offline over a payout and log in. The interest should be waiting in the bank history.
5. Hit the payout cap: put more than the cap would pay on and check the payout stops at the cap.
6. Withdraw with a fee and check that period's interest is forfeited.
7. Open /eco admin as staff and check the interest line on the dashboard.

_Questions for this feature: 10 in the sheet (Feature = "Interest (bank payouts)")._

### Sell chest (/sell)

A chest menu where players drop items and press Sell. It shows the live total, the player's sell bonus and what they will get. There is also a button to sell everything sellable in the inventory. Selling is also reachable from the satchel and the shop. /sell hand and /sell all are quick versions. Items not on the price list cannot be sold.

| Command | Who | What |
|---|---|---|
| `/sell` | player | Open the sell chest |
| `/sell hand` | player | Sell the stack in your hand (2 s cooldown) |
| `/sell all` | player | Sell every sellable stack in hotbar and inventory (2 s cooldown) |
| `Sell inventory (button)` | player | Moves all sellable stacks into the chest and sells |

**Test:**

1. Type /sell. Put cobblestone in the chest. The total should update every second.
2. Click the green Sell button. Check money and the sale line in chat or action bar.
3. Close the chest with items in it. They must all come back to the inventory.
4. Press Sell inventory with a pickaxe and a sword in the hotbar. They must stay.
5. Try to sell a tool, an enchant book, a money note and a wardrobe piece. All must be refused.
6. Sell a big stack worth over $100,000. A chat line with a hover breakdown should appear.
7. Use /sell hand with 64 wheat, then /sell all with mixed items.
8. Open /sell and run /profiles switch. Items must come back, never lost.
9. Try shift-click, drag, number keys and double-click in the chest on Java. Check nothing duplicates.
10. On Bedrock, tap items into the chest and press Sell.

_Questions for this feature: 11 in the sheet (Feature = "Sell chest (/sell)")._

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

_Questions for this feature: 10 in the sheet (Feature = "Dynamic sell prices, Hot Item and the +300% cap")._

### Shop quick-buy (/shop)

A builder shop with 8 categories and 332 items: blocks, wood, glass, decoration, redstone, seeds, utility. Left-click buys 1, right-click buys a stack, shift-click opens an amount screen. Bedrock taps open an amount screen. The shop never buys items back. Every item costs more than it can ever sell for.

| Command | Who | What |
|---|---|---|
| `/shop [category]` | player | Open the shop or one category (building-blocks, wood, stone-bricks, glass-colors, decoration, redstone, farming, utility) |
| `Sell (button)` | player | Opens the sell chest from inside the shop |

**Test:**

1. Type /shop. Check 8 categories and your balance at the top.
2. Left-click an item to buy 1. Check the action bar line and the price.
3. Right-click to buy 64. Check the amount and cost.
4. Shift-click and use the amount screen (+1, +16, +64). Buy 100.
5. Try buying with too little money. Check the message.
6. Fill your inventory and buy. Nothing should be taken if there is no room.
7. Buy an item costing $100,000 or more. A confirm screen should appear.
8. Use /shop wood to jump straight to a category.
9. On Bedrock, tap an item and use the amount screen.
10. Try to sell something to the shop. It must say the shop does not buy.

_Questions for this feature: 12 in the sheet (Feature = "Shop quick-buy (/shop)")._

### Auction house (/ah)

Players list items at a fixed price. Buyers browse by category, search and sort. The seller pays a 1% fee when listing and 5% tax when the item sells. Players get 5 listings (more with ranks). Money goes to the seller even when offline. Expired or cancelled items wait in a collection bin. Staff can remove listings.

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
10. Test on Bedrock: search prompt, sort and the buy confirm.

_Questions for this feature: 14 in the sheet (Feature = "Auction house (/ah)")._

### Trading (/trade)

Two players near each other can trade items and money (never crystals) in a safe window. Each side adds items and money, both press ready, a 3 second countdown runs, then the swap happens in one step. Anything that changes cancels the countdown. If a player has no room, the items wait and arrive later.

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
8. Try to trade with a new account (under 30 min) with money. Money should be refused, items allowed.
9. Try to trade with your own other profile. It must be refused.

_Questions for this feature: 11 in the sheet (Feature = "Trading (/trade)")._

### Economy dashboard and alerts (/eco admin)

Staff (moderators and up) see a dashboard of how much money is created and removed, the sink ratio, an inflation badge (OK, Watch, ACT), top earners and biggest transactions. Alerts fire for strange events: very high earning, big or repeated transfers, many notes cashed, a huge sell spike, or money appearing without a record. Alerts never punish by themselves.

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

_Questions for this feature: 10 in the sheet (Feature = "Economy dashboard and alerts (/eco admin)")._

## The Depths

### The Depths: zones and tier locks

The Depths is the main money world. It has three wings (Mine with 12 tiers, Forest with 11, Combat with 10) around a hub called The Gate. Each tier needs a skill level (0, 5, 10 ... 66). A locked tier shows iron bars only to you and pushes you back. Unlocked tiers are reached with /dungeon after a short countdown.

| Command | Who | What |
|---|---|---|
| `/dungeon` | player | Tier menu with tabs Mine, Forest, Combat. Click an unlocked tier to teleport (3 s countdown) |
| `/depths tp <zone>` | builder | Staff: teleport to The Gate, a tier or the boss arena, ignoring locks |
| `/depths generate [zone\|all]` | admin | Staff: build the placeholder layout |
| `/depths debug [player]` | admin | Staff: show region, timers, flags |

**Test:**

1. Type /dungeon. Check the three tabs and that locked tiers show 'Mining 5 needed' with a progress bar.
2. Click tier 1 of the Mine. Wait for the 3 s countdown. Move during it and see it cancel.
3. Walk to the gate of tier 2 without the level. Bars should appear and you should be pushed back.
4. Ask staff to /skills set you mining 5 and see the tier 2 gate open with a title and sound.
5. Ask staff to lower your level while you stand in tier 2. You should be sent to The Gate.
6. Try to break a normal block or place a block in a zone. It must be refused.
7. Try ender pearls into a locked tier. They must not get you in.
8. Test on Bedrock: the /dungeon menu and the teleport countdown.

_Questions for this feature: 11 in the sheet (Feature = "The Depths: zones and tier locks")._

### The Depths: ore, logs and drops

In the Mine and Forest each block gives a custom drop (coal, copper, iron ... voidstone; oak, birch, spruce ... warped logs) that goes straight into your satchel. Ores are personal: only you see them and they come back after about 10 seconds. Blocks also give crystals now and then, and very rarely a book or a blueprint. Higher tiers give more valuable drops.

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

_Questions for this feature: 12 in the sheet (Feature = "The Depths: ore, logs and drops")._

### The Depths: combat zones, mobs and mini-bosses

The Combat wing has 10 tiers (Graveyard to Warlord's Citadel). Mobs spawn while players are in the zone: 8 plus 2 per player, max 30 per zone. A mini-boss appears every 10 minutes in each zone with 15 times health and better loot. Kills give combat XP, drops into the satchel, crystals and vanilla XP.

| Command | Who | What |
|---|---|---|
| `/dungeon (Combat tab)` | player | Go to a combat tier |
| `/depths debug` | admin | Staff: see combat mobs and the mini-boss timer |

**Test:**

1. Open /dungeon, Combat, tier 1 (Graveyard). Check mobs spawn and you can kill them with your starter sword.
2. Check drops go to the satchel and crystals show on the action bar.
3. Wait for the mini-boss (about 10 minutes with players in the zone). Check the boss bar and the chat line.
4. Kill the mini-boss with a friend. Both should get rewards if both dealt 10% or more.
5. Die to a mob. Check you keep your inventory and respawn where expected.
6. Leave the zone and wait 30 s. Mobs should disappear.
7. Test with a bow, a trident and a crossbow and see how they compare to a sword.
8. On Bedrock check the boss bar and hits.

_Questions for this feature: 12 in the sheet (Feature = "The Depths: combat zones, mobs and mini-bosses")._

### Satchel (Depths storage)

A virtual bag that holds everything dropped in the Depths. It starts at 256 items and grows with Mining and Foraging levels and 5 money upgrades (up to 100,000). From the satchel you can sell all, sell one item type, take items out to trade, and the Forge uses what is inside. If the satchel is full, mining stops giving drops.

| Command | Who | What |
|---|---|---|
| `/dungeon > Satchel (button)` | player | Open the satchel (also in /menu > The Depths) |
| `Sell all (button)` | player | Sell everything in the satchel |
| `Take out (click item)` | player | Move one stack or all that fit into your inventory |
| `Upgrade (button)` | player | Buy more space with money |

**Test:**

1. Mine some blocks in the Depths and open the satchel. Check the amounts, price and value.
2. Click Sell all. Check the money and the sound.
3. Take a stack of coal out into your inventory. Check it shows as an item and can be traded.
4. Buy the first upgrade ($25,000) and check the capacity grows by 256.
5. Fill the satchel to full and keep mining. Check the warning and that the block is not broken.
6. Check the scoreboard shows 'Satchel N / M'.
7. Switch profile and check each profile has its own satchel.
8. On Bedrock, tap items and Sell all.

_Questions for this feature: 10 in the sheet (Feature = "Satchel (Depths storage)")._

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

_Questions for this feature: 10 in the sheet (Feature = "Forge and living tools")._

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

_Questions for this feature: 10 in the sheet (Feature = "Blueprints and skins")._

### Skills (/skills)

Three skills exist now: Mining, Foraging and Combat (Farming and Fishing come later). XP comes from the Depths and from mob kills. Levels go up to 150, and the first 66 unlock Depths tiers. Skills are per profile and reset each season. Anyone can look at another player's skills.

| Command | Who | What |
|---|---|---|
| `/skills [player]` | player | Open the skills menu for you or another player |
| `/skills set <player> <skill> <levels>` | admin | Staff: set a level |
| `/skills add <player> <skill> <levels>` | admin | Staff: add levels |

**Test:**

1. Type /skills. Check three skills active and two 'coming soon'.
2. Mine a few blocks. The action bar should show '+N Mining XP'. Turn it off in /settings and check it stops.
3. Level up. Check the title, chat line with the next unlock and sound.
4. Open /skills <friend> to see their skills.
5. As admin: /skills set <you> mining 20, then /skills add <you> combat 5. Check the tiers unlock.
6. Kill a mob from a spawner on your island. It should give no Combat XP.
7. Break an island generator block and check you get no Mining XP.
8. Switch profile and check the skills are different.

_Questions for this feature: 10 in the sheet (Feature = "Skills (/skills)")._

### Enchants and the Enchanter (/enchanter)

There are 48 custom enchants (mining, forestry, farming, combat, fishing, armor) with rarities from Common to Mythic. You find books by mining, chopping and killing, or roll them at the Enchanter for crystals. Books unlock an enchant on a tool slot, crystals raise its level. Enchants can be upgraded, combined, removed with a scroll or shattered for 20% back. Books are unique items and tradeable.

| Command | Who | What |
|---|---|---|
| `/enchanter` | player | Apply, upgrade, combine, remove books, Book shop rolls, guide |
| `Book shop rolls` | player | Buy Apprentice (500), Adept (4,000) or Master (25,000) crystal rolls |
| `Disenchant Scroll` | player | Buy for 2,500 crystals, removes an enchant and keeps the book |

**Test:**

1. Type /enchanter and open the guide. Look at the list and rarities.
2. Buy an Apprentice Roll (500 crystals) and check a level I book arrives.
3. Apply the book to a tool slot (needs tool level 5 for slot 1).
4. Upgrade the enchant with crystals, then try combining two equal books.
5. Remove the enchant without a scroll and check the 20% crystal refund.
6. Buy a scroll and remove with it. The book must come back.
7. Try to put a book on a vanilla tool or an anvil. Both refused.
8. Trade a book to a friend and list one in /ah.
9. Check a copy of a book (staff test) is taken away when used.

_Questions for this feature: 10 in the sheet (Feature = "Enchants and the Enchanter (/enchanter)")._

## Wardrobe & sets

### Wardrobe and armor sets (/wardrobe)

There are 16 sets (5 starter sets for $20k, then sets bought with money, crystals or gems, and top sets only from crates). Wearing 2 or 4 pieces gives bonuses like sell price, drops, speed or damage reduction. Sets level from 0 to 50 by doing what they boost, and the bonus grows from 0x to 2x. Two max-level sets can be merged. Set armor is locked to your body and cannot be dropped or copied. A Wardrobe Chest holds tools and books.

| Command | Who | What |
|---|---|---|
| `/wardrobe (/sets)` | player | Open all sets, wear or take off pieces, buy, level, merge, tomes |
| `Set screen > Wear all / Take all off` | player | Put on or off a full set |
| `Redeem a Set Token (right-click)` | player | Unlock a set from a crate token |

**Test:**

1. Type /wardrobe. Check the filter tabs and that owned sets show first.
2. Buy a $20k starter set. Check the confirm screen (always asks).
3. Wear all pieces. Try to drop, click, shift-click, swap or put them in a chest. All must fail.
4. Wear 2 pieces only and check the 2-piece bonus line; mine until the action bar shows set XP.
5. Take off the set and check pieces vanish (not in inventory).
6. Swap profile and check sets are per profile.
7. Try to wear a set in the warzone (blocked) and check it counts as 1x there.
8. Ask staff for a Wardrobe Tome and a Set Token and use both.
9. Test merging with staff help at level 50: check the result is level 0 with both bonuses.

_Questions for this feature: 11 in the sheet (Feature = "Wardrobe and armor sets (/wardrobe)")._

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
8. Check names with spaces turn into dashes.

_Questions for this feature: 10 in the sheet (Feature = "Loadouts (/loadout)")._

## Stacking & spawners

### Stacking: mobs, items, entity limits and dark crops

On islands, mobs of the same kind within 5 blocks join one stack (up to 1,024), shown as 'Zombie x45'. Stacked spawner mobs do not move. Dropped items merge into one with a counter. Islands have limits for armor stands, item frames, minecarts, boats, villagers and display entities. Crops also grow in the dark. A weapon without a custom enchant kills one mob of a stack per hit.

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

_Questions for this feature: 10 in the sheet (Feature = "Stacking: mobs, items, entity limits and dark crops")._

### Spawners (stackable)

Spawner items come in 5 tiers (pigs and cows up to blaze, iron golem, evoker). They can only be placed on islands. One block holds 8 spawners and can grow to 512 with a mastery. Right-click with another spawner of the same type to add. Break = take one, sneak-break = take all. Mobs spawn straight into stacks, with no light or space needed, only when a player is within 16 blocks.

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

_Questions for this feature: 11 in the sheet (Feature = "Spawners (stackable)")._

## AFK pool

### AFK pool

A small area at spawn where standing still pays. Every 15 minutes inside gives money, island fuel, and sometimes a key or a sell booster. The longer you stay, the bigger the reward (up to 180% after 6 hours). Maximum 8 hours per account per day. One account per IP can earn at a time. There is no command. It is closed until builders add the area.

| Command | Who | What |
|---|---|---|
| `(no command)` | player | Stand in the pool. See the action bar and the AFK line on the spawn scoreboard |
| `/help afk` | player | Help page for the pool |

**Test:**

1. Ask staff to open the pool (add the afk_pool region or set the box in the config).
2. Stand inside and watch the action bar countdown and the scoreboard AFK line.
3. Wait 15 minutes. Check money arrives and the chat line.
4. Leave and return. The session bonus should reset; the daily total stays.
5. Use a second account on the same IP. It should show 'Paused: another account is earning'.
6. Stay 1 hour. Check the first key is saved as pending (crates are not built).
7. Check the sell booster starts only after you step out of the pool.
8. On Bedrock check the action bar and whether two Bedrock players on one network are paused.

_Questions for this feature: 10 in the sheet (Feature = "AFK pool")._

