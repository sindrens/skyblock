# Resource pack GUI art v1: what is in the pack and how to wire it

**Status:** art v2, 31 Oct 2026 (v1 + the owner's second round: new rank badges, new currency icons, simpler icons, crate chests). It is ready for the developer: RP1 → RP3 in `RESOURCE_PACK.md`, queue item GUI-6. Open questions from round 2 are listed in §1.
- This branch adds **art and docs only**. No plugin code changes.
- Generated from `main` at a3966f7 (update110), with the owner's answers from 31 Oct.
- Everything here is original pixel art, drawn as code. Nothing is copied or traced from Mojang.

**In short:**
- **{{NBG}} menu backgrounds** (key screens + one frame set per area of the game).
- **{{NICONS}} icons:** {{NBUTTONS}} menu buttons, {{NFEATURES}} feature icons, 9 shop tabs, 4 currencies.
- **{{NGLYPHS}} font glyphs:** currencies, logo and full-name rank badges.
- **7 crate chests** for /warp crates (3D models, one per crate, look set by rarity).
- Total: {{SIZE}}.
- **Every menu on `main` has a background:** {{NCOV}} menu classes, listed in §9.
- **Vanilla items keep their vanilla textures.** Only our own buttons, feature icons and custom items get art.

---

## 0. Where everything is
```
resourcepack/
  README.md                                  ← short guide to this folder
  java/assets/phoenix/textures/
    gui/menu/<file>.png                      ← 31 menu backgrounds (font phoenix:menu)
    item/menu/<id>.png                       ← menu buttons        → phoenix:menu/<id>
    item/menu/icon/<id>.png                  ← feature icons       → phoenix:menu/icon/<id>
    item/menu/tab/<id>.png                   ← shop tab icons      → phoenix:menu/tab/<id>
    item/currency/<id>.png                   ← currency icons      → phoenix:currency/<id>
    font/icons/<id>.png                      ← glyphs (font phoenix:icons)
    item/crate/block/<crate>.png             ← crate chest textures (64×64)
  java/assets/phoenix/models/item/crate/block/<crate>.json   ← crate chest 3D models
  src/preview/                               ← contact sheets: look here first
    screens.png        12 menus in context (backgrounds + icons, 3x)
    backgrounds.png    all 31 backgrounds
    buttons.png  features.png  tabs_currencies.png  glyphs.png  crates.png
  src/gui-art/                               ← the source: edit here, then regenerate (§13)
    core.js            palette ramps, cartoon shading, outline
    set_buttons.js  set_features.js  set_tabs.js  set_glyphs.js   ← the art (letter maps)
    bg_engine.js  bg_defs.js                 ← the backgrounds (wells, area colours, decor)
    catalog.py         every id: what it is, where it shows, its carrier item today → ids_gui.json
    coverage.py        every menu class → background → coverage.json
    export_pack.js     writes the PNGs and the preview sheets
    build_docs.py      writes the tables in this file
  src/crate-art/crates.js                    ← the crate chests (models, textures, preview)
```
**Related docs:**
- `docs/design/GUI_DESIGN.md`: the layout rules these backgrounds follow.
- `docs/design/SHOP_DESIGN.md`: the shop layout.
- `docs/plans/RESOURCE_PACK.md`: the plumbing (sending, fonts, ids.yml, build script, Geyser).
- `docs/plans/RESOURCE_PACK_ART_BRIEF.md`: still the list for item art (tools, books, sets, keys and so on). Its GUI parts (§B, §C, §D, §J icons, §N) are done here.

---

## 1. Owner decisions in this art
| Decision | Answer | When |
|---|---|---|
| Look | **Phoenix Night**: dark charcoal panel, phoenix-red frame, ember corner flames, dark wells | GUI sketches, 31 Oct |
| Paint the player-inventory half too | **Yes.** Otherwise a dark top sits on a grey bottom. This **changes RP-D16** ("No, top half only"). | 31 Oct |
| Frame line colour per area of the game | **Yes** (§4.1) | 31 Oct |
| Backgrounds | **Key screens + area frames: 31 files**, instead of 52 per-menu backgrounds | 31 Oct |
| Rank badges | **Full names.** The target is ≤ 44 px; TORCHBEARER needs 45 px (§6). | 31 Oct |
| Icon style | **Cartoony, with more personality, suited to the server.** Above all, easy to understand and relevant. | 31 Oct |
| Vanilla items | **Keep vanilla textures.** Only custom things get art. | 31 Oct |
| Staff badge colours | **Owner yellow, slightly orange · Admin light red · Mod light blue · Helper light green · Builder orange** | 31 Oct, round 2 |
| Rank badges | **Better design for all; the better the rank, the more unique; an "enchanted" effect** (§6) | 31 Oct, round 2 |
| Currency icons | **Money stays. Crystals must look like a crystal, shards like a shard, the gem like a gem.** | 31 Oct, round 2 |
| Gems | **Renamed credits. Nothing is called gems any more** (`docs/OWNER_ANSWERS_PACK1.md`) | 31 Oct, round 2 |
| Icons | **Simpler and easier to read: it must not look "too much done"** (§3) | 31 Oct, round 2 |
| In-game items | **Only a custom crate chest per crate at /warp crates, by tier rarity** (§7). Everything else vanilla. | 31 Oct, round 2 |

**Waiting for the owner (round 2 page, "Phoenix Pack Round 2"):** B1 badge shapes, B2 bought-rank colours, B3 how the shimmer works, B4 which ranks shimmer, C1 credits gem colour, C2 where credits are spent, K1 crate chest look, K2 crate keys in crate colours, I1 the simpler icons. The art here already uses the recommended answers; a "No" is a small change in the source.

---

## 2. How a menu is built with the pack (Java)
From the back to the front:
1. **Background:** a glyph in the menu title (font `phoenix:menu`). It paints the panel, the frame, the slot wells and the inventory half.
2. **Frame slots:** they hold invisible panes (`menu/blank`), so the art shows through.
3. **Buttons and feature icons:** our models on the button's own item (`custom_model_data` string). Without the pack, the vanilla item shows.
4. **Vanilla items** (shop goods, sell chest, AH listings): Minecraft's own textures.
5. **Title text:** drawn over the top band.

**Bedrock** gets no backgrounds and no title change. It gets the icons through Geyser and shows them on its own grey wells. Every icon was checked on both wells (see the sheets).

**Technical rules the art depends on:**
- **Title colour:** send the title text in parchment `#E8DCC0` to pack viewers. The client draws titles in dark grey `#404040` by default, which can't be read on the dark band. Bedrock and no-pack viewers keep the default.
- **"Inventory" label:** the client draws this label itself, in dark grey and in the player's language. The art paints a parchment plate behind it (x 6–77, 11 px tall), so it reads in every language. Check one long language in RP3 (for example French "Inventaire").
- **Backgrounds are static.** They can't change per player or per click, so **"ready" and "selected" live on the item:**
  - Ready rewards use the `menu/icon/rewards_ready` model (open chest, gold, sparkles) plus a count.
  - The selected tab uses the enchant glint plus "✔ You are here".
  - Fixed decorations are fine: the hub's Rewards well always has a gold rim, and the Top screen's podium rings never move.
  - This corrects the "gold well" wording in GUI_DESIGN §1.5 and §3.
- **Frame panes:** keep their material, use model `menu/blank`, and **hide their tooltip** (`tooltip_display` with `hide_tooltip`). Hovering the frame then shows nothing.
- **Glyph metrics:** width 176, height `114 + rows × 18`. Start values are ascent 13 and title offset +8 (RESOURCE_PACK §6.1). Tune them once on a test client in RP3.
- **Slot grid:** cell (row r, column c) has its top-left at x = 7 + 18c, y = 17 + 18r. The item covers the inner 16×16.

---

## 3. Art style (so new art matches)
- **Simple first (owner, round 2).** Thousands of players read these at a glance. One object per icon, no extra props, at most one or two sparkles, and only where they mean something (ready, magic). If a detail needs explaining, remove it.
- **Size:** item icons are 16×16. Glyphs are 9 px tall.
- **Outline:** every shape has a 1 px warm dark outline (`#2B1210`). This is the cartoon look, and it keeps icons readable on Bedrock's grey wells.
- **Shading:** a light edge on the top and left, a dark edge on the bottom and right, and one or two white shine pixels. `core.js` does this automatically for every ramp colour.
- **Shapes:**
  - One big, chunky object per icon that fills about 14×14. No tiny details.
  - No words. Numbers appear only on the Buy 1 / 16 / 64 plates and the "123" slip.
- **Colour means something** (GUI_DESIGN §6):

  | Colour | Means |
  |---|---|
  | Green | yes, gain, buy, up |
  | Red | no, danger, down |
  | Gold | reward, money |
  | Ember | movement, back and next |
  | Violet | power, magic |
  | Blue | info |

  Every colour state also has its own **shape** (✔ ✘ + − arrows, padlock), for colour-blind players.
- **Phoenix touches:** phoenix-red roofs, awnings, flags, books and armour; gold trims; ember sparks at the Forge. Fun first, but understanding first of all.
- **Pick the object a player already knows:**

  | Feature | Icon |
  |---|---|
  | Home | house |
  | Travel | signpost |
  | Shop | market stall |
  | Bank | piggy bank |
  | Auction | gavel |
  | Top | trophy |
  | Store | crown |
  | Disband | bomb |

  Test each icon by asking: "would a new player guess what this button does?"

---

## 4. Backgrounds ({{NBG}})

### 4.1 Area colours: the frame line tells you where you are
The colours follow the `/menu` groups. Money is gold, Power is violet and More is the rewards red. Play splits into island green and Depths steel blue.

| Area | Frame line | Corners | Modules |
|---|---|---|---|
| core | ember brown `#B8741E` | red flames | menus (help, settings, You, Travel), tutorial, npcs, profiles, common, box |
| island | green `#4E9A30` | red flames | islands, progression (upgrades, tasks, level), stacking (spawners) |
| economy | gold `#C8941A` | red flames | economy (bank), shop, auction, trade |
| depths | steel blue `#3F86B0` | red flames | depths (/dungeon, satchel) |
| progress | violet `#8A4ACC` | red flames | enchants, wardrobe, skills, achievements, cosmetics, tools (Forge) |
| rewards | red `#D1202F` | gold flames | crates, rewards (/daily), boosters, perks |
| staff | cold steel panel `#1A1E24`, line `#5A6A80` | red corner marks, no flames | moderation, staff, logs, admin screens, every Staff*/Editor* menu |

### 4.2 The files
{{BG}}

**Notes:**
- `frame_core_4` and `frame_core_6_tabs` are spares: no menu uses them today. They stay because MenuLayout supports 4 rows and tabs.
- B01–B08, B11, B17 and B20 keep their codepoints from the art brief. The other per-menu backgrounds (B09–B52) are replaced by the area frames. The codepoints run U+E300–U+E31E. Nothing was built yet, so nothing breaks.
- Crate previews and the crate roll (B32–B40) use `frame_rewards_6` until crate art exists.

---

## 5. Icons ({{NICONS}})

### 5.1 Menu buttons
{{BUTTONS}}

**Changes from the art brief §C:**
- **`minus_1/10/100` and `plus_1/10/100` become `*_small / *_mid / *_big`**, drawn as + / ++ / +++ (and − / − − / − − −). The steps differ per screen (the bank uses 1k / 10k / 50k), so the exact number stays in the item name.
- **`info`** is the blue "?" at slot 48. **`help`** is the guide book at slot 50 on /menu.
- **New:** `main_menu`, `add_inventory`, `buy_1`, `buy_16`, `buy_64`, `fill`, `undo`.

### 5.2 Feature icons
{{FEATURES}}

- **One icon per feature, the same everywhere.** Travel reuses the feature icons:

  | Travel entry | Icon |
  |---|---|
  | My Island | `home` |
  | Depths Gate | `depths` |
  | Market | `shop` |
  | Forge Quarter | `forge` |
  | Crate Yard | `crates` |

- **Player heads stay heads:** You (slot 4), member lists and the Top podium. A player's own skin is the best icon for a player.
- **Members on /is:** it uses PLAYER_HEAD today. A textured head can't carry our model well (RP-D11), so give pack viewers and Bedrock a `paper` carrier with `menu/icon/members`. No-pack Java keeps the heads.

### 5.3 Shop tabs
{{TABS}}

### 5.4 Currencies (menu icons, not real items)
{{CURRENCIES}}

- **Gems are renamed credits** (owner, 31 Oct). The old `currency/gems` icon and glyph U+E202 are gone; credits use U+E204.
- **Credits are an ember-orange cut gem.** That is the colour credits already have in text (`style.yml credits: #FF8C1A`), and it can't be mixed up with the cyan crystals or the pink shards (owner check C1).
- **Crystals** are a crystal cluster (one tall faceted prism and two small ones). **Shards** are a broken, jagged piece. Each currency has its own shape: coin, cluster, broken piece, cut gem.

---

## 6. Font glyphs (font `phoenix:icons`, 9 px tall, ascent 7)
{{GLYPHS}}

- **Rank badges v2 (round 2): the shape tells the group, the colour tells the rank.**

  | Group | Shape | Ranks |
  |---|---|---|
  | Free | grey outline, dark inside | Ashborn |
  | Bought | round pill; **pointed ends from Sunfeather up**; gold top and bottom edge on Firewing and Flamecrest | Hatchling sand · Fledgling ember · Firewing crimson · Flamecrest magenta · Sunfeather gold · Pyrelord dark ember with gold letters · Immortal phoenix gradient |
  | Won (season) | **ribbon ends** (notched) + gold edge | Torchbearer teal · Flameheart violet · Firecrown gold |
  | Staff | **square plate** in the owner's colours | Owner yellow-orange `#FFC21A` · Admin light red `#FF6B6B` · Mod light blue `#6CC4FF` · Helper light green `#7CEB8A` · Builder orange `#FF9A3D` |

  - Colour alone collides with 16 ranks (Builder vs Fledgling, Owner vs Sunfeather/Firecrown, Admin vs Firewing). The shapes fix that, also for colour-blind players.
  - **TORCHBEARER is 45 px wide.** That is the longest name, one pixel over the 44 px target. Every other badge is ≤ 44 px.
- **The enchanted shimmer** (recommended, owner check B3/B4):
  - **Who:** Pyrelord, Immortal, Flameheart and Firecrown. Rare stays special, and chat stays calm.
  - **Java:** a shine sweeps across the badge every ~2.6 s, done by the pack's text shader (`rendertype_text`). The badge glyph is sent in a reserved trigger colour, and the shader adds the moving band only for that colour (the same trick Oraxen's text effects use, supported from 1.21.4). No packets, no lag.
  - **Bedrock, old clients, or a player pack that overrides text shaders:** the still version, with two sparkles baked into the badge frame.
  - **Cost:** one shader file in a pack overlay per client version range; check it on each new Minecraft version (RP2 test list).
