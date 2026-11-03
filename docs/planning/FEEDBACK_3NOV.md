# Player feedback 31 Oct - 3 Nov 2026: implementation plan

**Status:** plan, written 3 Nov 2026 on branch `q/release-v4` (HEAD `21e11399`). Read-only analysis of the code at that commit.
**Owner order:** plan everything fully, then implement every item properly. Where the owner has not decided, the plan picks the
safest sensible default and marks it **Default (owner can change)**. All of those are repeated in the last section.

Already done and excluded: hunger off, sell every block, `/shop <item>`, AH blocks menu item, Depths inventory/satchel kit, tutorial
text in GUIs, no free starter kit, forge requirements/balance, hoe skips unripe crops, plain vanilla menus.

## House rules that apply to every item (do not repeat per item)

- No hardcoded numbers/text/items: every value is a `@Setting` in the module config (`ModuleConfig`), every text a key in
  `modules/<m>/src/main/resources/messages/<m>.yml` **and** its copy in `server/plugins/PhoenixSkyblock/messages/<m>.yml`
  (tracked, deployed by `server/scripts/deploy.sh`; tracked config files in `server/plugins/PhoenixSkyblock/*.yml` are deployed too, so
  a changed default must also be changed there or carried by `ModuleConfig.version()` + `valueChanges()` which only flips
  *untouched* values).
- Permissions: `modules/<m>/src/main/resources/permissions/<m>.yml` (one owner per node, `PermissionFilesTest`), rank grants in
  `server/permissions/<m>.txt` (every granted `skyblock.*` node must be declared).
- Commands: `CommandSpec` with an `Arg` suggestion source for every argument (tab completion is mandatory), an entry in
  `docs/commands/<m>.md`, then `./gradlew commandsIndex`. Every command also reachable from a GUI and `/help` (`modules/menus/.../help.yml`).
- Audit: `core.audit().log(AuditCategory.X, "<event>", Actor, ...)` for anything that moves money/items or is staff-triggered.
- Dupe safety (STANDARDS section 3): inventory -> database = take items, `PlayerSaves.commitAfterSave`; database -> inventory = delete row first,
  give, `PlayerSaves.save`. Money only through `EconomyService.execute(Transaction...)` with an idempotency key.
- Tests: unit tests for pure logic (`./gradlew build` before merge, never start a server), manual checklist (Java + Bedrock) per item
  appended to `docs/TEST_GUIDE.md`, `CHANGELOG.md` entry, ROADMAP/QUEUE row, new owner decisions into `docs/OPEN_QUESTIONS.md`
  ("Waiting for owner") and the Decisions page.
- Suggested commit order (one logical change each, conventional commits): Q1-Q4, Q5, Q6, Q7, then A2/A3/A4, A1, A5, B1, B2, B3, C4, C3, C2, C1, D2, D4.
  C4 (rounding) goes before anything that adds new reward numbers.

Size key: S = under half a day, M = about a day, L = several days.

---

# Quick fixes

## Q1 - Only the `//wand` axe selects with WorldEdit  (S)

**What exists today**
- `server/plugins/FastAsyncWorldEdit/worldedit-config.yml:97` -> `wand-item: minecraft:wooden_axe`. FAWE/WorldEdit compares only the **item type** of the main-hand
  item (no tag, no NBT) against `wand-item`; so every wooden axe selects (left click = pos1, right click = pos2).
- Our wooden axes: the island axe at tier 0 (`ToolRules.material(kind, 0)` -> `wooden_axe`, `modules/islandtools/.../ToolRules.java:16`), the Depths axe
  tier `oak` (`modules/tools/src/main/resources/tools-defaults.yml:31`, `material: wooden_axe`), and any vanilla wooden axe.
- It only bites people who hold `worldedit.selection.pos` / `worldedit.*`: `server/permissions/base.txt:68` gives `worldedit.*` to the `builder` group (admins/owner inherit). So the
  victims are builders/admins playing normally with their island/Depths axe (every click on a block also sets a selection and cancels the click).

**Root cause:** the wand is identified by item type only, and the type we chose is the starter axe of the whole game.

**Best way (chosen):** change the wand to an item nobody can own: `wand-item: minecraft:warped_fungus_on_a_stick`.
Why: it is not craftable without warped fungus (the Nether is closed), not in `sell.yml`, not a tool/weapon anywhere in our modules (grep of
`modules/` for it finds nothing), right-click does nothing outside riding a strider, and `//wand` keeps giving it (FAWE gives whatever `wand-item` says).
Alternatives rejected: (a) a plugin-side listener that cancels FAWE selection unless the item carries our tag - FAWE's `WorldEditListener`
handles `PlayerInteractEvent` for `LEFT_CLICK_BLOCK/RIGHT_CLICK_BLOCK` and cancels it itself when it handled the click; to veto it we would have to cancel the same
event at `LOWEST` (which also stops stripping logs, tilling and block breaking with the left click for every wooden axe) or rely on `setUseItemInHand(DENY)` being
honoured by FAWE, which cannot be verified without the FAWE jar (not in the repo). (b) a unique `custom_model_data` wand: FAWE does not read it.

**Exact change**
1. `server/plugins/FastAsyncWorldEdit/worldedit-config.yml:97`: `wand-item: minecraft:warped_fungus_on_a_stick`. (FAWE reads the file at start; deploy + restart, or `//reload`.)
2. `server/CONFIG_NOTES.md`: one line under the FAWE table: wand = warped fungus on a stick (reason: island/Depths axes are wooden axes).
3. Optional guard (no code needed): `sell.yml` / shop must never list `warped_fungus_on_a_stick` (add a line to the existing sell-list test only if such a test exists; none needed otherwise).
4. Staff handout: builders who still hold an old `//wand` axe run `//wand` again (message in the TEST_GUIDE / staff notice). No migration, no DB.
5. Tests: none (config only). Manual: builder with island axe breaks/strips a log with no selection message; `//wand` gives the fungus stick; left/right click selects.

**Edge cases:** Bedrock builders cannot see the item name difference (fine); `//tool`/brush items unaffected; players who own a warped fungus on a stick by chance (admin gift) only select if they have WE permission.
**Default (owner can change):** the warped fungus stick. If the owner insists on an axe look, the fallback is a spike (verify FAWE event priority on the test server, owner permission needed).

## Q2 - Remove the "[name] is AFK" chat broadcast  (S, mostly done)

**What exists today:** commit `21e11399` already set `SocialConfig.afkBroadcast = false` (`modules/social/.../SocialConfig.java:37-38`) and flipped the tracked
`server/plugins/PhoenixSkyblock/social.yml`. `SocialModule.announceAfk` (`SocialModule.java:684`) then only tells the player themself (`afk.you-afk` / `afk.you-back`). Because tracked configs are
deployed, the live file flips on the next release.

**Remaining work**
1. Config migration for servers that do not take the tracked file: `SocialConfig.version()` -> 2 and `valueChanges()` = `Map.of(2, Map.of("afk.broadcast", ValueChange.of(true, false)))` (untouched `true` flips, an owner-chosen value stays).
2. The `/settings` toggle `afk-broadcast` (`SocialModule.java:109, 157`, label "AFK announcements") is now a dead switch: do not register it while `config.afkBroadcast == false`
   (UI rule: hide placeholder tiles that do nothing). Keep `SETTING_AFK_BROADCAST` reading code for the on-case.
3. The tab list already shows `[AFK]` (`chat.yml format.afk-part`): nothing to do.
4. Test: `SocialConfigTest` - default false, `valueChanges` for version 2 contains the key; message-key test for `afk.you-afk`.
**Edge cases:** `/afk` command still works and still tells the player; the AFK pool (afk module) is unaffected.

## Q3 - `/is create`  (S)

**What exists today:** `/is` with no island opens the "Create your island" confirm menu (`IslandManager.openOrCreate`, `IslandManager.java:967`; `create(Player)` at `:1006`
builds, `createNow` at `:1037`). With an island, `/is` opens the island menu. There is no `create` sub-command (`IslandsModule.java:571`).

**Exact change**
- `IslandsModule.registerCommands`: add `.sub(CommandSpec.sub("create").description("Create your island (if you have none yet)").permission(PERM_USE).playerOnly().cooldown(Duration.ofSeconds(2)).example("is create").executes(ctx -> manager.createCommand(ctx.player())))`. No arguments, so nothing to complete; it still shows in `/is <tab>`.
- `IslandManager.createCommand(Player)`: if `islandOfPlayer(key(player))` is present -> `msg.send(player, "create.already-have")` + sound `error`, **do not** teleport and do not open anything (the issue says "explain");
  otherwise identical to `openOrCreate` (tutorial gate, `building` guard, `worldLoaded`, confirm menu - a confirm screen stays, it is an irreversible-ish action and Bedrock needs it).
- Message `create.already-have` (islands.yml + server copy): `"<prefix><c:error><sym:cross></c:error> <c:text>You already have an island. <click:run_command:'/is'><c:action>[Open it]</c:action></click> <click:run_command:'/is home'><c:action>[Go home]</c:action></click><newline><c:muted>To start over, use /is disband first."` Bedrock: the commands are written out in text (same pattern as other messages).
- Help: add `/is create` to the islands topic in `modules/menus/src/main/resources/help.yml`. Docs: row in `docs/commands/islands.md`.
- No new permission (uses `skyblock.island`). Audit: the existing `island_create` row.
- Tests: a message-key test (`create.already-have` exists), `IslandManager` logic is an existing-state branch; add a unit test for the pure decision if you extract it (`CreateDecision.of(hasIsland, building, worldLoaded)`).
**Edge cases:** blocked joiners (`create.blocked`) still handled in `create`; double click while building -> `create.busy`; coop-only player has no island -> allowed.

## Q4 - `/keys`  (S)

**What exists today:** crate keys are per profile in `crate_keys (profile_key, crate_id, bound_amount, tradeable_amount)` (`CrateStore.keys`, `modules/crates/.../CrateStore.java:61`). `CratesModule` caches
`ProfileView` (`CratesModule.java:97`, `view(profile)` / `loadView(profile)` at ~`:390`). The crates menu shows keys on slot 4 only. The only command is `/crates` (`CratesModule.registerCommand`, `:365`).
Key **items** (withdrawn tradeable keys, `KeyItems`) are not counted in the table.

**Exact change**
- `CratesModule.registerCommand`: second command `keys` (alias `key`), `.permission(PERM_USE).playerOnly().category("Rewards")`, no arguments (optionally `/keys <crate>` -> same as `/crates <crate>`, with `Arg.word("crate", ...)` reusing the existing `crate` arg and `Suggest.of(this::visibleIds)`).
- Handler: `problem(player)` check like `open()`; `ProfileView v = view(profile)` or `loadView(profile)` then print on the main thread:
  header `keys.header` "Your keys", one line per visible crate that has keys: `<keyMini(crate)> x<total> <muted>(<tradeable> tradeable)` with a clickable `[Open]` -> `/crates <crate>`, then if none `keys.none` ("You have no keys. Get them from /vote, /daily, quests or the AFK pool."), and a footer line with `[Crates]` -> `/crates`.
  Add the number of withdrawn key items in the inventory only if cheap (`KeyItems.count(player)`; otherwise skip - state it).
- Messages `keys.*` in `crates.yml` + server copy. Permission: existing `skyblock.crates.use`. Docs `docs/commands/crates.md`. Help topic "Crates" mentions `/keys`.
- Menu path: the crates menu "Your keys" item (slot 4) lore gets "Chat list: /keys".
- Tests: `CratesMessagesTest` key list; a pure `KeyLines.of(view, visibleIds)` formatter test (ordering = catalog order, hidden crates skipped, zero skipped).
**Edge cases:** profile switching (`problem` -> `switching`), frozen/staff mode follow `/crates`; hidden crates (Eclipse/Conqueror) never listed; numbers formatted with `Msg.number`.

## Q5 - Right-click obsidian with an empty bucket -> lava bucket  (M)

