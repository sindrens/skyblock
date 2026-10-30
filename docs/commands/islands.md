# Islands module (`modules/islands`): M2.1, M2.2, M2.3, M2.4, M2.7, ISL2

Grid, creation, home and per-player borders. Config `islands.yml`, texts `messages/islands.yml`, schematics in
`plugins/PhoenixSkyblock/schematics/` (`<creation.schematic>.schem` + optional `<name>.yml` metadata with `home`, `chest`,
`sign` offsets and `home.yaw`; missing file or no FastAsyncWorldEdit = the built-in "Ember Meadow" placeholder islet).

| Command | Description | Permission | Status |
|---|---|---|---|
| `/is` (`/island`) | No island yet: "Create your island" confirm menu, then the starter islet is built (FAWE async or placeholder), the starter chest filled and you are teleported with a title, sound and your first objective. Has an island: teleport home | `skyblock.island` (everyone) | ✅ |
| `/is home [home]` | Teleport to your island home (safe-spot check: unsafe homes move to the nearest safe spot; warmup `home.warmup-seconds`). With a name: one of your own homes (ISL2, tab completes them) | `skyblock.island` | ✅ |
| `/is setmyhome [name]` | ISL2: set your own home on your island where you stand (inside the border, safe spot, Y 0-256). Every member has `homes.per-member` (1), more from masteries later; the same name again moves it. Name default `homes.default-name` (`my`) | `skyblock.island` | ✅ |
| `/is go` | Same as `/is home` (your island is the one you're a member of; visiting others is `/is visit`) | `skyblock.island` | ✅ |
| `/is sethome` | Set the island home where you stand: Leader, Admin, Mod; inside your border, safe spot, Y 0-256 | `skyblock.island` | ✅ |
| `/is invite <player>` | Leader/Admin/Mod: invite an online player without an island. On a **solo** island the leader first sees "Inviting makes your island a Team Island forever. Solo and team islands are on separate leaderboards." (confirm screen; cancel keeps it solo). The target gets a clickable `[Accept] [Deny]`; expires after `members.invite-expiry-seconds` (5 min) | `skyblock.island` | ✅ |
| `/is accept [player]` | Accept your newest invite, or the one from that player's island (inviter or any member). Checks join limits, member slots and the target having no island, all in one transaction | `skyblock.island` | ✅ |
| `/is deny [player]` | Decline your newest invite, or the one from that player's island; the inviter is told | `skyblock.island` | ✅ |
| `/is kick <player>` | Only a higher role kicks: Leader kicks admins/mods/members, Admin mods/members, Mod members (confirm screen). Kicked players are sent to spawn if they stand on the island | `skyblock.island` | ✅ |
| `/is leave` | Leave your island (confirm screen). The leader must `/is transfer` first, or `/is disband` | `skyblock.island` | ✅ |
| `/is promote <player>` | Leader: Member → Mod, Mod → Admin. Admin: Member → Mod | `skyblock.island` | ✅ |
| `/is demote <player>` | Leader: Admin → Mod, Mod → Member. Admin: Mod → Member | `skyblock.island` | ✅ |
| `/is transfer <player>` | Leader only: hand the island to a member (two confirm screens); the old leader becomes an **Admin** (ISL2, owner #23) | `skyblock.island` | ✅ |
| `/is coop <player>` | Leader/Admin/Mod: temporary trust for an online player (coop role permissions). Ends automatically when no leader or mod of the island is online; never turns solo into team; no XP, task progress or bank access; takes no member slot (max `members.max-coops`) | `skyblock.island` | ✅ |
| `/is uncoop <player>` | Leader/Admin/Mod: end a player's coop trust | `skyblock.island` | ✅ |
| `/is members` | Member list GUI: heads with role, join date, inviter, online state; click a head for the actions you may use (promote, demote, transfer, kick, uncoop, leave). Buttons: Invites, Invite (player picker), Permissions (the matrix) and Settings | `skyblock.island` | ✅ |
| `/is invites` | ISL2: invites you received (click: Accept / Deny) and invites your island sent (click: cancel, Leader/Admin/Mod; confirm). Refreshes every 2 s | `skyblock.island` | ✅ |
| `/is settings [setting] [on\|off\|value]` | ISL2: the island settings menu; with a setting name it changes it right away (no value: toggles flip, weather/time go to the next value). Who may change: the `settings` role permission (Leader + Admin by default); every member can look. Tab completes settings and, after one, its values | `skyblock.island` | ✅ |
| `/is disband` | Leader only: confirm screen (what is lost, members removed, creation cooldown), then type `disband` in chat (`disband.confirm-word`, `cancel` keeps it). The island is removed for good: members and coop players are removed and told, everyone on it is sent to spawn, the slot is cleared and reused. New island after `creation.cooldown-hours` (24 h) | `skyblock.island` | ✅ |
| `/is visit [player]` | No name: the **visit browser** (Most visited, Top rated, Most liked, Newest; click an island to visit). With a name (any member of that island, online or offline): teleport to that island's home after a safe-spot check and `visiting.warmup-seconds` (3 s). Your own island = home | `skyblock.island` | ✅ |
| `/is lock` / `/is unlock` | Leader/Mod: only members and coop players can enter a locked island; locking sends visitors on it to spawn. Locked islands are hidden in the browsers | `skyblock.island` | ✅ |
| `/is expel <player>` | Leader/Mod: send a visitor on your island to spawn (tab: visitors on your island) | `skyblock.island` | ✅ |
| `/is ban <player>` / `/is unban <player>` | Leader/Mod: banned players can't enter, teleport or pearl in (any teleport into the slot is refused); banned while inside = sent to spawn. Members must be kicked and coop players uncooped first. Max `visiting.max-bans` (100) | `skyblock.island` | ✅ |
| `/is setwarp <name> [category]` | Leader/Mod: set a warp where you stand (inside the border, safe spot). Categories: `shops`, `farms`, `parkour`, `builds`, `other` (default). The same name again moves the warp. Max `warps.start-slots` (3) per island, sign warps included | `skyblock.island` | ✅ |
| `/is delwarp <name>` | Leader/Mod: delete one of your warps | `skyblock.island` | ✅ |
| `/is warp [player] [name]` | No name: the **warp browser** (public warps of every unlocked island, most visited first, one button per category). Player only: their warp, or their warp list when there are several. Player + name: that warp (private warps only for members) | `skyblock.island` | ✅ |
| `/is rate <player> <1-5>` | Rate an island you visited: one rating per player per island per season, change it any time; never your own island | `skyblock.island` | ✅ |
| `/is like <player>` | Like an island you visited: once per day per island (separate from ratings); never your own island | `skyblock.island` | ✅ |
| `/is bypass` | Staff: toggle ignoring island protection (build, containers, everything; also walk out of borders). Off by default and again on quit; every toggle is logged (`staff`: `island_bypass_on` / `island_bypass_off`) | `skyblock.island.bypass` (admin) | ✅ |

Rules: one island per player (database-enforced), creation cooldown after a disband `creation.cooldown-hours` (24 h),
start size 3x3 chunks (48x48), grows by chunk rings up to 15x15 (`IslandService.setSize`, Phase 5 / `/isadmin setsize`). Only players
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
Roles (ISL2: Admin added, see below): **Leader** (everything; the only one who transfers and disbands), **Mod** (invite, kick members, coop,
sethome, edit the Member/Coop/Visitor permissions), **Member** (sees the member list and the matrix, can leave), **Coop**
(temporary, never stored). The **permission matrix** (`/is members` → Permissions): since ISL2 columns = Admin, Mod,
Member, Coop, Visitor and rows = the actions above plus hurt-monsters and settings, 4 per page. One plain click toggles a cell (Bedrock-safe); a value equal to the server
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

## Lifecycle (M2.7)
- **Removing an island** (`/is disband`, `/isadmin delete`, `/isadmin reset`) is one transaction: the `islands` row gets
  `deleted_at`, `deleted_reason` (`disband` | `delete` | `reset`) and `deleted_by` and is kept with its `season_id` for
  history; memberships end (`player_island_joins.left_reason = disband`, never starts the join cooldown), coop players,
  invites and permission overrides are removed and the slot goes to `clearing`. Only `disband` starts the creation
  cooldown. Island money/bank: decided with the island bank (M3.3).
- **Slot states** (`island_slots.state`): `used` → `clearing` → `free` → `used`. A clearing slot is never handed out;
  free slots are reused (lowest id first) before a new spiral slot is added.
- **Clearer:** empties the max island size + `lifecycle.clear.extra-ring-chunks` chunk rings around the slot center,
  full world height, in slices of `lifecycle.clear.chunks-per-slice` chunks every `lifecycle.clear.ticks-between-slices`
  ticks. With FastAsyncWorldEdit (`lifecycle.clear.use-fawe`) off the main thread; otherwise (or when a FAWE step fails or
  takes longer than `lifecycle.clear.fawe-timeout-seconds`) a built-in clearer that skips empty sections and checks at
  most `lifecycle.clear.fallback-blocks-per-tick` positions per tick. Then every non-player entity in those chunks is
  removed. Never-generated chunks are skipped, only one slice of chunks is loaded at a time (offline islands are never
  kept loaded), and unfinished slots resume after a restart (`lifecycle.clear.start-delay-seconds`).
- Logged (category `island`): `island_disband`, `island_delete`, `island_reset` (members, coops, slot, owner),
  `island_clear_start`, `island_clear_error`, `island_slot_freed` (blocks, entities, time, mode).

## Staff tools: `/isadmin` (M2.7)
`<island>` = the owner's or any member's name (tab completes island owners). Destructive sub-commands open a confirm
screen for players and need `/isadmin … confirm` (30 s) from the console. Every use is logged in category `staff`
(`isadmin_<sub>`); data changes are also logged in category `island` with the staff member as actor.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/isadmin info <island>` | Owner, type, season, slot + grid + center, size, members with roles and join dates, coop players, created, home, id; clickable Teleport / Slots / Size | `skyblock.island.admin.info` (helper) | ✅ |
| `/isadmin tp <island>` | Teleport to the island home (center if not built yet) | `skyblock.island.admin.tp` (helper) | ✅ |
| `/isadmin delete <island>` | Remove the island for good (confirm); members told, slot cleared and reused; no creation cooldown for the owner | `skyblock.island.admin.delete` (admin) | ✅ |
| `/isadmin setsize <island> <size>` | Border width in chunks (odd, 1 to `size.max-chunks`); shown at once | `skyblock.island.admin.setsize` (admin) | ✅ |
| `/isadmin addmember <island> <player>` | Add a player as Member without an invite (member slots and one island per player still apply; no join limit/cooldown, doesn't count as a join; makes a solo island a team island) | `skyblock.island.admin.addmember` (admin) | ✅ |
| `/isadmin kick <player>` | Remove a member or coop player from their island (confirm). Leaders: `setowner` first or `delete` | `skyblock.island.admin.kick` (admin) | ✅ |
| `/isadmin setowner <island> <member>` | Make a member the leader (confirm); the old leader becomes an Admin | `skyblock.island.admin.setowner` (admin) | ✅ |
| `/isadmin reset <island>` | Remove the island and give its leader a new starter island right away (built now if online, else on their next `/is`); other members removed (confirm) | `skyblock.island.admin.reset` (admin) | ✅ |
| `/isadmin bypass` | Same as `/is bypass` | `skyblock.island.bypass` (admin) | ✅ |
| `/isadmin slots` | Grid overview GUI (spiral order): used (owner, members, size), clearing (progress), free; filter and reload buttons; click = teleport | `skyblock.island.admin.slots` (admin) | ✅ |
| `/isadmin teleport-slot <slot>` | Teleport to the top of a slot's center column (tab completes slot ids) | `skyblock.island.admin.teleport-slot` (admin) | ✅ |

## Visiting, warps and showcase (M2.4)
- **Who enters:** members and coop players always; everyone else unless banned (checked first) or the island is locked.
  Staff with `skyblock.island.visit.bypass` (helper and up), the `/is bypass` toggle or spectator mode ignore locks and
  bans. Enforced on every teleport into an island's slot (commands, `/tpa`, ender pearls, chorus fruit, plugins: the
  essentials TeleportService guard plus a teleport listener), when flying in across the void (stopped at the slot edge)
  and on login (sent to spawn).
- **Member alerts** (chat, each a `/settings` toggle in Social, all on): someone tried to visit while locked (with an
  [Unlock] button), a visitor arrived, a visitor left. The same visitor is announced at most every
  `visiting.notify-cooldown-seconds` (30 s) / `visiting.attempt-cooldown-seconds` (60 s). Vanished staff and
  spectators are never announced or counted. Visitors see "Visiting <island>" in the action bar.
- **Visit counter:** unique visitors per island and day (members never count; day = `visiting.timezone`, Europe/Oslo).
- **Warps:** 3 per island (`warps.start-slots`; island column `warp_slots` for a later upgrade, cap `warps.max-slots`
  10). Names: letters, digits, `_`, `-`, up to `warps.name-max-length` (16), unique per island ignoring case. Icon
  (picker from `warps.icons`; new warps get their category's icon from `warps.category-icons.*`), public/private,
  category and delete in the warp edit menu (`/is warp` → Your warps → click a warp). Teleports check the spot is safe
  (else the nearest safe spot inside the border). Warp visits count once per visitor per day.
- **Sign warps:** a sign with `[warp]` (`warps.sign-tag`) on line 1, the name on line 2 and optionally a category on
  line 3, written by the leader or a mod inside their border, creates a warp at the spot where they stand (counts toward
  the slots). The sign is rewritten to show the warp; editing it is blocked; breaking it deletes the warp (a sign
  destroyed another way removes its warp the next time someone uses it).
- **Ratings and likes:** `/is rate` 1-5 stars (one per player per island per season, changeable), `/is like` once per
  player per island per day (owner O7, per account). Both need a visit first (`visiting.require-visit`) and never work on
  your own island; members are told about likes.
- **Browsers** (6-row redesigned menus, paged, one plain click everywhere for Bedrock): visit browser tabs Most visited
  (unique visitors per day over `browser.visits-days` = 7 days), Top rated (season average, at least
  `browser.min-ratings` = 3 ratings), Most liked (season), Newest. Totals are reloaded every `browser.refresh-seconds`
  (120 s) and updated at once for your own rating/like/visit. `/menu` → Warps opens the warp browser.
- **Tables (V005):** `islands.locked`, `islands.warp_slots`, `island_bans`, `island_warps`, `island_warp_visits`,
  `island_visits`, `island_ratings`, `island_likes`. Removing an island (M2.7) deletes its warps and bans; visits,
  ratings and likes stay as history.
- **Logged** (category `island`): `island_lock`, `island_unlock`, `island_expel`, `island_ban`, `island_unban`,
  `island_warp_set` (create/move, sign), `island_warp_delete` (command/menu/sign-broken/sign-missing), `island_warp_icon`,
  `island_warp_public`, `island_warp_category`, `island_warp_visit`, `island_visit` (first visit of the day),
  `island_rate` (old/new stars), `island_like`.

## Islands round 2 (ISL2)
- **Admin role** (owner #23) between Leader and Mod: every Mod right, plus editing the Mod column of the permission
  matrix and promoting/demoting Member ↔ Mod; kicks mods and members. Only the leader makes or removes admins,
  transfers and disbands. After `/is transfer` (and `/isadmin setowner`) the old leader becomes Admin. Stored as `admin`
  in `island_members.role` and `island_role_perms.role` (migration V006 also turns unknown roles into `member`).
  Defaults: `permissions.defaults.admin` (`*`).
- **Permission matrix:** columns = Admin, Mod, Member, Coop, Visitor; rows = actions, 4 per page. New actions:
  **Hurt monsters** (`hurt-monsters`, owner #25: hitting, shooting, fire aspect and fishing rods on hostile mobs; default
  every role and visitors, `permissions.role-defaults.hurt-monsters`) and **Island settings** (`settings`: may change
  `/is settings`; default Leader + Admin, `permissions.role-defaults.settings`; only for Admin/Mod/Member and only given
  by someone who has it). `*` in a role list never includes these two, so older `islands.yml` files keep working.
- **Join cooldown** (owner #20): `members.join-cooldown-hours` (24 h) counts from the last join only; leaving or being
  kicked never restarts it. `/is leave` tells the time that is left.
- **`/is settings`** (owner round 3; defaults in `islands.yml → flags.defaults.*`, island changes in `island_settings`,
  only differences from the defaults): mob spawning (off), animal spawning (on), fire spread (off), explosions break
  blocks (off), leaf decay (on), crop trampling (off; protects farmland from players and mobs), visitors pick up items
  (off; the same switch as the Visitor "Pick up items" matrix cell), visitor alerts (on; members hear about visitors
  arriving/leaving/knocking, each member can still mute them in `/settings`), visitor fly (on), weather (sunny, rain,
  storm; default sunny) and time (day, noon, sunset, night, real; default day). Weather and time are client-side (player
  weather/time) for everyone in the island's slot and reset when they leave; "storm" = rain + thunder sounds only they
  hear (`flags.weather.thunder-*`), "real" = the server clock in `visiting.timezone` (refreshed every
  `flags.time.real-refresh-seconds`); tick values in `flags.time.*-ticks`. Natural spawning counts NATURAL, patrol,
  reinforcement, trap, jockey, mount and village-invasion spawns; spawners, eggs, breeding and commands always work. The
  islands world has natural spawning and fire spread on in `worlds.yml`; the module switches them off per island. Mob
  stacking is not a setting. Changes are logged (`island_setting`, or `island_perm` for visitor pickup) and announced
  to the members.
- **Island fly** (owner I4): `skyblock.island.fly` (no rank yet) or the island's Wings mastery
  (`IslandService.hasWings`, false until the masteries exist) lets you fly inside an island's border (double-tap jump):
  members and coop players always, visitors while the island's visitor-fly setting is on. Leaving the border, losing the
  permission or a setting change turns it off at once; the next fall within `fly.landing-protection-seconds` (10) does
  no damage (also after you stop flying yourself). Flight from creative, staff mode or freeze is never touched.
  Checked on every block move plus every `fly.check-interval-ticks` (20).
- **Personal homes** (owner I13): see `/is setmyhome`; table `island_member_homes`; removed when the member leaves or is
  removed and with the island. Mastery hook: `IslandService.extraMemberHomes` / `extraHomesProvider`.
- **Invites menu** (owner O17): `/is invites`, also from `/is members`. Cancelling is logged (`island_invite`,
  `result: cancelled`).
- **Logged** (category `island`): `island_setting`, `island_member_home` (set/move), `island_invite` (cancelled), plus
  the existing role/permission events with the new values.
- **Service hooks:** `hasWings(island)`, `wingsProvider(predicate)`, `extraMemberHomes(island, player)`,
  `extraHomesProvider(function)`; `IslandService.Role` gained `ADMIN`.

Permission definitions: `modules/islands/src/main/resources/permissions/islands.yml`. Rank grants: `server/permissions/islands.txt`.
