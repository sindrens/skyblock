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
