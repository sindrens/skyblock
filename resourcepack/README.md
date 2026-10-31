# resourcepack/

This folder holds the Phoenix Skyblock resource pack (RESOURCE_PACK.md §12, RP-D4: one folder in this repo).

**Today it holds the GUI art v2** (31 Oct 2026, after the owner's second round):
- 31 menu backgrounds;
- 41 menu buttons;
- 40 feature icons;
- 9 shop tabs;
- 4 currency icons (money, crystals, shards, credits; gems are renamed credits);
- 21 font glyphs (currencies, logo, full-name rank badges);
- 7 crate chests for /warp crates (3D models, one per crate).

The full guide is **[docs/plans/RESOURCE_PACK_GUI.md](../docs/plans/RESOURCE_PACK_GUI.md)**. It covers the ids, where each one shows, every menu's background, and how to wire it.

| Path | What |
|---|---|
| `java/assets/phoenix/textures/gui/menu/` | Menu backgrounds (font `phoenix:menu`, U+E300–U+E31E) |
| `java/assets/phoenix/textures/item/menu/` | Buttons (`phoenix:menu/<id>`), feature icons (`menu/icon/`), shop tabs (`menu/tab/`) |
| `java/assets/phoenix/textures/item/currency/` | Currency icons (`phoenix:currency/<id>`) |
| `java/assets/phoenix/textures/font/icons/` | Glyphs (font `phoenix:icons`, U+E200+) |
| `java/assets/phoenix/models/item/crate/block/` + `textures/item/crate/block/` | Crate chests (`phoenix:crate/block/<crate>`) |
| `src/preview/` | Contact sheets. **Start with `screens.png`.** |
| `src/gui-art/` | The source: the art as letter maps, plus the generator. Edit there and regenerate (guide §13). |
| `src/crate-art/` | The crate chest generator (models, textures, preview). |

**Rules:**
- **Original art only.** Never copy, trace or recolour Mojang textures.
- **Vanilla items keep vanilla textures.**

**Not here yet** (RP1 builds it):
- `ids.yml`, `pack.mcmeta`, `pack.png`;
- the generated model and font JSON;
- the Bedrock pack;
- sounds, HUD bars and the crate opening animation.

The owner decided (31 Oct) that the only custom in-game items are the crate chests. Tools, books, sets and other items keep the vanilla look.
