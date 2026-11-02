# Pack v1 removal audit (31 Oct 2026)

Research only. No code, config or doc was changed to produce this file. Checked against `main` at `251751b0` (clean tree except unrelated review-doc edits). Owner wish: remove the OLD resource pack (PACK1 / RP1-RP6 / GUI-6, the `pack` module, the forced prompt, custom GUI textures, fonts, model data) and restart menus, GUI and pack as a simple "vanilla+" design with **no custom font and no custom writing**.

Legend. **Remove** = delete. **Keep** = leave as is. **Replace** = needs new code or content. **Decide** = needs an owner answer first. Risk is the risk of the removal step, not of the file.

## 0. The five facts that matter most

1. **The pack is opt-in per viewer by design.** Every pack surface (menu background, icon swap, rank badge font, pack sounds, crate models) is behind `PackService.hasPack(viewer)` and has a vanilla path that Bedrock and no-pack players already use today. Removal is mostly deleting the pack branch, not building a fallback.
2. **There is a zero-code kill switch today.** `pack.yml` (live file on the VPS) `enabled: false`, then `/pack reload` or `/skyblock reload pack`. Result: `onConfigure` returns early (no prompt, no kick, no download), `status()` is `DISABLED`, `hasPack()` is false everywhere, menus and crates are vanilla, the web server on TCP 25580 stops. One side effect: `PackService` stays registered, so rank badges still render, as coloured `[Name]` text (the "simple" form), not as the LuckPerms prefix. Use this as step 0 and as the rollback switch.
3. **The pack module is also the source of the rank colours and the Bedrock rank codes.** `pack-ids.yml -> badges.ranks` holds colour, gradient, `bedrock:` legacy code and cap glyphs per LuckPerms group. `RankBadges.colorOrNull` feeds `core.ranks().colorSource(...)`. Without `PackService`, `RankBadges.enabled()` is false and everything falls back to the LuckPerms prefix text and `meta color` (base.txt has hex colours for default, builder, helper, mod, admin, owner only). The bought/won ranks (Hatchling .. Immortal, Torchbearer, Flameheart, Firecrown) would lose their colours. This must be re-homed (style.yml, as `RESOURCE_PACK_GUI.md` section 6.2 already suggests) before `pack-ids.yml` is deleted.
4. **`menu.yml` has no `model:` entries.** Only a comment mentions `model:` (`modules/menus/src/main/resources/menu.yml:19`, same in the tracked server copy). Icons like `menu/icon/home` and `currency/credits` come from the Java constants in `MenuModels.java` and `MenuIcons.java`, not from config. Nothing in any yml needs migrating for models; the `model` field lives only in two Java records.
5. **No DB table and no player data is tied to the pack.** The status (`LOADED`, `DECLINED`...) is an in-memory map. The only persisted leftovers are four `/settings` toggles (`reduced-motion`, `simple-badges`, `large-badges`, `rank-color`) in the existing player-settings store, and item data on keys (see section 6).

## 1. Code: the pack module and the shared pack API

