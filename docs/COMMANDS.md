# Commands and Permissions

Index. Every module owns its own command doc, permission file and rank grants, so parallel work never edits the same file:

| What | Where |
|---|---|
| Commands of a module | `docs/commands/<module>.md` (table: Command, Description, Permission, Status) |
| Permission nodes (description + default) | `modules/<module>/src/main/resources/permissions/<module>.yml`, registered automatically when the module enables |
| Plugin-level permissions (`/skyblock`, config editor, cooldown bypass, staff notify) | `plugin/src/main/resources/plugin.yml` |
| LuckPerms rank grants | `server/permissions/base.txt` (groups, prefixes, wildcards, people) + `server/permissions/<module>.txt` |

Permission root: `skyblock.`. Status: ⬜ planned · 🟡 in progress · ✅ done. Every command must also be reachable from a GUI.
Per-command aliases and cooldowns live in `commands.yml`.

## Modules
The table below is generated: run `./gradlew commandsIndex` after adding a file in `docs/commands/`.

<!-- BEGIN GENERATED INDEX (./gradlew commandsIndex) -->
| Module | Contents |
|---|---|
| [core](commands/core.md) | Core (`plugin`, `common`) |
| [achievements](commands/achievements.md) | Achievements (`modules/achievements`) |
| [afk](commands/afk.md) | AFK pool (`modules/afk`) |
| [auction](commands/auction.md) | Auction house (`modules/auction`) |
| [box](commands/box.md) | Item mailbox (`modules/box`) |
| [chat](commands/chat.md) | Chat, tab list, nametags (`modules/chat`) |
| [cosmetics](commands/cosmetics.md) | Cosmetics module (`modules/cosmetics`): M1.11 |
| [crates](commands/crates.md) | Crates (`modules/crates`) |
| [depths](commands/depths.md) | Depths commands |
| [economy](commands/economy.md) | Economy (`modules/economy`) |
| [enchants](commands/enchants.md) | Enchants module (`modules/enchants`): E1 |
| [essentials](commands/essentials.md) | Essentials (module `essentials`) |
| [hud](commands/hud.md) | Scoreboard (M1.4, module `hud`) |
| [islands](commands/islands.md) | Islands module (`modules/islands`): M2.1, M2.2, M2.3, M2.4, M2.5, M2.6, M2.7, ISL2, NOTES |
| [logs](commands/logs.md) | Logs (`modules/logs`, M1.10) |
| [menus](commands/menus.md) | Menus module (`modules/menus`): M1.6 + M1.7 + staff hub and `/admin` (M1.12) |
| [moderation](commands/moderation.md) | Punishments (`modules/moderation`, M1.8; replace vanilla `/ban /kick /pardon`) |
| [motd](commands/motd.md) | MOTD (`modules/motd`) |
| [npcs](commands/npcs.md) | NPCs and holograms (`modules/npcs`) |
| [perks](commands/perks.md) | Perks module (`modules/perks`) |
| [profiles](commands/profiles.md) | Profiles module (`modules/profiles`): PROF |
| [progression](commands/progression.md) | Progression module (`modules/progression`): M5.1 pillars, M5.2 island level, M5.3 task engine |
| [shop](commands/shop.md) | Builder shop (`modules/shop`) |
| [skills](commands/skills.md) | Skills module (`modules/skills`): M4.5 |
| [social](commands/social.md) | Social (`modules/social`) |
| [stacking](commands/stacking.md) | Stacking (`modules/stacking`) |
| [staff](commands/staff.md) | Staff tools (M1.9, module `staff`) |
| [tools](commands/tools.md) | Tools module (`modules/tools`): M4.2 |
| [trade](commands/trade.md) | Secure trade (`modules/trade`) |
| [tutorial](commands/tutorial.md) | Join tutorial (`modules/tutorial`) |
| [wardrobe](commands/wardrobe.md) | Wardrobe module (`modules/wardrobe`): W1 + W2 + W3 |
| [worlds](commands/worlds.md) | Worlds (`modules/worlds`): worlds and finished builds |
| [planned](commands/planned.md) | Planned commands |
<!-- END GENERATED INDEX -->

## Staff ranks (LuckPerms, `server/permissions/`)
| Rank | Weight | Inherits | Can |
|---|---|---|---|
| Builder | 5 | default | WorldEdit/FAWE, gamemode, teleport, time, `/skyblock worlds` + `tp` |
| Helper | 10 | default | Cheat alerts, staff chat |
| Moderator | 20 | Helper | Staff mode, inspect, kick, ban, teleport, gamemode, block lookups |
| Admin | 30 | Moderator + Builder | All `skyblock.admin.*`, all vanilla commands, Grim, CoreProtect, Via, Geyser |
| Owner | 100 | Admin | Everything (`*`) |

Applied automatically after every update/deploy by `server/scripts/apply-permissions.sh`: `base.txt` first, then every module file alphabetically. Add people in the People section of `server/permissions/base.txt` (`lp user <name> parent add <rank>`).
