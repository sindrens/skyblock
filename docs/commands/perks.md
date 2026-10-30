# Perks module (`modules/perks`)

Rank perks are permission-gated; donor ranks receive them with the store (Phase 11). Until then only ops/staff have them. Settings: `perks.yml`, texts: `messages/perks.yml`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/craft` (`/workbench`, `/wb`) | Crafting table anywhere | `skyblock.perks.craft` | ✅ |
| `/ec` (`/enderchest`) | Your ender chest anywhere | `skyblock.perks.enderchest` | ✅ |
| `/hat` | Wear the held item as a hat (safe swap, Curse of Binding respected) | `skyblock.perks.hat` | ✅ |
| `/nick <nickname\|off>` | Set/remove your nickname: letters, numbers, `_`; 3-16 letters; reserved words and other players' names blocked; unique; cooldown | `skyblock.perks.nick` (+ `.color` for `&` codes, `.hex` for hex/gradients, `.bypass` for reserved words) | ✅ |
| `/setnick <player> <nickname\|off>` | Set/remove another online player's nickname | `skyblock.staff.nick.others` | ✅ |
| `/realname <nickname>` (`/whonick`) | Who is behind a nickname (online or offline) | `skyblock.staff.realname` | ✅ |
| `/freerank` | Free lowest rank (owner RW4): after `freerank.min-playtime-minutes` (30) of total playtime, claim LuckPerms group `freerank.group` (`ember`, created in `server/permissions/base.txt`) once, then pick 1 of 3 tags (`freerank.tags`: ember, spark, ash) in a menu. Both kept forever. One atomic row per player in `perks_freerank`; a failed rank/tag grant is undone so nothing is lost; closing the menu is fine (`/freerank` reopens it until a tag is picked). Audited (`auth`: `freerank_claim`, `freerank_tag`) | `skyblock.perks.freerank` (everyone) | ✅ |
| `/trash` (`/disposal`) | Trash menu: items left inside are destroyed on close (logged) | `skyblock.perks.trash` (donators) | ✅ |
| `/repair [hand\|all] [player]` (`/fix`) | Repair vanilla durability (custom tools have none) | `skyblock.staff.repair` (+ `.others`) | ✅ |
| `/sudo <player> <command\|c:message>` | Run a command or chat as a player; never same/higher rank or `skyblock.sudo.exempt`; blocked command list in `perks.yml`; always audited | `skyblock.admin.sudo` (owner only) | ✅ |

Placeholders: `<ph:nick>` (nickname or name), `<ph:realname>`, `<ph:has_nick>`; nicknames also become the player's display name (`<ph:displayname>`).

Nicknames now show a `~` marker (perks.yml `nick.marker`) and reset when the rank that allowed them is lost (colors are dropped if only the color permission is lost; staff-set nicknames stay).

Permission definitions: `modules/perks/src/main/resources/permissions/perks.yml`. Rank grants: `server/permissions/perks.txt`.
