# Join tutorial (`modules/tutorial`)

The first thing on the server (owner answers 43, 44, 131, 158, 159; plan `docs/plans/TUTORIAL.md`). New players walk into the **Your Island** portal in the Gatehouse; 11 short steps (about 3 min) teach the core loop and **unlock features step by step** (replaces the old 30-minute new-account limit). Progress is per **account**, once (finishing on one profile opens every profile). Settings: `tutorial.yml` (every step, lock, count, target, guide, reward and the starter chest; editable in `/admin`); texts: `messages/tutorial.yml`; data: `db/tutorial`. Reload: `/skyblock reload tutorial`. Help: `/help tutorial`.

Built: milestones **TU1–TU8** (locks, step engine, guides, starter chest, finish reward, invite choice at the portal; TU3 cameras, TU5 Pip + NPC lines + Guide NPC, TU6 optional steps + tips, TU7 stats + event purge, TU8 polish: Bedrock title lengths checked by a unit test, idle reminder with Pip line + analytics event).

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/tutorial` | Tutorial menu: progress (step N/11, time), **Take me there** (instant teleport to the step's place), **I'm stuck** (hint again; after 3 presses skippable steps can be skipped: arrive, depths, help; never sell or bank), **Steps** (all steps, click the current one to go there), **Watch again** (titles + hints of every step and the spawn / island / Depths cameras, nothing changes, Stop in the menu, sneak skips a camera), **Optional steps** (O1-O9 with progress and reward; click = Show me), **What unlocks when**. Not started: **Start**. Done: **Where next?** | `skyblock.tutorial` (everyone) | ✅ |
| `/tutorial admin status <player>` | Step, state, done steps, reward paid (online or offline) | `skyblock.tutorial.admin.status` (helper+) | ✅ |
| `/tutorial admin skip <player>` | Finish the tutorial for a player, **no reward**; every lock opens. Confirm. Audited `tutorial_staff_skip` | `skyblock.tutorial.admin` (mod+) | ✅ |
| `/tutorial admin reset <player>` | Back to the start, locks return, **never a second reward**. Confirm. Audited `tutorial_staff_reset` | `skyblock.tutorial.admin` (mod+) | ✅ |
| `/tutorial admin step <player> <step>` | Move a player to a step (earlier steps count as done). Audited `tutorial_staff_step` | `skyblock.tutorial.admin` (mod+) | ✅ |
| `/tutorial admin stats` | Stats menu (TU7, TQ22/TQ24): per step **reached · done · %**, players who **stopped** there (still on it, no event for 24 h), median and 90% **time**, **I'm stuck** presses; summary (started, finished, most stops), cameras played/skipped, optional steps done, idle reminders. Toggles **7 / 30 days** and **All / Java / Bedrock**; cached 60 s. Red step = more than 10% stop there. Also in the `/staff` hub and `/inspect → Tutorial` | `skyblock.tutorial.stats` (mod+) | ✅ |

Staff (no command): **`/inspect <player>` → Tutorial** (written book, slot 41) shows the state, step, times, done steps, reward paid and Watch-again count; mods and up get **Skip** and **Reset** (confirm screens) and **Tutorial stats**. **`/staff` → Tutorial stats** (slot 40). `skyblock.tutorial.bypass` (builder, helper and up): no locks; the tutorial is offered in chat, never started by force (TQ28).

## Steps (`flow.steps`, TQ1)
| # | Id | Done when |
|---|---|---|
| 1 | `portal` | `IslandPortalEnteredEvent` (the portal created or found the island). Flame trail along `trail.nodes`, compass to `targets.portal` |
| 2 | `arrive` | The **island camera** ended or was skipped (waits up to `cameras.island-wait-seconds` for the player to stand on their island). Without a camera: `steps.arrive.seconds` (6) after a "Welcome home" title |
| 3 | `chest` | The starter chest was opened and a starter pickaxe (`steps.chest.need-tool`) is in the inventory. No chest / empty chest / team joiner: the **Starter pack** instead |
| 4 | `crop` | 3 wheat (`steps.crop.blocks`, `.count`) placed on your own island. Dust on 3 farm spots near the home |
| 5 | `menu` | The island menu opened (`steps.menu.menu-ids`: `island`), from `/menu` or `/is` |
| 6 | `depths` | Standing in zone `mine_t01` (`steps.depths.zone`) |
| 7 | `mine` | 15 Mining XP gains in the Depths (`steps.mine.count`). Dust on the nearest ore |
| 8 | `sell` | Any sale (`ItemsSoldEvent`; `steps.sell.sources` empty = any) |
| 9 | `forge` | The Forge menu opened. Compass + hologram on NPC `depths_forge` (fallback role `forge`) |
| 10 | `help` | Any NPC clicked (`steps.help.npc-roles` empty = any). Compass + hologram on `depths_guide` |
| 11 | `bank` | A personal bank deposit (`steps.bank.count-island-bank` off) |
| – | finish | Title, chat summary, reward **$2,500 + 25 crystals** (`reward.*`, paid once per account to the profile you finish on, one transaction with the finished flag, idempotency key `tutorial_reward:<uuid>`), title `firstflight` (created in cosmetics when missing), "Where next?" menu |

Steps done early count (`flow.early-steps-count`, TQ21) and are skipped when reached. A disconnect never loses progress (every step, counter and the starter chest point are saved); on rejoin the player gets "Welcome back" and after `rejoin.delay-ticks` (2 s) an instant teleport to the step's place (island for steps 2–5, Mine Tier 1 for 6–10; sneak to stay). After `idle.reminder-minutes` (5) idle, one title + sound.

## Cameras (TU3, TQ10/TQ11, `cameras.*`)
- **spawn** (first join, before step 1: spawn → statue → keep → Gatehouse portals), **island** (arrival step, orbit around your island home), **depths** (after reaching Mine Tier 1), **market** (optional spawn tour, Show me). Max `cameras.max-seconds` (8) each; the core cameras together stay under 20 s. Shot lines: `<from> [to] look=<point> ticks=<n> [text=<key>]`, points `world:x,y,z`, `@home:dx,dy,dz`, `@zone:dx,dy,dz`, `@self:dx,dy,dz`; titles in `messages → cameras.<camera>.<text>`.
- **Java:** spectator mode following an invisible item display that glides along the shots (`cameras.segment-ticks`). **Bedrock** (and Java with `cameras.java-smooth: false`): **cut-and-fade** (short blindness, the player hovers frozen at each shot facing its point, a title per shot). Geyser's camera API is not on the classpath, so Bedrock always uses cut-and-fade. **Sneak = skip** on both; any command also ends the camera first.
- **Crash-safe:** before a camera, game mode, position, flight, speeds and invulnerability are saved in the player's data (PDC). Quit, kick, death, module stop give them back at once; after a crash or restart they come back on the next join, before anything else. Invulnerable while a camera runs. A new island on a 2nd/3rd profile gets the island camera (TQ31, `cameras.welcome-new-profile`).

## Pip and NPCs (TU5, TQ9, TQ27)
- **Pip**: a small red parrot only you can see (`pip.*`), flies over your shoulder toward the step's target and perches on it when you are close; one short line per step (name tag 4 s + chat, `messages → pip.lines.<step>`), also at the finish and the idle reminder. No AI, silent, invulnerable, never saved; hidden during cameras, in spectator and while vanished.
- **Guide NPC** (spawn, role `guide`, `guide-npc.roles`) opens `/tutorial` (sneak-click still shows its help). During the tutorial NPCs say a short line for your step (`messages → npc-lines.<step>.<role>`: guide at the portal step, forge, Depths guide, sell, bank).

## Optional steps (TU6, TQ8, TQ26, `optional.*`)
After the core tutorial (also legacy and staff-skipped players; `optional.during-core` off), once per account, **5 crystals** each (`optional.reward-crystals`, paid in one transaction with the "done" flag, idempotency key `tutorial_optional:<uuid>:<id>`, ledger reason `tutorial_optional`):

| Id | Done when | Tip shows |
|---|---|---|
| `generator` (O1) | 5 cobblestone broken on your own island | on your island |
| `spawn_tour` (O2) | walked up to (or clicked) the Shop, Sell, Bank and Auction NPCs; Show me = teleport + Market camera | in the spawn world |
| `settings` (O3) | `/settings` opened | anywhere |
| `skills` (O4) | `/skills` opened | anywhere |
| `team` (O5) | `/is members` opened | on your island |
| `upgrades` (O6) | island upgrades opened (hidden: `enabled=false` until Phase 5) | on your island |
| `enchanter` (O7) | the Enchanter clicked (hidden while `/enchanter` doesn't exist) | near the Enchanter |
| `wardrobe` (O8) | `/wardrobe` opened (hidden while it doesn't exist) | anywhere |
| `warzone` (O9) | walked up to the warzone portal (the rules line shows) | near the portal |

Tips: one chat line the first time you reach the place (once per step and account), at most one per `optional.tip-cooldown-minutes` (10), never in the first 10 minutes after joining or finishing; players turn them off in **`/settings` → Tips**. Audit `tutorial_optional`.

## Locks (`gates`, TQ4)
Checked by `TutorialGate` (common) at every entry point; **fail-open** when the module is off or `enabled: false`; staff bypass; progress still loading = locked for a moment. A feature opens when its step **and every step before it** are done (a money step done early does not open money features).

| Feature | Opens after | Where it is checked |
|---|---|---|
| `island_team` | step 4 `crop` | `/is invite`, `/is coop`, `/is visit` (+ browser), `/is warp` (accepting invites is always open) |
| `depths` | step 5 `menu` | `/dungeon` teleports, `to_depths` portal |
| `auction` | step 8 `sell` | `/ah` (browse), `/ah sell`, listing from the menu, buying |
| `pay`, `withdraw` | step 11 `bank` | `/pay`, `/withdraw` |
| `trade` | finish | `/trade` requests and accepts |
| `warzone`, `outposts`, `events` | finish | build portals |
| `profile_create` | finish | creating a 2nd/3rd profile |

Locked message: action bar every time, chat at most every `locked.chat-cooldown-seconds` (10): `✘ Unlocks at tutorial step 8 (Sell). [Tutorial]`.

## Starter chest and pack (owner answer 158, TQ14–TQ18)
- New islands: the islands module asks `StarterContents` (only while `starter-chest.enabled`; otherwise its own `islands.yml → starter-chest.items`). Plain items from `starter-chest.items` (`slot: item: amount`; default wheat seeds 8, bone meal 8, oak saplings 2, lava bucket, bread 16, crafting table, chest), the **Phoenix Field Guide** (6 pages, `messages → book.pages`) in `starter-chest.book-slot` (4) and the **Forge starter kit** (5 tools, `starter-chest.kit-slots` 10–14). The kit counts as the profile's first starter kit claim (24 h timer, TQ15); while a claim runs no second kit. No money note (TQ16). Anything that does not fit goes to the owner's inventory / `/box`.
- **Starter pack** (team joiners, or no usable chest): bread 16 (`starter-pack.items`), the Field Guide and the starter kit (if no claim runs), straight to the inventory (overflow `/box`). Once per account (atomic claim).
- **Invite choice at the portal** (TQ19, `islands.yml → members.portal-invite-choice`): a player without an island who has pending invites gets a 3-row menu: **Create my own island** or **Join &lt;island&gt;** (newest 3); joining teleports them to that island.

## Guides (Java + Bedrock)
Boss bar compass (`» Target · 42m «`, `« « Target`, `Target » »`, `Turn around`; counters `Mine blocks · 7 / 15`), action bar hint every 2 s, scoreboard **Next:** line (placeholders `objective`, `tutorial_next`, `tutorial_step`), per-player particles (flame trail on the spawn road, flame + end-rod column at the target, happy-villager dust on farm spots and ore), and a hologram over the target only that player sees (text display; Bedrock viewers get a name-tag armor stand, `guide.holograms-bedrock`, TQ32). All in `guide.*`.

## Data and audit
- `tutorial_progress` (state `new|active|finished|legacy|staff_skipped`, step, times, flow version, pack given, rewarded, starter chest point), `tutorial_step_done` (early flag, profile), `tutorial_counts`, `tutorial_optional` (V003: tipped, done, rewarded, progress per optional step), `tutorial_events` (start, done, stuck, skip, quit, resume, replay, finish, idle, staff_*, camera_play, camera_skip, opt_tip, opt_done; Java/Bedrock, time in step) for the stats. Events older than `analytics.keep-days` (180) are deleted at start and daily (audit `tutorial_events_purge`).
- First start: every player already in `players` is marked **legacy** (finished, no reward, TQ6), once (migration `V002`).
- Audit category **`tutorial`** (`/logs`, helpers+): `tutorial_start`, `tutorial_step`, `tutorial_finish`, `tutorial_reward`, `tutorial_pack`, `tutorial_optional`, `tutorial_events_purge`, `tutorial_staff_skip|reset|step`; the kit as `starter_kit` (category `item`, source `tutorial`).

## Hooks for other modules
`TutorialGate` (`denied(services, player, gate)`, `allows(...)`), `TutorialService` (`state`, `currentStep`, `start`, `openMenu`, `openStaffView`), `StarterContents`, `Gates` (ids), new events `MenuOpenedEvent` (every menu, `Menu#menuId()`), `ItemsSoldEvent`, `BankDepositEvent`, `IslandJoinedEvent`, service `DepthsZones` (zone at a location, zone spawn), `ToolService#starterKit`.