- **The colour table in `RANKS_TITLES_ACHIEVEMENTS.md` §A** should follow these colours once the owner answers B1/B2.
- **Codepoints** follow the art brief §J and §N. `RESOURCE_PACK.md` §7.2 still lists the old rank names (Squire, Knight and so on) and needs fixing.

---

## 7. Crate chests at /warp crates (the only custom in-game items)
The owner wants one custom crate chest per crate at the crate yard, its look set by the crate's rarity, "and that's it" for in-game items.

![crates](../../resourcepack/src/preview/crates.png)

| Crate | Rarity (crates.yml) | Bands and lock | Extra detail |
|---|---|---|---|
| Ember | common | white-grey | plain oak chest |
| Cinder | uncommon | green | – |
| Blaze | rare | blue | dark wood, gem on the lid |
| Inferno | epic | purple | glowing seam under the lid |
| Phoenix | legendary | gold | phoenix-red paint, flame on the lid |
| Eclipse | mythic | pink | black with stars, eclipse ring on the lid |
| Conqueror | special (hidden until KOTH) | red | dark iron, crossed swords |

- **One chest shape for all,** so players see "a crate" from far away. The rarity colours are the same ones players see on rewards.
- **Files:** `models/item/crate/block/<crate>.json` (body, lid and latch; `parent: block/block`, so it also renders as an icon in menus) and `textures/item/crate/block/<crate>.png` (64×64). Model string `phoenix:crate/block/<crate>` (RESOURCE_PACK §5.2). Source: `resourcepack/src/crate-art/crates.js`.
- **How they show (plugin, crates M10.3):**
  - Keep the real chest block on each pedestal (build points `crates`), so Bedrock and no-pack players see a normal chest, and the clicks keep working (`PhysicalCrates`).
  - Pack players also get an `ItemDisplay` with the crate model at scale ~1.02, which hides the vanilla chest. An alternative is a per-player block change to air for pack players, which avoids any overlap.
  - Above it, a text display with the crate name in its rarity colour (all players).
