# Islands module (`modules/islands`): M2.1, M2.2, M2.3, M2.4, M2.5, M2.6, M2.7, ISL2, NOTES

Grid, creation, home and per-player borders. Config `islands.yml`, texts `messages/islands.yml`, schematics in
`plugins/PhoenixSkyblock/schematics/` (`<creation.schematic>.schem` + optional `<name>.yml` metadata with `home`, `chest`,
`sign` offsets and `home.yaw`; missing file or no FastAsyncWorldEdit = the built-in "Ember Meadow" placeholder islet).


**Moderation hooks (Phase P7, spec 13.2):** island punishments from the `/p` ladders. Island ban: the player can't enter that one island (members too; staff with the visit bypass still can), enforced by the same guards as visitor bans (teleports, pearls, walking/flying in, logging in there) and saved in `island_staff_bans` (never deleted; lifted rows keep `lifted_at`). Island kick: sent off the island now. Coop remove: removed from the team (`IslandService.removeMember`). Coop block (`IslandService.blockJoining`): can't accept invites, be cooped or create an island (`create.blocked`). Audit: `island_staff_ban`, `island_staff_unban`, `island_staff_kick`, `island_join_block`.
| Command | Description | Permission | Status |
|---|---|---|---|
| `/is` (`/island`) | No island yet: "Create your island" confirm menu, then the starter islet is built (FAWE async or placeholder), the starter chest filled and you are teleported with a title, sound and your first objective. Has an island: the **island menu** (M2.5, see below; `/is home` teleports) | `skyblock.island` (everyone) | ✅ |
| `/is home [name]` | Teleport to your island home (safe-spot check: unsafe homes move to the nearest safe spot; warmup `teleport.yml → island-home.warmup-seconds`). With a name: one of the island's **shared homes** (M5.5 Homestead, owner P5-21 / R3-17), for every member. Tab: the island's home names. Personal member homes and `/is setmyhome` stay removed (owner answer 247) | `skyblock.island` | ✅ |
| `/home [name]` · `/sethome [name]` | Owner answer 347: same as `/is home [name]` and `/is sethome [name]` (staff homes are `/shome`). Tab: home names | `skyblock.island` | ✅ |
| `/is go` | Same as `/is home` (your island is the one you're a member of; visiting others is `/is visit`) | `skyblock.island` | ✅ |
| `/is sethome [name]` | Set the island home where you stand; with a name, set (or move) a **shared island home** (M5.5 Homestead: the island home is free, +1 named home per Homestead rank). Island permission `homes` (M5.5: default Leader, Admin, Mod, as before; the leader changes it in `/is permissions`); inside your border, safe spot, Y 0-256. Names: 1-16 of a-z, 0-9, _. Tab: existing names (to move one) and a suggestion while a slot is free | `skyblock.island` | ✅ |
| `/is delhome <name>` | M5.5: delete a shared island home (island permission `homes`); the island home itself can't be deleted. Tab: the island's home names | `skyblock.island` | ✅ |
| `/is invite <player>` | Leader/Admin/Mod: invite an online player without an island. On a **solo** island the leader first sees "Inviting makes your island a Team Island forever. Solo and team islands are on separate leaderboards." (confirm screen; cancel keeps it solo). The target gets a clickable `[Accept] [Deny]`; expires after `members.invite-expiry-seconds` (5 min). A player who `/ignore`s you can't be invited (same answer as "invites off"; owner answer 316). Owner F10: after a member leaves or is kicked that slot stays **locked 48 h** (`members.slot-lock-hours`); an invite into a locked slot says "A slot opens again in 31h 12m — a member left recently" | `skyblock.island` | ✅ |
| `/is accept [player]` | Accept your newest invite, or the one from that player's island (inviter or any member). Checks join limits, member slots, F10 slot locks and the target having no island, all in one transaction | `skyblock.island` | ✅ |
| `/is deny [player]` | Decline your newest invite, or the one from that player's island; the inviter is told | `skyblock.island` | ✅ |
| `/is kick <player>` | Only a higher role kicks: Leader kicks admins/mods/members, Admin mods/members, Mod members (confirm screen). Kicked players are sent to spawn if they stand on the island. Locks the freed slot 48 h (F10), unless the member was offline 21+ days (`members.slot-lock-inactive-days`) | `skyblock.island` | ✅ |
| `/is leave` | Leave your island (confirm screen). The leader must `/is transfer` first, or `/is disband`. No loss for the island; the freed slot is locked 48 h before a new invite (F10) | `skyblock.island` | ✅ |
| `/is promote <player>` | Leader: Member → Mod, Mod → Admin. Admin: Member → Mod | `skyblock.island` | ✅ |
| `/is demote <player>` | Leader: Admin → Mod, Mod → Member. Admin: Mod → Member | `skyblock.island` | ✅ |
| `/is transfer <player>` | Leader only: hand the island to a member (two confirm screens); the old leader becomes an **Admin** (ISL2, owner #23) | `skyblock.island` | ✅ |
| `/is coop <player>` | Leader/Admin/Mod: temporary trust for an online player (coop role permissions). Ends automatically when no leader or mod of the island is online; never turns solo into team; no XP, task progress or bank access; takes no member slot (max `members.max-coops`) | `skyblock.island` | ✅ |
| `/is uncoop <player>` | Leader/Admin/Mod: end a player's coop trust | `skyblock.island` | ✅ |
| `/is members` | Member list GUI: heads with role, join date, inviter, online state; click a head for the actions you may use (promote, demote, transfer, kick, uncoop, leave). Buttons: Invites, Invite (player picker), Permissions (the matrix) and Settings | `skyblock.island` | ✅ |
| `/is notes [island]` | NOTES (owner N1-N9): your island's shared notes / to-do list menu (also from `/is members`). Add a note (chat input, max `notes.max-length` 200 characters, chat filter), click a note to tick it done / open it again, change its text, pin it as a hologram where you stand, unpin or delete it (confirm). Open as book (temporary book, saved on close), Clear done notes (manage). Without an island, opens the notes of the island you stand on when your role may read them (coop). `[island]` = staff only: any island's notes read only plus its deleted notes (tab completes island names for staff) | `skyblock.island`; `[island]`: `skyblock.island.notes.staff` | ✅ |
| `/is chat [message]` | M2.5: island chat. Without text: switch your chat to your island and back (toggle mode; also the Island chat button in the menu). With text: one message to your island. Members only (Leader, Admin, Mod, Member; coop players and visitors don't hear it). Lines are `[Island] <role> <name> » text` in the role's color (the leader picks role colors in the menu). Uses the chat module's checks: mute, rate limit, duplicates, word/link filter, `&` colors by permission, `[item]`. Tab: example texts | `skyblock.island` | ✅ |
| `/is info [player]` | M2.5: your island, or that player's island, in chat: name, solo/team, open/locked, leader, members, size, island level (M5.2), warps, created date and your role there. Tab: island names | `skyblock.island` | ✅ |
| `/is stats` | M2.6: your island's **stats menu** (also the Stats button of the island menu): per category all time, today, this week, last week and the change vs last week; click a category for each member's part (heads, share in %, this week, today; former members keep theirs). Categories without their feature yet show `-` (Coming soon). Members only; reloads every 10 s while open | `skyblock.island.stats` (everyone) | ✅ |
| `/is level` | M5.2 (owner P5-25): opens the island level menu of the progression module (points per source, Recalculate, pillars); also the island item in the island menu. See `docs/commands/progression.md` | `skyblock.progression.level` (everyone, progression module) | ✅ |
| `/is top` | M5.2 (owner P5-25): island leaderboards of the progression module (Level/Value, Solo/Team) | `skyblock.progression.top` (everyone, progression module) | ✅ |
| `/is upgrade` | M5.4: island upgrades of the progression module (tabs Island, Economy, Depths, Production, Masteries; tasks + island bank money; buy needs the island permission `upgrades`); also the Upgrades button of the island menu and the Upgrades source in `/is level`. See `docs/commands/progression.md` | `skyblock.progression.upgrade` (everyone, progression module) | ✅ |
| `/is masteries` | M5.5: the same menu on its Masteries tab (masteries + Ascension, island bank). See `docs/commands/progression.md` | `skyblock.progression.upgrade` (everyone, progression module) | ✅ |
| `/is invites` | ISL2: invites you received (click: Accept / Deny) and invites your island sent (click: cancel, Leader/Admin/Mod; confirm). Refreshes every 2 s | `skyblock.island` | ✅ |
| `/is settings [setting] [on\|off\|value]` | ISL2: the island settings menu; with a setting name it changes it right away (no value: toggles flip, weather/time go to the next value). Who may change: the `settings` role permission (Leader + Admin by default); every member can look. Tab completes settings and, after one, its values | `skyblock.island` | ✅ |
| `/is disband` | Leader only, and only while nobody else is a member (owner answer 141: `/is transfer` first, then `/is leave`); two confirm screens (GUI-1, buttons swap sides; owner answer 261, no typed word). Owner R3-30: the middle item shows the real amounts you lose: island bank, your money and items (inventory + ender chest, while the profile reset is on), spawners on the island and the island level. The island is removed for good: coop players are told, everyone on it is sent to spawn, the slot is cleared and reused, and the island bank money is deleted (owner answer 263). A new island right away (no cooldown). Owner answer 591: this profile's money, inventory and ender chest are reset (staff keep a restorable backup) | `skyblock.island` | ✅ |
| `/is visit [player]` | No name: the **visit browser** (Discover (default: every island, random, favouring more playtime), Most visited, Top rated, Most liked, Newest; click an island to visit). With a name (any member of that island, online or offline): teleport to that island's home after a safe-spot check, instantly (owner answer 285; 3 s `teleport.yml → danger.warmup-seconds` while combat-tagged or in a combat zone). No visitor limit (owner answer 291). Your own island = home. Names, visitor notices and tab lists use `Visibility.knows` (VIS): players hidden by the AFK pool or "Show players" can still be visited by name; only vanished staff are unknown. An island whose **leader is banned** is closed to visitors (owner answer 522: hidden from the browser, visits/warps refused with "closed to visitors", visitors on it sent to spawn at the ban; members and coop keep playing; `visiting.close-banned-leader-islands`, reopened within `visiting.banned-leader-refresh-seconds` after the ban ends, uses moderation's `BanStatus`) | `skyblock.island` | ✅ |
| `/is lock` / `/is unlock` | Leader/Mod: only members and coop players can enter a locked island; locking sends visitors on it to spawn. Locked islands are hidden in the browsers | `skyblock.island` | ✅ |
| `/is expel <player>` | Leader/Mod: send a visitor on your island to spawn (tab: visitors on your island) | `skyblock.island` | ✅ |
| `/is ban <player>` / `/is unban <player>` | Leader/Mod: banned players can't enter, teleport or pearl in (any teleport into the slot is refused); banned while inside = sent to spawn. Members must be kicked and coop players uncooped first. Max `visiting.max-bans` (100) | `skyblock.island` | ✅ |
| `/is setwarp <name> [category]` | Leader/Mod: set a warp where you stand (inside the border, safe spot). Categories: `shops`, `farms`, `parkour`, `builds`, `other` (default). The same name again moves the warp. Max `warps.start-slots` (3) per island, sign warps included | `skyblock.island` | ✅ |
| `/is delwarp <name>` | Leader/Mod: delete one of your warps | `skyblock.island` | ✅ |
| `/is warp [player] [name]` | No name: the **warp browser** (public warps of every unlocked island, most visited first, one button per category). Player only: their warp, or their warp list when there are several. Player + name: that warp (private warps only for members) | `skyblock.island` | ✅ |
| `/is rate <player> <1-5>` | Rate an island you visited: one rating per player per island per season, change it any time; never your own island; needs 1 h playtime and at most 3 accounts from one address per island (owner answer 292) | `skyblock.island` | ✅ |
| `/is like <player>` | Like an island you visited: once per day per island (separate from ratings); never your own island; same playtime and address rules as rating | `skyblock.island` | ✅ |
| `/is bypass` | Staff: toggle ignoring island protection (build, containers, everything; also walk out of borders). Off by default and again on quit; every toggle is logged (`staff`: `island_bypass_on` / `island_bypass_off`) | `skyblock.island.bypass` (admin) | ✅ |

Rules: one island per player (database-enforced), no creation or join cooldown after a leave, kick, disband, staff
delete or reset (owner answers 121/161/258/259, principle 1; the dead key `members.join-cooldown-hours` is removed on
load). Owner F10: a leave or kick locks the freed member slot for `members.slot-lock-hours` (48 h; table
`island_slot_locks`, V016); staff removals (`/isadmin kick`, punishments, deleted profiles) and kicks of members offline
`members.slot-lock-inactive-days` (21) days don't lock; `/is members` shows locked slots; staff `/isadmin addmember`
ignores locks, start size 3x3 chunks (48x48). Normal Island Size upgrades reach
`size.normal-max-chunks` (9x9, owner answer 157); bigger sizes up to `size.max-chunks` (15) only with gems or from the
black market (hook for Phase 5; `/isadmin setsize` can set up to `size.max-chunks`). Upgrades are paid from the island
bank only (`upgrades.island-bank-only`, owner answer 169; `/is upgrade`, PHASE-5 M5.4, raises size, member slots and
warp slots through `IslandService#setSize`, `#setMemberSlots` and `#setWarpSlots`, logged `island_size`,
`island_member_slots`, `island_warp_slots`). Players always respawn at
spawn after dying, never at the island home (essentials, owner answer 160). Only players
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
- **Limits (REVIEW A5/B1, owner answer 242):** `members.start-slots` 5 = 4 members plus the leader (island column
  `member_slots`, raised by the Phase 5 Member Slots upgrade up to `members.max-slots` 9 = 8 plus the leader; old files
  with the untouched defaults 4/8 are moved to 5/9 once, `config-version` 2); a player joins at most
  `members.max-joins-per-season` (0 = unlimited, owner N2-1) islands per season (`season-id`, table `player_island_joins`). No join cooldown.
  Founding an island never counts as a join.
- **Kick backups (owner answer 251, SECFIX1 F-02):** kicking a stored member (`/is kick`, `/isadmin kick`, moderation
  removals) saves a backup in `island_kick_backups` (V012): island and role only. A kick takes only the membership (the
  player keeps their money and items), so a restore gives back only the membership (logged `island_kick_backup`). Admins
  restore it with `/isadmin restore <player>`. Old kick backups that still hold money or items never give them back
  (that was a dupe: spend everything, ask for a restore).
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
  history; memberships end (`player_island_joins.left_reason = disband`), coop players, invites and permission
  overrides are removed and the slot goes to `clearing`. No creation cooldown after any of them. Island bank: a disband
  or a reset deletes the money (owner answers 263/591, ledger reason `bank_island_deleted`); a staff delete keeps it in
  the closed bank for staff to decide.
- **Profile reset (owner answer 591):** a disband (`lifecycle.profile-reset.on-disband`) or `/isadmin reset`
  (`on-reset`) resets every member profile of the island: first a backup row per member in `island_kick_backups`
  (removal `island_disband` / `island_reset`: role, money, and inventory + ender chest when the member is online on
  that profile; the leader's row also keeps the island bank money, V014 `bank_money`), then the profile's money is
  taken (`lifecycle.profile-reset.money`, ledger `island_profile_reset`, never more than was backed up; SECFIX1 F-02: the
  amount really withdrawn is recorded in `money_taken`, V015) and the captured inventory and ender chest are emptied
  (`lifecycle.profile-reset.items`; only when they still hold exactly what was saved, otherwise the backup drops its
  items so nothing can be doubled). Items are saved as pending first and become restorable only after the inventory was
  emptied and the player file saved (a crash in between leaves "Pending: check by hand" in the menu, never a restorable
  copy next to the player's items). No backup saved = no reset. Members are told; logged `island_profile_reset`. Admins
  restore with `/isadmin restore <player>`: exactly the money that was taken (`money_taken`, ledger
  `island_kick_restore`, once; never a top-up to the old balance), the island bank money paid into that profile's purse
  (ledger `island_bank_restore`, once), items into an empty inventory (player file saved right after); logged
  `island_reset_restore`. Skills, Depths unlocks and other per-profile progress are not reset yet (open question).
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
| `/isadmin info <island>` | Owner, type, season, slot + grid + center, size, members with roles and join dates, coop players, created, home, id, hoppers in the busiest loaded chunk (F13, warning above `stacking.yml → hoppers.alert-above`); clickable Teleport / Slots / Size | `skyblock.island.admin.info` (helper) | ✅ |
| `/isadmin tp <island>` | Teleport to the island home (center if not built yet) | `skyblock.island.admin.tp` (helper) | ✅ |
| `/isadmin delete <island>` | Remove the island for good (confirm); members told, slot cleared and reused; no creation cooldown for the owner | `skyblock.island.admin.delete` (admin) | ✅ |
| `/isadmin setsize <island> <size>` | Border width in chunks (odd, 1 to `size.max-chunks`); shown at once | `skyblock.island.admin.setsize` (admin) | ✅ |
| `/isadmin addmember <island> <player>` | Add a player as Member without an invite (member slots and one island per player still apply; no join limit/cooldown, doesn't count as a join; makes a solo island a team island) | `skyblock.island.admin.addmember` (admin) | ✅ |
| `/isadmin kick <player>` | Remove a member or coop player from their island (confirm). Leaders: `setowner` first or `delete` | `skyblock.island.admin.kick` (admin) | ✅ |
| `/isadmin setowner <island> <member>` | Make a member the leader (confirm); the old leader becomes an Admin | `skyblock.island.admin.setowner` (admin) | ✅ |
| `/isadmin reset <island>` | Remove the island and give its leader a new starter island right away (built now if online, else on their next `/is`); other members removed (confirm). Owner answer 591: every member profile is reset and the island bank money deleted, with restorable backups (`/isadmin restore`) | `skyblock.island.admin.reset` (admin) | ✅ |
| `/isadmin hide <island> [note]` | Owner answer 293: hide an island from the visit browser (it can still be visited by name), with an optional note shown in `/isadmin info`. Logged `island_browser_hide` | `skyblock.island.admin.hide` (mod) | ✅ |
| `/isadmin unhide <island>` | Show a hidden island in the visit browser again (tab completes hidden islands). Logged `island_browser_unhide` | `skyblock.island.admin.hide` (mod) | ✅ |
| `/isadmin restore <player>` | Owner answer 251: the player's backups (chest menu, newest first: island, role, money back, items, who kicked, state). SECFIX1 F-02: a backup gives back only what was taken, once. Kick: click → confirm → the profile joins the island again with its old role (once; slots and one island per profile apply, a refused restore stays open); no money, no items (a kick takes none). Disband/reset: the money the reset withdrew (once, ledger `island_kick_restore`), the island bank money, and the saved inventory + ender chest only into an empty inventory and ender chest of that profile while it is online (else they wait; click again later). Tab completes players with open backups. Logged `isadmin_restore`, `island_kick_restore`, `island_reset_restore`, `island_kick_restore_items` | `skyblock.island.admin.restore` (admin) | ✅ |
| `/isadmin bypass` | Same as `/is bypass` | `skyblock.island.bypass` (admin) | ✅ |
| `/isadmin slots` | Grid overview GUI (spiral order): used (owner, members, size), clearing (progress), free; filter and reload buttons; click = teleport | `skyblock.island.admin.slots` (admin) | ✅ |
| `/isadmin teleport-slot <slot>` | Teleport to the top of a slot's center column (tab completes slot ids) | `skyblock.island.admin.teleport-slot` (admin) | ✅ |
| `/isadmin stats view <island>` | M2.6: open any island's stats menu read only (logged `isadmin_stats_view`) | `skyblock.island.admin.stats` (helper) | ✅ |
| `/isadmin stats reset <island>` | M2.6: delete all stats of an island: day, week and all-time counters plus the ones not saved yet (confirm `/isadmin stats reset <island> confirm` within 30 s; logged `isadmin_stats_reset` with the rows removed) | `skyblock.island.admin.stats.reset` (admin) | ✅ |

## Visiting, warps and showcase (M2.4)
- **Who enters:** members and coop players always; everyone else unless banned (checked first) or the island is locked.
  Staff with `skyblock.island.visit.bypass` (helper and up), the `/is bypass` toggle or spectator mode ignore locks and
  bans. Enforced on every teleport into an island's slot (commands, `/tpa`, ender pearls, chorus fruit, plugins: the
  core TeleportService guard plus a teleport listener), when flying in across the void (stopped at the slot edge)
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
  your own island; members are told about likes. Owner answer 292: both need `visiting.vote-min-playtime-minutes` (60)
  of playtime, and at most `visiting.vote-max-accounts-per-ip` (3) accounts from one address may rate (and, separately,
  like) the same island per season; only a SHA-256 hash of the address is stored (V013 `ip_hash`). Changing your own
  rating is always allowed.
- **Browsers** (6-row redesigned menus, paged, one plain click everywhere for Bedrock): visit browser tabs Discover
  (default, owner answer 290: every built, unlocked island that staff did not hide, in random order per open; weight
  1 + `browser.discover-playtime-weight` x ln(1 + island playtime hours), so active islands show up more), Most visited
  (unique visitors per day over `browser.visits-days` = 7 days), Top rated (owner R3-33: ratings from at least
  `browser.top-rated-min-ips` = 5 different addresses and `browser.min-ratings` = 3 ratings; ranked by a smoothed
  Bayesian average, `browser.top-rated-prior-weight` = 5 virtual ratings at the server-wide season mean), Most liked (season), Newest. Totals are reloaded every `browser.refresh-seconds`
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
- **Join cooldown:** removed (owner answers 121/161/258/259, round 1); the old `members.join-cooldown-hours` and
  `creation.cooldown-hours` keys are ignored.
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
- **Personal homes:** removed (owner answer 247): no `/is setmyhome`, no `/is home <name>`; the table
  `island_member_homes` is dropped by V012 and the old `homes.*` keys are removed from islands.yml on load (REV3-FIX). `IslandService.extraMemberHomes` stays in
  the shared API but is unused.
- **Invites menu** (owner O17): `/is invites`, also from `/is members`. Cancelling is logged (`island_invite`,
  `result: cancelled`).
- **Logged** (category `island`): `island_setting`, `island_invite` (cancelled), plus
  the existing role/permission events with the new values.
- **Service hooks:** `hasWings(island)`, `wingsProvider(predicate)`, `extraMemberHomes(island, player)`,
  `extraHomesProvider(function)`; `IslandService.Role` gained `ADMIN`.

Permission definitions: `modules/islands/src/main/resources/permissions/islands.yml`. Rank grants: `server/permissions/islands.txt`.

## Island notes (NOTES)
Owner answers N1-N9 (`docs/DECISIONS_2026-10-30.md`, `docs/QUESTIONS_ROUND2.md` Part 3). Table `island_notes` (V008).
- **Who:** three new rows in the permission matrix (own defaults, never part of `*`): **Read notes** (`notes-read`:
  Admin, Mod, Member, Coop), **Write notes** (`notes-write`: add notes, tick any note done/open, change/pin/unpin/delete
  your own; Admin, Mod, Member) and **Manage notes** (`notes-manage`: change/pin/unpin/delete anyone's notes, clear done
  ones; Admin, Mod). Write and Manage are management rights (stored members only). The leader has all three. Config:
  `permissions.role-defaults.notes-read|notes-write|notes-manage`.
- **Notes:** one shared list per island (N2), max `notes.max-notes` (50, open + done; deleted ones don't count), each
  max `notes.max-length` (200) characters (N6), author (profile key, name shown) and date, who ticked it and when (N4).
  Text goes through the chat filter (links, IPs, phone numbers, blocked/replaced words, caps; `TextFilter` service from
  the chat module, N8); colors and line breaks are removed; text is never parsed as formatting. Anti-spam:
  `notes.add-cooldown-ms`.
- **Menu:** open notes newest first, then done ones; summary (open/done, x/50, pins x/5), Open as book (46), Add (47),
  Clear done (51, manage), Deleted notes (52, staff). Click a note: Tick done / Open again, Change text, Pin here / Move
  pin here, Unpin, Delete (confirm). All single plain clicks (Bedrock). Lore width `notes.lore-width`.
- **Temporary book (N3):** writers get a Book and Quill with one open note per page plus an empty page; closing it (Done,
  sign or Escape) saves in one transaction: changed pages change their notes (own, or anyone's with manage), new pages
  become notes up to the limit, emptied pages are ignored (deleting is done in the menu). Readers get a written book with
  every note. The book is taken back after saving, after `notes.book-seconds` (300), on quit, death, profile switch,
  island removal and module disable; it can't be moved, dropped, crafted with, put in frames/lecterns/bookshelves or
  given to allays. A book without a live session (after a crash) is removed on join.
- **Holograms (N9):** pin a note where you stand (inside your island's border, Y `build.min-y`..`build.max-y`), at most
  `notes.max-holograms` (5) per island. Paper `TextDisplay`, not saved with the chunk (persistent = false), spawned when
  the island's notes load and when the pin's chunk loads, removed on chunk unload, unpin, tick done (N9), delete,
  island removal and module disable. Never shown outside its own island (re-checked on every spawn). Settings:
  `notes.hologram.y-offset`, `line-width`, `view-range`. Holograms count toward the stacking module's display limit.
- **Notify (N5):** online members and coop players who can read get "X added a note: ... [Open]" (`/settings` toggle
  "New island notes", on). Placeholder `island_next_note` = the newest open note of your island when your `/settings`
  toggle "Next note on scoreboard" is on (off by default; the scoreboard line itself is for the hud module).
- **Deleted notes (N7):** deleting, clearing done notes and removing the island (disband, staff delete, reset) only mark
  notes deleted; staff see them in the Deleted notes view for `notes.deleted-keep-days` (7) days, then they are removed
  for good (checked on enable and daily).
- **Logged** (category `island`): `island_note_add`, `island_note_edit`, `island_note_done`, `island_note_reopen`,
  `island_note_pin`, `island_note_unpin`, `island_note_delete`, `island_note_clear`, `island_note_book`,
  `island_note_purge`, all with the full text; `island_note_blocked` (category `chat`) for filtered text;
  `island_notes_view` (category `staff`) when staff open another island's notes.

## Island menu and island chat (M2.5)
- **`/is` with an island opens the island menu** (6 rows, UI_STYLE frame, one plain click per button, Bedrock-safe; refreshes every 2 s):
  4 island info (name, your role, leader, solo/team, members, size, warps, level, created, open/locked) ·
  10 Home (teleport) · 11 Members (`/is members`) · 12 Permissions (the matrix) · 13 Warps (your island's warps) ·
  14 Visitors (open/locked, visitors on it now, bans; Leader/Admin/Mod click = lock/unlock) · 15 Settings (`/is settings`) ·
  16 Invites (`/is invites`) · 19 Notes (`/is notes`, when your role may read them) · 20 Island chat (toggle) ·
  21 Chat colors · 22 Visit islands (the browser) · 23 Stats, 24 Bank, 25 Upgrades: gray "Coming soon" ·
  40 Disband (leader, same confirm button as `/is disband`, no typed word: owner answer 261) or Leave (everyone else, confirm). Back from every sub-menu
  returns to the island menu. `/is home` and `/is go` still teleport straight home.
- **Island chat** (`/is chat`): toggle mode is per player and not saved (off after a relog or a profile switch). Routing:
  islands publishes a `ChatChannel` service (common/chat); the chat module checks every chat message after mute, freeze and
  menu prompts (LOWEST) and, when the player's island chat is on, runs its normal checks and hands the message to the island
  instead of broadcasting it. One-shots go through the chat module's `ChatChannels` service (same checks; mute via the
  moderation `muted` placeholder). Staff chat mode (`/sc`) wins over island chat. The chat lock (`/mutechat`) and slow mode
  don't apply to island chat by default (`chat.yml` → `channels.respect-lock` / `channels.respect-slow-mode`). Without the chat
  module: one-shots still work (mute check, the text filter if present, plain text), toggle mode has nothing to capture.
- **Role colors** (owner I11): Chat colors menu, one wool per role (Leader, Admin, Mod, Member) in its color; the **leader**
  clicks a role for the next color of `islands.yml` → `chat.palette` (8 colors by default: gold, red, aqua, green, yellow,
  light_purple, blue, white). Defaults `chat.role-colors.*` (gold, red, aqua, green). Saved atomically (re-checks the leader in the
  transaction), table `island_chat_colors` (migration V009), removed with the island. Everyone else sees them read-only.
- **Staff spy:** `skyblock.island.chat.spy` (helper+) + the "Island chat spy" toggle in `/settings` → Chat (default off) shows
  every island's chat as `[Spy] [island] ...`. The console gets the same line (`chat.console`, default on).
- **Logged:** every island chat message as category `chat`, action `chat`, with `channel: island`, the island id/name and the
  number of recipients (blocked ones as `chat_blocked` with the reason, like public chat); role color changes as category
  `island`, `island_chat_color` (role, from, to).

## Island stats (M2.6)
Counters per island **and** per member (profile key) and category, in **day** and **week** buckets plus **all-time**
totals (tables `island_stats_daily`, `island_stats_weekly`, `island_stats_total`, migration V010). Days are local calendar
days in `island-stats.yml → timezone` (Europe/Oslo: 23/25-hour days on DST changes), weeks start Monday 00:00.

| Category | Counted when | Who |
|---|---|---|
| Blocks mined / Logs chopped / Crops harvested | a member breaks a block inside the island border: logs → logs, ripe crops (`crops.ripe-blocks` at max age) and `crops.harvest-blocks` → crops, everything else → blocks mined. A block placed on the island recently (`anti-farm.placed-memory`, 512 per island) gives nothing when broken again (ripe crops excepted) | member |
| Blocks placed | a member places a block on the island | member |
| Generator output | lava + water forms `generator.blocks` (cobblestone, stone, basalt) on the island | island |
| Mobs killed | a member kills a mob on the island; stacked batch kills add their extra kills (`StackKillEvent`) | member |
| Visitors | first visit of a player to the island today (the same unique-visitor rule as the visit browser) | island |
| Time on the island | every `playtime-sample-seconds` (60) for members standing inside their own island's border | member |
| Items sold / Money earned | fed by the sell system through `IslandStatsService.recordFor` (not hooked yet: shows `-`) | member |
| KOTH time / Bosses killed | Phase 7/8 features (show `-` until then) | member |

- Only stored members count: never coop players or visitors (REVIEW A4), never creative/spectator mode. What a member
  added stays with the island when they leave (A5) and shows as "Former member".
- **Performance:** every event is an O(1) grid lookup + one in-memory increment at MONITOR (cancelled actions never
  count). Counters are written in **one batched transaction** every `flush-seconds` (30) and on shutdown; a failed write
  is retried with the next flush (nothing is lost or counted twice); a crash loses at most one flush interval. Reads
  (the menu) include the counters not saved yet.
- **Retention:** day rows `retention.daily-days` (35), week rows `retention.weekly-weeks` (104), totals forever (rows of
  removed islands stay for season history); cleanup every `retention.purge-hours` (6).
- **API** (`common/island/IslandStatsService`): `record`, `recordIsland`, `recordFor(player, stat, amount)` (members only),
  `source(stat)` (marks a category as fed), `snapshot(island)` (totals, today, this week, last week, per member) and
  `top(stat, period, board, limit)` (TODAY / THIS_WEEK / LAST_WEEK / ALL_TIME, solo / team / all boards, deleted islands
  left out) for the island top (Phase 5), missions and achievements.
- Audit: only staff actions (`isadmin_stats_view`, `isadmin_stats_reset`); counting itself is not logged.

## Profiles (PROF)
Island membership, ownership, roles, coop trust, invites and personal homes belong to the active **profile** (stored under the profile key; profile 1 = the player UUID, so all existing islands are profile 1's). Join limits and the join cooldown stay **per player** (owner P11): `player_island_joins` is per player and remembers the profile (`profile_id`, migration V007; old rows = profile 1). Two profiles of one player are never on the same island, also not one after the other (P3): invite, accept, coop and `/isadmin addmember` are refused (`*.own-profile`, audited `profile_transfer_blocked`). An island of your other profile can't be visited or entered (not even with the staff bypass) and nothing can be done on it (`protection.own-profile`), so no item can move between your profiles. Bans, likes and ratings are per player; an island any of your profiles belongs to counts as your own for ratings/likes. Deleting a profile: refused while it leads a team island with other members; otherwise its solo island is removed and its memberships/coops end. `IslandService#islandOf/role/isCoop` take an online player's UUID (active profile) or a profile key; `islandOfProfile(key)` never resolves.

## Island bank rights (M3.3)
Three rows in the permission matrix (own defaults, never part of `*`, management rights: stored members only, never coop
players or visitors): **Bank deposit** (`bank-deposit`: Admin, Mod, Member), **Bank withdraw** (`bank-withdraw`: Admin;
daily limit per role in `bank.yml`) and **Bank history** (`bank-history`: Admin, Mod). The Leader has all three. Config:
`permissions.role-defaults.bank-deposit|bank-withdraw|bank-history`. **Upgrades** (`upgrades`, PHASE-5 M5.4, owner
170 / P5-10): buy island upgrades with the island bank; default Leader + Admin (`permissions.role-defaults.upgrades`),
never coop players or visitors; everyone can look at `/is upgrade` and hand in task items. The island menu's **Bank** button opens `/bank`
(economy module, `docs/commands/economy.md`). When an island is disbanded, what is left in its bank is deleted (owner
answer 263: the money belongs to the island); a staff delete or reset keeps it in the closed bank.
