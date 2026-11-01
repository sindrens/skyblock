# Resource pack v2: vanilla+ · id: I7 · status: Locked · version: 3

Cluster GUI-PACK (restart, 31 Oct 2026), with I9 (GUI standard), G9, G7, G8, G1, A3, A5. All questions (GUI-Q1 to GUI-Q3, GUI-Q16) are answered and folded in; ledger ids `I7-R1-01` to `I7-R1-07`. Locked 31 Oct 2026. Pack v1 inventory for removal: `feature-overview/disagreements/PACK-V1-AUDIT.md`. Build spec for phase 0: `feature-overview/build-specs/GUI-PHASE0.md`.

## In one sentence
Pack v1 is deleted at once and the server runs on pure vanilla; later a small **pack v2** (our own simple icons repainted on real vanilla base items, plus at most one optional chest background) is **forced for Java players** and asked for after they have joined, so Bedrock and anyone without the pack still see the same menus on the normal items.

## How it works

### 1. Pack v1: removed completely (phase 0; exact files in PACK-V1-AUDIT.md and the build spec)
| Part of v1 | Where it lives | Verdict |
|---|---|---|
| 31 menu backgrounds drawn through negative-space font titles (fonts `phoenix:menu`, `phoenix:space`) | `resourcepack/java/.../gui/menu`, `common/menu/PackTitles`, `pack-ids.yml` `menu.*` | **Remove** |
| Rank badge fonts (3 fonts) and the moving-shine shader | `common/pack/Badges*`, `BadgeService`, `chat/RankBadges`, `/rank preview` pack forms, badge switches in `/settings` | **Remove the fonts and shader.** The colour, gradient and Bedrock code of every rank move FIRST into the chat module config `ranks.looks` (step 1 of the build spec); a rank then shows as plain coloured `[Name]` everywhere |
| Currency and logo font glyphs (U+E200 and up) | `textures/font/icons`, `pack-ids.yml` glyph entries | **Remove**; text symbols from `style.yml` |
| 41 button, 40 feature and 9 shop-tab icon models, applied by swapping the item to PAPER | `textures/item/menu`, `MenuIcons.render`, `MenuModels` | **Remove the art, the swap and `MenuIcons`**; v2 redraws icons on the real base item (section 2) |
| 4 currency item icons | `textures/item/currency` | **Remove and discard** (owner GUI-Q9: forget the old icons) |
| 7 crate chest 3D models (21 model files), 7 keys, lid animation rigs | `textures/item/crate`, `models/item/crate`, `crates/CrateDisplays`, `CrateYardFx` | **Remove all**; crates stay real vanilla blocks (F5 picks the block that exists in 1.21.5 for the copper chest); keys become icons in phase 3 |
| 3 UI sounds and 1 crate sound (`pack-sound`) | `resourcepack/.../sounds` | **Remove**; vanilla sounds only |
| Two bundled zips, the generator sources and the 427-entry art list | `plugin/src/main/resources/pack/`, `resourcepack/src/`, `tools/build_pack.py` | **Remove from the tree**; kept in git history under tag `pack-v1-final` |
| Pack web server, sender, `/pack reload` and `/pack status`, `pack.yml` | `modules/pack` | **Remove the whole module and `PackService` in phase 0** (owner GUI-Q3: pack v1 completely). Phase 1 builds a fresh small module and may copy the generic pieces (`PackHost`, `PackUrls`, `PackStatuses`) from the tag |
| Planned but never built: worn set looks, HUD bars, world border texture, Geyser converter, Bedrock pack | old I7 text, RESOURCE_PACK.md | **Dropped from the plan** |

