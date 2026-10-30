# Profiles module (`modules/profiles`): PROF

Island profiles (owner decisions P1–P12 and round 3 PR1–PR5, `docs/DECISIONS_2026-10-30.md`). A player has up to 3 profiles; a profile is one island and everything that belongs to it. Settings: `profiles.yml`; texts: `messages/profiles.yml`; data: tables `profiles`, `profile_accounts`, `profile_states`, `profile_slots`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/profiles` (`/profile`) | Profile menu: one button per slot with name, icon, island, island level, playtime and created date. A profile opens its own screen: **Switch**, **Rename** (typed in chat), **Change icon**, **Delete** (confirm screen, then type the profile's name; no cooldown, P9). An empty unlocked slot creates a profile; a locked slot can be bought with gems (confirm screen). The only way to switch (P5) | `skyblock.profiles.use` (everyone) | ✅ |

Also in `/menu` (slot 37) and `/help profiles`.

## Per profile vs. shared
- **Per profile** (stored under the profile key): money, crystals, shards, inventory + armor + offhand, ender chest, XP levels, health/food/effects, skills, Depths tier unlocks, island membership/role/personal homes/coop trust, auction listings + collection bin, money notes created, `/settings` values (keys in `settings.profile-keys`), playtime. Future: satchel, personal bank, quests/battle pass, achievements, tools (items follow the inventory).
- **Shared** (stored under the player UUID): gems, rank, titles/tags (achievement titles become account titles), cosmetics, nickname, punishments, island join limits (P11), likes/ratings, island bans, the audit identity, staff homes, vanish.

## Switching
Save the current state (inventory, armor, offhand, ender chest, XP, health, food, effects, location) and make the target active in **one** database transaction, then load the target's state and teleport to its island home (spawn when it has no island). While the switch runs the player can't drop, pick up, click, use, build, run commands or take damage. Refused: in combat/boss arena (modules cancel `ProfileSwitchEvent`; `switch.blocked-worlds`), while frozen or in staff mode (staff module), while dead, with an item on the cursor, to a profile whose slot is locked. Anti-spam: `switch.cooldown-seconds` (10; bypass `skyblock.cooldown.bypass`). Crash safety: the active profile's state lives in the player file; a saved state row of the active profile means "not applied yet" and is applied on the next join; a player whose switch is still saving can't log in for that moment. If the database takes longer than `switch.timeout-seconds` (10) the player is kicked (their file equals the saved state because they were locked) and the next join applies whatever the database decided; a profile state that fails to load also kicks instead of playing on with half an inventory. New items for a profile (auction deliveries, notes) never land in an inventory while its player switches.

## Slots (P6, P8, PR5)
Slot 1 free. Slot 2: `slots.price-2` gems (500) or `skyblock.profiles.slots.2` (Knight+). Slot 3: `slots.price-3` gems (1,500) or `skyblock.profiles.slots.3` (Warlord+). Slots are independent. Gem payment: ledger reason `profile_slot`, idempotency key `profile_slot:<player>:<slot>` (paid once ever). Stored unlocks keep their source (`gems`, `grant`); a season reset keeps only `gems` (`ProfileServiceImpl#seasonReset`, called by the season module later). A profile whose slot is locked again (rank lost) stays, but you can't switch to it (you can delete it).

## No transfers between own profiles (P3, P4)
`ProfileService#blockOwnTransfer` refuses and audits `profile_transfer_blocked` (details: path, from/to profile) on: `/pay`, auction buy, money note redeem, island invite accept/coop/join, item pickup of items dropped or thrown (tridents/arrows) by another own profile. Islands: another own profile's island can't be joined, cooped or visited.

## For other modules
`ProfileService.of(core.services())` (`common/profile`): `profileKey(player)` (online), `profileKeyAsync(player)` (offline: last active), `ownerOf(key)`, `sameOwner`, `ownOtherProfile`, `onlinePlayer(key)`, `isLive(key)`, `active`, `profiles`, `loadProfiles`, `isSwitching`, `blockOwnTransfer`, `bestPerOwner(map)` (P7 leaderboards: best live profile per player), `seasonResetSlots(actor)` (P8, for the season module). Events: `ProfileSwitchEvent` (before, cancellable with a reason), `ProfileSwitchedEvent` (after: reload per-profile caches), `ProfileDeleteEvent` (before, cancellable), `ProfileDeletedEvent` (after: remove/zero the profile's data). The first profile of every account uses the **player UUID as its key**, so all data from before profiles is profile 1 with no data moved; later profiles get random UUIDs and a deleted key is never reused.

Placeholders: `<ph:profile_name>`, `<ph:profile_slot>`, `<ph:profile_playtime>` (the account total stays `<ph:playtime>`, used by `/freerank` and new-account limits). The scoreboard line is in `messages/hud.yml` (commented out, optional).

Audit (category `auth`): `profile_create`, `profile_switch`, `profile_switch_resumed`, `profile_rename`, `profile_icon`, `profile_delete`, `profile_slot_unlock`, `profile_slots_season_reset`; `profile_transfer_blocked` in the category of the blocked path.

Permission definitions: `modules/profiles/src/main/resources/permissions/profiles.yml`. Rank grants: `server/permissions/profiles.txt` (donor groups arrive in Phase 11).
