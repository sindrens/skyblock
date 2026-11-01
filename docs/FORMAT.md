# Phoenix FORMAT (v3 proposal, 1 Nov 2026)

The one format every new feature follows: menus, tooltips, titles, shop grids, scoreboard, tab, boss bar, holograms, chat, messages, items, sounds. **Developer version** of card I9 v3 (`features/I9.md`): templates with placeholders, colour tokens, icon keys, spacing and copy rules. If this file and I9 disagree, I9 (owner decisions) wins. Status: **Locked with I9 v3 (1 Nov 2026)**; the parts marked `[pack]` are the frame layer (build spec `feature-overview/build-specs/GUI-FONT.md`, font allowed by V3-Q1 b).

Principle: **less is more, details easy to understand.** One idea per line, one action per tile, one colour per meaning, one place for every fixed button. A new screen copies a template below; it never invents a look.

## 0. Three layers (what a viewer can see)
| Layer | Who sees it | What it holds |
|---|---|---|
| **Plain** | everyone (Java without pack, Bedrock, fail-open, staff test) | vanilla chest, vanilla font, base items, coloured names, symbols from §2 |
| **Icons** | Java with the pack LOADED | our cartoon icon on the same base item (`custom_model_data` string, catalogue key) |
| **Frame** `[pack]` | Java with the pack LOADED | panel, plaque, soft tile behind items, cartoon title lettering, logo (a custom font, titles/scoreboard/tab only) |
Rules: (1) plain is always complete: nothing important is readable only in a higher layer; (2) a viewer gets a higher layer only when `PackService.hasPack(viewer)` and not Floodgate; (3) tooltips, lore, chat, holograms and messages stay plain vanilla font everywhere (readable, copy-pasteable, Bedrock-identical).

## 1. Colour tokens (names live in `style.yml`; modules never write hex)
| Token | Hex (Bedrock) | Use |
|---|---|---|
| `<area:money>` | `#4DFF88` (green) | Shop, Sell, Bank, Auction House, prices, Buy |
| `<area:reward>` | `#FFD21A` (yellow) | Rewards, Daily, Crates, ready counts |
| `<area:move>` | `#FF8C1A` (gold) | Travel, Back, pages, timers, Events |
| `<area:depths>` | `#5AB4E5` (aqua) | The Depths, info, crystals |
| `<area:skills>` | `#B36BFF` (purple) | Skills, Enchanter, Wardrobe, Forge, Collection, upgrades |
| `<area:people>` | `#3DA5FF` (blue) | Friends, Members, Parties, Visit |
| `<area:home>` | `#7CFF4D` (green) | Home, Island |
| `<area:danger>` | `#FF3B3B` (red) | delete, reset, disband, leave only |
| `<area:neutral>` | `#FFFFFF` (white) | Settings, Help, Rules, Profiles |
| `<area:locked>` | `#5E5E66` (dark gray) | locked, empty progress dots |
| `<c:text>` / `<c:value>` / `<c:muted>` | `#E0E0E0` / `#FFFFFF` / `#A0A0A0` | sentences / values and names in text / asides |
| `<c:header>` + `<c:bar>` | `#FF2D3F` / `#D1202F` | bold `\| Header` and the `\|` bar (the red identity) |
| `<c:action>` | `#FFE135` | the `» CLICK` line and `[Clickable]` chat words only |
| `<c:success>` `<c:error>` `<c:warning>` | `#4DFF6A` `#FF3B3B` `#FFB020` | the ✔ ✘ mark, never a whole sentence |
| `<cur:money>` `<cur:crystals>` `<cur:shards>` `<cur:credits>` | green, `#33E0FF`, `#FF6B8A`, `#FF8C1A` | amounts of that currency |
| `<tag:crate|store|market|event|booster>` | `#FFD21A #FF2D3F #4DFF88 #FF8C1A #5AB4E5` | `[Tag]` at the start of server event lines |
Rules: max **5 accent colours per screen**; red as a name colour only for danger; every colour state also has a shape (`✔ ✘ ●`); every colour has a Bedrock legacy fallback that still reads.

## 2. Text rules
- Bedrock-safe symbols only: `✦ ✧ ◆ ● ▸ ▶ ★ ✔ ✘ ⚔ ⛏ ❤ ⌛ » « │ · •`. Currency symbols: money `$`, crystals `◆`, shards `✧`, credits `✦`.
- Names and titles: Title Case. Sentences: sentence case, short, plain English, no filler ("please", "successfully"). No italics anywhere. No brackets on tooltip names (brackets mean "clickable" in chat).
- Numbers: compact (`1.2k`, `3.4M`) in lore and HUD; full with separators (`1,250,000`) in confirmations, transactions and always for credits. Time: `2h 15m`, `45s`, `3d 4h`. Tiers: Roman numerals.
- Max **36 visible characters per tooltip line**, max **6 lines per tile** (shop tile: 7). One action line, always last.
- One word for the action: `Click`. A plain click works on Java and Bedrock. Right-click and shift-click are Java extras shown as one muted hint line.