| Area | Path | What it is | Action | Risk |
|---|---|---|---|---|
| Module | `modules/pack/build.gradle.kts` | Gradle subproject (depends on `:common`) | Remove (or keep module as the v2 shell, see section 9) | low |
| Module | `modules/pack/src/main/java/.../pack/PackModule.java` (343 lines) | Sends the pack in `AsyncPlayerConnectionConfigureEvent`, `required(true)`, prompt, waits for answer, kicks on decline/fail, skips Floodgate, `/pack reload`, `/pack status`, resend on reload, publishes `PackService`, installs `Sounds.setPackViewers` | Remove (keep ideas for v2) | med |
| Module | `.../pack/PackConfig.java` | Typed `pack.yml`: `enabled`, `variant safe/shine`, `url`, `host.bind/port/public-host`, `prompt`, `required`, `kick-on-fail`, `timeout-seconds` | Remove (v2 may keep a subset) | low |
| Module | `.../pack/PackHost.java` | Tiny `com.sun.net.httpserver` that serves exactly one zip on TCP 25580 | Keep as generic (v2 hosting) or remove | low |
| Module | `.../pack/PackUrls.java` | Builds the download URL and the stable per-variant UUID | Keep as generic (drop `variant`) | low |
| Module | `.../pack/PackFiles.java` | Extracts the zip from the jar to `plugins/PhoenixSkyblock/pack/` and checks SHA-1 against `pack-ids.yml` | Replace (v2 reads sha1 from config, not `pack-ids.yml`) | low |
| Module | `.../pack/PackStatuses.java` | Pure map from client status to ours, and kick-key choice | Keep as generic | low |
| Common API | `common/.../common/pack/PackService.java` | Interface: `status`, `hasPack(viewer)`, `ids()`, `Status` enum. Consumed by 6 classes | Remove `ids()`; keep `hasPack` seam only if v2 needs it | med |
| Common API | `common/.../common/pack/PackIds.java` (169 lines) | Parser of generated `pack/pack-ids.yml`: zips, menu backgrounds, icons, glyphs, space table, badges, sounds, crates | Remove | med |
| Common API | `common/.../common/pack/PrivateUse.java` | Strips U+E000-U+F8FF from player text (anti-spoof of pack glyphs) | **Keep** (cheap, 11 call sites, also protects Bedrock from controller-icon glyphs) | low |
| Common API | `common/.../common/pack/Badges.java` (231 lines) | Renders a rank badge: font form (pack), simple `[Name]` form, Bedrock legacy-code form | Replace: keep simple and Bedrock forms, delete the font form (`font`, `fontId`, caps, letter gap) | med |
| Common API | `common/.../common/pack/BadgeOptions.java` | Per-viewer badge flags `pack, bedrock, reducedMotion, simple, large, gradient` | Replace (drop `pack`, `reducedMotion`, `large`) | med |
| Common API | `common/.../common/pack/BadgeService.java` | Interface used by chat, hud, social, cosmetics | Replace (keep as the "rank display" seam, drop `enabled()` = pack present) | med |
| Common API | `common/.../common/pack/BadgeSettings.java` | `/settings` entries: reduced motion, simple badges, bigger text, Immortal colour picker | Replace: delete 3 toggles; keep the colour picker only if the owner keeps Immortal gradients | low |
| Common API | `common/.../common/pack/BadgeGradients.java` | Parses approved Immortal gradients from `chat.yml -> badges.immortal-gradients` | Keep if gradients stay (pure text gradient, no font) | low |
| Menu framework | `common/.../common/menu/PackTitles.java` (111 lines) | Builds the negative-space title: space(-8), background glyph in font `phoenix:menu`, space(-(width+1)), offset, parchment recolour. `CODE_BACKGROUNDS` map of 11 menu classes | Remove | low |
| Menu framework | `common/.../common/menu/MenuIcons.java` (149 lines) | ~34 icon id constants, `feature()`, `tab()`, `currency()`, `currencyOf()` (maps `gems`/`credits`), PDC tag `phoenix:menu_icon`, `render()` = PAPER + `CUSTOM_MODEL_DATA` string (blank panes keep material + hidden tooltip) | Remove `render` and the constants; `tag` is a harmless seam (see order, step 3) | med |
| Menu framework | `common/.../common/menu/MenuService.java` lines 94-95, 172, 181-211, 292, 473, 504-533, 575-590, 627-656, 709-710 | `packs` supplier, `hasPack`, `render(viewer,item)`, `viewTitle` (title glyph prefix), `framePanesPack` cache, `redrawInPlace` reopens for pack viewers, `MenuIcons.tag(...)` on filler, loading, error, empty, locked, back, info, close, help, main-menu, toggle items. Line 922 uses `PrivateUse.strip` (keep) | Edit: delete pack branches, keep vanilla branches | med |
| Menu framework | `common/.../common/menu/ConfirmMenu.java` (109-125), `NumberPickerMenu.java` (108-141), `PagedMenu.java` (88-93), `PickListMenu.java` (123-128), `PlayerPickerMenu.java` (91-93) | `MenuIcons.tag(...)` on confirm/cancel, +/- steps, set min/max, type amount, previous/next, search | Edit (drop tag calls or leave no-op) | low |
| Menu framework | `common/.../common/menu/TypedText.java` (line 40) | Calls `PrivateUse.strip` | Keep | low |
| Sounds | `common/.../common/text/Sounds.java` (33-50, 70-96, 125-137, 178-184) | `pack-sound.*` section, `DEFAULT_PACK` (`phoenix:ui.click/success/error`), `setPackViewers`, `pick(normal, pack, key, viewerHasPack)` | Remove the pack variant; keep `play` | low |
| Items | `common/.../common/item/ItemDefinition.java` (19, 33, 105, 153-171), `ItemRegistry.java` (93-94) | Optional `item_model` component per item. **No caller sets it** | Keep (generic, useful for vanilla+ item models) | low |
| Wiring | `plugin/src/main/java/.../SkyblockPlugin.java` 141-142 and 271-272 | `menus.packs(() -> services.optional(PackService...))`; `modules.register(new PackModule(core))` | Remove | low |
| Wiring | `plugin/build.gradle.kts:47`, `settings.gradle.kts:59` | `implementation(project(":modules:pack"))`, `include("modules:pack")` | Remove | low |
| Wiring | `plugin/src/main/resources/pack/pack-ids.yml` (463 lines, GENERATED) | 101 icon ids, 32 backgrounds, 155 menu-class mappings, 5 glyphs, space table, 17 rank badge looks, 4 sounds, 7 crate models | Remove (after the rank look is re-homed, fact 3) | high |
| Wiring | `plugin/src/main/resources/pack/phoenix-java-1.0.0-bca1f7f3.zip` and `...-shine-a29b40c6.zip` (432 KB total) | The two built zips embedded in the jar (safe and shine variant) | Remove | low |

## 2. Code: modules that use pack data (who depends on what)

