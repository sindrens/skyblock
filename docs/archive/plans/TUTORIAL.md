# Join Flow, Tutorial and Starter Chest Plan (PLANTUT)

> **Status:** built (TU1-TU4 in TUT1, TU5-TU8 in TUT2, 2026-10-30); see `docs/commands/tutorial.md` for what exists. Originally: plan, 2026-10-30. **Every name, number and rule is a default** the owner can change. All numbers go into typed configs (`tutorial.yml`), all text into `messages/tutorial.yml`.
> Decisions that matter have an id `TQ<n>`. The owner answers them on the one-click page (§12, JSON at the end).
> Sources: `OWNER_ANSWERS_ROUND1.md` (**43, 44, 131, 133, 158, 159, 160, 203, 254, 273**), `OWNER_PRINCIPLES.md` (**4, 5**, also 1, 2, 3, 6), `DESIGN.md` §2, `STANDARDS.md` (tutorial under 3 min, re-openable), `BUILDER_BRIEF.md` §5.11, `server/builds/spawn_plan.json` (`first_view`, `tutorial_route`, walking distances), `server/builds/spawn_gatehouse_v1.yml` (portal `to_island`), `server/builds/islands_starter_meadow_v1.yml` (starter chest, pond, generator pad), `ECONOMY_BALANCE.md` §2, §4.1, `docs/commands/{tools,depths,economy,islands,npcs,menus}.md`, PORTALS branch (`IslandPortalEnteredEvent`), `common/npc/NpcDirectory`, `common/hook/BedrockInfo`.

---

## 0. Summary

- **First thing on the server:** the player walks into the **Your Island** portal in the Gatehouse (spawn, ~95 blocks north of the spawn point). The portal creates the island and fires `IslandPortalEnteredEvent(firstTime = true)`. The tutorial runs on the island and in the Depths.
- **11 core steps, about 2 min 50 s:** Portal → Arrival → Starter chest → First crop → Menu → To the Depths → Mine → Sell → Forge → Help NPC → Bank. Then **Finish**: reward, full permissions, "Where next?" menu.
- **Locks open step by step** (owner 131). This replaces the removed 30-minute new-account limit. Team features after step 4, the Depths after step 5, the auction house after step 8, `/pay` and `/withdraw` after step 11, `/trade`, the warzone and new profiles at Finish.
- **Once per account.** Finishing on one profile opens everything on every profile (TQ3).
- **Guides that work on Java and Bedrock:** boss bar compass, flame trail on the ground, a flame column at the target, title and action bar text, a scoreboard line, holograms above targets, a small red parrot companion ("Pip") only you can see, and 3 short camera flyovers (skippable).
- **Custom starter chest** (owner 158): the Forge starter kit (5 tools), 8 wheat seeds, 8 bone meal, 2 oak saplings, 1 lava bucket, 16 bread, a crafting table, a chest and a 6-page guide book. No money note. Every item has a job in the first hour (§6).
- **Extras:** 9 optional steps for later, re-watch from `/tutorial`, staff skip/reset, a funnel menu that shows where players stop.
- **Milestones TU1–TU8** (§11). **32 questions** (§12).

---

## 1. Rules this plan follows

| Rule | What it means here |
|---|---|
| Tutorial unlocks the game (principle 4, owner 131, 159) | The portal is the first thing. The tutorial gates money-moving and PvP features per step. No playtime timers |
| No waiting, no busywork (principle 1) | Core under 3 minutes. Steps done early count. Every camera can be skipped. "Take me there" teleports are instant |
| Help everywhere, lores clean (principle 5, owner 44) | Help sits in NPCs, holograms, the guide book and `/tutorial`. Item lores stay short |
| Bedrock first | Every choice is a chest menu. No clickable chat needed. No glow outlines needed. Only Bedrock-safe symbols (`» « ▸ ▶ ✔ ✘ ★ ● ·`). Boss bar and titles short |
| Profiles are islands (principle 2) | Tutorial progress is per **account**. Starter chest, starter kit claim and reward money land on the **profile** you are on. Nothing moves value between your own profiles |
| Nothing lost by accident (principle 3) | Full inventory: rewards go to `/box`. A disconnect never loses progress. A camera always gives back your game mode and position |
| English only (principle 6) | All tutorial text in English |
| Everything audited | Start, every step, finish, reward, staff skip/reset, replays: audit category `tutorial` |
| Atomic DB for value | The reward payout and the "finished" flag are one transaction with an idempotency key (`tutorial_reward:<uuid>`) |

---

## 2. The flow at a glance

```
JOIN (spawn dais, facing north)
  │  6 s flyover: spawn → statue → keep → Gatehouse portal
  ▼
1 PORTAL  walk ~95 blocks north, flame trail, boss bar compass
  │  island created (portal), IslandPortalEnteredEvent(firstTime)
  ▼
2 ARRIVE  6 s camera around the island
3 CHEST   open the starter chest, take the items
4 CROP    till dirt, plant 3 wheat seeds
5 MENU    open /menu (Nether Star), click Island
  │  menu → The Depths → Mine Tier 1 (instant teleport)
  ▼
6 DEPTHS  5 s camera over the Gate: mine, Forge, Sell
7 MINE    break 15 blocks (drops go to the satchel)
8 SELL    open the satchel, press Sell all
9 FORGE   walk to the Forge NPC, open the Forge, look at Tier 2
10 HELP   right-click the Depths Guide (help is on every NPC)
11 BANK   open /bank, deposit any amount
  ▼
FINISH  title + reward + every lock open + "Where next?" menu
```

