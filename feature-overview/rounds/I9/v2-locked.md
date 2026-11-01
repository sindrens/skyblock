# GUI and menu standard v2 · id: I9 · status: Locked · version: 2

Cluster GUI-PACK (restart, 31 Oct 2026): I9 (this card), I7 (resource pack v2), G9 (/menu hub), plus short Review sections in G7, G8, G1, A3, A5. All questions (`feature-overview/questions/GUI-PACK.json`, GUI-Q1 to GUI-Q16) are answered and folded in; ledger ids `I9-R1-01` to `I9-R1-08` (plus `C4-R1-01`, `C5-R1-01`, `C5-R1-02`). Locked 31 Oct 2026. Inspiration brief (the owner's reference, not something to copy): `feature-overview/inspiration/MCHUB-GUI.md`.

Owner brief (31 Oct): start over; remove pack v1; **vanilla+**, simple and good; **no custom writing** (no custom font, no glyph or negative-space title tricks, no custom text rendering anywhere: titles, lore, scoreboard, bars and holograms stay in the vanilla font). This card replaces the GUI rules in `docs/UI_STYLE.md` §5, `docs/design/GUI_DESIGN.md` and `docs/planning/MENU_GUI.md` where they differ (their old pack and "Phoenix Night" parts are dead).

## In one sentence
One standard that every menu follows: compact centred layouts on vanilla chest screens with empty slots left empty, one minimal navigation row (Back, pages, Close), plain bold names whose colour carries meaning, a vanilla base item for every icon (so it reads on Bedrock and without the pack) that the pack later repaints with our own simple icon, a red danger icon far away from the safe buttons, and a short tooltip (coloured name, one grey sentence, one yellow `» ACTION` line).

## How it works

### 1. The seven rules
1. **Vanilla font, vanilla items, vanilla sounds.** Everything a player reads is normal text. An icon is always a real vanilla item whose own look and our name explain it. A pack (I7) may only repaint an icon; nothing needs it.
2. **One plain click does it.** Left click (Java) or tap (Bedrock) does the main thing on every button. Right-click and shift-click are optional Java shortcuts; their hint lines are not shown to Bedrock players.
3. **Same place every time.** Back, pages and Close are always in the same slots (Search exists only in the auction house).
4. **Colour = meaning** (table in section 8), and every colour state also has a shape (`✔ ✘ ●`), for colour-blind players and Bedrock's 16 colours.
5. **Less is more.** A screen shows what most players need now, grouped, with space between groups. Unbuilt features are hidden (decided 41, GUI-1 G2). Empty slots stay empty.
6. **Nothing can be taken out of a menu** (unchanged: all click types, drag, number keys, offhand are blocked).
7. **Help is in the world** (NPC and hologram help, A5; the hub's Help button; `/help <topic>`); lore stays short. There is no `?` button on every screen (owner, GUI-Q5).

### 2. Sizes and centred layouts
Chest screens are always 9 columns wide; only the row count changes (1 to 6). An empty slot is always drawn by the client as a vanilla slot cell, so "compact" means **fewer rows**, not narrower panels (a narrower panel would need a custom container texture; see I7).
| Kind of menu | Rows | Layout |
|---|---|---|
| Confirm, 2-3 choices, number picker pieces | 3 | choices at 11, 13, 15; Back 18; Close 22 |
| Small hub (You, Travel, island control, settings groups), up to 8 buttons | 3 | row 1, centred and spread (patterns below) |
| Medium menu, up to 14 buttons | 4 | rows 1-2 centred, navigation row on row 3 |
| Lists, browsers, shop, profiles, hub | 6 | 7 x 4 grid (slots 10-16, 19-25, 28-34, 37-43), navigation row on row 5 |
- **Centred spread patterns** (column numbers 0-8 in a row; same pattern on every menu): 1 button = 4; 2 = 3 and 5; 3 = 2, 4, 6; 4 = 1, 3, 5, 7; 5 = 0, 2, 4, 6, 8. Buttons of one meaning sit together; groups with different meaning get an empty column or row between them (the "Select item type" screenshot idea).
- **Slot 4 of the top row** is the optional summary item (your head, balance, "what is this").
- **Symmetry:** when a screen has an odd number of choices the middle one is the most common one.
- 5-row menus are not used (the 3 / 4 / 6 sizes cover everything); `MenuLayout` keeps 5 for old code until it is migrated.
- Decided (GUI-Q4, owner): compact, empty slots stay empty.

### 3. Filler rule
- **No border frame and no filler panes.** The black border, red corners and orange glow panes of today are removed. Empty slots stay empty (owner, GUI-Q4). There is no grey-pane option and no `filler` config key.
- A pane is only allowed to **mark a state** (for example a red pane in an error item), never to decorate. No pane has a tooltip.
- A subtle coloured pattern (MC Hub island menu) is not part of the standard; it can be added per menu later without changing anything else.

### 4. Fixed navigation row (minimal, owner GUI-Q5)
Only what the screen needs, nothing decorative. 6 rows (the row starts at 45; 4 rows: the same offsets from slot 27):
| Slot | Button | Base item | Shown when |
|---|---|---|---|
| 45 | Back | arrow | there is somewhere to go back to (never replaced by Previous page) |
| 46, 47 | screen actions | any | the screen needs them (shop: Sell 46, Undo 47; as built) |
| 49 | Close | barrier | always |
| 51 | Search | spyglass | **auction house only** (section 11) |
| 52 | Previous page | paper, count = target page | more than one page |
| 53 | Next page | paper, count = target page | more than one page |
- **Removed from the old bar:** `?` (slot 48), Main menu (slot 50) and Search on every list. The Nether Star in the last hotbar slot opens the hub from anywhere (section 10), so a Main menu button is not needed. The `/menu` hub alone has one extra button: Help (book, slot 50), which opens the help topics (`/help`, G10).
- 3 rows: Back 18, Close 22. **Navigation stack** and **in-place refresh** stay as built (`Navigation`, `title.update-in-place`).
- **Own icon for each button:** Back, Previous and Next page and Close each get our own simple icon in pack v2 phase 1 (I7), on the same vanilla base item. Without the pack, on Bedrock, or while the pack loads, the base item shows.
- Slots 46, 47 stay free for screen actions. A screen that needs a third action asks the framework first (`MenuLayout` logs a clash at start-up, as today).

### 5. Danger-action rule
- Delete island, delete profile, reset, disband, leave, permanent ban, destroy items: **one tile each, base item TNT with our own red danger icon in pack v2 phase 1** (owner, GUI-Q7), red name (`error` colour), red `» CLICK to ...` line, two short grey lines saying exactly what is lost.
- Placed in a **corner that is not next to Back, the page arrows or the main action** (default slot 8, top right; on menus with tabs the slot at the far end of the content block). Never in the centre.
- A click opens the existing `ConfirmMenu`; things that cannot be undone use `twoStep()` (second screen swaps the sides and says "Last check: this can't be undone"). Details and costs go on the middle item (13), not on the green button, so Bedrock players read them before tapping.
- Confirm tile = lime concrete, cancel tile = red concrete (own icons in phase 1); both are big enough to hit on a phone. The danger icon (TNT) is used for nothing else; Close stays a barrier.

### 6. Tooltip and lore template (one builder: `LoreBuilder`, texts in module message files)
```
Island Upgrades                        name: bold, colour = area meaning (section 8), no italics
Make your island better.               ONE grey sentence (`text` colour, wrapped at 32-36 characters)

| Level: 3 / 9                         optional section(s), at most 2: bold red `| Header`, dark red bar, grey label, white value
| Next: $12,000

» CLICK to open                        bold `action` yellow, verb UPPER CASE, always the last line (a footer like RARE TOOL comes after)
```
- A menu button is at most **6 lines**: name, one sentence, up to 3 info lines, one action line. Help text goes in `?`, NPCs and holograms, not the lore (owner 44).
- **Price lines** (shop, sell chest, AH): bold `BUY` and `SELL` style as in UI_STYLE §4.1: `| Buy Price: $8 each` (bold green), `| Sell Price: $2 each` (bold red, muted `Not sellable` when impossible). Currency amounts keep their currency colour.
- **Live note:** one coloured line only when a price moves: `Falling: -4% today` (orange) or `Rising: +6% today` (green). Words, not arrows (arrows are not Bedrock-safe). Shop buy prices are fixed (C5-Q3), so the note normally belongs to the sell price.
- **Java-only hint:** a muted line such as `Right-click: sell` or `Shift-click: buy a stack` appears only for Java viewers. The action line always names the plain click.
- Names are **plain and bold, coloured by meaning** (section 8; owner GUI-Q6). Action symbol `»` (on the Bedrock-safe list). Tooltip names have **no brackets**: in chat a yellow `[Word]` means "clickable" (`[Confirm]`, `[Accept]`), and a tooltip name must never look like a chat button (GUI-Q6). Category tags in chat events (`[Crate]`, G1) and hologram or NPC names (`[ Banker ]`, A5) are not tooltips and use their own colour, never the action yellow.
- Hide vanilla attributes, enchant lists and potion text; custom enchanted items keep the glint. Lore is generated from data, never edited by hand.

### 7. Icon rules (vanilla+)
1. Every menu button has a **base item** that explains it on its own (the map in UI_STYLE §5.5, extended in section 9). Two buttons on one screen never share a base item.
2. A pack model is an **optional repaint**: the same base item plus a `custom_model_data` string, sent only to viewers whose pack status is LOADED (`PackService.hasPack`). The material never changes (v1 swapped everything to PAPER; v2 does not), so a missing pack, a declined pack, Bedrock or a failed download all show the real base item.
3. **Never `item_model` pointing at a pack-only model**: a client without the pack draws the purple-and-black missing model. The one exception is `item_model` pointing at another **vanilla** model (for example `minecraft:golden_pickaxe` on the tier "gold" tool, built in `ToolItems`): that works for every Java client with no pack at all.
4. **Bedrock** never receives a pack model and never sees one (Geyser needs extra mappings and a Bedrock pack for that; not planned). Name and base item carry the meaning.
5. Back and page arrows use vanilla items (arrow, paper), not textured player heads. Heads stay only for people (profile, members, visit lists).
6. **Style of our own icons (owner, GUI-Q9):** the old v1 icons are discarded. New icons look like vanilla Minecraft items, simple, clearly different from each other and readable at a glance by every kind of player, new or old, Java or phone. The owner draws them (I7).
7. Counts are allowed as information (ready count, page number); they work on Bedrock.
8. Locked, loading, error, empty, ready, selected and toggle states keep today's looks (UI_STYLE §5.1): gray dye with `✘ Name`, clock `⌛ Loading...`, red glass `✘ Couldn't load this`, cobweb `Nothing here yet`, count plus glint plus `● 3 ready`, glint plus `✔ You are here`, lime dye `✔ On` / gray dye `✘ Off`.

### 8. Colour per meaning (names in menus; same colours as chat, scoreboard and holograms)
| Meaning | Colour (Bedrock fallback) | Used for names of |
|---|---|---|
| Money, buy, gain | green `#4DFF88` (green) | Shop, Sell, Bank, Auction House, prices |
| Reward, ready, new | flame gold `#FFD21A` (yellow) | Rewards, Daily, Crates, ready counts (the `» CLICK` line uses the near-identical `action` yellow) |
| Movement, time, events | ember orange `#FF8C1A` (gold) | Travel, Back and page arrows, timers, Events |
| The Depths, info, crystals | steel sky `#5AB4E5` (aqua) | The Depths, `?`, information, crystals |
| Skills, magic, mastery | violet `#B36BFF` (purple) | Skills, Enchanter, Wardrobe, Forge, Collection |
| People | social blue `#3DA5FF` (blue) | Friends, Members, Parties, Visit |
| Home, island | lime `#7CFF4D` (green) | Home, Island |
| Danger | error red `#FF3B3B` (red) | delete, reset, disband, leave only |
| Neutral, system | white `#FFFFFF` | Settings, Help, Rules, Profiles |
| Locked, empty | `locked` gray (dark gray) | locked entries |
- At most **5 accent colours on one screen**. The `/menu` hub is the one exception to "each name has its meaning colour": its names take their row colour (G9). Brand red is a frame colour in chat and titles; in menus it appears only as the danger colour and the `|` bars.
- Colours are named `area.*` tags in `style.yml`; no module writes a hex value.
- **Phoenix identity (owner, GUI-Q11):** the red-and-ember look comes from the red `|` bars, the red `▸` title marker, the ember and flame tones, the hub head and our own icons. Red as a **name** colour stays reserved for danger.

### 9. Icon map (base items; canonical list, extends UI_STYLE §5.5)
| Feature | Base item | | Feature | Base item |
|---|---|---|---|---|
| Home | red bed | | Shop | emerald |
| Island | grass block | | Sell | gold nugget |
| The Depths | iron pickaxe | | Bank | barrel (island: ender chest) |
| Travel | ender pearl | | Auction House | gold block |
| Forge | smithing table | | Skills | experience bottle |
| Enchanter | enchanting table | | Wardrobe | leather chestplate |
| Rewards | chest | | Top | golden helmet |
| Profiles | armor stand | | Titles | name tag |
| Achievements | totem of undying | | Settings | comparator |
| Store | diamond | | Rules | lectern |
| Quests | writable book | | Collection | bookshelf |
| Search (auction house only) | spyglass | | Danger | TNT |
- **Currencies (owner, GUI-Q9: colour + symbol + our own new simple icon):**
| Currency | Colour (`style.yml currencies.*`) | Text symbol | Base item in menus |
|---|---|---|---|
| Money | green | `$` | gold ingot |
| Crystals | aqua | `◆` | amethyst shard |
| Shards | pink | `✧` | prismarine shard |
| Credits | orange `#FF8C1A` | `✦` (already on the Bedrock-safe list; replaces the old gem star key `symbols.gems`, renamed in the C1 build) | diamond |
  Each gets one new simple vanilla-looking icon in pack v2 phase 1 (first slice). Bedrock and no-pack viewers see the base item and the coloured name. Credits are always written exact with separators (C1-R2-22); the old pack glyph U+E204 and the ember-ticket icon are retired.

### 10. Hotbar menu item, paging, titles, sounds
- **Menu item (as built, `MenuItem`):** a Nether Star named `Menu` in the last hotbar slot (slot 9); `» CLICK to open`. It cannot be dropped, moved, stored or lost; switch in `/settings` per profile (decided 50). Same slot and icon always (MC Hub, image 2, does the same).
- **Paging:** 28 entries per page; page number in the title `(2/5)` and as the count on the arrow. More than 5 pages means the menu needs tabs or a pick menu instead (search exists only in the auction house).
- **Titles:** vanilla font, a short noun in Title Case (`Shop`, `Spawner Shop`, `Island » Upgrades`), at most 2 parts and 32 characters (`title.max-parts`, `title.max-length`). Confirm titles are a verb and its object (`Disband island?`). The red `▸` marker and gray crumbs stay. A phase 2 container background (I7) must keep the dark-gray title readable.
- **Sounds:** the vanilla set in `sounds.yml` (click, menu-open, page, success, error, notify ...). One sound per kind of event, the same everywhere; `error` on every refusal. The three `pack-sound` entries are deleted.

### 11. Search and lists (owner, GUI-Q10: search only in the auction house)
- **Search exists only in the auction house** (C6): one Search button (slot 51), one chat prompt on Java, a text input form on Bedrock (existing `TypedText` and `BedrockForms`). It shows up to 28 hits on one page; the same button then reads `Clear search`. Case-insensitive contains match over the in-memory list; one search per second per player.
- **No Search button** in `/shop`, the island browser, player lists, collection, warps or help. Long lists use tabs, pick menus and sorting instead (small pick menus: one click picks, no cycling buttons, GUI1 answer).
- Kept as built: the player picker (`PlayerPickerMenu`) narrows a list of players by part of a name. It is how a player is chosen on Bedrock without typing a command, not a list search, so it stays.
- This reverses answer 718 (shop search) and drops the C5 search button (`C5-Q4`).

### 12. Buy and sell standard (owner, GUI-Q8; ties to C5 and C4, does not rewrite them)
- **Click map on a shop item** (replaces the C5-Q1 proposal and the old "right-click buys a stack"):
| Click | What happens | Platforms |
|---|---|---|
| Left | opens the **buy GUI** (as built) | Java and Bedrock |
| Right | opens the **sell GUI** of that item | Java. Bedrock cannot right-click: the buy GUI has a `Sell` button that opens the same sell GUI |
| Middle | **sells every one of that item in the inventory at once** | Java. A vanilla client sends a middle click in a chest screen only in creative mode, so survival players get the same action with **Shift + Right-click**; the build checks both on the test server. Bedrock: the `Sell all` button of the sell GUI |
| Shift + Left | buys one stack (as built) | Java |
- **Sell GUI (3 rows):** the item in the middle with price now, how many you carry and the trend word; `Sell 1`, `Sell 16`, `Sell 64`, `Sell all`, `Type an amount` (same layout and pieces as the buy GUI); a `Buy` button that switches back; Back and Close. One learned layout for both directions.
- **Sell rules = C4, exactly (same path as `/sell`):** every sell click calls `SellService` (dynamic price, Hot Item, push limit, bonus cap, ledger key `sell:<sale id>`, atomic, items taken then saved then paid, items back or to `/box` on failure, never the ground). Craftable items follow the computed price (C4 derived prices), so shop-buy then sell can never gain (the shop price stays above x7.5 of the best sell price, 712). An item the sell service does not accept shows `Not sellable` on its tile and the click only plays the error sound. A sale under $1 total shows `Too little to sell`. A one-item sale includes hotbar slots (the player named the item) and never touches armor, offhand or items C4 calls never sellable. No undo for sales (as C4). This needs the owner's veto only if he wants different rules.
- **No confirm page for single buys or sells.** C5 protects big buys with Undo (15 s) and a second click above 50% of your money. A confirm page exists only for costly or irreversible things (credits, island perks, ranks).
- **Hover on a shop tile** shows `| Buy Price` (bold green) and `| Sell Price` (bold red, or muted `Not sellable`) with the live note when the sell price moves, then `» CLICK to buy`. Java viewers also get the muted lines `Right-click: sell` and `Shift-click: buy a stack`.
- The sell chest (C4, `/sell`) stays the place for bulk selling of many item types; the Sell button in the shop bottom row (slot 46) opens it.
- Hover wording and slots are the same in `/shop`, `/sell`, `/ah` and the credit shop.

### 13. What the MC Hub screenshots suggest
| Idea in the screenshots | Decision | Why |
|---|---|---|
| Name colours carry meaning (chat) | **Adopt** (section 8, G1) | cheap, readable, vanilla |
| Compact content, empty slots left empty | **Adopt** (sections 2-3) | less is more; only fewer rows are possible without a custom container |
| Panel shorter than a chest, title plaque, pixel-font title | **Skip** | needs a custom container texture and a custom font (owner: no custom writing) |
| Star in last hotbar slot opens the hub | **Adopt** (as built) | we already have it |
| Icons grouped in columns by meaning, big gaps | **Adopt** (section 2 patterns) | scannable, symmetrical |
| Tooltip = coloured name + grey sentence + one yellow action | **Adopt** (section 6) | already our template |
| `[Name]` bracket names and `* Click *` lines on tooltips | **Skip** (owner GUI-Q6) | brackets are our clickable-chat marker; NPC names keep brackets (A5) |
| Fixed bottom row: arrow, magnifier, arrow | **Adopt, minimal** (section 4; owner GUI-Q5) | Back, pages and Close; Search only in the auction house |
| Red forbidden icon for Delete, far from safe buttons | **Adopt** (section 5) | with TNT, because Close is the barrier |
| Checker pattern of tinted panes | **Skip for now** | conflicts with empty slots; optional per menu later |
| Category row with a gap in the middle (store) | **Adopt** (credit shop, I10) | one row of category icons |
| `BUY (Left-Click)` / `SELL (Right-Click)` and a live price note | **Adopt** (section 12, owner GUI-Q8) | left buys, right sells, middle sells all; Bedrock reaches both through buttons |
| Scoreboard logo header, small icon before each line | **Skip** | needs a custom font; text and safe symbols instead (G7) |
| Scoreboard sections, progress bar line, timer line | **Adopt** (G7) | vanilla text |
| Tutorial boss bar | **Adopt** (A3) | already vanilla |
| Hologram title, NPC name plus command, `Click me` | **Adopt** (A5) | already close |
| Chat events with a short coloured category tag | **Adopt** (G1) | vanilla text |

### 14. Screens with their own rules
- **Profiles GUI (B17-R1-09):** follows this standard (compact, one danger tile for delete profile in a corner with the two-step confirm of B17-R1-08, stats per profile as B17-R1-18).
- **Island menu (B8):** 3 to 4 rows; Leave and Disband use the danger tile and confirm rules.
- **Mastery tree (B14-R0-01):** its own tree layout in 6 rows (branches, prerequisites, locked and unlocked nodes); nodes use the state looks of section 7 rule 8 and the same bottom row. B14 designs the layout; its node icons are a later I7 slice.

## What the player sees
- Menus that are as small as their content, with the same bottom row everywhere, plain text titles and short tooltips.
- Icons that are normal Minecraft items until the owner's icons exist; then Java players (the pack is forced for Java, I7) see our own simple icons. Bedrock players always see the normal items.
- Red, far-away delete buttons that always ask once or twice.

## Rules and limits
- Max 6 lines per button tooltip; max 36 visible characters per line; no italics; only Bedrock-safe symbols (UI_STYLE §2.1).
- No pack-only information: nothing important is readable only with the pack or only in hover text (the key fact is in the name or the middle item).
- Every menu has Back (except `/menu`) and Close.
- Performance: menus are built when opened and refreshed in place; per-viewer render is a map lookup; price lookups come from the 1 s cache; no per-tick work.
- Security: unchanged (debounce, no item removal, all value moves in one ledger transaction).

## Reality check (card vs docs vs code, 31 Oct)
| # | Docs or card say | Code or owner says | Resolution in this card |
|---|---|---|---|
| 1 | UI_STYLE §5.1 / GUI_DESIGN: black border, red corners, orange glow on every menu | `Menu.frame()` builds it on all 6-row menus; MC Hub and the owner's "less is more" use empty slots | Frame removed (section 3); decided |
| 2 | OWNER_ANSWERS_GUI1 G11/G12: "Phoenix Night for all GUIs, the pack also paints the inventory half" | Owner 31 Oct: start over, vanilla+, no custom writing | Superseded; the one optional background (I7) is not a per-menu skin |
| 3 | RESOURCE_PACK_GUI / PackTitles: negative-space font title paints a background per menu (31 backgrounds) | Same code is live behind `PackService` | Removed in phase 0 (I7) |
| 4 | `MenuIcons.render` swaps every tagged item to PAPER and hides frame panes | Works only for pack viewers | v2 keeps the base item and adds a model string (section 7) |
| 5 | UI_STYLE §5.1: Back and page buttons are textured heads (MHF arrows) | `MenuHeads` loads them asynchronously, Bedrock shows heads | Vanilla arrow and paper (section 7.5) |
| 6 | `MenuService` line 292: `titleUpdateInPlace` is skipped for pack viewers because the glyph title changes | Pack titles die with v1 | In-place refresh for everyone |
| 7 | G9 card and MENU_GUI.md: 28 or 29 hub buttons | `menu.yml` already has the GUI-2 hub: 15 buttons, 4 groups, You and Travel menus | G9 rewritten to the built hub |
| 8 | UI_STYLE §5.2: confirm uses wide green and red pane targets (10-12, 14-16) | Panes conflict with the filler rule | One lime and one red concrete tile (section 5) |
| 9 | UI_STYLE §5.5: Close is a barrier; MC Hub's danger icon is a barrier-like red icon | Same item for two meanings would confuse | Danger = TNT |
| 10 | GUI_DESIGN §5: lore never says Right-click or Shift-click | MC Hub shows them; Bedrock cannot right-click | Java-only hint lines (section 6) |
| 11 | C1-R1-14 says "ConfirmMenu (Bedrock form via BedrockForms)" | `ConfirmMenu` is a chest for everyone; `BedrockForms` only does typed text (decided 33 holds) | Confirm stays a chest menu everywhere |
| 12 | UI_STYLE §1.3 and `style.yml` still list gems (`✦`) and `hud.yml values.gems` | credits replaced gems (C1-R1-03); credits have no symbol | Credits symbol `✦` (section 9); C1 build renames the keys |
| 13 | OWNER_PRINCIPLES 7 and answers 33, 77: a forced pack will skin all GUIs, crates, sets and tools | Owner restart: icons first, at most one background later, nothing else | Decided: icons first, pack forced after join, no skin for all GUIs (I7) |
| 14 | `docs/commands/chat.md`: rank badges are pack fonts for pack viewers | The non-pack and Bedrock form is already `[Name]` in the rank colour | Plain coloured text becomes the only form (G1, G8) |

## Developer decisions (defaults; the owner only vetoes)
- **Framework, `common/menu` (one change fixes most of the 189 menus):** `Menu.frame()` is replaced by `navBar()` (Back, Close, pages only; no panes, no filler option); `MenuLayout` gets `search(rows)` (51, used by the auction house only) and a pure `Centered.slots(count)` helper; the `?` and Main menu slots are removed (the hub keeps Help at 50); frame pane config keys (`frame.border`, `accent`, `accent-glow`) are removed with a one-line migration note; `MenuIcons` is deleted in phase 0 (I7, GUI-PHASE0 build spec); the pack v2 phase 1 adds a new small icon seam (icon id on the button, `custom_model_data` set on the unchanged material for viewers whose pack is LOADED); `PackTitles` and the BLANK pane model are deleted with pack v1.
- **Per-viewer lore:** items are already built per viewer. A `java-only` lore key is hidden when the viewer is a Floodgate player (cached lookup).
- **Kept as built:** `ConfirmMenu` and `twoStep()`, `NumberPickerMenu`, `PickListMenu`, `OfferMenu` (Bedrock popups for chat offers), `Navigation` stack, debounce, state builders, `TypedText` and its private-use stripping (cheap input safety even without glyphs).
- **Migration order:** framework, then `/menu`, You, Travel, then module by module (the 189-menu list in `docs/design/MENU_FIXES.md`), staff screens last.
- **Config:** `menus.yml` (`layout.compact`, nav slots), `style.yml` (`area.*` colours), module `messages/*.yml` (all text). `/skyblock reload menus` applies it.
- **Tests (no server):** `MenuLayout` slot maths for 3, 4 and 6 rows, `Centered.slots`, no duplicate base item per screen (scan of the default layouts), every icon id has a base item, lore line count and width limit, Java-only line hidden for Bedrock, colour tags only from the area table (message audit, X1), the sell click map (pure: click type to action, Bedrock never needs right or middle click), no Search slot outside the auction house.
- **Commands and permissions:** none new. Tab completion unchanged.
- **Bedrock check (owner or one tester, Bedrock phone):** compact menus, arrow and paper icons, the page-number count, TNT and barrier, the text input form for auction search, the `Sell` button on the buy GUI and `Sell all` on the sell GUI.
- **Docs:** `docs/UI_STYLE.md` §5 and §6 are rewritten from this card at build time; `docs/design/GUI_DESIGN.md` gets a "superseded by I9" header; ROADMAP and CHANGELOG at release.

## Questions for the owner
None open. GUI-Q4 to GUI-Q10 and GUI-Q16 are answered (Decided).

## Connections
- Resource pack v2 (I7), main menu (G9), help and settings (G10), scoreboard (G7), tab (G8), chat (G1), messages (G4), tutorial bar (A3), NPCs and holograms (A5).
- Shop (C5) and selling (C4): slots and the hover standard; auction house (C6); credit shop (I10); island menu (B8); profiles (B17, B17-R1-09); tools (E10); wardrobe (E13); crates (F5); currencies (C1).

## Decided
- C1-R1-24: the GUI design needs a full overhaul: simple, easy to understand, readable for 1,000 players.
- 33: chest menus for Bedrock too, no pop-up forms (typed text uses a form only when typing is needed).
- 41 and GUI1 G2: unbuilt features are hidden.
- 44: everything reachable from menus, help in NPCs and holograms, lores clean.
- 50: the menu item is a per-profile setting.
- OWNER_PRINCIPLES 0 and 7: fun first, less is more; V2 bright palette, red identity, bold yellow `» ACTION` lines, red `|` bars.
- 31 Oct restart: vanilla+, no custom writing, pack v1 removed (details in I7).
- I9-R1-01 (GUI-Q4): compact menus, empty slots stay empty, no border, no filler panes.
- I9-R1-02 (GUI-Q5): minimal bottom row, Back, pages and Close only as needed, no Search on the shop, each with our own custom icon (base item fallback); `?` and Main menu button removed.
- I9-R1-03 (GUI-Q6): plain bold names, colour by meaning, yellow `» CLICK` line.
- I9-R1-04 (GUI-Q7): red danger icon in a corner, then confirm (twice for the irreversible), own custom icon.
- I9-R1-05 (GUI-Q8): shop item left = buy GUI, right = sell GUI, middle = sell all of that item; sells use the C4 rules.
- I9-R1-06 (GUI-Q9): currency = colour + symbol + our own new simple vanilla-looking icon; old icons discarded; credits symbol `✦`.
- I9-R1-07 (GUI-Q10): search only in the auction house; reverses answer 718.
- I9-R1-08: the ripples B17-R1-09 (profiles GUI) and B14-R0-01 (mastery tree) are absorbed in section 14.
- Conflict resolutions (ledger): C4-R1-01 (sell clicks use the C4 rules), C5-R1-01 and C5-R1-02 (C5-Q1 and C5-Q4 answered), I9-R1-06 (C1-R1-03 glyph U+E204 retired), I7-R1-02 and I9-R1-07 (owner reversals of 77 and 718).

## Open questions
None.

## Rejected ideas
- Pixel-font titles, a title plaque, per-menu backgrounds, logo headers, icons inside scoreboard lines (owner: no custom writing).
- Bedrock forms for menus (33).
- A per-menu container texture (impossible without font tricks).
- Grey filler panes in small menus (GUI-Q4: empty slots stay empty); keeping today's black frame with red corners.
- The `?` button and a Main menu button in every bottom row (GUI-Q5: minimal row).
- A Search button on the shop and on every list over 3 pages (GUI-Q10: auction house only).
- `[Name]` bracket names on tooltips (GUI-Q6); one colour for all names.
- Right-click buys a stack and "right-click sells one" (GUI-Q8 gave right = sell GUI).
- Credits without a symbol, colour-and-word-only currencies, reusing the v1 currency icons (GUI-Q9).
- Type-the-name confirm for deleting (GUI-Q7: a red danger tile and a confirm screen).

## Change log
- Review 1 (31 Oct): new card from the owner's restart, the MC Hub brief, UI_STYLE, GUI_DESIGN, MENU_GUI, OWNER_ANSWERS_GUI1/PACK1 and the code (`common/menu`, `menus`, `pack`). Nothing locked, no ledger lines.
- v2 / Locked (31 Oct): owner answers GUI-Q4 to GUI-Q10 folded in (I9-R1-01 to I9-R1-08): minimal bottom row without `?`, Main menu and shop search, own icons per button, red danger icon, currency look with new icons and `✦`, shop click map with a sell GUI, search only in the auction house; section 14 added; open questions removed.
