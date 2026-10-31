# Resource Pack Art Brief (ARTBRIEF)

> **31 Oct 2026: the GUI part is done.** Menu backgrounds (§B), menu buttons (§C), feature icons (§D), the currency icons and glyphs (§J) and the rank badges and small logo (§N) are drawn: v1, generated cartoon art in `resourcepack/`. The ids and where each one shows are in **`docs/plans/RESOURCE_PACK_GUI.md`**. This brief stays the to-do list for everything else (tools, books, sets, keys, crates, notes, spawners, Depths drops, rarity tags, HUD, sounds, pack icons).

> **For:** the owner, who draws every texture himself. **Status:** 2026-10-30, written from the built modules (tool tiers, `sets.yml`, crates, Depths drops, menus) and `RESOURCE_PACK.md` (ids, glyph sizes, milestones).
> **Owner decisions (30 Oct):** the Java pack is **forced** (accept before joining, professional prompt); custom textures on crates, GUIs, sets/wardrobe and tools; size is not critical but optimise; the owner makes the art; the style is not fixed yet (§0.3).
> Work top to bottom through the P1 items first. Each line has an id (`B07`) you can tick off or mention ("B07 done").

## Summary

| Group | What | Entries | Files | P1 entries | P1 files |
|---|---|---|---|---|---|
| A | [Pack basics](#a-pack-basics) | 2 | 2 | 2 | 2 |
| B | [Menu backgrounds (GUI)](#b-menu-backgrounds-gui) | 52 | 52 | 33 | 33 |
| C | [Menu buttons](#c-menu-buttons) | 34 | 34 | 34 | 34 |
| D | [Feature icons](#d-feature-icons) | 29 | 29 | 0 | 0 |
| E | [Tools (Forge)](#e-tools-forge) | 61 | 61 | 56 | 56 |
| F | [Enchant books and scroll](#f-enchant-books-and-scroll) | 7 | 7 | 0 | 0 |
| G | [Wardrobe sets](#g-wardrobe-sets) | 86 | 102 | 86 | 102 |
| H | [Crate keys and crate blocks (7 crates)](#h-crate-keys-and-crate-blocks-7-crates) | 14 | 42 | 14 | 42 |
| I | [Crate animations](#i-crate-animations) | 6 | 6 | 0 | 0 |
| J | [Currencies (crystals, gems, shards, money, credits)](#j-currencies-crystals-gems-shards-money-credits) | 10 | 10 | 5 | 5 |
| K | [Money notes and cosmetic items](#k-money-notes-and-cosmetic-items) | 4 | 4 | 0 | 0 |
| L | [Depths drops and finds](#l-depths-drops-and-finds) | 35 | 35 | 0 | 0 |
| M | [Spawner tiers](#m-spawner-tiers) | 5 | 5 | 0 | 0 |
| N | [Rank badges and logo glyphs](#n-rank-badges-and-logo-glyphs) | 14 | 14 | 0 | 0 |
| O | [Rarity tags](#o-rarity-tags) | 7 | 7 | 0 | 0 |
| P | [Pillar icons](#p-pillar-icons) | 6 | 6 | 0 | 0 |
| Q | [Island roles and chat tags](#q-island-roles-and-chat-tags) | 7 | 7 | 0 | 0 |
| R | [Scoreboard, action bar and boss bar glyphs](#r-scoreboard-action-bar-and-boss-bar-glyphs) | 11 | 38 | 0 | 0 |
| S | [Borders, frames and tooltips](#s-borders-frames-and-tooltips) | 8 | 15 | 0 | 0 |
| T | [Custom sounds](#t-custom-sounds) | 29 | 29 | 8 | 8 |
| | **Total** | **427** | **505** | **238** | **282** |

- **P1** = needed for the first pack release: GUIs (backgrounds, buttons, currency icons on buttons), tools, crates (keys, blocks, crate sounds) and sets (icons, worn looks, tokens, tomes). **P2** = later releases; the game shows the vanilla look (or the safe text symbol) until the file exists, so nothing breaks while a P2 item is missing.
- "Files" counts PNG/OGG files: a crate block is 5 faces, a worn set look is 2 layers, a bar is 11 fill states.

---

## 0. Before you draw

### 0.1 Where files go and how ids map
- Put your work in `resourcepack/java/assets/phoenix/` (folder from RESOURCE_PACK §12). Sources (`.aseprite`/`.psd`) go in `resourcepack/src/`, they are never packed.
- The **id** column is the file path under `assets/phoenix/textures/` (or `assets/phoenix/` for sounds). Example: `item/tool/pickaxe/iron.png` → model string `phoenix:tool/pickaxe/iron` (the `item/` prefix is dropped). Paths starting with `minecraft:` replace a vanilla file in `assets/minecraft/textures/`.
- `· U+E2xx` after a font file is its character (codepoint). The developer wires every id into `resourcepack/ids.yml`; you only deliver the PNG/OGG at the right path. A file that is missing just shows the vanilla look.
- Names: lowercase, `a-z 0-9 _`, exactly as listed.

### 0.2 Technical rules (all files)
- PNG, 8-bit RGBA, no colour profile, no metadata. Fully transparent background. Avoid half-transparent pixels on item icons (they render as cutouts); menu backgrounds and tooltips may use them.
- Item icons **16×16**. 32×32 only where the size says "32×32 ok" (legendary/mythic tools, skins, tier IV sets, crate keys and crate faces). Never other sizes.
- Animation (only where the style note allows it): a vertical strip of ≤ 8 frames of the same size + a `.png.mcmeta` (the developer can write it, tell him the frame time).
- Font glyphs: 9 px tall (`ascent 7`), drawn on a transparent canvas exactly the listed size; they sit on the text line, so keep the bottom 1–2 px for descenders only.
- **Original art only.** You may look at vanilla for the size and the shading feel, but never copy, trace or recolour Mojang or third-party textures (RP-D7).
- Bedrock: item icons, glyphs and sounds are converted automatically (2D only). Menu backgrounds, HUD bars, worn armor looks, tooltips and crate blocks are Java only, so you don't need a Bedrock version of anything.
- Size budget (optimise, not critical): Java pack target ≤ 8 MB, Bedrock ≤ 5 MB. Flat colours compress best; avoid noise/dithering on big backgrounds.

### 0.3 Style (decided 31 Oct: cartoon, see RESOURCE_PACK_GUI.md §3)
> The owner chose cartoony icons with personality: 1 px warm dark outline (`#2B1210`), light top/left and dark bottom/right edges, one chunky object per icon, colour + shape for every state. New item art should match the GUI v1 art in `resourcepack/`.

- **Identity: phoenix red/ember.** Red is the frame, ember orange and flame gold are the accents, charcoal and parchment are the base, rarity colours mark rarity. Palette (UI_STYLE §1):

| Swatch | Hex | Use in art |
|---|---|---|
| Phoenix Red (`brand`) | `#FF2D3F` | Main accent, logos, highlights |
| Frame red | `#D1202F` | Borders, frame lines |
| Ember | `#FF8C1A` | Glow, corner flames, secondary accents |
| Flame gold | `#FFD21A` | Highlights, fills, tips |
| Charcoal | `#2B2B2B` | Panel backgrounds |
| Parchment | `#E8DCC0` | Paper, scrolls, light panels |
| Locked grey | `#5E5E66` | Empty sockets, locked states |

- Rarity colours: common `#F2F2F2`, uncommon `#4DFF6A`, rare `#3DA5FF`, epic `#C05CFF`, legendary `#FFB414`, mythic `#FF4FD8`, special `#FF2D3F`. Currency colours: money `#4DFF88`, crystals `#33E0FF`, gems `#2EFFC0`, shards `#FF6B8A`, credits `#FF8C1A`.
- Network art (pack icon, SkybornMC S glyph) uses the frost-blue network palette instead (`#E4EBF0`, `#9FC6E8`, `#6CA1D6`, `#49649E`).
- **Style pilot first (recommended):** before drawing everything, draw 5 pieces in your chosen style: `B01` generic 6-row background, `E03` iron pickaxe, `H01` Ember key, `G02` Delver's chestplate, `J01` money icon. The developer puts them in a test pack, you look at them in game on a normal GUI scale and on a phone (Bedrock icons), then lock the style. It is much cheaper to change 5 files than 400.
- Two directions that fit the current look (pick one or mix): **(a) Vanilla+**: vanilla-like pixel density and shading, warmer colours, ember rim light (safe, fits Bedrock). **(b) Crisp fantasy**: darker outlines, stronger contrast, more saturated fire accents (stands out more, risk of looking busy at 16 px).

### 0.4 Delivery order
1. Style pilot (5 files above). 2. Generic frames B01–B04 and buttons C. 3. Tools E (P1). 4. Crates H + crate menus + crate sounds. 5. Sets G (icons, then worn looks). 6. Remaining P1 menus. 7. P2 in any order (suggestion: currency glyphs J and rarity tags O first, they show everywhere).

---

## A. Pack basics

Two small images that show before anything else.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| A01 | `pack.png` (pack root) | Java pack icon in the resource-pack list and on the join prompt | 128×128 | Join prompt, Options → Resource Packs | P1 | SkybornMC S logo (already in `branding/`), network frost blue: the one place the network look is used, not phoenix red. |
| A02 | `bedrock/pack_icon.png` | Bedrock pack icon | 256×256 | Bedrock: Settings → Global Resources, download screen | P1 | Same art as `pack.png`, scaled up cleanly (redraw edges, no blurry upscale). |

## B. Menu backgrounds (GUI)
> **Done (v1), replaced:** 31 backgrounds (key screens + area frames) instead of the 52 below. List, codepoints and the menu → background table: RESOURCE_PACK_GUI.md §4 and §8. The inventory half is painted too (RP-D16 changed).


Java only (Bedrock keeps the vanilla panes). One PNG per menu, drawn at **1× pixel scale** on the full chest canvas **176 × (114 + rows × 18)**: 3 rows = 176×168, 4 rows = 176×186, 6 rows = 176×222. Paint only the top part (title band + chest rows, height 17 + rows × 18); leave the bottom ~97 px (player inventory) **fully transparent** (RP-D16). See the slot template right below the table. Codepoints are in font `phoenix:menu`.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| B01 | `gui/menu/generic_6.png` · U+E300 | Generic 6-row frame (every list/paged menu without its own art) | 176×222 | Every paged menu: members, warps, AH lists, blueprints, skins, help topics, logs … | P1 | The house style. Charcoal panel, ember corner flames, dark slot wells. Nav wells at 45/48/49/50/53 slightly raised. Everything else copies this. |
| B02 | `gui/menu/generic_3.png` · U+E301 | Generic 3-row frame (small choices) | 176×168 | Member/invite/note actions, `/trade` money, merge look pick, small 3-row screens | P1 | Same style as generic_6, shorter. Inner slots 10–16 as wells. |
| B03 | `gui/menu/number_picker.png` · U+E302 | Number picker (amount screens) | 176×222 | Withdraw, bank deposit, any "choose an amount" screen | P1 | Value plate behind slot 22, red minus wells 19–21, green plus wells 23–25, confirm strip 39–41. |
| B04 | `gui/menu/confirm.png` · U+E303 | Confirm dialog | 176×168 | Every "Are you sure?" (disband, gem buys, merges, prestige) | P1 | Green zone behind 10–12, neutral plate behind 13, red zone behind 14–16. Must read as yes/no without text. |
| B05 | `gui/menu/generic_4.png` · U+E304 | Generic 4-row frame | 176×186 | Staff notify menu (and any future 4-row menu) | P2 | generic_6 style, 4 rows. |
| B06 | `gui/menu/staff_6.png` · U+E305 | Staff 6-row frame | 176×222 | `/staff`, `/admin`, `/logs`, `/inspect`, reports, appeals, economy watch | P2 | Cold dark steel instead of charcoal, thin red line. Staff screens should look different from player screens at a glance. |
| B07 | `gui/menu/staff_3.png` · U+E306 | Staff 3-row frame | 176×168 | Staff confirm/outcome/ack screens | P2 | Same as staff_6, 3 rows. |
| B08 | `gui/menu/hub.png` · U+E307 | `/menu` main hub | 176×222 | `/menu` (first screen players learn) | P1 | The showpiece: phoenix wings spread across the top band, profile well at slot 4, feature wells in the grid. |
| B09 | `gui/menu/help.png` · U+E308 | `/help` main | 176×222 | `/help` | P2 | Open book / parchment centre, topic wells 11–15, 20–24, 29–33, 38–42. |
| B10 | `gui/menu/settings.png` · U+E309 | `/settings` | 176×222 | `/settings` | P2 | Comparator/gear motif in the corners, toggle wells in rows. |
| B11 | `gui/menu/island.png` · U+E30A | `/is` island menu | 176×222 | `/is` | P1 | Floating island silhouette in the top band; wells 10–16, 19–25, leave well at 40 tinted red. |
| B12 | `gui/menu/island_settings.png` · U+E30B | `/is settings` | 176×222 | `/is settings` | P2 | Sky and clouds in the top band, toggle wells. |
| B13 | `gui/menu/island_permissions.png` · U+E30C | `/is permissions` matrix | 176×222 | `/is permissions` | P2 | Grid lines between role columns so the matrix reads as a table. |
| B14 | `gui/menu/island_stats.png` · U+E30D | `/is stats` / top | 176×222 | Island stats and member stats | P2 | Trophy/podium motif, gold accents. |
| B15 | `gui/menu/bank.png` · U+E30E | `/bank` | 176×222 | `/bank` (personal left, island right) | P2 | Vault door look; divider column 13/22/31/40 as a metal bar between the two halves. |
| B16 | `gui/menu/auction.png` · U+E30F | `/ah` main list | 176×222 | `/ah` | P1 | Market hall: gold-trimmed panel, auction gavel in the top band. |
| B17 | `gui/menu/shop.png` · U+E310 | `/shop` category grid | 176×222 | `/shop` | P1 | Market stall awning (red/cream stripes) in the top band; summary well at 4, sell well at 51. |
| B18 | `gui/menu/shop_category.png` · U+E311 | `/shop` quick-buy grid | 176×222 | Inside a shop category | P1 | Same awning, thinner; price plate under the grid. |
| B19 | `gui/menu/shop_amount.png` · U+E353 | Shop amount screen (Bedrock tap / shift-click) | 176×222 | Buy-amount screen | P1 | Item plate at 22, minus 19–21, plus 23–25, type-amount 31, cancel 37, buy plate 39–41. |
| B20 | `gui/menu/sell_chest.png` · U+E312 | `/sell` sell chest | 176×222 | `/sell` (SELL2: 4 input rows + button bar) | P1 | Wooden crate interior behind rows 0–3 (slots 0–35, no frame there!), divider row 36–44, gold Sell plate 52, total plate 47, bonus 51, sell-inventory 46. |
| B21 | `gui/menu/satchel.png` · U+E313 | Satchel (paged) | 176×222 | Depths satchel | P1 | Leather bag interior, stitched edges, fill gauge drawn in the top band. |
| B22 | `gui/menu/satchel_item.png` · U+E314 | Satchel item actions | 176×168 | Click an item in the satchel | P1 | Small leather panel; item well 13, sell 12, take 14/16, back 10. |
| B23 | `gui/menu/forge.png` · U+E315 | `/forge` | 176×222 | `/forge` and the Forge NPC | P1 | Anvil and glowing coals; kind tabs 2–6 in the top band, tier wells 10–34, starter 47, blueprints 51. |
| B24 | `gui/menu/tool.png` · U+E316 | `/tool` (living tool) | 176×222 | `/tool` while holding a custom tool | P1 | Tool on a display stand at 13, level 11, stats 15; five perk sockets 29–33 (the 5th one a prestige ★ socket). |
| B25 | `gui/menu/enchanter.png` · U+E317 | `/enchanter` main | 176×222 | `/enchanter` and the Enchanter NPC | P1 | Purple runes, book stand, candle light; action wells 11–15, 21/23, 30/32. |
| B26 | `gui/menu/enchant_slots.png` · U+E318 | Enchant slot screen | 176×222 | Applying a book to a tool slot | P1 | Tool at 13 with glowing rune sockets 29–33. |
| B27 | `gui/menu/enchanter_sub.png` · U+E32A | Enchanter sub-screens (upgrade, remove, shop, combine, armor) | 176×222 | All other enchanter screens | P1 | enchanter style, plainer centre. |
| B28 | `gui/menu/wardrobe.png` · U+E319 | `/wardrobe` set list | 176×222 | `/wardrobe` | P1 | Wooden wardrobe cabinet with open doors; bottom bar 46/47/51/52. |
| B29 | `gui/menu/set_detail.png` · U+E32B | Wardrobe set detail | 176×222 | Click a set in `/wardrobe` | P1 | Four armor silhouettes (helmet→boots) behind piece wells 10–13; level plate at 22. |
| B30 | `gui/menu/loadout.png` · U+E31A | `/loadout` presets | 176×222 | `/loadout` | P1 | Weapon rack; preset wells 10/12/14/16/28/30/32/34 as framed plaques. |
| B31 | `gui/menu/loadout_edit.png` · U+E32C | Loadout edit | 176×222 | Editing a preset | P1 | Hotbar strip 20–24 drawn as a hotbar. |
| B32 | `gui/menu/crates.png` · U+E31B | `/crates` overview | 176×222 | `/crates` (all 7 crates + your keys) | P1 | Seven crate lids in their rarity colours along the grid. |
| B33 | `gui/menu/crate_ember.png` · U+E320 | Ember crate preview | 176×222 | Preview of the Ember crate | P1 | Crate lid in the top band, common white/ash with warm ember glow. |
| B34 | `gui/menu/crate_cinder.png` · U+E321 | Cinder crate preview | 176×222 | Preview of the Cinder crate | P1 | Same layout, uncommon green embers. |
| B35 | `gui/menu/crate_blaze.png` · U+E322 | Blaze crate preview | 176×222 | Preview of the Blaze crate | P1 | Same layout, rare blue flame. |
| B36 | `gui/menu/crate_inferno.png` · U+E323 | Inferno crate preview | 176×222 | Preview of the Inferno crate | P1 | Same layout, epic purple fire. |
| B37 | `gui/menu/crate_phoenix.png` · U+E324 | Phoenix crate preview | 176×222 | Preview of the Phoenix crate | P1 | Same layout, legendary gold with phoenix feathers. The richest of the seven. |
| B38 | `gui/menu/crate_eclipse.png` · U+E325 | Eclipse crate preview | 176×222 | Preview of the Eclipse (monthly) crate | P1 | Mythic pink corona around a black sun. |
| B39 | `gui/menu/crate_conqueror.png` · U+E326 | Conqueror crate preview | 176×222 | Preview of the Conqueror (KOTH) crate | P1 | Special red, banners and a crown, war-like. |
| B40 | `gui/menu/crate_roll.png` · U+E31C | Crate roll (spinner) | 176×168 | Opening any crate | P1 | Spinner window across 10–16, gold marker arrows above and below slot 13. |
| B41 | `gui/menu/auction_sell.png` · U+E327 | AH sell screen | 176×168 | `/ah sell` | P2 | Small market panel; item well 10, cancel 16. |
| B42 | `gui/menu/dungeon.png` · U+E328 | `/dungeon` (The Depths) | 176×222 | `/dungeon` | P1 | Cave stone and lantern light; wing tabs 3–5 in the top band, satchel 47. |
| B43 | `gui/menu/skills.png` · U+E329 | `/skills` | 176×222 | `/skills` | P2 | Five pillar wells 20–24, each with its pillar colour under it. |
| B44 | `gui/menu/spawner.png` · U+E32D | Spawner menu | 176×222 | Right-click a spawner | P2 | Iron cage bars; info well 13, add 29/30, upgrades 31, take 32/33. |
| B45 | `gui/menu/achievements.png` · U+E32E | `/achievements` | 176×222 | `/achievements` | P2 | Trophy shelves. |
| B46 | `gui/menu/trade.png` · U+E32F | `/trade` | 176×222 | Player trading | P2 | Two halves (mine 10–39 left, theirs 14–43 right) split by a divider 13/22/31/40; ready wells 47/51 and money 46/52. |
| B47 | `gui/menu/cosmetics.png` · U+E350 | `/display` and cosmetics list | 176×222 | `/display`, tags and titles | P2 | Wardrobe mirror, name-tag motif; preview well 4. |
| B48 | `gui/menu/profiles.png` · U+E31F | `/profiles` | 176×168 | `/profiles` | P1 | Three profile cards behind 11/13/15. |
| B49 | `gui/menu/profile.png` · U+E351 | Single profile actions | 176×168 | Click a profile | P2 | One large card. |
| B50 | `gui/menu/free_rank.png` · U+E352 | `/freerank` | 176×168 | `/freerank` tag choice | P2 | Ember rank colours; three tag wells. |
| B51 | `gui/menu/punish_main.png` · U+E31D | `/p` punish screen | 176×222 | Staff `/p <player>` | P2 | Staff steel, one red severity band per category row. |
| B52 | `gui/menu/punish_step.png` · U+E31E | `/p` step screens | 176×168 | Staff `/p` duration/reason steps | P2 | Staff steel, 3 rows. |

**Slot template (applies to every background):**
- Title band: y 0–16. The title text (crumbs) is drawn by the game at about x 8, y 6, so keep that strip calm.
- Slot cell of row `r`, column `c` (both from 0): top-left at **x = 7 + 18·c, y = 17 + 18·r**, size 18×18; the item itself covers the inner 16×16 (x+1, y+1). Slot number = 9·r + c.
- Content wells (generic frame): slots 10–16, 19–25, 28–34, 37–43. Fixed buttons: 45 back/previous, 48 info, 49 close, 50 help, 53 next.
- Frame slots (0–9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 51, 52 unless the menu uses them) hold invisible panes with the pack, so the art shows through there: that is where the frame decoration goes.
- Never put important detail under a slot that holds an item (it gets covered) or rely on colour alone (some players are colour-blind).
- Bitmap fonts allow max 256 px height; all our canvases are below that. Keep each file under ~20 KB (flat colours, no noise).

## C. Menu buttons
> **Done (v1):** see RESOURCE_PACK_GUI.md §5.1 (final ids; `minus/plus_1/10/100` became `_small/_mid/_big`).


Item models `phoenix:menu/<name>` on the button's current material. 16×16. They replace the vanilla buttons for pack users only.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| C01 | `item/menu/blank.png` | Invisible frame pane | 16×16 | Every frame slot | P1 | Fully transparent 16×16. No drawing, just an empty PNG. |
| C02 | `item/menu/back.png` | Back to the parent menu (slot 45) | 16×16 | Every menu with a parent | P1 | Left arrow, ember outline, small house/return hook so it differs from previous. |
| C03 | `item/menu/previous.png` | Previous page (slot 45) | 16×16 | Paged menus, page 2+ | P1 | Plain left arrow, ember. |
| C04 | `item/menu/next.png` | Next page (slot 53) | 16×16 | Paged menus | P1 | Plain right arrow, ember (mirror of previous). |
| C05 | `item/menu/close.png` | Close menu (slot 49) | 16×16 | Every menu | P1 | Red ✘ on a dark round plate. |
| C06 | `item/menu/info.png` | What this screen does (slot 48) | 16×16 | Every menu | P1 | Book with an "i", parchment. |
| C07 | `item/menu/help.png` | Open /help (slot 50) | 16×16 | Every menu | P1 | Question mark on a knowledge-book cover. |
| C08 | `item/menu/confirm.png` | Big confirm button | 16×16 | Confirm dialog slot 11, number picker 40 | P1 | Green check on a raised plate. |
| C09 | `item/menu/confirm_side.png` | Confirm side panes | 16×16 | Confirm dialog 10/12, picker 39/41 | P1 | Flat green plate, no symbol. |
| C10 | `item/menu/cancel.png` | Big cancel button | 16×16 | Confirm dialog slot 15 | P1 | Red ✘ on a raised plate. |
| C11 | `item/menu/cancel_side.png` | Cancel side panes | 16×16 | Confirm dialog 14/16 | P1 | Flat red plate, no symbol. |
| C12 | `item/menu/toggle_on.png` | Setting enabled | 16×16 | /settings, /is settings, permissions | P1 | Switch lever to the right, green. |
| C13 | `item/menu/toggle_off.png` | Setting disabled | 16×16 | Same as toggle_on | P1 | Switch lever to the left, red/grey. |
| C14 | `item/menu/loading.png` | Loading data | 16×16 | Any async menu | P1 | Hourglass, flame gold sand. May be animated (4 frames). |
| C15 | `item/menu/error.png` | Couldn't load | 16×16 | Any menu on error | P1 | Cracked red pane with ✘. |
| C16 | `item/menu/empty.png` | Nothing here yet | 16×16 | Empty lists (slot 22) | P1 | Faint cobweb in muted grey. |
| C17 | `item/menu/locked.png` | Locked entry | 16×16 | Tiers, sets, slots not unlocked | P1 | Padlock, dark grey with red keyhole. |
| C18 | `item/menu/maxed.png` | Maxed / done | 16×16 | Upgrades, levels at max | P1 | Gold star with a check. |
| C19 | `item/menu/minus_1.png` | Amount −1 | 16×16 | Number picker, shop amount | P1 | Red plate with "-1" in pixel digits. |
| C20 | `item/menu/minus_10.png` | Amount −10 | 16×16 | Same | P1 | Red plate "-10". |
| C21 | `item/menu/minus_100.png` | Amount −100 | 16×16 | Same | P1 | Red plate "-100" (digits 3 px wide). |
| C22 | `item/menu/plus_1.png` | Amount +1 | 16×16 | Same | P1 | Green plate "+1". |
| C23 | `item/menu/plus_10.png` | Amount +10 | 16×16 | Same | P1 | Green plate "+10". |
| C24 | `item/menu/plus_100.png` | Amount +100 | 16×16 | Same | P1 | Green plate "+100". |
| C25 | `item/menu/set_min.png` | Set minimum | 16×16 | Number picker slot 30 | P1 | Empty bucket shape / down-to-bar arrow. |
| C26 | `item/menu/set_max.png` | Set maximum | 16×16 | Number picker slot 32 | P1 | Full bucket shape / up-to-bar arrow. |
| C27 | `item/menu/type_amount.png` | Type an amount in chat | 16×16 | Number picker 31, shop amount 31 | P1 | Quill on a name tag. |
| C28 | `item/menu/sell.png` | Sell this | 16×16 | Sell chest 52, satchel item 12 | P1 | Gold coin with a down arrow. |
| C29 | `item/menu/sell_all.png` | Sell everything | 16×16 | Satchel 47, sell chest 46 | P1 | Stack of gold coins with a down arrow. |
| C30 | `item/menu/take_all.png` | Take everything | 16×16 | Satchel item 16, collection bin | P1 | Open hand / up arrow over a stack. |
| C31 | `item/menu/buy.png` | Buy | 16×16 | Shop amount 40, enchanter shop | P1 | Green coin with an up arrow (mirror of sell). |
| C32 | `item/menu/search.png` | Search | 16×16 | AH, lists | P1 | Magnifying glass. |
| C33 | `item/menu/filter.png` | Filter | 16×16 | AH, wardrobe, lists | P1 | Funnel. |
| C34 | `item/menu/sort.png` | Sort | 16×16 | AH, lists | P1 | Up/down arrows. |

## D. Feature icons
> **Done (v1) for every feature that is live:** RESOURCE_PACK_GUI.md §5.2. Hidden features (quests, pets, events, black market, collection, loadout, satchel, sigil) get their icon when they ship.


Item models `phoenix:menu/icon/<feature>`, 16×16, used on the buttons that open a feature (`/menu`, `/is`, help). Same icon everywhere for the same feature (UI_STYLE §5.5). Until drawn the vanilla item stays.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| D01 | `item/menu/icon/island.png` | Island | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Grass block); ember rim light, readable at 16 px. |
| D02 | `item/menu/icon/upgrades.png` | Island upgrades | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Anvil); ember rim light, readable at 16 px. |
| D03 | `item/menu/icon/island_top.png` | Island top / stats | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Golden helmet); ember rim light, readable at 16 px. |
| D04 | `item/menu/icon/members.png` | Island members | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Player heads); ember rim light, readable at 16 px. |
| D05 | `item/menu/icon/permissions.png` | Island permissions | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Shield); ember rim light, readable at 16 px. |
| D06 | `item/menu/icon/notes.png` | Island notes | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Writable book); ember rim light, readable at 16 px. |
| D07 | `item/menu/icon/warps.png` | Warps / visit | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Ender pearl); ember rim light, readable at 16 px. |
| D08 | `item/menu/icon/depths.png` | The Depths `/dungeon` | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Iron pickaxe); ember rim light, readable at 16 px. |
| D09 | `item/menu/icon/satchel.png` | Satchel | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Bundle); ember rim light, readable at 16 px. |
| D10 | `item/menu/icon/shop.png` | Shop | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Emerald); ember rim light, readable at 16 px. |
| D11 | `item/menu/icon/sell.png` | Sell | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Gold nugget); ember rim light, readable at 16 px. |
| D12 | `item/menu/icon/auction.png` | Auction house | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Gold block); ember rim light, readable at 16 px. |
| D13 | `item/menu/icon/bank.png` | Bank | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Barrel); ember rim light, readable at 16 px. |
| D14 | `item/menu/icon/crates.png` | Crates | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Tripwire hook); ember rim light, readable at 16 px. |
| D15 | `item/menu/icon/quests.png` | Quests | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Writable book); ember rim light, readable at 16 px. |
| D16 | `item/menu/icon/skills.png` | Skills | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (XP bottle); ember rim light, readable at 16 px. |
| D17 | `item/menu/icon/wardrobe.png` | Wardrobe | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Red leather chestplate); ember rim light, readable at 16 px. |
| D18 | `item/menu/icon/loadout.png` | Loadouts | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Armor stand); ember rim light, readable at 16 px. |
| D19 | `item/menu/icon/enchanter.png` | Enchanter | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Enchanting table); ember rim light, readable at 16 px. |
| D20 | `item/menu/icon/forge.png` | Forge | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Anvil + flame); ember rim light, readable at 16 px. |
| D21 | `item/menu/icon/pets.png` | Pets (later) | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Bone); ember rim light, readable at 16 px. |
| D22 | `item/menu/icon/events.png` | Events | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Clock); ember rim light, readable at 16 px. |
| D23 | `item/menu/icon/black_market.png` | Black Market | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Wither rose); ember rim light, readable at 16 px. |
| D24 | `item/menu/icon/settings.png` | Settings | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Comparator); ember rim light, readable at 16 px. |
| D25 | `item/menu/icon/collection.png` | Collection log | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Bookshelf); ember rim light, readable at 16 px. |
| D26 | `item/menu/icon/cosmetics.png` | Titles / cosmetics | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Name tag); ember rim light, readable at 16 px. |
| D27 | `item/menu/icon/achievements.png` | Achievements | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Trophy); ember rim light, readable at 16 px. |
| D28 | `item/menu/icon/profiles.png` | Profiles | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Three cards); ember rim light, readable at 16 px. |
| D29 | `item/menu/icon/sigil.png` | Wardrobe Sigil (gem-set claim) | 16×16 | `/menu`, `/help`, related menus | P2 | Phoenix-styled take on the current icon (Wax seal); ember rim light, readable at 16 px. |

