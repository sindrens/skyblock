# Commands and Permissions

Planned list. Update as features ship. Every command must also be reachable from a GUI.
Permission root: `skyblock.`. Status: ⬜ planned · 🟡 in progress · ✅ done.

## Player
| Command | Description | Permission | Status |
|---|---|---|---|
| `/menu` | Main hub GUI | `skyblock.menu` | ⬜ |
| `/help [topic]` | Help GUI | `skyblock.help` | ⬜ |
| `/tutorial` | Start/resume tutorial | `skyblock.tutorial` | ⬜ |
| `/spawn` | Teleport to spawn | `skyblock.spawn` | ⬜ |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/back` | Teleports | `skyblock.tpa`, `skyblock.back` | ⬜ |
| `/warp`, `/warps` | Server + island warps | `skyblock.warp` | ⬜ |
| `/is` | Island GUI / home | `skyblock.island` | ⬜ |
| `/is visit <player>`, `/is invite`, `/is kick`, `/is lock` | Island management | `skyblock.island.*` | ⬜ |
| `/is upgrade` (= `/is masteries`) | Upgrades + tasks | `skyblock.island.upgrade` | ⬜ |
| `/is stats`, `/is top` | Stats, leaderboards | `skyblock.island.stats` | ⬜ |
| `/is outposts`, `/is biomes` | Outposts and biomes | `skyblock.island.outposts` | ⬜ |
| `/dungeon` | The Depths tier selector | `skyblock.depths` | ⬜ |
| `/bal`, `/pay`, `/withdraw` | Currencies | `skyblock.economy.*` | ⬜ |
| `/bank` | Personal + island bank | `skyblock.bank` | ⬜ |
| `/sell`, `/ah`, `/shop` | Selling and trading | `skyblock.sell`, `skyblock.ah` | ⬜ |
| `/skills`, `/sets`, `/enchanter` | Progression | `skyblock.skills` … | ⬜ |
| `/crates`, `/vote`, `/daily`, `/quests`, `/pass`, `/blackmarket`, `/collection` | Rewards | … | ⬜ |
| `/pets`, `/duel`, `/events`, `/report`, `/pv` | Misc | … | ⬜ |
| `/is leave`, `/is disband`, `/is transfer <member>`, `/is chat` | Island membership (REVIEW B10) | `skyblock.island.*` | ⬜ |
| `/trade <player>` | Secure two-sided trade | `skyblock.trade` | ⬜ |
| `/msg`, `/r`, `/ignore`, `/mail` | Messaging | `skyblock.msg` … | ⬜ |
| `/settings` | Personal toggles | `skyblock.settings` | ⬜ |
| `/stats [player]`, `/titles`, `/cosmetics` | Profile, titles, cosmetics | `skyblock.stats` … | ⬜ |
| `/satchel` | Depths satchel + sell all | `skyblock.depths.satchel` | ⬜ |
| `/fly` | Fly on own island (perk) | `skyblock.fly` | ⬜ |
| `/trash`, `/afk`, `/rules`, `/discord`, `/store`, `/halloffame` | Utility | … | ⬜ |
| `/tags` | Choose an owned cosmetic tag shown in chat/tab | `skyblock.tags` | ⬜ |
| `/is fuel` | Island Fuel status (offline production) | `skyblock.island.fuel` | ⬜ |

## Staff
| Command | Description | Permission | Status |
|---|---|---|---|
| `/staff` | Staff mode (vanish, inspect, freeze, teleport) | `skyblock.staff.mode` | ⬜ |
| `/inspect <player>` | Player overview GUI | `skyblock.staff.inspect` | ⬜ |
| `/admin` | In-game config editor | `skyblock.admin.config` | ⬜ |
| `/skyblock modules` | Module status | `skyblock.admin.modules` | ✅ |
| `/skyblock reload [core\|module]` | Reload everything, core.yml or one module; shows config problems | `skyblock.admin.reload` | ✅ |
| `/skyblock style` | Preview palette (hover = hex + Bedrock fallback), symbols, gradients, number formats, message types, title, action bar and a sound | `skyblock.admin.reload` | ✅ |
| `/skyblock config [file]` | List config files with status / show a file's active values (hover = description + default) | `skyblock.admin.reload` | ✅ |
| `/skyblock worlds` | List worlds (click to teleport) | `skyblock.admin.worlds` | ✅ |
| `/skyblock tp <world>` | Teleport to a world's spawn | `skyblock.admin.worlds` | ✅ |
| `/season` | Season tools (reset, pre-season) | `skyblock.owner.season` | ⬜ |
| `/unban <player\|ip>` | Unban a player (tab-completes banned names) or IP; staff are notified | `skyblock.staff.unban` | ✅ |
| `/sc <msg>` | Staff chat | `skyblock.staff.chat` | ⬜ |
| `/maintenance` | Maintenance mode + whitelist | `skyblock.admin.maintenance` | ⬜ |
| `/eco admin` | Economy dashboard and corrections (logged) | `skyblock.owner.economy` | ⬜ |

## Staff ranks (LuckPerms, `server/permissions/luckperms.txt`)
| Rank | Weight | Inherits | Can |
|---|---|---|---|
| Builder | 5 | default | WorldEdit/FAWE, gamemode, teleport, time, `/skyblock worlds` + `tp` |
| Helper | 10 | default | Cheat alerts, staff chat |
| Moderator | 20 | Helper | Staff mode, inspect, kick, ban, teleport, gamemode, block lookups |
| Admin | 30 | Moderator + Builder | All `skyblock.admin.*`, all vanilla commands, Grim, CoreProtect, Via, Geyser |
| Owner | 100 | Admin | Everything (`*`) |
Applied automatically after every update/deploy. Add people at the bottom of the file (`lp user <name> parent add <rank>`).


Other permissions: `skyblock.cooldown.bypass` skips command cooldowns. Per-command aliases and cooldowns live in `commands.yml`.

## Essentials (module `essentials`)
Settings in `essentials.yml`, texts in `messages/essentials.yml`. Every staff action is written to the audit log (category `staff`). Aliases can be changed in `commands.yml`.

| Command | What | Permission | Status |
|---|---|---|---|
| `/spawn` | Teleport to spawn (warmup 3 s, cooldown 5 s) | `skyblock.player.spawn` (everyone) | ✅ |
| `/tpa <player>` · `/tpahere <player>` | Ask to teleport to someone / ask them to come to you (clickable Accept/Deny, expires after 60 s) | `skyblock.player.tpa` (everyone) | ✅ |
| `/tpaccept [player]` · `/tpdeny [player]` · `/tpcancel` | Answer or cancel requests (aliases `/tpyes`, `/tpno`) | `skyblock.player.tpa` | ✅ |
| `/tp <player> [target]` | Teleport to a player, or a player to another | `skyblock.staff.tp` (+ `.others`) | ✅ |
| `/tphere <player>` | Bring a player to you | `skyblock.staff.tphere` | ✅ |
| `/tppos <x> <y> <z> [world]` | Teleport to coordinates (`~` = current) | `skyblock.staff.tppos` | ✅ |
| `/top` | Highest block above you | `skyblock.staff.top` | ✅ |
| `/back` | Previous location or death point | `skyblock.staff.back` | ✅ |
| `/home [name]` · `/sethome [name]` · `/delhome <name>` | Staff homes (max 5) | `skyblock.staff.home` | ✅ |
| `/gm <c/s/sp/a> [player]` · `/gmc` `/gms` `/gmsp` `/gma` | Game mode (each mode has its own permission) | `skyblock.staff.gamemode` + `.creative/.survival/.spectator/.adventure` (+ `.others`) | ✅ |
| `/fly [player]` | Toggle flying | `skyblock.staff.fly` (+ `.others`) | ✅ |
| `/speed <1-10> [fly/walk]` | Fly/walk speed (1 = normal) | `skyblock.staff.speed` | ✅ |
| `/god [player]` | No damage, no hunger | `skyblock.staff.god` (+ `.others`) | ✅ |
| `/heal [player]` · `/feed [player]` | Heal / feed | `skyblock.staff.heal` · `skyblock.staff.feed` (+ `.others`) | ✅ |
| `/clear [player]` | Clear inventory (confirm for others, contents logged; alias `/ci`) | `skyblock.staff.clear` (+ `.others`) | ✅ |
| `/invsee <player>` · `/ecsee <player>` | View inventory / ender chest; edit with `.edit` (every changed slot logged) | `skyblock.staff.invsee` · `skyblock.staff.ecsee` (+ `.edit`) | ✅ |
| `/vanish [player]` | Invisible to players, silent join/quit, stays on after rejoin (alias `/v`) | `skyblock.staff.vanish` (+ `.others`, `.see`) | ✅ |
| `/i <item> [amount]` · `/give <player> <item> [amount]` | Vanilla items (custom items have their own commands) | `skyblock.staff.give` | ✅ |
| `/time <day/noon/night/midnight/ticks> [world]` | Set time | `skyblock.staff.time` | ✅ |
| `/weather <clear/rain/thunder> [world]` | Set weather | `skyblock.staff.weather` | ✅ |
| `/skull <player>` · `/more` | Player head / fill held stack (not for custom items) | `skyblock.staff.skull` · `skyblock.staff.more` | ✅ |

Other: `skyblock.staff.teleport.bypass` skips warmups, cooldowns and teleport restrictions.
Ranks (luckperms.txt): builder = `/i /give /gm c+s /fly /speed /time /weather /tppos /top`; helper = `/tp /vanish /invsee /ecsee` (view only); mod = all teleports, gamemode, fly, heal, feed, god, clear, back, homes; admin = everything incl. editing inventories, `/skull`, `/more`.
## Social (`modules/social`)
| Command | What it does | Permission | Status |
|---|---|---|---|
| `/msg <player> <message>` (`/w`, `/tell`, `/whisper`, `/m`, `/pm`) | Private message; click a name to reply; rate-limited; logged (30 days) | `skyblock.social.msg` | ✅ |
| `/r <message>` (`/reply`) | Reply to your last private message | `skyblock.social.msg` | ✅ |
| `/ignore <player>` / `/ignore list` | Block a player's private messages (staff can't be ignored) | `skyblock.social.ignore` | ✅ |
| `/unignore <player>` | Stop ignoring | `skyblock.social.ignore` | ✅ |
| `/afk` | Toggle AFK (auto after `social.yml → afk.auto-minutes`) | `skyblock.social.afk` | ✅ |
| `/list` (`/online`, `/who`) | Online players grouped by rank | `skyblock.social.list` | ✅ |
| `/ping [player]` | Connection delay | `skyblock.social.ping` | ✅ |
| `/playtime [player]` (`/pt`) | Total play time | `skyblock.social.playtime` | ✅ |
| `/seen <player>` | Last online / first joined | `skyblock.social.seen` (+ `skyblock.staff.seen.details`) | ✅ |
| `/discord`, `/store`, `/vote`, `/website`, `/rules` | Clickable links (URLs in `social.yml`) | `skyblock.social.links` | ✅ |
| `/socialspy` | Staff: see all private messages | `skyblock.staff.socialspy` | ✅ |
| `/whois <player>` | Staff: UUID, rank, platform, times, location, names; IP + alts with `skyblock.staff.whois.ip` | `skyblock.staff.whois` | ✅ |
| `/broadcast <message>` (`/bc`, `/announce`) | Staff: announcement in chat + title + sound (MiniMessage) | `skyblock.staff.broadcast` | ✅ |

Other: `skyblock.staff.unignorable` (private messages always delivered), `skyblock.staff.vanish.see` (see vanished players in `/list` and tab completion; defined by the vanish feature).
## Perks module (`modules/perks`)
Rank perks are permission-gated; donor ranks receive them with the store (Phase 11). Until then only ops/staff have them. Settings: `perks.yml`, texts: `messages/perks.yml`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/craft` (`/workbench`, `/wb`) | Crafting table anywhere | `skyblock.perks.craft` | ✅ |
| `/ec` (`/enderchest`) | Your ender chest anywhere | `skyblock.perks.enderchest` | ✅ |
| `/hat` | Wear the held item as a hat (safe swap, Curse of Binding respected) | `skyblock.perks.hat` | ✅ |
| `/nick <nickname\|off>` | Set/remove your nickname: letters, numbers, `_`; 3-16 letters; reserved words and other players' names blocked; unique; cooldown | `skyblock.perks.nick` (+ `.color` for `&` codes, `.hex` for hex/gradients, `.bypass` for reserved words) | ✅ |
| `/setnick <player> <nickname\|off>` | Set/remove another online player's nickname | `skyblock.staff.nick.others` | ✅ |
| `/realname <nickname>` (`/whonick`) | Who is behind a nickname (online or offline) | `skyblock.staff.realname` | ✅ |
| `/trash` (`/disposal`) | Trash menu: items left inside are destroyed on close (logged) | `skyblock.perks.trash` (donators) | ✅ |
| `/repair [hand\|all] [player]` (`/fix`) | Repair vanilla durability (custom tools have none) | `skyblock.staff.repair` (+ `.others`) | ✅ |
| `/sudo <player> <command\|c:message>` | Run a command or chat as a player; never same/higher rank or `skyblock.sudo.exempt`; blocked command list in `perks.yml`; always audited | `skyblock.admin.sudo` (owner only) | ✅ |