| Module | Path | What it does with the pack | Action | Risk |
|---|---|---|---|---|
| menus | `modules/menus/.../menus/MenuModels.java` (66 lines) | Default icon per hub/you/travel button id (`menu/icon/home`...), resolves `model:` from yml | Remove | low |
| menus | `ButtonLayout.java` (55-58, 186), `WarpLayout.java` (39, 96) | `String model` field on `Button` and `Warp` records | Edit: drop the field (tests build `Button(..., null, null)` in `HubButtonsTest.java:169-170`) | low |
| menus | `Nav.java:84`, `HubMenu.java` (111-112, 130, 138, 169, 178-179), `YouMenu.java` (69, 72), `TravelMenu.java` (75, 81) | `Nav.icon(item, model)` = `MenuIcons.tag`. Hub group marks tagged `BLANK`. Rewards-ready tagged `feature("rewards_ready")` | Edit | low |
| menus | `modules/menus/src/main/resources/menu.yml:19-20` and `server/plugins/PhoenixSkyblock/menu.yml:19-20` | Comment describing `model:` | Edit (both copies, same commit) | low |
| islands | `modules/islands/.../IslandMenu.java` (19 uses of `MenuIcons`, lines 114-265) | Feature icons on every /is button | Edit | low |
| shop | `ShopModule.java` (1025, 1036, 1068-1069, 1094), `BuyMenu.java` (129-136, 173) | Tab icons (9), balance currency icon, undo, type-amount, buy 1/16/64, fill | Edit | low |
| economy | `TransactionsMenu.java` (185-186, 220) | `MenuIcons.currencyOf(...)` on currency rows | Edit | low |
| rewards | `DailyMenu.java` (226-227) | Crystals icon on hourly reward | Edit | low |
| chat | `RankBadges.java` (149 lines), `ChatModule.java` (22-24, 128-129, 162, 238-273), `Display.java` (10, 92-108), `TabService.java` (8, 100, 280-290, 442) | Registers `BadgeService`, rank colour source, the 4 badge settings, `/rank preview` with pack forms, per-board badge variants for tab and nametags | Replace (decide the simple rank look first) | high |
| chat | `plugin/src/main/resources/messages/chat.yml` lines 76-108 and tracked copy | Badge setting texts, `rank-preview.*` (shine, still, large, no-pack, "pack module isn't loaded") | Edit | low |
| chat | `modules/chat/.../TextSurfaces.java`, `ChatModule.java` (140, 395, 490) | `PrivateUse.strip` on chat, signs, books | Keep | low |
| hud | `HudService.java:320-326` | Sidebar `<rank>` = badge when `BadgeService.enabled()` | Edit | med |
| social | `SocialModule.java` (418, 811-814) | `/list` rank badge; `PrivateUse.strip` | Edit (badge part only) | med |
| cosmetics | `FormatPreview.java:76-90` | Format preview rank = badge | Edit | low |
| perks | `PerksModule.java:329` | `PrivateUse.strip` in nicknames | Keep | low |
| islands | `NoteRules.java:88`, `IslandChatRules.java:104` | `PrivateUse` in notes and island chat | Keep | low |
| crates | `CrateDisplays.java` (296 lines), `CrateHinge.java` (102 lines) | Two `ItemDisplay`s (PAPER + CMD `crate/block/<id>_body/_lid`) per pedestal, shown per viewer, lid hinge animation | Remove | med |
| crates | `CrateYard.java` (82, 125, 132, 224, 384-401, 414, 475, 479-510, 606), `CrateYardFx.java` (4, 32-47, 77, 84, 103) | Calls displays, `onPackStatus(PlayerResourcePackStatusEvent)`, per-viewer chime choice | Edit | med |
| crates | `KeyItems.java:184-190` (`keyModel`, `CUSTOM_MODEL_DATA` = `crate/key/<crate>`) | Every crate key item carries a CMD string for the pack's key texture | Edit (stop writing it) | low |
| crates | `CratesSettings.java` 73, 124, 162-195 (`displays.*`, 9 keys), 198-225 (`lid.*`, 9 keys, the sound keys may still be wanted), 262-263 (`approach.pack-sound`) | Settings that exist only for the pack models | Remove + add to `removedKeys()` | med |
| crates | `modules/crates/src/main/resources/crates.yml:5` | Comment "the pack retextures it later" | Edit | low |
| tools | `ForgeMenu.java:190-193`, `ToolItems.java:216-224`, `ToolCatalog.java` (53-71, 228-290), `SkinsMenu.java`, `modules/tools/src/main/resources/tools-defaults.yml` (6, 21, 60, 100-105) | `ITEM_MODEL` from `model:` in tools config. Defaults use **vanilla** keys (`minecraft:golden_pickaxe`, `minecraft:golden_hoe`) and `""` for skins | **Keep** (vanilla+ trick, works without a pack; check Bedrock). Reword the comment about "resource pack" | low |
| boosters, economy | `BoostersSettings.java:111`, `EconomyConfig.java:183` | Setting descriptions mention "resource pack" | Edit text only | low |

## 3. Config, messages, permissions, commands

| Area | Path | What it is | Action | Risk |
|---|---|---|---|---|
| Config | live `plugins/PhoenixSkyblock/pack.yml` on the VPS (not tracked; written by `PackConfig`) | Live settings of the module | Remove by hand on the VPS after the jar without the module is deployed | low |
| Messages | `modules/pack/src/main/resources/messages/pack.yml` + tracked `server/plugins/PhoenixSkyblock/messages/pack.yml` | `kick.declined`, `kick.failed`, `command.reloaded/status`, `status.*` | Remove both | low |
| Messages | `plugin/src/main/resources/messages/chat.yml` 76-108 and tracked copy | See section 2 (badge toggles, `rank-preview`) | Edit | low |
| Sounds | `plugin/src/main/resources/sounds.yml:17-22` and tracked `server/plugins/PhoenixSkyblock/sounds.yml:17-22` | `pack-sound:` section (`phoenix:ui.*`) | Remove from both. **Live file keeps it** (updates copy without deleting); after the code change the live keys would be read as ordinary sounds named `pack-sound.click`. Clean the live file | med |
| Permissions | `modules/pack/src/main/resources/permissions/pack.yml` | `skyblock.pack.reload`, `skyblock.pack.status` (default op) | Remove | low |
| Rank grants | `server/permissions/pack.txt` | `helper -> skyblock.pack.status`, `admin -> skyblock.pack.reload` | Remove the file. `apply-permissions.sh` only adds, so also run `lp group helper permission unset skyblock.pack.status` and the admin one on the live server (`PermissionFilesTest` fails the build if a granted node is undeclared) | med |
| Permissions | `modules/chat/src/main/resources/permissions/chat.yml:32-35`, `server/permissions/chat.txt:15-19` | `skyblock.chat.rankcolor` (Immortal picker), `skyblock.rank.preview` | Decide with the badge outcome | low |
| Commands | `/pack reload`, `/pack status <player>` registered in `PackModule.registerCommands` (tab completion: `Args.onlinePlayer`) | Staff commands | Remove (or keep `status` for v2) | low |
| Commands | `docs/commands/pack.md`; line 43 of `docs/COMMANDS.md` (generated: `./gradlew commandsIndex`) | Command docs | Remove file, regenerate index | low |
| Commands | `/rank preview <group> [name]` in `modules/chat` (`ChatModule.rankPreview`), `docs/commands/chat.md` lines 9 and 19 | Shows shine, still, large, simple, Java, Bedrock badge lines | Replace or remove | low |
| Commands | `/skyblock reload pack` | Not special-cased; module id based | Disappears with the module | low |
| Admin | `/admin` config editor | Lists `pack.yml` through `ModuleConfig`; no hand-written GUI | Disappears with the module | low |