Why this order:
- The island comes first (owner 159). The first crop is the island's first production.
- `/menu` comes before the Depths because the menu is how players travel (Bedrock never types long commands, owner 44).
- Mine → Sell → Forge is the core money loop (ECONOMY_BALANCE §2: the Depths are the main income; the Forge is the first goal).
- The help NPC step teaches "right-click any NPC" once, so players find help alone later.
- Bank is last: it is the step before `/pay` and `/withdraw` open.

---

## 3. Step table (core)

Time = target for a new player. "Done when" is checked by the tutorial module from events (§9.3). A step done early (before it is reached) is marked done and skipped when reached.

| # | Id | Where | Player does | Done when | Guide techniques | Opens (TQ4) | Time |
|---|---|---|---|---|---|---|---|
| 1 | `portal` | Spawn → Gatehouse | Watch the flyover, walk into **Your Island** portal | `IslandPortalEnteredEvent` | Flyover, flame trail, boss bar compass, flame column at the portal, hologram "Your Island", Pip flies ahead | – | 30 s |
| 2 | `arrive` | Island | Watch a short camera around the island | Camera ends or skipped | Island camera, title | – | 6 s |
| 3 | `chest` | Island (starter chest) | Open the chest, take the items | Chest opened and the starter pickaxe is in the inventory | Hologram "Starter Chest", flame column, Pip perches on the chest, boss bar compass | – | 10 s |
| 4 | `crop` | Island (farm spot by the pond) | Till dirt with the hoe, plant 3 wheat seeds | 3 wheat crops placed on your island | Dust markers on 3 suggested blocks, action bar counter | Team: `/is invite`, `/is coop`, `/is visit`, warps | 20 s |
| 5 | `menu` | Anywhere | Open `/menu` (Nether Star in the hotbar), click **Island** | The island menu opened from `/menu` | Title, action bar "Right-click the Nether Star", menu button shows `» TUTORIAL` line | The Depths (portal + `/dungeon`) | 10 s |
| 6 | `depths` | Depths, Mine Tier 1 | `/menu` → The Depths → Mine Tier 1, watch the camera | Arrived in zone `mine_t01` | Action bar, Depths camera | – | 12 s |
| 7 | `mine` | Mine Tier 1 | Break 15 blocks | 15 Mining XP gains in the Depths (`SkillXpGainEvent`) | Boss bar progress `7 / 15`, dust on nearby ore blocks | – | 30 s |
| 8 | `sell` | Anywhere | Open the satchel (`/menu` → The Depths → Satchel), press **Sell all** | Any sale (satchel, `/sell`, NPC) | Title, action bar, scoreboard line | Auction house (`/ah`) | 10 s |
| 9 | `forge` | Depths Gate, Forge NPC | Walk to the Forge NPC, open the Forge, look at Tier 2 | Forge menu opened | Boss bar compass to `depths_forge` (`NpcDirectory`), flame column, Pip | – | 15 s |
| 10 | `help` | Depths Gate, Guide NPC | Right-click the Depths Guide | `NpcClickEvent` on role `dungeon` (or any NPC) | Compass to `depths_guide`, hologram "Right-click me" | – | 8 s |
| 11 | `bank` | Anywhere | Open `/bank` (or `/menu` → Bank), deposit any amount | One personal bank deposit | Title, action bar | `/pay`, `/withdraw` | 12 s |
| – | `finish` | Anywhere | Read the finish screen, pick where to go | – | Title, firework (only you see it), sound, chat summary, "Where next?" menu | `/trade`, warzone, new profiles, everything else | 6 s |

**Total: about 2 min 50 s** (cameras 17 s of it, all skippable).

### 3.1 Optional steps (later, from `/tutorial` or a tip)
Each one is 20–60 s. Offered by a tip the first time the player reaches the place (TQ26), or from the `/tutorial` menu. Small reward each (TQ8).

| # | Id | What | Where |
|---|---|---|---|
| O1 | `generator` | Build the cobble generator: pour the lava bucket at the marked spot by the pond, mine 5 cobblestone | Island (generator pad) |
| O2 | `spawn_tour` | Walk the Market Square: Shop, Sell chest, Bank, Auction house NPCs (camera + compass) | Spawn |
| O3 | `settings` | Open `/settings`, try "Hide other players" and sounds | Anywhere |
| O4 | `skills` | Open `/skills`, see Mining and the tier unlocks | Anywhere |
| O5 | `team` | Open `/is members`, see invite and coop; read the team rules | Island |
| O6 | `upgrades` | Open the island upgrades (when Phase 5 exists; hidden until then) | Island |
| O7 | `enchanter` | Visit the Enchanter, see a book | Spawn, Forge Quarter |
| O8 | `wardrobe` | Open `/wardrobe`, see Delver's Garb | Anywhere |
| O9 | `warzone` | Read the warzone rules at the portal (PvP, what you can lose) | Spawn, Gatehouse |

Why settings and the shop are optional, not core: the core must stay under 3 minutes (STANDARDS). Settings and shop are one click from `/menu`, which step 5 teaches. The finish screen points at both. (TQ30)

---

## 4. Locks per step (owner 131)

Locks are checked in code by a small `TutorialGate` service in `common` (§9.1). Not LuckPerms groups: per-step locks change often, must be instant and unit-testable, and LuckPerms writes for every step are slow. Staff with `skyblock.tutorial.bypass` never see a lock.

