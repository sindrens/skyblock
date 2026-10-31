# Phoenix icon pack (I7 v3, pack v2 phase 1)

A small Java resource pack that only repaints **menu icons**. Every menu button stays a real vanilla item (its "base
item"); Java players with the pack loaded see our own icon on it through a `custom_model_data` string. Bedrock,
declined, failed or still-loading players see the plain base item. No fonts, glyphs, shaders or sounds.

## Files
| Path | What |
|---|---|
| `icons.json` | **The catalogue** (single source of truth): key, base item, name, meaning colour, file, slice, motif, where it is used. Bundled into the plugin (`icons/icons.json`) by Gradle. |
| `vanilla-fallback.json` | The vanilla 1.21.5 item definition of every base item (structure only, no textures). Written once by `fetch-vanilla`. |
| `pack/` | The pack tree that is zipped into the plugin jar (`modules/pack`, Gradle task `packZip`). |
| `pack/assets/phoenix/textures/item/icon/<file>.png` | **The art.** One PNG per catalogue key. |
| `preview/contact-sheet.png` | Every icon at 4x on the vanilla slot grey, with key and base item. |

## Replace an icon (owner)
1. Open `preview/contact-sheet.png` to find the icon and its file name (`key` with `/` replaced by `_`, e.g. `feature/home` -> `feature_home.png`).
2. Draw `pack/assets/phoenix/textures/item/icon/<file>.png`: **16x16 or 32x32, RGBA**, transparent background. Keep the look of a vanilla item: no anti-aliasing, a dark 1 px outline, light top-left, dark bottom-right. The `motif` field in `icons.json` says what the picture should show.
3. Run `python3 tools/build_icons.py pack` (and `sheet` to refresh the preview), then `python3 tools/build_icons.py check` (must print `OK`).
4. Rebuild the plugin (`./gradlew build`). The new pack has a new hash, so clients download it again; players who are online get it after `/pack reload`.

The generator **never overwrites an existing PNG** (only `art --force-art` or `art --only <key>` do). Most icons are
still generator placeholders: hand-drawn pictures for the menu star, currencies, navigation and states, and a
coloured letter badge for every feature, travel, island and shop icon. Those badges are waiting for real art.

## Add or change an icon (developer)
1. Add a row to `icons.json` (key `group/name`, a base item that exists in 1.21.5, a meaning from the list in the build spec).
2. New base item: `python3 tools/build_icons.py fetch-vanilla` (one-time download of the 1.21.5 client jar into `tools/icons_lib/.cache/`, git-ignored).
3. `python3 tools/build_icons.py art` (draws only the missing PNG), `pack`, `sheet`, `check`.
4. Use it in code with `core.menus().icon(viewer, "group/name", name, lore)`.

Two icons may share a base item; two buttons on the same screen never do. Never point `item_model` at a pack-only
model (a client without the pack would show the missing-model cube).
