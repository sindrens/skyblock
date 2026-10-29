# UI Style Guide

The look and feel of every message, item, GUI, sound and HUD element. **Consistency is a feature:** players learn the language once and understand every new system instantly.

All colors, symbols and templates below live in a shared `style.yml` in `common` (never hardcoded). Modules reference them by name: `<c:money>`, `<c:error>`, `<rarity:epic>`.

---

## 1. Color palette

Network: **SkybornMC** (`play.skybornmc.net`). Gamemode: **Phoenix Skyblock**, themed on the phoenix. This guide covers Phoenix Skyblock; the network brand (hub, website) is defined separately.
Theme: **phoenix fire**, mainly **red**, balanced with ember orange, flame gold, deep charcoal and parchment, plus clear status colors. Red carries the identity (headers, borders, scoreboard, details); the other colors keep it readable and never monotone.

**Balance rule:** in any GUI, message or HUD, red is the *frame* (title, borders, accents), not the body. Body text stays neutral (gray/white), values stay white, and actions stay flame gold. Roughly 30% red, 15% ember/gold, 55% neutral.

> **Bedrock:** Geyser only shows the 16 legacy colors and rounds hex values. Every color has a **legacy fallback** that must still read correctly. Test on Bedrock.

### 1.1 Core
| Name | Hex | Legacy fallback | Use |
|---|---|---|---|
| `brand` | `#C21F2B` | dark_red `§4` | **Phoenix Red.** Server name, GUI titles, headers, scoreboard title, borders |
| `brand_bright` | `#FF3D2E` | red `§c` | Gradient highlight only (never alone, so it can't be confused with errors) |
| `ember` | `#FF8A1F` | gold `§6` | Secondary accent: section headers, separators `»`, scoreboard labels |
| `flame` | `#FFD23F` | yellow `§e` | Highlights, "new"/"ready" badges |
| `charcoal` | `#2B2B2B` | black `§0` | Dark backgrounds (GUI filler, resource-pack panels) |
| `parchment` | `#E8DCC0` | white `§f` | Highlighted body text |
| `text` | `#C9C9C9` | gray `§7` | Normal body text, lore descriptions |
| `muted` | `#7A7A7A` | dark_gray `§8` | Hints, subtitles, separators |
| `value` | `#FFFFFF` | white `§f` | Numbers, names, the important part of a line |
| `action` | `#FFD23F` | yellow `§e` | "Click to …" lines, clickable chat (same as `flame`) |

### 1.2 Status
| Name | Hex | Legacy | Use |
|---|---|---|---|
| `success` | `#6BDB7A` | green `§a` | Success messages, "Unlocked", "Ready" |
| `error` | `#FF5C5C` | red `§c` | Errors, "Locked", "Not enough" |
| `warning` | `#FFB347` | gold `§6` | Confirmations, irreversible-action warnings |
| `info` | `#63B8FF` | aqua `§b` | Tips, neutral notices |

### 1.3 Currencies
| Currency | Hex | Legacy | Symbol | Format |
|---|---|---|---|---|
| Money | `#5BE37D` | green `§a` | `$` | `$1.2M` |
| Tokens | `#C77DFF` | light_purple `§d` | `✦` | `✦ 4,250` |
| Gems | `#4FD1FF` | aqua `§b` | `◆` | `◆ 120` |
| Shards | `#FF6B6B` | red `§c` | `✧` | `✧ 35` |
| Credits (store) | `#FF8A1F` | gold `§6` | none | `120 Credits` |

### 1.4 Rarity (items, crates, pets, enchants)
| Rarity | Hex | Legacy | Footer text |
|---|---|---|---|
| Common | `#D9D9D9` | white `§f` | `COMMON` |
| Uncommon | `#6BDB7A` | green `§a` | `UNCOMMON` |
| Rare | `#4F9DFF` | blue `§9` | `RARE` |
| Epic | `#B266FF` | dark_purple `§5` | `EPIC` |
| Legendary | `#FFAA00` | gold `§6` | `LEGENDARY` |
| Mythic | `#FF55D4` | light_purple `§d` | `MYTHIC` (monthly/seasonal only) |
| Special | `#FF4040` | red `§c` | `SPECIAL` (event/limited) |

Crates use the same colors: Common, Uncommon, Rare, Epic and Legendary crates, and the Monthly crate = Mythic.

### 1.5 Pillars and skills
| Pillar | Hex | Legacy | Icon item |
|---|---|---|---|
| Mining | `#B7C0CC` | gray `§7` | Iron pickaxe |
| Foraging | `#8BCB5A` | green `§a` | Oak log |
| Farming | `#F0D264` | yellow `§e` | Wheat |
| Combat | `#E5534B` | red `§c` | Iron sword |
| Conquest | `#F0883E` | gold `§6` | Golden banner pattern / white banner |
| Fishing (skill) | `#5AC8E6` | aqua `§b` | Fishing rod |

### 1.6 Gradients
Gradients only for: the server name, Legendary/Mythic item names, season titles and big announcements. **Never** on normal text. Bedrock shows the first color only, so the first color must be the right one.

**Phoenix fire gradient** (the signature): `<gradient:#C21F2B:#FF3D2E:#FF8A1F>`, dark red → bright red → ember. Used for `PHOENIX` in the scoreboard title, tab header, join title and announcements.

### 1.7 Brand vs. errors
Brand red (`§4` on Bedrock) and error red (`§c`) must never be confused:
- Errors always start with `✘` and are sentences; brand red is only used for names, titles and frames.
- Brand red is never used for body text.

---

## 2. Text rules

- All text is **MiniMessage**. Legacy `&` codes are not allowed in configs.
- **Item names and lore: no italics** (always set `italic=false`).
- **No "small caps" Unicode fonts:** unreadable for young players, and broken on Bedrock.
- Sentence case for messages ("Island upgraded!"). Title Case for item and GUI names ("Island Upgrades").
- Numbers: compact (`1.2k`, `3.4M`) in lore and HUD; full with separators (`1,250,000`) in confirmations and transactions.
- Time: `2h 15m`, `45s`, `3d 4h`. Never raw seconds.
- Roman numerals for tiers and enchant levels: `Fortune III`, `Tier IV`.

### 2.1 Bedrock-safe symbols (only these, verify the list on a Bedrock client before launch)
`✦ ✧ ◆ ● ▸ ▶ ★ ✔ ✘ ⚔ ⛏ ❤ ⌛ » « │ ·`
Anything else must be tested on Bedrock first and added here.

---

## 3. Chat messages

### 3.1 Format
```
<prefix> <message>
```
- **Prefix:** `<gradient:#C21F2B:#FF3D2E>Phoenix</gradient> <c:ember>»</c>` → **Phoenix »**. Configurable in `style.yml`.
- Module-specific prefixes are *not* used. One prefix keeps chat calm.
- The important part of a sentence is in `value` color, and currency amounts use their currency color.

### 3.2 Templates
| Type | Example |
|---|---|
| Success | `» Upgraded **Island Size** to **III**.` (`success` verb, `value` names) |
| Error with fix | `» You need **$12.5k** more. Sell items with /sell.` (`/sell` clickable, `action` color) |
| Info | `» KOTH starts in **5m** at the Warzone. [Teleport]` |
| Warning/confirm | `» Inviting a player makes your island a **Team Island** forever. [Confirm] [Cancel]` |

Rules:
- **Every error says how to fix it** and links to the fix when possible.
- **Never spam:** repeated identical messages within 2 s are merged ("x3").
- Clickable components always have a hover text explaining the click.

### 3.3 Announcements (server-wide)
Blank line, a centered header with the brand gradient, 1–2 lines, a clickable action, blank line. Max one per 30 s from the auto-announcer.

---

## 4. Items

### 4.1 Lore template (every custom item)
```
<rarity>Item Name                          ← name, rarity color, no italics
<muted>Category · Tier III                 ← subtitle line

<text>Short description of what it        ← max 2 lines, ~32 chars/line
<text>does, in plain words.

<muted>Stats                               ← section header (only if needed)
 <pillar>▸ <text>Mining Speed <value>+12%
 <pillar>▸ <text>Token Bonus <value>+5%

<muted>Enchants
 <c:enchant>Fortune III                    ← custom enchant color #C77DFF
 <c:enchant>Vein Miner I

<action>▶ Right-click <text>to open upgrades
<action>▶ Shift + Right-click <text>to view stats

<rarity><bold>RARE TOOL                    ← footer: RARITY + TYPE
```
Rules:
- Max **~36 characters per line** (mobile/Bedrock readability).
- Empty line between sections. No section if it's empty.
- **Action lines always last** before the footer, always `▶` + `action` color.
- Hide vanilla attributes, enchant lists and potion text (`HIDE_*` flags/tooltip display). Show our own lines instead.
- Custom enchanted items get the enchant **glint**.
- Lore is **generated from data** (stats, enchants, level) on every change. Never edited manually.

### 4.2 Item types and footers
`TOOL`, `ARMOR`, `SET PIECE`, `COLLECTOR`, `CRATE KEY`, `PET EGG`, `PET`, `CONSUMABLE`, `MATERIAL`, `NOTE`, `COSMETIC`, `MENU` (GUI-only items have no footer).

### 4.3 Special items
| Item | Rule |
|---|---|
| Omnitool | Name shows the mode: `Omnitool · Pickaxe`. Level bar in lore: `Level 4 ▰▰▰▰▱▱▱▱▱▱`. Soulbound line: `<muted>Soulbound: cannot be dropped or sold` |
| Money note | `<c:money>$50,000 Note`, signed-by line, unique id |
| Keys | `<rarity>Rare Crate Key`, "Use at /crates or a crate at spawn" |
| Locked menu entries | Gray dye or the real item with `<c:error>✘ Locked`, followed by the requirement and progress: `Mining 15 ▸ 9/15` |
| Maxed | `<c:success>✔ Maxed` + glint |

---

## 5. GUIs

### 5.1 Layout standard (6-row chest = 54 slots)
```
Row 1:  [ border / header items ]
Rows 2–5: content (28 slots inside a 1-slot border, or full rows for lists)
Row 6:  45 Back · 46 – · 47 – · 48 Info (?) · 49 Close · 50 Help · 51 – · 52 – · 53 Next page
```
- **Fixed slots everywhere:** Back = 45 (arrow), Info = 48 (book), Close = 49 (barrier), Help = 50 (knowledge book), Previous page = 45 when paging (Back moves into the info item), Next page = 53 (arrow).
- **Frame:** top row and bottom row (except the button slots) are `red_stained_glass_pane`; the side columns are `black_stained_glass_pane`. All filler panes have an empty name (` `). Pillar menus may swap the top row for the pillar's color pane.
- **Title format:** dark red is unreadable as a chest title on dark backgrounds, so chest titles use default dark gray text with a red `▸` marker: `<c:brand>▸</c> Island » Upgrades`.
- Titles stay short enough for Bedrock (max ~24 chars).
- Smaller GUIs (3-row) for simple choices and confirmations.

### 5.2 Confirmation dialog (3-row)
```
11 = Confirm (lime concrete, "✔ Confirm", lore = exactly what happens + cost)
13 = The item/action being confirmed
15 = Cancel (red concrete, "✘ Cancel")
```
Required for: anything irreversible, anything above a money threshold, solo→team, disband, prestige, merges, leaving islands, buying with gems.

### 5.3 Help on every screen
- **Info item (slot 48):** what this screen does, in 2–4 lines, plus "Click for full help". Opens `/help <topic>` for that screen.
- Every clickable item's lore ends with the action lines (§4.1).
- First time a player opens a feature GUI: a one-time tip in chat with a link to its help topic (togglable in `/settings`).

### 5.4 Behavior
- Clicks must respond **instantly** (sound + visual). Async data shows a "Loading…" item, never a frozen GUI.
- No item can be taken out of a menu GUI (all click types blocked, including number keys, drag, shift, double-click and offhand swap).
- GUIs update live where values change (timers, progress).
- Shift-click must never be *required* (Bedrock).
- Close the GUI on teleport or on long actions.

### 5.5 Feature icon map (same icon everywhere for the same feature)
| Feature | Icon |
|---|---|
| Island | Grass block |
| Island upgrades | Anvil |
| Island stats / top | Gold ingot / golden helmet |
| The Depths `/dungeon` | Iron pickaxe → shows mode icons inside |
| Shop | Emerald |
| Sell | Gold nugget |
| Auction house | Gold block |
| Bank | Barrel (personal) / Ender chest (island) |
| Crates | Tripwire hook |
| Quests | Writable book |
| Skills | Experience bottle |
| Sets | Leather chestplate (dyed Phoenix Red) |
| Enchanter | Enchanting table |
| Pets | Bone / spawn egg |
| Events | Clock |
| Black Market | Wither rose |
| Settings | Comparator |
| Help | Knowledge book |
| Warps | Ender pearl |
| Collection log | Bookshelf |
| Titles / cosmetics | Name tag / armor stand |

---

## 6. HUD

### 6.1 Scoreboard (right side, max 12 lines)
```
 ✦ PHOENIX ✦           ← phoenix fire gradient, bold
                       
 ▸ Next: Mine 50 Coal  ← the current objective (tutorial/quest), always line 3
                       
 $ 1.2M                ← money
 ✦ 4,250               ← tokens
 ◆ 120                 ← gems
                       
 Island Lv 34 (#12)    ← level + rank
 Fuel ⌛ 6h 20m          
                       
 play.skybornmc.net    ← address, ember
```
Labels (`Island Lv`, `Fuel`) in `ember`, values in `value` white, currency symbols in their currency color, blank-line separators. Red appears only in the title and as `▸` markers.
Contextual: in The Depths, the island lines are replaced by tier + satchel `Satchel 412/640`. During events: the event timer.

### 6.2 Tab list
Header: `SKYBORNMC` (network) above `Phoenix Skyblock` in the fire gradient + online count + season (e.g. `Season 1 · Day 23`), with a thin red line of `▬` above and below. Footer: `/menu · /help · /discord · /store`. Players sorted by rank, then name. Format: `[title] [rank] name`.

### 6.3 Action bar
Skill XP gains (`+12 Mining XP (68%)`), tool mode changes, satchel full warnings. Max one message per 1.5 s.

### 6.4 Boss bar
Event and timer countdowns (KOTH, envoys, world boss HP, boosters). Color = event color.

### 6.5 Titles (screen)
Only big moments: level up (island/skill/tool), unlocks, season winners, event start. Keep them short and with a sound.

---

## 7. Sounds

| Event | Sound | Pitch |
|---|---|---|
| GUI click | `ui.button.click` | 1.0 |
| GUI open | `block.chest.open` (subtle, volume 0.4) | 1.2 |
| Success / purchase | `entity.experience_orb.pickup` | 1.2 |
| Error / denied | `entity.villager.no` | 1.0 |
| Level up | `entity.player.levelup` | 1.0 |
| Unlock / rare drop | `ui.toast.challenge_complete` | 1.0 |
| Money received | `block.amethyst_block.chime` | 1.4 |
| Warning / confirm | `block.note_block.pling` | 0.8 |
| Event start | `item.goat_horn.sound.0` (or `event.raid.horn`) | 1.0 |
| Brand moments (first join, season start, prestige) | `entity.blaze.shoot` + `item.firecharge.use` (the phoenix "rebirth") | 0.8 |
| Crate roll tick / win | `block.note_block.hat` / `entity.firework_rocket.twinkle` | 1.0 |

All sounds are configurable and can be muted in `/settings`.

---

## 8. Help system (`/help`)

- **Main GUI** with categories: Getting Started, Island, The Depths, Economy, Progression (skills, pillars, upgrades), Gear (Omnitool, sets, enchants), Combat & Events, Rewards (crates, quests, vote), Community (Discord, rules, report), Commands.
- Each topic: 3–6 short pages (written book or GUI pages), with a "Take me there" button and related topics.
- `/help <topic>` and tab completion for topics.
- Help texts live in `help.yml`, editable in-game.
- **FAQ** topic with the 15 most common questions. Staff can add answers from `/admin`.
- `/discord` + `/report` + "Ask staff" (creates a ticket) visible in the Help main GUI.

---

## 9. Brand assets

| Asset | Spec |
|---|---|
| Names | Network: **SkybornMC** (one word, capital S and MC). Gamemode: **Phoenix Skyblock**. Short form in chat: **Phoenix** |
| Domain | `play.skybornmc.net` (Java + Bedrock), website `skybornmc.net`, store `store.skybornmc.net` |
| Logo | A phoenix (owner's artwork). Needed: original file (PNG/SVG, transparent) |
| SkybornMC logo | `branding/skybornmc-logo.svg` (+ 128/512/1024 PNG): a chunky 3D **S** in sky white→cyan→blue with a navy extrusion and golden cracks, white wings with gold tips, a night-sky disc in a cloud ring, gold/white sparkles. Network colors: sky white `#FFFFFF`, cyan `#58C2FF`, blue `#2B78F0`, navy `#061638`, gold `#FFD23F` |
| Server icon | `server/server-icon.png`, **64×64 PNG**: the SkybornMC S logo (deployed with the server configs) |
| MOTD | Animated hex gradients from the `motd` module (`motd.yml`): 4 designs (coming soon, the phoenix rises, season live, double XP), rotation, auto-centering, hover info. Once the network has a proxy, the same module/config moves there |
| Resource pack glyph | The phoenix as a font glyph (for chat prefix, scoreboard title, tab). Needs a Geyser mapping so Bedrock shows it too; until then, fall back to `✦` |
| Particles | Brand particles: `flame`, `small_flame`, `lava` drips, `dust` in brand red. Used at spawn, crates, level-ups |