| Feature | Open from | Why |
|---|---|---|
| Chat, `/msg`, `/spawn`, `/help`, `/menu`, `/settings`, `/is` (own island), `/is home`, `/sell`, `/shop` (buy), `/bal`, `/baltop`, `/tpa` | Start | Harmless. New players must be able to talk and ask (TQ5) |
| Accept an island invite (`/is accept`, `/is invites`) | Start | The invite path (§7) |
| `/is invite`, `/is coop`, `/is visit`, island warps | Step 4 `crop` done | They know what an island is now |
| The Depths: `to_depths` portal, `/dungeon` teleports | Step 5 `menu` done | Step 6 uses them; before that the arrow points to the island |
| Auction house `/ah` (browse, buy, list) | Step 8 `sell` done | They know prices now. Stops fresh alts buying junk at fake prices from a main |
| `/pay`, `/withdraw` (money notes) | Step 11 `bank` done | Money-moving. A fresh alt can't send money in the first minute |
| `/trade` | Finish | Moves money and items |
| Warzone portal | Finish | PvP: they should know the rules first |
| Create a 2nd/3rd profile (`/profiles`) | Finish | A new profile mid-tutorial would split the tutorial |
| Outposts, events portals (when built) | Finish | Later content |

Locked message (action bar + chat once per 10 s): `✘ Unlocks at tutorial step 8 (Sell). /tutorial to continue.`
Config: `gates` list in `tutorial.yml`, as `feature: step-id` lines (typed, validated: unknown feature or step = startup warning).

Fail-open rule: if the tutorial module is disabled, `TutorialGate` is absent and every module treats features as open (same as today).

---

## 5. Mockups per step (what the player sees)

Legend: **T** title, **S** subtitle, **AB** action bar, **BB** boss bar (red, progress = distance or count), **SB** scoreboard line, **Pip** companion line (chat `Pip » ...` + name tag), **W** what is in the world. Titles ≤ 20 characters, subtitles ≤ 36 (Bedrock).

### Join (before step 1)
```
Fade in from black (blindness 1 s)
T  : PHOENIX SKYBLOCK
S  : Your island is waiting
Camera (6 s): spawn dais → over the statue → over the keep → down to the Gatehouse portals → back to you
```

### 1 · portal
```
T  : Find your island
S  : Walk into the Island portal
BB : » Your Island · 92m «            (turn left: « « Your Island · 92m)
AB : Follow the flames
SB : Tutorial 1/11 · » Island portal
Pip: Your island is past the gate. Follow me!
W  : flame trail on the north road (every 2 blocks, 20 blocks ahead of you)
     flame column at the to_island portal, hologram "Your Island · Walk in"
```
Compass rule: angle between your facing and the target. Within ±30° `» name · Xm «`. Left `« « name · Xm`. Right `name · Xm » »`. Behind `Turn around · name · Xm`. Only Bedrock-safe symbols.

### 2 · arrive
```
Fade (0.5 s)
T  : Welcome home
S  : This island is yours
Camera (6 s): orbit around the island at 12 blocks, ends behind you facing the shed
Sound: block.beacon.activate (only you)
AB : Sneak to skip
```

### 3 · chest
```
T  : (none)
AB : Open your starter chest
BB : » Starter Chest · 3m «
SB : Tutorial 3/11 · » Open the chest
Pip: (lands on the chest) Everything you need to start is in here.
W  : hologram over the chest: "Starter Chest" / "» Open me «", small flame column
After opening: AB "Take everything. The book explains it all."
```

### 4 · crop
```
AB : Plant wheat: 0 / 3
BB : » Farm spot · 4m «
SB : Tutorial 4/11 · » Plant 3 seeds
Pip: Right-click dirt with your hoe, then plant the seeds.
W  : happy-villager dust on 3 dirt blocks next to the pond
Done: S "Your first crop!"  AB "Team features unlocked: /is invite, /is visit"
```

### 5 · menu
```
T  : The menu
S  : Right-click the Nether Star
AB : Everything is in /menu
SB : Tutorial 5/11 · » Open /menu
Pip: The star in your hotbar opens every feature.
In /menu: the Island button has an extra line "» TUTORIAL: click me"
Done: AB "The Depths unlocked. Open /menu » The Depths"
```

### 6 · depths
```
AB : /menu » The Depths » Mine Tier 1
SB : Tutorial 6/11 · » Go to the Depths
On arrival: Camera (5 s): over the Gate, past the Forge and the Sell NPC, down into Mine Tier 1
T  : The Depths
S  : Mine, sell, forge, repeat
```

### 7 · mine
```
BB : Mine blocks · 7 / 15         (progress bar fills)
AB : Drops go to your satchel
SB : Tutorial 7/11 · » Mine 15 blocks
Pip: Break the ore around you. It grows back for you only.
W  : dust markers on the 5 nearest unbroken blocks
```

### 8 · sell
```
T  : Sell your loot
S  : /menu » The Depths » Satchel
AB : Press Sell all in your satchel
SB : Tutorial 8/11 · » Sell your loot
Done: AB "Auction house unlocked: /ah"
```

### 9 · forge
```
BB : » Forge · 18m «
AB : Better tools = more money
SB : Tutorial 9/11 · » Visit the Forge
Pip: The Forge turns your loot into better tools.
W  : flame column over the depths_forge NPC
In the Forge: Tier 2 has an extra line "» Your first goal"
```

### 10 · help
```
BB : » Depths Guide · 12m «
AB : Right-click any NPC for help
SB : Tutorial 10/11 · » Talk to the Guide
W  : hologram over depths_guide "Right-click me"
```

