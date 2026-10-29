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
| [chat](commands/chat.md) | Chat, tab list, nametags (`modules/chat`) |
| [cosmetics](commands/cosmetics.md) | Cosmetics module (`modules/cosmetics`): M1.11 |
| [depths](commands/depths.md) | Depths commands |
| [economy](commands/economy.md) | Economy (`modules/economy`) |
| [essentials](commands/essentials.md) | Essentials (module `essentials`) |
| [hud](commands/hud.md) | Scoreboard (M1.4, module `hud`) |
| [islands](commands/islands.md) | Islands module (`modules/islands`): M2.1, M2.2, M2.3 |
| [logs](commands/logs.md) | Logs (`modules/logs`, M1.10) |
| [menus](commands/menus.md) | Menus module (`modules/menus`): M1.6 + M1.7 + staff hub and `/admin` (M1.12) |
| [moderation](commands/moderation.md) | Punishments (`modules/moderation`, M1.8; replace vanilla `/ban /kick /pardon`) |
| [motd](commands/motd.md) | MOTD (`modules/motd`) |
| [perks](commands/perks.md) | Perks module (`modules/perks`) |
| [skills](commands/skills.md) | Skills module (`modules/skills`): M4.5 |
| [social](commands/social.md) | Social (`modules/social`) |
| [staff](commands/staff.md) | Staff tools (M1.9, module `staff`) |
| [worlds](commands/worlds.md) | Worlds (`modules/worlds`) |
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
