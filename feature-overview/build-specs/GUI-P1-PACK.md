# Build spec GUI-P1-PACK: pack v2 phase 1 (icons, forced prompt after join, host, fallback)

Cards: I7 v3 (Locked), I9 v2 (Locked), G9 v3 (Locked; only the icon list). Ledger: I7-R1-01..07, I9-R1-02, I9-R1-04, I9-R1-06, G9-R1-01. Written 31 Oct 2026 for the coder. No code in this file. Build order: **P1 (this) -> P2 (GUI-P2-MENUS.md) -> P3 (GUI-P3-SHOP.md)**, all on ONE worktree/branch `q/gui-phase1` created from `q/gui-phase0` (pack v1 is already removed there: no `modules/pack`, no `common/pack`, no `resourcepack/`, no `PackService`).

## 0. Goal, scope, owner rules
**Goal:** a small, forced (Java) icon pack. Every menu button keeps its real vanilla base item; Java players with the pack loaded see our own simple icon on it through a `custom_model_data` **string**. Bedrock, declined, failed, still-loading, "pack host down" and "pack module off" all show the plain base item. This build ships the whole mechanism and ALL icon files that P2 (menus) and P3 (shop) need, drawn by a generator script as placeholders the owner can overwrite PNG by PNG.

**In scope:** icon catalogue, generator tool, pack tree and zip, new `modules/pack`, `common` icon API (`Icons`, `PackService`, `MenuService#icon`), forced prompt after join, friendly kick, 15 s wait hook (essentials `JoinFlow` and tutorial `TutorialEngine`), host-down fail-open + staff alert, `/pack status|reload`, permissions, docs, tests.

**Out of scope (do not start):** any menu layout change (P2), any shop change (P3), a container background (phase 2), Geyser mappings or a Bedrock pack, fonts/glyphs/shaders/sounds (forbidden), a player-facing `/pack` command or settings switch (GUI-P9), `item_model` to a pack-only model (forbidden, I9 7.3), tool/skin/key/mastery icons (phase 3).

**Rules:** `./gradlew build` only if the owner says so; never start a server (`runServer`). One commit per numbered step (conventional commits, e.g. `feat(pack): icon catalogue and generator`). Edit bundled yml and its tracked copy under `server/plugins/PhoenixSkyblock/` in the same commit, then run `python3 tools/sync_server_configs.py` and commit what it adds (the newer commit wins; never leave the two different).

## 1. Assumes from phase 0 (verified in `/Users/sindreskogholt/Development/skyblock-wt-gui0`)
- `plugin/src/main/java/net/skybornmc/skyblock/SkyblockPlugin.java#registerModules` has no pack module; `settings.gradle.kts` and `plugin/build.gradle.kts` have no `modules:pack`.
- `common/menu`: `MenuService` has no `packs` supplier, no `render`, no glyph titles; `MenuButton`, `Menu`, `PagedMenu` exist unchanged otherwise (P2 reworks them). `Sounds` has no pack sounds.
- `MenuItem.create(Player)` (modules/menus) builds the hotbar Nether Star; `JoinFlow` (modules/essentials) and `TutorialEngine#joined` (modules/tutorial) are the A1 welcome code (section 8).
- `tools/` has `sync_server_configs.py` and no icon tooling. Python on the dev Mac is 3.9.6: write 3.9-compatible code, **stdlib only** (no Pillow; v1 used Pillow but it is not guaranteed on every machine).
- Facts re-checked in code: the pack-v1 pieces to reuse live in git: `git show pack-v1-final:<path>` run in `/Users/sindreskogholt/Development/skyblock-wt-wave1b` (`modules/pack/.../PackHost.java`, `PackUrls.java`, `PackStatuses.java`, `PackFiles.java`, tests `PackHostHttpTest`, `PackLogicTest`). Reuse those four classes almost verbatim (package `net.skybornmc.skyblock.pack`); everything else is new. The v1 `FORMAT_MIN 55 .. FORMAT_MAX 97` (1.21.5 .. 26.3) and `FIXED_TIME`/sorted-zip ideas are reused.