## 3. Menu panels (the `Frame` layer; plain layer = same layout on a vanilla chest)
Chest width is always 9 columns; only the row count changes. **Four panel types, nothing else:**

### 3.1 `P3` small panel, 3 rows (27 slots): confirm, buy/sell GUI, number picker, You, Travel, small hubs (one content row, up to 9 tiles)
```
 row 0   .  .  .  .  S  .  .  .  D        S summary/wallet (4)   D danger corner (8)
 row 1   c  c  c  c  c  c  c  c  c        content, centred pattern (9-17)
 row 2   B  A  A  .  X  .  .  P  N        nav: Back 18, actions 19 20, Close 22, pages 25 26
```
### 3.2 `P4` medium panel, 4 rows (36 slots): island hub, members, upgrade detail, pick lists (two content rows, up to 14 tiles)
```
 row 0   .  .  .  .  S  .  .  .  D
 row 1   c  c  c  c  c  c  c  c  c        content (9-17)
 row 2   c  c  c  c  c  c  c  c  c        content (18-26)
 row 3   B  A  A  .  X  .  Q  P  N        nav: Back 27, actions 28 29, Close 31, Search 33 (auction only), pages 34 35
```
### 3.3 `P6` large panel, 6 rows (54 slots): `/menu` hub (four groups on four rows), upgrades, level, top, masteries, shop item grid, auction house, profiles (three or four content rows)
```
 row 0   .  .  .  .  S  .  .  .  D        tabs use row 0 (cols 1..7); when row 0 holds the wallet tile, tabs go on row 1
 row 1   .  c  c  c  c  c  c  c  .
 row 2   .  c  c  c  c  c  c  c  .        the 7 x 4 grid: slots 10-16, 19-25, 28-34, 37-43
 row 3   .  c  c  c  c  c  c  c  .
 row 4   .  c  c  c  c  c  c  c  .
 row 5   B  A  A  .  X  .  Q  P  N        nav: Back 45, actions 46 47, Close 49, Search 51 (auction only), prev 52, next 53
```
### 3.4 `List` = `P6` with the 7 x 4 grid and paging (shop item grid, auction house, visit lists): 28 entries per page; groups start on a new row; the page count is the count on the arrow items, not in the title
Fixed slots on every panel (offsets from the nav row start, `R`): **Back `R+0`, action A `R+1`, action B `R+2`, Close `R+4`, Search `R+6` (auction house only), Previous `R+7`, Next `R+8`**; summary tile slot `4`; danger tile slot `8`. `R` = 18, 27 or 45.

### 3.5 What is drawn `[pack]`
| Part | Drawn how | Rule |
|---|---|---|
| Panel | one glyph per panel type (`P3`, `P4`, `P6`) covers the whole window including the player inventory half | same size as the vanilla window (176 px wide); a narrower panel is impossible without replacing the chest texture for real chests too |
| Plaque | one of three widths above the panel top, 8 px above the window edge (pack constant `plaque_extend`, not a server setting) | holds the title noun; `S` up to 8 letters, `M` up to 12, `L` up to 16 |
| Empty slots | **not drawn**: the panel has no slot cells | the vanilla hover highlight still shows on hover |
| Item slots | a soft tile (cell glyph, 18 x 18) behind every slot that holds an item, composed per open | `gui.font.tiles` (true) |
| Player inventory half | normal cells, always drawn | it is the player's own inventory |

### 3.6 Title rules
- **Plain layer:** `▸ Island » Upgrades` (red marker, gray crumbs, at most 2 parts, 32 characters; unchanged).
- **Frame layer:** the plaque shows **one short noun** in the cartoon lettering (all caps): `UPGRADES`, `SHOP`, `FARMING`. The parent crumb is dropped (Back says where you came from). At most 16 letters. Confirm screens are a verb and its object (`DISBAND?` 8 letters); the full question is on the middle tile. The plain title text stays the Title Case source so screen readers read real words.
- Paging, balance, state: never in the title (page = count on the arrow items; balance = the wallet tile).
- Wording list: one key per menu in `messages/<module>.yml` `title.short` (the plaque text); the plain breadcrumb stays in `title.crumb`.