### 11 · bank
```
T  : Keep it safe
S  : Open /bank, deposit some money
AB : The bank pays interest every 8 hours
SB : Tutorial 11/11 · » Deposit money
Done: AB "/pay and /withdraw unlocked"
```

### Finish
```
T  : TUTORIAL COMPLETE
S  : Every feature is unlocked
Sound: ui.toast.challenge_complete · firework only you see
Chat: ✔ Tutorial done. You got $2,500, 25 ◆ and the title Firstflight.
      ▸ Unlocked: /trade, warzone, new profiles.
      ▸ Settings: /settings · Shop: /shop · Help: /help · Again: /tutorial
Menu "Where next?" (3 rows): [My island] [Keep mining] [Tour the spawn (O2)]
```

---

## 6. Custom starter chest (owner 158)

The starter island has an empty chest at `containers.starter_chest` (`-1, 101, -2` relative). The island code fills it once when the island is created. Today it holds lava, ice, bone meal, bread, saplings, sugar cane, cactus, melon and pumpkin seeds (`islands.yml → starter-chest.items`). The new contents fit this server:

| Slot | Item | Amount | Why |
|---|---|---|---|
| 10 | Starter Pickaxe (`tool_starter_pickaxe`) | 1 | The Depths are the main income. Needed in step 7 |
| 11 | Starter Axe | 1 | Depths forest (Foraging) and island wood |
| 12 | Starter Hoe | 1 | Step 4 (till dirt) and island farming |
| 13 | Starter Sword | 1 | Depths combat zones later |
| 14 | Starter Rod | 1 | Part of the Forge starter kit (fishing later) |
| 15 | Wheat Seeds | 8 | Wheat is island crop tier 1 (ECONOMY_BALANCE §4.1). Step 4 plants 3 |
| 16 | Bone Meal | 8 | Grows the first wheat at once, so the player sees a harvest in the first minutes |
| 19 | Oak Sapling | 2 | Wood for crafting and building; trees regrow on the island |
| 20 | Lava Bucket | 1 | The starter island has a generator pad and a 4-source pond built for a cobble generator (O1) |
| 21 | Bread | 16 | Food for the first session |
| 22 | Crafting Table | 1 | The starter island has none; needed for chests and tools |
| 23 | Chest | 1 | First storage |
| 4 | Phoenix Field Guide (written book) | 1 | The whole core loop in 6 short pages (below). Bedrock reads books |

**The 5 tools are the Forge starter kit** (tools module, M4.2). They are made by `ToolService` for that profile and count as the profile's first starter kit claim, so the 24 h re-claim timer starts (TQ15). Lost pieces come back from the Forge's Starter kit button. Starter tools can't go on the auction house (as built), so they are worth nothing to alts.

**Removed from today's list, and why** (TQ17):
- Sugar cane, cactus, melon and pumpkin seeds: their crop tiers are 4, 5 and 7. Planted on day one they confuse players.
- Ice: the pond already has 4 water sources (an infinite source). Ice adds nothing.

**No money note** (TQ16): every profile already starts with $1,000 (`economy.yml → start.money`, owner 121). A note is tradeable value, so free notes would feed alt accounts. The finish reward pays money straight to the profile instead.

**Phoenix Field Guide** (6 pages, text in `messages/tutorial.yml → book.pages`):
1. Welcome. The loop: mine in the Depths, sell, forge better tools, grow your island.
2. Your island: `/is` menu, home, crops, the cobble generator.
3. The Depths: `/menu` → The Depths. Blocks grow back for you only. Drops go to your satchel.
4. Money: sell (satchel, `/sell`), bank (interest every 8 h), auction house.
5. Tools: the Forge turns loot into higher tiers. Tools level up while you use them.
6. Help: right-click any NPC, `/help`, `/tutorial` to watch again.

Config: `islands.yml → starter-chest.items` keeps working for plain items (`material:amount`). New typed keys in `tutorial.yml → starter-chest`: `kit: true` (put the starter kit in), `book: true`, `slots` (`item: slot` lines). The islands module asks a small `StarterContents` service (published by the tutorial module) for the stacks; without it, it uses its own list (TU4).

### 6.1 Starter pack (players who join a team instead)
A new player who accepts an invite gets no island chest. They get a **Starter pack** straight into the inventory (overflow to `/box`): the 5 starter tools (if not yet claimed on that profile), the Field Guide and 16 bread. The island items (seeds, saplings, lava, table, chest) are not given: the team island has them already. (TQ18)

---

## 7. Invites (a new player invited to an island)

- **Invite before the portal** (the player is at spawn, has no island): the invite shows as normal (chat line on Java, a menu button on Bedrock via `/is invites`). The boss bar adds `Invite from Name · /is invites`.
- **Portal with a pending invite:** the portal does not create an island at once. It opens a 3-row choice menu: **Create my own island** / **Join Name's island** (one button per invite, newest first, max 3). (TQ19)
- **Joining:** the player is teleported to the team island. Step 1 is done. Step 2 shows the team island. Step 3 becomes **Starter pack** (done when the pack is given). Step 4 plants on the team island (members can place by default, ISL2 role rights). The rest is the same.
- **Invite after the island was created** (mid-tutorial): owner 254 applies. The player must `/is disband` first or use a free profile. Tutorial progress is per account, so it continues on the new island.
- **Inviting others:** sending invites opens at step 4. Accepting is open from the start.
- **Coop:** a player in the tutorial can be coop on another island (accepting is open). Their own steps stay on their own island.

