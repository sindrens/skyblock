# Essentials (module `essentials`)

Settings in `essentials.yml`, texts in `messages/essentials.yml`. Every staff action is written to the audit log (category `staff`). Aliases can be changed in `commands.yml`.

| Command | What | Permission | Status |
|---|---|---|---|
| `/spawn` | Teleport to spawn (warmup 3 s, cooldown 5 s) | `skyblock.player.spawn` (everyone) | ✅ |
| `/tpa <player>` · `/tpahere <player>` | Ask to teleport to someone / ask them to come to you (clickable Accept/Deny, expires after 60 s). Refused like "requests off" when the target `/ignore`s you (owner answer 316) | `skyblock.player.tpa` (everyone) | ✅ |
| `/tpaccept [player]` · `/tpdeny [player]` · `/tpcancel` | Answer or cancel requests (aliases `/tpyes`, `/tpno`) | `skyblock.player.tpa` | ✅ |
| `/tp <player> [target]` | Teleport to a player, or a player to another | `skyblock.staff.tp` (+ `.others`) | ✅ |
| `/tphere <player>` | Bring a player to you | `skyblock.staff.tphere` | ✅ |
| `/tppos <x> <y> <z> [world]` | Teleport to coordinates (`~` = current) | `skyblock.staff.tppos` | ✅ |
| `/top` | Highest block above you | `skyblock.staff.top` | ✅ |
| `/back` | Previous location or death point | `skyblock.staff.back` | ✅ |
| `/shome [name]` · `/setshome [name]` · `/delshome <name>` | Staff homes (max 5). Owner answer 347: `/home` and `/sethome` are the island's (islands module) | `skyblock.staff.home` | ✅ |
| `/gm <c/s/sp/a> [player]` · `/gmc` `/gms` `/gmsp` `/gma` | Game mode (each mode has its own permission). Creative: builders and admins only, only in `staff-powers.creative-worlds` (spawn, Depths; owner answer 331); mods use spectator | `skyblock.staff.gamemode` + `.creative/.survival/.spectator/.adventure` (+ `.others`) | ✅ |
| `/fly [player]` | Toggle flying; only in staff mode or `/build` mode, and only for a target in one of those modes (owner answer 333) | `skyblock.staff.fly` (+ `.others`) | ✅ |
| `/speed <1-10> [fly/walk]` | Fly/walk speed (1 = normal) | `skyblock.staff.speed` | ✅ |
| `/god [player]` | No damage, no hunger; only in staff mode or `/build` mode, target too (owner answer 333) | `skyblock.staff.god` (+ `.others`) | ✅ |
| `/heal [player]` · `/feed [player]` | Heal (only while in staff mode or `/build` mode, owner answer 333) / feed | `skyblock.staff.heal` · `skyblock.staff.feed` (+ `.others`) | ✅ |
| `/clear [player]` | Clear inventory (confirm for others, contents logged; alias `/ci`) | `skyblock.staff.clear` (+ `.others`) | ✅ |
| `/invsee <player>` · `/ecsee <player>` | View inventory / ender chest; edit with `.edit` (every changed slot logged) | `skyblock.staff.invsee` · `skyblock.staff.ecsee` (+ `.edit`) | ✅ |
| `/vanish [player]` | Invisible to players, silent join/quit, stays on after rejoin (alias `/v`) | `skyblock.staff.vanish` (+ `.others`, `.see`) | ✅ |
| `/i <item> [amount]` · `/give <player> <item> [amount]` | Vanilla items (custom items have their own commands); always staff items (hidden tag, owner answer 332) | `skyblock.staff.give` | ✅ |
| `/time <day/noon/night/midnight/ticks> [world]` | Set time | `skyblock.staff.time` | ✅ |
| `/weather <clear/rain/thunder> [world]` | Set weather | `skyblock.staff.weather` | ✅ |
| `/skull <player>` · `/more` | Player head / fill held stack (not for custom items); the result is a staff item | `skyblock.staff.skull` · `skyblock.staff.more` | ✅ |

Other: `skyblock.staff.teleport.bypass` skips warmups, cooldowns and teleport restrictions.
Ranks (luckperms.txt): builder = `/i /give /gm c+s /fly /speed /time /weather /tppos /top`; helper = `/tp /vanish /invsee /ecsee` (view only); mod = all teleports, gamemode (no creative, owner answer 331), fly, heal, feed, god, clear, back, homes; admin = everything incl. editing inventories, `/skull`, `/more`.

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

**Staff powers (ANS3, owner answers 331-334; `essentials.yml → staff-powers.*`):**
- Creative mode only for players with `skyblock.staff.gamemode.creative` (builders, admins; mods lost it) and only in `staff-powers.creative-worlds` (`spawn`, `depths`). Every switch to creative is checked (our `/gm`, vanilla `/gamemode`, staff mode restoring an old mode); leaving those worlds or joining outside them switches to survival (`creative_refused`, `creative_ended` in the audit log).
- God mode, `/fly` and `/heal` need staff mode (`/staffmode`) or build mode (`/build`). God mode and fly that were given turn off within `staff-powers.check-seconds` (1) once both modes are off (`staff_powers_off`). Staff otherwise play normally on the same account.
- Staff items (`staff-powers.tag-staff-items`): `/give`, `/i`, `/more`, `/skull` and everything taken or moved in the creative inventory (or thrown out of it) carry the hidden `phoenixskyblock:staff_item` tag (`StaffItems` in common). The tag goes onto crafting and furnace results made with them and, with `staff-powers.track-placed-blocks`, onto the drops of a staff block placed in survival (remembered in the chunk data; creative builds are not tracked). Staff items can't be sold (sell chest, `/sell`, shop), traded, auctioned (also not inside shulker boxes or bundles); the box logs `staff_item_transfer` if one ever reaches another player.

Permission definitions: `modules/essentials/src/main/resources/permissions/essentials.yml`. Rank grants: `server/permissions/essentials.txt`.
