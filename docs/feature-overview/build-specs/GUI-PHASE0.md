# Build spec GUI-PHASE0: remove resource pack v1 completely, run pure vanilla

Cards: I7 v3 (Locked), I9 v2 (Locked), G9 v3 (Locked). Ledger: I7-R1-02, I7-R1-03, I7-R1-04, I7-R1-06. Source inventory: `feature-overview/disagreements/PACK-V1-AUDIT.md` (read it first; this spec orders it and fixes the open choices). Written 31 Oct 2026. No code in this file.

## 0. Goal and scope
**Goal:** after this build the server has no resource pack at all: no prompt, no download, no kick, no web server on TCP 25580, no pack module, no pack fonts, glyphs, models or sounds. Menus use the plain vanilla base items they already have. Ranks show as plain coloured `[Name]` text with the **same colours as today**. Crates stay real vanilla blocks. Player data and the database are untouched.

**In scope:** everything in section 3 (ordered steps).

**Out of scope (do not start):** the I9 menu framework change (no frame removal, no new bottom row, no compact layouts: menus keep today's layout minus pack looks), any new pack code (phase 1), new icons, the C1 credits rename, `/pack` in any form, changing a rank colour value.

**Result the owner sees:** menus look like plain Minecraft chest screens with today's dark frame; ranks the same colours; nothing about a pack anywhere. It is a "what does vanilla look like" build, on purpose (GUI-Q3).

## 1. Branch, tag, rules
- Worktree on branch `q/pack-removal` (one lane). Each numbered step below is its own commit (conventional commits, e.g. `refactor(chat): rank looks in chat.yml`); `./gradlew build` must pass after every step. Never start a test server (`runServer`) without the owner's permission.
- Before step 1, on `main`, create the tag `pack-v1-final` (the commit that still contains all v1 code and art). Push the tag with the release push. History keeps the art and generators for the owner.
- Edit bundled and tracked server copies of a yml **in the same commit** (`tools/sync_server_configs.py` lets the newer commit win, so an old copy can come back).

## 2. Keep-list (do not touch with search-and-delete)
- Starter pack (`tutorial` `pack_given`, messages `pack.given`, `StarterContents.giveJoinPack`), `packed_mud` / `packed_ice` in shop and depths configs, `RecentPlacements.pack`, backpack items, `Style.unsafeGlyphs` and `bedrock-safe-symbols` in `style.yml`.
- `PrivateUse` (strip of U+E000 to U+F8FF from player text) and its call sites (chat, signs, books, nicknames, notes, island chat, `TypedText`); the class moves (step 2), the behaviour does not change. Its tests stay.
- `ItemDefinition.itemModel` and the tool `item_model` / `model:` keys in `tools-defaults.yml` (vanilla model keys such as `minecraft:golden_pickaxe`); only reword comments that say "resource pack".
- `ConfirmMenu`, `NumberPickerMenu`, `PickListMenu`, `PlayerPickerMenu` (its player-name search stays), `OfferMenu`, `Navigation`, `TypedText`, `BedrockForms`.
- `BadgeGradients` and the `rank-color` setting, permission `skyblock.chat.rankcolor`, `chat.yml badges.immortal-gradients` (Immortal gradient name perk; F2 decides its future).
- `server/server.properties` pack lines (already empty), Geyser `enable-integrated-pack`, ViaVersion `forced-use-1_17-resource-pack: false`, Floodgate config, `branding/*`, `tools/sync_server_configs.py`, `tools/owner_sheet/**`, `server/scripts/*` (no pack lines).
- `crates.yml physical.blocks` (barrel, chest, copper chest, ender chest, vault, ominous vault): unchanged in phase 0. The copper chest does not exist in 1.21.5; F5 picks the replacement block (ripple GUI-P3). Do not change it here.
- Existing crate key items in player hands: leave (an inert `custom_model_data` string does no harm).

## 3. Ordered steps

### Step 1. Re-home the rank looks FIRST (nothing is deleted yet)
New typed setting `ranks.looks` in `ChatConfig` (`chat.yml`), editable in `/admin`: a map `group id -> { color, bedrock }`.
- `color`: one hex (`#FF9A4D`) or three comma-separated hexes (a gradient, Immortal only).
- `bedrock`: the legacy colour code string (`&6`, `&e&l`, or the segment form `&e&l>&6&l>&c&l` where `>` splits the text into equal parts, exactly as `Badges.bedrock` reads it today).
- Defaults in code are these 16 entries, copied **exactly** from `pack-ids.yml badges.ranks` (the `font`, `shine`, `family`, `caps`, `cap-color` fields are dropped; they only drew the font badge):

| Group | color | bedrock | Group | color | bedrock |
|---|---|---|---|---|---|
| default | `#B5ABA4` | `&7` | torchbearer | `#2EE6D0` | `&3` |
| hatchling | `#FF9A4D` | `&6` | flameheart | `#FF5FD2` | `&d` |
| fledgling | `#9BE84A` | `&a` | firecrown | `#FFFFFF` | `&f&l` |
| firewing | `#4FB4FF` | `&9` | owner | `#FFC21A` | `&e&l` |
| flamecrest | `#B06BFF` | `&5` | admin | `#FF6B6B` | `&c&l` |
| sunfeather | `#FFD21A` | `&e` | mod | `#6CC4FF` | `&9&l` |
| pyrelord | `#FF3B4A` | `&c` | helper | `#7CEB8A` | `&a&l` |
| immortal | `#FFE27A,#FF8C1A,#FF2D3F` | `&e&l>&6&l>&c&l` | builder | `#FF9A3D` | `&6&l` |

- The `default` entry is the look of any group that has no entry (this is the "Ashborn look" of today). Do **not** fix the near-duplicate hatchling `#FF9A4D` and builder `#FF9A3D` here (F2 and G8 do); phase 0 keeps every value identical.
- New pure class `RankLooks` (parse and lookup, no Bukkit) in `common`; the name colour of a rank is the middle colour of its list (`colors.get(size / 2)`, as `Badges.nameColor` does).
- Keep working beside it: pack-ids still exists in this step; add a test that the 16 default entries equal the `pack-ids.yml badges.ranks` values (read the bundled file in the test). This test is deleted in step 6 together with `pack-ids.yml`, after it has proven the copy.
- `server/permissions/base.txt`: do not change any command. Add one comment line at the colour block: rank display colours are set in `chat.yml ranks.looks`; the `color` meta here is only the fallback for a group missing there.
- Config key documentation (`desc`) says where the values came from; `YamlKeysTest` and `DeadConfigKeysTest` must pass.

### Step 2. Rank display without the pack
- `git mv` `Badges`, `BadgeOptions`, `BadgeService`, `BadgeGradients`, `BadgeSettings` from package `common.pack` to a new package `common.rank`; `PrivateUse` to `common.text`. Fix imports (mechanical).
- `Badges.render(RankLooks, group, displayName, options)` keeps only the two text forms: **simple** `[Name]` in the rank colour (a per-character text gradient for the three-colour Immortal look) and **Bedrock** `[Name]` with the legacy code(s). Delete the font form, `fontId`, `glyphLetters`, caps, letter gap, shine and large variants. `cleanName` (strip private-use, `&`, `§`, never blank) stays. An unknown group uses the `default` look.
- `BadgeOptions` keeps `bedrock` and `gradient` only (drop `pack`, `reducedMotion`, `simple`, `large`, `fontBadge`); `key()` stays for the cache.
- `BadgeService`: `enabled()` is removed (callers no longer branch on "pack present"); `ids()` removed; the rest stays (`options`, `badge`, `gradient`, `gradientChoice`, `color`, `gradientIds`). `RankBadges` reads `RankLooks` from `ChatConfig`, drops every `PackService` reference, and `colorOrNull` always returns the rank colour for a listed group. `ChatModule` keeps `core.ranks().colorSource(badges::colorOrNull)` so names in chat, tab, nametag, scoreboard, join messages and hover keep today's colours.
- Callers: `HudService` (sidebar `<rank>`), `SocialModule` (`/list` header), `FormatPreview`, `TabService`, `Display`, `ChatModule`: remove the "badges enabled" branches; they always use the badge (which is the plain coloured `[Name]` text). The tab list entry and the nametag keep their current simple form.
- `BadgeSettings`: delete `REDUCED_MOTION`, `SIMPLE_BADGES`, `LARGE_BADGES` and their `/settings` entries; keep `RANK_COLOR` (needs `skyblock.chat.rankcolor` and the group `immortal`). Stored values of the three removed keys are ignored (same pattern as `RetiredTexts`); no SQL.
- `/rank preview <group> [name]` stays as a staff tool: lines `Simple`, `Java` and `Bedrock` only (delete the `shine`, `still`, `large` lines and the `unavailable` pack message). Tab completion: LuckPerms groups plus every group in `ranks.looks`. Permission `skyblock.rank.preview` unchanged.
- `messages/chat.yml` (bundled and tracked copy, same commit): remove the three retired toggles' texts and `rank-preview.shine/still/large/unavailable`; keep `rank-color` and the remaining `rank-preview` lines.
- Docs: rewrite the "Rank badges (pack-badges)" paragraph and the `/rank preview` row in `docs/commands/chat.md`.

### Step 3. Crates (leaf cut)
Delete `CrateDisplays`, `CrateHinge`, `CrateHingeTest`, `KeyModelTest`; in `CrateYard` and `CrateYardFx` remove the display calls, `onPackStatus(PlayerResourcePackStatusEvent)`, the lid shake/swing and the per-viewer chime choice (keep the vanilla `approach.fallback-sound` chime and the rarity dust); `KeyItems`: stop writing the `custom_model_data` `crate/key/<crate>` string (delete `keyModel`); `CratesSettings`: remove `displays.*` (9 keys), `lid.*` (9 keys) and `approach.pack-sound`, add them to `removedKeys()`; `crates.yml:5` comment ("the pack retextures it later") reworded; `docs/commands/crates.md` line 14 ("Pack polish") fixed. Pedestal blocks, holograms (`» CLICK to open`), reward show, previews and key redeem are untouched.

### Step 4. Sounds (leaf cut)
`Sounds`: remove the `pack-sound.*` section, `DEFAULT_PACK`, `setPackViewers`, `pick(normal, pack, key, viewerHasPack)`; keep `play`. Delete `PackSoundTest`. Remove the `pack-sound:` block (lines 17 to 22) from `plugin/src/main/resources/sounds.yml` and `server/plugins/PhoenixSkyblock/sounds.yml` in one commit. `PackModule` stops calling `setPackViewers` in step 6.

### Step 5. Menus (leaf cut)
- `MenuService`: delete the `packs` supplier and `hasPack`, `render(viewer, item)`, the title glyph prefix in `viewTitle`, the `framePanesPack` cache, the pack branch in `redrawInPlace` (`titleUpdateInPlace` now works for every viewer), the `MenuIcons.tag(...)` calls on filler, loading, error, empty, locked, back, info, close, help, main-menu and toggle items (lines in audit section 1). Keep `PrivateUse.strip` on typed input.
- Delete `PackTitles`, `MenuIcons` (render, all id constants, `tag`, `currencyOf`, `minus`/`plus`) and `MenuModels`. Remove every `MenuIcons.*` and `Nav.icon(...)` call site in `common/menu` (`ConfirmMenu`, `NumberPickerMenu`, `PagedMenu`, `PickListMenu`, `PlayerPickerMenu`), `menus` (`Nav`, `HubMenu`, `YouMenu`, `TravelMenu`), `islands` (`IslandMenu`, 19 uses), `shop` (`ShopModule`, `BuyMenu`), `economy` (`TransactionsMenu`), `rewards` (`DailyMenu`): the item stays as built (its vanilla base material and name), only the tag goes. Currency menu items keep `EconomyConfig.icon(...)`. Do **not** rename `gems` (the C1 build removes it).
- `ButtonLayout.Button` and `WarpLayout.Warp`: drop the `model` field; fix `HubButtonsTest` (`new ButtonLayout.Button(..., null, null)` at lines 169 and 170). Hub marker panes tagged BLANK: leave the panes as they are (G9 removes them in the I9 build).
- `menu.yml` (bundled and tracked copy): reword the `model:` comment at lines 19 and 20.
- Reword "resource pack" in setting descriptions: `BoostersSettings:111`, `EconomyConfig:183`, `ToolItems`/`ToolCatalog`/`tools-defaults.yml` comments.
- Delete `PackMenusTest` (the source scanner that forced every menu into a pack background map).
- Tools (`ForgeMenu`, `ToolItems`, `ToolCatalog`, `SkinsMenu`): only comments change.

### Step 6. Remove the provider
- `SkyblockPlugin`: delete `menus.packs(...)` (lines 141 and 142) and `modules.register(new PackModule(core))` (about line 271); `plugin/build.gradle.kts:47` (`implementation(project(":modules:pack"))`); `settings.gradle.kts:59` (`include("modules:pack")`).
- Delete the whole `modules/pack` directory (PackModule, PackConfig, PackHost, PackUrls, PackFiles, PackStatuses, their two tests, `messages/pack.yml`, `permissions/pack.yml`, `build.gradle.kts`), `common/.../pack/PackService.java`, `PackIds.java`, `PackIdsTest`, `BadgeSettingsTest` (rewrite a small one for `rank-color` only), `RankBadgeKeyTest` (rewrite for the new cache key), the tracked `server/plugins/PhoenixSkyblock/messages/pack.yml`, `plugin/src/main/resources/pack/` (`pack-ids.yml` and both zips), `server/permissions/pack.txt`, `docs/commands/pack.md`.
- Remove the step 1 test that compares `ranks.looks` to `pack-ids.yml` (the file is gone; the golden values stay pinned in `RankLooksTest`).
- `PermissionFilesTest` must still pass (no granted node is undeclared); `./gradlew commandsIndex` regenerates `docs/COMMANDS.md`.
- No `/pack` command, no `skyblock.pack.*` node, no `/skyblock reload pack` target remain. (Phase 1 creates a fresh module and its own nodes.)

### Step 7. Art and tools
After tag `pack-v1-final` exists: delete `tools/build_pack.py`, the whole `resourcepack/` directory (art, generators, previews, README, VERSION), and `resourcepack/src/gui-art/RESOURCE_PACK_GUI.template.md` with it. Keep `branding/`.

### Step 8. Docs (no file is deleted except `docs/commands/pack.md` in step 6)
- Add the header "Superseded by feature card I7 v3 (31 Oct 2026): pack v1 was removed" to `docs/plans/RESOURCE_PACK.md`, `RESOURCE_PACK_GUI.md`, `RESOURCE_PACK_ART_BRIEF.md`, `PACK_BUILD.md`, `PACK_BUILD_PROMPT.md`, `docs/PACK_TEST_CHECKLIST.md`, `docs/OWNER_ANSWERS_PACK1.md` (keep its credits rename task), and a short "see I7/I9" note in `docs/design/GUI_DESIGN.md`, `docs/planning/MENU_GUI.md`.
- `docs/OWNER_PRINCIPLES.md` principle 7: replace the sentence about the forced pack with "A **forced** resource pack (asked after the player has joined) will repaint item icons, and maybe one chest background, later; the owner draws the art. Pure vanilla until then; Bedrock plays without it." (owner decisions GUI-Q1, GUI-Q2, GUI-Q3.)
- `docs/FEATURES.md` I7 row (lines 32, 196); `docs/QUEUE.md` rows GUI-6, PACK1, PACK2, RP1, PLAN-RP: mark superseded, never delete rows; `docs/UI_STYLE.md` lines 43 (`charcoal` "resource-pack panels") and 469 (pack glyph row) only; `docs/STATUS.md:68`, `docs/BACKLOG.md:34`, `docs/LATER.md:6`: mention removal.
- `feature-overview/CODE-MAP.md` I7 row: `pack` module is gone until phase 1.
- `CHANGELOG.md`: entry "Resource pack v1 removed (pure vanilla): no prompt, no download, no kick; ranks are plain coloured text (colours in `chat.yml ranks.looks`); crate models, menu backgrounds, badge fonts, pack sounds, `/pack` removed. Tag `pack-v1-final` keeps the old pack."

### Step 9. Build and release
`./gradlew build` green (all tests). Commit, merge to `main`, push, `server/scripts/deploy.sh` (keep the shell alive until `== Released.`), verify the jar `md5sum` on the VPS, then do section 6 (live cleanup). Send the owner the manual checklist (section 8).

## 4. Tests
| Test | Action |
|---|---|
| `PackLogicTest`, `PackHostHttpTest` (modules/pack) | delete with the module |
| `PackIdsTest`, `BadgeSettingsTest`, `PackSoundTest`, `PackMenusTest`, `KeyModelTest`, `CrateHingeTest` | delete |
| `BadgesTest` (20 tests) | rewrite for the simple and Bedrock forms: golden name colour per rank, three-colour gradient text, Bedrock segment codes, `cleanName` (private-use and `&` stripped, never blank), unknown group = default look |
| `RankBadgeKeyTest` | rewrite for the new cache key (`bedrock`, gradient) |
| New `RankLooksTest` | the 16 defaults equal the table in step 1 (pinned literals), unknown group falls back to `default`, bad hex ignored with a log line, three-colour list for `immortal`, `bedrock` segment split |
| New small `BadgeSettings` test | only `rank-color` is registered; the three retired keys are not |
| `PrivateUseTest`, `TypedTextTest.privateUseGlyphsAreStripped`, `StarterWordsTest.surfacesDropPrivateUseGlyphs`, `NoteRulesTest.cleanDropsPrivateUseGlyphs`, `IslandChatRulesTest.limitDropsPrivateUseGlyphs` | keep (package moves only) |
| `HubButtonsTest` | edit the two `Button(...)` constructors |
| `DeadConfigKeysTest`, `PermissionFilesTest`, `YamlKeysTest`, `DecisionLedgerTest` | keep; they must stay green (they catch leftovers) |
| New source-scan test | no main source file references `PackService`, `PackIds`, `PackTitles`, `MenuIcons`, `MenuModels`, `phoenix:menu`, `phoenix:badge`, `custom_model_data` strings starting `menu/` or `crate/`, or a private-use escape (outside tests of `PrivateUse`) |
| `ChatConfig` test | the default `ranks.looks` map round-trips through the config and the tracked yml tests |

## 5. Config, messages, permissions cleanup (checklist)
- Gone: `pack.yml` (module config), `messages/pack.yml` (bundled and tracked), `permissions/pack.yml`, `server/permissions/pack.txt`, `sounds.yml pack-sound`, `crates-settings.yml displays.*` / `lid.*` / `approach.pack-sound`, `messages/chat.yml` retired toggle and preview texts, the `model:` comment in `menu.yml`.
- New: `chat.yml ranks.looks`.
- Unchanged: `chat.yml badges.immortal-gradients`, `skyblock.chat.rankcolor`, `skyblock.rank.preview`, `server/permissions/chat.txt`.
- No new command, no new permission node, no new message key beyond the existing ones. Tab completion: `/rank preview` as above; nothing else changes.

## 6. Live server (VPS) leftovers: do after the deploy, with the deploy key or `tmux send-keys -t skyblock` (owner allowed console commands; restore nothing, this is the end state)
1. LuckPerms: `lp group helper permission unset skyblock.pack.status` and `lp group admin permission unset skyblock.pack.reload` (`apply-permissions.sh` only adds, so the old grants stay otherwise).
2. Delete on the VPS: `plugins/PhoenixSkyblock/pack.yml`, the folder `plugins/PhoenixSkyblock/pack/` (extracted zip), `plugins/PhoenixSkyblock/messages/pack.yml`.
3. `plugins/PhoenixSkyblock/sounds.yml`: confirm the `pack-sound:` block is gone (deploy overwrites tracked files; if it is still there, remove the block, otherwise its keys would be read as ordinary sounds).
4. Firewall: close TCP 25580 (the rule was opened by hand; no script has it): remove it with the VPS firewall tool in use, then `ss -ltn | grep 25580` prints nothing.
5. `server.properties`: the five `resource-pack*` and `require-resource-pack` lines stay empty or false. DNS: if the owner created `pack.skybornmc.net`, drop it (the code never created one).
6. Check the start log: no line with "pack", no "missing sound pack-sound", no unknown-key warnings from `crates-settings.yml`; `/skyblock reload` lists no pack module; `/admin` config list has no `pack.yml`; `lp group helper permission info` shows no `skyblock.pack.*`.
7. Player settings rows of the three retired badge switches stay (harmless, ignored).

## 7. Acceptance checks (automated and by search)
- `./gradlew build` is green; `settings.gradle.kts` and `plugin/build.gradle.kts` no longer mention `modules:pack`.
- `rg -n -i "PackService|PackIds|PackTitles|MenuIcons|MenuModels|hasPack|pack-sound|phoenix:(menu|badge|space|icons|ui|crate)"` over `common`, `modules`, `plugin`, `server` finds nothing outside comments that say "removed".
- `rg -n "[\x{E000}-\x{F8FF}]"` (and `\\uE0..\\uF8..` escapes) over main sources and resources finds nothing; only `PrivateUse` tests contain such characters.
- The plugin jar has no `pack/` folder and no zip (`unzip -l`); size drops by about 430 KB.
- `RankLooksTest` pins the 16 colours and Bedrock codes of section 3 step 1.
- Server start: no web server on 25580, no log line about the pack (checked on the live deploy, section 6).

## 8. Manual test checklist for the owner (Java and Bedrock)
Java (vanilla client, no pack):
1. Join: no resource-pack prompt, no download, no kick; the join and tutorial portal flow starts as before (and is a little faster).
2. `/menu`, You, Travel, `/is`, `/shop` (tabs and buy page), `/sell`, number picker, a confirm dialog, paging: readable plain titles, correct normal items, Back, Close, Help work, in-place refresh works (no menu reopen flicker).
3. Currency lines in menus show a normal item each (gold ingot, amethyst shard, prismarine shard, diamond) with the coloured name.
4. Chat, tab list, nametag, `/list` and the sidebar: ranks are plain coloured `[Name]`; compare a few ranks with the old look (the colour of owner, mod, helper, a donor rank must be identical); Immortal shows a gradient; `/settings`: the three badge switches are gone, "Rank colour" still works for Immortal; `/rank preview mod` shows Simple, Java, Bedrock lines.
5. Crate yard: pedestal blocks, hologram `» CLICK to open`, opening a crate shows the reward as before; keys are named tripwire hooks with lore and redeem correctly.
6. Tools, skins, boosters, money notes, wardrobe: normal looks; `/settings` sound toggles; click, success, error sounds are vanilla.
7. Typing a private-use character into chat, a sign, a nickname, an island note shows nothing special (stripped).
8. Staff: `/pack` does not exist; `/skyblock reload` has no `pack`; `/admin` config list has no `pack.yml`.
Bedrock (phone): join without any prompt; menus show the same chest screens and items; ranks show `[Name]` in the legacy colours (compare with the Bedrock column of the table); crate yard works; no weird symbols or controller icons in any text.
Both: nothing in any screen mentions a resource pack; the tutorial is unchanged.

## 9. Rollback
`git revert` of the merge (or deploy the previous jar from tag `pack-v1-final`) restores v1; the live files removed in section 6 are re-created by the old jar on start (`pack.yml`, `pack/`), and the LuckPerms grants come back with `apply-permissions.sh`. The database was never touched, so no data rollback exists or is needed.

## 10. Notes for the coder
- Phase 0 is only a removal and a re-home; if a menu looks plain, that is the goal. Do not add new icons, panes or framework code.
- A search-and-delete on "pack" is wrong (see the keep-list); use the audit tables and compile errors.
- Check `ChatConfig` for the setting style (`@Setting`, map settings) before writing `ranks.looks`; keep the default map in code and the tracked `chat.yml` free of it unless a tracked copy already exists.
- After this build, I7 phase 1 (new icons, forced prompt after joining) gets its own build spec when the owner has drawn the first 9 icons.