### 2. What pack v2 contains (small on purpose)
- **Item icons only.** One 16 x 16 (or 32 x 32) PNG per icon, a model file per icon, and one `assets/minecraft/items/<base item>.json` per base item that selects the model by the `custom_model_data` string and falls back to the normal vanilla model.
- **Our own new icons (owner GUI-Q9):** the v1 art is discarded. Every icon looks like a vanilla Minecraft item, simple, easy to understand and readable at a glance by every kind of player; the base item stays the same, only the look is ours.
- **Planning size of the first full release: 50 icons, under 100 KB** (v1 was 212 KB with fonts, 3D models and sounds).
| Group | Icons | Base items |
|---|---|---|
| Menu item (the hotbar star) | 1 | nether star |
| Currencies | 4 | gold ingot (money), amethyst shard (crystals), prismarine shard (shards), diamond (credits) |
| Navigation and states | 13 | arrow (Back), paper (page), barrier (Close), TNT (danger), lime and red concrete (confirm, cancel), lime and gray dye (on, off), gray dye (locked), clock (loading, undo), cobweb (empty), spyglass (auction search), book (hub Help) |
| Hub, You and Travel features (the map in I9 section 9) | 23 | red bed, grass block, iron pickaxe, ender pearl, gold nugget, emerald, barrel, gold block, smithing table, experience bottle, enchanting table, leather chestplate, chest, golden helmet, armor stand, name tag, totem, comparator, lectern, writable book, bookshelf, campfire, compass ... |
| Shop tabs | 9 | one per tab (C5) |
- **Later, optional (phase 3):** 7 crate keys (base item tripwire hook), tool tier and skin icons once E10 locks its list, mastery tree node icons once B14 locks its layout. Skins are a Java bonus: Bedrock sees the base item, so nothing paid may depend on being seen through the pack (developer decision below).
- **Nothing else in the pack:** no fonts, no glyphs, no shaders, no sounds, no language files, no armour or HUD changes, no per-menu backgrounds.
- **Rule for each icon:** the vanilla base item and the item name explain it alone (I9 section 7). The pack only makes it ours.
- Technical note (checked against the code): the pack icon is a `custom_model_data` string on the real base item, sent only to viewers whose status is LOADED. It is **not** `item_model` (a client without the pack would draw the purple-and-black missing model). `item_model` is allowed only toward another vanilla model (the gold tier tool uses `minecraft:golden_pickaxe` today).

### 3. The one optional container background (phase 2, only if the owner wants it after seeing phase 1)
- One replaced texture: `assets/minecraft/textures/gui/container/generic_54.png`. Every chest-sized screen draws from it: all our menus, real chests, ender chests and barrels, for any number of rows, **including the player-inventory half**. It cannot differ per menu, per row count or per player group; empty slots are still drawn as slot cells.
- So it can only be a **global tint and frame**: same slot grid, no text, no logo, light enough for the dark-gray vanilla title and for item names to stay readable.
- Decided: icons first, background later (GUI-Q1, owner). Phase 2 is a separate owner decision with the icons already seen on a real screen.

### 4. Forced for Java, asked after joining (GUI-Q2, owner)
- **The pack is forced for Java players** ("yes and will ask when the player has joined the server"). The prompt is sent **after the player has joined**, not before joining and not in the configuration phase. This changes the old detail "asked before joining" (answer 77, OWNER_PRINCIPLES 7); "forced" stays (answers 33 and 77).
- **Declining, or a failed download, kicks the player** with a friendly message that says why the pack is needed (it only repaints the icons, about 100 KB) and exactly how to try again (turn on Server Resource Packs for this server, then rejoin). The kick is never silent and never staff-alerted as an incident.
- **Bedrock cannot be forced:** Bedrock (Floodgate) players are never sent the pack and play without icons on the normal items.
- **Join order (default, owner may veto):** the player joins the spawn world, the pack prompt shows at once, and the join flow of A1 (welcome, tutorial start) begins when the answer is LOADED, or after 15 s if the client never answers. Player data is saved before a kick, so a tutorial in progress resumes after the retry.
- **Fail-open safety (developer decision):** if the pack host itself is unhealthy (a self-check every 60 s cannot fetch its own file, or the file is missing), the server stops requiring the pack until it is healthy again and alerts staff once. A broken host can therefore never lock everyone out.
- **Switch:** `pack.required` (default true) in the phase 1 module config; set false and reload to make it an optional offer without a kick. Nothing is sent and nobody is kicked before phase 1 exists (phase 0 is pure vanilla).
- Because everything works on the base items, a player who somehow has no pack (Bedrock, staff test, fail-open) sees the same menus.