Placeholders: `<ph:nick>` (nickname or name), `<ph:realname>`, `<ph:has_nick>`; nicknames also become the player's display name (`<ph:displayname>`).

## Spawn and join flow (M1.1, module `essentials`)
| Command | What | Permission | Status |
|---|---|---|---|
| `/build` | Build mode in protected worlds (spawn); without it builders can't break anything by accident | `skyblock.build.spawn` | ✅ |
| `/setspawn` | Set the spawn point where you stand (saved to `worlds.yml`) | `skyblock.admin.setspawn` | ✅ |

- `/spawn` is now instant (`essentials.yml → spawn.warmup-seconds: 0`).
- Join/quit messages only for players with `skyblock.join.message` (staff ranks now, donor ranks later). First-join welcome + broadcast with join number for everyone.
- Menu item: Nether Star in hotbar slot 9, runs `/menu`; service `MenuItem` (`setEnabled(player, bool)` for `/settings`).
- Spawn protection in `protection.worlds`: no build/break, whitelisted blocks only, no damage/hunger/drops/natural mobs/explosions, void → spawn.

## Scoreboard (M1.4, module `hud`)
No commands. Layout in `messages/hud.yml`, settings in `hud.yml`. Service `HudService`: `setEnabled(player, bool)` (for `/settings`), `addLayout(owner, layout, predicate)` (Depths/events), `scoreboard(player)` (nametag teams for M1.5).
## Chat, tab list, nametags (`modules/chat`)
| Command | What it does | Permission | Status |
|---|---|---|---|
| `/chat clear` · `/clearchat` (`/cc`) | Clear the chat for everyone (staff keep theirs) | `skyblock.staff.chat.manage` | ✅ |
| `/chat lock` · `/mutechat` (`/lockchat`) | Lock/unlock the chat | `skyblock.staff.chat.manage` | ✅ |
| `/chat slow <seconds>` | Slow mode (0 = off) | `skyblock.staff.chat.manage` | ✅ |
| `/sc [message]` (`/staffchat`) | Staff chat; without text toggles staff-chat mode | `skyblock.staff.chat` | ✅ |

