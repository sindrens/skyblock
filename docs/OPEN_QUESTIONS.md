# Open Questions

## Waiting for owner (answer in one sitting)
- P0 freeze: frozen players get **no commands at all** by default (`staff.yml → freeze.allowed-commands: []`, the old default allowed /msg /r /tell /w /discord /rules /help). Chat still goes to staff only. Allow `/discord` and `/rules`? (default: none)
- P0 freeze: staff and plugin teleports (`/tp`, `/tphere`, a `/spawn` warmup that was already running) still move a frozen player and they stay frozen at the new spot (`freeze.allowed-teleport-causes: [command, plugin]`). OK, or staff-only? (default: command + plugin)
- P0 freeze: a frozen player hovers (flight on, no gravity) so they can't fall away; on unfreeze their own flight comes back and fall damage is ignored for `freeze.fall-grace-seconds` (10). OK? (default: yes)
- P0 freeze: with `freeze.logout-action: ban` the freeze is still stored, so after an unban they come back frozen. Clear the freeze on a ban instead? (default: keep it)
- P0 staff mode: the Inspect tool is now an ender eye (`mode.inspect-tool-material`), because a spyglass always zooms on the player's screen. Another item? (default: ender eye)
- Punishment ladders in `punishments.yml` (default proposal: Hacking 30d → perm; Toxicity warn → 1h → 1d → 7d → perm mute).
- Public ban/mute announcements for players? (default: on)
- Helpers may freeze / use staff mode and see join logs (auth)? (default: yes, IPs hidden)
- Logout while frozen: alert (default) or auto-ban?
- Achievements as their own module with progress tracking? (default: yes, later, F3)
- Deleted titles/tags: keep in DB hidden (default) or wipe?
- Discord webhook for punishments now or with the Discord integration? (default: later)
- F2: `server/permissions/luckperms.txt` was split into `base.txt` + one file per module. Updates copy files without deleting, so the old file stays on the VPS; delete `/opt/skyblock/server/permissions/luckperms.txt` once, or make `update-vps.sh`/`deploy.sh` delete removed files? (default: the apply script ignores the stale file and says so)
- Depths (M4.3): impossibly fast breaks are silently ignored (no reward, no message to the player); staff get an alert + audit row at 3 flags in 10 s (`depths.yml → anti-fast-break`). OK, or should the player see a message / get kicked? (default: silent)
- Depths (M4.3): fast-break audit rows use category `world` (there is no anti-cheat category). Add an `anticheat` audit category? (default: `world`)
- Depths (M4.3): the Depths visibility choice (everyone / island / none) is kept in memory only and resets to "everyone" on rejoin until the `/settings` toggle is built. Persist it? (default: persist once the toggle exists)
- Depths (M4.3): inside regions players can't break non-resource blocks or place blocks (`engine.protect-regions: true`). Keep? (default: yes)
- Depths (M4.1): the Mine and Forest wings come back on a row **south** of their outward row (the Combat U fills the north band). The strip directly south of The Gate (x -80..79) stays free for a possible fourth wing, which would need its own U. OK? (default: yes)
- Depths (M4.1): Combat tier unlock levels are not in WORLD.md; `zones.yml` uses the M3.0 proposal 0/6/12/18/24/30/38/46/55/66. OK? (default: yes)
- Depths (M4.1): the `depths` world spawn (worlds.yml, 0.5 100 0.5) is the center of The Gate, kept free under the brazier canopy; the arrival pad and `/depths tp gate` use the south pad (0.5 100 38.5). Move the world spawn to the arrival pad? (default: keep)
- Depths (M4.1): tier gates are open frames until M4.6 adds the per-player doors; the boss portal in Combat tier 10 already checks the tier's Combat level. OK? (default: yes)
- Depths (M4.1): `zones.yml` is not copied into the tracked `server/plugins/PhoenixSkyblock/` configs, so a deploy never overwrites coordinates builders changed on the server (the plugin writes the default on first start). OK? (default: yes)
- Depths (M4.1): `/depths generate` is admin only; builders get `/depths tp`. Should builders also be allowed to generate placeholders? (default: admin only)
- Depths (M4.3): right after a break the real ore may flash for one tick (50 ms) before the depleted block shows (the server re-sends the real block when a break is cancelled). Acceptable? (default: yes; a packet-level fix would need ProtocolLib/PacketEvents)
- M3.0 balance sheet (`docs/ECONOMY_BALANCE.md`, DRAFT): approve or change it before M3.4 prices go live. Checklist in its §12.
- M3.0: day-3 target $5M (top) vs model ~$15M: accept the smooth curve (default) or reshape tiers 1-8 / 9-12 prices?
- M3.0: endless late money sink "Mastery" after upgrade level 12 (default: yes; without it the sink ratio falls to ~25% after day 60)?
- M3.0: tool XP curve x1.21 per level instead of x1.12 so perk slots land at ~1 h / day 2 / week 1 / week 4 (default: x1.21)?
- M3.0: combat skill thresholds per tier 0/6/12/18/24/30/38/46/55/66 (default: as proposed)?
- M3.0: interest on the lowest balance of each 3 h cycle + interest only for accounts with >= 2 h play in the last 7 days (default: both on)?
- M3.0: tokens tradeable via /pay or AH? (default: no, prevents alt farming)
- M3.0: new accounts (< 2 h played) can't /pay or list on the AH (default: yes)?
- M3.0: island production only counts as "online" while a member was active in the last 15 min, otherwise it burns fuel (default: yes)? Also: does time in The Depths earn island fuel? (default: no, as DESIGN §5.4 says "on the island")
- M3.0: dynamic price recovery as 2% of the gap per 10 min (default) instead of a fixed 2 points; Hot Item +25% for 24 h?
- M3.0: personal bank tier unlock cost 2% of the tier's capacity (default: yes)?
- M2.1 `/is go <member island?>`: what should the argument mean once teams exist (M2.3)? (default: `/is go` = `/is home` without an argument until then)
- M2.2 island protection: hostile mobs can be hurt by everyone, visitors too (self-defense; there is no "monsters" permission in the plan). OK? (default: yes)
- M2.2: `/is bypass` is granted to admins only (`server/permissions/islands.txt`). Also moderators/helpers? (default: admins only)
- M2.2: the staff bypass toggle is per session (off after relog, every toggle and the quit-off logged). Persist it? (default: per session)
- M2.2: default role permissions (`islands.yml → permissions.defaults`): Mod everything; Member everything except spawners; Coop everything except fire and spawners; visitors nothing. OK? (default: as listed)
- M2.2: a player inside an island border can't walk/fly/be pushed out of it (also with /fly; creative/spectator and bypass excepted); they leave by /spawn, /is home etc. OK? (default: yes, `protection.keep-players-inside`)
- M2.2: falling blocks (sand, gravel, anvils) that would land outside their own island's border disappear instead of dropping an item (prevents gravity dupes at the border). OK? (default: yes)
- M2.1 Who may move the island home with `/is sethome`? (default: leader only until M2.3 makes it a role permission)
- M2.1 Players still join at spawn (`essentials.yml` `join.teleport-to-spawn: true`). Join at your island instead once islands exist? (default: keep spawn)
- M2.1 Staff build bypass on islands is permission-only (`skyblock.island.bypass`, admins) until M2.2 adds the logged `/is bypass` toggle. OK? (default: yes)
- M2.1 Scoreboard shows "Lv -" for island level until Phase 5 and no island position (`island_rank` empty). OK? (default: yes)
- Economy (M3.1): `/eco give|take|set|reset` is owner-only (`skyblock.owner.economy`); should admins get it too? (default: owner only, STANDARDS "economy edits = owner")
- Economy (M3.1): tokens can't be paid with `/pay` (`economy.yml → tradeable.tokens: false`). Should tokens be tradeable? (default: no; money and shards yes, gems no as decided)
- Economy (M3.1): `/pay` minimums $10 / 1 token / 1 gem / 1 shard and confirm thresholds $1M (decided) / 10,000 tokens / 100 gems / 1,000 shards. OK? (all in `economy.yml`)
- Economy (M3.1): balances are global and the ledger records the season (0 until the season service exists). At season start, which currencies are wiped (money, tokens, shards) and which kept (gems: see Economy question 2)? The starting $1,000 is given once per season per player. (default: decide with the season system)
- Economy (M3.1): `/bal <player>` lets every player see others' balances (`skyblock.economy.balance.others`, default true). OK, or staff only? (default: everyone)
- Economy (M3.1): the staff reason of `/eco` is shown to the player in `/transactions` (as the note, max 64 characters). OK? (default: yes)
- Skills (M4.5): max skill level 66 (= the highest tier unlock, Voidstone); XP keeps counting after it for leaderboards (`skills.yml → curve.max-level`). Higher cap for Phase 6 perks? (default: 66)
- Skills (M4.5): combat XP per kill is priced for the same XP per hour as the mine at the same unlock level (zombie 5, skeleton 10, spider 22, slime 40, drowned 72, witch 135, pillager/vindicator 250, blaze 420, wither skeleton 700, ravager/evoker 1,200). OK? (default: yes; M4.8 custom mobs will set their own XP)
- Skills (M4.5): Mining/Foraging XP only from The Depths, not from island blocks (generator ores, island trees); Combat XP from any mob a player kills (also on islands), but never from spawner mobs. OK? (default: yes)
- Skills (M4.5): staff `/skills set|add <player> <skill> <amount>` was not in the plan, so it is not built (the service already has an audited `setXp`). Add it? (default: no command until approved)
- Skills (M4.5): own `/settings` toggle "Skill XP in the action bar" (Display, default on) instead of reusing `tips`/`ui-sounds`. OK? (default: yes)
- Skills (M4.5): level-up audit rows use category `world` (no progression category). Add a `progression` audit category? (default: `world`)
- Skills (M4.5): anyone may view another player's skills with `/skills <player>` (`skyblock.skills.others`, default true). Keep public? (default: yes)
- Auction (M3.6): the 1% listing fee is not refunded when the seller cancels (`auction.yml → fee.refund-on-cancel: false`); expired listings never get it back. Refund on cancel? (default: no)
- Auction (M3.6): new accounts (< 2 h played) can neither list nor buy (`anti-abuse.new-account.block-listing/block-buying`). The balance sheet only says "can't sell"; allow buying? (default: both blocked)
- Auction (M3.6): extra listing slots per rank (`skyblock.auction.slots.<n>`, default 5, cap 54). Which store/bank ranks get how many? (default: nobody has extra slots until the store ranks exist)
- Auction (M3.6): money notes are not listable (`items.allow-money-notes: false`) until the notes module (M3.2) decides. OK? (default: not listable)
- Auction (M3.6): filled shulker boxes and bundles can't be listed (their contents could hide starter kit tools or dupes; `items.allow-filled-containers: false`). OK? (default: must be empty)
- Auction (M3.6): anti-abuse defaults: price $10 to $1T, 10 s between listings, the same item at most 3 times per 30 min. OK? (all in `auction.yml`)
- Auction (M3.6): `/ah sell <price> [duration]` was added as a sub-command of the planned `/ah` (needed to list from chat; the menu can do the same). OK? (default: keep)
- Auction (M3.6): staff remove listings from the `/ah` menu (`skyblock.staff.auction.remove`, mods+, reasons in `auction.yml → staff.remove-reasons`); no staff command. Add `/ah admin` (e.g. view a player's listings/collection bin)? (default: no command until approved)
- Auction (M3.6): staff alert for sales far above an item's 7-day median or between linked accounts (balance sheet R4) is left to the M3.8 anomaly alerts. OK? (default: M3.8)
- Auction (M3.6): money only for now; listings in shards ("filters (currency later)") later? (default: money only)
- Money notes (M3.2): money notes stay NOT listable on the auction house (`auction.yml → items.allow-money-notes: false`). Allow them? (default: not listable)
- Money notes (M3.2): `/eco note` sits under `/eco`, so it is owner-only (`skyblock.owner.economy`) like `/eco give`, although the plan says "staff". Give admins (or moderators) a separate node for it? (default: owner only, it creates money)
- Money notes (M3.2): limits $100 minimum / $1T maximum per note, 10 s between `/withdraw`, no fee (balance sheet §9.4). OK? (all in `economy.yml → notes`)
- Money notes (M3.2): new accounts (< 2 h played, `notes.new-account-minutes: 120`) can't `/withdraw`, the same as the balance sheet's "new accounts can't /pay" (a note is a way to pay). Cashing a note is allowed for everyone. OK? (default: yes; note `/pay` itself does not have this rule yet)
- Money notes (M3.2): notes are money only (`notes.currencies: [money]`). Also shard notes? (default: money only)
- Money notes (M3.2): a note whose id is not in the database (e.g. spawned with a generic item give) is confiscated like a copy, with the same staff alert and a `note_dupe` audit row (`why: unknown`). OK? (default: yes)
- Money notes (M3.2): if the server crashes in the instant between taking the money and handing out the note, the note row stays active without an item; staff can see it in `/logs` (`note_withdraw`) and re-issue it with `/eco note`. Automatic recovery (e.g. a "pending" state re-delivered on join)? (default: manual, very rare)
- Money notes (M3.2): notes can't be crafted with, sold to villagers or used in a cartography table (they are paper). OK? (default: yes)

Decisions the owner still needs to make. **Developers: never guess. Add new questions here and ask.** Move answered items into `DESIGN.md`.

## Economy
1. **Shards:** how are they earned? (Proposal: bosses, envoys, KOTH, high-tier dungeon zones.)
2. **Gems at season reset:** are store-bought gems kept or wiped? (Recommendation: keep unspent gems/credits to avoid support issues.)
3. **Personal bank:** which perk at each of the 10 tiers, and the cap per tier?
4. **Max total sell multiplier:** proposal +100% max.

## The Depths
5. **Mine drops:** ore items to sell, tokens, key chance, rare items? Exact mix per tier.
6. Number of custom ore/tree tiers after vanilla, and their names.
7. Tier unlock thresholds (blocks broken / logs / kills) per tier.

## Gear
8. **Sets:** how are pieces obtained (tokens, drops, crafting)? Final set list.
9. Tool prestige at level 50: confirm, and what it gives (default: permanent title + skin unlock + small bonus on that tool).

## Leaderboards
10. Team island rewards for 2nd and 3rd place.
11. Weekly leaderboard rewards.

## Other
13. Hosting specs (decides plugin budget). Owner will handle; single server at start.
14. Spawner types and the max stack size.
15. Rank perk table per rank (vault count, cosmetics, convenience).

## From design review #1 (see REVIEW.md for details and defaults)
19. Team size cap: 4 → 8 with upgrades OK? (B1)
20. Death rules in PvP zones (B7)
21. Vanilla rules table (WORLD §6): rest of the table approve or change
22. Nether/End: what is the future use? (owner has a plan, TBD)
23. Elytra: how are they obtained without the End? (proposal: crates/Black Market/events)

## Network (SkybornMC)
24. Network brand: logo drafted (sky-blue S, see branding/). Confirm or request changes. Phoenix Skyblock stays red/phoenix.
25. Other gamemodes planned, and when? (decides when the proxy + hub are needed)
26. Hub/lobby: what should it contain?

## Phase plans (defaults apply unless the owner changes them; details in docs/phases/)

Answered 2026-10-29 (moved into the plans): retention 30 days / forever, `/transactions` yes, AH 1%+5% and 5 listings, `/pay` confirm >$1M, gems not tradeable, start $1,000, island build Y 0–256, Depths logs 10 s + personal blocks, Omnitool replaced by Forge/living tools/blueprints/skins (tradeable, no durability), titles + tags in one menu.

## Decided 2026-10-29 (batch 3)
- Our `/tp`, `/give`, `/gamemode`, `/msg`, `/list`, `/help` replace the vanilla commands.
- Spawn point fixed at 0, 100, 0 (`worlds.yml`: 0.5 / 100 / 0.5); any `/setspawn` change is copied into the tracked file.
- Join IP history (`/whois`, alts) and `auth` audit rows are kept **forever** (security).
- Nicknames get a visible marker (`~Phoenix`) and reset when the player loses the rank that allowed them.
- Depths wings bend into a U inside the 2000 border; boss arena on its own north island via a portal from tier 10.
- Spawn gets the Forge Quarter (Quartermaster, Blacksmith, Enchanter).
- Builds are done by a separate builder agent/team from `docs/BUILDER_BRIEF.md`.
