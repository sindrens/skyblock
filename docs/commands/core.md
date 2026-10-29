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

Other permissions: `skyblock.cooldown.bypass` skips command cooldowns. Per-command aliases and cooldowns live in `commands.yml`.

Plugin-level permissions (plugin.yml): `skyblock.admin.modules`, `skyblock.admin.reload`, `skyblock.admin.worlds`, `skyblock.staff.notify`, `skyblock.cooldown.bypass`, `skyblock.admin.config`, `skyblock.owner.config`. Rank grants: `server/permissions/base.txt`.