- **Later (RP8):** lid-open frames `_open_0..5` for the opening animation and a roll background. Crate keys in their crate colours are proposed (owner check K2).

## 8. Wiring it (for the developer: RP1 → RP3, queue GUI-6)
1. **ids.yml:** add every id from `src/gui-art/ids_gui.json`:
   - models, each with its **carrier materials** (the "Carrier today" columns above);
   - glyphs with codepoints;
   - backgrounds with codepoint and rows.

   List only the carrier/model pairs that are used, so Geyser gets a short mapping list.
2. **build_pack.py:**
   - Write `assets/phoenix/models/item/<path>.json` (`item/generated`, layer0 `phoenix:item/<path>`).
   - Write one vanilla override per carrier, `assets/minecraft/items/<carrier>.json`: a select on the `custom_model_data` string, with the vanilla model as the fallback.
   - Write fonts `phoenix:menu`, `phoenix:icons` and `phoenix:space`.
3. **common/menu (MenuService):** put the model strings on every standard button:
   - back, previous/next, info, close, main_menu / help;
   - confirm/cancel plates;
   - toggles;
   - loading / error / empty / locked;
   - picker plates, min / type / max.

   Frame fillers get `menu/blank` and a hidden tooltip.
4. **menu.yml:** add `model:` to each hub, You and Travel entry (for example `home: {…, model: menu/icon/home}`), so the config stays the one source.
5. **Backgrounds:**
   - `Menu.background()` defaults to `frame_<area>_<rows>`, with the area taken from the module (§4.1, list in §9).
   - These menus override it: hub, island, shop, shop_buy, sell_chest, number_picker, confirm and top.
   - Menus with a tab row in row 0 use the `_tabs` variant.
   - Title = `[space −8][background glyph][space −177][space +8]`, then the title text in parchment, for pack viewers only.