## 2. Design decisions made in this spec (developer decisions; owner only vetoes)
| # | Decision | Why |
|---|---|---|
| D1 | The icon **catalogue and the icon API live in `common`**, the pack **module** only does status, prompt, host, kick. | `icon(key)` must work with the pack module OFF (it then returns the plain base item). `MenuService` is in `common` and cannot depend on a module. |
| D2 | Catalogue = one committed file `resourcepack/icons.json` (single source of truth). Gradle copies it into `common` resources (`icons/icons.json`); the Python tool, the unit tests and the runtime read the same file. JSON because Python has no YAML in stdlib; Java reads it with `YamlConfiguration.loadFromString` (JSON is valid YAML), so no new library. | One place; no drift. |
| D3 | The generator **never overwrites an existing PNG** unless `--force-art`. It writes only missing ones. The owner replaces a file by name and it stays. | Owner draws later (I7 section 8). |
| D4 | Hosting: **built-in web server on TCP 25580** (v1 `PackHost`), URL `http://<host.public-host>:25580/<file>`, with `url:` as an external override. Needs ONE firewall rule: `ufw allow 25580/tcp` (added to `server/scripts/setup-vps.sh`; phase 0 told the owner to close the hand-made rule, so it must be re-opened by this script). No CDN, no DNS record. The fail-open self-check (D7) makes a closed port harmless (nobody is kicked). | Simplest that works behind the existing ufw (22, 25565/tcp, 19132/udp). Paper's `server.properties` pack lines stay empty (plugin sends it). |
| D5 | The plugin sends the pack with `ResourcePackRequest.required(false)` and **enforces "required" itself** (kick on DECLINED/FAILED). | In the play phase the vanilla disconnect for a required pack shows vanilla text; our friendly message must be the one players see. The coder verifies on the test server (owner permission) that no vanilla disconnect screen appears. |
| D6 | Status is **memory only**; no tables, no migration. | I7 rules. |
| D7 | Host health: async self-check every `host.check-seconds` (60): `GET` the pack URL (the one players use; `host.check-url` overrides) and compare the body SHA-1 with the bundled one; **2 consecutive failures = unhealthy, 1 success = healthy**; **initial state = unhealthy until the first success** (nobody is sent a dead URL). Unhealthy = the pack is not sent, nobody is kicked. One staff alert per outage. | I7 section 4 fail-open. |
| D8 | Fallback models: every `assets/minecraft/items/<base>.json` replaces the vanilla item definition for ALL items of that type for pack users, so its `fallback` must be the **exact vanilla definition** (block items, dyed leather, beds, chests, heads, compass/clock/spyglass/maps/helmets are not plain `item/<name>` models). A one-time tool step reads the real 1.21.5 client jar and stores each base item's vanilla `model` object in `resourcepack/vanilla-fallback.json`; the generator embeds it as `fallback`. | Without this a wrong fallback would break every crafting table or clock of every pack player. Structure data only, no textures copied. |
| D9 | Two page icons: `nav/prev` and `nav/next` (the ledger's "page" slice becomes 2; first slice = 10 icons). Both on `paper`. | User asked for Previous and Next. |
| D10 | Size: **75 icons** (I7 planned 50: the shop categories, island menu and travel extras were added). Budget: zip under 150 KB, hard 1 MB (test). | |

## 3. Icon catalogue (`resourcepack/icons.json`) - exact content
File format:
```
{"version": 1, "icons": [ {"key": "nav/back", "base": "arrow", "name": "Back", "meaning": "move", "file": "nav_back",
                            "motif": "chunky left arrow, ember orange", "slice": 1, "used": "all menus"}, ... ]}
```
- `key`: `[a-z0-9_]+(/[a-z0-9_]+)?`, unique; this string is the `custom_model_data` string. `base`: vanilla item id that exists in 1.21.5 (checked against `vanilla-fallback.json`). `name`: English plain name (what the owner sees on the contact sheet). `meaning`: one of `money reward move depths skills people home danger neutral locked currency-money currency-crystals currency-shards currency-credits` (= I9 section 8 colour; `currency-*` per I9 section 9). `file`: `<group>_<name>` without extension (key with `/` -> `_`). `slice`: 1 first slice, 2 menus (P2), 3 shop (P3). `used`: where P2/P3 use it. `motif`: one line of what the picture shows (drives the generator and tells the owner what to draw).
- **Two icons may share a base item** (different keys, different screens). Two buttons on the SAME screen never share a base (checked in P2).

| Slice | Key | Base item | Name | Meaning |
|---|---|---|---|---|
| 1 | `menu` | nether_star | Menu | reward |
| 1 | `currency/money` | gold_ingot | Money | currency-money |
| 1 | `currency/shards` | prismarine_shard | Shards | currency-shards |
| 1 | `currency/crystals` | amethyst_shard | Crystals | currency-crystals |
| 1 | `currency/credits` | diamond | Credits | currency-credits |
| 1 | `nav/back` | arrow | Back | move |
| 1 | `nav/prev` | paper | Previous page | move |
| 1 | `nav/next` | paper | Next page | move |
| 1 | `nav/close` | barrier | Close | neutral |
| 1 | `nav/danger` | tnt | Danger | danger |
| 2 | `state/confirm` | lime_concrete | Confirm | money |
| 2 | `state/cancel` | red_concrete | Cancel | danger |
| 2 | `state/on` | lime_dye | On | home |
| 2 | `state/off` | gray_dye | Off | locked |
| 2 | `state/locked` | gray_dye | Locked | locked |
| 2 | `state/loading` | clock | Loading | move |
| 2 | `state/undo` | clock | Undo | move |
| 2 | `state/empty` | cobweb | Nothing here yet | locked |
| 2 | `nav/search` | spyglass | Search (auction house only, planned) | depths |
| 2 | `nav/help` | book | Help | neutral |
| 2 | `ui/info` | knowledge_book | Info (planned, generic info tile) | depths |
| 2 | `feature/home` | red_bed | Home | home |
| 2 | `feature/island` | grass_block | Island | home |
| 2 | `feature/depths` | iron_pickaxe | The Depths | depths |
| 2 | `feature/travel` | ender_pearl | Travel | move |
| 2 | `feature/sell` | gold_nugget | Sell | money |
| 2 | `feature/shop` | emerald | Shop | money |
| 2 | `feature/bank` | barrel | Bank | money |
| 2 | `feature/auction` | gold_block | Auction House | money |
| 2 | `feature/forge` | smithing_table | Forge | skills |
| 2 | `feature/skills` | experience_bottle | Skills | skills |
| 2 | `feature/enchanter` | enchanting_table | Enchanter | skills |
| 2 | `feature/wardrobe` | leather_chestplate | Wardrobe | skills |
| 2 | `feature/rewards` | chest | Rewards | reward |
| 2 | `feature/top` | golden_helmet | Top | reward |
| 2 | `feature/staff` | shield | Staff | neutral |
| 2 | `feature/profiles` | armor_stand | Profiles | neutral |
| 2 | `feature/titles` | name_tag | Titles | skills |
| 2 | `feature/achievements` | totem_of_undying | Achievements | reward |
| 2 | `feature/settings` | comparator | Settings | neutral |
| 2 | `feature/store` | diamond | Store | currency-credits |
| 2 | `feature/rules` | lectern | Rules | neutral |
| 2 | `feature/quests` | writable_book | Quests | reward |
| 2 | `feature/collection` | bookshelf | Collection | skills |
| 2 | `travel/spawn` | campfire | Spawn | move |
| 2 | `travel/crate_yard` | tripwire_hook | Crate Yard | reward |
| 2 | `travel/market` | emerald | Market | money |
| 2 | `travel/visit` | compass | Visit Islands | people |
| 2 | `island/permissions` | comparator | Permissions | people |
| 2 | `island/warps` | ender_pearl | Warps | move |
| 2 | `island/settings` | repeater | Island settings | neutral |
| 2 | `island/invites` | paper | Invites | people |
| 2 | `island/notes` | writable_book | Notes | neutral |
| 2 | `island/chat_on` | glow_item_frame | Island chat on | people |
| 2 | `island/chat_off` | oak_sign | Island chat off | locked |
| 2 | `island/chat_colors` | orange_dye | Chat colors | neutral |
| 2 | `island/visit` | filled_map | Visit islands | people |
| 2 | `island/stats` | gold_ingot | Island stats | money |
| 2 | `island/bank` | ender_chest | Island bank | money |
| 2 | `island/upgrades` | anvil | Upgrades | skills |
| 2 | `island/visitors_open` | oak_fence_gate | Visitors welcome | people |
| 2 | `island/visitors_locked` | iron_bars | Island locked | locked |
| 3 | `shop/buy` | emerald | Buy | money |
| 3 | `shop/sell` | gold_nugget | Sell | money |
| 3 | `shop/sell_all` | hopper | Sell all | money |
| 3 | `shop/fill` | chest | Fill inventory | money |
| 3 | `shop/essentials` | crafting_table | Essentials | money |
| 3 | `shop/farming` | wheat_seeds | Farming | money |
| 3 | `shop/wood` | oak_log | Wood | money |
| 3 | `shop/stone_earth` | stone_bricks | Stone & Earth | money |
| 3 | `shop/special` | quartz_block | Special Blocks | money |
| 3 | `shop/colors` | light_blue_stained_glass | Colors | money |
| 3 | `shop/decoration` | lantern | Decoration | money |
| 3 | `shop/redstone` | redstone | Redstone | money |
| 3 | `shop/spawners` | spawner | Spawners | money |

The `motif` text of each row is written by the coder in the file (one short line, e.g. `feature/home`: "small red-roofed house"). Slice 1 is drawn first and checked first on Java and Bedrock (I7 section 9).
**Base items that are special in vanilla** (their fallback must come from the jar, D8): `compass`, `clock`, `spyglass`, `filled_map`, `leather_chestplate`, `golden_helmet`, `red_bed`, `chest`, `ender_chest`, `grass_block`, `shield`, `barrel`, `spawner`, `tnt` and every block item. The tool treats all bases the same way; this list is only for the manual check.

## 4. Ordered steps

### Step 1. Catalogue, tools, pack tree (no Java yet)
Files (all new):
- `resourcepack/icons.json` (section 3), `resourcepack/README.md` (how the owner replaces an icon: draw `resourcepack/pack/assets/phoenix/textures/item/icon/<file>.png` 16x16 or 32x32 RGBA, then `python3 tools/build_icons.py pack`, then rebuild).
- `tools/build_icons.py` (CLI) and `tools/icons_lib/` (`png.py`, `draw.py`, `art.py`, `font5x7.py`). Sub-commands:
  - `fetch-vanilla` (one-time, needs network, never run by Gradle or tests): reads `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`, downloads the **1.21.5** client jar into `tools/icons_lib/.cache/` (git-ignored; add the line to `.gitignore`), and for every distinct `base` in `icons.json` writes `assets/minecraft/items/<base>.json`'s top-level `"model"` object into `resourcepack/vanilla-fallback.json` as `{"base": {...model object...}}` (sorted keys). Fails loudly for a base that has no definition in the jar (= not a 1.21.5 item).
  - `art`: writes `resourcepack/pack/assets/phoenix/textures/item/icon/<file>.png` for every catalogue entry whose PNG is **missing** (`--force-art` overwrites all; `--only <key>` one).
  - `pack`: regenerates `pack.mcmeta`, `pack.png` (copy of `branding/skybornmc-logo-128.png`, header-cleaned PNG), every `assets/phoenix/models/item/icon/<file>.json` and every `assets/minecraft/items/<base>.json`; deletes generated files that are no longer in the catalogue.
  - `sheet`: writes `resourcepack/preview/contact-sheet.png` (every icon at 4x on the vanilla slot grey `#8B8B8B`, a row per group, key and base item name under it in the built-in 5x7 font) so the owner draws against the real background.
  - `check`: exits non-zero if the committed tree differs from what `art`(missing only) + `pack` would produce, or a PNG is not 16/32 square RGBA.
- Determinism: PNGs written with `zlib.compress(level 9)`, fixed chunk order, no metadata chunks, alpha only 0 or 255; JSON with `sort_keys`, 1-space indent, trailing newline (`json.dumps(obj, indent=1, sort_keys=True) + "\n"`).
- Generated pack content (exact):
  - `pack.mcmeta`: `{"pack": {"description": "Phoenix Skyblock icons", "pack_format": 55, "supported_formats": [55, 97], "min_format": 55, "max_format": 97}}` (the v1 range; the coder re-checks the 26.3 value in the Paper changelog and keeps the max if still right).
  - Model `assets/phoenix/models/item/icon/<file>.json`: `{"parent": "minecraft:item/generated", "textures": {"layer0": "phoenix:item/icon/<file>"}}`.
  - Item definition `assets/minecraft/items/<base>.json` (one per distinct base; cases sorted by key):
    `{"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": [{"when": "<key>", "model": {"type": "minecraft:model", "model": "phoenix:item/icon/<file>"}}, ...], "fallback": <vanilla model object from vanilla-fallback.json>}}`.
- Art rules for the generated placeholders (so they look like vanilla items and stay readable): 16x16, transparent background, **no anti-aliasing**, 1 px outline in a dark tone of the icon's main colour, 3-tone ramp (highlight top-left, base, shadow bottom-right), at most 6 colours plus outline, silhouette centred within 1 px, no text except single glyphs. Palette (constants in `draw.py`, from I9 section 8/9 and `style.yml`): money `#4DFF88`, reward/flame `#FFD21A`, move/ember `#FF8C1A`, depths/info `#5AB4E5`, skills `#B36BFF`, people `#3DA5FF`, home `#7CFF4D`, danger `#FF3B3B`, neutral `#FFFFFF`/grey `#B5ABA4`, locked `#5E5E66`, credits `#FF8C1A`, shards `#FF6B8A`, crystals `#33E0FF`, phoenix red accent `#FF2D3F`, outline base `#2B1B17`.
- `art.py` has a hand-built drawing function for each of: `menu` (five-point star with a small red ember core), the four currencies (money: green coin with a `$` mark; crystals: faceted aqua gem; shards: two pink prism shards; credits: orange diamond with a small sparkle), `nav/back|prev|next` (left arrow; chevron `<` / `>` on a white page), `nav/close` (neutral grey-white X), `nav/danger` (red triangle with a white `!`), `state/confirm` (lime tile with a white check), `state/cancel` (red tile with a white cross), `state/on|off|locked|loading|undo|empty|search|help|info`. **Every other key** (features, travel, island, shop) uses the generic `badge(meaning_colour, glyph)`: a rounded tile in the meaning colour with a 3x5 glyph (first letter of `name`) so the pack is complete and valid before any real art exists. Real motifs for these are the owner's job (README says so); the generator must not block on them.

Acceptance: `python3 tools/build_icons.py check` prints OK; the zip of the tree is under 150 KB; contact sheet looks sane (coder opens it).

### Step 2. Gradle wiring and `common` icon API (pure first)
- `common/build.gradle.kts`: `tasks.processResources { from(rootProject.layout.projectDirectory.file("resourcepack/icons.json")) { into("icons") } }`.
- New package `net.skybornmc.skyblock.common.icon`:
  - `IconDef` record `(key, base, name, meaning, file, slice)`.
  - `IconCatalog` (pure): `static IconCatalog load(String jsonText)`; `Optional<IconDef> get(String key)`; `Collection<IconDef> all()`; `static String cmdString(IconDef)` = the key. Validation on load (problems logged, entry skipped): key format, duplicate key, unknown field types. Reads via `YamlConfiguration.loadFromString` (top-level map, `icons` = list of maps).
  - `Icons` (the API other modules use; held by `MenuService`, reachable as `core.menus().icons()`):
    - `ItemStack item(Player viewer, String key)`: `new ItemStack(Material.matchMaterial(base))`; when `viewerGetsModel(viewer)` also sets the custom model data **string** `key` (Paper: `meta.getCustomModelDataComponent()`, `component.setStrings(List.of(key))`, `meta.setCustomModelDataComponent(component)`; verify the exact 26.3 API at compile time). Unknown key: logs once, returns a `BARRIER` named nothing (never a missing-model cube).
    - `Material base(String key)`; `boolean has(String key)`.
    - `static boolean viewerGetsModel(PackService.Status s)`: true **only** for `LOADED`. `boolean viewerGetsModel(Player p)` = `packs.get() != null && packs.get().hasPack(p)`.
    - Constructor takes the catalogue and a `Supplier<PackService>` (null-safe: no service = nobody gets models).
  - `MenuService`: new field `Icons icons`, `public Icons icons()`, `public ItemStack icon(Player viewer, String key)`, `public ItemStack icon(Player viewer, String key, Component name, List<Component> lore)` (base+model, then the same name/lore/flags as `item(...)`), and `public void packs(Supplier<PackService> packs)` (called in `SkyblockPlugin` after the `ServiceRegistry` exists: `menus.packs(() -> services.optional(PackService.class).orElse(null))`). `reload()` also reloads the catalogue (classpath).
- New `net.skybornmc.skyblock.common.pack.PackService` (interface, published by the pack module; `core.services().optional(PackService.class)`):
  - `enum Status { PENDING, LOADED, DECLINED, FAILED, BEDROCK, DISABLED, SKIPPED }` (SKIPPED = not sent: host down or already-in-game late offer).
  - `Status status(UUID player)`; `default boolean hasPack(Player viewer)` = `viewer != null && status(viewer.getUniqueId()) == Status.LOADED`.
  - `void afterAnswer(Player player, Runnable action)`: runs `action` on the main thread once, as soon as the player's status is not PENDING, or after `join.wait-seconds` (15), whichever comes first; immediately if not PENDING; never if the player left or was kicked. At most one pending runnable chain per player (several calls run in order).
  - `boolean healthy()`; `boolean required()`.
- New Bukkit event `net.skybornmc.skyblock.common.pack.PackStatusEvent(Player, Status old, Status now)` (plain `Event`, fired on the main thread by the pack module when a status changes). MenuItem listens (step 7).
- Static helper `net.skybornmc.skyblock.common.pack.PackWait.run(ServiceRegistry services, Player player, Runnable action)`: if a `PackService` exists calls `afterAnswer`, else runs `action` now. (`essentials` and `tutorial` use it; they never depend on the pack module.)
- `Style`: nothing here (area colours come in P2).
- Tests (this step): `IconCatalogTest`, `IconsTest` (section 6).

### Step 3. New module `modules/pack`
- `modules/pack/build.gradle.kts`: `dependencies { implementation(project(":common")) }` plus the zip task:
  ```
  val packZip = tasks.register<Zip>("packZip") {
      from(rootProject.layout.projectDirectory.dir("resourcepack/pack"))
      archiveFileName.set("phoenix-icons.zip"); destinationDirectory.set(layout.buildDirectory.dir("pack"))
      isPreserveFileTimestamps = false; isReproducibleFileOrder = true
  }
  tasks.processResources { from(packZip) { into("pack") } }
  ```
  `settings.gradle.kts`: `include("modules:pack")`; `plugin/build.gradle.kts`: `implementation(project(":modules:pack"))`; `SkyblockPlugin#registerModules`: `modules.register(new net.skybornmc.skyblock.pack.PackModule(core));` right **after** `MenusModule` (so menus exist; PackModule has no dependencies, it only publishes a service).
- Classes in `net.skybornmc.skyblock.pack` (module id `pack`):
  | Class | Responsibility |
  |---|---|
  | `PackModule` | `SkyblockModule` + `Listener` + `PackService`. `enable`: load `PackConfig`, `messages(ID)`, `apply()` (read `pack/phoenix-icons.zip` from the jar, SHA-1, pack file name `phoenix-icons-<sha1 first 8>.zip`, pack id `UUID.nameUUIDFromBytes("phoenix-pack:icons")`, start host or use `url`), register `PackService`, listeners, `/pack` command, start the health check. `disable`: stop host and check, clear statuses. `reload()`: reload config/messages, re-apply, resend (section 5.4). |
  | `PackConfig` | typed `pack.yml` (section 5.1). |
  | `PackHost` | v1 class, unchanged behaviour (GET exact path only, virtual threads). |
  | `PackUrls` | v1; `packId(String)` takes the fixed string `"icons"`. |
  | `PackFiles` | v1 `sha1(byte[])`; `extract` writes the zip to `plugins/PhoenixSkyblock/pack/<file>` (atomic move) and no longer compares with a stored hash (the hash is computed from the bundled bytes; the old `pack-ids.yml` is gone). |
  | `PackStatuses` | v1 mapping `terminal(ResourcePackStatus)`, `terminalByName`, plus new pure `kickKey(Status, boolean required, boolean enforce, boolean healthy)` (section 5.3). |
  | `PackJoinPolicy` (new, pure) | `Decision decide(boolean bedrock, boolean enabled, boolean healthy, boolean firstOffer)` -> `SEND` / `SKIP_BEDROCK` / `SKIP_DISABLED` / `SKIP_HOST_DOWN`. |
  | `PackWaiters` (new, pure) | map `UUID -> pending runnables + deadline`; `add(uuid, action, deadlineMillis)`, `complete(uuid) -> List<Runnable>`, `expired(now) -> Map<UUID, List<Runnable>>`, `drop(uuid)`. Ticked once per second by the module (one repeating task, no per-player tasks). |
  | `HostHealth` (new, pure state) | `record(boolean ok)`; fields `healthy`, `failures`; returns an enum `STAYED`, `WENT_DOWN`, `CAME_UP` so the module alerts only on transitions; initial `healthy=false` ("unknown"). |
  | `HostCheck` (new) | async `java.net.http.HttpClient` GET with 5 s timeout; ok = HTTP 200 and SHA-1 of the body equals the bundled one. Runs every `host.check-seconds` on the async scheduler (`core.scheduler().asyncRepeat`), first run at enable (delay 0). |
- Module messages `modules/pack/src/main/resources/messages/pack.yml` + tracked copy (section 5.2). Permissions `modules/pack/src/main/resources/permissions/pack.yml` (section 5.5). Rank grants `server/permissions/pack.txt`.

### Step 4. Join prompt, answers, kick (in `PackModule`)
Exact behaviour:
1. `PlayerJoinEvent` at `MONITOR`: `PackJoinPolicy.decide(...)`. `core.bedrock().isBedrock(uuid)` is reliable here (play phase); do not use the name prefix. `SKIP_*` sets the status (`BEDROCK`, `DISABLED`, `SKIPPED`) and fires `PackStatusEvent`; nothing is sent. `SEND`: status `PENDING`, enforce flag true, send `player.sendResourcePacks(request)` with `ResourcePackInfo.resourcePackInfo(packId, uri, sha1)`, `.required(false)`, `.prompt(prompt)`, `.replace(true)`, and a callback that maps the client status through `PackStatuses.terminal` and hops to the main thread (`core.scheduler().ensureMain`). Also schedule a **timeout** `timeout-seconds` (60) task per player (cancelled on answer/quit): still PENDING then = `FAILED`.
2. A terminal answer: `LOADED` -> status LOADED, event, `PackWaiters.complete`. `DECLINED` / `FAILED`: status set, event, waiters completed (they no-op because the player is about to be kicked), then `PackStatuses.kickKey(...)`: kick only when `enabled && required && enforce && healthy-at-send-time`. A host that went down between send and answer means no kick (checked at answer time too). Kick = `player.saveData()` then `player.kick(msg.component(player, key))`. Quit/kick cleans statuses, waiters and the timeout.
3. `PlayerResourcePackStatusEvent` (play phase, covers late/`/pack reload` resends): same mapping for the pack id only; `DISCARDED` is ignored; **late offers are never enforced** (`enforce=false`: reload resend, host-recovered resend).
4. Reload and recovery: `/pack reload` and "host came back" resend to every online Java player whose status is not BEDROCK, with `enforce=false`, `.required(false)`, same id (a new hash replaces the old copy). After a reload: one chat line `reload.notice` to online players that were sent the pack.
5. `PackService.afterAnswer` is backed by `PackWaiters` + the 1 s ticker; deadline = join time + `join.wait-seconds`.
6. Fire `PackStatusEvent` only on a real change.

### Step 5. Config, messages, commands, permissions (exact)
#### 5.1 `pack.yml` (typed `PackConfig extends ModuleConfig`, `fileName()` = `pack.yml`)
| Key | Default | Note |
|---|---|---|
| `enabled` | `true` | false: nothing sent, everyone DISABLED, icons plain |
| `required` | `true` | false: offer only, never kick |
| `url` | `""` | external full URL of the zip (its content must equal the bundled one; checked by the health check) |
| `host.bind` | `""` | restart |
| `host.port` | `25580` | restart; TCP; needs `ufw allow 25580/tcp` |
| `host.public-host` | `play.skybornmc.net` | used to build the URL when `url` is empty |
| `host.check-url` | `""` | empty = the URL players use; set `http://127.0.0.1:25580/<file>` if the VPS cannot reach its own public IP (hairpin) |
| `host.check-seconds` | `60` | 10..600 |
| `host.check-failures` | `2` | 1..10 |
| `prompt` | `<gradient:#ff9a3c:#ff3d2e><bold>Phoenix Skyblock icons</bold></gradient> <gray>A tiny pack (about 100 KB): menus get their own icons.` | MiniMessage on the client screen |
| `timeout-seconds` | `60` | 5..600 |
| `join.wait-seconds` | `15` | 0..60; the A1 welcome waits at most this long (0 = never wait) |
Header text: "Phoenix icon pack (I7 v3). Reload: /pack reload".

#### 5.2 Messages (`messages/pack.yml`, English, MiniMessage + style tags)
```
kick:
  declined: "<c:warning><bold>Phoenix Skyblock needs its icon pack</bold></c:warning><newline><newline><c:text>The pack only repaints menu icons. It is tiny (about 100 KB) and downloads once.<newline><newline><c:text>To turn it on:<newline><c:value>Multiplayer > Phoenix Skyblock > Edit > Server Resource Packs: Enabled</c:value><newline><newline><c:text>Then join again."
  failed: "<c:warning><bold>The icon pack didn't load</bold></c:warning><newline><newline><c:text>Your game couldn't download the pack, so we stopped you here instead of letting you play with broken icons.<newline><newline><c:text>Join again to retry. If it keeps happening, check that <c:value>Server Resource Packs</c:value> is <c:value>Enabled</c:value> for this server and that your connection is stable."
alert:
  host-down: "<prefix><c:error><sym:cross></c:error> <c:text>The icon pack host can't be reached (<c:value><reason></c:value><c:text>). The pack is not required until it is back, so nobody is kicked and new players join without icons."
  host-up: "<prefix><c:success><sym:check></c:success> <c:text>The icon pack host is back. The pack is required again."
reload:
  notice: "<prefix><c:text>The icon pack was updated. Accept the download to see the new icons."
command:
  reloaded: "<prefix><c:success><sym:check></c:success> <c:text>Pack reloaded and offered again to <c:value><count></c:value> online player(s)."
  status-one: "<prefix><c:value><player></c:value><c:text>: <c:value><status></c:value> <c:muted>(<info>)"
  status-all: "<prefix><c:text>Pack: <c:value><loaded></c:value> loaded, <c:value><pending></c:value> loading, <c:value><bedrock></c:value> Bedrock, <c:value><other></c:value> other. Host: <c:value><health></c:value><c:text>."
status:
  loaded: "<c:muted>pack loaded, sees our icons"
  pending: "<c:muted>not answered yet or still downloading"
  declined: "<c:muted>declined the pack"
  failed: "<c:muted>download or loading failed"
  bedrock: "<c:muted>Bedrock player, plain items"
  disabled: "<c:muted>the pack is turned off in pack.yml"
  skipped: "<c:muted>not sent (host down or a late offer)"
health:
  up: "up"
  down: "down"
```
The `<reason>` of `alert.host-down` is one of `timeout`, `HTTP <code>`, `wrong file`, `no answer yet` (plain text, never a stack trace).

#### 5.3 Pure rule `PackStatuses.kickKey(status, required, enforce, healthy)`
`DECLINED` -> `kick.declined` only if `required && enforce && healthy`; `FAILED` -> `kick.failed` under the same condition; everything else empty. (No separate `kick-on-fail` key: failed and declined behave the same.)

#### 5.4 Commands (staff only; **tab completion on every argument**)
| Command | Does | Permission |
|---|---|---|
| `/pack status` | one chat line `status-all` (counts of online players per status, host up/down) | `skyblock.pack.status` |
| `/pack status <player>` | `status-one` for one online player; tab: online players (`Args.onlinePlayer("player")`) | `skyblock.pack.status` |
| `/pack reload` | reload `pack.yml` + messages, re-host, offer again (section 4 step 4) | `skyblock.pack.reload` |
`CommandSpec.command("pack")` with `.category("Staff")`, examples, subs `status` (optional online-player arg) and `reload` (no args). No player-facing command.

#### 5.5 Permissions (`modules/pack/src/main/resources/permissions/pack.yml`)
- `skyblock.pack.status` (op): `/pack status`. `skyblock.pack.reload` (op): `/pack reload`. `skyblock.pack.alerts` (op): receives the host-down / host-up chat line (also written to the console log and one audit row `pack_host_down` / `pack_host_up`, category `CONFIG`, actor `Actor.system("pack")`).
- `server/permissions/pack.txt`: helper = `skyblock.pack.status`; admin = `skyblock.pack.reload`; mod and admin = `skyblock.pack.alerts` (`lp group mod permission set skyblock.pack.alerts true`, same for `admin`). Every node granted is declared (PermissionFilesTest).

### Step 6. Host, ufw, docs
- `server/scripts/setup-vps.sh`: after `ufw allow 19132/udp` add `ufw allow 25580/tcp   # Phoenix icon pack web server (modules/pack, host.port)`. A one-time live step for the owner/deploy key (ask first, firewall): `sudo ufw allow 25580/tcp`.
- `server/CONFIG_NOTES.md`: add the pack row (port, URL, fail-open, `pack.yml` keys).
- `docs/commands/pack.md` (table: Command, Description, Permission, Status), then `./gradlew commandsIndex`. `docs/UI_STYLE.md` section 5.5: add "icons come from `resourcepack/icons.json`; a base item per key". `CHANGELOG.md`: "Icon pack v2: catalogue of 75 icons (generator placeholders), forced prompt after join, friendly decline kick, fail-open host check, /pack status|reload."
- `docs/FEATURES.md` I7 row and `docs/STATUS.md`: phase 1 built.

### Step 7. Consumers inside this build
- `modules/menus/.../MenuItem.java#create(Player)`: build the item through `nav.core.menus().icon(player, "menu", name, lore)` instead of `new ItemStack(Material)` (keeps the material setting; model only for LOADED viewers); keep `HIDE_*` flags, glint and the PDC key. Add a `PackStatusEvent` listener: when the new status is `LOADED` (or changes away from it) re-create the item in the menu-item slot **only if the slot still holds our item** (`is(item)`), via the existing placement helper (no duplicate item can appear). The 5 s check keeps working unchanged.
- `modules/essentials/.../JoinFlow.java`: in `firstJoin` wrap the 20-tick body (sound `unlock`, fallback welcome) and in `onJoin` the welcome-back title (10-tick task) with `PackWait.run(core.services(), player, ...)`. The privacy-line task and the broadcast stay untouched. Re-check `player.isOnline()` inside.
- `modules/tutorial/.../TutorialEngine.java#joined(Player, Progress)`: keep `online.put(id, p)` (locks) immediate; wrap the rest of `joined` (the NEW start with title/chat/spawn camera, and the ACTIVE resume title/rejoin teleport) in `PackWait.run`, and inside re-check `player.isOnline() && online.get(id) == p`. `StepRules.start`/`resume` already guard double starts.
- Nothing else uses the pack in this build; P2/P3 call `menus.icon(...)`.

## 5b. Data and migrations
None. No tables, no player data, no migration. Config `pack.yml` is typed and creates itself.

## 6. Tests to write (names; the coder runs them only if the owner allows)
`common`:
- `IconCatalogTest`: parses the bundled `icons/icons.json`; keys unique and well-formed; files unique and equal to key with `/`->`_`; every `meaning` is from the allowed list; every `base` is in `vanilla-fallback.json`; the ten slice-1 keys exist; slice values 1..3; **two buttons never need distinct keys for the same base** is NOT tested here (P2 tests screens).
- `IconsTest`: `viewerGetsModel` true only for `LOADED` (all seven statuses); no service = false; unknown key returns the barrier fallback and logs once (inject the catalogue and a logger; no ItemStack where the server registry is needed: test the pure decision functions `Icons.shouldTag(Status, boolean known)`).
- `PackWaitTest` (in `PackWaitersTest` of the module) see below.
- `NoPackOnlyModelsTest` (source scan over `common/src/main`, `modules/*/src/main`): no `item_model` call whose argument contains `phoenix:`; no `\uE000-\uF8FF` escapes outside `PrivateUse`; the string `phoenix:item/icon` appears only in `modules/pack` and `tools/`.
`modules/pack`:
- `IconTreeTest`: reads `../../resourcepack`: every catalogue file has a PNG (`javax.imageio`), square 16 or 32, RGBA; a model JSON; each base's `items/<base>.json` has exactly one case per key of that base and `fallback` equal to the `vanilla-fallback.json` entry; no PNG/model/items file without catalogue entry; `pack.mcmeta` has `min_format 55`/`max_format 97`; total size of `resourcepack/pack` < 150 KB.
- `PackZipTest`: the bundled `pack/phoenix-icons.zip` on the classpath contains `pack.mcmeta`, every PNG, no entry with a timestamp other than the fixed one, and its entry names are sorted.
- `PackHostHttpTest`, `PackLogicTest` (renamed `PackUrlsTest`, `PackStatusesTest`): ported from tag `pack-v1-final`; `PackStatusesTest` adds the kick table of 5.3 (declined/failed x required/enforce/healthy, LOADED/BEDROCK never kick, DISCARDED ignored).
- `PackJoinPolicyTest`: Bedrock never sent; disabled never; host down never; healthy Java = SEND.
- `PackWaitersTest`: complete runs actions once in order; expired after the deadline; `drop` on quit removes them; a second `complete` returns nothing; `wait-seconds 0` expires at once.
- `HostHealthTest`: initial unhealthy; one success = healthy (`CAME_UP`); two failures after healthy = `WENT_DOWN` once; a third failure = `STAYED` (no repeated alert); a single failure does not flip.
- `PackConfigTest`: defaults equal 5.1 and ranges hold.
- `PermissionFilesTest` (existing) must stay green: `skyblock.pack.status|reload|alerts` declared once, granted nodes declared.
`essentials` / `tutorial`: `PackWaitHookTest` (pure: the wrapper runs now without a service and defers with one; use a fake `PackService`).

## 7. Edge cases and abuse cases
- Client sends fake `LOADED`: only cosmetic (it receives custom_model_data strings it cannot render: the fallback model shows). Harmless.
- A player who never answers: `timeout-seconds` -> FAILED -> kick (unless host down). Slow download beyond 60 s -> kick with the "retry" text (acceptable; the file is 100 KB).
- Declined -> kicked every join: each join/quit pair may print a rank join line; accepted.
- Rejoin spam to hammer the web server: exact-path GET only, 100 KB body, cached by clients by hash; accepted. The firewall port is the only new exposure; the host serves no directory listing.
- Host goes down mid-session: nobody is kicked later (`healthy` checked at answer time); new joiners are not sent the pack; staff get **one** alert; recovery resends non-enforced and posts one `host-up` line.
- Pack module off or `enabled: false`: `PackService` absent/DISABLED, `icon()` returns plain base items, `afterAnswer` runs now.
- Hash change after a rebuild: the file name and SHA-1 change, same pack id: clients replace the old copy; players online get it only on `/pack reload`.
- Staff in `/vanish` or staff mode are treated like everyone.
- `host.public-host` wrong: health check fails -> unhealthy -> nobody kicked; staff alert names the reason; the owner fixes `pack.yml` and `/pack reload`.
- Bedrock player with a Java-like name: decided by Floodgate uuid only.
- Players on 1.21.4 or older (via ViaVersion) may not understand item definitions: they decline or fail the pack and are kicked with the friendly text. Accepted; documented in the kick text.

## 8. Manual test checklist (owner; Java and Bedrock)
Java (needs the test server and owner permission; client with Server Resource Packs = Prompt):
1. Join: prompt "Phoenix Skyblock icons" appears AFTER you are in the world. Accept: no kick; the Nether Star in slot 9 shows our star (then `/menu`: P2 icons).
2. Join again: no download (cached), still no kick.
3. Decline the prompt: you are kicked with the friendly text (why, how to enable, retry). Join again, enable "Server Resource Packs: Enabled": works.
4. First-time account (new whitelisted test account): the welcome and tutorial camera start only after the pack is loaded (or 15 s); the prompt never sits over the cinematic.
5. Close the pack host (`/pack reload` with a wrong `host.public-host`, or stop the web server): within ~2 minutes one staff alert; new joins are not prompted or kicked; fix it and `/pack reload`: `host-up` line, online players are offered the pack again (decline does NOT kick them).
6. `/pack status`, `/pack status <player>` (tab completion lists online players), `/pack reload`: texts as in 5.2. A player without the permission gets the normal permission error.
7. `pack.required: false` + decline: no kick, plain base items. `enabled: false`: no prompt at all.
8. Holding a vanilla compass, clock, spyglass, filled map, leather chestplate, golden helmet, bed, chest, spawner, TNT, grass block: they look and behave exactly like vanilla with the pack on (fallback check, D8).
Bedrock (phone): join, no prompt, no kick; every later menu shows plain base items with coloured names; nothing says "pack" anywhere.

## 9. Risks and rollback
| Risk | Mitigation |
|---|---|
| Wrong vanilla fallback breaks vanilla items for pack players | D8 tool + `IconTreeTest` + checklist item 8 |
| Port 25580 closed -> pack never required | fail-open by design; staff alert; ufw line in setup script |
| `required(false)` shows a prompt without the "Disconnect" button | intended (friendly kick is ours); owner confirms on first run |
| 26.3 changes the pack format number | max kept at 97 (v1 value); coder re-checks the changelog before release |
| Custom model data API differs in Paper 26.3 | coder verifies `CustomModelDataComponent#setStrings` at compile time; fallback is `DataComponentTypes.CUSTOM_MODEL_DATA` |
Rollback: `git revert` of the merge, or set `pack.enabled: false` (no prompt, no kick, plain items) and `/pack reload`; no data to undo. The pack zip is never needed by anything but this module.

## 10. Size estimate
**L** (about 25 Java files in total, one Python tool with 4 helper files, 75 generated PNGs, about 200 generated JSON files, 12 test classes). Roughly 2-3 working days for the coder.

## 11. Questions the coder cannot settle (safe default applies)
1. Bedrock-only icon fidelity is out of scope: default none.
2. Is `ufw allow 25580/tcp` acceptable on the VPS? Default yes (it is the same port the owner opened for v1); if refused, set `pack.url` to an external host.
3. Should the health-check URL be the public URL (truthful, may fail with hairpin NAT) or loopback? Default public URL, `host.check-url` to switch.