---

## 8. Edge cases

| Case | What happens |
|---|---|
| Disconnect mid-step | Progress is saved per step. On rejoin they spawn at spawn (owner 273), then after 2 s: title "Welcome back", and an instant teleport to the step's place (island for 2–5, Mine Tier 1 for 6–10; steps 1 and 11 stay). Sneak in those 2 s to stay at spawn (TQ20) |
| Disconnect or restart during a camera | Before a camera starts, game mode, position, flight and walk speed are saved in the player's data container (PDC). On join the module restores them first, before anything else |
| Death mid-tutorial | Respawn at spawn (owner 160). The boss bar points back; the `/tutorial` menu has **Take me there** (instant) |
| Player leaves the step's place (`/spawn`, visits) | The boss bar points to the place, or says `Go back · /is home`. Nothing is lost |
| Island already exists | Portal fires `firstTime = false`. If the tutorial was never started, it starts at step 2 on that island. If the starter chest is gone or empty, step 3 gives the Starter pack instead |
| Island paste failed (placeholder islet) | No chest point: step 3 gives the chest contents straight into the inventory (overflow to `/box`) |
| Steps done early | Marked done when they happen (e.g. `/menu` opened during step 2). Skipped when reached |
| Full inventory | Chest items stay in the chest. Starter pack and reward overflow go to `/box` (principle 3) |
| Another profile (2nd/3rd) | The tutorial is per account: no tutorial again. A new island on a new profile still gets the starter chest and a 6 s welcome camera (TQ31) |
| Players from before the tutorial | On first start of the module, every player with any island, money row or playtime > 0 is marked **finished (legacy)**: no locks, no reward (TQ6) |
| Staff | `skyblock.tutorial.bypass`: no locks. The tutorial is offered but never started by force; they can run it to test. In staff mode or vanish the boss bar, Pip and holograms are hidden and the step clock pauses (TQ28) |
| Bedrock | Same steps. No clickable chat: choices are menus. No glow: flame columns and particles instead. Camera: Geyser camera API if available, else cut-and-fade shots (§9.4). Mannequin/text-display support follows the NPCHOLO setting (TQ32). Touch players tap = hit, and NPCs act on a hit (as built) |
| AFK during a step | Nothing times out. After 5 min idle, one reminder (sound + title) |
| Stuck on a step | `/tutorial` → **I'm stuck**: re-shows the camera/hint, offers **Take me there**, logs a `stuck` event. After 3 stuck presses on one step, the step can be skipped by the player (only optional steps and steps 2, 6, 10; never 8 or 11, which open money features) |
| Satchel full / no blocks left | Depths blocks respawn in 10 s (per player). Satchel starts with room for far more than 15 drops |
| Island chest opened by a coop/visitor first | Only the owner's own opening counts. Visitors can't open containers by default (role rights) |
| Tutorial steps change in an update | Progress is stored by step id with a `flow_version`. Finished players stay finished. Players mid-way continue at the first not-done step |
| Profile switch mid-tutorial | New players have one profile; creating another is locked until Finish |
| Grim anticheat | The player is exempt while a camera runs (spectator on Java; frozen on Bedrock) |

---

## 9. How it works (for the builder agent)

### 9.1 New module `modules/tutorial` + common API
- `common/tutorial/TutorialGate`: `boolean open(Player, Gate)`, `Optional<Component> lockedMessage(Player, Gate)`. `Gate` = string ids (`island_team`, `depths`, `auction`, `pay`, `withdraw`, `trade`, `warzone`, `profile_create`, `outposts`, `events`).
- `common/tutorial/TutorialService`: `state(uuid)`, `currentStep(uuid)`, `isActive(player)`, `start(player)`, `openMenu(player)`. Published via `core.services()`.
- `common/tutorial/StarterContents`: `List<ItemStack> chest(Player, profile)`, `List<ItemStack> pack(Player, profile)`.
- Step engine: a small state machine. Each step has an id, a place (`island`, `depths:mine_t01`, `npc:<role>`, `any`), a goal (event + count) and its guide set. Pure logic (`StepRules`: next step, early completion, unlocks per step, compass text) is unit-tested.

### 9.2 Hooks needed in other modules
| Module | Hook |
|---|---|
| worlds (PORTALS) | `IslandPortalEnteredEvent` (exists on the PORTALS branch). Portal checks `TutorialGate` for `depths`, `warzone`, `outposts`, `events`. Pending-invite choice menu before creating the island |
| economy | `PayRules` / `NoteRules` (the `TODO(tutorial)` lines): gates `pay`, `withdraw`. New `ItemsSoldEvent(player, source, value)` after a sale. New `BankDepositEvent(player, amount, account)` |
| auction | Gate `auction` on `/ah` and its menus |
| trade | Gate `trade` |
| islands | Gates `island_team` on invite, coop, visit, warps. Starter chest asks `StarterContents`. API `starterChest(island)` → location or empty |
| depths | Gate `depths` on `/dungeon` teleports. API `zoneAt(location)` → zone id (add if missing). Mining count via the existing `SkillXpGainEvent` |
| tools | `ToolService.starterKit(player, profile, markClaimed)` → the 5 stacks |
| menus (common/menu) | New `MenuOpenedEvent(player, menuId)` fired by `MenuService` (ids: `menu`, `island`, `forge`, `satchel`, `bank`, `settings`, `skills`...). Extra `» TUTORIAL` lore line on the button for the current step |
| npcs | Existing `NpcClickEvent` and `NpcDirectory.npcByRole`. Guide role action → `command tutorial` (TQ27) |
| profiles | Gate `profile_create` |
| hud | Placeholder `tutorial_next` shown as the top scoreboard line only while a tutorial runs |
| staff | Button **Tutorial** in the inspect menu (status, skip, reset) |