**What exists today:** nothing handles it. `IslandProtection` (`modules/islands/.../IslandProtection.java:308-315`) guards `PlayerBucketEmptyEvent` (PLACE) and `PlayerBucketFillEvent` (BREAK) only; vanilla does not
fire a fill event on obsidian. `IslandProtection.allows(Player, Location, IslandPerm)` (`:135`) is the central role check (package-private). Obsidian is sold for $6 (`sell.yml:232`) and bought for $60 (`shop.yml:120`); a lava bucket costs $200 (`shop.yml:346`).

**Exact change** (module `islands`, new class `ObsidianBucket implements Listener`, registered in `IslandsModule` next to `IslandProtection`):
- `PlayerInteractEvent`, `EventPriority.HIGH`, `ignoreCancelled = false` is wrong - use `ignoreCancelled = true`, `RIGHT_CLICK_BLOCK`, `getHand() == HAND`, item type exactly `BUCKET` with **no** custom item key (skip any stack that has our `ItemKeys` PDC), block type exactly `OBSIDIAN` (not crying obsidian, not respawn anchors).
- Guards in order: `config.obsidianBucket.enabled`; player in the islands world (`IslandService.isIslandWorld`) - so never spawn, Depths, warzone, outposts; `islandAt(block)` present; gamemode survival/adventure not spectator;
  not frozen / not in staff mode (reuse the existing flags helper used by crates `flag(player, "frozen")`, or `StaffModeBypass`); cooldown per player in a `HashMap<UUID, Long>`;
  hourly cap (`max-per-hour`, 0 = unlimited); tutorial gate not needed.
- Permission/ownership: build a `BlockBreakEvent(block, player)` with `setDropItems(false)`, `callEvent()`; if cancelled -> `msg actionBar obsidian.denied` (`IslandProtection.onBreak` at LOW enforces the island role `BREAK`, bans, locked island, staff bypass, border). That gives "own or allowed island only" without duplicating rules.
- If allowed: `block.setType(AIR)`; `inventory.setItemInMainHand(new ItemStack(LAVA_BUCKET))` (a bucket never stacks, so the single item is replaced; assert amount 1, else decrement + `addItem` and overflow to `/box` via `ItemBox.give`); cancel the interact; sound `ITEM_BUCKET_FILL_LAVA`, particle `LAVA` at the block, actionBar `obsidian.done`.
- Placed-block memory: the island-level/milestone code must not count this as "mined"; mark the position as player-modified through the same `PlacedBlocks` API the islandtools listener uses (`placed.forget/remember` in `ToolListener.onBreak`) - verify the API and call it; otherwise the generated obsidian would count as a natural mining action.
- Config (`islands.yml`, `ObsidianBucketSettings` section): `obsidian-bucket.enabled: true`, `obsidian-bucket.cooldown-seconds: 2`, `obsidian-bucket.max-per-hour: 120`, `obsidian-bucket.sound: true`. `IslandsConfig.version()` bump (new keys are added automatically, no valueChange needed).
- Messages: `obsidian.done`, `obsidian.denied` (explains "you need Break permission on this island"), `obsidian.cooldown`, `obsidian.limit`.
- Permission: `skyblock.island.obsidianbucket` (default `true`, in `modules/islands/.../permissions/islands.yml`) so it can be revoked; no grants file change.
- Audit: category ISLAND, event `obsidian_bucket` (player, island id, location); no money. Do not log every use at `INFO`; audit rows only (one line each).
- Command/tab: none (an interaction). Help text in the islands topic: "Right-click obsidian with an empty bucket to turn it back into lava."
- Tests: `ObsidianBucketRules` (pure): cooldown and hourly-window logic; manual: own island yes, other island/coop without BREAK no, spawn no, creative no.