## E. Tools (Forge)

Item models `phoenix:tool/<kind>/<tier>`, picked from the tool's tier (RP-D12: level never changes the look). Tier art follows the tier's material; the outline/accent shows the rarity colour. Tiers from `tools-defaults.yml`.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| E01 | `item/tool/pickaxe/coal.png` | Coal Pickaxe (tier 1, common) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Stone-grey handle, coal-black head with tiny ember specks. The starter look. |
| E02 | `item/tool/pickaxe/copper.png` | Copper Pickaxe (tier 2, common) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Bright copper head, a few verdigris pixels. |
| E03 | `item/tool/pickaxe/iron.png` | Iron Pickaxe (tier 3, common) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Clean iron, cool grey. |
| E04 | `item/tool/pickaxe/gold.png` | Gold Pickaxe (tier 4, uncommon) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Gold head, warm shine. |
| E05 | `item/tool/pickaxe/redstone.png` | Redstone Pickaxe (tier 5, uncommon) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Dark iron head with glowing red veins. |
| E06 | `item/tool/pickaxe/lapis.png` | Lapis Pickaxe (tier 6, uncommon) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Deep blue lapis inlays, gold rivets. |
| E07 | `item/tool/pickaxe/diamond.png` | Diamond Pickaxe (tier 7, rare) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Cyan diamond head, sharp highlights. |
| E08 | `item/tool/pickaxe/emerald.png` | Emerald Pickaxe (tier 8, rare) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Emerald green facets. |
| E09 | `item/tool/pickaxe/amethyst.png` | Amethyst Pickaxe (tier 9, rare) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Purple crystal shards forming the head. |
| E10 | `item/tool/pickaxe/quartz.png` | Quartz Pickaxe (tier 10, epic) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Cream-white quartz, faint gold trim. |
| E11 | `item/tool/pickaxe/netherite.png` | Netherite Pickaxe (tier 11, epic) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Black netherite with an ember edge glow. |
| E12 | `item/tool/pickaxe/voidstone.png` | Voidstone Pickaxe (tier 12, legendary) | 32×32 ok | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Purple-black void head with a glow; 32×32 and up to 8-frame animation allowed. |
| E13 | `item/tool/axe/oak.png` | Oak Axe (tier 1, common) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Wooden axe, oak handle. |
| E14 | `item/tool/axe/birch.png` | Birch Axe (tier 2, common) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Stone head, pale birch handle. |
| E15 | `item/tool/axe/spruce.png` | Spruce Axe (tier 3, common) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Copper head, dark spruce handle. |
| E16 | `item/tool/axe/jungle.png` | Jungle Axe (tier 4, uncommon) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Iron head, jungle handle with a vine wrap. |
| E17 | `item/tool/axe/acacia.png` | Acacia Axe (tier 5, uncommon) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Iron head, orange acacia handle. |
| E18 | `item/tool/axe/dark_oak.png` | Dark Oak Axe (tier 6, uncommon) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Iron head, dark oak handle. |
| E19 | `item/tool/axe/mangrove.png` | Mangrove Axe (tier 7, rare) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Diamond head, red mangrove handle with roots. |
| E20 | `item/tool/axe/cherry.png` | Cherry Axe (tier 8, rare) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Diamond head, pink cherry handle, a blossom. |
| E21 | `item/tool/axe/pale_oak.png` | Pale Oak Axe (tier 9, rare) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Diamond head, pale grey handle, moss. |
| E22 | `item/tool/axe/crimson.png` | Crimson Axe (tier 10, epic) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Netherite head, crimson stem handle. |
| E23 | `item/tool/axe/warped.png` | Warped Axe (tier 11, epic) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Netherite head, teal warped handle, faint glow. |
| E24 | `item/tool/sword/grave.png` | Grave Sword (tier 1, common) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Mossy stone blade, grave-cross guard. |
| E25 | `item/tool/sword/bone.png` | Bone Sword (tier 2, common) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Copper blade with a bone grip. |
| E26 | `item/tool/sword/venom.png` | Venom Sword (tier 3, common) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Iron blade dripping green venom. |
| E27 | `item/tool/sword/slime.png` | Slime Sword (tier 4, uncommon) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Iron blade with a slime-green edge. |
| E28 | `item/tool/sword/tide.png` | Tide Sword (tier 5, uncommon) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Prismarine-teal blade, wave guard. |
| E29 | `item/tool/sword/hex.png` | Hex Sword (tier 6, uncommon) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Diamond blade with witch-purple runes. |
| E30 | `item/tool/sword/raider.png` | Raider Sword (tier 7, rare) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Diamond blade, ominous-banner guard. |
| E31 | `item/tool/sword/blaze.png` | Blaze Sword (tier 8, rare) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Blade wrapped in blaze rods and flame. |
| E32 | `item/tool/sword/wither.png` | Wither Sword (tier 9, epic) | 16×16 | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Black netherite blade, skull pommel. |
| E33 | `item/tool/sword/warlord.png` | Warlord Sword (tier 10, legendary) | 32×32 ok | Hotbar, `/forge`, `/tool`, AH, loadouts | P1 | Crimson and gold war blade; 32×32 + animation allowed. |
| E34 | `item/tool/hoe/basic.png` | Basic hoe (farming, Phase 7 adds tiers) | 16×16 | Starter kit, hotbar | P1 | Stone hoe in the house style. |
| E35 | `item/tool/rod/basic.png` | Basic fishing rod | 16×16 | Starter kit, hotbar | P1 | Rod with a red/gold float. |
| E36 | `item/tool/rod/basic_cast.png` | Basic fishing rod while cast | 16×16 | Hotbar while fishing | P1 | Same rod, line gone (vanilla cast logic). |
| E37 | `item/skin/phoenix_flame/pickaxe.png` | Phoenix Flame skin on a pickaxe | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Burning phoenix feathers along the tool, red→orange→gold; animation allowed. |
| E38 | `item/skin/phoenix_flame/axe.png` | Phoenix Flame skin on a axe | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Burning phoenix feathers along the tool, red→orange→gold; animation allowed. |
| E39 | `item/skin/phoenix_flame/sword.png` | Phoenix Flame skin on a sword | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Burning phoenix feathers along the tool, red→orange→gold; animation allowed. |
| E40 | `item/skin/phoenix_flame/hoe.png` | Phoenix Flame skin on a hoe | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Burning phoenix feathers along the tool, red→orange→gold; animation allowed. |
| E41 | `item/skin/phoenix_flame/rod.png` | Phoenix Flame skin on a rod | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Burning phoenix feathers along the tool, red→orange→gold; animation allowed. |
| E42 | `item/skin/phoenix_flame/rod_cast.png` | Phoenix Flame skin, rod while cast | 32×32 ok | Hotbar while fishing | P1 | Same as the rod, line gone. |
| E43 | `item/skin/frostbite/pickaxe.png` | Frostbite skin on a pickaxe | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Ice-blue frozen metal with frost crystals (the only cold skin: keep it clearly a skin, not network art). |
| E44 | `item/skin/frostbite/axe.png` | Frostbite skin on a axe | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Ice-blue frozen metal with frost crystals (the only cold skin: keep it clearly a skin, not network art). |
| E45 | `item/skin/frostbite/sword.png` | Frostbite skin on a sword | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Ice-blue frozen metal with frost crystals (the only cold skin: keep it clearly a skin, not network art). |
| E46 | `item/skin/frostbite/hoe.png` | Frostbite skin on a hoe | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Ice-blue frozen metal with frost crystals (the only cold skin: keep it clearly a skin, not network art). |
| E47 | `item/skin/frostbite/rod.png` | Frostbite skin on a rod | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Ice-blue frozen metal with frost crystals (the only cold skin: keep it clearly a skin, not network art). |
| E48 | `item/skin/frostbite/rod_cast.png` | Frostbite skin, rod while cast | 32×32 ok | Hotbar while fishing | P1 | Same as the rod, line gone. |
| E49 | `item/skin/royal_gold/pickaxe.png` | Royal Gold skin on a pickaxe | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Polished gold with red gems, royal filigree. |
| E50 | `item/skin/royal_gold/axe.png` | Royal Gold skin on a axe | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Polished gold with red gems, royal filigree. |
| E51 | `item/skin/royal_gold/sword.png` | Royal Gold skin on a sword | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Polished gold with red gems, royal filigree. |
| E52 | `item/skin/royal_gold/hoe.png` | Royal Gold skin on a hoe | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Polished gold with red gems, royal filigree. |
| E53 | `item/skin/royal_gold/rod.png` | Royal Gold skin on a rod | 32×32 ok | Any tool with this skin applied (wins over the tier look) | P1 | Polished gold with red gems, royal filigree. |
| E54 | `item/skin/royal_gold/rod_cast.png` | Royal Gold skin, rod while cast | 32×32 ok | Hotbar while fishing | P1 | Same as the rod, line gone. |
| E55 | `item/tool/overlay/prestige_pickaxe.png` | Prestige spark overlay for pickaxes | 16×16 | Any prestiged tool (drawn on top of the tier art) | P2 | Transparent except 3–5 ember sparks; must fit over every tier of that kind. |
| E56 | `item/tool/overlay/prestige_axe.png` | Prestige spark overlay for axes | 16×16 | Any prestiged tool (drawn on top of the tier art) | P2 | Transparent except 3–5 ember sparks; must fit over every tier of that kind. |
| E57 | `item/tool/overlay/prestige_sword.png` | Prestige spark overlay for swords | 16×16 | Any prestiged tool (drawn on top of the tier art) | P2 | Transparent except 3–5 ember sparks; must fit over every tier of that kind. |
| E58 | `item/tool/overlay/prestige_hoe.png` | Prestige spark overlay for hoes | 16×16 | Any prestiged tool (drawn on top of the tier art) | P2 | Transparent except 3–5 ember sparks; must fit over every tier of that kind. |
| E59 | `item/tool/overlay/prestige_rod.png` | Prestige spark overlay for rods | 16×16 | Any prestiged tool (drawn on top of the tier art) | P2 | Transparent except 3–5 ember sparks; must fit over every tier of that kind. |
| E60 | `item/tool/blueprint/rare.png` | Tool blueprint, rare (Sage Pickaxe, Windcutter Axe) | 16×16 | Drops in The Depths, `/forge` blueprints | P1 | Rolled blue paper with a white tool sketch, blue ribbon. |
| E61 | `item/tool/blueprint/epic.png` | Tool blueprint, epic (Magma Pickaxe, Vampire Blade) | 16×16 | Same | P1 | Same roll, purple ribbon and a wax seal. |