## 4. Tile (tooltip) templates
```
Name                          bold, colour = meaning (§1), Title Case, no brackets, no italics
One grey sentence.            `<c:text>`, at most 36 characters per line, at most 2 lines
| Header                      optional, at most 2 sections: bold red `| Header`
| Label: value                at most 3 rows per section: dark red bar, gray label, white value
» CLICK to open              bold `<c:action>`, verb UPPER CASE, ALWAYS the last line
```
- Java-only hint (muted, one line, hidden for Bedrock): `Right-click: sell`.
- State looks (same everywhere): locked = gray dye + `✘ Name` muted; loading = clock + `⌛ Loading...`; error = red glass + `✘ Couldn't load this`; empty = cobweb + `Nothing here yet`; ready = count + glint + `● 3 ready`; selected = glint + `✔ You are here`; on/off = lime dye `✔ On` / gray dye `✘ Off`.
- **Danger tile** (delete, reset, disband, leave): TNT icon, red name, red `» CLICK to ...`, two short gray lines saying what is lost; slot 8, never next to Back or the main action; always a confirm screen, two steps for the irreversible.
- **Wallet tile** (slot 4 of every screen that spends, nowhere else): currency icon, name `Your Money` in `<cur:money>`, the full amount in the currency colour. Costs inside lore are `| Cost: $12k`.
- **Shop tile:** name, `| Buy Price: $8 each` (bold green), `| Sell Price: $2 each` (bold red, muted `Not sellable`), one live note line only when the price moved, then `» CLICK to buy`; Java hint lines `Right-click: sell` and `Shift-click: buy a stack`.

## 5. Other surfaces
| Surface | Template | Limits | Layer |
|---|---|---|---|
| **Scoreboard** | title (logo `[pack]` or phoenix-gradient `PHOENIX SKYBLOCK`), `\| You` section, one place section (`\| Island`, `\| Depths` ...), footer `play.skybornmc.net` | 10 lines with title and footer; at most 3 rows per section; at most 1 progress line `●●●●○○○○○○ 46%` and 1 timer line | logo per viewer; text always |
| **Tab** | header: logo `[pack]` or `Phoenix Skyblock` gradient + `12 online · Season 1`; footer address only; entries `[RANK] name` in the rank colour | staff line only for staff | logo per viewer |
| **Boss bar** | `<bold>Word</bold> · one instruction`, bar colour = meaning (tutorial yellow, boss and event red, booster steel sky) | one bar per purpose, fill = progress | plain always |
| **Hologram** | line 1 bold flame title (a noun), lines 2-3 white, last line muted `Click me` only if clickable | 3 lines, 36 characters | plain always (a text display is seen by everyone) |
| **NPC label** | `[ Banker ]` bold flame, `/bank` white, `Click me` muted | one or two words for the role | plain always |
| **Chat reply** | `PHOENIX » ` then `✔` or `✘` mark, gray sentence, white values, the fix in a few words | one line | plain |
| **Chat event** | `[Tag] ` (`<tag:...>` bold) then one line; never clickable | one line, no prefix | plain |
| **Chat click word** | `<c:action>[Confirm]</c:action>` with a hover text | Title Case | plain |
| **Action bar** | `✔ +64 Torch -$256 · $1,199,744 left` | one message per 1.5 s | plain |
| **Screen title** | bold flame line + gray subtitle | big moments only, with a sound | plain |
| **Custom item name/lore** | rarity colour bold name, tile template §4, footer line (`RARE TOOL`) after the action line | no italics, hide vanilla attributes | plain (+ icon layer) |

## 6. Icon keys (stable names; the Java look is swappable, the key never changes)
`custom_model_data` string = key. The base item is the Bedrock and no-pack look; to swap either, edit one row in `resourcepack/icons.json`. Which shape replaces the arrows is decided from the icon preview (V3-Q6); the keys below stay.
| Key | Role | Base item today | Colour meaning |
|---|---|---|---|
| `nav/back` | go back | arrow | move |
| `nav/prev`, `nav/next` | previous / next page (count = target page) | paper | move |
| `nav/close` | close | barrier | neutral |
| `nav/danger` | danger tile | tnt | danger |
| `nav/search`, `nav/help` | auction search, hub help | spyglass, book | depths, neutral |
| `state/confirm`, `state/cancel` | confirm / cancel tiles | lime / red concrete | money / danger |
| `state/on`, `state/off`, `state/locked`, `state/loading`, `state/undo`, `state/empty` | state looks (§4) | lime dye, gray dye, gray dye, clock, clock, cobweb | per state |
| `currency/money|crystals|shards|credits` | wallet and cost tiles | gold ingot, amethyst shard, prismarine shard, diamond | currency colours |
| `feature/*`, `travel/*`, `island/*`, `shop/*` | one per feature, same everywhere | see the icon map in I9 §9 | meaning colour |
| ~~`menu`~~ | the hotbar star | **removed in v3: the star is the plain vanilla Nether Star** | |