## 4. Server side, hosting, build tools, art

| Area | Path | What it is | Action | Risk |
|---|---|---|---|---|
| server.properties | `server/server.properties:56-60` | `require-resource-pack=false`, `resource-pack=`, `-id=`, `-prompt=`, `-sha1=` all **empty**. The pack is sent by the plugin, not by vanilla | Keep (already clean) | none |
| Hosting | TCP **25580** (built-in `PackHost`) | Firewall rule on the VPS (opened by hand, not in any script; `grep` finds it only in docs) | Close the rule after removal | low |
| Hosting | DNS `pack.skybornmc.net` (RP-D5 idea, in the I7 card and RESOURCE_PACK.md) | Never created by code; `host.public-host` defaults to `play.skybornmc.net` | Nothing to remove; if the owner created a record, drop it | none |
| Hosting | live `plugins/PhoenixSkyblock/pack/` on the VPS | Extracted zip written by `PackFiles.extract` | Delete by hand | low |
| Scripts | `server/scripts/*.sh` (deploy, install, update, setup, restart-and-apply, apply-permissions) | **No pack lines.** Only `COPYFILE_DISABLE` comments ("never pack ._ files") and apt "Packages" | Keep untouched | none |
| Scripts | `server/scripts/apply-permissions.sh` | Adds, never removes, LuckPerms nodes | Keep; see `pack.txt` row | low |
| Build tool | `tools/build_pack.py` (275 lines, Pillow) | Builds both zips and `pack-ids.yml` into `plugin/src/main/resources/pack/`; `--check` mode (not run in CI: `.github/workflows/build.yml` only runs `./gradlew build`) | Remove | low |
| Build tool | `tools/sync_server_configs.py` | Syncs bundled yml and tracked server copies (newer commit wins) | Keep. Gotcha: edit bundled and tracked copies in the same commit or the newer old one wins | med |
| Pack source (built art) | `resourcepack/java/assets/phoenix/` (181 files, 952 KB): `textures/gui/menu` 31 backgrounds, `textures/item` 108 (menu buttons, 40 feature icons, 9 tabs, 4 currency, crate keys and blocks), `textures/font` 11 (currency glyphs, logo, badge sheets), `models/item/crate/block` 21, `font` 6 badge fonts, `sounds` 4 ogg | The generated pack content | Remove (git history keeps it) | low |
| Pack source (generators) | `resourcepack/src/` (32 files, 1.3 MB): `gui-art/*.js,*.py,*.json,*.txt`, `badge-font/badges.py+ranks.json`, `crate-art/crates.js+hinges.json`, `sounds/sounds.py`, `shaders/text.fsh`, `preview/*.png` | Art generators (need node, three.js, Python Pillow) and contact sheets | Remove (or archive in a branch/tag first: the owner drew the look and may want to reuse the palette) | low |
| Pack meta | `resourcepack/README.md`, `resourcepack/VERSION` (1.0.0) | Folder docs, version | Remove | low |
| Bedrock / Geyser | `server/plugins/Geyser-Spigot/config.yml` (`force-resource-packs: true`, `enable-integrated-pack: true`, `resource-pack-urls: []`) | Geyser's own defaults. **No** `custom_mappings`, `geyser_mappings` or `.mcpack` exists in the repo (PACK2 was never built) | Keep | none |
| Bedrock | `server/plugins/floodgate/config.yml` | Not pack related | Keep | none |
| Compat | `server/plugins/ViaBackwards/config.yml:3-5, 42-43` (`pass-original-item-name-to-resource-packs`, `map-custom-model-data`), `server/plugins/ViaVersion/config.yml:149-152` (`forced-use-1_17-resource-pack: false`) | Stock options, unrelated to our pack | Keep | none |
| Branding | `branding/*` (logos, `concepts/`) | Source art. `build_pack.py` used `skybornmc-logo-128.png` as `pack.png` | Keep | none |

## 5. Docs, cards, queue