### 9.3 Guide techniques (Java + Bedrock)
| Technique | Java | Bedrock (Geyser) | Notes |
|---|---|---|---|
| Boss bar compass | ✔ | ✔ | Text from facing vs. target angle, updated every 5 ticks. Progress = distance or count |
| Title / subtitle / action bar | ✔ | ✔ | Short texts, legacy color fallbacks (UI_STYLE §1) |
| Scoreboard line | ✔ | ✔ | `» Next: ...` top line |
| Flame trail | ✔ | ✔ (verify) | `player.spawnParticle` (only that player), `FLAME` every 2 blocks along path nodes, 20 blocks ahead, every 10 ticks. Path nodes from build points `tutorial` (spawn) or a straight line (island) |
| Flame column at target | ✔ | ✔ | `FLAME` + `END_ROD` rising 6 blocks; visible from far |
| Dust markers on blocks | ✔ | ✔ (verify) | `HAPPY_VILLAGER` on farm spots and ores |
| Holograms over targets | ✔ | follows NPCHOLO | Text display visible only to the player (`setVisibleByDefault(false)` + `showEntity`). If Bedrock can't see text displays: a name-tag armor stand for Bedrock viewers (TQ32) |
| Companion Pip | ✔ | ✔ | Red parrot, no AI, silent, invulnerable, visible only to the player. Flies (teleports in steps of 0.5 blocks) to the next target and perches. Name tag shows its current line for 4 s (TQ9) |
| Glow on targets | ✔ | ✘ | Java-only extra (glowing on the chest marker / NPC). Never the only hint |
| Camera flyovers | ✔ | ✔ | §9.4 |
| Clickable chat | ✔ | ✘ | Java extra only. Every choice also exists in the `/tutorial` menu |

Path nodes for step 1: spawn dais `(0.5, 100, 0.5)` → courtyard `(0, 100, -13)` → keep passage `(0, 100, -50)` → Gatehouse entry `(0, 102, -74)` → portal `to_island` `(0, 103, -97)`. About 95 blocks, ~22 s walking (`spawn_plan.json → walking_distances`). The built copper floor line goes to the Forge (old route, DESIGN §2); the flame trail overrides it (TQ13).

### 9.4 Cameras
- **Shots:** each camera is a list of shots in `tutorial.yml → cameras.<id>`: `from`, `to` (build point id or island-relative offset), `look-at`, `ticks`, `title`. Three cameras: `spawn` (6 s), `island` (6 s), `depths` (5 s). Max 8 s each.
- **Java:** save state (PDC) → spectator mode → `setSpectatorTarget(item display)` → move the display with `setTeleportDuration` (client smooths it) → restore. Sneak stops spectating on Java; that is the skip signal.
- **Bedrock:** if Geyser's camera API is present (`GeyserConnection#camera()`, eased camera positions, fades), use it while the player stays in survival, frozen and invulnerable. Sneak = skip. If the API is missing: **cut-and-fade**: blindness fade, teleport to each shot's `from` with the right facing, title, next shot, fade back, teleport home. Works on every client. (TQ11)
- Always: invulnerable during a camera, Grim exempt, restore on quit/kick/death/restart, never more than 20 s of camera in the whole core.

### 9.5 Data (atomic, audited)
- `tutorial_progress`: `player_uuid` (PK), `progress_state` (`new`, `active`, `finished`, `legacy`, `staff_skipped`), `step_id`, `step_started_at`, `started_at`, `finished_at`, `finished_profile`, `replays`, `flow_version`.
- `tutorial_step_done`: `player_uuid`, `step_id`, `done_at`, `early` (bool), `profile_id`. PK (`player_uuid`, `step_id`).
- `tutorial_events`: `id`, `player_uuid`, `step_id`, `event_kind` (`start`, `done`, `stuck`, `skip`, `quit`, `resume`, `replay`, `staff_skip`, `staff_reset`), `created_at`, `bedrock`, `millis_in_step`. Kept `analytics.keep-days` (180).
- No MariaDB reserved words (checked by `SqlReservedWordsTest`).
- Finish = one transaction: set `finished`, pay money + crystals (EconomyService, idempotency key `tutorial_reward:<uuid>`), grant title (Cosmetics). Audit `tutorial/finish`, `tutorial/reward`.

### 9.6 Commands and permissions
| Command | What | Permission |
|---|---|---|
| `/tutorial` | Tutorial menu: progress, **Resume / Take me there**, **I'm stuck**, steps list, optional steps, **Watch again**. Already listed in `docs/commands/planned.md` | `skyblock.tutorial` (everyone) |
| `/tutorial admin status <player>` | Step, times, stuck count, last events | `skyblock.tutorial.admin.status` (helper+) |
| `/tutorial admin skip <player>` | Finish for that player (no reward). Audit `tutorial/staff_skip` | `skyblock.tutorial.admin` (mod+) |
| `/tutorial admin reset <player>` | Back to step 1 (locks return, no reward again). Confirm screen. Audit | `skyblock.tutorial.admin` (mod+) |
| `/tutorial admin step <player> <step>` | Jump to a step (support) | `skyblock.tutorial.admin` (mod+) |
| `/tutorial admin stats` | Funnel menu (§10) | `skyblock.tutorial.stats` (mod+) |

