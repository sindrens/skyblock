# Build spec GUI-ICONS-FINAL: merge the icon redesign, apply the owner's icon choices, plain Nether Star, lit Depths

Cards: I9 v3 (Locked 1 Nov), I7 v4 (Locked 1 Nov), G9 v3 (hub), A3 (tutorial). Owner answers: `rounds/OWNER_ANSWERS_2026-11-01.md` (V3-Q6 a, V3-Q7 a, C4-Q4 note "make the Depths light up with the resource pack in the tutorial"). Written 1 Nov 2026 for the `coder` agent. No code in this file. **Run this spec first** (it is small); GUI-FONT and MENUS-V3 start from its branch.

## 0. Goal and scope
**Goal:** the live menus use the new icon set (`q/gui-icons2`): our own cartoony icon only for things that are not a real Minecraft item (currencies, Back/Prev/Next/Close, danger, confirm/cancel, buy/sell, state switches, Bank, Top, Auction), plain vanilla items for everything else. The hotbar menu item is a plain vanilla Nether Star. While the tutorial step is `depths`, the Depths tile lights up. The pack zip and its SHA-1 are regenerated.

**Owner picks applied (all are the defaults already live in the icons2 catalogue; this spec adds a test that pins them):**
| Keys | Style | Picture |
|---|---|---|
| `nav/back`, `nav/prev`, `nav/next`, `nav/close` | **B** | little wooden door / pointing glove left / pointing glove right / red cross in a round button |
| `state/confirm`, `state/cancel` | **C** | round lime button with a tick / round red button with a cross |
| `shop/buy`, `shop/sell`, `shop/sell_all` | **C** | shopping cart / coin stack / two coin stacks |
| everything else custom | one look | see `docs/design/ICON_MAP.md` |
| `menu` | **removed** | hotbar star = plain vanilla Nether Star (no key, no PNG, no variant) |
| `feature/depths_lit` | **new** | iron pickaxe with an aqua glow ring and sparkles (tutorial highlight) |

**In scope:** merge, catalogue edit (remove `menu`, add `feature/depths_lit`), call-site swaps, hotbar star, tutorial highlight, regenerate pack, tests, docs.
**Out of scope:** the frame font (GUI-FONT), menu layouts (MENUS-V3), art by the owner (the generator sprites are placeholders; the owner overwrites PNGs by name).

## 1. Assumptions (verified in the code on 1 Nov)
- `q/gui-phase2` (worktree `skyblock-wt-tutfix`, deployed) and `q/gui-icons2` (worktree `skyblock-wt-icons2`, one commit `b8311ef7` on top of `fa084810`): `git merge-tree --write-tree q/gui-phase2 q/gui-icons2` reports **no conflicts** (CHANGELOG merges cleanly).
- icons2 already changes `common/.../icon/IconCatalog.java` (new `vanilla` map, `isCustom`, `vanilla(key)`) and `Icons.java` (`item()` tags only custom keys). Every icon key literal in Java and yml on `q/gui-phase2` exists in the icons2 catalogue (checked with a script: 0 missing). So **no menu code needs a key rename**; keys are stable by design.
- icons2 catalogue today: 27 custom keys (one of them `menu`, style `vanilla`, no PNG) and 49 plain vanilla keys; live styles are already B (nav), C (confirm/cancel, buy/sell/sell_all).
- Tools are called, never edited by hand: `tools/build_icons.py` (`art`, `use`, `pack`, `sheet`, `fetch-vanilla`, `verify-jars`, `check`), `tools/icons_lib/sprites.py` (the 16x16 pictures), `tools/icons_preview.py` (owner preview html/png).
- `TutorialService#currentStep(UUID)` (common) returns the id string `depths` while the tutorial wants the player to go to the Depths (`Steps.DEPTHS`).

