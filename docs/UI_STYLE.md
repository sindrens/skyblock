# UI Style Guide

The look and feel of every message, item, GUI, sound and HUD element. **Consistency is a feature:** players learn the language once and understand every new system instantly.

All colors, symbols and templates below live in a shared `style.yml` in `common` (never hardcoded). Modules reference them by name: `<c:money>`, `<c:error>`, `<rarity:epic>`.

---

## 1. Color palette

Network: **SkybornMC** (`play.skybornmc.net`). Gamemode: **Phoenix Skyblock**, themed on the phoenix.

**Two identities, one look (decided 2026-10-29 with the new logo):**
- **SkybornMC (network) = frost blue**, taken from the logo (icy silver-blue S, steel-blue spiked plate, white cloud). Used for the network name, website, Discord, MOTD network parts, "Java & Bedrock", store/network info.
- **Phoenix Skyblock (gamemode) = phoenix fire**, everything below. All gameplay UI (GUIs, scoreboard, items, chat) uses the fire palette.

| Network name | Hex | Legacy | Use |
|---|---|---|---|
| `sky_frost` | `#E4EBF0` | white `§f` | Lightest: start of the SKYBORN gradient, highlights |
| `sky_ice` | `#9FC6E8` | aqua `§b` | Main network color |
| `sky_blue` | `#6CA1D6` | blue `§9` | "MC", network links |
| `sky_steel` | `#49649E` | dark_blue `§1` | Separators/frames on network screens (never small text, too dark) |
| `sky_cloud` | `#FBFCFF` | white `§f` | Cloud white |

**SkybornMC gradient:** `<gradient:#E4EBF0:#9FC6E8:#6CA1D6>` (frost → ice → blue). Bedrock shows the first color, so it starts readable.
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

### 1.6b Essentials-style color codes (owner request)
Besides MiniMessage, `&` codes work wherever players or staff type colors: LuckPerms prefixes/suffixes, nicknames, player chat (by permission), broadcasts. Supported: `&0-&9 &a-&f`, `&l &o &n &m &k &r`, hex `&#FF3D2E` or `{#FF3D2E}` (`LegacyColors` in common). Rank prefixes stay classic `&` colors so Bedrock and other plugins read them; our chat/tab/nametags may add the rank's hex `color` meta on top.

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

**Owner (2026-10-30): "clean, short, direct messages with normal words".** Every message file follows the same format
(UX1 rewrote all of them; moderation, depths and islands follow in UX2).

### 3.1 Format
```
<prefix><mark> <c:text>Short sentence with <c:value>values</c:value>. Fix in a few words.
```
- **Prefix:** `<grad:phoenix><bold>PHOENIX</bold></grad> <c:ember>»</c:ember>` → **PHOENIX »** (`style.yml → prefix`). One prefix, no module prefixes.
- **Where the prefix goes:** only on replies to something the player did (a command, a menu click, a request they got) and on direct notices to one player. **Never** on broadcasts (join/quit, AFK, announcements), staff alert lines (`[Staff]`, `[Economy]`, `[Report #]`), list/detail lines under a header, lore, the scoreboard, titles or the action bar.
- **Success:** `<c:success><sym:check></c:success> <c:text>...` → green ✔ and gray text.
- **Error:** `<c:error><sym:cross></c:error> <c:text>...` → red ✘ and gray text, and the fix in a few words ("Try again soon.", "Press Tab to see them all.", "Use /tpcancel.").
- **Warning** (it worked, but look out): `<c:warning><sym:cross></c:warning> <c:text>...`.
- **Info / neutral** (prompts, status, "teleporting in 3s"): just `<c:text>...`, no mark.
- **Values and names** (players, amounts, items, times, commands to type): `<c:value>` white. Currency amounts keep their currency color (`<cur:money>`).
- **Clickable parts:** `<c:action>[Word]</c:action>` (yellow, square brackets, Title case: `[Confirm]`, `[Accept]`, `[Undo]`), always with a hover text.
- **Asides** (cancel hints, timers, logged notes): `<c:muted>(...)`, e.g. `<c:muted>(cancel to stop, 30s)`.
- **Brand red** only in the prefix, titles and frames. Status colors (`success`/`error`) only on the mark, never on whole sentences. On/off states read `<c:value>on</c:value>` / `<c:value>off</c:value>` inside a success line.
- **Words:** plain English, short sentences, no filler ("Please", "Heads up", "right now", "successfully"). Contractions are fine. Say what happened, then what to do.
- **GUI lore** keeps §4/§5 (status lines like `<c:success>✔ Equipped` and `<c:error>✘ Locked` are fully colored there).

