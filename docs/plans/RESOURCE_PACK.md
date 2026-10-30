# Resource Pack Plan (PLAN-RP)

> **Status:** plan, 2026-10-30 (updated the same day with the owner's pack decisions). Comes **after** the vanilla UI (STYLE2, SELL2 and the crates module first).
> **Owner decisions 2026-10-30:** the Java pack is **forced** (players accept it before they join, behind a professional prompt; RP-D1 decided); custom textures on crates, GUIs, sets/wardrobe and tools; size is not critical but keep it optimised; the owner draws the art himself (RP-D7 decided); the style is not fixed yet. The full art list he works through is `docs/plans/RESOURCE_PACK_ART_BRIEF.md`.
> **Every choice here is a default** the owner can change. Defaults that matter have an id (`RP-D1` …) so the owner can answer "RP-D4: no" (register in §17).
> Sources: `DECISIONS_2026-10-30.md` (design round: "vanilla now, resource pack later, same slots, the pack only paints on top"; sketches V2 approved; I5 borders; 7 crates), `UI_STYLE.md` (palette, §4 lore, §5 menus, §6 HUD, §7 sounds, §9 brand glyph), `style.yml`, `plans/ENCHANTS.md` §11, `plans/WARDROBE.md` §10, `DESIGN.md` version policy (Java 1.21.5 → current, Bedrock via Geyser), the modules as built (item ids and menu sizes in §5 and §6).

---

## 0. Summary

- **The pack only paints.** Every menu keeps its slots, clicks and items. A player without the Java pack (Bedrock, a failed download, staff emergency switch-off) sees today's vanilla UI. Nothing in the game needs the pack to work, even though it is forced.
- **Forced** (RP-D1, decided 30 Oct). The Java pack is sent in the configuration phase, before the player enters the world, with a professional prompt; declining disconnects with a friendly how-to (§2.3). Bedrock is forced by Geyser already.
- **Fallback-safe item looks:** custom items keep their vanilla material and get a **`custom_model_data` string** (`phoenix:tool/pickaxe/iron`). The pack overrides the vanilla item definition and picks our model for that string. Without the pack the client ignores the string and shows the vanilla item (RP-D3).
- **Per-viewer rendering:** the plugin knows per player if the Java pack is loaded, if the Bedrock pack is loaded, or neither. Glyph icons, menu backgrounds and pack sounds are only sent to players who can show them. Everyone else gets the Bedrock-safe symbols (`$ ◆ ✦ ✧`) and vanilla sounds.
- **Bedrock** gets a converted pack through Geyser: item icons (custom item mappings), font icons (glyph sheets), custom sounds. Bedrock does **not** get menu backgrounds, negative-space layouts, 3D models or custom armor looks.
- **One source of truth:** `resourcepack/ids.yml` lists every model id, glyph codepoint and sound event. The build script generates the Java JSON, the Bedrock mappings and a check file the plugin's unit tests read.
- **Milestones:** RP1 plumbing → RP2 font icons → RP3 menu backgrounds → RP4 item models → RP5 Geyser → RP6 sounds → RP7 HUD → RP8 crates → RP9 world → RP10 launch (§16).

---

## 1. Goals and rules

| Goal | What it means |
|---|---|
| Vanilla stays the fallback | Same slots, same clicks, same items, same messages. The pack adds art on top. A menu must be fully usable with the pack off. |
| No missing textures, ever | Never send something a client can't draw. No `item_model` pointing at a pack model, no private-use glyphs, no custom sound ids to players without the pack (§3.3). |
| Java and Bedrock look related | Same icons and colors where Bedrock can show them. Where it can't, the vanilla UI_STYLE look is the Bedrock look. |
| Small and fast | Size is not critical (owner, 30 Oct) but optimised: Java target ≤ 8 MB, Bedrock target ≤ 5 MB (§14). Every player downloads it before joining, so it must stay quick. |
| Data-driven | Model ids, glyphs and sounds come from configs (`ids.yml` → `pack.yml` in the plugin). No ids hard-coded in Java. |
| Brand | Phoenix fire palette for gameplay art (UI_STYLE §1). SkybornMC frost blue only for network art (pack icon, logo glyph on the tab header). |

---

## 2. Hosting and the prompt

### 2.1 How the pack is sent (RP-D2)
- **Default: the plugin sends it** with Paper's resource pack API on join (`Player#sendResourcePacks`), from `pack.yml` (url, sha1, id, required). Text of the prompt in `messages/core.yml → pack.*`.
- **Before joining (forced pack, RP-D1):** the pack is sent during the **configuration phase**, so the player accepts and downloads it on the loading screen before entering the world (no join, no chunks, no chat until it is loaded). Paper exposes this phase through its connection-configuration API (`AsyncPlayerConnectionConfigureEvent` and the configuration-phase audience; exact calls checked on Paper 26.3 in RP1, never guessed). `required: true` in `pack.yml`.
- **Prompt text** (`messages/core.yml → pack.prompt`, professional, short, no shouting), e.g. "Phoenix Skyblock uses a custom resource pack for its menus, items and sounds. It is about N MB and only downloads once per update." The decline message (`pack.declined`) explains how to allow it: Multiplayer → select the server → Edit → Server Resource Packs: Enabled.
- `server.properties` stays empty (`resource-pack=`, `resource-pack-sha1=`, `resource-pack-id=`, `require-resource-pack=false`, `resource-pack-prompt=`). Using both would send the pack twice.
- Why the plugin: a version bump reaches online players after `/skyblock reload` (existing command) without a restart; the prompt uses our message style; the plugin sees the status events it needs (§3.3).
- **Fallback (if the plugin route fails on a client version):** fill the five `server.properties` lines with the same values (`require-resource-pack=true`, the prompt as JSON text). Vanilla also sends the pack in the configuration phase. Same zip, same hash.

### 2.2 Hosting (RP-D5)
- **Default:** a static file on the VPS behind nginx: `https://pack.skybornmc.net/phoenix-java-<version>.zip`. Long cache headers, the version is in the file name.
- The URL must be a direct download (no HTML page, no redirect to a login). HTTPS only.
- Bedrock: the `.mcpack` goes into `plugins/Geyser-Spigot/packs/`. Geyser sends it itself (no URL needed).
- Later, if downloads get slow at 200–300 players: move the same files to a CDN (Cloudflare in front of the VPS). No code change, only the URL.

### 2.3 Forced pack (RP-D1, decided 2026-10-30)
| Case | Java | Bedrock |
|---|---|---|
| Accepts | Downloads on the loading screen, then joins with the pack (`JAVA_LOADED`). | Geyser `force-resource-packs: true` (integrated pack) already forces ours too. |
| Declines | Disconnected before joining with `pack.declined` (how to enable server packs). No retry loop: the next connect asks again. | n/a |
| Download fails (host down, bad hash, `FAILED_DOWNLOAD`, `INVALID_URL`, `DISCARDED`) | **Default: let them in on the vanilla fallback** (state `FAILED`), alert online staff once per minute at most, retry on the next join. A broken host must never lock everyone out (open question). `pack.kick-on-fail: false`. | n/a |
| Emergency | `pack.required: false` + `/skyblock reload` turns the pack back into an optional offer without a release. | unchanged |

- Testing (RP1–RP9): the test server uses the same forced setup as soon as the first real pack exists; until then `pack.required: false`.
- **`/settings` toggle (RP-D6) is hidden while the pack is required** (a forced pack can't be switched off by players). It only shows when staff set `pack.required: false`.
- The vanilla fallback stays forever: Bedrock (no backgrounds, worn looks, tooltips, HUD bars), failed downloads, ViaVersion edge cases.
- Because Java players now always have the pack (or a rare failed download), features that show a missing texture without it become usable on Java: **custom worn armor looks** (§5.5, RP4b) and **rarity tooltip styles** (§11, RP-D15). Failed-download players see those parts wrong until their next join; accepted.

### 2.4 Version bumps
- `ids.yml → version: 12` (an integer). The build writes `phoenix-java-12.zip` and its SHA-1.
- The **pack id (UUID) never changes**. A new hash under the same id makes Java clients replace the old copy instead of stacking it.
- Bedrock `manifest.json` header version becomes `[1, 0, 12]`. Bedrock caches by version: forgetting the bump means Bedrock keeps the old pack.
- Release order: upload the zip → update `pack.yml` (url + sha1) → `/skyblock reload` → online pack users get the new version with a one-line notice; the next join does it for everyone else.
- Keep the last 3 zips on the host so players mid-download don't break.

---

## 3. Plugin side (what the code needs, built in RP1)

### 3.1 Pack status per player
- New core service `PackService` in `common` (no command). States per player: `NONE`, `OFFERED`, `JAVA_LOADED`, `BEDROCK` (Floodgate player; the Bedrock pack is forced by Geyser), `DECLINED`, `FAILED`.
- Fed by `PlayerResourcePackStatusEvent` (`SUCCESSFULLY_LOADED` → `JAVA_LOADED`; `DECLINED`/`FAILED_DOWNLOAD`/`INVALID_URL`/`DISCARDED` → fallback).
- Until the status arrives, the player counts as `NONE` (vanilla). Open menus re-render when it switches.
- Capabilities from the state: `icons` (Java pack or Bedrock pack), `backgrounds` (Java pack only), `models` (Java pack or Bedrock pack), `sounds` (Java pack or Bedrock pack).

### 3.2 Where the plugin reads it
| System | With the capability | Without |
|---|---|---|
| Menu titles (`MenuService.title`) | Background glyph + negative space + the same crumbs | Today's `▸ Island » Upgrades` title |
| Frame panes | `custom_model_data` `phoenix:menu/blank` (invisible, the background draws the frame) | Today's black/red/orange panes |
| Text tags | New tags `<icon:money>`, `<icon:crystals>`, `<icon:rank_knight>` … resolve to the glyph | Resolve to the Bedrock-safe symbol (`$`, `◆`, `✦`, `✧`) or the rank text |
| Sounds (`sounds.yml`) | `pack-sound` value of the key (custom event) | The vanilla value of the key (today's) |
| Items | Same item, same `custom_model_data` for everyone (the client decides) | Same |

- Chat and broadcasts render **per recipient** (one component per capability group, cached per message), so a pack user sees icons and a vanilla user sees symbols in the same line.
- **Chat filter:** strip private-use characters (U+E000–U+F8FF) from anything players type (chat, signs, anvils, nicknames, warp names, notes). Otherwise a player could fake rank badges.

### 3.3 Never send what the client can't draw
- `item_model` is only used with **vanilla** models (as the tools do now: `minecraft:golden_pickaxe`). A pack model in `item_model` shows a missing-model cube to vanilla clients (RP-D3).
- `ItemDefinition.itemModel` stays for vanilla models. RP1 adds `ItemDefinition.modelData` (a string) that `ItemRegistry` writes into the `custom_model_data` component's string list. Renderers can set it per stack (a tool's tier, a book's rarity, a note's value band).
- Glyphs (U+E200+) and custom fonts only go to players with `icons`/`backgrounds`.
- Custom sound events only go to players with `sounds`; others get the vanilla sound of the same key.
- Worn armor looks (`equippable` asset ids) and tooltip styles need the pack. With the forced pack (RP-D1) they are allowed on Java (§5.5, §11); Bedrock and failed-download players are the accepted exceptions.

---

## 4. Geyser and Bedrock

### 4.1 What Bedrock gets
| Feature | Bedrock | How |
|---|---|---|
| Item icons | Yes | Geyser custom item mappings (`plugins/Geyser-Spigot/custom_mappings/phoenix_items.json`), format v2, one entry per `custom_model_data` string on its vanilla item → a Bedrock item `phoenix:<family>_<id>` with a 2D icon. |
| Enchant glint | Yes | Vanilla glint, unchanged. |
| Font icons (currencies, ranks, pillars, rarity tags) | Yes | Same codepoints in Bedrock glyph sheets (`font/glyph_E2.png`). Geyser passes the characters through. |
| Custom sounds | Yes (test in RP5) | `sounds/sound_definitions.json` in the Bedrock pack with the **same names** as the Java events. |
| Phoenix logo glyph (chat prefix, scoreboard title) | Yes | Glyph sheet, 9 px tall. |
| Menu backgrounds | **No** | Bedrock draws its own chest UI; there is no negative space. Bedrock keeps the vanilla frame panes. |
| Action-bar bars, offset HUD glyphs | **No** | No ascent offsets on Bedrock. Bedrock keeps the text action bar. |
| 3D item models | **No** (2D icons only) | Converter flattens to the icon texture. |
| Worn custom armor | **No** | Vanilla armor + trims + dye (WARDROBE §10) stay the look. |
| Crate block/display animations | **No** | Geyser does not show display entities reliably. Bedrock gets the GUI roll + sounds. |
| Border/particle texture overrides | Partly | Bedrock pack can override its own textures; test in RP9. |

### 4.2 Conversion
- The Java pack is the master. The build script converts it: item icons → Bedrock textures + `item_texture.json`; glyphs → glyph sheets; sounds → `sound_definitions.json`; `manifest.json` from a template (fixed header UUID and module UUID).
- Tool choice in RP5 (RP-D9): our own small converter inside `build_pack.py` (only 2D icons, glyphs and sounds, which is all we need) instead of a general Java→Bedrock converter. Simpler and predictable.
- Bedrock item names stay ours (Geyser shows the Java display name). Max 16 characters (already the rule).

### 4.3 Codepoint rule for Bedrock
- Bedrock's own button icons live in `glyph_E0`/`glyph_E1`. **Our shared icons start at U+E200** (sheet `glyph_E2.png`, 256 cells).
- Java-only glyphs (backgrounds, HUD bars) use U+E300+ and are never sent to Bedrock.
- Negative space lives in its own Java font (`phoenix:space`), never sent to Bedrock.

---

## 5. Item models

### 5.1 Naming scheme
- **`custom_model_data` string:** `phoenix:<family>/<path>` (lowercase, `a-z 0-9 _ /`). Example: `phoenix:tool/pickaxe/iron`.
- **Model file:** `assets/phoenix/models/item/<family>/<path>.json`; texture `assets/phoenix/textures/item/<family>/<path>.png`.
- **Vanilla override:** `assets/minecraft/items/<vanilla_item>.json` is a `minecraft:select` on `custom_model_data` (strings) with the vanilla model as `fallback`. One override file per vanilla material we use. The build generates these files from `ids.yml`.
- **Bedrock id:** `phoenix:<family>_<path with / → _>` (`phoenix:tool_pickaxe_iron`).
- The custom item id in the item's PDC (`phoenixskyblock:item_id`) does not change. The model string is a separate, renderable value.

### 5.2 Id table
Counts are today's catalogs. New entries in a catalog need a texture or they fall back to the vanilla look (the build warns, the game never breaks).

| Family | Model string | Count | Vanilla item(s) | Source (built) | Notes |
|---|---|---|---|---|---|
| Pickaxes | `phoenix:tool/pickaxe/<tier>` | 12 | stone/copper/iron/diamond/netherite pickaxe | `tools-defaults.yml` tiers coal … voidstone | Set by `ToolRenderer` from the tier. Starter piece = tier 1 model. |
| Axes | `phoenix:tool/axe/<tier>` | 11 | wooden … netherite axe | oak … warped | |
| Swords | `phoenix:tool/sword/<tier>` | 10 | stone … netherite sword | grave … warlord | |
| Hoe, rod | `phoenix:tool/hoe/basic`, `phoenix:tool/rod/basic` | 2 | stone_hoe, fishing_rod | Phase 7 adds tiers | Rod needs a `cast` variant (select on `fishing_rod/cast`). |
| Tool skins | `phoenix:skin/<skin>/<kind>` | 3 × 5 = 15 | per tool | `skins.*` (phoenix_flame, frostbite, royal_gold) | Wins over the tier model. `skins.<id>.model` in tools.yml becomes this string. |
| Blueprints | `phoenix:tool/blueprint/<rarity>` | 2 (rare, epic) | paper | `tool_blueprint` | Rolled-paper icon tinted by rarity. |
| Enchant books | `phoenix:enchant/book/<rarity>` | 6 | enchanted_book | `enchant_book` (common … mythic, ENCHANTS §11) | Glint stays. |
| Disenchant Scroll | `phoenix:enchant/scroll` | 1 | paper | `disenchant_scroll` | |
| Set pieces (icon only) | `phoenix:set/<set>/<piece>` | 16 × 4 = 64 | the set's vanilla armor | `sets.yml` (delver … phoenix), `wardrobe_piece` | Inventory icon only. Worn look stays vanilla + trims (§5.5). |
| Set Token | `phoenix:set/token/<tier>` | 4 | per `wardrobe.yml` | `set_token` | One per set tier (I–IV). |
| Wardrobe Tome | `phoenix:set/tome` | 1 | per `wardrobe.yml` | `wardrobe_tome` | |
| Money notes | `phoenix:note/<currency>/<band>` | 3 bands per currency | paper (config) | `money_note` | Bands by amount: `small` < $100k ≤ `medium` < $10M ≤ `large` (numbers in `pack.yml`). |
| Tag item | `phoenix:cosmetic/tag` | 1 | name_tag | `tag_item` | |
| Spawners | `phoenix:spawner/<tier>` | 5 | spawner | `spawner_1` … `spawner_5` | Tier frame color on the cage. Mob type stays text. |
| Crate keys | `phoenix:crate/key/<crate>` | 7 | tripwire_hook | crates module (not built) | ember, cinder, blaze, inferno, phoenix, eclipse, conqueror. |
| Crate blocks/displays | `phoenix:crate/block/<crate>` (+ `_open_<0-5>` frames) | 7 (+ frames) | chest (display item) | crates module | Java only (§9). |
| Crate key fragment | `phoenix:depths/key_fragment` | 1 | tripwire_hook | `depths_key_fragment` | |
| Geode | `phoenix:depths/geode` | 1 | amethyst_cluster | `depths_geode` | |
| Depths ore drops | `phoenix:depths/ore/<id>` | 12 | vanilla materials | `depths_coal` … `depths_voidstone` | Default: **vanilla look kept** except `depths_voidstone` (echo shard → own icon) (RP-D10). |
| Depths log drops | `phoenix:depths/log/<id>` | 11 | vanilla logs | `depths_oak_log` … `depths_warped_stem` | Default: vanilla look kept. |
| Depths combat drops | `phoenix:depths/mob/<id>` | 9 | vanilla drops | `depths_rotten_flesh` … `depths_wither_ash` | Default: own icons only for `raider_scrap` and `wither_ash` (they reuse unrelated vanilla items). |
| Currencies (menu/shop icons) | `phoenix:currency/<id>` | 4 | sunflower/prismarine_crystals/emerald/… | shop, enchanter, wardrobe price buttons | money (coin stack), crystals, gems, shards. Not real items (crystals and gems are not tradeable). |
| Menu buttons | `phoenix:menu/<button>` | ~30 | the button's current material | `common/menu` + module menus | See §5.3. |

### 5.3 Menu buttons
`blank` (frame panes), `back`, `previous`, `next` (replace the player heads; heads stay the fallback), `close`, `info`, `help`, `confirm`, `confirm_side`, `cancel`, `cancel_side`, `toggle_on`, `toggle_off`, `loading`, `error`, `empty`, `locked`, `maxed`, `minus_1/10/100`, `plus_1/10/100`, `set_min`, `set_max`, `type_amount`, `sell`, `sell_all`, `take_all`, `buy`, `search`, `filter`, `sort`.
- Head buttons: a textured head can't take `custom_model_data` from our pack in a fallback-safe way and still look right without it. Default: pack users get an `arrow`-material button with the model; non-pack users keep the heads (RP-D11).

### 5.4 Model style
- 2D "generated" item models, 16×16. Legendary/mythic tools and crate keys may use 32×32 (§14).
- Tier art follows the material ladder of the tier (coal-black head → copper → iron … voidstone with a purple-black glow). Rarity reads from the outline color (UI_STYLE §1.4).
- Animated textures (`.mcmeta`) only for legendary/mythic items, ≤ 8 frames.
- Tool level (Lv 1–50) does **not** change the model (default). Prestige adds a small ember spark overlay (`phoenix:tool/<kind>/<tier>_p`), RP-D12.

### 5.5 Worn armor (RP4b, allowed since the pack is forced)
- Custom worn looks use `equippable` with an `asset_id` from the pack (`assets/phoenix/equipment/<set>.json` → `textures/entity/equipment/humanoid/<set>.png` + `humanoid_leggings/<set>.png`, 64×32). One look per set (16 sets, art list G65–G80 in the art brief).
- Vanilla armor trims still render on top, so WARDROBE's trim upgrades at set-level milestones keep working.
- Bedrock: Geyser ignores the Java asset id and shows the base vanilla material (+ trim, + dye); check in RP5. Failed-download Java players see the piece wrong until they rejoin (accepted, §2.3).
- Code: `WardrobeRenderer` sets `equippable.asset_id` from `sets.yml → look.asset` (new optional key, empty = vanilla look as today). Ships after RP4.

---

## 6. Menu backgrounds

### 6.1 How it works (Java only)
- The menu title starts with: `[space −8][background glyph][space −(width+1)][space +offset]` and then the normal title text.
- The background glyph is a bitmap in font `phoenix:menu` (never in the default font, so players can't type it). Width 176 px. Height = the chest's image height: `114 + rows × 18` (3 rows = 168, 4 = 186, 5 = 204, 6 = 222).
- The negative space comes from font `phoenix:space` (a `space` provider): U+F801…U+F809 = −1, −2, −4, −8, −16, −32, −64, −128, −256; U+F821…U+F829 = the same positive. The plugin builds any width from these.
- The background art includes the frame. The frame panes become invisible (`phoenix:menu/blank`), so the art shows through. Clicks on frame slots still do nothing (same as now).
- The art must leave the **content slots and button slots visible** (18×18 cells at the vanilla positions). The title text still shows (crumbs + page), drawn over the top band.
- Starting values (tuned in RP3 on a test client, stored in `ids.yml → menus.<id>`): glyph `ascent: 13`, title text offset `+8` after the shift back.
- The player inventory half stays vanilla (default; painting it is possible but every menu would need it).

### 6.2 Per menu
| Menu | Code | Rows | Glyph | Background idea |
|---|---|---|---|---|
| Generic 6-row frame | `common/menu` (every PagedMenu) | 6 | U+E300 | Charcoal panel, ember corner flames, dark slot wells. Default for every menu without its own art. |
| Generic 3-row (confirm, small choices) | `ConfirmMenu`, 3-row menus | 3 | U+E301 | Same style; confirm/cancel zones tinted green/red behind slots 10–12 / 14–16. |
| Number picker | `NumberPickerMenu` | 6 | U+E302 | Value plate behind slot 22. |
| Shop (category grid) | `modules/shop` ShopMenu | 6 | U+E310 | Market stall awning in the top band; category tabs. |
| Shop item / quick-buy | `modules/shop` CategoryMenu (quick-buy grid) + BuyAmountMenu (amount screen) | 6 | U+E311 | Price plate under the grid; amount strip (19-25), Buy plate 39-41. |
| Sell chest | `modules/economy` SellMenu (SELL2: 4 input rows + button bar) | 6 | U+E312 | Wooden crate interior for the 4 input rows; gold "Sell" plate behind 52/53; live total plate behind slot 47; divider row 36-44. |
| Satchel | `modules/depths` SatchelMenu (paged) | 6 | U+E313 | Leather bag interior, fill gauge in the top band. |
| Satchel item | SatchelItemMenu | 3 | U+E314 | Small leather panel. |
| Forge | `modules/tools` ForgeMenu | 6 | U+E315 | Anvil and hot coals; "stat change" arrow between old and new tool slots. |
| Tool | `modules/tools` ToolMenu | 6 | U+E316 | Perk slot sockets (5/15/30/45 + prestige ★). |
| Enchanter | `modules/enchants` EnchanterMenu (+ Slot/Upgrade/Remove/Shop/Armor menus) | 6 | U+E317 (+ E318 slots) | Purple runes, book stand; slot sockets for enchant slots. |
| Wardrobe | `modules/wardrobe` WardrobeMenu / SetMenu | 6 | U+E319 | Wardrobe cabinet; 4 armor silhouettes behind the piece slots. |
| Loadout | LoadoutMenu / LoadoutEditMenu | 6 | U+E31A | Weapon rack; hotbar strip. |
| Crates preview | crates module (not built) | 6 | U+E31B | Crate lid in the top band, colored per crate (one glyph per crate: U+E320–E326). |
| Crate roll | crates module | 3 | U+E31C | Spinner window with a gold marker over the winning slot 13. |
| `/p` punish screen | `modules/moderation` PunishMenus (6-row main, 3-row steps) | 6 / 3 | U+E31D / U+E31E | Staff look: dark steel, red severity bands per category row. Staff only. |
| `/profiles` | ProfilesMenu | 3 | U+E31F | Three profile cards. |

- Bedrock players and pack-less Java players see the vanilla frame of the same menu. The slot map never changes.
- **Full per-menu list:** the art brief (`RESOURCE_PACK_ART_BRIEF.md` §B) lists 52 backgrounds with sizes and slot notes. It adds codepoints for menus this table doesn't have (U+E303–E30F confirm/generic 4-row/staff/hub/help/settings/island/bank/AH, U+E327–E32F, U+E350–E353) and splits the shop amount screen off E311 (U+E353). `ids.yml` takes those values.
- A menu without its own glyph uses the generic one for its row count.

---

## 7. Font icons (chat, lore, scoreboard, tab)

### 7.1 Fonts
- `phoenix:icons`: bitmap glyphs, 9 px tall (`ascent: 7`), shared with Bedrock (same codepoints in `glyph_E2.png`).
- `phoenix:menu`: backgrounds (Java only). `phoenix:hud`: HUD glyphs with offsets (Java only). `phoenix:space`: negative/positive space.

### 7.2 Codepoints (shared, U+E200–E2FF)
| Range | Icons |
|---|---|
| U+E200–E20F | currencies and brand: `E200` money coin, `E201` crystal, `E202` gem, `E203` shard, `E204` credits, `E205` phoenix logo (small, chat prefix), `E206` phoenix logo (wide, scoreboard/tab title), `E207` SkybornMC "S" logo |
| U+E210–E21F | status marks: check, cross, warning, info, lock, star, hourglass (only if they look better than the safe symbols; default: keep the Unicode symbols, RP-D13) |
| U+E220–E23F | rank badges: `E220` Ember (free rank), `E221` Squire, `E222` Knight, `E223` Paladin, `E224` Warlord, `E225` Monarch; `E230` Helper, `E231` Mod, `E232` Admin, `E233` Owner, `E234` Builder |
| U+E240–E24F | rarity tags for lore footers: common, uncommon, rare, epic, legendary, mythic, special |
| U+E250–E25F | pillars/skills: mining, foraging, farming, combat, conquest, fishing |
| U+E260–E27F | island roles (leader, admin, mod, member, coop), chat channel tags (island, staff) |

- Badges are pill-shaped, max 32 px wide, 9 px tall, colored like the rank's hex color, text baked in.
- `style.yml` gets an `icons:` map (name → codepoint) and the fallback symbol per icon. Messages use `<icon:name>`, never raw codepoints.

### 7.3 Where icons show
- Chat: rank badge replaces the text rank prefix for capable viewers; the text prefix (LuckPerms) stays for others.
- Currency amounts: `<icon:money> 1.2M` vs `$1.2M`. Same in lore price lines, scoreboard and tab.
- Lore footer: rarity tag glyph + type text (`[RARE] TOOL`), fallback today's bold footer.
- Tab header/scoreboard title: phoenix logo glyph before `PHOENIX SKYBLOCK`.

---

## 8. HUD

| Element | With the pack (Java) | Bedrock (pack) | No pack |
|---|---|---|---|
| Scoreboard title | Logo glyph `E206` + gradient text | Logo glyph | Today's gradient text |
| Scoreboard lines | Currency and pillar icons before values | Same icons | Symbols |
| Action bar skill XP | A 100 px bar glyph (`phoenix:hud`, 10 segments U+E330–E33A, raised above the hotbar) + `+12 Mining XP` | Text line | Text line |
| Satchel fill | Small bag icon + fill bar next to the hotbar while in the Depths | Text warning when full | Text warning |
| Teleport warmup | Hourglass glyph + countdown | Same text | Same text |
| Boss bar | Custom bar textures by overriding `textures/gui/sprites/boss_bar/*` (global: all boss bars) | Vanilla | Vanilla |

- The action bar keeps the 1.5 s rule (UI_STYLE §6.3). The bar glyphs are pure text, so they cost nothing extra.

---

## 9. Crate animations
The crates module is not built yet. The roll logic and rewards live in the plugin; the pack only adds looks.
- **GUI roll (everyone, vanilla-safe):** 3-row roll menu, the row 10–16 spins, slows down and stops on slot 13. With the pack: crate-colored background (§6.2), marker art, and keys/rewards with their models.
- **Spawn crates (Java pack):** each crate at spawn is an `item_display` showing `phoenix:crate/block/<crate>`. Opening plays 6 lid frames (`_open_0` … `_open_5`, model swap every 2 ticks) + flame particles (UI_STYLE §9) + rarity sound.
- **Bedrock and no pack:** a vanilla chest block, particles and vanilla sounds. The GUI roll is the same.
- Win sounds by rarity (§10). Legendary/Mythic wins: title + `phoenix:crate.win.legendary` for the winner, a broadcast line for everyone (text).
- Max one crate animation per player at a time. Skippable by clicking (Bedrock: tap).

---

## 10. Sounds

### 10.1 Rules
- Custom events under the `phoenix` namespace in `assets/phoenix/sounds.json`. `subtitle` keys in the pack's `en_us.json`.
- `sounds.yml` keeps the vanilla sound per key and gets an optional `pack-sound` per key. Players without `sounds` hear the vanilla one.
- OGG Vorbis, mono (positional), 44.1 kHz for short UI sounds. UI sounds ≤ 1 s, jingles ≤ 4 s.
- The `/settings` sound toggles keep working (they mute the key, whichever sound it uses).

### 10.2 Which events (first set, 22)
| Event | Key in `sounds.yml` | When |
|---|---|---|
| `phoenix:ui.click` | click | Menu clicks |
| `phoenix:ui.open` / `ui.close` | menu-open / menu-close | Menus |
| `phoenix:ui.page` | page | Page turns |
| `phoenix:ui.success` / `ui.error` / `ui.warning` | success / error / warning | Replies |
| `phoenix:money.coins` | money | Money received, quick-buy |
| `phoenix:sell.register` | (new key `sell`) | Sell chest "Sell" |
| `phoenix:levelup.skill` / `levelup.tool` / `levelup.island` | level-up (+ new keys) | Level ups |
| `phoenix:unlock` | unlock | Tier/feature unlocks |
| `phoenix:forge.hammer` | (new key `forge`) | Forge craft |
| `phoenix:enchant.apply` | enchant | Book applied |
| `phoenix:drop.rare` / `drop.legendary` | (new keys) | Book/blueprint/rare find drop |
| `phoenix:crate.tick` / `crate.win.<rarity>` (5) | (crates module) | Roll ticks, wins |
| `phoenix:teleport.whoosh` | teleport | Teleports |
| `phoenix:brand.rebirth` | (brand key) | First join, season start, prestige (UI_STYLE §7) |
| `phoenix:depths.gate` | (new key) | Tier gate opens |
| `phoenix:wardrobe.equip` | (new key) | Set / loadout swap |
- New keys come with their features, not as part of the pack. The pack only provides the sound files.

---

## 11. Borders and frames
- **Island border (I5):** border colors and particle styles are built in the plugin now (vanilla). The pack adds a custom `textures/misc/forcefield.png` (phoenix ember pattern). This override is **global** (every world border looks like it). Default: yes (RP-D14).
- **Particles:** the pack can recolor vanilla particle textures (`flame`, `dust`). Global too, so only subtle changes. Default: none in RP9 unless the owner asks.
- **Menu frames:** part of the background art (§6). Panes stay as the fallback.
- **Item frames/tooltips:** custom tooltip backgrounds per rarity (`tooltip_style` component) need the pack and show a missing texture without it. With the forced pack they are allowed on Java (RP-D15 changed 30 Oct), as a P2 item; the plugin only sets `tooltip_style` for viewers with the pack loaded where it can (items are shared, so failed-download players may see a plain fallback).

---

## 12. Pack repo layout (RP-D4: a folder in this repo)
```
resourcepack/
  ids.yml                       ← single source of truth: version, models, glyphs, menus, sounds
  java/
    pack.mcmeta                 ← generated (pack format range from ids.yml)
    pack.png                    ← 128×128 SkybornMC S logo
    assets/minecraft/items/     ← generated vanilla overrides (select on custom_model_data)
    assets/minecraft/textures/misc/forcefield.png
    assets/minecraft/textures/gui/sprites/boss_bar/
    assets/phoenix/models/item/<family>/…
    assets/phoenix/textures/item/<family>/…
    assets/phoenix/textures/gui/menu/<menu>.png
    assets/phoenix/textures/font/{icons,hud}/…
    assets/phoenix/font/{icons,menu,hud,space}.json   ← generated
    assets/phoenix/sounds.json                         ← generated
    assets/phoenix/sounds/<category>/<name>.ogg
    assets/phoenix/lang/en_us.json                     ← subtitles
  bedrock/
    manifest.template.json
    pack_icon.png
    overrides/                  ← Bedrock-only art (if a texture needs a different crop)
  src/                          ← artist sources (.aseprite/.psd), not packed
tools/build_pack.py             ← build script (stdlib Python, like tools/economy_model.py)
build/pack/                     ← output (gitignored)
```
- `ids.yml` is also copied into the plugin jar as `pack-ids.yml`, so plugin unit tests can check that every `modelData`, `<icon:…>` and `pack-sound` the code or messages use exists in the pack.

---

## 13. Build script plan (`tools/build_pack.py`)
1. **Validate** `ids.yml`: unique model strings, unique codepoints, codepoints in the right ranges (§4.3), every texture exists, texture sizes allowed (§14), every sound file exists and is mono OGG.
2. **Generate** vanilla item overrides, font JSON, `sounds.json`, `pack.mcmeta`.
3. **Zip** deterministically (sorted entries, fixed timestamps, no `.DS_Store`) → `build/pack/phoenix-java-<version>.zip`. Same input = same SHA-1.
4. **SHA-1** → `build/pack/phoenix-java-<version>.sha1`.
5. **Bedrock**: icons → `textures/items` + `item_texture.json`; glyph sheet `font/glyph_E2.png`; `sound_definitions.json`; manifest version `[1,0,<version>]` → `build/pack/phoenix-bedrock-<version>.mcpack` + `custom_mappings/phoenix_items.json` (Geyser v2).
6. **Write** `server/plugins/PhoenixSkyblock/pack.yml` (url template + sha1 + version).
7. **Report** sizes against the budget; fail over the hard limits.
- **Upload** is a release step, not part of the script: the release script copies the zip to the VPS web folder, the `.mcpack` and mappings to `plugins/Geyser-Spigot/`, then runs `/skyblock reload`.
- Checks: `build_pack.py --check` (validation only, no zip) runs in the release script. The plugin's unit test only reads `pack-ids.yml` (§12), so `./gradlew build` needs no Python.

---

## 14. Performance budget
| Item | Budget |
|---|---|
| Java zip | target ≤ 8 MB, hard limit 20 MB (owner: size not critical, still optimise) |
| Bedrock .mcpack | target ≤ 5 MB, hard limit 10 MB (downloaded through Geyser, slower) |
| Item textures | 16×16; 32×32 only for legendary/mythic tools, skins, tier IV set icons, crate keys and crate faces |
| Animated textures | ≤ 8 frames, legendary/mythic only |
| Menu backgrounds | 176 × (114 + rows × 18), one PNG per menu, ≤ 60 menus (52 listed in the art brief) |
| Font icons | 9 px tall; badges ≤ 32 px wide; HUD bars ≤ 100 px wide |
| Models | 2D generated by default; 3D only for crate displays, ≤ 40 elements |
| Sounds | ≤ 100 KB each, ≤ 2 MB total, ≤ 30 events at RP6 |
| Glyph sheet (Bedrock) | one 256×256 sheet (`glyph_E2.png`) |
| Download time | < 3 s at 25 Mbit/s for the Java zip (every player downloads it before joining) |
| Worn armor layers | 64×32, 2 per set |

---

## 15. Version notes
- Java: the plugin supports clients 1.21.5 → current (ViaVersion/ViaBackwards). String `custom_model_data` + item definitions in `assets/*/items/` exist since 1.21.4, so every allowed client can use the pack.
- `pack.mcmeta` declares the format range for 1.21.5 → current (the build reads the numbers from `ids.yml → formats`; looked up when RP1 starts, never guessed).
- On each Minecraft update: bump the format range, test one menu, one item, one glyph, one sound on the oldest and newest client.

---

## 16. Milestones
| # | Item | Depends on | Lane |
|---|---|---|---|
| RP1 | Plumbing: `PackService` (status, capabilities, Floodgate), **forced sending in the configuration phase** + prompt/decline messages from `pack.yml` (`required`, `kick-on-fail`), `/settings` toggle (hidden while required), `ItemDefinition.modelData` → `custom_model_data`, `<icon:…>` tags with fallbacks, per-recipient chat render, PUA chat filter, `pack-sound` in `sounds.yml`; `resourcepack/` skeleton, `ids.yml`, `build_pack.py`, empty pack with pack.png; hosting on the VPS | STYLE2 | common (menu, item, text), modules/menus (settings), tools, resourcepack |
| RP2 | Font icons: currencies, logo, rank badges, rarity tags, pillars, roles; Java fonts + Bedrock glyph sheet; scoreboard/tab/chat/lore use them | RP1, ranks (badges for donor ranks can come later) | resourcepack, modules/hud, chat, cosmetics, style.yml |
| RP3 | Menu backgrounds: space font, generic 3/6-row + number picker, then shop, sell chest, satchel, forge, tool, enchanter, wardrobe, loadout, `/p`, `/profiles`; invisible panes; button icons | RP1, SELL2 (final shop/sell slots) | common/menu, resourcepack |
| RP4 | Item models: tools per tier + skins, books, scroll, blueprints, notes, tag, spawners, set icons, tokens, tome, rare Depths finds, currency icons | RP1, art (§17) | modules/tools, enchants, economy, stacking, wardrobe, depths renderers, resourcepack |
| RP4b | Worn set looks: `equippable.asset_id` per set (`sets.yml → look.asset`), equipment JSON in the build, Java + Bedrock check | RP4, W1, art G65–G80 | modules/wardrobe, resourcepack |
| RP5 | Geyser: Bedrock conversion in the build, custom item mappings v2, glyph sheet, test pass on a Bedrock client | RP2, RP4 | tools/build_pack.py, server/plugins/Geyser-Spigot |
| RP6 | Sounds: 22 events, `pack-sound` keys, Bedrock sound definitions | RP1 (Bedrock part after RP5) | resourcepack, sounds.yml |
| RP7 | HUD: action-bar bars (skill XP, satchel fill), boss bar textures | RP2 | modules/hud, skills, depths, resourcepack |
| RP8 | Crates: key models, crate displays + lid frames, roll background, win sounds | crates module, RP3, RP4, RP6 | crates module, resourcepack |
| RP9 | World: forcefield border texture, optional particle tweaks | RP1 | resourcepack |
| RP10 | Launch: forced pack live (decided), CDN, size audit, full Java + Bedrock test pass, docs in `/help` | RP1–RP9 | docs, server config |
| RP-later | Custom tooltip styles per rarity, Depths drop variants (allowed now that the pack is forced; P2 art) | RP4 | items, resourcepack |

---

## 17. Open questions (defaults apply until answered; also in `OPEN_QUESTIONS.md`)
| Id | Question | Default |
|---|---|---|
| RP-D1 | Java pack optional or required at launch? | **Decided 2026-10-30: forced.** Accept before joining (configuration phase), professional prompt; decline = friendly disconnect; failed download = vanilla fallback (open question). Bedrock is forced by Geyser already. |
| RP-D2 | Send the pack from the plugin (Paper API) or from `server.properties`? | Plugin; `server.properties` stays empty (fallback only). |
| RP-D3 | Item looks through `custom_model_data` strings on vanilla items (fallback-safe) instead of `item_model` with pack models? | Yes, `custom_model_data` strings. |
| RP-D4 | Pack lives in a `resourcepack/` folder in this repo, or its own GitHub repo? | Folder in this repo (one review, one release). |
| RP-D5 | Hosting: `pack.skybornmc.net` on the VPS (nginx), later Cloudflare in front? | Yes. Needs a DNS record from the owner. |
| RP-D6 | Players can switch the pack off/on in `/settings` (no new command)? | Changed by RP-D1: the toggle is hidden while the pack is required; it only shows if staff set `pack.required: false`. |
| RP-D7 | Who makes the art (textures, menu backgrounds, badges, sounds)? | **Decided 2026-10-30: the owner.** Art list: `RESOURCE_PACK_ART_BRIEF.md`. Until a file exists the vanilla look shows (no copied Mojang or third-party textures). |
| RP-D8 | Menu background style: dark charcoal panels with ember corners (matches today's frame)? | Yes. |
| RP-D9 | Bedrock conversion with our own small converter (icons, glyphs, sounds only)? | Yes. |
| RP-D10 | Depths drops keep the vanilla look (only voidstone, raider scrap and wither ash get own icons)? | Yes. |
| RP-D11 | Back/next/previous: pack users get model arrows, others keep the player heads? | Yes. |
| RP-D12 | Tool model changes with tier only; prestige adds a spark overlay; level never changes the model? | Yes. |
| RP-D13 | Status marks (✔ ✘ ★ ⌛) stay Unicode, not glyphs? | Yes (they already work on Bedrock). |
| RP-D14 | Custom world border texture (global, every border)? | Yes, ember pattern. |
| RP-D15 | Custom tooltip backgrounds per rarity (needs a required pack)? | Changed by RP-D1: allowed on Java, P2 art (S02–S08 in the art brief). |
| RP-D16 | Paint the player-inventory half of menus too? | No, top half only. |
| RP-D17 | Rank badges as glyphs in chat and tab (text prefix stays for non-pack viewers)? | Yes. |
| RP-D18 | Download failure with the forced pack: let the player in on the vanilla fallback (and retry next join) instead of kicking? | Yes (a broken host must never lock everyone out). |
| RP-D19 | Worn set looks for Java (RP4b) as part of the first pack release (P1)? | Yes; Bedrock keeps vanilla armor + trims + dye. |
