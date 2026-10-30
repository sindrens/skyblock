# Core (`plugin`, `common`)

The `/skyblock` admin command lives in the plugin itself; its permissions stay in `plugin/src/main/resources/plugin.yml`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/skyblock modules` | Module status | `skyblock.admin.modules` | ✅ |
| `/skyblock reload [core\|module]` | Reload everything, core.yml or one module; shows config problems | `skyblock.admin.reload` | ✅ |
| `/skyblock style` | Preview palette (hover = hex + Bedrock fallback), symbols, gradients, number formats, message types, title, action bar and a sound | `skyblock.admin.reload` | ✅ |
| `/skyblock config [file]` | List config files with status / show a file's active values (hover = description + default) | `skyblock.admin.reload` | ✅ |
| `/skyblock worlds` | List worlds (click to teleport) | `skyblock.admin.worlds` | ✅ |
| `/skyblock tp <world>` | Teleport to a world's spawn | `skyblock.admin.worlds` | ✅ |
| `/skyblock builds ...` | Finished builds: list, info, paste, paste-all, apply (see [worlds](worlds.md)) | `skyblock.admin.builds` | ✅ |

Other permissions: `skyblock.cooldown.bypass` skips command cooldowns. Per-command aliases and cooldowns live in `commands.yml`.

**Wrong input (FIXES1):** before Minecraft parses a player's command, our framework checks it against the command tree. An unknown sub-command or words after the last argument (e.g. `/is create Steve`) get our own message `commands.yml → unknown-input` ("Steve doesn't fit here. Usage: /is create", click to fill in) instead of the grey vanilla "Incorrect argument for command ... <--[HERE]". Missing arguments already showed our usage, bad values our argument errors. Not changeable: errors for other plugins' and vanilla commands, console input, and commands the player may not use at all (they stay hidden as "Unknown command").

Plugin-level permissions (plugin.yml): `skyblock.admin.modules`, `skyblock.admin.reload`, `skyblock.admin.worlds`, `skyblock.staff.notify`, `skyblock.cooldown.bypass`, `skyblock.admin.config`, `skyblock.owner.config`. Rank grants: `server/permissions/base.txt`.