6. **/menu group marks (slots 9, 18, 27, 36):** the coloured panes become `menu/blank`. The background paints the marks.
7. **States:**
   - Rewards with something to claim → `menu/icon/rewards_ready` + count.
   - Selected tab → glint + "✔ You are here".
   - Island visitors locked → `menu/icon/visitors_locked`.
   - Island chat on → glint.
8. **Bedrock (RP5):** Geyser custom item mappings v2 for the same carrier/model pairs. No backgrounds, no title colour.
9. **Tests:**
   - Every model string the code uses exists in `pack-ids.yml`.
   - Every `Menu` subclass resolves to a background that exists. The test fails when a new menu is added without one.
10. **Test pass (the owner's S2 rule):** one Java checklist and one Bedrock checklist.
    - Screens: hub, You, Travel, /is, shop + buy page, /sell, amount picker, confirm, Top, one staff screen.
    - GUI scale 2, 3 and 4.
    - English and one long language.

---

## 9. Every menu → its background ({{NCOV}} menu classes on `main`)
How the list was made:
- Every class in `modules/*` and `common` that extends `Menu` or `PagedMenu`. PagedMenu is always 6 rows.
- Moderation's multi-menu files are split into their classes.
- Base classes and a test were left out.
- {{NBGUSED}} of the 31 backgrounds are used.

{{COVERAGE}}

---

## 10. Changes this makes to other docs
- **RESOURCE_PACK.md:**
  - RP-D16 → "Yes: paint the player-inventory half too (Phoenix Night, owner 31 Oct)".
  - The last bullet of §6.1 changes to match.
  - §5.3 (button list) and §6.2 (per-menu backgrounds) point here.
  - §7.2 rank codepoints follow the art brief §N.
- **RESOURCE_PACK_ART_BRIEF.md:**
  - §B, §C, §D, the icon rows of §J, and §N are done here (generated v1).
  - §0.3 style is decided (§3 here).
  - The brief stays the to-do list for item art, crates, HUD, sounds and pack icons.
- **GUI_DESIGN.md §1.5 and §3:** "gold well" for selected or ready means the item's look (glint, ready model, count), because backgrounds can't change per player.

---

## 11. Last improvements (found while drawing; most are already in the art)
1. **Parchment title text for pack viewers.** Without it, titles are dark on dark (§2).
2. **A parchment plate under the client's "Inventory" label**, so it is readable in every language (§2).
3. **Hide frame tooltips**, so hovering the frame shows nothing (§2).
4. **"Ready" and "selected" live on the item, not the well** (§2). This keeps one background per screen.
5. **Generic amount plates** (small / mid / big) instead of numbered ones. They fit every picker (§5.1).
6. **One icon per feature,** Travel included. There is a calendar for "This week" on the Top screen, so the hourglass keeps meaning "Loading".
7. **The Main menu button uses the 4 /menu group colours,** so it quietly teaches the hub's groups.
8. **Frame line colours follow the /menu groups,** so players always know which part of the game they are in. Staff screens look cold and different at a glance.
9. **The store stays calm:** a crown icon, no glow and no gold well (GUI_DESIGN §6).
10. **Badges:** full names, readable at chat size. Owner check on the won-rank and staff colours (§6).
11. **Check on a real client in RP3:**
    - title ascent and offset;
    - the "Inventory" plate width in long languages;
    - icon readability at GUI scale 2;
    - Bedrock's grey wells;
    - item counts drawn over icons (Undo seconds, Rewards count).

---

## 12. Still missing (later batches, not GUI v1)
- **Icons for hidden or unbuilt features:** quests, pets, events, black market, collection, loadout, satchel, sigil, cosmetics editor. Draw each one when its feature ships.
- **Crates:** preview and roll backgrounds (art brief B32–B40), lid-open frames, and keys if the owner says yes (K2). The crate chests themselves are done (§7).
- **Item families (§E–§M): dropped.** The owner decided (31 Oct, round 2) that the only custom in-game items are the crate chests; tools, books, sets, tokens, money notes, spawners and Depths drops keep the vanilla look.
- **More glyphs:** rarity tags (§O), pillar and role glyphs (§P, §Q), status glyphs (the default stays Unicode, RP-D13), and `logo_wide` for the scoreboard title.
- **HUD and boss bars** (§R), **sounds** (§T), and **pack.png / Bedrock pack_icon** (from `branding/`).

---

## 13. Regenerate after an edit
You need Node with Playwright (Chromium) and Python 3. Run these from the repo root:
```
python3 resourcepack/src/gui-art/catalog.py resourcepack/src/gui-art/ids_gui.json
python3 resourcepack/src/gui-art/coverage.py resourcepack/src/gui-art/menus_main.txt resourcepack/src/gui-art/ids_gui.json resourcepack/src/gui-art/coverage.json
node resourcepack/src/gui-art/export_pack.js            # PNGs + preview sheets
node resourcepack/src/crate-art/crates.js               # crate chest models, textures, preview
python3 resourcepack/src/gui-art/build_docs.py          # tables in this file
```
- **Change an icon:** edit its letter map in `set_*.js`. `.` is transparent. Letters map to the palette ramps in `core.js`, and the outline and shading are automatic.
- **Quick look while drawing:** `node resourcepack/src/gui-art/preview.js /tmp/x.png menu/icon/` (any id prefix).
- **New menu class:** add it to `menus_main.txt`, then give it an area in `coverage.py`.
- **Commit the PNGs.** The generator is a dev tool; `build_pack.py` only reads the PNGs.