### 3.2 Before / after (UX1)
| Before | After |
|---|---|
| `✘ No player named Steve has joined before. Check the spelling (Bedrock players start with a dot).` (all red) | `✘ Steve has never joined. Check the spelling (Bedrock names start with a dot).` (red ✘, gray text, white name) |
| `✘ You already have 5 open reports. Staff will look at them soon.` (all red) | `✘ You have 5 open reports. Staff will check them soon.` |
| `Flying disabled.` (red word "disabled", no mark) | `✔ Flying off.` |
| `Are you sure? This can't be undone. [CONFIRM] or type /x confirm within 10s.` (orange) | `This can't be undone. [Confirm] (or type /x confirm within 10s)` |
| `✔ You paid $5,000 to Steve. Your balance: $12k` (green text) | `✔ Paid $5,000 to Steve. Balance: $12k` (green ✔, gray text, money green) |

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
| Custom tool | Name = tier + type in rarity color (`Iron Pickaxe`), subtitle `Tool · Tier 3`. Level line `Lv 23 ●●●●●●○○○○ 62%` (filled `●` in flame, empty `●` in muted; ▰/▱ only after a Bedrock test), stats, perk slots (`3/5`), skin name if applied, `Blocks mined: 48,210`. Footer `RARE TOOL` |
| Tag item | `<rarity>Tag: [Phoenix]` + preview line, "Right-click to redeem", tradeable note |
| Money note | `<c:money>$50,000 Note`, signed-by line, unique id |
| Keys | `<rarity>Rare Crate Key`, "Use at /crates or a crate at spawn" |
| Locked menu entries | Gray dye or the real item with `<c:error>✘ Locked`, followed by the requirement and progress: `Mining 15 ▸ 9/15` |
| Maxed | `<c:success>✔ Maxed` + glint |

---

## 5. GUIs