| Doc | Path | What it says | Action | Risk |
|---|---|---|---|---|
| Plan | `docs/plans/RESOURCE_PACK.md` (426 lines, 85 pack hits) | Master plan RP1-RP10, forced pack, Geyser, ids.yml, build script | Archive or replace by a short v2 plan | low |
| Plan | `docs/plans/RESOURCE_PACK_GUI.md` (708) + `resourcepack/src/gui-art/RESOURCE_PACK_GUI.template.md` (generated by `build_docs.py`) | Art v1 guide: ids, every menu background, wiring | Archive | low |
| Plan | `docs/plans/RESOURCE_PACK_ART_BRIEF.md` (686) | Art list the owner draws from (427 entries) | Archive (v2 needs a much shorter list) | low |
| Plan | `docs/plans/PACK_BUILD.md` (41), `docs/plans/PACK_BUILD_PROMPT.md` (110) | Build plan and the prompt for the pack session | Archive | low |
| Test | `docs/PACK_TEST_CHECKLIST.md` (46) | Java and Bedrock join/menu/badge checklist | Archive; write a short v2 one | low |
| Owner | `docs/OWNER_ANSWERS_PACK1.md` (72) | Decisions: badges, credits, crates, simpler icons, "crate chests and that's it". **Also holds the gems -> credits rename task** (still valid) | **Keep** (history + credits); add a header note that the pack part is superseded | low |
| Owner | `docs/OWNER_ANSWERS_GUI1.md`, `docs/OWNER_ANSWERS_ROUND1.md` (answer 33), `docs/OWNER_ANSWERS_ROUND3.md` (837), `docs/DECISIONS_2026-10-30.md` (lines 65, 80), `docs/OPEN_QUESTIONS.md` (133, 134, 654-656: RP-D1 forced, RP-D7) | "Forced pack skins all GUIs later" | Keep as history; add a superseding decision | low |
| Principles | `docs/OWNER_PRINCIPLES.md` principle 7 ("A **forced** resource pack ... will skin all GUIs, crates, sets and tools later"), `docs/FEATURES.md` lines 32 and 196 (I7 row), `docs/REVIEW.md:156-157` | Principle 7 contradicts the new wish; REVIEW.md:156 ("No custom-font GUI textures without a Bedrock fallback. Default: plain chest GUIs") is what the owner now wants | Edit principle 7 and the I7 row; keep REVIEW.md | med |
| Queue | `docs/QUEUE.md` line 15 `GUI-6`, line 54 `PACK1 ✅`, line 55 `PACK2`, line 95 `PLAN-RP`, line 136 `RP1` (also says "waits for art", stale since PACK1 shipped) | Items to close or supersede | Edit (mark superseded, never delete rows) | low |
| Backlog | `docs/BACKLOG.md:34`, `docs/LATER.md:6`, `docs/STATUS.md:68`, `docs/TEST_GUIDE.md` (49, 237-254), `docs/PLANNING.md` (43, 55), `docs/QUESTIONS_ROUND2.md:146`, `docs/QUESTIONS_ROUND3.md:90-95` | Mentions of the pack as upcoming | Edit | low |
| Design | `docs/design/GUI_DESIGN.md` (sections 2.1, 8, 9.6), `docs/design/README.md:12`, `docs/planning/MENU_GUI.md` (8, 33, 59), `docs/planning/TAB_LIST.md:12`, `docs/planning/MESSAGES_AND_COMMANDS.md:112`, `docs/design/sketches/gui-sketches.html` | "Fixed slots so the pack's background lines up", standard buttons as "custom pack icons" | Edit (the slot rules stay valid for vanilla) | med |
| UI style | `docs/UI_STYLE.md:43` (`charcoal`: "Dark backgrounds (resource-pack panels)"), `:469` (brand row "Resource pack glyph: the phoenix as a font glyph") | Only 2 spots assume the pack. Section 5 (GUIs) and 5.5 (feature icon map) are written for vanilla items and stay valid | Edit the 2 rows | low |
| Phases | `docs/phases/PHASE-0-FOUNDATION.md:62` (item_model per item id), `PHASE-4-DEPTHS.md:45`, `PHASE-7-PRODUCTION.md` (112, 298, 439), `PHASE-8-PVE.md` (133, 223, 268-269, 410, 453: Wyrm of the Deep waits for the pack), `PHASE-10-REWARDS.md:28` | "after the pack" dependencies | Edit; ask owner what replaces the Wyrm custom model | med |
| Plans | `docs/plans/ENCHANTS.md:469`, `WARDROBE.md:402`, `RANKS_TITLES_ACHIEVEMENTS.md:62` (badge glyph column, RP-D17) | Pack mentions in other plans | Edit | low |
| Commands doc | `docs/commands/pack.md`, `docs/commands/crates.md` line 14 ("Pack polish"), `docs/commands/chat.md` lines 9, 19 | See sections 2 and 3 | Edit | low |
| Misc docs | `docs/BUILDER_BRIEF.md:54` ("No resource pack is required to see any build correctly"), `docs/review/REVIEW3_DEVELOPER_BRIEF.md:173`, `CHANGELOG.md` (lines 5, 6, 111) | Already vanilla-first; changelog is history | Keep, add a removal entry later | low |
| Card | `features/I7.md` (Resource pack) | **Stale vs code**: says "No pack code or art exists yet"; code and art shipped in update120. Whole card describes the forced pack | Rewrite as "I7 vanilla+ pack v2" after the owner decides | med |
| Cards | `features/` B1, C1, D6, E1, E5, E11, F2, F9, G4, G8, G9, I4, I8, README mention the pack | E11 tools, F2 ranks (badges), G8 tab, G9 menus, I4 Wyrm are the ones with real dependencies | Edit with each card's review | low |
| Review | `feature-overview/CODE-MAP.md:107` (I7 -> pack, common/pack), `INDEX.md:107`, `GRAPH.md`, `ORDER.md`, `CROSS-CUTTING.md` (22, 35-39), `NEW-CARDS.md` (I9 GUI redesign, I10 credit shop "custom resource-pack look"), `LEDGER.md` (C1-R1-13, C1-R1-24, C1-R2-16), `OWNER_PROFILE.md:15`, `GLOSSARY.md`, `disagreements/C1.md:39`, `rounds/C1/*` | I7/I9 and ledger items built on the pack; C1-R2-16 says the credit shop is built "AFTER the resource-pack GUI redo (I7)" | Edit (CODE-MAP, I9, C1-R1-24, C1-R2-16 dependency) | med |
| Owner tool data | `tools/owner_sheet/**` (answers, seeds, `look-feel.json`, `build_sheet.py`...) | Historical question sheets that mention the pack | Keep (archive) | none |

## 6. Database and player data