Chat permissions: `skyblock.chat.color` (classic `&` colors), `skyblock.chat.format` (`&l &o &n &m`), `skyblock.chat.hex` (`&#RRGGBB`), `skyblock.chat.links` (links/IPs), `skyblock.chat.item` (`[item]`), `skyblock.chat.bypass` (lock, slow mode, rate limit, filters), `skyblock.staff.tab` (TPS/ping in the tab footer). Formats live in `chat.yml → format.*` (staff GUI editor in M1.11).
## Menus module (`modules/menus`): M1.6 + M1.7
Layout: `menu.yml` (hub), `help.yml` (help categories, topics, pages), `navigation.yml` (menu item), texts: `messages/navigation.yml`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/menu` (`/gui`) | Main hub: every feature; not-yet-built ones shown locked "Coming soon". Also the **menu item** (Nether Star, hotbar slot 9; right-click/tap) | `skyblock.menu` (everyone) | ✅ |
| `/help [topic\|category\|commands]` (`/guide`) | Help menu: categories, topics with pages, Take me there, Show in chat; command lists generated from the commands you may use. Console/chat gets the chat version. Every menu's "?" opens `/help <topic>` | `skyblock.help` (everyone) | ✅ |
| `/settings [setting] [on\|off\|toggle]` (`/options`, `/prefs`) | Personal settings menu (Chat, Social, Display, Sounds); chat form for quick changes, Tab lists them | `skyblock.settings` (everyone) | ✅ |

Settings now: **menu item** (works). Placeholders shown "Coming soon" until their module takes them over through the `PlayerSettings` service: private-messages, mentions-sound, join-messages, chat-colors, chat-timestamps, tpa-requests, trade-requests, island-invites, afk-broadcast, scoreboard, tips, particles, depths-visibility, ui-sounds, event-sounds.

## Punishments (`modules/moderation`, M1.8; replace vanilla `/ban /kick /pardon`)
Flags in the reason: `-s` silent (staff only see it), `-ip` IP-wide. Durations: `30m`, `2h`, `7d`, `1w`. Templates/ladders: `punishments.yml`. Length limits per rank: `moderation.yml → limits`.
| Command | What it does | Permission | Status |
|---|---|---|---|
| `/punish <player> [reason] [-s]` | Menu: category → reason (ladder shows the next step) → confirm; or apply a reason directly | `skyblock.staff.punish.gui` | ✅ |
| `/ban <player> [reason]` · `/tempban <player> <time> [reason]` | Ban (offline + Bedrock names work), styled ban screen with ID and appeal link | `skyblock.staff.punish.ban` | ✅ |
| `/ipban <player\|ip> [reason]` (`/ban-ip`) | Ban the account and its last IP (or an IP) | `skyblock.staff.punish.ipban` | ✅ |
| `/mute <player> [reason]` · `/tempmute <player> <time> [reason]` | Mute: blocks chat, private messages, signs and books | `skyblock.staff.punish.mute` | ✅ |
| `/warn <player> <reason>` | Warning (chat + title) | `skyblock.staff.punish.warn` | ✅ |
| `/kick <player> [reason]` | Kick (kept in history) | `skyblock.staff.punish.kick` | ✅ |
| `/unban <player\|ip> <reason>` (`/pardon`) | Unban; reason required | `skyblock.staff.unban` | ✅ |
| `/unmute <player> <reason>` | Unmute; reason required | `skyblock.staff.punish.unmute` | ✅ |
| `/unwarn <player> <reason>` | Remove the latest warning; reason required | `skyblock.staff.punish.unwarn` | ✅ |
| `/history <player> [chat\|menu]` (`/punishments`) | All punishments (active/expired/removed, who, why) | `skyblock.staff.punish.history` | ✅ |
| `/alts <player>` | Accounts sharing an IP, with ban/mute status | `skyblock.staff.punish.alts` | ✅ |

Other: `skyblock.staff.punish.silent` (`-s`), `skyblock.staff.punish.exempt` / `.exempt.bypass`, `skyblock.staff.punish.max.<tier>` (length tiers). Alt alerts on join go to `skyblock.staff.notify`.
## Staff tools (M1.9, module `staff`)
Settings in `staff.yml`, texts in `messages/staff.yml`, data in `db/staff` (`staff_mode`, `reports`). Every action is audited (category `staff`; frozen chat in `chat`, logout while frozen in `punishment`).

| Command | What | Permission | Status |
|---|---|---|---|
| `/staff` (`/staffmode`, `/mod`) | Staff mode: inventory saved (crash-safe) and swapped for the staff hotbar: Inspect, Freeze, Random player, Logs here, Vanish, Punish, Reports, Leave. Vanish, fly, night vision, no damage/hunger/pickup | `skyblock.staff.mode` | ✅ |
| `/freeze <player>` (`/ss`) · `/unfreeze <player>` | Freeze for a check: no moving/interacting/teleporting/commands (except `freeze.allowed-commands`), chat to staff only, title + Discord link; logout = `freeze.logout-action` (alert / ban) | `skyblock.staff.freeze` (`.exempt`) | ✅ |
| `/inspect <player>` (`/check`) | Profile (rank, playtime, first/last seen, platform, ping, location), report counts, inventory, ender chest, teleport, freeze, punish, logs (offline players too) | `skyblock.staff.inspect` | ✅ |
| `/report <player> [reason]` | Report a player: reason picker GUI or typed reason; cooldown, max open reports, one open report per target | `skyblock.player.report` (everyone; `skyblock.report.bypass-cooldown`) | ✅ |
| `/reports [open/claimed/closed]` · `/reports claim/unclaim <id>` · `/reports close <id> <outcome>` · `/reports player <name>` | Report list GUI, claim, close with outcome (punished, warned, no-evidence, invalid, duplicate); the reporter is told the result (also later on join) | `skyblock.staff.reports` | ✅ |

Staff tools dispatch `/punish` (moderation module), `/logs` (M1.10) and `/invsee`/`/ecsee` (essentials); missing ones say "isn't available yet".
