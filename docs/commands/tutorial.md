# Join tutorial (`modules/tutorial`)

The first thing on the server (owner answers 43, 44, 131, 158, 159; plan `docs/plans/TUTORIAL.md`). New players walk into the **Your Island** portal in the Gatehouse; 11 short steps (about 3 min) teach the core loop and **unlock features step by step** (replaces the old 30-minute new-account limit). Progress is per **account**, once (finishing on one profile opens every profile). Settings: `tutorial.yml` (every step, lock, count, target, guide, reward and the starter chest; editable in `/admin`); texts: `messages/tutorial.yml`; data: `db/tutorial`. Reload: `/skyblock reload tutorial`. Help: `/help tutorial`.

Built: milestones **TU1–TU4** (locks, step engine, guides, starter chest, finish reward, invite choice at the portal). Later: TU3 cameras (the arrive step is a 6 s title for now; Watch again replays titles + hints), TU5 companion Pip + Guide NPC opens `/tutorial`, TU6 optional steps + tips, TU7 stats menu + event purge, TU8 polish.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/tutorial` | Tutorial menu: progress (step N/11, time), **Take me there** (instant teleport to the step's place), **I'm stuck** (hint again; after 3 presses skippable steps can be skipped: arrive, depths, help; never sell or bank), **Steps** (all steps, click the current one to go there), **Watch again** (titles + hints of every step, nothing changes, Stop in the menu), **What unlocks when**. Not started: **Start**. Done: **Where next?** | `skyblock.tutorial` (everyone) | ✅ |
| `/tutorial admin status <player>` | Step, state, done steps, reward paid (online or offline) | `skyblock.tutorial.admin.status` (helper+) | ✅ |
| `/tutorial admin skip <player>` | Finish the tutorial for a player, **no reward**; every lock opens. Confirm. Audited `tutorial_staff_skip` | `skyblock.tutorial.admin` (mod+) | ✅ |
| `/tutorial admin reset <player>` | Back to the start, locks return, **never a second reward**. Confirm. Audited `tutorial_staff_reset` | `skyblock.tutorial.admin` (mod+) | ✅ |
| `/tutorial admin step <player> <step>` | Move a player to a step (earlier steps count as done). Audited `tutorial_staff_step` | `skyblock.tutorial.admin` (mod+) | ✅ |
| `/tutorial admin stats` | Funnel menu (TU7, not built) | – | ⬜ |

Staff (no command): **`/inspect <player>` → Tutorial** (written book, slot 41) shows the state, step, times, done steps, reward paid and Watch-again count; mods and up get **Skip** and **Reset** (confirm screens). `skyblock.tutorial.bypass` (builder, helper and up): no locks; the tutorial is offered in chat, never started by force (TQ28).

## Steps (`flow.steps`, TQ1)
| # | Id | Done when |
|---|---|---|
| 1 | `portal` | `IslandPortalEnteredEvent` (the portal created or found the island). Flame trail along `trail.nodes`, compass to `targets.portal` |
| 2 | `arrive` | `steps.arrive.seconds` (6) after arriving (title "Welcome home"; the TU3 camera replaces it) |
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
- `tutorial_progress` (state `new|active|finished|legacy|staff_skipped`, step, times, flow version, pack given, rewarded, starter chest point), `tutorial_step_done` (early flag, profile), `tutorial_counts`, `tutorial_events` (start, done, stuck, skip, quit, resume, replay, finish, staff_*; Java/Bedrock, time in step) for the TU7 funnel.
- First start: every player already in `players` is marked **legacy** (finished, no reward, TQ6), once (migration `V002`).
- Audit category **`tutorial`** (`/logs`, helpers+): `tutorial_start`, `tutorial_step`, `tutorial_finish`, `tutorial_reward`, `tutorial_pack`, `tutorial_staff_skip|reset|step`; the kit as `starter_kit` (category `item`, source `tutorial`).

## Hooks for other modules
`TutorialGate` (`denied(services, player, gate)`, `allows(...)`), `TutorialService` (`state`, `currentStep`, `start`, `openMenu`, `openStaffView`), `StarterContents`, `Gates` (ids), new events `MenuOpenedEvent` (every menu, `Menu#menuId()`), `ItemsSoldEvent`, `BankDepositEvent`, `IslandJoinedEvent`, service `DepthsZones` (zone at a location, zone spawn), `ToolService#starterKit`.