## F. Enchant books and scroll

Item models `phoenix:enchant/book/<rarity>` on `enchanted_book` (ENCHANTS §11).

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| F01 | `item/enchant/book/common.png` | Enchant book, common | 16×16 | Drops, `/enchanter`, AH, wardrobe chest | P2 | Leather book with a white cover gem/clasp; the glint stays on top, so keep it darker than vanilla. |
| F02 | `item/enchant/book/uncommon.png` | Enchant book, uncommon | 16×16 | Drops, `/enchanter`, AH, wardrobe chest | P2 | Leather book with a green cover gem/clasp; the glint stays on top, so keep it darker than vanilla. |
| F03 | `item/enchant/book/rare.png` | Enchant book, rare | 16×16 | Drops, `/enchanter`, AH, wardrobe chest | P2 | Leather book with a blue cover gem/clasp; the glint stays on top, so keep it darker than vanilla. |
| F04 | `item/enchant/book/epic.png` | Enchant book, epic | 16×16 | Drops, `/enchanter`, AH, wardrobe chest | P2 | Leather book with a purple cover gem/clasp; the glint stays on top, so keep it darker than vanilla. |
| F05 | `item/enchant/book/legendary.png` | Enchant book, legendary | 16×16 | Drops, `/enchanter`, AH, wardrobe chest | P2 | Leather book with a gold cover gem/clasp; the glint stays on top, so keep it darker than vanilla. |
| F06 | `item/enchant/book/mythic.png` | Enchant book, mythic | 16×16 | Drops, `/enchanter`, AH, wardrobe chest | P2 | Leather book with a pink cover gem/clasp; the glint stays on top, so keep it darker than vanilla. Mythic: animated rune allowed. |
| F07 | `item/enchant/scroll.png` | Disenchant Scroll (removes an enchant into a book) | 16×16 | `/enchanter` remove, shop | P2 | Parchment scroll with a purple broken-rune seal. |

