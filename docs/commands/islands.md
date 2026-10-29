# Islands module (`modules/islands`): M2.1, M2.2, M2.3

Grid, creation, home and per-player borders. Config `islands.yml`, texts `messages/islands.yml`, schematics in
`plugins/PhoenixSkyblock/schematics/` (`<creation.schematic>.schem` + optional `<name>.yml` metadata with `home`, `chest`,
`sign` offsets and `home.yaw`; missing file or no FastAsyncWorldEdit = the built-in "Ember Meadow" placeholder islet).

| Command | Description | Permission | Status |
|---|---|---|---|
| `/is` (`/island`) | No island yet: "Create your island" confirm menu, then the starter islet is built (FAWE async or placeholder), the starter chest filled and you are teleported with a title, sound and your first objective. Has an island: teleport home | `skyblock.island` (everyone) | ✅ |
| `/is home` | Teleport to your island home (safe-spot check: unsafe homes move to the nearest safe spot; warmup `home.warmup-seconds`) | `skyblock.island` | ✅ |
| `/is go` | Same as `/is home` (your island is the one you're a member of; visiting others is M2.4) | `skyblock.island` | ✅ |
| `/is sethome` | Set the home where you stand: Leader + Mod (M2.3), inside your border, safe spot, Y 0-256 | `skyblock.island` | ✅ |
| `/is invite <player>` | Leader/Mod: invite an online player without an island. On a **solo** island the leader first sees "Inviting makes your island a Team Island forever. Solo and team islands are on separate leaderboards." (confirm screen; cancel keeps it solo). The target gets a clickable `[Accept] [Deny]`; expires after `members.invite-expiry-seconds` (5 min) | `skyblock.island` | ✅ |
| `/is accept [player]` | Accept your newest invite, or the one from that player's island (inviter or any member). Checks join limits, member slots and the target having no island, all in one transaction | `skyblock.island` | ✅ |
| `/is deny [player]` | Decline your newest invite, or the one from that player's island; the inviter is told | `skyblock.island` | ✅ |
| `/is kick <player>` | Leader kicks mods/members, Mod kicks members (confirm screen). Kicked players are sent to spawn if they stand on the island | `skyblock.island` | ✅ |
| `/is leave` | Leave your island (confirm screen). The leader must `/is transfer` first (disband is M2.7) | `skyblock.island` | ✅ |
| `/is promote <player>` | Leader: Member → Mod | `skyblock.island` | ✅ |
| `/is demote <player>` | Leader: Mod → Member | `skyblock.island` | ✅ |
| `/is transfer <player>` | Leader: hand the island to a member (two confirm screens); the old leader becomes a Mod | `skyblock.island` | ✅ |
| `/is coop <player>` | Leader/Mod: temporary trust for an online player (coop role permissions). Ends automatically when no leader or mod of the island is online; never turns solo into team; no XP, task progress or bank access; takes no member slot (max `members.max-coops`) | `skyblock.island` | ✅ |
| `/is uncoop <player>` | Leader/Mod: end a player's coop trust | `skyblock.island` | ✅ |
| `/is members` | Member list GUI: heads with role, join date, inviter, online state; click a head for the actions you may use (promote, demote, transfer, kick, uncoop, leave). Buttons: Invite (player picker) and Permissions (the matrix) | `skyblock.island` | ✅ |
| `/is bypass` | Staff: toggle ignoring island protection (build, containers, everything; also walk out of borders). Off by default and again on quit; every toggle is logged (`staff`: `island_bypass_on` / `island_bypass_off`) | `skyblock.island.bypass` (admin) | ✅ |

Rules: one island per player (database-enforced), creation cooldown after a disband `creation.cooldown-hours` (24 h),
start size 3x3 chunks (48x48), grows by chunk rings up to 15x15 (`IslandService.setSize`, Phase 5 / M2.7). Only players
on an island see its border. Void falls return you to the island home without damage.
Everything that changes data is logged (category `island`: `island_create`, `island_build`, `island_build_failed`,
`island_sethome`, `island_size`, `island_rescue_block`, `island_perm`, and from M2.3 `island_invite` (sent/denied,
`became_team`), `island_join`, `island_kick`, `island_leave`, `island_promote`, `island_demote`, `island_transfer`,
`island_coop` (add/remove + reason), `island_member_slots`, `island_join_block`).

## Protection (M2.2)
Inside an island every action needs the permission of your role: **Leader** (always everything), **Mod**, **Member**,
**Coop**, or **visitor** (not on the island). Permissions: `break`, `place`, `interact` (doors, trapdoors, gates,
buttons, levers, pressure plates, bells, beds, workstations), `containers`, `redstone` (repeaters, comparators, daylight
detectors, note blocks), `animals`, `villagers` (trade), `pickup`, `drop`, `armor-stands` (also item frames, paintings),
`vehicles`, `fire` (flint and steel, fire charges), `ender-pearls` (pearls and chorus fruit onto the island), `spawners`.
Defaults in `islands.yml → permissions.defaults.*`: Mod everything; Member everything except spawners; Coop building,
farming, containers and redstone but no fire/spawners; visitors nothing (walk and look). Per-island changes are stored in
`island_role_perms` (only differences from the defaults); the permission matrix GUI comes in M2.3, visitor settings in M2.5.

- Nothing can be built outside an island border; blocks only between `build.min-y` and `build.max-y` (Y 0-256).
- Cross-border: liquids, pistons, falling blocks, explosions (TNT, creepers, crystals, beds, anchors), tree/mushroom
  growth, fire, spreading (grass, vines, sculk …), forming and dispensers never change a block outside the island they
  come from (or the void between slots). Explosions can't hurt entities in another island.
- Mobs, animals, items and empty vehicles pushed out of a border are put back (sweep every
  `protection.return-entities.interval-ticks` of the chunk ring around occupied islands); a player inside a border can't
  be pushed or walk out of it (`protection.keep-players-inside`; spectator/creative and bypass excepted).
- PvP is off in the islands world (`protection.pvp`): hits, arrows, harmful potions, fishing rods, fire.
- Nether and end portals can't be built or used in the islands world (`protection.disable-portals`).
- Denials show a short action-bar message, at most one per `protection.message-cooldown-ms` (stepping on plates and item
  pickup are silent).

## Members and roles (M2.3)
Roles: **Leader** (everything; the only one who promotes, demotes and transfers), **Mod** (invite, kick members, coop,
sethome, edit the Member/Coop/Visitor permissions), **Member** (sees the member list and the matrix, can leave), **Coop**
(temporary, never stored). The **permission matrix** (`/is members` → Permissions): rows = Mod, Member, Coop, Visitor;
columns = the 14 actions above, 7 per page. One plain click toggles a cell (Bedrock-safe); a value equal to the server
default is stored as "default" again. The leader always has everything; a mod can't change the Mod row; members see it
read-only. Every change is logged (`island_perm`).

- **Solo → team:** the first invite that is actually sent turns the island into a team island forever (same
  transaction as the invite). Kicking everyone never turns it back; the leader is told so.
- **Limits (REVIEW A5/B1):** `members.start-slots` 4 members including the leader (island column `member_slots`, raised
  by the Phase 5 Member Slots upgrade up to `members.max-slots` 8); a player joins at most
  `members.max-joins-per-season` (3) islands per season (`season-id`) and waits `members.join-cooldown-hours` (24 h)
  after their last join, leave or kick (table `player_island_joins`). Founding an island never counts as a join.
- XP a member earned stays with the island when they leave (A5; implemented with stats in M2.6).
- Online members are told about every member change (invites, joins, leaves, kicks, promotions, transfers, coop).
- Tables (V003): `island_invites`, `island_coops`, `player_island_joins`, `island_join_blocks`, columns
  `islands.member_slots`, `island_members.invited_by`.

Service for other modules: `core.services().optional(IslandService.class)` (island at location, island of player,
members/roles, size, home, slot bounds, `teleportHome`, `setSize`; M2.3: `coops`, `isCoop`, `countsForIsland` (false for
coop players: no XP/bank), `memberSlots`, `setMemberSlots`, and the moderation hooks `removeMember(island, uuid, reason,
actor)` and `blockJoining(uuid, until[, reason, actor])` / `joinBlockedUntil` for Phase P7). Placeholders: `<ph:island_name>`, `<ph:island_level>`
(`-` until Phase 5), `<ph:island_rank>` (empty until the island top exists).

Permission definitions: `modules/islands/src/main/resources/permissions/islands.yml`. Rank grants: `server/permissions/islands.txt`.