### 5. Hosting and reload (phase 1, fresh module)
- A new small `modules/pack` (config `pack.yml`: `enabled`, `required`, `url`, `host.port`, `host.public-host`, `prompt`, `timeout-seconds`). It serves one zip bundled in the plugin jar with its SHA-1 from a tiny built-in web server (design of v1's `PackHost`, no CDN, no DNS record), or from an external `url:` set in config.
- Sender: after `PlayerJoinEvent` (Paper `Player#sendResourcePacks` with `required` true and the prompt); statuses LOADED, DECLINED, FAILED, BEDROCK, PENDING tracked in memory only; `PackService.hasPack(viewer)` is the one gate for icon rendering.
- The pack id never changes; the version (SHA-1) does, so clients download only after a real change. Online players get a changed pack on `/pack reload` with a one-line notice.
- Pack format: `pack.mcmeta` supports 1.21.5 to the current client (the format range is looked up in phase 1; icon files use only base items that exist in 1.21.5, C1-R2-24).

### 6. Bedrock
- Bedrock players are never sent the Java pack (Floodgate check). They see chest menus with the **vanilla base items** and our coloured names.
- Custom icons on Bedrock would need Geyser custom-item mappings plus a Bedrock pack. That is not planned; if the owner later wants it, it is a separate phase after launch. Every icon must therefore be readable by its base item and name (I9 section 7).
- The owner or one tester checks each pack release on a Bedrock phone (decided 78): joins, menus on base items, no prompt.

### 7. Fallback ladder
Pack loaded (Java) -> our icon on the base item. Bedrock, `pack.required` off and declined, still downloading, fail-open, pack module off -> the base item itself. Same menu, same slots, same text. `PackService.hasPack` stays the single gate.

### 8. Who draws what
- **Owner:** draws every PNG himself (original art only; no copied or traced Mojang textures), at 16 x 16 or 32 x 32 on a transparent background, in the "custom looking vanilla" style: simple shapes, strong silhouettes, readable at a glance.
- **Developer:** icon ids, model and `items/*.json` files, the build and zip, SHA-1, hosting, tests, the vanilla fallback in code, and an `icons.yml` list (id, base item, where used) that tells the owner exactly what is missing. A contact sheet of all base items (the plain look) is produced so the owner draws against the real background.

### 9. Delivery order
| Phase | What | Gate |
|---|---|---|
| **0** | Remove pack v1 completely (section 1), move rank colours first, run **pure vanilla**: no prompt, no kick, no download, no pack module | none; the game is complete without a pack |
| **1** | New icons. **First slice, 9 icons:** menu item (nether star), the 4 currencies, Back, page, Close and the danger icon (TNT). Check them in game on Java and Bedrock, then the hub, You and Travel features, navigation states, shop tabs. The forced prompt (section 4) is switched on with the first slice | the owner has drawn the first slice |
| **2** | Optional one container background | the owner wants it after seeing phase 1 |
| **3** | Optional: crate keys, tool tier and skin icons, mastery node icons | E10, F5 and B14 are locked |

## What the player sees
- Phase 0: nothing about a pack at all. No download prompt, no kick, no text screens. Plain coloured `[Name]` ranks and normal Minecraft items in menus.
- From phase 1 (Java): a short "Phoenix Skyblock icons" prompt right after joining, then the same menus with our own icons. Decline and you are kicked with a friendly line and how to retry. Bedrock: the normal items, no prompt.

## Rules and limits
- Never send what a client cannot draw: a model string goes only to LOADED viewers. No pack model reaches Bedrock.
- Size targets: Java zip 100 KB (hard 1 MB); downloads in under 1 s.
- Original art only. The pack id never changes; the version number does.
- Performance: the pack is sent once at join; `hasPack` is a map lookup; no per-tick work.
- A pack kick is not an incident: no staff alert; `/pack status` counts statuses. A fail-open switch-off alerts staff once.

## Reality check (card vs docs vs code, 31 Oct)
| # | Card v1 and docs say | Code and owner say | Resolution |
|---|---|---|---|
| 1 | Pack "Draft": no pack code or art exists yet (I7 v1 text) | Pack v1 is **merged** (commit 442ae493): `modules/pack`, `common/pack`, `common/menu/PackTitles`, `resourcepack/` with 215 files, two 212 KB zips bundled in the plugin jar | Real state is in section 1 |
| 2 | 427 art entries (505 files), 238 for the first release | v1 art in the repo: 31 backgrounds, 90 icons, 5 glyphs, 3 badge fonts, 7 crates, 7 keys | Whole list dropped; v2 list is 50 icons |
| 3 | `server.properties` carries the pack lines | Lines are empty; the plugin sends the pack | Plugin sends it (phase 1) |
| 4 | Forced pack, declining disconnects, "asked before joining" (33, 77, RP-D1, OWNER_PRINCIPLES 7) | `pack.yml required: true`, `kick-on-fail: true`, sent in the configuration phase | Forced stays; asked after joining (GUI-Q2) |
| 5 | Bedrock gets a converted pack through Geyser (RP5) | Never built | Not planned in v2; Bedrock cannot be forced |
| 6 | Item looks use `custom_model_data` on a vanilla item (RP-D3) | `MenuIcons.render` changes the material to PAPER; tools use `item_model` (vanilla model keys) | v2 keeps the base item (I9 section 7) |
| 7 | `pack-sound` entries replace vanilla sounds for pack viewers | 3 entries in `sounds.yml` | Deleted |
| 8 | Rank badge group still lists old rank names (known doc drift) | Badge fonts are generated from `ranks.json` | Moot: fonts are removed |
| 9 | X4 (CROSS-CUTTING): copper chest crate pedestal shows as a normal chest on 1.21.5 clients | `crates.yml` uses vanilla blocks including `copper_chest` | Stays a vanilla block in phase 0; F5 picks a 1.21.5 block (ripple) |
| 10 | C1-R1-03 (locked C1): credits colour `#FF8C1A`, "glyph U+E204 (pack)" | Glyph is a pack font character | Glyph retired (GUI-Q3, GUI-Q9); symbol `✦`; colour stays |
| 11 | Draft text: the four badge switches retire and the Immortal gradient stays a perk | One of the four switches (`rank-color`) IS the Immortal colour picker | Three switches retire (`reduced-motion`, `simple-badges`, `large-badges`); `rank-color` stays |
| 12 | Draft text: rank colours go to LuckPerms `base.txt` | `base.txt` hex colours differ from what players see today (owner `#C21F2B` vs shown `#FFC21A`); donor groups are not created in `base.txt`; LuckPerms cannot hold the Bedrock codes or gradients | Rank looks move to the chat module (`chat.yml ranks.looks`), values copied exactly from `pack-ids.yml` (developer decision, build spec step 1) |

## Developer decisions (defaults; the owner only vetoes)
- **Phase 0 is its own build** (`GUI-PHASE0.md`): remove v1, no menu redesign, no new pack code. Rollback is `git revert`; the old jar and tag `pack-v1-final` keep v1 reachable.
- **Rank colour source:** `chat.yml ranks.looks.<group>` (`color` one hex or three comma-separated for a gradient, `bedrock` legacy code) in the chat module, loaded by `ChatConfig`, defaults copied exactly from the 16 `pack-ids.yml badges.ranks` entries. `RankProvider.colorSource` stays and reads it, so names in chat, tab, nametag, scoreboard, join messages and hover keep today's colours. A group missing from `ranks.looks` falls back to LuckPerms `color` meta, then the prefix colour, then the `default` look. `server/permissions/base.txt` is not changed except a comment saying where rank colours live. One source, editable in `/admin`.
- **Rank display without the pack:** `BadgeService` no longer means "pack present": it always renders the simple `[Name]` form (Bedrock legacy-code form for Bedrock viewers, text gradient for gradient ranks). The font form, pack flags and `BadgeOptions` pack, reduced-motion and large fields are deleted. `/rank preview` keeps the simple, Java and Bedrock lines (staff tool for F2's colour choices).
- **Settings migration:** the three retired switches stop being registered; stored values are ignored (same pattern as `RetiredTexts`); no database change (the pack has no tables). `rank-color` and `skyblock.chat.rankcolor` stay.
- **Near-duplicate colours stay in phase 0:** hatchling `#FF9A4D` and builder `#FF9A3D` look almost the same. Phase 0 keeps every value identical; F2 and G8 fix the distinct-colour rule when the seven rank names are decided.
- **Phase 1 build:** `icons.yml` (id, base item, screen) checked by a unit test: every icon id used in code has a base item and a PNG, no PNG is unused, every base item exists in 1.21.5. A Gradle task zips `resourcepack/java` into the plugin resources and writes the SHA-1. The forced flow, fail-open check and kick texts have unit tests for the pure parts (status mapping, kick or not, timeout).
- **Paid cosmetics:** nothing sold for credits may be visible only through the pack. Tool skins, trails and sets must show something for everyone (name colour, lore, glint, dyed leather colour, particle); the pack look is a Java bonus and the shop page says "Java with the pack" (ripple E10, E13, I10).
- **Security:** one fixed pack URL and SHA-1 from config; no per-player URL; players cannot change the URL. `PrivateUse.strip` stays on all player text.
- **Docs:** `docs/plans/RESOURCE_PACK*.md`, `PACK_BUILD*.md`, `docs/PACK_TEST_CHECKLIST.md`, `docs/OWNER_ANSWERS_PACK1.md` get a "superseded by I7 v3" header; `docs/OWNER_PRINCIPLES.md` principle 7 is rewritten to match; no file is deleted.

## Questions for the owner
None open. GUI-Q1, GUI-Q2, GUI-Q3 answered by the owner; GUI-Q16 was not answered and the suggested default applies (extended, see Decided).

## Connections
- GUI standard (I9), main menu (G9), help and settings (G10).
- Tools (E10), wardrobe and sets (E13), crates (F5), currencies (C1), ranks (F2), chat and tab (G1, G8), scoreboard (G7), tutorial (A3), join flow (A1: the pack answer comes before the welcome).
- Launch prep (I8: Java join with the forced pack, decline message, Bedrock phone), store and credit shop (I6, I10: cosmetics must show without the pack), PvE (I4: the Wyrm of the Deep model is no longer pack-bound), mastery tree (B14: node icons in phase 3).

## Decided
- 31 Oct restart (owner): remove pack v1; vanilla+, simple and good; no custom writing; Bedrock must still work.
- 77: the owner makes the art himself. 78: each pack release is checked on a Bedrock phone. RP-D7: the owner makes the art (30 Oct).
- C1-R1-13 and C1-R1-24: currency symbols and GUI icons are redone simple and readable (now text symbols plus our own new simple icons).
- I7-R1-01 (GUI-Q1): icons first, background later (phase 2, optional).
- I7-R1-02 (GUI-Q2): the pack is forced for Java, asked after the player has joined, decline or failure kicks with a friendly message; Bedrock cannot be forced. Reverses "asked before joining" of 77 and OWNER_PRINCIPLES 7.
- I7-R1-03 (GUI-Q3): pack v1 removed completely now, pure vanilla until phase 1; rank colours move out of `pack-ids.yml` first.
- I7-R1-04 (developer decision): rank looks live in `chat.yml ranks.looks`, copied exactly.
- I7-R1-05 (GUI-Q16, no owner answer, suggested default extended by GUI-Q5, GUI-Q7, GUI-Q9): first icon slice is 9 icons: menu item, 4 currencies, Back, page, Close, danger.
- I7-R1-06 (GUI-Q9): our own new simple vanilla-looking icons; the old v1 icons are discarded.
- I7-R1-07: the ripples C1-R1-13 (symbols and GUI art redone) and B14-R0-01 (mastery node art) are absorbed in sections 2 and 9.
- Superseded by the restart and now written down: 33 and 77 "skins all GUIs" part, RP-D1 (forced before joining), RP-D8, GUI1 G11 and G12 (Phoenix Night), PACK1 items 1 to 8.

## Open questions
None.

## Rejected ideas
- Licensed or AI-made art (owner draws all art, 77).
- Custom fonts, glyph icons, negative-space titles, text shaders, rank badge fonts (owner 31 Oct: no custom writing).
- A general Java-to-Bedrock converter and Geyser mappings in the first release.
- Worn set looks, HUD bars, world border texture in the pack.
- Keeping pack v1 alive until v2 is ready, or keeping only the crate chests (GUI-Q3: remove all now).
- An optional pack with a `/pack` command and a "Server icons" switch (GUI-Q2: forced; nobody is left without it on Java).
- One container background in the first release (GUI-Q1: icons first).
- Reusing any v1 icon art (GUI-Q9).

## Change log
- v1 (31 Oct): written from RESOURCE_PACK.md, the art brief, OWNER_ANSWERS_ROUND1 and the old REVIEW3 answers.
- v2 / Review 1 (31 Oct): rewritten for the owner's restart: pack v1 removal table, pack v2 contents and size, one optional container background, forced vs optional, hosting reuse, Bedrock, delivery phases 0 to 3, Reality check, Developer decisions. Nothing locked, no ledger lines.
- v3 / Locked (31 Oct): owner answers folded in (I7-R1-01 to I7-R1-07): icons first; pack forced for Java and asked after joining (decline kicks with a friendly message, fail-open safety, Bedrock exempt); v1 removed completely including `modules/pack` and `PackService`; phase 0 = remove v1 + move rank colours to `chat.yml ranks.looks` + pure vanilla; phase 1 first slice 9 icons with our own new simple icons; phase 2 optional background; reality check rows 10 to 12 added; open questions removed.