## 7. Frame asset keys `[pack]` (catalogue `resourcepack/frame/panels.json`, codepoints from U+E300; Bedrock's own emoji sheets use U+E0xx and U+E1xx, so we never use those)
| Key | What | Codepoints |
|---|---|---|
| `panel/p3`, `panel/p4`, `panel/p6` | whole-window panel glyphs | `E300 E301 E302` |
| `plaque/s`, `plaque/m`, `plaque/l` | title plaques (80, 112, 144 px wide) | `E320 E321 E322` |
| `cell/slot` | soft tile behind an item, one glyph per row height | `E330 .. E335` |
| `logo/wide`, `logo/small` | scoreboard and tab logo | `E340 E341` |
| `space/-128 .. +128` | negative-space and positive-space glyphs (powers of two) | `F800 .. F80F` |
| `font/title` | cartoon lettering, plain ASCII letters and digits map to the glyphs (narrators read real words) | ASCII |
Fonts: one font `phoenix:frame` (panel, plaque, cell, logo, space) and one font `phoenix:title` (lettering). Player text is stripped of `U+E000..U+F8FF` (`PrivateUse.strip`, kept).

## 8. Spacing and layout rules
- **Group by meaning, gap between groups** (an empty column or row). At most 4 groups per screen.
- **Centred patterns** in a 9-wide row (columns 0-8): 1 = 4; 2 = 3,5; 3 = 2,4,6; 4 = 1,3,5,7; 5 = 0,2,4,6,8.
- **Item grids** are 7 wide (columns 1-7): a row with an **odd** count is contiguous and centred (1: 4, 3: 3-5, 5: 2-6, 7: 1-7); a row with an **even** count is split around the centre column with a gap (2: 3,5; 4: 2,3,5,6; 6: 1,2,3,5,6,7).
- **One group per row**, groups longer than 7 split evenly (9 = 5 + 4), sorted by price (cheap first) then vanilla order; helpers (bone meal) last in their group.
- Summary or wallet tile on slot 4; danger tile on slot 8; nothing else in row 0 except tabs.
- A screen shows what most players need now. Unbuilt features are hidden.

## 9. Sounds (vanilla only, one per kind, same everywhere)
click `ui.button.click`; open `block.chest.open` (soft); page `item.book.page_turn`; success `entity.experience_orb.pickup`; error `entity.villager.no` on every refusal; notify `block.note_block.pling`; money `block.amethyst_block.chime`. Keys in `sounds.yml`; players can mute in `/settings`.

## 10. Do and don't
| Don't | Do |
|---|---|
| Title `Island » Upgrades » Production (2/3)` in the plaque | Plaque `UPGRADES`; page on the arrow; the tab shows the area |
| `Click here to open the Island Upgrades menu!` | `» CLICK to open` |
| Name in red because it matters | Red only for danger; use the meaning colour |
| 9 lines of lore | name, one sentence, 3 info rows, one action |
| Grey filler panes, borders, decorations | empty slots stay empty; nothing is drawn for them |
| `Right-click` as the only way | plain click first; Right-click is a muted Java hint |
| Balance in every tooltip | one wallet tile on slot 4; costs as `\| Cost: $12k` |
| A new color or a hex in a message | a token from §1 |
| Text in the font glyphs (lore, chat, holograms) | plain vanilla text; the frame font is for plaque, panel, logo only |
| An icon that is only readable with the pack | base item + coloured name explain it alone |
| A new bottom-row button in a free slot | the fixed slots of §3 |
| Seeds, crops and trees mixed in one grid | one group per row, cheap to dear |

## 11. Checklist: is my new feature in the FORMAT?
1. Reachable from `/menu`, `/help` or its parent screen; Bedrock never needs a command.
2. Screen is one of `P3`, `P4`, `P6`, `List`; content centred by §8; nav slots as §3; summary on 4; danger on 8.
3. Title: a short noun (plaque, at most 16 letters) with a plain crumb for Bedrock; no page numbers, no balance.
4. Every tile follows §4: meaning colour name, one sentence, at most 3 info rows, one `» CLICK` line, at most 6 lines.
5. Icon: a catalogue key with a base item that explains it alone; two tiles on one screen never share a base item.
6. Currency: symbol + currency colour; wallet tile on slot 4 if the screen spends; credits exact.
7. Colours only from §1 tokens; at most 5 accents; every state has a shape; no red name except danger.
8. Text: English, short, no filler, Bedrock-safe symbols, no italics, texts in `messages/<module>.yml`, no hard-coded numbers.
9. Chat and HUD lines: tag or prefix rule of §5, one line each, fix included in errors.
10. Sounds from §9; `error` on every refusal.
11. Plain layer complete without the pack; frame layer only through `Glyphs.of(viewer)` (empty for Bedrock and no-pack viewers).
12. Tests: layout slots pure, line count and width, no duplicate base item, message audit for tokens.