`/tutorial admin` is new and needs owner approval (TQ22). Without approval, the same staff tools live only in the inspect menu and the `/staff` hub. Players' tab completion, step ids and online names complete. `skyblock.tutorial.bypass`: builder, helper and up.

`/tutorial` menu (3 rows, Bedrock-safe):
```
[ ]  [ ]  [Progress book: Step 7/11 Mine]  [ ]  [ ]  [ ]  [ ]  [ ]  [ ]
[Resume ▶] [I'm stuck] [Steps 1-11] [Optional steps] [Watch again] [ ] [ ] [ ] [ ]
[ ]  [ ]  [ ]  [ ]  [Close]  [ ]  [ ]  [Help]  [ ]
```

### 9.7 Watch again
From `/tutorial` → **Watch again**: runs all cameras and step hints in **replay mode**. No locks change, no rewards, any step can be skipped, quit with **Stop** in the menu. Steps also have a single **Replay camera** button. (TQ25)

---

## 10. Analytics (where players drop off)

Staff menu **Tutorial stats** (`/staff` hub, or `/tutorial admin stats`):
- **Funnel:** for the last 7 / 30 days: players who started, reached each step, finished. Shown as one item per step with `reached 412 · done 398 · 96%`.
- **Drop-off:** players whose last event is at that step and who have been offline 24 h or more.
- **Time per step:** median and 90th percentile.
- **Stuck:** stuck presses per step.
- **Java vs Bedrock:** every number split by client.
- Owner use: if one step loses more than 10% of players, change that step first.

Queries run async on `tutorial_events`; the menu caches results 60 s.

---

## 11. Milestones

| # | Milestone | Depends on | Lane |
|---|---|---|---|
| TU1 | Module skeleton, tables, `TutorialGate` + `TutorialService` in common, gates wired in economy (pay, withdraw), auction, trade, islands (team), worlds portals, depths `/dungeon`, profiles; legacy players marked finished; staff bypass; unit tests for gate rules | PORTALS | modules/tutorial, common/tutorial, small hooks |
| TU2 | Step engine (11 steps, early completion, resume, rejoin teleport), boss bar compass, flame trail and columns, dust markers, per-viewer holograms, scoreboard line, action bar; hooks `MenuOpenedEvent`, `ItemsSoldEvent`, `BankDepositEvent`, `zoneAt` | TU1 | modules/tutorial + hooks in menus, economy, depths, hud |
| TU3 | Cameras: Java spectator path, Bedrock Geyser camera or cut-and-fade, state save/restore, sneak skip, the 3 cameras | TU2 | modules/tutorial |
| TU4 | Starter chest (new contents, `StarterContents`, `ToolService.starterKit`), Field Guide book, Starter pack, finish reward + title (atomic), invite choice menu at the portal | TU2, M4.2 | modules/tutorial, islands, tools, worlds |
| TU5 | Companion Pip, NPC lines, `NpcClickEvent` step, spawn Guide NPC opens `/tutorial` | TU2, NPCHOLO | modules/tutorial, npcs config |
| TU6 | Optional steps O1–O9, tips on first visit + `/settings` toggle "Tips", Watch again | TU3, TU4 | modules/tutorial, menus (settings) |
| TU7 | Staff tools (`/tutorial admin` if approved, inspect button), stats menu, event purge | TU2 | modules/tutorial, staff |
| TU8 | Polish pass: every text, Bedrock checks (symbols, title lengths, particles, cameras), owner test checklist | TU1–TU7 | modules/tutorial |

Owner test (after TU8), Java and Bedrock each: new account → portal → all 11 steps under 3 min; locks show the right step; rejoin mid-step; die mid-step; quit during a camera; invite path; `/tutorial` Watch again; staff skip/reset; stats menu numbers.

---

## 12. QUESTIONS (for the one-click page)

Fields: `q` question, `s` suggested answer, `why` one-line reason, `p` 1 = important, 0 = detail.

