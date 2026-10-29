# Open Questions

## Waiting for owner (answer in one sitting)
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
