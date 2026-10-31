# Resource pack art: menus, rank badges and crates

**Status:** art v3, 31 Oct 2026 (v1 + the owner's rounds 2 and 3: rank badges as a font, new currency icons, simpler icons, custom crate chests and keys). It is ready for the developer: RP1 → RP3 in `RESOURCE_PACK.md`, queue item GUI-6. All owner answers so far are in §1.
- This branch adds **art and docs only**. No plugin code changes.
- Generated from `main` at a3966f7 (update110), with the owner's answers from 31 Oct.
- Everything here is original pixel art, drawn as code. Nothing is copied or traced from Mojang.

**In short:**
- **{{NBG}} menu backgrounds** (key screens + one frame set per area of the game).
- **{{NICONS}} icons:** {{NBUTTONS}} menu buttons, {{NFEATURES}} feature icons, 9 shop tabs, 4 currencies.
- **{{NGLYPHS}} font glyphs:** currencies and the phoenix logo.
- **7 crate chests and 7 matching keys** for /warp crates (3D models, one per crate, look set by rarity).
- **Rank badges as a font** (3 fonts), so rank names stay editable in LuckPerms.
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
    item/crate/block/<crate>.png             ← crate chest textures (128×128)
    item/crate/key/<crate>.png               ← crate keys (16×16)
    font/badge/*.png                         ← rank badge fonts
  java/assets/phoenix/models/item/crate/block/<crate>.json   ← crate chest 3D models
  java/assets/phoenix/font/badge*.json       ← the three badge fonts
  src/preview/                               ← contact sheets: look here first
    screens.png        12 menus in context (backgrounds + icons, 3x)
    backgrounds.png    all 31 backgrounds
    buttons.png  features.png  tabs_currencies.png  glyphs.png  badges.png  crates.png  crate_keys.png
  src/gui-art/                               ← the source: edit here, then regenerate (§13)
    core.js            palette ramps, cartoon shading, outline
    set_buttons.js  set_features.js  set_tabs.js  set_glyphs.js   ← the art (letter maps)
    bg_engine.js  bg_defs.js                 ← the backgrounds (wells, area colours, decor)
    catalog.py         every id: what it is, where it shows, its carrier item today → ids_gui.json
    coverage.py        every menu class → background → coverage.json
    export_pack.js     writes the PNGs and the preview sheets
    build_docs.py      writes the tables in this file
  src/crate-art/crates.js                    ← the crate chests and keys (models, textures, 3D preview)
  src/badge-font/badges.py                   ← the rank badge fonts (+ ranks.json: look per LuckPerms group)
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
| Icon style | **Cartoony, with more personality, suited to the server.** Above all, easy to understand and relevant. | 31 Oct |
| Vanilla items | **Keep vanilla textures.** Only custom things get art. | 31 Oct |
| Staff badge colours | **Owner yellow, slightly orange · Admin light red · Mod light blue · Helper light green · Builder orange** | 31 Oct, round 2 |
| Rank badges | **Better design for all; the better the rank, the more unique; an "enchanted" effect** (§6.2) | 31 Oct, round 2 |
| Rank style | **"Phoenix #3" looked best** (read as concept *3. Phoenix Flame* in `branding/concepts`): dark core, glowing letters, flame spikes | 31 Oct, round 3 |
| Rank names | **Must be changeable in LuckPerms without breaking anything** → badges are a font, the name comes from LuckPerms (§6.2) | 31 Oct, round 3 |
| Rank colours | **Not so similar; the right rank colours** (§6.2) | 31 Oct, round 3 |
| Shimmer | **B3 yes:** moving shine on Java (pack text shader), still sparkles on Bedrock. **B4 yes:** Pyrelord, Immortal, Flameheart, Firecrown | 31 Oct, round 3 |
| Currency icons | **Money stays. Crystals must look like a crystal, shards like a shard.** Credits: **the ticket** (C1: "I liked the other credits symbol") | 31 Oct, rounds 2–3 |
| Gems | **Renamed credits. Nothing is called gems any more.** Credits are spent **in /store only** (C2); /shop stays money (`docs/OWNER_ANSWERS_PACK1.md`) | 31 Oct, rounds 2–3 |
| Icons | **Simpler and easier to read: it must not look "too much done"** (§3). I1: the simpler set is approved | 31 Oct, rounds 2–3 |
| In-game items | **Only a custom crate chest per crate at /warp crates, by tier rarity** (§7). Everything else vanilla. | 31 Oct, round 2 |
| Crate chests | **Fully custom models**; the owner's pictures were inspiration only (K1) | 31 Oct, round 3 |
| Crate keys | **Must match their crate** (K2) | 31 Oct, round 3 |

**Round 2 page answered** (B3 R, B4 R, C1 other: the ticket, C2 R, K1 other: fully custom, K2 other: keys match the crates, I1 R). B1 and B2 were replaced by the round-3 rank feedback above.

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
- **Credits are an ember-orange ticket with a white star** (owner C1 picked it over the gem). Ember is the colour credits already have in text (`style.yml credits: #FF8C1A`).
- **Crystals** are a crystal cluster (one tall faceted prism and two small ones). **Shards** are a broken, jagged piece.
- **Every currency has its own shape:** coin, crystal cluster, broken shard, ticket. Nothing can be mixed up, even without colour.

---

## 6. Font glyphs and rank badges

### 6.1 Glyphs (font `phoenix:icons`, 9 px tall, ascent 7)
{{GLYPHS}}

- The old baked-in rank badge glyphs (U+E220–U+E234) are **retired**. Rank badges are a font now (§6.2).
- `RESOURCE_PACK.md` §7.2 still lists old rank codepoints; it should point here.

### 6.2 Rank badges: a font, so names stay editable in LuckPerms
![badges](../../resourcepack/src/preview/badges.png)

**What the owner asked for (round 3):**
- the "Phoenix #3" look (dark core, glowing letters, flame spikes);
- rank names that can be changed in LuckPerms without breaking anything;
- colours that are clearly different.

**How it works:**
1. **The name comes from LuckPerms:** the group's display name (`lp group hatchling setdisplayname "Hatchling"`).
   - Rename it any time (`… setdisplayname "Baby Phoenix"`). The badge shows the new name within a second, because `LuckPermsRanks` already refreshes on every change.
2. **The look comes from the group id, never from the name.** One config entry per group sets the font, the end caps, the colour, the cap colour, the shine and the Bedrock colour.
   - The data is in `resourcepack/src/badge-font/ranks.json`; move it into `style.yml` as `badges:`.
   - A group with no entry gets the plain Ashborn look, so nothing ever breaks.
3. **The plugin draws the name in a badge font.** Each letter picture carries its own piece of the plate, so any name becomes a badge.
   - The plugin adds the two end caps and a −1 px space (U+F801) between letters, because Minecraft leaves a 1 px gap after every bitmap glyph.
   - Example, Hatchling: `<font:phoenix:badge><#FF9A4D>`, then U+E002 (left cap), then `H`, U+F801, `A`, U+F801 … `G`, then U+E003 (right cap), then `</font>`.
   - Turn the text shadow off for the badge (`<shadow:#00000000>`, clients 1.21.4+).
4. **The text colour tints the badge.** Minecraft multiplies glyph pixels by the text colour, so one font serves every colour.
   - A gradient (Immortal) is a normal MiniMessage `<gradient>`: each letter gets its own colour.
5. **Characters:** A–Z, 0–9, space and `+ - ! ' .`.
   - Lower case is drawn as the same capitals.
   - The plugin leaves any other character out.
   - Long names make longer badges. Keep names to about 12 letters for chat.

**Fonts** (`assets/phoenix/font/` + `textures/font/badge/`):

| Font | Look | For |
|---|---|---|
| `phoenix:badge` | dark core, rim and letters glow in the rank colour (Phoenix Flame) | players |
| `phoenix:badge_shine` | the same pictures at alpha 254 | ranks with the moving shine |
| `phoenix:badge_solid` | light plate in the rank colour, dark letters, square ends | staff |

**End caps** (private chars, left/right):

| Cap | Chars | Ranks |
|---|---|---|
| `plain` | E000/E001 | Ashborn |
| `ember` (one flame tip) | E002/E003 | Hatchling, Fledgling |
| `wing` (two feathers) | E004/E005 | Firewing, Flamecrest |
| `sun` (three rays) | E006/E007 | Sunfeather, Pyrelord |
| `phoenix` (five rays) | E008/E009 | Immortal |
| `ribbon` (notched, an award) | E00A/E00B | won ranks |
| `square` | E00E/E00F | staff |

The flames grow with the tier, so a better rank is visibly more special.

**Colours: every player rank has its own hue.** Staff are solid plates, so a staff colour never looks like a player colour.

| Group | Family | Colour | Shine | Bedrock |
|---|---|---|---|---|
| default (Ashborn) | free | ash grey `#B5ABA4` | – | `&7` |
| hatchling | bought | ember orange `#FF9A4D` | – | `&6` |
| fledgling | bought | lime `#9BE84A` | – | `&a` |
| firewing | bought | sky blue `#4FB4FF` | – | `&9` |
| flamecrest | bought | violet `#B06BFF` | – | `&5` |
| sunfeather | bought | gold `#FFD21A` | – | `&e` |
| pyrelord | bought | crimson `#FF3B4A` | yes | `&c` |
| immortal | bought | phoenix gradient `#FFE27A → #FF8C1A → #FF2D3F`, gold caps | yes | `&e&l` → `&6&l` → `&c&l` |
| torchbearer | won | teal `#2EE6D0` | – | `&3` |
| flameheart | won | pink `#FF5FD2` | yes | `&d` |
| firecrown | won | white letters, gold caps | yes | `&f&l` |
| owner | staff | yellow, slightly orange `#FFC21A` | – | `&e&l` |
| admin | staff | light red `#FF6B6B` | – | `&c&l` |
| mod | staff | light blue `#6CC4FF` | – | `&9&l` |
| helper | staff | light green `#7CEB8A` | – | `&a&l` |
| builder | staff | orange `#FF9A3D` | – | `&6&l` |

**The moving shine** (B3/B4, yes):
- The pack's text shader (`rendertype_text`) adds a moving light band to glyph pixels whose alpha is 254. Only `phoenix:badge_shine` has those pixels, so no other text can trigger it.
- No packets and no lag; it runs on the client.
- The same idea as Oraxen's text effects, which work from 1.21.4.
- **Bedrock, older clients, or a player pack that replaces the text shader:** the badge just shows still.
- **Cost:** one shader file per client version range, in a pack overlay. Check it on each new Minecraft version (RP2 test list).

**Bedrock and no-pack viewers** get `[Name]` as plain text in the Bedrock colour from the table. Bedrock has only the 16 legacy colours, so staff are bold to stay apart.

**Follow-ups:**
- The colour table in `RANKS_TITLES_ACHIEVEMENTS.md` §A should follow these colours.
- The player name colour in chat and tab should use the same colour.

---

## 7. Crate chests at /warp crates (the only custom in-game items)
The owner wants a custom crate chest per crate at the crate yard, its look set by the crate's rarity, "and that's it" for in-game items.
- **Round 3:** the chests are fully custom models. His two pictures were inspiration only.
- **The keys must match their crates.**

![crates](../../resourcepack/src/preview/crates.png)
![keys](../../resourcepack/src/preview/crate_keys.png)

**One family, so players see "a crate" from far away.** Every chest has:
- a wooden body and lid;
- metal corner posts that stick out;
- a belt in the rarity colour across the lid seam;
- metal bars along the lid's top edges;
- a lock in front.

Each tier then adds one thing you can see from a distance:

| Crate | Rarity (crates.yml) | Body | What makes it stand out |
|---|---|---|---|
| Ember | common | oak | plain: iron frame, white belt |
| Cinder | uncommon | mossy green wood | vines hanging over the front and sides, green crystal on top |
| Blaze | rare | blue wood | gold belt, arched lid, gold feet, blue crystal on top |
| Inferno | epic | purple wood | heavy dark frame, glowing purple seam, side handles, purple crystal |
| Phoenix | legendary | phoenix red | gold frame and feet, arched lid, **wings on both sides, a flame on top**, glowing gold seam |
| Eclipse | mythic | black with stars | pink belt and glow, arched lid, **eclipse ring on top** |
| Conqueror | special (hidden until KOTH) | dark iron | red belt and glow, **iron spikes with red tips**, red skull lock |

**Keys** (`textures/item/crate/key/<crate>.png`, model `phoenix:crate/key/<crate>` on the key's tripwire hook, RESOURCE_PACK §5.2):
- A ring bow in the crate's colour with the crate's crystal in it, a metal shaft and a chunky bit.
- Plus the crate's one detail: vines (Cinder), purple spark (Inferno), flame (Phoenix), stars (Eclipse), red spikes (Conqueror). So a key looks like a piece of its chest.

**Files:**
- `models/item/crate/block/<crate>.json`: 18–30 elements. It uses `parent: block/block`, so it also renders as a 3D icon in menus (crate preview, /crates).
- `textures/item/crate/block/<crate>.png`: 128×128.
- The source is `resourcepack/src/crate-art/crates.js`. It writes the models, textures, keys and a 3D preview, rendered with three.js using the same faces and UVs as the game.

**How they show in game (plugin, crates M10.3):**
- Keep the real chest block on each pedestal (build points `crates`). Bedrock and no-pack players see a normal chest, and the clicks keep working (`PhysicalCrates`).
- Pack players also get an `ItemDisplay` with the crate model, scale about 1.05, facing the walkway, which covers the vanilla chest. A per-player block change to air for pack players avoids any overlap, if needed.
- The front is the model's south side (+z); rotate the display so it faces the path.
- Above the chest, a text display shows the crate name in its rarity colour, for all players.

**Later (RP8):** lid-open frames for the opening animation (the lid is already a separate part), and a roll background.

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
- **Crates:** preview and roll backgrounds (art brief B32–B40) and lid-open frames. The crate chests and keys are done (§7).
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
node resourcepack/src/crate-art/crates.js               # crate chests + keys (needs three.js: THREE_JS=path/to/three.min.js)
python3 resourcepack/src/badge-font/badges.py           # rank badge fonts + badges.png
python3 resourcepack/src/gui-art/build_docs.py          # tables in this file
```
- **Change an icon:** edit its letter map in `set_*.js`. `.` is transparent. Letters map to the palette ramps in `core.js`, and the outline and shading are automatic.
- **Quick look while drawing:** `node resourcepack/src/gui-art/preview.js /tmp/x.png menu/icon/` (any id prefix).
- **New menu class:** add it to `menus_main.txt`, then give it an area in `coverage.py`.
- **Commit the PNGs.** The generator is a dev tool; `build_pack.py` only reads the PNGs.