## 2. Ordered steps
1. **Branch and merge.** New worktree `q/gui-icons-final` from `q/gui-phase2`; `git merge q/gui-icons2` (clean). Build once (`./gradlew build`) to prove the merge compiles before any edit.
2. **Catalogue: remove `menu`.**
   - `resourcepack/icons.json`: delete the `menu` row. `resourcepack/variants/menu/` (the `ember.png`) deleted.
   - `tools/icons_lib/sprites.py` and `art.py`: delete the `menu` sprite function and its dispatch entry; `tools/icons_preview.py`: delete the `menu` card (grep `'menu'`).
   - `build_icons.py` itself is not changed (the `vanilla` variant style stays a supported feature for other keys).
3. **Catalogue: add `feature/depths_lit`.** Row: `{"key":"feature/depths_lit","base":"iron_pickaxe","name":"The Depths (lit)","meaning":"depths","file":"feature_depths_lit","slice":3,"motif":"iron pickaxe with a pulsing aqua glow ring and three white sparkles","used":"tutorial: hub Depths tile and Travel Depths gate while the step is depths"}`.
   - Sprite: new function in `tools/icons_lib/sprites.py` (16x16, same palette helpers as the other sprites): the vanilla-looking iron pickaxe silhouette (so it still reads as "the Depths pickaxe"), a 2 px glow ring in steel sky `#5AB4E5` at 60% alpha around it, three sparkle pixels in white. Static (no `.mcmeta` animation: `check` requires square PNGs). Dispatch entry in `art.py`.
   - `python3 tools/build_icons.py fetch-vanilla` (adds the `iron_pickaxe` base to `vanilla-fallback.json`; network or `--jar`), then `art`, `pack` (writes `assets/phoenix/models/item/icon/feature_depths_lit.json` and `assets/minecraft/items/iron_pickaxe.json`), `sheet`, `check` (must print OK).
   - `python3 tools/build_icons.py verify-jars --jar <1.21.5 client.jar> --jar <1.21.11 client.jar> --jar <26.3 client.jar>` must print OK (the jars are in the owner's launcher folder `~/Library/Application Support/minecraft/versions/`; if one is missing the coder asks the owner, never skips). This is the check that stops a purple-and-black cube (the `red_bed` bug).
4. **Hotbar star, `modules/menus/.../MenuItem.java`.**
   - Delete `static final String ICON = "menu"` and the `menus.icon(...)` branch in `create(Player)`; the item is `new ItemStack(base)` (material from `menu-item.material`, default `nether_star`) named `menu-item.name`, lore `menu-item.lore`, PDC marker and `HIDE_ATTRIBUTES, HIDE_ENCHANTS` kept. No model string for anyone.
   - Glint: remove the unconditional `setEnchantmentGlintOverride(true)`; call it only when the new config key `menu-item.glint` is true (`MenusConfig`, path `menu-item.glint`, default **false**, desc "Force the enchant glint on the menu item. The vanilla Nether Star glints by itself; set true only if it does not on your client version."). Manual check on the test server (checklist item 1): the plain star glints. If it does not, set the key to true in the live file (no code change).
   - Delete the `onPackStatus` handler (the star no longer changes with the pack) and its import.
   - Messages unchanged (`menu-item.name` flame gold `Menu`, lore `» CLICK to open the menu`).
5. **Call-site swaps (only these; everything else keeps its key).**
   | File | Change |
   |---|---|
   | `common/.../menu/NumberPickerMenu.java` line 108 | `Material.NAME_TAG` item for "Type an amount" becomes `service.icon(viewer, "ui/type", name, lore)` (pencil, base `name_tag`) |
   | `modules/shop/.../BuyMenu.java` line ~112, `SellItemMenu.java` line ~202 | the clickable "Type an amount" tile (`Material.NAME_TAG`) uses `ui/type` the same way; the greyed `GRAY_DYE` state stays |
   | `modules/auction/.../AuctionMenu.java` line ~164 | the Search button uses `nav/search` (base `ender_eye`) instead of `cfg.icon("search", OAK_SIGN)`; `auction.yml icons.search` stays readable but is ignored (log once: "icons.search is ignored, the icon is nav/search"). Clear-search stays a barrier (a different base item on the same screen) |
   | `modules/menus/.../HubMenu.java` | none (`nav/help` already) |
   `ui/info` is **reserved** (no call site yet; first use is the plaque-less info tiles of later menus). `currency/*`, `shop/*`, `state/*`, `island/*`, `travel/*`, `feature/*` need no edit.
6. **Tutorial highlight (Depths lights up).** Module `menus`:
   - New pure class `Highlights` (`modules/menus/.../Highlights.java`): `record Rule(String target, String step, String icon)`; `static Optional<Rule> pick(List<Rule> rules, String target, boolean tutorialActive, Optional<String> currentStep)` returns the first rule whose `target` equals and whose `step` equals `currentStep` while `tutorialActive`. Parsing of `menu.yml` section `highlights:` (below) with problems logged, unknown icon key skipped.
   - `menu.yml` new top-level section (defaults):
     ```
     highlights:
       hub-depths:  {target: "hub.depths",          step: depths, icon: feature/depths_lit}
       gate-depths: {target: "travel.depths-gate",  step: depths, icon: feature/depths_lit}
     ```
   - `HubMenu.render(...)`: before `nav.icon(viewer, button.icon(), ...)` ask `nav.highlight(viewer, "hub." + button.id())` (new `Nav` method: reads `TutorialService` from `core.services().optional(...)`, `isActive(player)` and `currentStep(uuid)`); when a rule matches use `rule.icon()`, add the glint (`setEnchantmentGlintOverride(true)`) and one extra lore line before the action line: new message `hub.highlight` = `"<c:action>● Start here</c:action>"` (bold flame, Bedrock-safe `●`). **Everyone gets the glint and the line; only Java with the pack LOADED also gets the lit picture** (Bedrock and no-pack see the plain iron pickaxe with glint, still clearly marked).
   - `TravelMenu` does the same for the place id `depths-gate` (`travel.depths-gate`).
   - Live: `HubMenu.tick()` (every 40 ticks) also compares `litNow` with the value used for the last build and calls `refresh()` when it changed, so the tile lights up when the previous step is done and goes normal when the step ends. `TravelMenu` has no tick; it is rebuilt on every open.
   - No rule matches when the tutorial module is off or the step is anything else. The tile still does exactly what it did (`dungeon` command / travel).
7. **Regenerate and verify the pack.** `python3 tools/build_icons.py art && pack && sheet && check`, `python3 tools/icons_preview.py` (writes `feature-overview/inspiration/icons-v2-preview.html` and the png; the owner reviews them), `./gradlew build` (the `modules/pack` `packZip` task builds `phoenix-icons.zip`; its SHA-1 is the pack version, so clients download it again). On the test server: `/pack reload`, then `/pack status`.
8. **Docs.** `docs/design/ICON_MAP.md` is **the contract** and is updated in this branch: header count "27 custom keys, all live"; delete the `menu` row (add a line under Notes: "The hotbar menu item is the plain vanilla Nether Star; there is no `menu` key"); add the `feature/depths_lit` row; confirm the nav/confirm/buy rows show B or C in bold as live; plain vanilla table unchanged (49 keys). `resourcepack/README.md` (menu star sentence removed, depths_lit mentioned), `docs/UI_STYLE.md` section 5.5 (one-line pointer to `ICON_MAP.md`), `docs/FORMAT.md` section 6 (the `menu` row is already struck; add `feature/depths_lit`), `CHANGELOG.md` ("Icons v3: ... plain Nether Star, lit Depths in the tutorial"). `python3 tools/sync_server_configs.py` and commit the tracked copies of `menu.yml`, `messages/navigation.yml`, `navigation.yml`.

## 3. Config keys
| File | Key | Default | Note |
|---|---|---|---|
| `navigation.yml` (`MenusConfig`) | `menu-item.glint` | `false` | force the glint on the star |
| `menu.yml` | `highlights.<id>.{target,step,icon}` | the two rules above | tutorial highlight rules; a rule with an unknown icon key is skipped with a console line |

## 4. Messages (`modules/menus/src/main/resources/messages/navigation.yml` + tracked copy)
`hub.highlight: "<c:action>● Start here</c:action>"` (the only new text). `menu-item.*` unchanged.

## 5. Permissions, commands, tab completion
None new. (`/pack reload` and `/pack status` exist; no argument changes.)

## 6. Tests (names; no server; run `./gradlew build`)
| Test | Where | What |
|---|---|---|
| `IconCatalogTest` | `common` | the bundled `icons/icons.json` parses with 0 problems; 27 custom keys + 49 vanilla keys; key `menu` is absent; `feature/depths_lit` is custom with base `iron_pickaxe`; every custom base is a real item name |
| `IconStylesTest` | `common` | pins the owner picks: `nav/back`, `nav/prev`, `nav/next`, `nav/close` style `B`; `state/confirm`, `state/cancel`, `shop/buy`, `shop/sell`, `shop/sell_all` style `C` |
| `IconKeysUsedTest` | `plugin` (scans `../modules`, `../common` like `PermissionFilesTest`) | every icon key literal in `*.java` main sources and every `icon:` value in `menu.yml` is a catalogue key or a vanilla item id; the string `"menu"` is not used as an icon key |
| `ScreenBaseItemsTest` | `menus` | for the hub, You and Travel layouts of the bundled `menu.yml`, resolved through the catalogue, no two tiles of one screen share a base item |
| `HighlightsTest` | `menus` | `pick`: matching target and step while active returns the rule; wrong step, inactive tutorial, unknown target return empty; two rules, first wins |
| `MenuItemGlintTest` | `menus` | pure helper `MenuItem.glint(boolean configured)`: false by default, true only when configured |
| existing `HubButtonsTest`, `TravelLayoutTest` | `menus` | stay green |
Python: `build_icons.py check` and `verify-jars` as in step 3 and 7 (part of the coder's Definition of Done, not of Gradle).

## 7. Manual checklist (owner; Java 26.3 with the pack, and a Bedrock phone)
Java:
1. Hotbar slot 9 is a plain Nether Star named `Menu`, it glints, no custom picture; click opens `/menu`.
2. `/menu`: Home is a red bed (no purple cube), Travel a compass, Island a grass block, Depths an iron pickaxe, Sell a gold nugget, Shop an emerald, Bank/Top/Auction show our icons.
3. Any menu: Back = door, page buttons = gloves with the page number as count, Close = red round cross; confirm screens = round lime / red buttons; shop Buy = cart, Sell = coin stack, Sell all = two stacks; number picker "Type an amount" = pencil.
4. New account in the tutorial: when the step says go to the Depths, the Depths tile in `/menu` and in Travel glows with the aqua ring and says `● Start here`; after the step it is the normal pickaxe again (re-open or wait 2 s).
5. `/pack status` shows you as loaded; `/pack reload` re-sends the new pack once.
Bedrock:
6. Same menus show the plain base items (no cubes); the Depths tile is an iron pickaxe with glint and `● Start here` during the step; hotbar star is the normal star.

## 8. Risks and rollback
| Risk | Answer |
|---|---|
| A base item definition differs between Minecraft versions and our override breaks it (purple cube) | `verify-jars` against 1.21.5, 1.21.11 and 26.3 is mandatory before every pack release; custom icons only sit on bases that are identical in all three |
| Real items (tools, crate keys) that share a base item with a custom icon (`iron_pickaxe`, `bell`, `emerald`...) | the only code that sets `custom_model_data` is `common/.../icon/Icons.java` (grep, 1 Nov); our item definition selects only our exact key strings and falls back to the vanilla model for every other stack |
| Owner dislikes a variant | `python3 tools/build_icons.py use <key> <A|B|C>`, `pack`, `check`; no Java change |
| The star does not glint | `menu-item.glint: true` |
Rollback: `git revert` the merge commit (the old pack zip comes back; menus keep working because keys are stable). `highlights:` removed from `menu.yml` switches the tutorial highlight off with no deploy (`/skyblock reload menus`).

## 9. Size
**S to M**: 1 merge, 1 catalogue edit, 1 sprite, 1 small class, about 6 call-site edits, 6 tests, docs. About 1 working day. Deploy lane: build, `server/scripts/deploy.sh`, `md5sum` of the jar on the VPS, `/pack reload`.