### 5.1 Layout standard (6-row chest = 54 slots)
Built by the menu framework (`common/menu`, `frame()`), so every menu looks the same automatically.
```
 R  O  B  B  B  B  B  O  R      R = red pane (corner accent)   O = orange pane (ember glow)
 B  .  .  .  .  .  .  .  B      B = black pane (border)         . = content (28 slots: 10-16, 19-25, 28-34, 37-43)
 B  .  .  .  .  .  .  .  B
 B  .  .  .  .  .  .  .  B
 B  .  .  .  .  .  .  .  B
 ←  O  B  ?  ✘  H  B  O  →      45 Back/Previous · 48 Info · 49 Close · 50 Help · 53 Next
```
- **Why this frame:** full red rows (the old look) were loud and fought with red error items and the content. A dark border makes items pop; red only on the corners with an orange "glow" next to them is the phoenix accent, like embers at the edges. Content slots stay empty (no filler), so the list reads cleanly. All panes have no tooltip at all.
- **Fixed slots everywhere:** 45 = Back on page 1 (only when there is a parent menu) and Previous page on page 2+ (decided 2026-10-29, no right-click needed on Bedrock); 48 = Info (book: what this screen does + opens `/help <topic>`); 49 = Close (barrier); 50 = Help (knowledge book); 53 = Next page.
- **Navigation heads:** Back/Previous/Next are textured player heads (Mojang's official `MHF_ArrowLeft` / `MHF_ArrowRight` by default, or any minecraft-heads.com texture value in `menus.yml → heads.*`); loaded once in the background, with a plain arrow until ready or if the lookup fails. Bedrock shows a player head with the same name.
- **Title format:** `▸ Island » Upgrades (2/5)`: red `▸` marker, crumbs in title gray `#3F3F3F` (readable on the light chest background), separator and page in `#8B8B8B`. Built with `service.title(viewer, crumbs…)`. Max **32 characters** (`menus.yml → title.max-length`): leading crumbs are dropped first, then the last one is shortened with `..`.
- **Page indicator:** in the title (`(2/5)`, only when there is more than one page) and in the lore of Previous/Next ("Go to page 3 of 5").
- **Button lore:** name = color word + white value; lore = description lines (`<c:text>`), an empty line, then the action line `▸ Click to …` in `<c:action>`. No italics anywhere.
- **States:** Loading = clock "⌛ Loading..."; Error = red stained glass "✘ Couldn't load this"; Empty list = cobweb in slot 22 "Nothing here yet" + a hint (per-menu key); Toggle = lime dye "✔ Enabled" / red dye "✘ Disabled" + "▸ Click to switch".
- Smaller GUIs (3-row) for simple choices and confirmations: same frame, inner slots filled with gray panes.

### 5.2 Confirmation dialog (3-row)
```
 R  O  B  B  B  B  B  O  R
 B  ✔  ✔  ✔  ◆  ✘  ✘  ✘  B      10-12 Confirm (11 lime concrete, 10/12 lime panes: one big target)
 R  O  B  B  B  B  B  O  R      13 the item/action being confirmed · 14-16 Cancel (15 red concrete, 14/16 red panes)
```
- Confirm lore: "This will:" + exactly what happens and what it costs + "▸ Click to confirm". Cancel: "Nothing will change."
- One-shot: a confirm can never run twice. Cancel returns to the parent menu.
- Title: `▸ Disband island` (or the default `▸ Are you sure?`).

Required for: anything irreversible, anything above a money threshold, solo→team, disband, prestige, merges, leaving islands, buying with gems.

### 5.2.1 Number picker (6-row)
```
 R  O  B  B  B  B  B  O  R
 B  ·  ·  ·  ·  ·  ·  ·  B      · = gray pane
 B −100 −10 −1  $  +1 +10 +100 B   19-21 red dye (stack size = step), 22 sunflower = value (exact + compact, min/max)
 B  ·  · Min  ✎  Max ·  ·  B     30 bucket = set minimum, 31 name tag = type an amount in chat (1.5k/2M), 32 water bucket = set maximum
 B  ·  ·  ✔ [✔]  ✔  ·  ·  B     39-41 Confirm (40 lime concrete)
 ←  O  B  ?  ✘  H  B  O  R
```

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

### 6.1 Scoreboard (right side, 10 lines with the title)
Owner (2026-10-30): "cleaner, better colors, it looks hard now". Fewer labels, more space, soft colors:
```
 PHOENIX SKYBLOCK          ← phoenix fire gradient, bold (the only red)

  Pouched · Mod            ← name + rank, both in the rank color, muted dot

  Money  $1.2M             ← labels muted gray, money green
  Tokens  ✦ 4,250          ← tokens purple
  Gems  ◆ 120              ← gems aqua

  Island  Lv 34 #12        ← level white, position muted
  play.skybornmc.net       ← address, network ice blue
```
**The Depths** (`scoreboard.depths`, used in the `depths` world; HudService picks the layout named after the world):
```
 THE DEPTHS

  Pouched · Mod

  Zone  Copper Caves       ← <ph:depths_zone>
  Tier  Mine · Tier 2      ← <ph:depths_tier>
  Mining  Lv 12 (48%)      ← <ph:skill_mining_level>, <ph:skill_mining_progress>

  Money  $1.2M
  play.skybornmc.net
```
- Labels `<c:muted>`, values `<c:value>` white or their currency color, two spaces between label and value, one leading space.
- No separator bars, no online count, no objective line (the objective returns with the tutorial/quests as its own layout).
- Max 10 lines with the title (`SidebarLinesTest` checks the bundled layouts). Events and later places (SB) add their own layout.

### 6.2 Tab list
```
 ────────────  SKYBORNMC  ────────────     ← left line frost white (first letter), right line blue (MC)
            Phoenix Skyblock               ← phoenix fire gradient, bold

          12 online · Season 1 · Preseason ← count white, rest muted

        » play.skybornmc.net «             ← SkybornMC gradient, blue arrows

     TPS 20.0  ·  Ping 34 ms               ← staff only: TPS green ≥ 19, orange ≥ 15, red below
```
- **Header lines:** the left line takes the color of the first letter (frost white `sky_frost`) and the right line the color of the last letter (`sky_blue`, the "MC"), so the lines read as the SKYBORN gradient continuing outwards. Chosen over one long gradient across the whole line because it stays clean with strikethrough spaces, and on Bedrock (first color only) both sides still look intentional.
- **Footer:** only the address. The `/menu · /help · /discord · /store` line was removed (owner 2026-10-30).
- **Staff line** (`skyblock.staff.tab`): `TPS` + value colored by `chat.yml → tab.tps-good / tab.tps-ok` (texts `messages/chat.yml → tab.tps.*`), then ping.
- Players sorted by rank, then name. Format: `[title] [rank] name (tag)`.

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

- **Main GUI** with categories: Getting Started, Island, The Depths, Economy, Progression (skills, pillars, upgrades), Gear (tools, Forge, blueprints, sets, enchants), Combat & Events, Rewards (crates, quests, vote), Community (Discord, rules, report), Commands.
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
| SkybornMC logo | **Owner's logo (2026-10-29):** `branding/skybornmc-logo-source.png` (transparent, cropped) + 128/512/1024 PNG. A chunky 3D **icy marble S** (frost white → ice blue, deep navy extrusion) on a scalloped steel-blue plate with silver spikes, white sparkles, sitting on a puffy white cloud. Colors: frost `#E4EBF0`, ice `#9FC6E8`, blue `#6CA1D6`, steel `#49649E`, navy `#192F66`, cloud `#FBFCFF`. Old red concept in `branding/old/` |
| Server icon | `server/server-icon.png`, **64×64 PNG**: the SkybornMC S logo (deployed with the server configs) |
| MOTD | Animated hex gradients from the `motd` module (`motd.yml`): 4 designs (coming soon, the phoenix rises, season live, double XP), rotation, auto-centering, hover info. Once the network has a proxy, the same module/config moves there |
| Resource pack glyph | The phoenix as a font glyph (for chat prefix, scoreboard title, tab). Needs a Geyser mapping so Bedrock shows it too; until then, fall back to `✦` |
| Particles | Brand particles: `flame`, `small_flame`, `lava` drips, `dust` in brand red. Used at spawn, crates, level-ups |