**Edge cases / exploits:** infinite obsidian loop (lava source + water -> obsidian -> bucket -> repeat) yields $6 per cycle at the sell price - negligible against the economy (about $4k/h of manual clicking) but the 2 s cooldown + 120/h cap bounds it;
nether-portal frames on islands lose their obsidian (acceptable, it is the owner's block); never works through `/is bypass` rules differently (staff already pass `BlockBreakEvent`); water-logged/locked islands follow protection; do not allow in the `outposts` world (it is not the islands world) - revisit when outposts are built.
**Default (owner can change):** cooldown 2 s, 120 per hour, requires the `BREAK` island permission.

## Q6 - Disband + new island: the starter chest must fully reset  (M, needs a reproduction first)

**What exists today**
- `IslandBuilder.finish(...)` (`modules/islands/.../IslandBuilder.java:197`) resolves the chest block (schematic metadata offset, `StarterIsland.CHEST` for the placeholder, or `findChest`), then `fillChest(chest, island.owner())` (`:324`) and reports `Build(home, source, chestFilled, chestItems)` (audit row `island_build` field `starter_chest`).
- With the tutorial module on, `fillChest` delegates to `StarterContents` = `modules/tutorial/.../StarterKitContents.fillChest` (`:47`): `StarterSpec.chest` items + Field Guide book into slots, `put()` (`:102`) falls back to the first free slot, then to `/box` overflow; `rememberChest` stores the chest point **only while the tutorial is not complete** (`p.chest == null || !complete`, `:131-143`); the Forge kit is `starter-chest.kit` (now false).
- `IslandClearer` (slot clearing after disband, FAWE or main-thread fallback) removes blocks and non-player entities; a slot returns to `free` only when finished (`SlotState`), so a new island normally takes another slot or the same slot afterwards.
- Tutorial `Progress.chestOpened` is reset only by `StepRules.reset` / step advance (`StepRules.java:61,132`); no module in `tutorial` listens to `IslandRemovedEvent` (grep: only progression, shardblock, economy bank, islands, production do).

**Root cause (honest status):** not provable by reading code alone. Concrete, code-visible defects that can each produce "the chest is not like a fresh one":
1. `fillChest` never clears the target inventory; if the chest block entity of a reused slot still holds items (FAWE paste of a schematic chest without NBT does not necessarily replace an existing tile entity state; the main-thread clearer sets AIR without `Inventory#clear`), new items are added to leftovers and `put()` reroutes to other slots/`/box`.
2. If `chest.getState()` is not yet a `Container` right after the paste (async FAWE flush), `inv == null` and **everything** goes to the overflow `/box` (`put` with `inv == null`), so the chest stays empty while the Build reports `chestFilled = true`.
3. After the tutorial is complete, `p.chest` keeps the **first island's** coordinates forever (by design, `rememberChest` comment) - after a disband these point into cleared air, and `chestGone(player, p)` (`TutorialListener.java:443`) / the chest hologram (`hologramSteps: chest`) read stale data.
4. Starter claims are one-shot per profile (Forge kit `claim`, `packGiven`), so a second island never gets tools/pack again (by design, "no free starter kit"), which players may read as "chest not reset".

**Exact change**
1. Step 0 (before coding, 10 minutes): query the live audit log for `island_build` rows where `starter_chest` is empty or 0 after a `island_disband` by the same player; that tells whether it is #2 (empty) or #1/#3 (items present but wrong). Add the result to this file.
2. `IslandBuilder.fillChest`: resolve the chest again one tick after the paste if `block.getState()` is not a `Container` (retry up to `starter-chest.retry-ticks` times, config `islands.yml starter-chest.fill-retries: 5`, interval 2 ticks); log a WARNING and set `Build.chestFilled=false` if still not a container (never claim success).
3. Before filling: `container.getInventory().clear()` (config `starter-chest.clear-before-fill: true`) so a reused/old chest is exactly the starter content. Applies to both paths (tutorial + plain list). Staff `reset` uses the same path.
4. `StarterKitContents.rememberChest`: always update the remembered point when the **island id changes** (store island id next to the point; `Progress.chest` gets `island` field via `tutorial` store migration `V00x__tutorial_chest_island.sql` - or simply drop the "keep the first island's chest" rule: the point is only used for the chest step and hologram, both irrelevant after completion; the database write remains `setChest`).
5. Reset the per-island tutorial chest state on removal: `TutorialModule` listens to `IslandRemovedEvent` for the owner's profile -> `p.chestOpened = false; p.chest = null` (and clears the chest hologram). Needs the event class from `common` (already published).
6. Config: `starter-chest.kit` stays false; add `starter-chest.give-again-after-disband: true` (Default, owner can change) meaning: the **plain items + Field Guide** are filled for every new island (they already are); tools are not re-given (unchanged owner rule).
7. Tests: `StarterSpecTest` unchanged; new `ChestFillPlan` pure test (given occupied slots -> expected placement with `clear-before-fill`), `StepRules` test that island removal clears `chestOpened`.
**Edge cases:** double fill on `buildAndArrive(created=false)` (crash rebuild) - clear-before-fill makes it idempotent; double chest (schematic with a double chest) -> `getInventory()` is the combined inventory, clear it once; offline owner (staff reset) -> overflow goes to the offline profile box (existing); never drop items on the ground.
**Estimate:** M (1 day incl. reproduction).

## Q7 - Staff one-shot profile reset  (M)

**What exists today:** `/profiles` (alias `/profile`) opens the player's own menu (`ProfilesModule.registerCommand`, `modules/profiles/.../ProfilesModule.java:65`); players can delete an *inactive* profile after typed confirmation (`ProfileServiceImpl.delete`, `:567`): `ProfileDeleteEvent` (modules can refuse) -> soft delete in the store -> `ProfileDeletedEvent`, to which 17 modules react (skills, collection, rewards, economy, crates, islands (solo island removed / member leaves), pass, auction, quests, satchel, tier gates, achievements, boosters, box, staff `ProfileSwitchGuard`, ...). There is no staff entry; `/isadmin reset` resets only an island.

**Exact change** (module `profiles`; reuse the delete pipeline so every module's per-profile cleanup runs):
- Command `/profiles reset <player> [slot]` (sub-command `reset` of the existing `profiles` command), permission `skyblock.profiles.admin.reset` (default `op`; grant to `admin` and `owner` in `server/permissions/profiles.txt`), `Arg.word("player", Suggest known/offline players)`, `Arg.integer("slot", 1..3)` with suggestions = the target's existing slots (default: the target's active slot). Alias `/profile reset`. Staff GUI path: `/inspect <player>` gets a "Reset profile" button (opens a `ConfirmMenu` with details: player, slot, profile name, island, balance) - the staff module adds the button and calls `ProfileService.staffReset(...)` via the interface in `common`.
- Confirmation: players via `ConfirmMenu` (two clicks, Bedrock-safe); console via `/profiles reset <player> <slot> confirm` (same pattern as `/isadmin ... confirm`). A typed-name step is not needed (confirm screen with the profile details is the standard).
- Common API: `ProfileService.staffReset(UUID owner, UUID profileId, Actor actor, String reason): CompletableFuture<Result>`; new event `ProfileStaffResetEvent` (cancellable, `refuse(Component)`) so modules can veto (islands: refuse while the profile is leader of a **team** island with other members, same rule as `onProfileDelete` in `IslandsModule.java:712`; staff first `/isadmin setowner`).
- Flow in `ProfileServiceImpl.staffReset`: (1) target must be **offline** for the first version (online -> staff gets "Kick <player> first (/kick) - resetting an active profile needs a relog"; a button "Kick and reset" is a follow-up). Default (owner can change): offline only, because the active profile has live inventory/xp state and a mid-session swap is the riskiest code in the plugin. (2) fire `ProfileStaffResetEvent`; (3) one DB transaction: soft-delete the profile row (`store.delete`) and create a fresh profile in the **same slot** with the default name, icon reset (`store.create` with that slot; keeps `slots.*` purchases); (4) fire the existing `ProfileDeletedEvent(gone)` so all 17 listeners wipe their data (this is the single source of truth for cleanup - do not add a second wipe list); (5) audit `profile_staff_reset` (category AUTH, actor staff, target, slot, old profile id, new profile id, reason); (6) Discord sink via the existing moderation DiscordSink if present.
- Backup first (STANDARDS: staff-recoverable): before step (3) write `profile_reset_backups` (profile id, owner, snapshot JSON of money/crystals/credits balances, island id, inventory bytes from `StoredState`) in the profiles module migration `V00x__profile_reset_backup.sql`; a staff `/profiles restore` is **not** part of this item (note in the audit that the soft-deleted row and the state blob stay for `retention-days: 30`, config `profiles.yml reset.backup-days`).
- Messages: `reset.confirm` (details), `reset.done`, `reset.online` (kick first), `reset.refused`, `reset.no-profile`, `reset.audit` ; the target sees nothing until next login where the normal "new profile" flow applies (tutorial for that profile restarts through the existing `ProfileDeletedEvent` handler in `tutorial`? verify; if not, call `TutorialService.reset(profile)` in a `ProfileDeletedEvent` listener there).
- Tab completion: player names (all known incl. offline), slots 1-3 of that player, `confirm`.
- Tests: `ProfileResetRulesTest` (online refused, slot defaults to active, unknown slot refused, team-leader veto), store test for the transaction (soft delete + create same slot atomically), and a test that `ProfileDeletedEvent` is fired exactly once.
**Edge cases:** owner resets own account (allowed for staff on themselves? Default: refused unless console - staff must not wipe themselves by a typo); target with credits (bought credits are **account-level** `credits` ledger - not wiped; only per-profile data); pending auction listings -> existing auction `ProfileDeletedEvent` handler returns items to the collection bin (verify it does, else it would lose items); trade open -> target offline so none; two staff at once -> transaction `WHERE deleted = false` guard.

## Q8 - Profile switch cooldown 30 s  (S, done, verify)

**What exists today:** commit `21e11399` set `ProfilesConfig.switchCooldownSeconds = 30` (`modules/profiles/.../ProfilesConfig.java:19-21`), `version()` 3 and `valueChanges()` 3 -> `switch.cooldown-seconds` 10 -> 30 (`:31-40`). `server/plugins/PhoenixSkyblock/profiles.yml` is not tracked, so the migration is what updates the live file.
**Remaining:** confirm `SwitchRules` reads `config.switchCooldownSeconds` and that the bypass `skyblock.cooldown.bypass` still works (test `SwitchRulesTest`); the cooldown message shows the remaining seconds (`switch.cooldown`); `docs/commands/profiles.md` mentions 30 s; CHANGELOG line. No other work.

---

# Phase A - farming / islands

## A1 - Hoe upgrades: till a row, plant a row, autoplant  (L)

**What exists today** (`modules/islandtools`): upgrades are lines `kind:id:effect:max:base-cost:growth` in `islandtools.yml upgrades` (`IslandToolsConfig.java:97-118`, parsed by `Upgrades.parse`, effects enum `Upgrades.Effect` `:20`). The hoe already has `row` (harvest row length via `hoe.row-lengths` `1,3,5,7,9`, `ToolRules.row/rowOffsets`, used in `ToolListener.onBreak` `:146`), `storage`, `fortune`, `extra_drops`, `magnet`, `efficiency`, `haste`, `shard_finder`, `auto_sell`.
Tilling today: vanilla one block; `IslandGrowth.onTill` (`IslandGrowth.java:223`) makes it moist farmland at once (`wet`), `TILLABLE` set `:58`; planted crops join the island clock via `onPlant` (`BlockPlaceEvent`, `:213`). Harvest `p.breakBlock(b)` pattern (`ToolListener.expand3`, `:210`) is how extra blocks respect protection. Replant after harvest: `HoeFarming.replant` (free).
`ProgressionService.upgradeEffectValue` is not involved: tool upgrades are per tool row (`m.effectLevel(rec, Effect)`), bought with shards.

**Exact change**
- `Upgrades.Effect`: add `TILL`, `PLANT`, `AUTOPLANT` (+ `Effect.of` is automatic). Default lines appended to `IslandToolsConfig.upgrades`:
  `hoe:till:till:3:15:2.5`, `hoe:plant:plant:3:20:2.5`, `hoe:autoplant:autoplant:1:150:1` (shards; cost curve like `row`; owner can retune).
- New config: `hoe.till-lengths` (`1,3,5,7`), `hoe.plant-lengths` (`1,3,5`, i.e. "about 3 wide" at level 2), `hoe.autoplant-seeds` (priority list `wheat_seeds,carrot,potato,beetroot_seeds,nether_wart`), `hoe.use-storage-seeds: true`, `hoe.max-per-click: 9`. `IslandToolsConfig.version()` -> 3, `valueChanges()`: `upgrades` `ValueChange.of(UPGRADES_V2 /*the current list*/, newDefault)` (keep the current list as constant `UPGRADES_V2`).
- New class `HoeRows` (listener in `islandtools`, registered next to `ToolListener`): `PlayerInteractEvent`, `HIGH`, `ignoreCancelled = false` (block clicks are not cancelled air clicks; use `RIGHT_CLICK_BLOCK` only), main hand = the player's own valid island hoe (`m.valid(p, hand)`; same copy-id check as `ToolListener.usable`) and island world only:
  - clicked block `TILLABLE` and `TILL > 0`: next tick, for each offset of `ToolRules.rowOffsets(lengthForLevel, yaw)` (extra blocks only, the clicked one is tilled by vanilla), if the block is tillable with air above: create the farmland state, fire a `BlockPlaceEvent` (so `IslandProtection.onPlace` enforces role `PLACE`, bans, borders, and `IslandGrowth.onPlace/placed-memory` see it), cancelled -> revert; then `wet(b)`. If `AUTOPLANT > 0` the freshly tilled blocks (including the clicked one) get seeds in the same pass.
  - clicked block `FARMLAND` (empty above) and `PLANT > 0`: plant a row of `plant-lengths[level]` along the same offsets using the seed type chosen below.
- Seed choice and consumption: (1) the stack in the **off hand** if it is a plantable seed; else (2) first type from `hoe.autoplant-seeds` that the player has in the inventory; else (3) hoe storage (`HoeStorage` holds crops: if it holds the seed item and `use-storage-seeds`, take from it via its existing guarded take-out path). One seed is removed **after** its `BlockPlaceEvent` passed and the block was set, per block; running out stops the row with actionBar `hoe.out-of-seeds`. Nether wart only on soul sand (existing `HoeFarming.replant` rule); stems (melon/pumpkin seeds) only if `hoe.plant-stems: false` default (they need free neighbour blocks; Default off).
- Crop mapping `Seed -> crop block` in a small pure class `Seeds` (wheat_seeds->WHEAT, carrot->CARROTS, potato->POTATOES, beetroot_seeds->BEETROOTS, nether_wart->NETHER_WART). Crops placed are tracked by `IslandGrowth.onPlant` automatically.
- Performance: at most `max-per-click` blocks per click, executed in one tick; per-player click cooldown 5 ticks to stop macro spam (`hoe.row-click-cooldown-ticks: 5`).
- `ToolMenu.effectText` (`ToolMenu.java:141`): add the three effects ("Tills N blocks", "Plants N seeds", "Autoplant"); messages: `upgrade.till/plant/autoplant.name+lore`, `hoe.out-of-seeds`, `hoe.no-room`.
- Permissions/commands: none new (menu-only upgrades, owner D4); help text in `islandtools` topic; `docs/commands/islandtools.md` (effects table).
- Audit: shard purchase is already audited by the upgrade path; row actions are not audited individually (seed use is player-visible); count seeds in a debug stat only.
- Tests: `ToolRulesTest` (till/plant lengths by level, offsets reuse), `SeedsTest` (mapping, priority, soul sand rule), `UpgradesTest` (new lines parse, `autoplant` max 1), `IslandToolsConfig` migration test (v2 list flips to v3, edited list stays).
**Edge cases / exploits:** seed dupes - remove the seed only after the block is set and the place event not cancelled; hoe storage take-out goes through the guarded transaction (do not decrement memory first); tilling onto another player's blocks is stopped by the place event; do not till `dirt_path`/`rooted_dirt` unless vanilla does; crops on the border are cut by `IslandProtection`; Bedrock: right-click on block works (no new input); the sneak+air menu stays untouched; fly/lag: the batch is limited by `max-per-click`.

## A2 - More ore variety in the cobble generators  (S-M, config + one test)

**What exists today:** `modules/production/.../GeneratorSettings.java`: `enabled: false` (master switch, owner enables), `ores` list `:46` (cobble, coal, copper, iron, gold, redstone, lapis, diamond, emerald, custom ashstone `gilded_blackstone`, custom emberite `nether_gold_ore`) and `levels` `:64` (0 pure cobble ... 10: 40% cobble, nine ores). The Generator level comes from the island upgrade `upgrades.yml generator` (10 levels). `GeneratorTable` parses `<id>: block=<block|keep> [tier=] [depths-xp=] [item= rarity=]`. Sell prices for vanilla ore drops: `sell.yml categories.ores` (coal 10 ... diamond 61; emerald off).

**Exact change** (all in `generators.yml` defaults = `GeneratorSettings`):
- New filler stones (no tier, no XP, vanilla drops, sell prices exist in `sell.yml roots`): `andesite`, `diorite`, `granite`, `tuff` (`block=andesite` ...), from level 1 (small shares), plus `deepslate` from level 3 (`block=deepslate`, drops cobbled_deepslate - vanilla behaviour). These give the visible "variety" without touching income.
- New vanilla ores: deepslate variants of coal/copper/iron/gold/redstone/lapis/diamond/emerald (`deepslate_*_ore`, same tier/XP/drops as the stone version, share taken from the stone ore at levels 5+), `nether_quartz_ore` (tier 10 pillar XP, item `quartz`, sell price proposed `quartz: 396`... **price needs the owner/economy check**: add under `categories.ores` at `emberite`'s level only after BAL check; Default: quartz `block=nether_quartz_ore tier=10 depths-xp=40` replacing nothing, share 3% at level 10, sell price 150 (below emberite 396, above diamond)), `amethyst`/`glowstone` are not ores - skip.
- New default `levels` (every row sums to 100, cobble share kept >= 35% at level 10 so the island still makes building stone): e.g. `1: cobble=85 andesite=4 diorite=3 granite=3 coal=5`; `4: cobble=60 andesite=3 diorite=3 granite=3 tuff=3 coal=9 copper=8 iron=7 gold=4`; `10: cobble=36 deepslate=5 coal=5 copper=5 iron=5 gold=5 redstone=5 lapis=5 diamond=6 emerald=5 ashstone=6 emberite=5 quartz=3`. Final numbers are produced by a small script in `tools/` (like `gen_upgrades_catalog.py`) so each level's ore income stays within the BAL2 model (island production ~16% of income) - **do not raise average ore value per roll by more than 5% per level**; a unit test enforces it.
- Migration: `GeneratorSettings.version()` 1 -> 2 with `valueChanges`: `ores` and `levels` `ValueChange.of(OLD_LIST, NEW_LIST)` (flip untouched defaults only). The file is not tracked in `server/plugins`, and the master switch is off, so nothing changes live until the owner enables production.
- Drops of custom ores unchanged. Sell: stone variants already listed in `roots` (andesite/diorite/granite 0.66, tuff 0.66, deepslate 0.79); deepslate ore drops equal the stone ore drops (same sell prices).
- Messages: none (names are vanilla). `GeneratorTable` test: every level parses, weights positive, expected value per roll monotonically increasing by level (cap 5% step), every `block=` is a valid Material.
**Edge cases:** the pillar-XP memory (`memory.per-island`) is unaffected; `lag-guard.max-rolls-per-second` unchanged; fuel "asleep" islands still form plain cobble; Bedrock sees vanilla blocks.
**Default (owner can change):** feature stays behind `enabled: false` (production switch is the owner's call).

## A3 - Spawner mobs: no damage to players; killable as now  (S)

**What exists today:** `MobStacker.prepareSpawnerMob` (`modules/stacking/.../MobStacker.java:412`): `mob.setAware(false)` ("owner S7: no AI"), equipment cleared, slime size 1, no day-burn, piglin no zombification. Players kill stacks with weapons (weapon-based stack kills, F2 efficiency). Nothing else prevents damage that does not need AI: pufferfish/guardian contact and thorns, magma-cube/slime/tropical fish contact (`playerTouch`), a creeper that is ignited (flint and steel, lightning) or an explosion, drowned/guardian beams, fire from blaze death, wither skeleton wither effect from contact, and mobs that regain AI (a restart: `Aware` is not guaranteed to survive chunk unload on every Paper build - verify by killing a stack after restart).

**Exact change**
- New `stacking.yml mobs.spawner-mobs-hurt-players: false` (`StackingConfig`, Default false).
- `MobStacker` (or a small `SpawnerMobSafety` listener) with `priority = HIGHEST`, `ignoreCancelled = true`:
  - `EntityDamageByEntityEvent`: damager (resolve projectile shooter / `TNTPrimed` source / `Creeper`) carries `StackKeys.SPAWNER_MOB` and victim is a `Player` -> cancel. Thorns: `EntityDamageEvent.DamageCause.THORNS` with the guardian damager is covered by the same check.
  - `EntityTargetLivingEntityEvent` (and `EntityTargetEvent`): spawner mob targeting a player -> cancel + `setTarget(null)`.
  - `EntityPotionEffectEvent` cause `ATTACK` from a spawner mob (cave spider poison, wither skeleton wither, pufferfish) -> cancel.
  - `ExplosionPrimeEvent` / `EntityExplodeEvent` with entity `SPAWNER_MOB` -> cancel (a spawner creeper never explodes; players could still kill it with a weapon).
  - `EntityCombustByEntityEvent` (fire aspect irrelevant), `EntityShootBowEvent` by spawner skeleton -> cancel.
- Re-assert on load: `CreatureSpawn`/`EntitiesLoadEvent` for entities with the PDC flag -> `setAware(false)` again (cheap, only flagged mobs).
- **Do they take damage?** Default (owner can change): **yes, killable as now** - killing is the gameplay (stack kills, enchant bonuses, Slaughter, combat XP rules). Add `mobs.spawner-mobs-invulnerable-to-other: ` is NOT added. Optional second switch for the owner: `mobs.spawner-mobs-immune-to-environment: true` (fire/lava/suffocation, no hurt except players' weapons) - Default **false** because lava/fall kills are an accepted farm design (SPAWN-F2 text "grinders", ECONOMY_BALANCE section 4.3).
- Messages: none. Audit: none. Tests: `SpawnerMobSafetyTest` (pure predicate `blocks(damagerIsSpawnerMob, victimIsPlayer, config)`), manual: stand next to a stacked pufferfish/creeper/cave-spider spawner.
**Edge cases:** the player's own thorns/arrow damage to the mob stays allowed; a staff kill uses the normal path; Bedrock identical.

## A4 - Mob drop prices, gold nugget  (S, config only)

**What exists today**
- Spawner mobs with a line in `stacking.yml spawners.drops` drop **only** a custom item `mobdrop_<mob>` (`StackingConfig.java:140-157`, materials `clay_ball`, `flint`, `iron_nugget`, `gold_nugget`, `echo_shard`); vanilla loot is replaced. Their prices are in `sell.yml custom-items.spawner-drops` (`economy/.../sell.yml:144-165`): tier 1 **$5**, tier 2 **$10**, tier 3 **$16**, tier 4 **$30**, premium **$80** ("placeholders ... equal payback").
- `ECONOMY_BALANCE.md` section 4.3 (table `spawners`) assumes **loot per mob $15 / $27 / $50 / $91 / $270**, fixed spawner price $117k / $213k / $387k / $706k / $2.1M and a 60-hour payback. With the live prices (5/10/16/30/80, about one third) payback is about 3x longer (~180 h for tier 1), i.e. spawners look like a bad deal and the "payback" lore in the shop turns red ("Poor deal").
- Vanilla `gold_nugget` and `iron_nugget` items (from other sources) are not in `sell.yml` and cannot be sold; custom `mobdrop_*` items are priced as `custom:<id>` only ("custom items are NEVER priced as the vanilla item they are"), so players who see "gold nugget" in their bag cannot understand why selling a real nugget fails or why the tier-4 drop is worth only $30.

**Proposal (balanced, round numbers):** align the drop prices with the balance sheet and round them (the model value rounded):
| Tier | Mobs | Today | Model | Proposed | Payback of the 1st spawner |
|---|---|---|---|---|---|
| 1 | pig, cow, chicken, sheep, rabbit | $5 | $15 | **$15** | ~60 h |
| 2 | zombie, skeleton, spider, cave spider, slime | $10 | $27 | **$25** | ~65 h |
| 3 | creeper, witch, drowned, husk, stray | $16 | $50 | **$50** | ~60 h |
| 4 | blaze, enderman, guardian, hoglin, zombified piglin | $30 | $91 | **$90** | ~60 h |
| 5 | iron golem, wither skeleton, evoker | $80 | $270 | **$270** | ~60 h |
Plus: add vanilla `gold_nugget: 2` and `iron_nugget: 2` to `sell.yml` `mob-loot` (rule "no item sells for more than it is crafted/smelted from": nugget <= raw ingot price / 9 -> gold 25/9 = 2.7, iron 18/9 = 2), so a real nugget is sellable and cannot beat the custom item; show in the drop item lore "Sells for $X" (already generic).
**Exact change:** edit the 23 `mobdrop_*` prices in `modules/economy/src/main/resources/sell.yml` (new defaults are merged automatically for new keys, but **changed prices of existing keys are not** - add a `config-version: 3` migration step in the sell file loader if the loader supports value migration; otherwise ship the change through the tracked `server/plugins/PhoenixSkyblock/sell.yml` (it is tracked: it is in the listing), edit both files). Update `ECONOMY_BALANCE.md` table note and `tools/economy_model.py` run output. `SellPricesTest` (existing consistency test): add "spawner drop price x1 spawner output per hour / spawner price = 60-70 h" assertion.
**Risk (state to the owner):** this triples spawner income versus what is live now. The model says it is intended (income meta: spawners ~18%), and the F2 diminishing-output brake is already built; money supply grows. **Default (owner can change): apply the table**; the owner can instead keep current prices and lower the shop prices, or choose half-way values. Because it is an economy change affecting all current spawner owners, release it with a `/broadcast` and a CHANGELOG line.

## A5 - `/is storage` (`/is chest`): upgradeable island storage  (L)

**What exists today:** the upgrade `island_vault` exists in `modules/progression/src/main/resources/upgrades.yml:91` (5 levels at island level 1/3/5/7/9, values 2..6 = rows; level 0 = 1 row) with effect `ISLAND_VAULT("island-vault", false)` in `UpgradeEffect.java:19` - the `false` makes it "Coming soon / can't be bought". No storage exists. `docs/planning/ISLAND_STORAGE.md` holds the proposal with 11 questions still unanswered (needs owner input); `QUEUE.md STORE2` (mastery deliveries, take-out limits, staff recall) waits for it. Patterns to reuse: the crash-safe sell chest, the kick backups (`KickBackupStore`), the leader log (I9), island role matrix (`IslandPerm`, `PermissionsMenu`), hoe storage guarded take-out.

**Exact change (first release = the proposal's "Our proposal", every number configurable):**
- Module `islands` (own sub-package `storage`): `IslandStorage` service, `StorageMenu` (6-row chest GUI, bottom bar per GUI-1), `IslandStorageStore` (DB), `StorageLog`.
- DB migration `modules/islands/src/main/resources/db/islands/V018__island_storage.sql`: `island_storage_items (island_id, slot INT, item MEDIUMBLOB, amount INT, PRIMARY KEY (island_id, slot))` (serialized with `ItemCodec`-style `serializeAsBytes`), `island_storage_log (id, island_id, at, actor BINARY(16), actor_name, action 'put'|'take', item_key, amount, slot)` (7-day retention job, config), `island_storage_limits (island_id, player, day, taken_items)` for the daily take-out limit, and a `version` int per island for optimistic concurrency.
- Shared live view: one `Inventory` per island held in memory while anybody has it open (reference-counted); every click is applied to the in-memory inventory on the main thread (single writer) and a debounced async write (2 s, `storage.save-delay-ticks`) persists the whole snapshot with `WHERE version = ?` (conflict -> reload); on last close and on shutdown a synchronous flush. Crash = at most 2 s lost, never a duplicate because the snapshot is the only truth and take-outs are applied to the in-memory snapshot before the player's inventory is credited (the item exists in exactly one place at any commit point; `PlayerSaves.commitAfterSave` for puts: take from the player's inventory, save the player file, then commit the slot).
- Size: `rows = progression.upgradeEffectValue(island, "island-vault")` with level 0 = 1 row; flip `ISLAND_VAULT` live flag (`UpgradeEffect` second argument true) in the same release; shrinking never happens (levels only go up). Slots beyond the owned rows are filler panes.
- Roles: new `IslandPerm.STORAGE_PUT` and `STORAGE_TAKE` (`ownDefaults=true, management=true`; defaults: put = Leader/Admin/Mod/Member, take = Leader/Admin/Mod). Coop and visitors never. They appear in `/is permissions`.
- Daily take-out limit per role (`islands.yml storage.take-limit-per-day: member 64, mod 512, admin/leader 0 = unlimited`), counted in items; resets at the server day boundary (`IslandClock`). Forge/mastery deliveries count toward it (R3-23) - hook via a small `StorageAccess` interface in `common/island` for STORE2 (not part of this item).
- Allowed items: stackable normal items and Depths materials; refused: unique/bound/tagged items (tools, books, set tokens, keys, money notes - anything with `ItemKeys` unique tag or `BoundItems`), filled shulkers and bundles; reasons shown in the click error (`storage.refused-*`).
- Blocked while frozen, in combat, in staff mode, or when the island is being cleared/disbanded.
- Disband/reset: contents archived to the existing reset-backup table pattern (`V014__reset_backups_bank.sql`) and deleted; kick: nothing (the kicked member loses access).
- Commands: `/is storage` (alias sub `chest`): opens the GUI (island member with `STORAGE_PUT` or `STORAGE_TAKE`), `/is storage log` (leader/admin view of the last 7 days with paging), `/is storage sort` (the single Sort button from Q5/ST declined list: the owner earlier agreed one Sort button; Default: include Sort as button + `/is storage sort`). Tab: sub-commands only. Island menu gets a "Storage" chest button (visible with level >= 0, locked lore when not allowed). Help topic added.
- Permission nodes: `skyblock.island.storage` (default true, gating the command; the real access is the island role), staff `skyblock.island.admin.storage` (view/recall; helper+), `skyblock.staff.island.storage.recall` (admin). Staff GUI: `/isadmin storage <player>` read-only view + `/isadmin storage recall <player> <days>` (STORE2, listed as follow-up unless time allows).
- Audit: every put/take summarized per close (item key + amount) as `island_storage_put/take`; leader log shows it.
- Tests: `StorageRules` (allowed items, limit math, rows from level), `StorageSnapshot` codec round trip, concurrency test with two simulated viewers (put then take: totals conserved), migration test.
**Edge cases / exploits:** shift-click/hotbar swap/number-key/drag/double-click collect in a custom GUI (cancel all except explicit slots and handle `InventoryClickEvent` + `InventoryDragEvent` per slot, as the sell chest does), two members taking the same stack, island deleted while open (close all viewers first), chunk unload irrelevant (database), crash between put commit and player save (take first, commit after save). 
**Default (owner can change):** the whole proposal in `ISLAND_STORAGE.md` with its "Suggested" answers; unanswered questions are listed in Owner questions.

---

# Phase B - Depths / dungeons

## B1 - Combat-dungeon mobs must attack players and spawn on the ground  (M-L)

**What exists today** (`modules/depths/.../combat`): `CombatZones` spawns tier mobs per zone (`mobs.yml combat-zones`, `ZoneSpawns`, `SpawnMath.target/toSpawn`), chooses the spot with `spot(...)` (`CombatZones.java:530`) from `standY(world, x, z, area.maxY(), area.minY())` (`:562`, cached by `SpawnColumns`) and configures the entity in `MobFactory.configure` (`MobFactory.java:109`: persistent false, no despawn, attributes SCALE/MOVEMENT_SPEED/ATTACK_DAMAGE/MAX_HEALTH/FOLLOW_RANGE only when `> 0`, `setGlowing`; nothing touches `setAware`/`setAI`). Damage to players is set in `CombatZones.onDamage` (`:962`; cancels hits on non-players, applies attack-cooldown, `event.setDamage(def.damage * mult)`). Targeting is vanilla; `onTarget` (`:1036`) only cancels non-player targets and a returning boss. `RegionProtection` (worlds module) cancels damage and targeting only where the flags `pve-taken` / `mob-target` apply (arrival/exit paths `safe_no_pve`).
Searches for `setAware(false)` find only the **spawner** mobs of the stacking module (`MobStacker.java:415`), not the Depths combat mobs. The Depths world difficulty is `normal` (`worlds.yml:86`), so peaceful is not the cause.

**Root cause analysis (ranked; step 1 of the work is to confirm on the test server with the owner's permission to run it):**
1. **Spawn height (confirmed in code, explains both symptoms):** `standY` scans from `arena.maxY()` **down** and returns the **first** (highest) position with a solid block below and two passable blocks. Anything solid counts as ground: the top of walls, pillars, roofs, tree canopies (leaves), the top of the cave/arena ceiling, a bedrock cap, slabs, glass. With an arena box taller than the walkable floor the highest standing spot is **on top of the structure**, unreachable for pathfinding. Mobs there never reach the player (looks like "do not attack"), and `combat.leash` only checks the zone region, not reachability.
2. **No defensive AI setup:** `configure` never calls `mob.setAware(true)`/`setAI(true)`; if anything (the `MobStacker` spawn handler, a plugin, or a copied spawner entity) changed it, the mob stays dumb. `NO_STACK_TAG` is set before the spawn event, so the stacker should skip it, but this is not asserted.
3. **Melee gate:** `onDamage` cancels a hit when `abilities.meleeAllowed` is false; mobs with `attack-cooldown` also depend on `Bukkit.getCurrentTick()`; verify a mob with no cooldown set is always allowed (`MobAbilities.java:235` returns true for cooldown <= 0 - fine).
4. **Region flags:** `mob-target`/`pve-taken` regions defined in the builds (`worlds.yml:195-208`) overlapping the arena would silently cancel targeting/damage; check `depths_combat_*` regions for stray flags.
5. **Targeting range:** `FOLLOW_RANGE` is only touched when mobs.yml sets it; vanilla zombie 35, but skeleton/witch need line of sight; if the spawn is on a roof there is none.

**Exact change**
1. **Ground-only spawn spots** (`CombatZones.standY` + new pure class `SpawnGround`):
   - Scan range limited to the zone's walking floor: new per-zone data `ground-y` (and `ground-height`) read from the build metadata if present (`BuildPoints` category `spawns`/zone metadata), else derived: `floorY = arena.minY() + config.combatFloorOffset` and only `[floorY, floorY + combat.spawn-max-above-floor]` (config `depths.yml combat.spawn-max-above-floor: 4`) is scanned, starting at the floor and going **up** (lowest standable spot first), so ceilings, rooftops and canopies are never candidates.
   - The block below must be "ground": `isSolid()` and not in `combat.spawn-bad-floors` (default list: `*_leaves`, `*_slab`, `*_stairs`, `glass*`, `ice`, `*_trapdoor`, `cobweb`, `scaffolding`, `barrier`), the two blocks above passable and **not liquid**, and the column above must have `>= 2` free blocks (already).
   - Reachability filter (cheap, cached with `SpawnColumns`): the spot must be within `combat.spawn-max-path-check` (default 24) horizontal blocks of a block in the same connected floor as the zone's arrival marker; implement with the existing pathfinder only once per cached column using `world.getBlockAt(...)` flood limited to `combat.spawn-reach-budget: 400` nodes, or (simpler, default) **require at least `combat.spawn-min-open-neighbours: 3` of the 8 horizontal neighbours at the same height to be walkable** (stops pillar-tops and ledges). Config: `combat.spawn-reachability: neighbours|off`.
   - `SpawnColumns` cache key includes the new config hash; `/depths reload` clears it.
   - Debug command `/depths spawntest <zone> [count]` (staff `skyblock.depths.admin`, tab: zones) shows particles at accepted/rejected columns for 10 s - lets builders verify without a mob wave; subcommand + tab completion + docs.
2. **Aggro guarantee** (`MobFactory.configure` + `CombatZones` tick): `mob.setAware(true)`, `mob.setAI(true)`, `mob.setTarget(null)` clean; every `combat.retarget-ticks` (default 40) for tracked mobs in a zone with players present and no valid target: pick the nearest valid player (not in a `mob-target`-denied region, not vanished/staff mode/AFK-pool, within `combat.aggro-range` (default 24), same zone) and `mob.setTarget(player)`; budgeted (max `combat.retarget-per-tick` 20 mobs per tick, round-robin). This also fixes ranged mobs whose vanilla targeting fails on odd geometry. Config `combat.retarget-enabled: true`.
3. **Make melee reach real:** after the target is set, if `mob.getPathfinder().findPath(target)` is null for two consecutive retarget cycles, teleport the mob to `spot()` near the target's floor within `combat.spawn-ring` (existing ring min/max) once, count in a debug stat (`depths-combat-stuck`) and log at FINE. This handles mobs that spawned fine but got walled off.
4. **Diagnosis tool:** `/depths debug mob` (look at / nearest mob, staff): prints aware, hasAI, target, path exists, region flags at mob and player, zone, attack cooldown state, damage attribute. Part of `DepthsDebug`.
5. Config keys (all in `depths.yml`, section `combat.`): `spawn-max-above-floor`, `spawn-bad-floors`, `spawn-reachability`, `spawn-min-open-neighbours`, `retarget-enabled`, `retarget-ticks`, `retarget-per-tick`, `aggro-range`. `DepthsConfig` gets `version()+1` (new keys only, no value changes).
6. Messages: `debug.mob.*` lines; no player-facing text. Permissions: reuse `skyblock.depths.admin` (verify the node name in `permissions/depths.yml`).
7. Tests: `SpawnGroundTest` (a fake `FloorProbe` interface: pillar top rejected, leaves rejected, floor accepted, neighbours rule, max-above-floor cut), `RetargetRulesTest` (nearest valid player, flags excluded), `SpawnMathTest` unchanged. Manual: tier 1-3 graveyard/crypt/den: wait for a wave, no mob on roofs; stand still: mobs walk up and hit; arrival safe path: mobs do not aggro.
**Edge cases:** safe zones keep `mob-target` denied (retarget must check `RegionFlags.denied(MOB_TARGET)`); mini-boss leash unchanged; ambient mobs are separate (`AmbientMobs`, harmless by design); spawn in lava/water arenas (tier 5 drowned) - water spots are allowed for drowned only (`MobDef` type check: `EntityType.DROWNED` may stand in water, others may not); Visibility service: hidden players are not valid targets.
**Estimate:** M-L; the spawn fix alone is S, the retarget layer M.

## B2 - Mine blocks with particles that give haste / a boost  (M)

**What exists today:** Depths mining is the per-player block engine (`DepthsEngine.onBreak` `engine/DepthsEngine.java:386`): the real block never changes, `ResourceBreakEvent` (fired per break, with player, region, tier, tool) is the extension hook; per-player depletion and `queueResend`; regions list resource blocks. No boost blocks exist. Haste for tools comes from the island tool haste upgrade and potion effects; boosts can also be bonus-family percents via `BonusService` (`BonusFamily.SPEED`, `SKILL_XP`, `EXTRA_DROPS`).

**Design (new feature "Glowing ore"):** a rare **boost node** appears on a resource block of the player's own view; only that player sees a particle column (per-player, `Player#spawnParticle`, so nobody else's fps or screen is polluted). Breaking it grants a short boost.
- Class `BoostNodes` in `modules/depths/.../engine` (listens to `ResourceBreakEvent` at `MONITOR` for the effect; a 1 Hz particle task per player in a mine/forest region):
  - Spawn rule: per player and per region, a node is chosen every `boost.nodes.interval-seconds` (default 90) among **currently solid (not depleted)** resource blocks within `boost.nodes.range` (default 14) of the player and in line-of-sight-ish (`player.hasLineOfSight`), at most `boost.nodes.max-active` (default 2) per player; stored in memory (`Map<UUID, Set<Long blockKey>>`, keys via `BlockKeys.block`), lost at restart (harmless).
  - Visual: `Particle.END_ROD`/`HAPPY_VILLAGER` rings above the block every 10 ticks (config `boost.nodes.particle`, `boost.nodes.particles-per-tick`), only to that player; Bedrock gets the same particles (Geyser maps most; fall back to `GLOW`); sound hint once on spawn (`UI_*` via `core.sounds()` id `boost-node`).
  - On break (`ResourceBreakEvent`, not cancelled): remove the node; roll the boost from a weighted table `boost.effects` (config list, e.g. `haste:2:30` = Haste II 30 s weight 50, `speed:1:30` weight 20, `skill_xp:25:60` weight 20 (a personal BonusService part for 60 s, **not** stackable with the booster item: separate source id `depths_boost` capped by the family cap), `extra_drops:20:30` weight 10). Haste/speed are plain `PotionEffect` (ambient, particles hidden) refreshed to the longer duration only; bonuses use a small `BoostBonusSource` registered like `BoosterBonusSource` with per-player expiry timestamps.
  - Guards: tier locked/cancelled breaks give nothing; AFK pool/staff mode excluded; per-hour cap `boost.nodes.max-per-hour` (default 40) against macros; node must be broken with the proper tool (the break event already ensured).
- Config: `depths.yml boost.*` (all above, default `enabled: false` until owner approves numbers? -> **Default (owner can change): enabled: true with haste-only table in the first release** to avoid touching income: effects `haste:2:30` (70), `speed:1:30` (30)). XP/extra-drop effects exist in the table but are weight 0 by default (the economy owner decides).
- Messages: `boost.found` ("A glowing block appeared nearby!"), `boost.gained`, `boost.expired` (actionBar). Permissions: none (everyone); staff toggle `/depths boost <on|off|spawn <player>>` for testing (`skyblock.depths.admin`, tab: on/off/spawn + player). Docs: `docs/commands/depths.md`.
- Audit: none for haste/speed; XP/drop boosts log `depths_boost` (player, effect) because they touch the economy.
- Tests: `BoostTableTest` (weighted pick, parse, invalid lines skipped), `BoostNodeRulesTest` (max-active, interval, hourly cap), sources add through the family cap.
**Edge cases:** the break is cancelled by the engine (`event.setCancelled(true)` - the real block never changes), so use the custom event only; per-player visibility means no shared state; glowing ore must not reveal rare blocks to cheaters (the chosen block is already visible to the player); particle spam (limit per tick and per player); players who stand still do not get nodes (require movement within the last 30 s).

## B3 - Hidden treasures for new players (money, keys, title for all)  (L)

**What exists today:** the builds already carry `treasure_spots` (e.g. `server/builds/depths_gate_v1.yml:39-43` four spots with hints "forge: behind the cauldron", "sell stall: behind the ore crates", ...; 9 build files contain the key). `BuildPointsStore` stores the category and `BuildPoints.points("treasure_spots")` serves it (`common/builds/BuildPoints.java`); the BACKLOG lists "treasure spots" as stored but unused. Rewards exist as services: `EconomyService` (money), `CrateKeys.giveKeys(profileKey, crateId, amount, reason, actor)`, `Cosmetics.grantTitle`, `ItemBox`. The collection log (`modules/collection`) and achievements module exist for "found X of Y".

**Design:** a small new module `modules/treasures` (copy an existing module's `build.gradle.kts`, `settings.gradle.kts` include, `plugin/build.gradle.kts` dependency, register in `SkyblockPlugin#registerModules`).
- Data: spots come from `BuildPoints.points("treasure_spots")` (id, x, y, z, hint, build), so builders control placement; extra spots can be added in `treasures.yml` (`spots: ["id: world x y z"]`) without a build. Per spot a **reward profile** in config: `rewards.default` plus overrides: `money: 2000` (scaled by the finder's tier: `money-minutes` of income like daily quests, Default 3 minutes via the same `QuestMath.money` rule), `key: ember x1` for most, a rarer `cinder` key at `chance`, and **one title for the full set** (`finish-title: treasure_hunter`, every player who finds all spots gets it, "title for all" reading: everyone can earn it, not a one-time exclusive).
- Discovery: a hidden interaction target is needed. Default: **right-click the block at the spot** (the build places a barrel/decorated pot/skull there) with a short hold: `PlayerInteractEvent` on the exact block; the plugin does not place blocks by itself (build owns it). Players see only the hint via the existing `/guide`/Pip text and a "Treasure map" entry in the collection/`/menu` > Explore; no waypoint particles (the point is hiding). Optional proximity hint: actionBar "You feel something nearby" at <= `hint-radius` (default 6) once per spot per session (config, can be off).
- Claim (atomic, dupe-proof): table `treasure_found (profile_key BINARY(16), spot_id VARCHAR(64), found_at BIGINT, PRIMARY KEY (profile_key, spot_id))` via migration `V001__treasures.sql`; claim = `INSERT` inside the same transaction that records the reward intent; rewards paid after the row exists with idempotency keys `treasure:<profile>:<spot>` (money), key via `CrateKeys.giveKeys(... reason "treasure")` (already idempotent per call? use `reason` + audit; if the call can double on crash, record `reward_state` and a recovery pass at start like the auction), title via `Cosmetics.grantTitle`. Per **profile** (progress per profile like daily rewards), not per account, so alts cannot farm: reward money is small and the new-player condition below limits abuse.
- New players: spots are active for profiles with `playtime < treasures.new-player-hours` (default 20 h) **or** always for the title/first-find; Default (owner can change): spots stay findable forever but the money/key reward only pays while the profile is "new" (below the playtime threshold or before Depths tier 4); later finds only mark the spot (so the set title is still reachable).
- Commands: `/treasures` (alias `/hunt`) opens a GUI "Treasure hunt": found count, hint list for **unfound** spots (hint text from the build, not coordinates), reward preview; `/treasures reset <player> [spot]` (staff `skyblock.treasures.admin`, tab: player, spot ids) for testing; permissions in `modules/treasures/.../permissions/treasures.yml` (`skyblock.treasures.use` default true, `skyblock.treasures.admin` op, grants in `server/permissions/treasures.txt`). Listed in `/menu` Explore and `/help`.
- Messages in `treasures.yml` (found, already-found, hint, set-complete title/chat), sound id `treasure-found`, particle burst at the spot for the finder only.
- Audit: category ITEM/ECONOMY `treasure_found` (profile, spot, rewards). Announce the set completion to chat (`treasures.announce-complete: true`, once per player).
- Tests: `TreasureRulesTest` (new-player gate, set completion), `TreasureStoreTest` (claim once, concurrent claim -> one winner), config parse test for rewards.
**Edge cases:** two players clicking the same spot is fine (per profile); player in tutorial can find (it is a discovery reward; set the gate `TutorialGate` off for this); spots in worlds not loaded are skipped with a warning; builds re-applied move spots (ids stay stable, positions refresh via `BuildPoints.revision()`); hologram/NPC hint texts must not reveal coordinates.
**Default (owner can change):** money + Ember key per spot, Cinder key rare (5%), title "Treasure Hunter" for completing the set, rewards only for new players, per-profile.

---

# Phase C - economy / auction

## C1 - Auction house bidding (`/ah bid`)  (L)

**What exists today** (`modules/auction`): fixed-price listings only. Table `ah_listings` (`V001__auction.sql`) with `status` pending -> active -> buying -> sold, active -> cancelled/expired; `AuctionEngine.create` (`:201`), `buy`/`claimAndSettle`/`settle` (`:281-400`: buyer withdraw `ah_buy:<id>`, seller deposit `ah_sale:<id>`, conditional DB updates, crash recovery resolves `pending`/`buying`), `expireDue()` (`:458`), collection bin `ah_collection` (items waiting; `uq_ah_collection_listing`), `Pricing` (fee/tax), `ListingRules.price` (min/max), own-profile block (`OwnProfileCheck`), `SendGate` (F1 self-earned $10k rule for buying), `TxReason.AH_BUY/AH_SALE/AH_FEE` (strings in `common/economy/TxReason.java:17-19`), `Events.listed/sold/expired`, menus `AuctionMenu`, `SellMenu`, `MyListingsMenu`, `StaffListingMenu`. Command `/ah` + `sell` (`AuctionModule.java:227-241`).

**Design: auctions are a second listing mode** (`mode = BUY_NOW | AUCTION`), settled by the same engine.
- Migration `V004__auction_bids.sql`: `ALTER TABLE ah_listings ADD COLUMN mode VARCHAR(8) NOT NULL DEFAULT 'buynow', ADD COLUMN start_price BIGINT NULL, ADD COLUMN current_bid BIGINT NULL, ADD COLUMN bidder BINARY(16) NULL, ADD COLUMN bidder_name VARCHAR(32) NULL, ADD COLUMN bid_count INT NOT NULL DEFAULT 0, ADD COLUMN bid_seq INT NOT NULL DEFAULT 0, ADD COLUMN buy_now_price BIGINT NULL`; new table `ah_bids (id BIGINT AUTO_INCREMENT, listing_id BIGINT, seq INT, bidder BINARY(16), bidder_name, amount BIGINT, state VARCHAR(10) /* pending|leading|outbid|won|refunded|lost */, refunded BOOLEAN DEFAULT FALSE, created_at BIGINT, UNIQUE (listing_id, seq))`. For an auction `price` keeps the **start price** (so the fee/tax and existing indexes work); `current_bid` is the leader (null = no bid).
- Money model (escrow, atomic): a bid **withdraws the bidder's full bid immediately** (`Transaction.withdraw(player, MONEY, amount, TxReason.AH_BID)` key `ah_bid:<listing>:<seq>`), so a bidder can never lose a bid they cannot pay. Outbid players are refunded in full (`Transaction.deposit ... TxReason.AH_BID_REFUND`, key `ah_bid_refund:<listing>:<seq>`). Add both reasons to `TxReason` and the economy reasons registry (staff "Reasons" screen). The seller is paid only at the end (net of tax) with the existing `ah_sale:<id>` key.
- Bid algorithm in `AuctionEngine.bid(listingId, bidder, name, amount, actor, gate)` (single `inFlight` guard per listing, like `buy`):
  1. Checks: listing `ACTIVE` auction and `expires_at > now`; not the seller; not the current leader raising against themself unless raising >= min increment (Default: allowed, refunds the old own bid atomically as a top-up: simplest = treat as a new higher bid, old one refunded); `ownProfile.blocked(seller, bidder)`; `SendGate` check as for `buy` (F1: bidding spends money = counts as a payment); frozen/blocked trading (`SanctionService.Use.AUCTION_BUY`); amount >= `max(start_price, current_bid + minIncrement)` where `minIncrement = max(ah.bid.min-increment (Default $1), ceil(current_bid * ah.bid.min-increment-percent / 100))` (Default 5%); amount <= `ah.bid.max` and <= 10x current (config cap against typos, Default `ah.bid.max-multiple: 20` of start price for the first bid).
  2. Insert `ah_bids` row `pending` (seq = `bid_seq + 1`) - the recovery anchor.
  3. Withdraw the new amount (idempotent key). Not enough funds -> mark `lost`, tell the player.
  4. Conditional update: `UPDATE ah_listings SET current_bid=?, bidder=?, bidder_name=?, bid_count=bid_count+1, bid_seq=? , expires_at=? WHERE id=? AND status='active' AND bid_seq=? AND expires_at>?` (optimistic on `bid_seq`). If 0 rows (someone else won the race / ended): refund the new bidder immediately (same refund key), mark `refunded`, answer "outbid while you were bidding".
  5. On success: refund the previous leader (deposit key `ah_bid_refund:<listing>:<oldSeq>`), set that bid `outbid`/`refunded`; notify (actionBar/chat to the outbid player if online with clickable `[Bid again]`, else a login notice via the existing seller-notice mechanism extended with `bid_notice`).
  6. Recovery (module enable + hourly): bids `pending` older than 2 min -> re-run step 3 with the same key (DUPLICATE means already paid) then step 4/refund; bids `outbid` with `refunded = false` -> refund again (idempotent key). This makes every crash end in either "bid recorded and funds held" or "funds returned".
- Anti-sniping (Default (owner can change)): a bid in the last `ah.bid.extend-window-seconds` (120) extends the end to `now + extend-seconds` (120), at most `ah.bid.max-extensions` (10) times; 0 disables.
- Ending (`expireDue` extended): auction with `current_bid` null -> normal expiry (item to seller's collection bin, no refund of fee); with bids -> `settleAuction`: status `buying` (reuse), pay seller `current_bid - tax` (`ah_sale:<id>`), put the item into the **winner's** collection bin with source `won` (new source key; `uq_ah_collection_listing` ensures once), mark sold, bid rows `won`; audit `ah_auction_won`; mails/notices to both (winner notice on login, `[Collect]` click). No refund needed for the winner (escrowed). Staff cancel/remove of an auction with bids refunds the leader (`ah_bid_refund`) before returning the item to the seller.
- Seller options: `/ah sell <price> [duration]` stays buy-now; new `/ah auction <start> [duration]` (and the SellMenu gets a "Mode: Buy now / Auction" toggle button, with optional "Buy now" price off by default, `ah.auction.buy-now-allowed: false`). Listing fee: `Pricing.fee` on the start price, same slots as buy-now listings (`listing.slots`), durations list `ah.auction.durations` (Default 6h, 12h, 24h, 48h; max 48 h so money is not locked for days).
- Buyers: `/ah bid <id> <amount>` (`CommandSpec.sub("bid")`, `Arg.integer id` with suggestions = ids of active auctions the sender can see via `engine.activeListings()` filtered to auctions not their own, `Args.amount("amount")` whose only suggestion is the **minimum next bid** for the typed id (owner R2A removed 10k/10m/10b suggestions)); GUI: browse shows auctions with a "Current bid $X (N bids) - ends in 3h 12m" lore and a "Place bid" screen with buttons `Min bid`, `+5%`, `+10%`, `Custom` (uses `MenuService.input` for Bedrock) and a ConfirmMenu with the amount (a bid is a payment: confirm above `ah.bid.confirm-above` Default $100k). `/ah` browse gets a filter tab "Auctions".
- Permissions: `skyblock.auction.bid` (default true), `skyblock.auction.sell` reused for starting an auction, staff `skyblock.staff.auction.remove` reused. Grants file unchanged (defaults true).
- Messages (auction.yml + server copy): `bid.placed`, `bid.too-low` (shows the minimum), `bid.outbid`, `bid.won`, `bid.lost-race`, `bid.own`, `bid.ended`, `bid.cannot-afford`, `auction.listed`, `auction.sold-to`, `auction.no-bids`. Config (auction.yml `bid.*`, `auction.*`) as above, `AuctionConfig` version bump.
- Audit: `ah_bid` (listing, bidder, amount, previous leader), `ah_bid_refund`, `ah_auction_won`, `ah_auction_nobids` in category AUCTION; the economy ledger already records the two reasons.
- Tests (unit, no server): `BidRulesTest` (increment math, minimums, caps, extension), `AuctionEngineTest` with the existing in-memory/H2 test setup if present (race: two bids same seq -> exactly one wins and the loser is refunded), `BidRecoveryTest` (pending/outbid-unrefunded rows resolve idempotently), `TxReason` registry test, migration test.
**Edge cases / exploits:** bid-and-cancel tricks (a bid cannot be withdrawn - escrow only returns on outbid); last-second sniping (extension); shill bidding by alts (own-profile block + `SendGate`; same-IP rule: reuse the economy's R3-39 same-IP transfer check in `ownProfile` implementation - if no helper exists, log both IPs in the audit row); bidding on a listing that just sold (seq/expiry guard); a banned/frozen leader (money stays escrowed and the item goes to the bin; staff can refund via the `ah_bid_refund` path); winner offline (item waits in the collection bin; notice on login); inventory full (bin); cross-profile (bid is per profile, escrow per profile); currency is MONEY only; price caps `priceMax` apply to bids; transaction ordering above guarantees no moment where money exists twice.
**Estimate:** L (engine + DB + menus).

## C2 - Ranked players' new auctions announced in chat  (S-M)

**What exists today:** `AuctionEngine.listed(Listing, Actor)` (`:273`) audits `ah_list` and calls `events.listed(...)` (the module has an `Events` interface with `sold/expired`; check for `listed`); `/settings` toggles via `SettingToggle` (see social AFK toggle); rank groups: `ember` (free) and donor ranks hatchling ... firecrown, staff groups builder/helper/mod/admin/owner (`server/permissions/base.txt`); auction slots per rank already granted there (`skyblock.auction.slots.N`). Chat broadcast style: `docs/planning/CHAT_BROADCASTS.md` (one line, `» KIND`, a click action, blank-line-free for Bedrock) and a 30 s auto-announcer rule.

**Exact change**
- Permission `skyblock.auction.announce` (default `false`) in `modules/auction/.../permissions/auction.yml`; grants in `server/permissions/auction.txt`: `lp group hatchling permission set skyblock.auction.announce true` (all paid ranks inherit upward? verify the group inheritance chain in `base.txt`; if donor groups do not inherit, grant on each of hatchling ... firecrown) and for `helper`, `mod`, `admin`, `owner`, `builder`. **Default (owner can change):** hatchling and above; Ashborn (free) and New do not announce.
- `AuctionModule` implements `Events.listed(Listing)`: if the seller (online, by profile owner) has the node, and the listing price/start >= `announce.min-price` (config `auction.yml announce.min-price: 10000`), and the seller is not rate limited (`announce.per-player-seconds: 120`, global `announce.global-seconds: 20` so 300 players are never spammed), then broadcast to everyone who has the setting `ah-announcements` on (new `SettingToggle` in `/settings` > Social, default on; a player can mute them): `» AUCTION  <rank badge><name> listed <item> x<amount> for $<price> (auction: starting at $...)  [View]` where `[View]` runs `/ah view <id>` (opens the browse menu on that listing; add `/ah view <id>` sub-command with id tab completion; Bedrock: command written out).
- Not shown to players in the tutorial (Gate), hidden/vanished staff are not announced (use `Visibility`/staff-mode flag), console gets the line.
- Config: `announce.enabled: true`, `announce.min-price`, `announce.per-player-seconds`, `announce.global-seconds`, `announce.show-item-lore: false`. Messages `announce.listed`, `announce.listed-auction`, setting labels. Rank badge via `BadgeService` (chat module) for consistent look.
- Audit: none (the `ah_list` row exists). Tests: `AnnounceRulesTest` (permission, min price, both rate limits, setting off), message-key test.
**Edge cases:** price-spam to advertise (min-price + rate limits + one announcement per listing); item names with MiniMessage tags must be escaped (use `Msg.p`/`escapeTags`, as `AuctionTexts` does); profile switch between list and broadcast; announcements for listings that failed fee payment (`pending` -> only announce once `ACTIVE`).

## C3 - Crate rewards: skill XP boosters (boosters module)  (M)

**What exists today:** crates already give `skill_xp` booster items: `crates.yml` lines `xp_booster ... booster: skill_xp percent minutes` (e.g. `:43, :61, :80, :106, :165`); `RewardType.BOOSTER` -> `CrateOpener` (`:229-245`) creates a personal booster item through `BoosterService` when the module runs and `valid(type, percent, minutes, global)`. `BoosterType` (common) has only `SELL, SKILL_XP, TOOL_XP, CRYSTALS` and each type = one `BonusFamily` (`BoosterType.of(family)`); `BoosterBonusSource` adds the running personal+global booster of that type to every skill XP gain regardless of skill. DB `booster_active (profile_key, booster_type VARCHAR(16), ...)` is one row per type.
So "skill XP boosters" exist but are all-skills only and appear as one generic "XP booster". The request reads as: dedicated skill-XP boosters as crate rewards, per skill.

**Exact change**
- `common/booster/BoosterType`: add `MINING_XP`, `FORAGING_XP`, `COMBAT_XP`, `FARMING_XP`, `FISHING_XP`, each with `family() = SKILL_XP` and a new `skill()` returning the `SkillType`; keep `SKILL_XP` as "all skills". Ids fit `VARCHAR(16)` (`foraging_xp` = 11). `BoosterType.of(family)` stays for the all-skills type (first match); add `BoosterType.forSkill(SkillType)`.
- `BoosterBonusSource.value(family, ctx)`: for `SKILL_XP` add the all-skills booster **plus** the skill-specific booster whose `skill().id()` equals `ctx.activity()` (the skill id is already the activity for skill XP: see `EnchantBonusSource.total(..., "mining")`). Verify in `SkillManager.addXp` that the context is built with the skill id; if not, add `BonusContext.of(player, null, skill.id())`.
- Stacking rule (Default (owner can change)): a skill booster and the all-skills booster **add** (both run), within the existing `SKILL_XP` family cap (300%). Different skills run in parallel; one active per type.
- Crates: `crates.yml` entries use `booster: mining_xp` etc.; `CratesModule` validation uses `BoosterType.values()` already (`BOOSTER_TYPES` list in `CrateCatalog`), so new ids validate automatically. Default crate changes (catalog is data; shipped through the crates catalog migration like `CatalogMigration`, version bump): replace the generic `xp_booster` in the mid/high crates by two rotating skill boosters per crate, e.g. blaze: `mining_xp 50% 30 min` and `combat_xp 50% 30 min`, cinder/ember keep the generic 25% one, inferno/phoenix: `farming_xp`/`foraging_xp` options; chance stays equal to the replaced reward (so crate EV does not change; add the sell-value check test `CrateEvTest` if it exists).
- `BoostersSettings`: material per new type (`out.put(BoosterType.MINING_XP, Material...)` at `:184` pattern), limits per type (`limits.skill-xp` percent/minutes reused for all five unless overridden `limits.<type>`), messages `type.mining_xp` etc. in `boosters.yml` + server copy, `/boosters` menu shows the skill name in the lore ("+50% Mining XP, 30 min"), `BoosterItems` lore/name per type.
- Migration: none for the table (type is a string); data is forward compatible. The config file for crates: bump `CratesSettings` version only if values change via `valueChanges`.
- Permissions/commands: none new; `/boosters give` staff (if exists) gets the new ids in its tab completion (it is built from `BoosterType.values()`; verify).
- Tests: `BoosterTypeTest` (ids unique, skill mapping, `forSkill`), `BoosterBonusSourceTest` (activity match adds, other skill does not, all-skills + skill add and cap), `CratesCatalogTest` (all reward booster ids valid), `BoostersMessagesTest` (every type has name/lore).
**Edge cases:** item uniqueness (`booster_used_items` unchanged); bound-to-opener rule unchanged; global boosters are not made per skill (Default: only personal); skill XP from kills (Slaughter) uses `StackKillEvent.SKILL_XP_SOURCE` with activity combat - make sure the context carries "combat".

## C4 - Round reward numbers (milestones, tasks, quests)  (M)

**What exists today**
- `modules/progression/src/main/resources/tasks.yml`: task rewards `crystals` come from a model: 20, 27, 36, 49, 66, 90, 121, 163, 221, 298, 402, 543 (15 tasks each); `pillar-xp` 50, 68, 91, 123, 166, 224, 303, 409, 552, 745, 1005, 1357; `amount` targets include 31, 39, 42, 49, 58, 61, 64, 66, 76, 95, 112, 119, 144, 149, 184, 186, 1144, ... (a mix of round and odd values).
- Quests: `QuestMath.nice(long)` (`modules/quests/.../QuestMath.java:68`) already rounds **amounts** (up to 20 as is, steps of 5 below 100, two significant digits above) but `QuestMath.crystals` (`:92`, `Math.round(base * growth^(tier-1))` with growth 1.3 -> 20, 26, 34, 44, 57, 74, 96 ...) and `QuestMath.money` (minutes x REF/min, any integer) are not rounded.
- Milestones: `MilestoneModel.Curve.round2` (`:119`): two significant figures (250, 980, 3,800, 15,000 ...); perk values are small integers already.
- Others to check with the same rule: `rewards` daily/playtime (`PlaytimeService`, `RewardsSettings`), pass tiers, crate pouch amounts (`crystal-growth` 1.3 in `CratesSettings`/`QuestsSettings pouch.crystal-growth`), Depths token/crystal drops are balance outputs and out of scope.

**Proposed rounding rule ("nice numbers")** - one pure helper `common/util/NiceNumbers`:
- `nice(value)`: `< 20` -> as is (integers); `20-99` -> multiples of 5; `100-999` -> two significant digits, with the leading pair snapped to a multiple of 5 when it is above 30 (so 123 -> 120, 166 -> 170, 221 -> 220, 298 -> 300, 402 -> 400, 543 -> 550, 980 -> 1,000); `>= 1,000` -> same rule on the leading two digits (3,800 -> 4,000, 5,430 -> 5,500, 14,850 -> 15,000). Always strictly increasing inside a series (`ensureIncreasing`: bump by one step if a rounded value does not exceed the previous).
- Money rewards >= 10,000 use 2 significant digits only (no 5-snap): $1.5k, $2.4k, $12k.
- Applied: (a) `QuestMath.crystals` and `QuestMath.money` call `NiceNumbers`, `QuestMath.nice` delegates to it (one implementation); (b) `MilestoneModel.Curve.round2` -> `NiceNumbers` (series monotonic); (c) `tasks.yml` rewritten once by a script `tools/round_rewards.py` (crystals 20, 25, 35, 50, 65, 90, 120, 160, 220, 300, 400, 550; pillar-xp 50, 70, 90, 120, 165->170, 225->220 ...) and `amount` targets snapped the same way; (d) `RewardsSettings`/`PlaytimeService` display only: values come from config, so edit defaults to nice values by hand (list in the PR).
- Test `NiceNumbersTest` (table above, monotonic series, idempotent `nice(nice(x)) == nice(x)`, never below 1), `TasksRoundedTest` (every `crystals`, `pillar-xp`, `amount` in the shipped `tasks.yml` satisfies `NiceNumbers.isNice`), `QuestMathTest` updated.
- Migration: task rewards are paid once per season per task and stored as "done", so changing the numbers only affects future payouts. `tasks.yml` is a module-shipped file merged with the live copy: bump its `config-version` header (see how `sell.yml`/`tasks.yml` versions are handled in `ConfigManager`) so unedited live files take the new values; edited files keep theirs and the loader logs the differing keys at INFO.
**Edge cases:** island level points / `block-values.yml` stay (balance model outputs); do not round spawner and bank prices (out of scope, listed in Owner questions); crystals totals per tier change by < 5% (document in `ECONOMY_BALANCE.md` and re-run `tools/economy_model.py`); progress bars/lore show the new values automatically.
**Default (owner can change):** rule above; costs (tool upgrade money, bank tiers, spawner prices) are **not** rounded in this item.

---

# Phase D - PvP / cosmetics

## D1 - keepInventory only in the PvP area with PvP armor  (plan only, owner question)

**What exists today:** `worlds.yml` default gamerule `keep_inventory: true` for **every** world (`server/plugins/PhoenixSkyblock/worlds.yml:16-17`, "death rules are handled by the plugin (REVIEW B7)"). A PvP world exists: `warzone` (`pvp: true`, `worlds.yml:99-113`), built (KOTH keep, envoy field, duel arenas), but **no PvP/KOTH code** (Phase 9 unbuilt; `PHASE-9-EVENTS.md` WR1: "warzone death = keep inventory, lose 2% purse (max $1M)"). `REVIEW.md B7` default table: keep inventory everywhere, PvP zones lose only carried event loot. Wardrobe has "warzone normalization" (W2) for sets; there is no "PvP armor" item yet (grep finds none).
**Interpretation needed:** (a) "keepInventory only in the PvP area" = outside the warzone you **lose** items on death (reverses B7 and affects every player in the Depths/islands), or (b) in the warzone you keep inventory **only while wearing the PvP armor**, otherwise your inventory drops, or (c) the warzone is the only place where a death is allowed to drop things.
**Plan (no code now):** set the gamerule per world (`worlds.yml`) - `keep_inventory: true` everywhere except `warzone: false` - and let the plugin decide in `PlayerDeathEvent` (staff/tutorial listeners already touch it): in the warzone `setKeepInventory(wearsPvpSet)`, drops filtered so tools/sets/keys/bound items are always kept (`BoundItems`), event loot dropped. Needs: PvP armor definition (item `pvp_armor` set, tiered, normalized stats, bound), combat tag service (COMBAT_LOGGING.md), killer reward rule (R3-42), death messages. Cost L once the PvP module exists.
**Default (owner can change): no change now.** Keep B7 (keep inventory everywhere) until the owner answers the questions below. Safe because nothing today can drop items in PvP.

## D2 - Fall damage off  (S)

**What exists today:** per-world game rules in `worlds.yml`; `spawn` already has `fall_damage: false` (`:45`); `islands`, `outposts`, `depths`, `warzone` use the vanilla default (on). The worlds module re-applies `gamerules` on every start (`worlds.yml` header) - the 26.x rule id is `fall_damage`.
**Exact change:** `modules/worlds/src/main/resources/worlds.yml` (shipped defaults) **and** the tracked `server/plugins/PhoenixSkyblock/worlds.yml`: add `fall_damage: false` to `islands` and `outposts` (Default: off on islands, spawn (already), outposts); `depths` and `warzone` keep `true` (Default (owner can change); the Depths has voids and tier danger, and PvP needs it). Comment lines explain why. Ender pearl damage uses the same rule in 26.x (verify on the test server with owner permission); elytra crash (`FLY_INTO_WALL`) is a separate cause and stays.
- Optional per-island flag later: `/is settings fall-damage` (not in this item).
- Void rescue / "fall into void" teleport logic is unaffected (it is `VOID`, not `FALL`).
- Migration: the file is tracked and deployed; since `worlds.yml` is re-applied on start, a restart applies it. If a world file is edited on the VPS, merge manually (the deploy script already pulls the owner's GitHub edits for punishments only - note in the release message).
- Tests: `WorldsConfigTest` (parse `fall_damage` for the five worlds), manual: jump from 20 blocks on island and spawn (no damage), in the Depths (damage).
**Edge cases:** the Depths arrival pad and portals have no fall damage anyway; fall-based spawner grinders (S5 "fall and lava kill a whole stack") are mob deaths, not player damage - unaffected; fly mode irrelevant.

## D3 - Cape -> elytra  (plan only, owner question)

**What exists today:** no cape cosmetic exists (grep `cape` finds only "escape" strings). Elytra is a Black Market offer (`blackmarket.yml` `elytra`, rotation test `elytra`) and `WORLD.md:161` says elytra are allowed except in the warzone, obtained via crates/Black Market/events. Rank perk lists may mention capes in `docs/` only.
**Plan:** vanilla cannot show a cape server-side without a pack (pure vanilla since 31 Oct), so a "cape" can only be an **elytra worn in the chest slot**. Options: (A) a cosmetic "Cape elytra": a bound, unbreakable elytra item with a trim/dye-free custom name that does not allow flight (`gliding` blocked by cancelling `EntityToggleGlideEvent` for items with a `cosmetic_cape` PDC key) - looks like a cape, no gameplay change; (B) real elytra as a rank perk (needs rules for armor slot conflicts with wardrobe sets, the set chest slot is occupied!). Wardrobe sets use the chestplate slot (`Equipment.java`), so any cape/elytra conflicts with the set; solution in (A): the wardrobe offers a "back" cosmetic slot only when the set has no chest piece shown, otherwise skip. Needs owner decision; not designed further.
**Default (owner can change): no work until answered.**

## D4 - Staff ranks look better in chat and tab  (S-M)

**What exists today:** chat format `chat.yml format.chat = "<rank><name><tag> <gray>»</gray> <white><message>"` (`ChatConfig.java:17-19`), tab `"<rank><name><tag><afk>"`, names white next to a rank word (`ranks.name-color: #FFFFFF`), rank words from `ranks.looks` (`RankLooks.DEFAULTS`, `common/rank/RankLooks.java:40-57`): staff are single bold colours (`owner=#FFFF55|&e&l`, `admin=#FF5555|&c&l`, `mod=#55FFFF|&b&l`, `helper=#55FF55|&a&l`, `builder=#FFAA00|&6&l`), donor ranks single colours, Immortal a 3-colour gradient; Bedrock gets the legacy code part. Tab teams are one per player sorted by weight (`TabService`, team name `pxs<999-weight><name>`), so staff already sort on top. Prefixes come from LuckPerms (`base.txt`: `lp group owner meta addprefix ... "&e&lOWNER &f"`).
**Proposal (config + small code):**
1. **Looks:** give each staff rank a two-colour gradient and keep the Bedrock code: `owner=#FFAA00,#FFFF55|&e&l`, `admin=#FF3B3B,#FF8A8A|&c&l`, `mod=#2AB7FF,#7DF3FF|&b&l`, `helper=#2ED36B,#8CFFB0|&a&l`, `builder=#FF8C1A,#FFC266|&6&l` (stay clear of donor colours as the Immortal-gradient note requires). Config-only: `ranks.looks` list, `ChatConfig.version()+1` with `valueChanges("ranks.looks", RankLooks.DEFAULTS_V2 -> DEFAULTS_V3)` (the repo already does this for V1->V2).
2. **Chat line for staff:** staff messages get the rank-coloured name and a thin separator: new part `<staff-sep>` (e.g. `<dark_gray>|</dark_gray>`) shown only for staff groups, so `OWNER | Pouched » hi`; the plain chat message stays white (readability, no coloured walls of text); config `format.staff-chat` (empty = use `format.chat`). Staff identified by LuckPerms group (builder/helper/mod/admin/owner) through the existing `Display` rank data (no extra lookup).
3. **Hover card on the name** (already described in the config: "Titles show in the hover card"): add for staff a line "Staff: <rank>" and "Ask me for help" (`format.hover-staff`).
4. **Tab:** a thin separator row/header isn't possible per entry in vanilla; instead (a) staff entries use the gradient badge and a leading `✦`-style symbol only if the font renders it on Bedrock (use a plain `*`-free fallback: Default no symbol), (b) the tab footer line shows "Staff online: N" (config `tab.footer-staff-count: true`, count excludes vanished staff via the existing `VanishService`), (c) `afk` marker unchanged. 
5. Tests: `RankLooksTest` (new defaults parse, gradients valid), `ChatConfigMigrationTest`, `DisplayStaffTest` (staff part only for staff, vanished staff not counted).
6. Mockups to attach to the owner questions (render as text): `OWNER | Pouched » hi`, `HELPER | Alex » need help?`, `MOD ...`, tab block with five staff rows on top.
**Edge cases:** gradients on a Bedrock client fall back to the legacy colour code (already supported); keep the badge short (mobile), no brackets; vanished staff must not appear in the count or in tab (existing visibility rules); name-colour setting `ranks.name-color` stays white; `/nick` marker unchanged.
**Default (owner can change):** the gradient looks above, no symbol, separator only in chat, staff count in the tab footer.

---

# Owner questions (all defaults are applied unless the owner answers otherwise)

Q1 WorldEdit wand
1. Is `warped_fungus_on_a_stick` fine as the WorldEdit wand? (**Default: yes**; alternative: keep an axe look, needs an event-priority spike with FAWE on the test server.)

Q3 / Q5 / Q6 / Q7
2. `/is create` on an existing island only explains and offers `[Open]` / `[Go home]` (**Default: yes**) - or should it teleport home?
3. Obsidian -> lava: cooldown 2 s, 120 per hour, needs the island `BREAK` permission (**Default: yes**). Also allow it on outposts later?
4. Starter chest after disband: every new island gets the full plain starter chest again (items + Field Guide), tools are never re-given (**Default: yes**). Is that what you meant by "fully resets", or should a new island also reset the tutorial?
5. Staff profile reset: `/profiles reset <player> [slot]`, target must be offline, confirm screen, 30-day backup (**Default: yes**). Allowed on team-island leaders? (**Default: no**, move leadership first.)

Phase A
6. Hoe upgrade names/levels: Till (1/3/5/7 wide), Plant (1/3/5), Autoplant (1 level), shard prices 15 / 20 / 150 base (**Default: as listed**). Rows across the facing direction like the harvest row, or forward (**Default: across**)?
7. Cobble generators: add andesite/diorite/granite/tuff/deepslate, deepslate ores and nether quartz; keep the whole feature behind `enabled: false` until you switch production on (**Default: yes**). Sell price for nether quartz $150? (**Default: yes**)
8. Spawner mobs never hurt players and stay killable (**Default: yes**). Should they also be immune to fire/lava/fall (**Default: no**, grinders are intended)?
9. Spawner drop prices to the balance-sheet values $15 / 25 / 50 / 90 / 270 (3x today's, payback ~60 h) and sellable vanilla gold/iron nuggets at $2 (**Default: apply**). This raises spawner income about 3x - confirm.
10. Island storage: answer the 11 questions in `docs/planning/ISLAND_STORAGE.md` (**Default: all "Suggested"**): shared live chest, 1 -> 6 rows via Island Vault, role toggles, take-out limits 64/512/none, no unique items, 7-day log, one Sort button.

Phase B
11. Dungeon mobs: are mobs sometimes standing on roofs/outside the cave (so the spawn-height fix is the right one), or do mobs on the floor also fail to hit? (**Default: fix both: ground-only spawns plus a retarget layer**.) Which zones/tiers do you see it in?
12. Boost blocks: haste II 30 s / speed I 30 s only at first (**Default**), XP/extra-drop boosts off until you approve numbers. Frequency: a node every 90 s, max 2 visible, 40 per hour?
13. Treasure hunt: money + Ember key per spot, 5% Cinder key, title "Treasure Hunter" for finding all spots, rewards only for profiles under 20 h playtime (**Default: yes**). Who is "all" for the title - every player who completes the set (**Default**) or only the first N?

Phase C
14. Auction bidding: escrow the full bid at once, +5% / $1 minimum raise, anti-snipe extension 2 min up to 10 times, 6-48 h durations, no buy-now option, bids from alts blocked as for buying (**Default: yes**). Fee on the start price like normal listings?
15. Ranked auction announcements: hatchling and above (all paid ranks) and all staff, minimum price $10,000, 2 min per player / 20 s global, players can mute (**Default**). Or only specific ranks?
16. Skill XP boosters per skill (mining, foraging, combat, farming, fishing), added on top of the all-skills booster, replacing generic XP boosters in Blaze/Inferno/Phoenix crates at the same chance (**Default: yes**). Global (server-wide) skill boosters too? (**Default: no**)
17. Rounding rule for rewards (nice numbers: 20, 25, 35, 50, 65, 90, 120, 160, 220, 300, 400, 550; quest/milestone targets 1,000, 4,000, 15,000) and shipping a new `tasks.yml` (**Default: yes**). Also round costs (tool upgrades, bank tiers, spawner prices)? (**Default: no**)

Phase D
18. keepInventory: which meaning? (a) outside the warzone death drops items, (b) in the warzone you keep inventory only with PvP armor, (c) other. Needs a PvP armor design (tiers, normalization, how obtained). (**Default: no change, keep B7 "keep inventory everywhere"**.)
19. Fall damage off on islands, spawn and outposts; on in the Depths and warzone (**Default: yes**). Also off in the Depths?
20. Cape -> elytra: a cosmetic "cape" that is a non-flying elytra worn on the back (conflicts with set chest pieces), or real elytra as a rank perk, or drop the idea? (**Default: no work**.)
21. Staff chat/tab look: gradient badges, `OWNER | name » message` separator, "Staff online: N" in the tab footer (**Default: yes**). Want a different palette or a symbol?

Open items outside the owner's reach: Q6 needs one audit-log query on the live DB before coding; B1 needs a test-server run (owner permission) to confirm the mobs' behaviour; Q1 optional spike needs the FAWE jar.
