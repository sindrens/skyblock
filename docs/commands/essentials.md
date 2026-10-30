# Essentials (module `essentials`)

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

## Spawn and join flow (M1.1, module `essentials`)

| Command | What | Permission | Status |
|---|---|---|---|
| `/build` | Build mode in protected worlds (spawn); without it builders can't break anything by accident | `skyblock.build.spawn` | ✅ |
| `/setspawn` | Set the spawn point where you stand (saved to `worlds.yml`) | `skyblock.admin.setspawn` | ✅ |

- `/spawn` is now instant (`teleport.yml → spawn.warmup-seconds: 0`; every warmup and cooldown lives in `teleport.yml` since F4).
- Join/quit messages only for players with `skyblock.join.message` (staff ranks now, donor ranks later). First-join welcome + broadcast with join number for everyone.
- Menu item: Nether Star in hotbar slot 9, runs `/menu`; service `MenuItem` (`setEnabled(player, bool)` for `/settings`).
- Spawn protection in `protection.worlds`: no build/break, whitelisted blocks only, no damage/hunger/drops/natural mobs/explosions, void → spawn.
- DEPTHSPROT: the worlds module's region flags (`worlds.yml → protection.*`) protect spawn too and close the gaps (bone meal, sign editing/dyeing, flint and steel, placing armor stands/boats, fire spread); `/build` mode also lifts them. A block must be allowed in both `interact-allowed` lists. The Forge Quarter's anvils, enchanting table and containers are decoration (region kind `forge_quarter`).

Permission definitions: `modules/essentials/src/main/resources/permissions/essentials.yml`. Rank grants: `server/permissions/essentials.txt`.
