# Phoenix icon pack (I7 v3, pack v2 phase 1)

A small Java resource pack that only repaints **menu icons**. Every menu button stays a real vanilla item (its "base
item"); Java players with the pack loaded see our own icon on it through a `custom_model_data` string. Bedrock,
declined, failed or still-loading players see the plain base item. No fonts, glyphs, shaders or sounds.

## Files
| Path | What |
|---|---|
| `icons.json` | **The catalogue** (single source of truth). `icons` = custom icons (key, base item, name, meaning colour, file, slice, motif, where used, optional `variants` + `style`); `vanilla` = menu keys that are plain vanilla items (key -> item id). Bundled into the plugin (`icons/icons.json`) by Gradle. |
| `vanilla-fallback.json` | The vanilla item definition of every base item (structure only, no textures), from the 1.21.5 client. Written by `fetch-vanilla`; compared with newer clients by `verify-jars`. |
| `pack/` | The pack tree that is zipped into the plugin jar (`modules/pack`, Gradle task `packZip`). |
| `pack/assets/phoenix/textures/item/icon/<file>.png` | **The live art.** One PNG per live custom icon. |
| `variants/<file>/<style>.png` | Every variant of an icon with several looks (A/B/C, `ember`). Not in the pack. |
| `preview/contact-sheet.png` | Every icon and variant at 4x. |
| `../docs/design/ICON_MAP.md` | Which keys are custom and which vanilla material every other key uses. |

## Rule: custom only when there is no good vanilla item
A custom icon exists only for things that are not a real Minecraft item: currencies, UI actions (Back, pages, Close, Confirm, Cancel, Buy, Sell,
Sell all, Info, Help, Danger, Search, Lock, Undo, Fill, Type, switches) and a few feature symbols (Bank, Top, Auction). Everything else is a plain
vanilla item under a stable key in `"vanilla"`. To give one a custom picture later: add it to `icons`, remove it from `vanilla`.

## Switch a look, replace an icon (owner)
1. Open `feature-overview/inspiration/icons-v2-preview.html` (made by `python3 tools/icons_preview.py --out feature-overview/inspiration --open`) and pick a style.
2. `python3 tools/build_icons.py use nav/back B`, then `pack` and `check` (must print `OK`). Style `vanilla` removes the custom model. The hotbar menu item is the plain vanilla Nether Star (no key, no PNG). `feature/depths_lit` is the lit Depths pickaxe shown during the tutorial's Depths step.
3. Draw your own: put a **16x16 or 32x32 RGBA** PNG over `pack/.../icon/<file>.png` (key with `/` replaced by `_`). Keep the vanilla look: no anti-aliasing, a dark outline.
4. Rebuild the plugin (`./gradlew build`). The new pack has a new hash, so clients download it again; online players get it after `/pack reload`.

The generator **never overwrites an existing PNG** (only `art --force-art` or `art --only <key>` do), so hand-drawn art survives.
The art itself is code (`tools/icons_lib/sprites.py`, ASCII sprites with an automatic outline), so a redesign is a diff, not a binary.

## Add or change an icon (developer)
1. Add a row to `icons.json` `icons` (key `group/name`, a base item that exists in 1.21.5, a meaning, optional `variants` + `style`).
2. `python3 tools/build_icons.py fetch-vanilla` if the base item is new (needs network, or `--jar client.jar`), then `verify-jars --jar` for 1.21.5, the newest release and the Paper client version.
3. Add the sprite to `sprites.py` (`SINGLE` or `VARIANTS`), then `art`, `pack`, `sheet`, `check`.
4. Use it in code with `core.menus().icon(viewer, "group/name", name, lore)`.

Never base a custom icon on an item whose definition differs between client versions (`verify-jars` fails for those: bed, compass, shield, spyglass
and filled map are examples); the pack REPLACES the whole item definition, so a stale fallback shows the purple missing-model cube.
Never point `item_model` at a pack-only model.