| What | Where | Tied to the pack? | Action | Risk |
|---|---|---|---|---|
| Pack status (loaded, declined, failed) | `PackModule.statuses` ConcurrentHashMap | In memory only; no table, no migration (grep for `pack` in `db/` finds only the tutorial's `pack_given` = Starter pack, unrelated) | Nothing to migrate | none |
| Player settings | existing player-settings store: keys `reduced-motion`, `simple-badges`, `large-badges`, `rank-color` | Orphan rows if the toggles are removed | Leave (harmless) or purge with a one-time SQL | low |
| Crate key items already in player hands | item component `custom_model_data` strings `crate/key/<crate>` (written by `KeyItems.create`) | Inert without a pack. Keys carry a unique UUID in PDC, so they never stack by data; removing the write does not break redeem | Leave; optional strip when a key is read | low |
| Menu items | PDC `phoenix:menu_icon` on tagged menu items | Menu items are marked and deleted if they leave a menu | None | none |
| Audit | No audit category for the pack | none | none | none |

## 7. Tests that reference the pack

Counts are `@Test` methods. 65 pack-only tests in 11 files; run `./gradlew build` after each step.

| Test | Path | Tests | What it checks | Action |
|---|---|---|---|---|
| PackLogicTest | `modules/pack/src/test/.../PackLogicTest.java` | 8 | URL building, status mapping, kick rules, SHA-1, defaults (port 25580) | Remove with the module (the pure parts become v2 tests if kept) |
| PackHostHttpTest | `modules/pack/src/test/.../PackHostHttpTest.java` | 1 | Single-file HTTP routing | Remove or keep for v2 |
| PackIdsTest | `common/src/test/.../common/pack/PackIdsTest.java` | 5 | Parses the generated `pack-ids.yml` | Remove |
| BadgesTest | `common/src/test/.../common/pack/BadgesTest.java` | 20 | Font, simple, Bedrock badge builder (embeds a mini `pack-ids` with fonts `phoenix:badge*`) | Rewrite for the simple form |
| BadgeSettingsTest | `common/src/test/.../common/pack/BadgeSettingsTest.java` | 4 | `/settings` badge toggles | Remove or shrink |
| PrivateUseTest | `common/src/test/.../common/pack/PrivateUseTest.java` | 2 | Strip logic | **Keep** |
| PackSoundTest | `common/src/test/.../common/text/PackSoundTest.java` | 2 | `Sounds.pick` pack variant | Remove |
| RankBadgeKeyTest | `modules/chat/src/test/.../chat/RankBadgeKeyTest.java` | 2 | Badge cache key | Remove or rewrite |
| PackMenusTest | `plugin/src/test/.../PackMenusTest.java` (297 lines) | 11 | **Source scanner**: every menu class has a pack background, every icon id used in code exists in `pack-ids.yml`, hub models cover default buttons, title space maths | Remove. This test also **forces every new menu class into a pack background map**; deleting it frees new menus |
| KeyModelTest | `modules/crates/src/test/.../crates/KeyModelTest.java` | 1 | `keyModel("Phoenix") = crate/key/phoenix` | Remove |
| CrateHingeTest | `modules/crates/src/test/.../crates/CrateHingeTest.java` | 9 | Lid hinge maths for the pack models | Remove with `CrateHinge` |
| Partial (keep) | `TypedTextTest.privateUseGlyphsAreStripped`, `StarterWordsTest.surfacesDropPrivateUseGlyphs`, `NoteRulesTest.cleanDropsPrivateUseGlyphs`, `IslandChatRulesTest.limitDropsPrivateUseGlyphs` | 4 | Private-use strip on player text | Keep (they test `PrivateUse`, which stays) |
| Constructor edit | `modules/menus/src/test/.../HubButtonsTest.java:169-170` | 2 uses | `new ButtonLayout.Button(..., null, null)` has the `model` arg | Edit when the field goes |
| Guards | `plugin/src/test/.../DeadConfigKeysTest.java`, `PermissionFilesTest.java`, `YamlKeysTest.java`, `DecisionLedgerTest.java` | n/a | Fail the build if a setting is unread, a granted permission is undeclared, or a tracked yml has a key outside its typed config | Will catch leftovers: good safety net during removal |

No test needs a server. No CI step builds the pack.

## 8. What breaks, and what needs a vanilla fallback

Everything below already has a vanilla path for Bedrock and no-pack Java. "Needs work" means the path is vanilla but the pack-only branch must be deleted without losing behaviour.

| Surface | Today with pack | Without pack code | Needs work |
|---|---|---|---|
| Menu titles | Glyph background + parchment text; pack viewers get a reopen when the title changes | Plain title; `titleUpdateInPlace` works for everyone (the `hasPack(player)` exception in `redrawInPlace` goes) | Delete `viewTitle` pack branch |
| Menu frame | Frame panes become blank panes with a hidden tooltip (`framePanesPack`) | `filler()` panes with hidden tooltip already (vanilla path) | Delete cache |
| Buttons (hub, You, Travel, /is, shop tabs, currencies, back/close/info/help, confirm, number picker, paging) | PAPER + CMD string icon | Original vanilla item (stays tagged with a harmless PDC) | Delete `MenuIcons.render` and constants; keep or remove `tag` |
| Currency icons | `currency/money|crystals|shards|credits` | Vanilla items from `economy` config (`EconomyConfig.icon`) | Wave 1a credits rename touches `MenuIcons.currencyOf` (`gems` alias): remove, don't rename |
| Rank badges (chat, nametag, tab, `/list`, sidebar, format preview) | Font badge; simple and Bedrock otherwise | **Simple `[Name]` text in the rank colour, Bedrock legacy codes, optional gradient** | Re-home rank colours and Bedrock codes (fact 3); `RankBadges.enabled()` must stop meaning "pack present" |
| `/settings` Display tab | 4 badge entries | Reduced motion and Bigger text become meaningless | Delete 2 or 3 toggles, decide Rank colour |
| `/rank preview` | Shine, still, large, simple, Java, Bedrock | Only simple, Java, Bedrock make sense | Edit or drop |
| Sounds | `phoenix:ui.*` for pack viewers | Vanilla `sounds.yml` keys already play for everyone | Delete pack variant and the live `pack-sound` keys |
| Crate yard | Custom chest models (body + lid), lid swing, per-viewer block hiding, chime `phoenix:crate.chime` | Vanilla blocks from `physical.blocks` (barrel, chest, copper chest, ender chest, vault, ominous vault) plus holograms, dust, vanilla chime (`approach.fallback-sound`) | Delete displays; lid swing and shake go too (they only animate the displays) |
| Crate keys | `tripwire_hook` + CMD `crate/key/<crate>` texture | Plain tripwire hook with the key name and lore | Stop writing the CMD |
| Tool and skin looks | `ITEM_MODEL` from config (vanilla keys today) | Same | None (keep) |
| Kicks | Decline and fail disconnect screens | No prompt, no kick | None |
| Staff | `/pack status` to debug "why no badges" | Not needed | None |
| Unlisted vanilla-only risk | Menus designed for fixed slots so a background lines up | Slot layout stays valid; the dark "inner filler" look is now the whole GUI | Visual review of all 189 menus on Java and Bedrock after the cut |

Things that are **not** pack and must not be touched by a search-and-delete: Starter pack (`tutorial` `pack_given`, messages `pack.given`, `StarterContents.giveJoinPack`), `packed_mud` / `packed_ice` in shop and depths configs, `RecentPlacements.pack`, backpack, `Style.unsafeGlyphs` and `bedrock-safe-symbols` (Unicode symbol list, not the pack font), Geyser `enable-integrated-pack`, ViaVersion `forced-use-1_17-resource-pack: false`.

## 9. Keep for a simple pack v2, versus delete

Idea: a vanilla+ pack v2 would be a small zip with item textures only (no `font/`, no shader, no title glyph, no badge). It needs: send it, host or point to it, know whether a viewer has it, and a safe fallback.

| Piece | Verdict | Why |
|---|---|---|
| `PackStatuses` (status mapping and kick key) | **Keep** | Pure, tested, needed for any send/ack flow |
| `PackUrls` | **Keep** (drop the per-variant id) | URL and stable UUID |
| `PackHost` + `PackHostHttpTest` | **Keep if self-hosting stays** | 89 lines, no dependency on the old pack. Alternative: external `url:` (CDN) and delete it |
| `PackModule` send in configuration phase, `PackService.hasPack`, `/pack status` and `/pack reload` | **Keep the shape, rewrite the module** | Paper 26.3 configuration-phase API is already proven here. Decide forced vs optional (owner) |
| `PackConfig` | **Keep, trimmed** | Remove `variant`; add `sha1` and `file` (replace `pack-ids.yml zips`) |
| `PackFiles` | **Replace** | It validates against `pack-ids.yml` |
| `PrivateUse` + 4 call-site tests | **Keep** | Still prevents spoofing and Bedrock controller glyphs; costs nothing |
| `MenuIcons.tag` PDC + `MenuService.render` seam | **Decide** | Cheapest way to give a few buttons a retexture later without touching call sites. If v2 uses plain `item_model` on vanilla items, drop it |
| `Sounds.setPackViewers` hook | **Decide** | Only useful if v2 has sounds |
| `ItemDefinition.itemModel` | **Keep** | Already generic and unused |
| Tool `model:` in config | **Keep** | Works with vanilla keys today |
| Badges: simple + Bedrock forms, gradients, rank colours | **Keep, move to chat/ranks** | They are the fallback every player sees today |
| `BadgeSettings` Immortal colour picker, `BadgeGradients` | **Decide** | Not font based |
| `PackIds`, `PackTitles`, `Badges.font`, `BadgeOptions` pack flags, `MenuModels`, `CrateDisplays`, `CrateHinge`, `pack-ids.yml`, zips, `build_pack.py`, all `resourcepack/` art and generators, `PackMenusTest`, `PackSoundTest`, `KeyModelTest`, `CrateHingeTest`, the 3 badge settings | **Delete** | Pack-v1 specific (fonts, negative space, glyph maps, generated ids) |

## 10. Disagreements (docs vs code)

| # | Doc says | Code says |
|---|---|---|
| P1 | `features/I7.md`, `docs/QUEUE.md` RP1: "No pack code or art exists yet", "waits for art" | `modules/pack`, `common/pack`, 181 art files and 2 built zips are merged (update120, commits `f068761f`, `4ff213ae`, `40a3d3a1`...) |
| P2 | `docs/OWNER_PRINCIPLES.md` 7, `FEATURES.md:32`: forced pack skins all GUIs, crates, sets and tools | Owner (31 Oct) now wants no custom font and a vanilla+ restart; `REVIEW.md:156` already said "plain chest GUIs with good icons" |
| P3 | `OWNER_ANSWERS_PACK1.md` 7: the only custom items are the crate chests and keys | Code also retextures ~100 menu icons, 32 backgrounds, rank badges and sounds |
| P4 | `RESOURCE_PACK.md` RP-D2/RP-D5: pack at `pack.skybornmc.net`, nginx | Plugin self-hosts on TCP 25580; no DNS or nginx exists |
| P5 | `resourcepack/README.md`: "Not here yet: pack.mcmeta, the generated model and font JSON, the Bedrock pack, sounds" | `tools/build_pack.py` generates all of it (sounds exist; the Bedrock pack and Geyser mappings really are missing: PACK2 never built) |
| P6 | `PackService.Status.BEDROCK` javadoc: "text fallback and Geyser mappings" | No Geyser mapping exists (PACK2 unbuilt); Bedrock gets the text fallback only |

## 11. Recommended removal order (safe steps)

Each step ends with `./gradlew build` and is its own commit. Use `q/pack-removal` in a worktree. Never start a test server without the owner.

0. **Rehearse, no code:** on the VPS set `pack.yml enabled: false`, `/pack reload`. Check Java and Bedrock join, menus, chat ranks, crate yard. This is the rollback switch and shows what players see. (Needs the owner's OK to edit the test-server config; restore afterwards.)
1. **Decide first (owner):** simple rank look and where rank colours live (style.yml vs LuckPerms meta); keep the Immortal picker?; forced or optional v2 pack; keep `MenuIcons.tag` seam?; what replaces the Wyrm and crate chests. Write them to `OPEN_QUESTIONS.md`.
2. **Re-home the rank data** (before anything is deleted): move colour, gradient and `bedrock` code per group from `pack-ids.yml badges` into `style.yml` (or LP meta); make `BadgeService` independent of `PackService`; keep `Badges` simple and Bedrock forms; update `BadgesTest`. Add `removedKeys` for dropped settings.
3. **Cut the consumers, leaves first** (each can ship alone):
   a. crates: delete `CrateDisplays`, `CrateHinge`, `CrateHingeTest`, `onPackStatus`, `displays.*`, `lid.*` (shake and swing), `approach.pack-sound`, key CMD, `KeyModelTest`; update `docs/commands/crates.md`;
   b. sounds: drop `pack-sound`, `DEFAULT_PACK`, `setPackViewers`, `PackSoundTest`, both `sounds.yml` files; clean the live file;
   c. menus: `MenuService` pack branches (`viewTitle`, `render`, `framePanesPack`, `redrawInPlace`), `PackTitles`, `MenuModels`, the `model` field on `Button` and `Warp`, `Nav.icon`, `PackMenusTest`; then either delete the ~20 `MenuIcons.tag` call sites or leave `tag` as a no-op for one release;
   d. badges UI: delete the three font toggles, `/rank preview` pack lines, `chat.yml` texts.
4. **Remove the provider:** `SkyblockPlugin` lines 141-142 and 271-272, `plugin/build.gradle.kts:47`, `settings.gradle.kts:59`, `modules/pack`, `common/pack/PackService` and `PackIds`, `pack-ids.yml`, both zips, `permissions/pack.yml`, `server/permissions/pack.txt`, `messages/pack.yml` (both copies), `docs/commands/pack.md` + `./gradlew commandsIndex`. (If v2 keeps a module shell, do this as a trim instead of a delete.)
5. **Remove the art and tools:** `tools/build_pack.py`, `resourcepack/` (tag the commit first, e.g. `pack-v1-final`).
6. **Live server clean-up (owner or deploy key):** `lp group helper permission unset skyblock.pack.status`, `lp group admin permission unset skyblock.pack.reload`; delete `plugins/PhoenixSkyblock/pack.yml`, `pack/`, `messages/pack.yml`, the `pack-sound` block in `sounds.yml`; close TCP 25580; check `server.properties` pack lines stay empty.
7. **Docs:** edit principle 7, I7 card (rewrite), I9, `QUEUE.md` (mark GUI-6, PACK1, PACK2, RP1 superseded, do not delete rows), `FEATURES.md` I7, `UI_STYLE.md` lines 43 and 469, `GUI_DESIGN.md` section 8, phases that wait for the pack, `CODE-MAP.md`, `CHANGELOG.md`; archive the six `docs/plans/*PACK*` / `RESOURCE_PACK*` files and the checklist with a "superseded" banner.
8. **Verify** (owner checklist, short): see the list below.

## 12. Must still work without any pack (vanilla fallback list)

Java and Bedrock, no prompt, no download, no kick:
1. Join, tutorial and portal flow (no config-phase wait; join time drops by the pack download).
2. `/menu` hub, You, Travel, `/is`, `/shop` (tabs and buy page), `/sell`, number picker, confirm dialogs, paging, search, `/top`, `/admin` and staff menus: readable titles, working Back, Close, Help, Main menu; frame panes without tooltip; chat prompt and Bedrock forms unchanged.
3. Currency lines in menus (money, crystals, shards, credits) with a vanilla icon each.
4. Rank display everywhere (chat, nametag, tab, `/list`, sidebar, hover, format preview): same colour on every surface, rename in LuckPerms still shows, Bedrock shows legacy-code text, no private-use characters ever reach a client.
5. Crates: pedestal blocks, hologram "CLICK to open", reward show, previews, key items (named tripwire hook with lore), key redeem, dust and chime with the vanilla sound.
6. Tools and skins (`ITEM_MODEL` with vanilla keys), boosters, money notes, wardrobe sets (vanilla materials, trims, dye).
7. Sounds: click, success, error and event sounds from `sounds.yml` (vanilla keys); `/settings` sound toggles.
8. Staff: `/admin` config editor no longer lists `pack.yml`; `/skyblock reload` has no `pack` module; permission and command indexes stay valid; `./gradlew build` green (permission files, dead-key and yml-key tests).
9. Player text safety: `PrivateUse.strip` still applied on chat, nicks, notes, island chat, signs, books, typed menu input.
10. Server start: no web server on 25580, no log line about the pack, no "missing sound pack-sound.*".

## 13. Questions for the owner (not blocking the audit)

1. Rank badges: are plain coloured `[Name]` tags (with the gradient for Immortal) the new look, or do you want something different? The colours and Bedrock codes of every bought and won rank must be re-homed either way.
2. Pack v2: still forced at join, optional with a prompt, or not sent at all until you have new art? (Decides whether `PackModule` is trimmed or deleted.)
3. Keep the `MenuIcons.tag` seam so a few buttons can be retextured later, or switch to plain `item_model` on vanilla items when v2 art exists?
4. Crate chests and keys: with no models, do you want a vanilla distinction by block per crate (already the case) and a named key only?
5. Keep the sound idea (custom UI sounds) for v2, or vanilla sounds only?
6. Archive the generators and art under a git tag, or delete them outright? (History keeps them either way.)