```json
[
  {"q": "TQ1: Core tutorial = 11 steps (portal, arrival, starter chest, first crop, menu, to the Depths, mine, sell, Forge, help NPC, bank), about 2 min 50 s?", "s": "Yes", "why": "Covers the whole money loop and stays under 3 minutes.", "p": 1},
  {"q": "TQ2: The core tutorial can't be skipped as a whole on the first run (staff can skip it for a player)?", "s": "Yes", "why": "It is short and it replaces the 30-minute limit as the anti-alt gate.", "p": 1},
  {"q": "TQ3: Tutorial once per account: finishing on one profile unlocks every profile?", "s": "Yes", "why": "Nobody should repeat it per profile; locks are about the account, not the island.", "p": 1},
  {"q": "TQ4: Locks open like this: team features after step 4, the Depths after step 5, auction house after step 8, /pay and /withdraw after step 11, /trade, warzone and new profiles at the finish?", "s": "Yes", "why": "Money-moving and PvP open last; everything harmless is open from the start.", "p": 1},
  {"q": "TQ5: Public chat and /msg are open from the start of the tutorial?", "s": "Yes", "why": "New players must be able to ask for help; the chat filter and mutes handle spam.", "p": 1},
  {"q": "TQ6: Players who joined before the tutorial existed count as finished (no locks, no reward)?", "s": "Yes", "why": "Testers and early players should not be locked out of features they already use.", "p": 0},
  {"q": "TQ7: Finish reward: $2,500 + 25 crystals + the title Firstflight, once per account, paid to the profile you finish on?", "s": "Yes", "why": "A clear reward without changing the economy (first hour earns about $50k).", "p": 1},
  {"q": "TQ8: Optional steps give 5 crystals each, once per account?", "s": "Yes", "why": "A small push to learn the rest of the server.", "p": 0},
  {"q": "TQ9: The guide is a small red parrot companion called Pip that only you can see?", "s": "Yes", "why": "Phoenix-like, never blocks the way, and parrots work on Bedrock; a player-shaped guide is the alternative.", "p": 1},
  {"q": "TQ10: Three short camera flyovers (spawn 6 s, island 6 s, Depths 5 s), skippable by sneaking?", "s": "Yes", "why": "Shows where to go like a real intro, but never wastes a player's time.", "p": 1},
  {"q": "TQ11: On Bedrock use Geyser's camera when available, otherwise short cut-and-fade shots?", "s": "Yes", "why": "Smooth where Bedrock supports it, and always works on every client.", "p": 0},
  {"q": "TQ12: New players walk the ~95 blocks from the spawn point to the Island portal (about 22 s) with a flame trail?", "s": "Yes", "why": "Their first walk shows the spawn; teleporting them would skip the first impression.", "p": 1},
  {"q": "TQ13: Ask the builder to change the copper floor line at spawn so it leads to the Island portal (it leads to the Forge now)?", "s": "Yes", "why": "The starter kit is now in the starter chest, so the first stop is the portal.", "p": 0},
  {"q": "TQ14: Starter chest = 5 starter tools, 8 wheat seeds, 8 bone meal, 2 oak saplings, 1 lava bucket, 16 bread, crafting table, chest, Field Guide book?", "s": "Yes", "why": "Every item has a job in the first hour of this server.", "p": 1},
  {"q": "TQ15: The 5 starter tools in the chest count as that profile's first starter kit claim (the 24 h timer starts)?", "s": "Yes", "why": "Stops getting two kits on day one; lost pieces still come back from the Forge.", "p": 0},
  {"q": "TQ16: No money note in the starter chest (every profile already starts with $1,000)?", "s": "Yes", "why": "Notes are tradeable value and would feed alt accounts.", "p": 0},
  {"q": "TQ17: Remove sugar cane, cactus, melon and pumpkin seeds and ice from the starter chest?", "s": "Yes", "why": "Those crops belong to later island tiers, and the pond already has infinite water.", "p": 0},
  {"q": "TQ18: A new player who joins a team gets a Starter pack (tools, book, bread) instead of a chest?", "s": "Yes", "why": "The team island already has the island items.", "p": 0},
  {"q": "TQ19: With a pending invite, the Island portal asks: create my own island or join that island?", "s": "Yes", "why": "Friends who invite a new player shouldn't lose them to an accidental solo island.", "p": 1},
  {"q": "TQ20: Rejoining mid-tutorial takes you straight back to your step after 2 s (sneak to stay at spawn)?", "s": "Yes", "why": "No walking back through the spawn to continue.", "p": 0},
  {"q": "TQ21: Steps done early count (for example opening /menu before the menu step)?", "s": "Yes", "why": "No busywork for players who explore.", "p": 0},
  {"q": "TQ22: Add the staff command /tutorial admin (status, skip, reset, step, stats)?", "s": "Yes", "why": "Support needs to fix a stuck player fast; it is a sub-command of the planned /tutorial.", "p": 1},
  {"q": "TQ23: Helpers can see tutorial status; only mods and up can skip or reset?", "s": "Yes", "why": "Skipping opens money features, so it belongs to mods.", "p": 0},
  {"q": "TQ24: Tutorial stats menu for mods and up (funnel, drop-off, time per step, Java vs Bedrock), events kept 180 days?", "s": "Yes", "why": "Shows which step loses players so you can fix it.", "p": 0},
  {"q": "TQ25: Watch again from /tutorial: no rewards, no locks change, quit any time?", "s": "Yes", "why": "Lets players and staff relearn without side effects.", "p": 0},
  {"q": "TQ26: After the tutorial, the first visit to a place offers its optional tour (with a Tips on/off toggle in /settings)?", "s": "Yes", "why": "Help shows up where it is needed instead of a long tutorial.", "p": 0},
  {"q": "TQ27: The Guide NPC at spawn opens the tutorial menu instead of the help menu?", "s": "Yes", "why": "The first NPC a new player sees should lead into the tutorial.", "p": 0},
  {"q": "TQ28: Staff never have tutorial locks, but can run the tutorial to test it?", "s": "Yes", "why": "Staff must reach every feature at once.", "p": 0},
  {"q": "TQ29: The mine step asks for 15 blocks in Mine Tier 1?", "s": "Yes", "why": "Enough to fill the satchel with something worth selling in about 30 s.", "p": 0},
  {"q": "TQ30: Settings and the shop are optional steps, not core steps?", "s": "Yes", "why": "Keeps the core under 3 minutes; both are one click from /menu and the finish screen points to them.", "p": 0},
  {"q": "TQ31: A new island on a 2nd or 3rd profile gets the starter chest and a 6 s welcome camera, but no tutorial?", "s": "Yes", "why": "The player already knows the game; the island is still new.", "p": 0},
  {"q": "TQ32: If Bedrock can't see text holograms or mannequins, use name tags and villagers for Bedrock viewers?", "s": "Yes", "why": "Every hint must be visible on both clients.", "p": 0}
]
```
