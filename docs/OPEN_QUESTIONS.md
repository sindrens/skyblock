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
