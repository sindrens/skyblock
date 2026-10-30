# Status (single source of truth for a fresh session)

Read this file and `docs/QUEUE.md`, then continue. Last updated: 2026-10-29 (after Phase 1 merge).

## Where we are
- **Phase 0 (foundation): done.** Config, style/messages, DB (MariaDB/H2 + migrations), player data, commands (enforced tab completion), menus (redesigned look), items (+dupe detection), audit log, services/placeholders (PlaceholderAPI optional), LuckPerms hooks, LegacyColors (`&` codes).
- **Phase 1 (core experience): code done, owner testing.** Modules: worlds, motd, moderation (M1.8 punishments), essentials (M1.1 join/spawn + approved essentials), hud (M1.4 scoreboard), social, perks (nicks `~`), chat (M1.3 chat, M1.5 tab/nametags), menus (M1.6 /menu /help, M1.7 /settings, M1.12 /admin, /staff hub), staff (M1.9 staff mode `/staffmode`, freeze, inspect, reports), logs (M1.10 /logs), cosmetics (M1.11 tags & titles, /display).
- **Phase 4 started:** M4.3 per-player blocks engine merged (modules/depths).
- **M3.0 balance sheet** merged as DRAFT (`docs/ECONOMY_BALANCE.md`, model `tools/economy_model.py --check`); owner must approve §12 before M3.4 prices go live.
- **Phase 2 started:** M2.1 islands (grid, /is, FAWE/placeholder starter island, borders, home) merged.
- **M3.1 economy core** merged (EconomyService in common, /bal /baltop /pay /transactions /eco). **M4.5 skills** merged (SkillService in common, /skills).
- Before each release run `python3 tools/sync_server_configs.py` (two-way: the newer committed copy wins, so owner GitHub edits of tracked configs are kept).
- **M2.2 island protection** merged. **Phase P** (owner's punishment spec) added as its own phase with priority; spec in `docs/specs/`.
- **M4.1 Depths layout** merged (`zones.yml` written on first start, not tracked; `/depths generate`, `/depths tp`).
- **M3.6 auction house** merged (modules/auction, `/ah`, `/ah sell`).
- **P0** merged: freeze really holds (snap-back teleport was self-cancelled), persisted, no commands; Inspect tool = ender eye.
- **M3.2 money notes** merged (`/withdraw`, `/eco note`).
- Release script on the Mac (`~/Development/.skyborn-claude/release.sh`) first merges owner edits from GitHub; if it writes `from-github.bundle`, stage it and merge it into the cloud repo.
- **M2.3 island members & roles** merged (invite/accept/kick/leave/promote/demote/transfer/coop, members GUI, permission matrix).
- **M4.6 Depths tiers** merged (per-player gate bars, /dungeon).
- **P1** merged: `/p` single-screen menu from punishments.yml, `/warns`, warn rules, PunishDecision pipeline, safe reload.
- **M2.7** merged: `/is disband`, slot clearing + reuse, `/isadmin`.
- **UX1** merged: short plain messages in one format, cleaner scoreboard (+ Depths layout) and tab, instant rank refresh, /baltop 30 s async, tags after the name.
- **P2** merged: [Undo], double-punish guard, offence expiry, /pstats.
- **CRY** merged: tokens replaced by crystals (aqua ◆, not tradeable), gems emerald ✦, DB/config migrations.
- **M2.4** merged: /is visit + browser, lock/expel/ban, warps (categories, signs), ratings, likes.
- **R2B** merged: 30-min new-account limit for /pay and /withdraw, /eco for admins, sell cap 300% helper.
- **R2A** merged: AH 30 min, no amount number hints, ban clears freeze, blaze rod Inspect / breeze rod Punish, Europe/Oslo display time, skills to 150 + /skills set|add, /freerank (Ember rank + tag).
- **PLAN-ENCH** merged: docs/plans/ENCHANTS.md (48 enchants, /enchanter, milestones E1–E6 in QUEUE).
- **ISL2** merged: island Admin role, hurt-monsters + settings role perms, /is settings (flags, weather, time), island fly, personal homes, /is invites.
- **STACK** merged: mob/item/spawner stacking, island entity limits, crops grow without light, /spawner give.
- **M4.2** merged: starter kit (Forge button), /forge, living tools Lv 1–50 + perk slots + prestige, blueprints, skins, /tool, staff give commands, ToolService.
- **Builds:** done by a separate builder agent/team from `docs/BUILDER_BRIEF.md` (not our job).

## Release / infrastructure
- Queue agents are launched with `Workflow({scriptPath: '/home/claude/wfscripts/queue-item.js', args: {item, task}})` (copy kept in `tools/queue-item.js`).
- Code repo in the cloud workspace: `/home/claude/skyblock` (branch `main`). The owner's Mac clone: `~/Development/skyblock` (GitHub `sindrens/skyblock`).
- Release (one call): build jar → `git bundle create /mnt/user-data/outputs/updateN.bundle <last>..main` + copy jar as `phoenix-uN.jar` → `device_commit_files` both to `~/Development/.skyborn-claude/` → device: `timeout 175 ~/mnt/Development/.skyborn-claude/release.sh N` (pulls bundle, pushes GitHub, deploys to VPS, prints jar md5 + log). The jar md5 printed must equal the local one.
- Console access for verification: device `~/vps.sh '<cmd>'`, console commands via `tmux send-keys -t skyblock "<cmd>" Enter`. Restore anything changed.
- Last released bundle: update90. Next bundle number: 91. Release with `release2.sh N` (bundle base: always 3516796, the Mac checkout HEAD that never moves now) (bundle + deploy<N>.tgz built in the cloud; no Mac checkout, works without delete permission).
- Owner gets a push notification on every release (PushNotification tool).

## Owner answers page
- Round-2 questions are answered in the artifact https://claude.ai/artifact/QHrsxdXw45E9Tti5jHtVVy (db doc `answers/owner`: map code → {c: d=suggested | n=no | o=other, t: text}, `done: true` when finished). Read it with ArtifactData `get` collection `answers` doc `owner`. Source questions: `docs/QUESTIONS_ROUND2.md` (answered in chat → `docs/DECISIONS_2026-10-30.md`). Round 3: `docs/QUESTIONS_ROUND3.md`, answers in db doc `answers/round3`.

## Overnight mandate (owner, 2026-10-30)
- Keep implementing everything planned without asking; start the next queue item automatically.
- New plan docs (enchants, wardrobe/loadout, resource pack): write them, mark every choice as a changeable default, and build them.
- Restart/release the test server after every milestone, anytime.
- Build and release profiles (migrate existing data to profile 1), carefully, with unit tests.

- Morning deliverable: `docs/MORNING_REPORT.md` = changelog of everything new tonight, what to test (one combined checklist) and every new command. Also send it to the owner.
- Security: anything suspicious → refuse, log in `docs/SECURITY_LOG.md`, mention it in the morning report.

## Owner rules (always)
- Unit tests allowed: `./gradlew build` (with tests) must pass before every merge. Never a test server without permission.
- Tab completion on every command/argument. All text in messages files, all numbers in typed configs, everything audited.
- Ask before adding new commands. Open questions never block: safest default + add to `docs/OPEN_QUESTIONS.md` → "Waiting for owner".
- Agents: max 3 at once, distinct lanes, merge each one as soon as it finishes, then start the next queue item (see QUEUE.md).

## Running agents (background workflows, each in /home/claude/wt/<item> on branch q/<item>)
- ANS2 owner answers round 2
- DEPTHSPROT Depths protection + safe zones
- PLAN5 Phase 5 progression plan

## Parked / broken
(none)

## Owner decisions page
- Clickable page "Phoenix Skyblock Decisions": https://claude.ai/artifact/KvSdKBox2cMSS5h6WDUXfs (1,013 short questions, one tap: Suggested / No / Other). Answers live in its database, collection `answers`, one doc per area (`m.<question id> = {a: s|n|o|null, note, t}`). Read them with ArtifactData list. Question data: tools/owner_sheet/final_q.json (same ids as docs/owner/Phoenix_Skyblock_questions.xlsx).
