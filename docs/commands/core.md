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

## Player visibility (VIS, REVIEW3 D4)

One shared service in common (`VisibilityService`, implementation `CoreVisibilityService`) is the **only** code that calls `Player#hidePlayer` / `showPlayer` for this plugin. Bukkit tracks hides per plugin, so two features hiding and showing on their own would undo each other (the AFK pool would un-vanish a moderator). No command.

- **Rule:** `hidden(viewer, target) = target vanished and viewer lacks skyblock.staff.vanish.see` **or** (target is not staff **and** (a module rule hides **or** the viewer's Show players choice hides)). Vanished staff follow vanish only. Staff with `skyblock.staff.visible` (helpers and up) are always visible while not vanished (`visibility.yml → staff-always-shown`).
- **Show players** (`/settings`, Display row): Everyone (default, `visibility.yml → setting.default`) / Friends + island (island members and coop players; a friends system can add itself later through `addRelation`) / Nobody. Saved per profile like every setting. It only applies where **both** players stand in a place from `visibility.yml → setting.worlds` (default `depths`) or `setting.regions` (build region ids, default `afk_pool`), in the same world. Spawn and islands keep everyone visible unless the owner adds them to the lists. No pin feature (DV-7).
- **Module rules:** `addRule(owner, id, (viewer, target) -> ...)`. The AFK pool hides players inside it from each other (R3-36, see [afk](afk.md)). Rules of a module are removed when it stops.
- **Only changes are applied:** the service keeps, per viewer, who it has hidden and applies the difference. Triggers: join (the joiner's pairs are applied again, so a stale hide from an earlier visit is fixed), quit, world change, profile switch, island join, a Show players change, `/vanish`, a module's `refresh(player)` (the AFK pool on enter/leave), and a position check every `region-check-ticks` (20) that only reacts when a player enters or leaves a place from `setting.*`. Never a per-tick scan of all pairs.
- **Tab list and collisions:** a hidden player also leaves that viewer's tab list (Minecraft removes hidden players from it). The viewer's client no longer knows the body, so it can't collide with it (Bukkit's `setCollidable` doesn't work between players and team collision rules belong to the chat module's nametag teams, so neither is used).
- **Names still work:** commands, tab completion, `/msg`, `/trade`, online counts and staff menus use `Visibility.knows(sender, target)` (vanish only), so a player hidden by the AFK pool or Show players can still be messaged, traded with, visited and named. `Player#canSee` is only for "is the body shown" checks (set trails, sword area effects).
- Config `visibility.yml` (reload with `/skyblock reload core`): `setting.worlds`, `setting.regions`, `setting.default`, `staff-always-shown`, `region-check-ticks`.

Plugin-level permissions (plugin.yml): `skyblock.admin.modules`, `skyblock.admin.reload`, `skyblock.admin.worlds`, `skyblock.staff.notify`, `skyblock.staff.visible` (always visible while not vanished), `skyblock.cooldown.bypass`, `skyblock.admin.config`, `skyblock.owner.config`. Rank grants: `server/permissions/base.txt`.
