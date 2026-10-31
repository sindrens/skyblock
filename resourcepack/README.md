# resourcepack/

This folder holds the Phoenix Skyblock resource pack (RESOURCE_PACK.md §12, RP-D4: one folder in this repo).

**Today it holds the GUI art v1** (31 Oct 2026):
- 31 menu backgrounds;
- 41 menu buttons;
- 40 feature icons;
- 9 shop tabs;
- 5 currency icons;
- 22 font glyphs (currencies, logo, full-name rank badges).

The full guide is **[docs/plans/RESOURCE_PACK_GUI.md](../docs/plans/RESOURCE_PACK_GUI.md)**. It covers the ids, where each one shows, every menu's background, and how to wire it.

| Path | What |
|---|---|
| `java/assets/phoenix/textures/gui/menu/` | Menu backgrounds (font `phoenix:menu`, U+E300–U+E31E) |
| `java/assets/phoenix/textures/item/menu/` | Buttons (`phoenix:menu/<id>`), feature icons (`menu/icon/`), shop tabs (`menu/tab/`) |
| `java/assets/phoenix/textures/item/currency/` | Currency icons (`phoenix:currency/<id>`) |
| `java/assets/phoenix/textures/font/icons/` | Glyphs (font `phoenix:icons`, U+E200+) |
| `src/preview/` | Contact sheets. **Start with `screens.png`.** |
| `src/gui-art/` | The source: the art as letter maps, plus the generator. Edit there and regenerate (guide §12). |

**Rules:**
- **Original art only.** Never copy, trace or recolour Mojang textures.
- **Vanilla items keep vanilla textures.**

**Not here yet** (RP1 builds it):
- `ids.yml`, `pack.mcmeta`, `pack.png`;
- the generated model and font JSON;
- the Bedrock pack;
- item art (tools, books, sets, keys), sounds and HUD.
