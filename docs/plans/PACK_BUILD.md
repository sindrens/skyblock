# Pack build v1: plan (RP1–RP6, RP8, GUI-6)

Source prompt: `docs/plans/PACK_BUILD_PROMPT.md`. Art: `docs/plans/RESOURCE_PACK_GUI.md`. Planned with Sonnet 5.5 subagents (code map), checked and decided 31 Oct 2026.

## Owner decisions for this build
| Question | Answer |
|---|---|
| Shine shader risk | **Two packs.** `safe` (badges shine as still sparkles) and `shine` (adds the moving-shine shader overlay, 26.3 / format 97 only). `pack.yml variant: safe\|shine` + `/pack reload` switches. Start with `safe`, switch to `shine` after one client check. |
| Unit tests | Allowed: `./gradlew build`. **Never a test server.** |
| Release | Branches only. The main session merges in the order below, runs `./gradlew build`, releases with `deploy.sh`, and hands `docs/PACK_TEST_CHECKLIST.md` to the owner. |

## Technical decisions
- **Hosting:** the VPS has no web server. The plugin serves the zip itself over HTTP (`com.sun.net.httpserver`, port in `pack.yml`, default 25580, opened in the firewall), or uses `pack.yml url:` when set. The zips are built into the plugin jar (`plugin/src/main/resources/pack/`), so `deploy.sh` does not change.
- **Sending:** `AsyncPlayerConnectionConfigureEvent` (configuration phase, before the world loads): `ResourcePackRequest` with `required(true)`, prompt from `pack.yml`, one stable UUID per variant, block until a terminal status (timeout in config). Declined → friendly kick with the steps. Failed → "Couldn't download the pack, please rejoin" (`kick-on-fail`). Floodgate players are skipped (status `BEDROCK`).
- **Per-viewer rendering:** `PackService.hasPack(viewer)` (common). Glyphs, badge fonts, menu backgrounds and icon models only for pack viewers; everyone else gets text and vanilla items.
- **Icon carrier:** for pack viewers, a button's item becomes `PAPER` with `custom_model_data` strings `[<icon id>]` (the pack's `items/paper.json` selects on it). Frame panes keep their material with `menu/blank` (pane item files) and a hidden tooltip. Keys use `tripwire_hook` (`crate/key/<id>`). Never `item_model` (must-have 4).
- **Badges:** font text, name from the LuckPerms display name, look from the group id (`pack-ids.yml badges`). Tab/nametags per viewer.
- **Crates:** two item displays (body + lid, transform NONE, scale ≈1.05) per pedestal for pack viewers; the lid swings on its back hinge with display interpolation, then closes. The real chest stays for Bedrock / no-pack and for clicks.
- **Bedrock in v1:** text fallback and vanilla items. Geyser item mappings and Bedrock sound definitions are **v1.1** (RP5), the pack is tiny today (0.2 MB), so this is the next item after v1.

## Generated files (no hand edits)
| Generator | Output |
|---|---|
| `resourcepack/src/gui-art/*` | menu backgrounds, buttons, icons, tabs, currencies, glyphs |
| `resourcepack/src/badge-font/badges.py` | 6 badge fonts (`badge`, `badge_shine`, `badge_solid` + `_large` 5×7 letters), `ranks.json` |
| `resourcepack/src/crate-art/crates.js` | crate models + `_body`/`_lid` split, `hinges.json`, keys |
| `resourcepack/src/sounds/sounds.py` | original UI sounds: `ui.click`, `ui.success`, `ui.error`, `crate.chime` |
| `resourcepack/src/shaders/text.fsh` | shine overlay (vanilla 26.3 text shader + one block; compiled with glslangValidator in all 3 variants) |
| **`tools/build_pack.py`** | both zips (reproducible, `phoenix-java-<ver>[-shine]-<sha8>.zip`), `pack.mcmeta` (formats 55–97, "Phoenix Pack v<ver>"), `pack.png`, item files, fonts `icons`/`menu`/`space`, `sounds.json`, `pack-ids.yml`. `--check` fails when the committed output is stale. |

## Queue (one branch each, merge in this order)
| # | Branch | What | Tests |
|---|---|---|---|
| 1 | `q/pack-build` | Pack tooling + generated zips + `common/pack` (`PackService`, `PackIds`, `PrivateUse`), this plan, the checklist | `PackIdsTest`, `PrivateUseTest` |
| 2 | `q/pack-module` | `modules/pack`: config-phase send, kicks, HTTP host, `/pack reload`, `/pack status <player>`, `pack.yml`, messages, permissions, docs. Private-use strip on every player-text input. `phoenix:ui.*` pack sounds with vanilla fallback | send/status logic, URL/hash, strip call sites |
| 3 | `q/pack-menus` | Background glyph titles in parchment, PAPER icon swap, blank frame panes with hidden tooltip, for pack viewers | every menu class has a background; every icon id used exists |
| 4 | `q/pack-badges` | Badges in chat, tab, nametag, scoreboard, hover; settings: reduced motion, simple badges, bigger text, Immortal colour; `/rank preview <group> [name]` | badge string builder, settings |
| 5 | `q/pack-crates` | Yard: "» CLICK to open" hologram line, dust for Inferno+, approach chime, body/lid displays with the lid animation | hinge maths, ranges, cooldowns |

## Open questions
None blocking. Title position (ascent 13, offset +8) is tuned once on a client (checklist).