## G. Wardrobe sets

16 sets × 4 pieces from `sets.yml`. Inventory icons are models `phoenix:set/<set>/<piece>`. **New with the forced pack:** custom worn looks (equipment assets) for Java; Bedrock keeps vanilla armor + trims + dye. Worn looks need code (RP4b) before they show.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| G01 | `item/set/delver/helmet.png` | Delver's Garb helmet (tier I, mining) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Miner's chainmail with copper lamp/buckles; earthy browns. (vanilla look today: chainmail, wayfinder trim, copper). |
| G02 | `item/set/delver/chestplate.png` | Delver's Garb chestplate (tier I, mining) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G03 | `item/set/delver/leggings.png` | Delver's Garb leggings (tier I, mining) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G04 | `item/set/delver/boots.png` | Delver's Garb boots (tier I, mining) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G05 | `item/set/woodsman/helmet.png` | Woodsman's Hide helmet (tier I, foraging) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Moss-green hide, leaf stitching. (vanilla look today: leather #5E7D32, wild trim, emerald). |
| G06 | `item/set/woodsman/chestplate.png` | Woodsman's Hide chestplate (tier I, foraging) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G07 | `item/set/woodsman/leggings.png` | Woodsman's Hide leggings (tier I, foraging) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G08 | `item/set/woodsman/boots.png` | Woodsman's Hide boots (tier I, foraging) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G09 | `item/set/tiller/helmet.png` | Tiller's Smock helmet (tier I, farming) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Straw-yellow smock, wheat pattern, sun hat feel. (vanilla look today: leather #D9B45A, shaper trim, gold). |
| G10 | `item/set/tiller/chestplate.png` | Tiller's Smock chestplate (tier I, farming) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G11 | `item/set/tiller/leggings.png` | Tiller's Smock leggings (tier I, farming) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G12 | `item/set/tiller/boots.png` | Tiller's Smock boots (tier I, farming) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G13 | `item/set/footman/helmet.png` | Footman's Mail helmet (tier I, combat) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Plain iron soldier gear with red tabard bits. (vanilla look today: iron, sentry trim, redstone). |
| G14 | `item/set/footman/chestplate.png` | Footman's Mail chestplate (tier I, combat) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G15 | `item/set/footman/leggings.png` | Footman's Mail leggings (tier I, combat) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G16 | `item/set/footman/boots.png` | Footman's Mail boots (tier I, combat) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G17 | `item/set/netcaster/helmet.png` | Netcaster's Garb helmet (tier I, fishing) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Blue oilskin, net texture, rope. (vanilla look today: leather #3F76A8, coast trim, lapis). |
| G18 | `item/set/netcaster/chestplate.png` | Netcaster's Garb chestplate (tier I, fishing) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G19 | `item/set/netcaster/leggings.png` | Netcaster's Garb leggings (tier I, fishing) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G20 | `item/set/netcaster/boots.png` | Netcaster's Garb boots (tier I, fishing) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G21 | `item/set/banneret/helmet.png` | Banneret Tabard helmet (tier II, conquest) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Iron with a red war-banner tabard. (vanilla look today: iron, raiser trim, redstone). |
| G22 | `item/set/banneret/chestplate.png` | Banneret Tabard chestplate (tier II, conquest) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G23 | `item/set/banneret/leggings.png` | Banneret Tabard leggings (tier II, conquest) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G24 | `item/set/banneret/boots.png` | Banneret Tabard boots (tier II, conquest) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G25 | `item/set/lorekeeper/helmet.png` | Lorekeeper Robes helmet (tier II, skills) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Parchment robes, purple runes, scholar. (vanilla look today: leather #E8DCC0, host trim, amethyst). |
| G26 | `item/set/lorekeeper/chestplate.png` | Lorekeeper Robes chestplate (tier II, skills) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G27 | `item/set/lorekeeper/leggings.png` | Lorekeeper Robes leggings (tier II, skills) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G28 | `item/set/lorekeeper/boots.png` | Lorekeeper Robes boots (tier II, skills) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G29 | `item/set/deepforge/helmet.png` | Deepforge Plate helmet (tier III, mining) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Heavy dark plate with molten seams. (vanilla look today: netherite, rib trim, gold). |
| G30 | `item/set/deepforge/chestplate.png` | Deepforge Plate chestplate (tier III, mining) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G31 | `item/set/deepforge/leggings.png` | Deepforge Plate leggings (tier III, mining) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G32 | `item/set/deepforge/boots.png` | Deepforge Plate boots (tier III, mining) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G33 | `item/set/thornward/helmet.png` | Thornward Mantle helmet (tier III, foraging) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Dark bark leather with thorns. (vanilla look today: leather #4A3221, ward trim, emerald). |
| G34 | `item/set/thornward/chestplate.png` | Thornward Mantle chestplate (tier III, foraging) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G35 | `item/set/thornward/leggings.png` | Thornward Mantle leggings (tier III, foraging) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G36 | `item/set/thornward/boots.png` | Thornward Mantle boots (tier III, foraging) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G37 | `item/set/cropwarden/helmet.png` | Cropwarden Robes helmet (tier III, farming) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Harvest-orange robes, sickle motif. (vanilla look today: leather #E3A42B, flow trim, copper). |
| G38 | `item/set/cropwarden/chestplate.png` | Cropwarden Robes chestplate (tier III, farming) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G39 | `item/set/cropwarden/leggings.png` | Cropwarden Robes leggings (tier III, farming) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G40 | `item/set/cropwarden/boots.png` | Cropwarden Robes boots (tier III, farming) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G41 | `item/set/dreadguard/helmet.png` | Dreadguard Mail helmet (tier III, combat) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Spiked diamond-black mail, menacing. (vanilla look today: diamond, snout trim, netherite). |
| G42 | `item/set/dreadguard/chestplate.png` | Dreadguard Mail chestplate (tier III, combat) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G43 | `item/set/dreadguard/leggings.png` | Dreadguard Mail leggings (tier III, combat) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G44 | `item/set/dreadguard/boots.png` | Dreadguard Mail boots (tier III, combat) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G45 | `item/set/stormkeel/helmet.png` | Stormkeel Scale helmet (tier III, fishing) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Sea-scale armor, storm-blue, lightning. (vanilla look today: chainmail, tide trim, diamond). |
| G46 | `item/set/stormkeel/chestplate.png` | Stormkeel Scale chestplate (tier III, fishing) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G47 | `item/set/stormkeel/leggings.png` | Stormkeel Scale leggings (tier III, fishing) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G48 | `item/set/stormkeel/boots.png` | Stormkeel Scale boots (tier III, fishing) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G49 | `item/set/coinmaster/helmet.png` | Coinmaster Silks helmet (tier III, selling) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Purple merchant silks with gold coins. (vanilla look today: leather #5B2A86, eye trim, gold). |
| G50 | `item/set/coinmaster/chestplate.png` | Coinmaster Silks chestplate (tier III, selling) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G51 | `item/set/coinmaster/leggings.png` | Coinmaster Silks leggings (tier III, selling) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G52 | `item/set/coinmaster/boots.png` | Coinmaster Silks boots (tier III, selling) | 16×16 | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G53 | `item/set/voidforged/helmet.png` | Voidforged Plate helmet (tier IV, gathering) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Void-black plate with purple light cracks. (vanilla look today: netherite, silence trim, amethyst). |
| G54 | `item/set/voidforged/chestplate.png` | Voidforged Plate chestplate (tier IV, gathering) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G55 | `item/set/voidforged/leggings.png` | Voidforged Plate leggings (tier IV, gathering) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G56 | `item/set/voidforged/boots.png` | Voidforged Plate boots (tier IV, gathering) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G57 | `item/set/conqueror/helmet.png` | Conqueror Aegis helmet (tier IV, conquest) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Black and red war plate, crown helm. (vanilla look today: netherite, bolt trim, redstone). |
| G58 | `item/set/conqueror/chestplate.png` | Conqueror Aegis chestplate (tier IV, conquest) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G59 | `item/set/conqueror/leggings.png` | Conqueror Aegis leggings (tier IV, conquest) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G60 | `item/set/conqueror/boots.png` | Conqueror Aegis boots (tier IV, conquest) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G61 | `item/set/phoenix/helmet.png` | Phoenix Regalia helmet (tier IV, all) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | The signature: gold plate with red flame feathers; animation allowed. (vanilla look today: gold, spire trim, redstone). |
| G62 | `item/set/phoenix/chestplate.png` | Phoenix Regalia chestplate (tier IV, all) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G63 | `item/set/phoenix/leggings.png` | Phoenix Regalia leggings (tier IV, all) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G64 | `item/set/phoenix/boots.png` | Phoenix Regalia boots (tier IV, all) | 32×32 ok | Inventory, `/wardrobe`, set detail, loadouts | P1 | Same set, matching the helmet. |
| G65 | `entity/equipment/humanoid/delver.png` + `humanoid_leggings/delver.png` | Delver's Garb: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G66 | `entity/equipment/humanoid/woodsman.png` + `humanoid_leggings/woodsman.png` | Woodsman's Hide: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G67 | `entity/equipment/humanoid/tiller.png` + `humanoid_leggings/tiller.png` | Tiller's Smock: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G68 | `entity/equipment/humanoid/footman.png` + `humanoid_leggings/footman.png` | Footman's Mail: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G69 | `entity/equipment/humanoid/netcaster.png` + `humanoid_leggings/netcaster.png` | Netcaster's Garb: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G70 | `entity/equipment/humanoid/banneret.png` + `humanoid_leggings/banneret.png` | Banneret Tabard: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G71 | `entity/equipment/humanoid/lorekeeper.png` + `humanoid_leggings/lorekeeper.png` | Lorekeeper Robes: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G72 | `entity/equipment/humanoid/deepforge.png` + `humanoid_leggings/deepforge.png` | Deepforge Plate: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G73 | `entity/equipment/humanoid/thornward.png` + `humanoid_leggings/thornward.png` | Thornward Mantle: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G74 | `entity/equipment/humanoid/cropwarden.png` + `humanoid_leggings/cropwarden.png` | Cropwarden Robes: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G75 | `entity/equipment/humanoid/dreadguard.png` + `humanoid_leggings/dreadguard.png` | Dreadguard Mail: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G76 | `entity/equipment/humanoid/stormkeel.png` + `humanoid_leggings/stormkeel.png` | Stormkeel Scale: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G77 | `entity/equipment/humanoid/coinmaster.png` + `humanoid_leggings/coinmaster.png` | Coinmaster Silks: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G78 | `entity/equipment/humanoid/voidforged.png` + `humanoid_leggings/voidforged.png` | Voidforged Plate: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G79 | `entity/equipment/humanoid/conqueror.png` + `humanoid_leggings/conqueror.png` | Conqueror Aegis: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G80 | `entity/equipment/humanoid/phoenix.png` + `humanoid_leggings/phoenix.png` | Phoenix Regalia: worn look (body layer + leggings layer) | 64×32 each | On the player's body (Java only) | P1 | Same design as the icons, on the vanilla armor UV layout (2 files). Vanilla trims still draw on top. |
| G81 | `item/set/token/i.png` | Set Token, tier I (crate win, redeem for a set) | 16×16 | Crate rewards, AH, wardrobe redeem | P1 | Wax-sealed parchment token with a chestplate stamp; seal colour by tier (iron). |
| G82 | `item/set/token/ii.png` | Set Token, tier II (crate win, redeem for a set) | 16×16 | Crate rewards, AH, wardrobe redeem | P1 | Wax-sealed parchment token with a chestplate stamp; seal colour by tier (copper). |
| G83 | `item/set/token/iii.png` | Set Token, tier III (crate win, redeem for a set) | 16×16 | Crate rewards, AH, wardrobe redeem | P1 | Wax-sealed parchment token with a chestplate stamp; seal colour by tier (gold). |
| G84 | `item/set/token/iv.png` | Set Token, tier IV (crate win, redeem for a set) | 16×16 | Crate rewards, AH, wardrobe redeem | P1 | Wax-sealed parchment token with a chestplate stamp; seal colour by tier (phoenix red). |
| G85 | `item/set/tome.png` | Wardrobe Tome (+2,500 set XP) | 16×16 | Crate rewards, quests, wardrobe | P1 | Thick red leather tome with a gold chestplate emblem. |
| G86 | `item/set/tome_greater.png` | Greater Wardrobe Tome (+10,000, Inferno/Phoenix crates) | 16×16 | Same | P1 | Same tome, gold cover, small flame. |

## H. Crate keys and crate blocks (7 crates)

Ember, Cinder, Blaze, Inferno, Phoenix, Eclipse (monthly), Conqueror (KOTH). The key's gem/bit carries the rarity colour. The crates module is not built yet; the art can start now.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| H01 | `item/crate/key/ember.png` | Ember Crate Key (common) | 32×32 ok | Inventory, `/crates`, rewards (AFK pool, weekly top, KOTH) | P1 | Warm iron key, small orange ember gem. |
| H02 | `item/crate/key/cinder.png` | Cinder Crate Key (uncommon) | 32×32 ok | Inventory, `/crates`, rewards (AFK pool, weekly top, KOTH) | P1 | Ash-grey key with a green ember. |
| H03 | `item/crate/key/blaze.png` | Blaze Crate Key (rare) | 32×32 ok | Inventory, `/crates`, rewards (AFK pool, weekly top, KOTH) | P1 | Gold key with a blue flame bit. |
| H04 | `item/crate/key/inferno.png` | Inferno Crate Key (epic) | 32×32 ok | Inventory, `/crates`, rewards (AFK pool, weekly top, KOTH) | P1 | Dark key wrapped in purple fire. |
| H05 | `item/crate/key/phoenix.png` | Phoenix Crate Key (legendary) | 32×32 ok | Inventory, `/crates`, rewards (AFK pool, weekly top, KOTH) | P1 | Gold feather-shaped key, animated flicker allowed. |
| H06 | `item/crate/key/eclipse.png` | Eclipse Crate Key (mythic) | 32×32 ok | Inventory, `/crates`, rewards (AFK pool, weekly top, KOTH) | P1 | Black key with a pink eclipse ring. |
| H07 | `item/crate/key/conqueror.png` | Conqueror Crate Key (special) | 32×32 ok | Inventory, `/crates`, rewards (AFK pool, weekly top, KOTH) | P1 | Iron key with a red crown bow. |
| H08 | `item/crate/block/ember_{top,side,front,bottom,inner}.png` | Ember crate at spawn: 5 faces (top, side, front with lock, bottom, inside of the lid/box) | 16×16 each (32×32 ok) | Crate yard at spawn (Java pack, item display) | P1 | Charred oak crate, iron bands, orange glow in the cracks. The inner face shows when the lid opens: draw it glowing. |
| H09 | `item/crate/block/cinder_{top,side,front,bottom,inner}.png` | Cinder crate at spawn: 5 faces (top, side, front with lock, bottom, inside of the lid/box) | 16×16 each (32×32 ok) | Crate yard at spawn (Java pack, item display) | P1 | Grey ash-stone crate, green embers. The inner face shows when the lid opens: draw it glowing. |
| H10 | `item/crate/block/blaze_{top,side,front,bottom,inner}.png` | Blaze crate at spawn: 5 faces (top, side, front with lock, bottom, inside of the lid/box) | 16×16 each (32×32 ok) | Crate yard at spawn (Java pack, item display) | P1 | Blackstone crate with blue fire seams. The inner face shows when the lid opens: draw it glowing. |
| H11 | `item/crate/block/inferno_{top,side,front,bottom,inner}.png` | Inferno crate at spawn: 5 faces (top, side, front with lock, bottom, inside of the lid/box) | 16×16 each (32×32 ok) | Crate yard at spawn (Java pack, item display) | P1 | Obsidian crate, purple lava cracks. The inner face shows when the lid opens: draw it glowing. |
| H12 | `item/crate/block/phoenix_{top,side,front,bottom,inner}.png` | Phoenix crate at spawn: 5 faces (top, side, front with lock, bottom, inside of the lid/box) | 16×16 each (32×32 ok) | Crate yard at spawn (Java pack, item display) | P1 | Gold and red crate with phoenix wings on the lid. The inner face shows when the lid opens: draw it glowing. |
| H13 | `item/crate/block/eclipse_{top,side,front,bottom,inner}.png` | Eclipse crate at spawn: 5 faces (top, side, front with lock, bottom, inside of the lid/box) | 16×16 each (32×32 ok) | Crate yard at spawn (Java pack, item display) | P1 | Black crate with a pink corona; can change colours monthly later. The inner face shows when the lid opens: draw it glowing. |
| H14 | `item/crate/block/conqueror_{top,side,front,bottom,inner}.png` | Conqueror crate at spawn: 5 faces (top, side, front with lock, bottom, inside of the lid/box) | 16×16 each (32×32 ok) | Crate yard at spawn (Java pack, item display) | P1 | Battle-worn crate with banners and a crown. The inner face shows when the lid opens: draw it glowing. |

## I. Crate animations

The lid animation (6 frames, `_open_0` … `_open_5`) is model work by the developer using the H faces: **no extra drawing**. Particles use vanilla flame. The art here is only the win flash.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| I01 | `font/hud/crate_win_common.png` · U+E354 | Win flash for a common reward (shown as a screen title) | 64×64 | Screen centre when a crate roll stops (winner only, Java) | P2 | Burst of flame in the common colour around an empty centre (the reward name shows under it). |
| I02 | `font/hud/crate_win_uncommon.png` · U+E355 | Win flash for a uncommon reward (shown as a screen title) | 64×64 | Screen centre when a crate roll stops (winner only, Java) | P2 | Burst of flame in the uncommon colour around an empty centre (the reward name shows under it). |
| I03 | `font/hud/crate_win_rare.png` · U+E356 | Win flash for a rare reward (shown as a screen title) | 64×64 | Screen centre when a crate roll stops (winner only, Java) | P2 | Burst of flame in the rare colour around an empty centre (the reward name shows under it). |
| I04 | `font/hud/crate_win_epic.png` · U+E357 | Win flash for a epic reward (shown as a screen title) | 64×64 | Screen centre when a crate roll stops (winner only, Java) | P2 | Burst of flame in the epic colour around an empty centre (the reward name shows under it). |
| I05 | `font/hud/crate_win_legendary.png` · U+E358 | Win flash for a legendary reward (shown as a screen title) | 64×64 | Screen centre when a crate roll stops (winner only, Java) | P2 | Burst of flame in the legendary colour around an empty centre (the reward name shows under it). Most dramatic: wings + feathers. |
| I06 | `font/hud/crate_win_mythic.png` · U+E359 | Win flash for a mythic reward (shown as a screen title) | 64×64 | Screen centre when a crate roll stops (winner only, Java) | P2 | Burst of flame in the mythic colour around an empty centre (the reward name shows under it). |

- Bedrock and pack-less players get the GUI roll (background B E31C) + sounds (T) only.

## J. Currencies (crystals, gems, shards, money, credits)
> **Done (v1):** icons J01–J05 and glyphs J06–J10, see RESOURCE_PACK_GUI.md §5.4 and §6.


Item icons for menus plus font glyphs for text. Glyphs are shared with Bedrock (sheet `glyph_E2.png`).

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| J01 | `item/currency/money.png` | Money icon (menu/shop price buttons, not a real item) | 16×16 | Price buttons in shop, enchanter, wardrobe, bank | P1 | Stack of gold coins, green shine (money colour #4DFF88 on the rim). |
| J02 | `item/currency/crystals.png` | Island crystals icon (menu/shop price buttons, not a real item) | 16×16 | Price buttons in shop, enchanter, wardrobe, bank | P1 | Faceted aqua crystal (#33E0FF), not a gem shape. |
| J03 | `item/currency/gems.png` | Gems icon (menu/shop price buttons, not a real item) | 16×16 | Price buttons in shop, enchanter, wardrobe, bank | P1 | Round cut gem (#2EFFC0), clearly different from the crystal. |
| J04 | `item/currency/shards.png` | Shards (Black Market) icon (menu/shop price buttons, not a real item) | 16×16 | Price buttons in shop, enchanter, wardrobe, bank | P1 | Broken pink-red shard (#FF6B8A). |
| J05 | `item/currency/credits.png` | Store credits icon (menu/shop price buttons, not a real item) | 16×16 | Price buttons in shop, enchanter, wardrobe, bank | P1 | Orange ticket/coupon (#FF8C1A). |
| J06 | `font/icons/money.png` · U+E200 | Money glyph in text | 9 px tall, ≤ 9 wide | Chat, lore prices, scoreboard, tab | P2 | Green-gold coin stack with a `$`. Must read at 9 px; fallback symbol is shown to Bedrock-less/pack-less viewers. |
| J07 | `font/icons/crystals.png` · U+E201 | Island crystals glyph in text | 9 px tall, ≤ 9 wide | Chat, lore prices, scoreboard, tab | P2 | Aqua crystal ◆. Must read at 9 px; fallback symbol is shown to Bedrock-less/pack-less viewers. |
| J08 | `font/icons/gems.png` · U+E202 | Gems glyph in text | 9 px tall, ≤ 9 wide | Chat, lore prices, scoreboard, tab | P2 | Emerald-green cut gem ✦. Must read at 9 px; fallback symbol is shown to Bedrock-less/pack-less viewers. |
| J09 | `font/icons/shards.png` · U+E203 | Shards (Black Market) glyph in text | 9 px tall, ≤ 9 wide | Chat, lore prices, scoreboard, tab | P2 | Pink-red jagged shard ✧. Must read at 9 px; fallback symbol is shown to Bedrock-less/pack-less viewers. |
| J10 | `font/icons/credits.png` · U+E204 | Store credits glyph in text | 9 px tall, ≤ 9 wide | Chat, lore prices, scoreboard, tab | P2 | Orange ticket. Must read at 9 px; fallback symbol is shown to Bedrock-less/pack-less viewers. |

## K. Money notes and cosmetic items

Notes are money only (owner decision); bands by amount (numbers in `pack.yml`).

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| K01 | `item/note/money/small.png` | Money note, small band (< $100k) | 16×16 | `/withdraw`, trades, AH | P2 | Folded green banknote, one coin seal. |
| K02 | `item/note/money/medium.png` | Money note, medium band ($100k – $10M) | 16×16 | `/withdraw`, trades, AH | P2 | Banknote bundle with a gold band. |
| K03 | `item/note/money/large.png` | Money note, large band (≥ $10M) | 16×16 | `/withdraw`, trades, AH | P2 | Thick bundle with a red phoenix wax seal. |
| K04 | `item/cosmetic/tag.png` | Tag item (redeem a chat tag) | 16×16 | Crate rewards, AH, `/display` | P2 | Name tag with a small phoenix feather. |

## L. Depths drops and finds

35 drops from `depths-drops-defaults.yml` and `mobs.yml`. RP-D10: most keep the vanilla look (marked Optional); 6 reuse unrelated vanilla items and are **Needed**.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| L01 | `item/depths/ore/coal.png` | Depths coal drop (common) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L02 | `item/depths/ore/copper.png` | Depths copper drop (common) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L03 | `item/depths/ore/iron.png` | Depths iron drop (common) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L04 | `item/depths/ore/gold.png` | Depths gold drop (uncommon) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L05 | `item/depths/ore/redstone.png` | Depths redstone drop (uncommon) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L06 | `item/depths/ore/lapis.png` | Depths lapis drop (uncommon) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L07 | `item/depths/ore/diamond.png` | Depths diamond drop (rare) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L08 | `item/depths/ore/emerald.png` | Depths emerald drop (rare) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L09 | `item/depths/ore/amethyst.png` | Depths amethyst drop (rare) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L10 | `item/depths/ore/quartz.png` | Depths quartz drop (epic) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L11 | `item/depths/ore/netherite.png` | Depths netherite drop (epic) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: the raw material with a Depths stone crumb. |
| L12 | `item/depths/ore/voidstone.png` | Depths voidstone drop (legendary) | 16×16 | Satchel, `/sell`, forge costs | P2 | Needed: today an echo shard. Purple-black void ore chunk with a glow. |
| L13 | `item/depths/log/oak_log.png` | Depths oak log drop (common) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L14 | `item/depths/log/birch_log.png` | Depths birch log drop (common) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L15 | `item/depths/log/spruce_log.png` | Depths spruce log drop (common) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L16 | `item/depths/log/jungle_log.png` | Depths jungle log drop (uncommon) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L17 | `item/depths/log/acacia_log.png` | Depths acacia log drop (uncommon) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L18 | `item/depths/log/dark_oak_log.png` | Depths dark oak log drop (uncommon) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L19 | `item/depths/log/mangrove_log.png` | Depths mangrove log drop (rare) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L20 | `item/depths/log/cherry_log.png` | Depths cherry log drop (rare) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L21 | `item/depths/log/pale_oak_log.png` | Depths pale oak log drop (rare) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L22 | `item/depths/log/crimson_stem.png` | Depths crimson stem drop (epic) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L23 | `item/depths/log/warped_stem.png` | Depths warped stem drop (epic) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. If drawn: a cut log section. |
| L24 | `item/depths/mob/rotten_flesh.png` | Depths rotten flesh drop (common) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. |
| L25 | `item/depths/mob/bone.png` | Depths bone drop (common) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. |
| L26 | `item/depths/mob/spider_eye.png` | Depths spider eye drop (common) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. |
| L27 | `item/depths/mob/slime_ball.png` | Depths slime ball drop (uncommon) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. |
| L28 | `item/depths/mob/prismarine.png` | Depths prismarine drop (uncommon) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. |
| L29 | `item/depths/mob/glowstone.png` | Depths glowstone drop (uncommon) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. |
| L30 | `item/depths/mob/raider_scrap.png` | Depths raider scrap drop (rare) | 16×16 | Satchel, `/sell`, forge costs | P2 | Needed: today an iron nugget. Bent pillager armour scrap with a red stripe. |
| L31 | `item/depths/mob/blaze_rod.png` | Depths blaze rod drop (rare) | 16×16 | Satchel, `/sell`, forge costs | P2 | Optional: vanilla look is fine. |
| L32 | `item/depths/mob/wither_ash.png` | Depths wither ash drop (epic) | 16×16 | Satchel, `/sell`, forge costs | P2 | Needed: today gunpowder. Black-grey ash pile with a faint soul glow. |
| L33 | `item/depths/mob/warlord_sigil.png` | Depths warlord sigil drop (legendary) | 16×16 | Satchel, `/sell`, forge costs | P2 | Needed: today a nether star. Crimson/gold war medal (boss drop). |
| L34 | `item/depths/geode.png` | Geode (rare find, tiers 9+ mine) | 16×16 | Satchel, rare-find message | P2 | Needed: cracked stone ball with purple crystals inside. |
| L35 | `item/depths/key_fragment.png` | Crate key fragment (rare find, tiers 11+) | 16×16 | Satchel, combine into keys | P2 | Needed: a broken third of a gold key with an ember. |

## M. Spawner tiers

Item models `phoenix:spawner/<tier>` (tiers 1–5, `stacking.yml`). Placed spawner blocks stay vanilla.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| M01 | `item/spawner/1.png` | Spawner item, tier 1 (farm animals) | 16×16 | Inventory, shop, crates, spawner menu | P2 | Spawner cage with a iron grey frame; mob type stays in the name. |
| M02 | `item/spawner/2.png` | Spawner item, tier 2 (zombie, skeleton, spider…) | 16×16 | Inventory, shop, crates, spawner menu | P2 | Spawner cage with a copper frame; mob type stays in the name. |
| M03 | `item/spawner/3.png` | Spawner item, tier 3 (creeper, witch…) | 16×16 | Inventory, shop, crates, spawner menu | P2 | Spawner cage with a gold frame; mob type stays in the name. |
| M04 | `item/spawner/4.png` | Spawner item, tier 4 (blaze, enderman…) | 16×16 | Inventory, shop, crates, spawner menu | P2 | Spawner cage with a diamond blue frame; mob type stays in the name. |
| M05 | `item/spawner/5.png` | Spawner item, tier 5 (golem, shulker, evoker…) | 16×16 | Inventory, shop, crates, spawner menu | P2 | Spawner cage with a phoenix red + flame frame; mob type stays in the name. |

## N. Rank badges and logo glyphs
> **Done (v1):** N01–N17 (full names; colours follow the GUI sketches, owner check in RESOURCE_PACK_GUI.md §6). Still open: N18 `logo_wide`, N19 SkybornMC S.


Font `phoenix:icons`, shared with Bedrock. Rank names and colours come from the ranks plan (`docs/plans/RANKS_TITLES_ACHIEVEMENTS.md` §A, owner answers RK1/RK2 and 390: free rank **Ashborn**, donor ranks **Hatchling → Fledgling → Firewing → Flamecrest → Sunfeather → Pyrelord → Immortal**, won ranks Torchbearer, Flameheart, Firecrown). The old proposal names (Squire, Knight, Paladin, Warlord, Monarch) and the Ember badge are gone. Ids N01–N11 match the ranks plan's colour table; codepoints run U+E220–U+E22A.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| N01 | `font/icons/rank_ashborn.png` · U+E220 | Rank badge: Ashborn (free rank, was Ember) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in #BFB3A8 ash, the rank word baked in with a 1 px dark outline. |
| N02 | `font/icons/rank_hatchling.png` · U+E221 | Rank badge: Hatchling (donor rank 1) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in #F2C46B, the rank word baked in with a 1 px dark outline. |
| N03 | `font/icons/rank_fledgling.png` · U+E222 | Rank badge: Fledgling (donor rank 2) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in #FFA43A, the rank word baked in with a 1 px dark outline. |
| N04 | `font/icons/rank_firewing.png` · U+E223 | Rank badge: Firewing (donor rank 3) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in #FF7A1F, the rank word baked in with a 1 px dark outline. |
| N05 | `font/icons/rank_flamecrest.png` · U+E224 | Rank badge: Flamecrest (donor rank 4) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in #FF4E2E, the rank word baked in with a 1 px dark outline. |
| N06 | `font/icons/rank_sunfeather.png` · U+E225 | Rank badge: Sunfeather (donor rank 5) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in #FFD84D, the rank word baked in with a 1 px dark outline. |
| N07 | `font/icons/rank_pyrelord.png` · U+E226 | Rank badge: Pyrelord (donor rank 6) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in #D81E3A, the rank word baked in with a 1 px dark outline. |
| N08 | `font/icons/rank_immortal.png` · U+E227 | Rank badge: Immortal (donor rank 7) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in gradient #FF3D2E → #FFB000 → #FFF2A8, the rank word baked in with a 1 px dark outline. |
| N09 | `font/icons/rank_torchbearer.png` · U+E228 | Rank badge: Torchbearer (won season rank) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in #FFE27A, the rank word baked in with a 1 px dark outline. |
| N10 | `font/icons/rank_flameheart.png` · U+E229 | Rank badge: Flameheart (won season rank) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in #FF5A3C, the rank word baked in with a 1 px dark outline. |
| N11 | `font/icons/rank_firecrown.png` · U+E22A | Rank badge: Firecrown (won season rank) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in gradient #FFB000 → #FF3D2E, the rank word baked in with a 1 px dark outline. |
| N12 | `font/icons/rank_helper.png` · U+E230 | Rank badge: Helper (staff) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in aqua, the rank word baked in with a 1 px dark outline (staff: bold caps). |
| N13 | `font/icons/rank_mod.png` · U+E231 | Rank badge: Mod (staff) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in gold, the rank word baked in with a 1 px dark outline (staff: bold caps). |
| N14 | `font/icons/rank_admin.png` · U+E232 | Rank badge: Admin (staff) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in red, the rank word baked in with a 1 px dark outline (staff: bold caps). |
| N15 | `font/icons/rank_owner.png` · U+E233 | Rank badge: Owner (staff) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in dark red, the rank word baked in with a 1 px dark outline (staff: bold caps). |
| N16 | `font/icons/rank_builder.png` · U+E234 | Rank badge: Builder (staff) | 9 px tall, ≤ 32 wide | Chat name prefix, tab list, nametags (pack users) | P2 | Pill shape in yellow, the rank word baked in with a 1 px dark outline (staff: bold caps). |
| N17 | `font/icons/logo_small.png` · U+E205 | Phoenix logo, small | 9×9 | Chat prefix | P2 | Phoenix head/flame in brand red→gold. |
| N18 | `font/icons/logo_wide.png` · U+E206 | Phoenix logo, wide | 9 px tall, ≤ 48 wide | Scoreboard title, tab header | P2 | Phoenix with spread wings. |
| N19 | `font/icons/logo_skyborn.png` · U+E207 | SkybornMC S logo | 9×9 | Tab header/footer (network) | P2 | Frost-blue S: network art, not phoenix red. |

## O. Rarity tags

Colours from UI_STYLE §1.4.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| O01 | `font/icons/rarity_common.png` · U+E240 | Rarity tag: COMMON | 9 px tall, ≤ 48 wide | Lore footer of every item (`[RARE] TOOL`) | P2 | Small banner in the common colour with the word baked in. |
| O02 | `font/icons/rarity_uncommon.png` · U+E241 | Rarity tag: UNCOMMON | 9 px tall, ≤ 48 wide | Lore footer of every item (`[RARE] TOOL`) | P2 | Small banner in the uncommon colour with the word baked in. |
| O03 | `font/icons/rarity_rare.png` · U+E242 | Rarity tag: RARE | 9 px tall, ≤ 48 wide | Lore footer of every item (`[RARE] TOOL`) | P2 | Small banner in the rare colour with the word baked in. |
| O04 | `font/icons/rarity_epic.png` · U+E243 | Rarity tag: EPIC | 9 px tall, ≤ 48 wide | Lore footer of every item (`[RARE] TOOL`) | P2 | Small banner in the epic colour with the word baked in. |
| O05 | `font/icons/rarity_legendary.png` · U+E244 | Rarity tag: LEGENDARY | 9 px tall, ≤ 48 wide | Lore footer of every item (`[RARE] TOOL`) | P2 | Small banner in the legendary colour with the word baked in. |
| O06 | `font/icons/rarity_mythic.png` · U+E245 | Rarity tag: MYTHIC | 9 px tall, ≤ 48 wide | Lore footer of every item (`[RARE] TOOL`) | P2 | Small banner in the mythic colour with the word baked in. |
| O07 | `font/icons/rarity_special.png` · U+E246 | Rarity tag: SPECIAL | 9 px tall, ≤ 48 wide | Lore footer of every item (`[RARE] TOOL`) | P2 | Small banner in the special colour with the word baked in. |

## P. Pillar icons

UI_STYLE §1.5.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| P01 | `font/icons/pillar_mining.png` · U+E250 | Pillar/skill icon: mining | 9×9 | Scoreboard, `/skills`, action bar XP line, chat | P2 | Pickaxe, grey-blue #D0D8E4. |
| P02 | `font/icons/pillar_foraging.png` · U+E251 | Pillar/skill icon: foraging | 9×9 | Scoreboard, `/skills`, action bar XP line, chat | P2 | Axe/leaf, green #7CFF4D. |
| P03 | `font/icons/pillar_farming.png` · U+E252 | Pillar/skill icon: farming | 9×9 | Scoreboard, `/skills`, action bar XP line, chat | P2 | Wheat, yellow #FFE04D. |
| P04 | `font/icons/pillar_combat.png` · U+E253 | Pillar/skill icon: combat | 9×9 | Scoreboard, `/skills`, action bar XP line, chat | P2 | Sword, red #FF4D4D. |
| P05 | `font/icons/pillar_conquest.png` · U+E254 | Pillar/skill icon: conquest | 9×9 | Scoreboard, `/skills`, action bar XP line, chat | P2 | Banner, orange #FF9A3D. |
| P06 | `font/icons/pillar_fishing.png` · U+E255 | Pillar/skill icon: fishing | 9×9 | Scoreboard, `/skills`, action bar XP line, chat | P2 | Fish hook, aqua #3DD8FF. |

## Q. Island roles and chat tags

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| Q01 | `font/icons/role_leader.png` · U+E260 | Island role: leader | 9×9 | Island chat, members menu, `/is` info | P2 | Crown. |
| Q02 | `font/icons/role_admin.png` · U+E261 | Island role: admin | 9×9 | Island chat, members menu, `/is` info | P2 | Shield with star. |
| Q03 | `font/icons/role_mod.png` · U+E262 | Island role: mod | 9×9 | Island chat, members menu, `/is` info | P2 | Shield. |
| Q04 | `font/icons/role_member.png` · U+E263 | Island role: member | 9×9 | Island chat, members menu, `/is` info | P2 | House. |
| Q05 | `font/icons/role_coop.png` · U+E264 | Island role: coop | 9×9 | Island chat, members menu, `/is` info | P2 | Two hands. |
| Q06 | `font/icons/chat_island.png` · U+E270 | Chat channel tag: island chat | 9 px tall, ≤ 24 wide | Island chat lines | P2 | Small grass-green "IS" pill. |
| Q07 | `font/icons/chat_staff.png` · U+E271 | Chat channel tag: staff chat | 9 px tall, ≤ 24 wide | Staff chat lines | P2 | Small steel "ST" pill. |

## R. Scoreboard, action bar and boss bar glyphs

Font `phoenix:hud` (Java only, uses offsets). The scoreboard uses the currency (J), pillar (P) and logo (N) glyphs, so it needs nothing extra.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| R01 | `font/hud/xp_bar_00..10.png` · U+E330–E33A | Skill XP bar, 11 fill states (0/10 … 10/10) | 100×7 each | Action bar above the hotbar while gaining XP (Java) | P2 | Draw one bar (charcoal trough, ember fill, flame-gold tip) and export 11 fill states. |
| R02 | `font/hud/satchel.png` · U+E33B | Satchel icon | 9×9 | Next to the satchel fill bar in The Depths | P2 | Leather bag. |
| R03 | `font/hud/satchel_bar_00..10.png` · U+E33C–E346 | Satchel fill bar, 11 states | 40×5 each | Action bar in The Depths (Java) | P2 | Brown trough, fill goes green → orange → red when full. |
| R04 | `font/hud/hourglass.png` · U+E347 | Teleport warm-up hourglass | 9×9 | Action bar during /spawn, /is home warm-ups | P2 | Flame-gold sand. |
| R05 | `minecraft:gui/sprites/boss_bar/pink_background.png` + `pink_progress.png` | Boss bar, pink (empty + filled) | 182×5 each | Every boss bar of that colour (global override): events, boss fights, timers | P2 | Charcoal trough with a thin ember rim; filled part in the bar colour with a lighter top row. |
| R06 | `minecraft:gui/sprites/boss_bar/blue_background.png` + `blue_progress.png` | Boss bar, blue (empty + filled) | 182×5 each | Every boss bar of that colour (global override): events, boss fights, timers | P2 | Charcoal trough with a thin ember rim; filled part in the bar colour with a lighter top row. |
| R07 | `minecraft:gui/sprites/boss_bar/red_background.png` + `red_progress.png` | Boss bar, red (empty + filled) | 182×5 each | Every boss bar of that colour (global override): events, boss fights, timers | P2 | Charcoal trough with a thin ember rim; filled part in the bar colour with a lighter top row. |
| R08 | `minecraft:gui/sprites/boss_bar/green_background.png` + `green_progress.png` | Boss bar, green (empty + filled) | 182×5 each | Every boss bar of that colour (global override): events, boss fights, timers | P2 | Charcoal trough with a thin ember rim; filled part in the bar colour with a lighter top row. |
| R09 | `minecraft:gui/sprites/boss_bar/yellow_background.png` + `yellow_progress.png` | Boss bar, yellow (empty + filled) | 182×5 each | Every boss bar of that colour (global override): events, boss fights, timers | P2 | Charcoal trough with a thin ember rim; filled part in the bar colour with a lighter top row. |
| R10 | `minecraft:gui/sprites/boss_bar/purple_background.png` + `purple_progress.png` | Boss bar, purple (empty + filled) | 182×5 each | Every boss bar of that colour (global override): events, boss fights, timers | P2 | Charcoal trough with a thin ember rim; filled part in the bar colour with a lighter top row. |
| R11 | `minecraft:gui/sprites/boss_bar/white_background.png` + `white_progress.png` | Boss bar, white (empty + filled) | 182×5 each | Every boss bar of that colour (global override): events, boss fights, timers | P2 | Charcoal trough with a thin ember rim; filled part in the bar colour with a lighter top row. |

## S. Borders, frames and tooltips

Menu frames are part of the backgrounds (B). Island border colours and particles are done by the plugin (no art). Tooltip styles are new with the forced pack (RP-D15 now allowed).

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| S01 | `minecraft:misc/forcefield.png` | World border texture (island borders, global) | same size as the vanilla file | Island borders (every world border) | P2 | Ember hex/feather pattern, mostly transparent; it tiles and scrolls. Check the vanilla file's size in RP9, never copy its art. |
| S02 | `gui/sprites/tooltip/common_background.png` + `common_frame.png` | Item tooltip style, common | 100×100 each (9-slice, with `.mcmeta`) | Hover tooltip of items of that rarity (Java) | P2 | Charcoal background (90% opaque), frame in the rarity colour with ember corner pixels. Corners 3 px, edges repeat. |
| S03 | `gui/sprites/tooltip/uncommon_background.png` + `uncommon_frame.png` | Item tooltip style, uncommon | 100×100 each (9-slice, with `.mcmeta`) | Hover tooltip of items of that rarity (Java) | P2 | Charcoal background (90% opaque), frame in the rarity colour with ember corner pixels. Corners 3 px, edges repeat. |
| S04 | `gui/sprites/tooltip/rare_background.png` + `rare_frame.png` | Item tooltip style, rare | 100×100 each (9-slice, with `.mcmeta`) | Hover tooltip of items of that rarity (Java) | P2 | Charcoal background (90% opaque), frame in the rarity colour with ember corner pixels. Corners 3 px, edges repeat. |
| S05 | `gui/sprites/tooltip/epic_background.png` + `epic_frame.png` | Item tooltip style, epic | 100×100 each (9-slice, with `.mcmeta`) | Hover tooltip of items of that rarity (Java) | P2 | Charcoal background (90% opaque), frame in the rarity colour with ember corner pixels. Corners 3 px, edges repeat. |
| S06 | `gui/sprites/tooltip/legendary_background.png` + `legendary_frame.png` | Item tooltip style, legendary | 100×100 each (9-slice, with `.mcmeta`) | Hover tooltip of items of that rarity (Java) | P2 | Charcoal background (90% opaque), frame in the rarity colour with ember corner pixels. Corners 3 px, edges repeat. |
| S07 | `gui/sprites/tooltip/mythic_background.png` + `mythic_frame.png` | Item tooltip style, mythic | 100×100 each (9-slice, with `.mcmeta`) | Hover tooltip of items of that rarity (Java) | P2 | Charcoal background (90% opaque), frame in the rarity colour with ember corner pixels. Corners 3 px, edges repeat. |
| S08 | `gui/sprites/tooltip/special_background.png` + `special_frame.png` | Item tooltip style, special | 100×100 each (9-slice, with `.mcmeta`) | Hover tooltip of items of that rarity (Java) | P2 | Charcoal background (90% opaque), frame in the rarity colour with ember corner pixels. Corners 3 px, edges repeat. |

## T. Custom sounds

OGG Vorbis, **mono**, 44.1 kHz, ≤ 100 KB each, ≤ 2 MB total. You can record, synthesize or use **licensed** samples (CC0 or bought, keep the licence). The `/settings` sound toggles keep working. "Size" here = max length.

| # | Id / file | What it is | Size | Where it shows | P | Style note |
|---|---|---|---|---|---|---|
| T01 | `sounds/ui/click.ogg` · `phoenix:ui.click` | Menu clicks | ≤ 0.15 s | Plays for pack users (vanilla sound otherwise) | P2 | Soft wooden/ember tick. |
| T02 | `sounds/ui/open.ogg` · `phoenix:ui.open` | Menu opens | ≤ 0.4 s | Plays for pack users (vanilla sound otherwise) | P2 | Quiet whoosh + chest creak. |
| T03 | `sounds/ui/close.ogg` · `phoenix:ui.close` | Menu closes | ≤ 0.3 s | Plays for pack users (vanilla sound otherwise) | P2 | Reverse of open, softer. |
| T04 | `sounds/ui/page.ogg` · `phoenix:ui.page` | Page turns | ≤ 0.3 s | Plays for pack users (vanilla sound otherwise) | P2 | Paper flip. |
| T05 | `sounds/ui/success.ogg` · `phoenix:ui.success` | Success replies | ≤ 0.5 s | Plays for pack users (vanilla sound otherwise) | P2 | Bright two-note chime. |
| T06 | `sounds/ui/error.ogg` · `phoenix:ui.error` | Errors | ≤ 0.4 s | Plays for pack users (vanilla sound otherwise) | P2 | Low muted thud (not annoying on repeat). |
| T07 | `sounds/ui/warning.ogg` · `phoenix:ui.warning` | Confirm screens, warnings | ≤ 0.5 s | Plays for pack users (vanilla sound otherwise) | P2 | Single low pling. |
| T08 | `sounds/economy/coins.ogg` · `phoenix:money.coins` | Money received, quick-buy | ≤ 0.6 s | Plays for pack users (vanilla sound otherwise) | P2 | Coins clinking. |
| T09 | `sounds/economy/register.ogg` · `phoenix:sell.register` | Sell chest "Sell" | ≤ 0.8 s | Plays for pack users (vanilla sound otherwise) | P2 | Cash-register ding + coins. |
| T10 | `sounds/level/skill.ogg` · `phoenix:levelup.skill` | Skill level up | ≤ 1.5 s | Plays for pack users (vanilla sound otherwise) | P2 | Rising ember arpeggio. |
| T11 | `sounds/level/tool.ogg` · `phoenix:levelup.tool` | Tool level up | ≤ 1.5 s | Plays for pack users (vanilla sound otherwise) | P2 | Anvil ring + rising tone. |
| T12 | `sounds/level/island.ogg` · `phoenix:levelup.island` | Island level up | ≤ 2 s | Plays for pack users (vanilla sound otherwise) | P2 | Fuller fanfare. |
| T13 | `sounds/level/unlock.ogg` · `phoenix:unlock` | Tier/feature unlocks | ≤ 1.5 s | Plays for pack users (vanilla sound otherwise) | P2 | Lock click + chime. |
| T14 | `sounds/tools/hammer.ogg` · `phoenix:forge.hammer` | Forge craft | ≤ 1 s | Plays for pack users (vanilla sound otherwise) | P2 | Hammer on hot metal, hiss. |
| T15 | `sounds/enchants/apply.ogg` · `phoenix:enchant.apply` | Book applied | ≤ 1.2 s | Plays for pack users (vanilla sound otherwise) | P2 | Magic shimmer + page. |
| T16 | `sounds/drops/rare.ogg` · `phoenix:drop.rare` | Rare drop (book, blueprint, find) | ≤ 1 s | Plays for pack users (vanilla sound otherwise) | P2 | Sparkle sting. |
| T17 | `sounds/drops/legendary.ogg` · `phoenix:drop.legendary` | Legendary drop | ≤ 2 s | Plays for pack users (vanilla sound otherwise) | P2 | Big sparkle + phoenix cry. |
| T18 | `sounds/crates/open.ogg` · `phoenix:crate.open` | Crate lid opens | ≤ 1 s | Plays for pack users (vanilla sound otherwise) | P1 | Heavy lid creak + fire whoosh. |
| T19 | `sounds/crates/tick.ogg` · `phoenix:crate.tick` | Each roll step | ≤ 0.1 s | Plays for pack users (vanilla sound otherwise) | P1 | Short wooden tick (plays many times, keep it soft). |
| T20 | `sounds/crates/win_common.ogg` · `phoenix:crate.win.common` | Common win | ≤ 1 s | Plays for pack users (vanilla sound otherwise) | P1 | Small chime. |
| T21 | `sounds/crates/win_uncommon.ogg` · `phoenix:crate.win.uncommon` | Uncommon win | ≤ 1.2 s | Plays for pack users (vanilla sound otherwise) | P1 | Chime + sparkle. |
| T22 | `sounds/crates/win_rare.ogg` · `phoenix:crate.win.rare` | Rare win | ≤ 1.5 s | Plays for pack users (vanilla sound otherwise) | P1 | Bright sting. |
| T23 | `sounds/crates/win_epic.ogg` · `phoenix:crate.win.epic` | Epic win | ≤ 2 s | Plays for pack users (vanilla sound otherwise) | P1 | Rising magic sting. |
| T24 | `sounds/crates/win_legendary.ogg` · `phoenix:crate.win.legendary` | Legendary win (+ broadcast) | ≤ 3 s | Plays for pack users (vanilla sound otherwise) | P1 | Fanfare + phoenix cry. |
| T25 | `sounds/crates/win_mythic.ogg` · `phoenix:crate.win.mythic` | Mythic win (Eclipse) | ≤ 4 s | Plays for pack users (vanilla sound otherwise) | P1 | Dark choir hit + fanfare. |
| T26 | `sounds/world/teleport.ogg` · `phoenix:teleport.whoosh` | Teleports | ≤ 0.8 s | Plays for pack users (vanilla sound otherwise) | P2 | Fire whoosh. |
| T27 | `sounds/brand/rebirth.ogg` · `phoenix:brand.rebirth` | First join, season start, prestige | ≤ 4 s | Plays for pack users (vanilla sound otherwise) | P2 | The phoenix rebirth: flame burst + cry. The signature sound. |
| T28 | `sounds/depths/gate.ogg` · `phoenix:depths.gate` | Depths tier gate opens | ≤ 2 s | Plays for pack users (vanilla sound otherwise) | P2 | Stone grind + rumble. |
| T29 | `sounds/wardrobe/equip.ogg` · `phoenix:wardrobe.equip` | Set/loadout swap | ≤ 0.8 s | Plays for pack users (vanilla sound otherwise) | P2 | Cloth + armor clink. |

---

## Not on this list (on purpose)
- Placed blocks and the island/spawn builds: vanilla blocks (the builds are made by the builder team). Crate blocks at spawn are item displays (H), not real blocks.
- Status marks ✔ ✘ ★ ⌛ stay Unicode (RP-D13). Particles stay vanilla (no global particle recolour unless you ask, RP §11).
- Pets, quests, Black Market, Conquest/KOTH items, harvester hoe and sell wands: not built yet. They get their own art lines when their module is planned; the same rules apply.
- The player-inventory half of menus stays vanilla (RP-D16).

## Open questions for this brief (also in `OPEN_QUESTIONS.md`)
- ~~Donor rank names (Squire … Monarch) are proposals~~: settled by the ranks plan (Ashborn, Hatchling … Immortal, won ranks); N01–N11 can be drawn.
- Set Tokens: one token per **tier** (4 files, the item name says which set) or one per **set** (16 files)? Default: per tier.
- Blueprints: one roll per **rarity** (2) or one per **blueprint** with the tool sketch (4)? Default: per rarity.
- Worn set looks (G65–G80) are P1 because the pack is forced, but need code (RP4b) and a Bedrock check (Bedrock shows the vanilla base armor). Keep P1? Default: yes (RP-D19).
