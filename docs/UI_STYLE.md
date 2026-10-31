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

### 1.1 Core (V2 palette, owner approved 2026-10-30, STYLE2)
The V2 update made every color brighter so it reads on the light chest background and on phones. Format in
`style.yml`: `"<hex> <legacy fallback>"`. Bedrock shows the legacy color.

| Name | Hex | Legacy fallback | Use |
|---|---|---|---|
| `brand` | `#FF2D3F` | red `§c` | **Phoenix Red.** Server name, titles, the prefix gradient start |
| `frame` | `#D1202F` | dark_red `§4` | Frames and borders (the old dark brand red) |
| `brand_bright` | `#FF5A4D` | red `§c` | Gradient highlight only |
| `ember` | `#FF8C1A` | gold `§6` | Secondary accent: separators `»`, small labels |
| `flame` | `#FFD21A` | yellow `§e` | Highlights, filled progress/level dots, "new"/"ready" badges |
| `charcoal` | `#2B2B2B` | black `§0` | Dark backgrounds (resource-pack panels) |
| `parchment` | `#E8DCC0` | white `§f` | Highlighted body text |
| `text` | `#E0E0E0` | gray `§7` | Normal body text, lore labels |
| `muted` | `#A0A0A0` | dark_gray `§8` | Hints, subtitles, "each", asides |
| `locked` | `#5E5E66` | dark_gray `§8` | Empty progress dots, locked or empty slots |
| `value` | `#FFFFFF` | white `§f` | Numbers, names, the important part of a line |
| `action` | `#FFE135` | yellow `§e` | `» CLICK` action lines, clickable chat |
| `header` | `#FF2D3F` | red `§c` | Bold `\| Header` section titles in lore and on the scoreboard |
| `bar` | `#D1202F` | dark_red `§4` | The `\|` bar in front of every section row |
| `buy` | `#4DFF88` | green `§a` | `\| Buy Price:` |
| `sell` | `#FF5A5A` | red `§c` | `\| Sell Price:` |
| `top1` / `top2` / `top3` | `#FFD21A` / `#E6E6E6` / `#FF9A3D` | yellow / white / gold | Leaderboard places #1, #2, #3 (gold, silver, bronze) |

### 1.2 Status
| Name | Hex | Legacy | Use |
|---|---|---|---|
| `success` | `#4DFF6A` | green `§a` | Success messages, "Unlocked", `[+]` rewards |
| `error` | `#FF3B3B` | red `§c` | Errors, "Locked", "Not enough" |
| `warning` | `#FFB020` | gold `§6` | Confirmations, irreversible-action warnings |
| `info` | `#3DC8FF` | aqua `§b` | Tips, neutral notices |
| `enchant` | `#D17AFF` | light_purple `§d` | Custom enchants |

### 1.3 Currencies
| Currency | Hex | Legacy | Symbol | Format |
|---|---|---|---|---|
| Money | `#4DFF88` | green `§a` | `$` | `$1.2M` |
| Crystals (island) | `#33E0FF` | aqua `§b` | `◆` | `◆ 4,250` |
| Gems | `#2EFFC0` | dark_green `§2` | `✦` | `✦ 120` |
| Shards | `#FF6B8A` | red `§c` | `✧` | `✧ 35` |
| Credits (store) | `#FF8C1A` | gold `§6` | none | `120 Credits` |

Crystals replaced tokens (owner decision O8): aqua crystal look. Gems have the emerald color and the `✦` symbol so the two are never confused; the old `<cur:tokens>`/`<sym:tokens>` tags still resolve to crystals. `<sym:sparkle>` (`✦`) is the decorative star for non-currency texts.

### 1.4 Rarity (items, crates, pets, enchants)
| Rarity | Hex | Legacy | Footer text |
|---|---|---|---|
| Common | `#F2F2F2` | white `§f` | `COMMON` |
| Uncommon | `#4DFF6A` | green `§a` | `UNCOMMON` |
| Rare | `#3DA5FF` | blue `§9` | `RARE` |
| Epic | `#C05CFF` | dark_purple `§5` | `EPIC` |
| Legendary | `#FFB414` | gold `§6` | `LEGENDARY` |
| Mythic | `#FF4FD8` | light_purple `§d` | `MYTHIC` (monthly/seasonal only) |
| Special | `#FF2D3F` | red `§c` | `SPECIAL` (event/limited) |

Crates (7): Ember (common), Cinder (uncommon), Blaze (rare), Inferno (epic), Phoenix (legendary), Eclipse (monthly, mythic), Conqueror (KOTH).

### 1.5 Pillars and skills
| Pillar | Hex | Legacy | Icon item |
|---|---|---|---|
| Mining | `#D0D8E4` | gray `§7` | Iron pickaxe |
| Foraging | `#7CFF4D` | green `§a` | Oak log |
| Farming | `#FFE04D` | yellow `§e` | Wheat |
| Combat | `#FF4D4D` | red `§c` | Iron sword |
| Conquest | `#FF9A3D` | gold `§6` | Golden banner pattern / white banner |
| Fishing (skill) | `#3DD8FF` | aqua `§b` | Fishing rod |

### 1.6 Gradients
Gradients only for: the server name, Legendary/Mythic item names, season titles and big announcements. **Never** on normal text. Bedrock shows the first color only, so the first color must be the right one.

**Phoenix fire gradient** (the signature, V2): `<gradient:#FF2D3F:#FF6A1A:#FFC21A>`, red → orange → gold. Used for `PHOENIX` in the scoreboard title, tab header, join title and announcements.

### 1.6b Essentials-style color codes (owner request)
Besides MiniMessage, `&` codes work wherever players or staff type colors: LuckPerms prefixes/suffixes, nicknames, player chat (by permission), broadcasts. Supported: `&0-&9 &a-&f`, `&l &o &n &m &k &r`, hex `&#FF3D2E` or `{#FF3D2E}` (`LegacyColors` in common). Rank prefixes stay classic `&` colors so Bedrock and other plugins read them; our chat/tab/nametags may add the rank's hex `color` meta on top.

### 1.7 Brand vs. errors
Since V2 the brand red and the error red share `§c` on Bedrock, so they are told apart by shape, never by color alone:
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
(UX1 rewrote all of them; UX2 did moderation, depths and islands). Island roles are written as in ISL2: Leader, Admin, Mod, Member, Coop.

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

### 4.1 Lore template (V2, STYLE2; one shared builder: `common/item/LoreBuilder`)
```
Iron Pickaxe                               ← name: rarity color, BOLD, no italics
Tool · Tier 3                              ← subtitle, muted

Mine ore. Levels up as you use it.         ← description, text color, wrapped (max ~32-36 chars)

Lv 23 ●●●●●●○○○○ 62%                       ← custom lines (level bar: flame filled, locked empty)

| Buy Price: $8 each                       ← bold, buy green  (shop/sell menus only, never on inventory items)
| Sell Price: $2 each                      ← bold, sell red
You can afford: 12

| Stats                                    ← bold header color; empty sections are dropped
| Speed: +12%                              ← bar "|" dark red, label gray + colon, value white

| Information                              ← any custom section (tools, notes, books "Details", sets "2 Pieces")
| Ability: Vein Breaker

| Perk Slots 2/3
| Fortune III                              ← enchant color
| Empty slot                               ← locked color

Progress: 23 / 50                          ← quests/achievements
●●●●○○○○○○ 46%

| Rewards
★ Inferno Key (x2) | 12.5%                 ← crate rewards: name in its rarity color, chance in white
[+] $5,000                                 ← quest rewards: bold green [+]

» CLICK to choose an amount               ← bold action color, verb UPPER CASE, always last before the footer

RARE TOOL                                  ← footer: bold rarity color
```
Rules:
- Max **~36 visible characters per line** (mobile/Bedrock readability); descriptions wrap at 32, max 3 lines.
- Exactly one empty line between sections. No section if it's empty.
- **Action lines always last** before the footer: `<bold><c:action>» VERB</c:action></bold> <c:text>what it does`.
- Only Bedrock-safe symbols: `|` is plain ASCII, `★ ● »` are on the safe list.
- All words and formats live in `messages/items.yml → lore.*` (and module message files); the builder only arranges them.
- Menus whose lore comes straight from a messages file write the same formats by hand (`<bold><c:buy>| Buy Price: </c:buy>…`,
  `<bold><c:header>| Information</c:header></bold>`, `<c:bar>| </c:bar><c:text>Label: </c:text><c:value>value`).
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
 ←  a  a  ?  ✘  M  a  <  >      45 Back · 46/47/51 screen actions · 48 ? · 49 Close · 50 Main menu · 52/53 pages
```
- **Why this frame:** full red rows (the old look) were loud and fought with red error items and the content. A dark border makes items pop; red only on the corners with an orange "glow" next to them is the phoenix accent, like embers at the edges. Content slots stay empty (no filler), so the list reads cleanly. All panes have no tooltip at all.
- **Fixed slots everywhere (GUI-1, docs/design/GUI_DESIGN.md §1.1, `MenuLayout`):** 45 = Back on every page (when there is somewhere to go back to); 48 = **?** "What is this?" (book: the screen's short guide in the lore, a click opens its help topic and Back returns here); 49 = Close (barrier); 50 = Main menu (nether star, opens /menu; on /menu itself: Help & guides); 52 = Previous page, 53 = Next page (only when there is one). Free for screen actions: 46, 47, 51. A paged menu that puts its own button on 52 is logged at startup of that menu.
- **4 and 5 rows:** the same bar on the last row (4 rows: 27 Back, 30 ?, 31 Close, 32 Main menu, 34/35 pages). **3 rows:** 18 Back, 22 Close.
- **Back (navigation stack):** Back returns to the menu that opened this one. A menu opened from another menu (also through a command, e.g. /menu → /is, or after a chat prompt) gets it as its Back target automatically; /menu is the bottom of the stack; closing the menu ends the stack (`common/menu/Navigation`).
- **In place:** tabs, page turns and refreshes rebuild the same window and update the title in place (no close/reopen flicker, the cursor stays). `menus.yml → title.update-in-place: false` falls back to reopening when the title changes.
- **Clicks:** left and right click both run a button's action (a Bedrock tap too). Right-click and shift-click only do something else on buttons that mark a Java-only shortcut (`MenuButton.withRight/withShift`); the plain click must still do the main thing.
- **Navigation heads:** Back/Previous/Next are textured player heads (Mojang's official `MHF_ArrowLeft` / `MHF_ArrowRight` by default, or any minecraft-heads.com texture value in `menus.yml → heads.*`); loaded once in the background, with a plain arrow until ready or if the lookup fails. Bedrock shows a player head with the same name.
- **Title format:** `▸ Island » Upgrades (2/5)`: red `▸` marker, crumbs in title gray `#3F3F3F` (readable on the light chest background), separator and page in `#8B8B8B`. Built with `service.title(viewer, crumbs…)`. Rule `Feature » Screen`: at most **2 parts** (`title.max-parts`, the item or player name stays last) and max **32 characters** (`menus.yml → title.max-length`): leading crumbs are dropped first, then the last one is shortened with `..`. An empty title shows the fallback title (`messages/menus.yml → title.fallback`).
- **Page indicator:** in the title (`(2/5)`, only when there is more than one page) and in the lore of Previous/Next ("Go to page 3 of 5").
- **Button lore:** name = color word + white value; lore = description lines (`<c:text>`), an empty line, then the action line `» CLICK to …` (bold action color, verb upper case). No italics anywhere.
- **States (one look each, shared builders in `MenuService`):** Loading = clock "⌛ Loading..." (`loadingItem`, `Menu.setLoading`); Error = red stained glass "✘ Couldn't load this" (`errorItem`, with a retry click when given); Empty list = cobweb in slot 22 (13 on small menus) "Nothing here yet" + one hint (`emptyItem`, `Menu.setEmpty`); Locked = gray dye "✘ Name" + how to unlock, a click shows it in the action bar (`locked`); Tab = selected glints with "✔ You are here", others "» CLICK to view" (`tab`); Toggle = lime dye "✔ Enabled" / red dye "✘ Disabled" + "▸ Click to switch".
- Smaller GUIs (3-row) for simple choices and confirmations: same frame, inner slots filled with gray panes, Back 18 and Close 22.

### 5.2 Confirmation dialog (3-row)
```
 R  O  B  B  B  B  B  O  R
 B  ✔  ✔  ✔  ◆  ✘  ✘  ✘  B      10-12 Confirm (11 lime concrete, 10/12 lime panes: one big target)
 ←  O  B  B  ✘  B  B  O  R      13 what is confirmed + what you lose · 14-16 Cancel (15 red concrete) · 18 Back · 22 Close
```
- The middle item (13) carries the details: its name is the key fact, the lore says "This will:" + exactly what happens and what it costs (GUI-1: no longer on the green button, so Bedrock players read it before tapping). Confirm: "Read the middle item first". Cancel: "Nothing will change."
- One-shot: a confirm can never run twice. Cancel returns to the parent menu.
- **Two-step** (`ConfirmMenu.twoStep()`) for things that can't be undone (disband, delete profile, permanent ban): the first Confirm shows a second check in the same window with the sides swapped (Cancel left, "Yes, do it" right) and "Last check: this can't be undone"; the title shows `(1/2)` / `(2/2)`.
- Title: `▸ Disband island` (or the default `▸ Are you sure?`).

Required for: anything irreversible, anything above a money threshold, solo→team, disband, prestige, merges, leaving islands, buying with gems.

### 5.2.0 Bedrock popups for chat offers (3-row, GUI-5)
Bedrock can't click chat. Anything answered with a chat click (TPA, island invites, trades, /discord /store /vote /website /rules, /help command picks, tutorial "I'm stuck", staff alerts) goes through **one API**: `core.menus().offers().offer(player, Offer...)`. Everyone gets the chat line; Bedrock players also get a popup:
```
 R  O  B  B  i  B  B  O  R      4 = what this is (name + lore from <key>.name/.lore); with no buttons it sits on 13
 B  .  ✔  .  ✘  .  ✘  .  B      buttons: 1 → 13, 2 → 11 + 15, 3 → 11 + 13 + 15 (max 3); a button runs its command
 ←  O  B  B  ✘  B  B  O  R      18 Back · 22 Close
```
- Never opens over another window or a chat prompt: it waits and opens when that closes (one at a time, the same id replaces an older one, max `menus.yml → bedrock-popups.queue-max`, dropped after `max-age-seconds`).
- When the request expires or is answered meanwhile, the buttons give way to "✘ Expired"; `<left>` counts down in every text.
- Links: the address is the item name; a click sends it once in plain chat (Bedrock can't open links).
- Shared button texts: `messages/menus.yml → offer.button.*`. Turn popups off: `menus.yml → bedrock-popups.enabled: false`.

### 5.2.1 Number picker (6-row)
```
 R  O  B  B  B  B  B  O  R
 B  ·  ·  ·  ·  ·  ·  ·  B      · = gray pane
 B −100 −10 −1  $  +1 +10 +100 B   19-21 red dye (stack size = step), 22 sunflower = value (exact + compact, min/max)
 B  ·  · Min  ✎  Max ·  ·  B     30 bucket = set minimum, 31 name tag = type an amount in chat (1.5k/2M), 32 water bucket = set maximum
 B  ·  ·  ✔ [✔]  ✔  ·  ·  B     39-41 Confirm (40 lime concrete)
 ←  O  B  ?  ✘  M  B  O  R
```

### 5.3 Help on every screen
- **? item (slot 48, "What is this?"):** what this screen does, in 2–4 lines, plus "Click for the full guide". Opens `/help <topic>` for that screen; Back in the help returns to the screen.
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

### 6.1 Scoreboard (right side, max 9 lines + title)
V2 (STYLE2): the same `| Header` sections as lore.
```
 PHOENIX SKYBLOCK          ← phoenix gradient (red → orange → gold), bold
 | Profile                 ← bold header color
 | Pouched · Mod           ← dark red bar, name + rank in the rank color
 | Money: $1.2M            ← label gray + colon, money green
 | Crystals: ◆ 4,250
 | Gems: ✦ 120

 | Island
 | Level: Lv 34 #12
 play.skybornmc.net        ← footer, network ice blue
```
**Per place (SB, owner Q6 "a different scoreboard per place, only relevant lines").** `hud.yml → places` picks the layout from where the player is; the layouts (title + lines) are in `messages/hud.yml → scoreboard.<layout>`:

| Place | Layout | Shows |
|---|---|---|
| Own island (and the island world outside any island) | `island` | name · rank, Island Lv, **Next: newest open note** (only when there is one and the "Next note on scoreboard" setting is on), money, crystals |
| Someone else's island (coop counts as visiting) | `island-visit` | name · rank, **Visiting <island name>**, money, crystals |
| The Depths, Mine wing and causeways | `depths` | zone, tier, Mining Lv (%), money |
| The Gate (Depths hub) | `depths-gate` | zone, Mining / Foraging / Combat Lv (the skills that unlock the wings) |
| Forest / Combat wing | `depths-forest` / `depths-combat` | zone, tier, Foraging / Combat Lv (%), money |
| Spawn | `spawn` | same as default (change freely) |
| Warzone (placeholder until it exists) | `warzone` | title WARZONE, Combat Lv, money |
| Anywhere else | world-named layout, else `default` | |

```
 PHOENIX SKYBLOCK                THE DEPTHS

  Pouched · Mod                   Pouched · Mod

  Island  Lv 34 #12               Zone  Copper Caves
  Next  Build the farm            Tier  Mine · Tier 2
                                  Mining  Lv 12 (48%)
  Money  $1.2M
  Crystals  ◆ 4,250               Money  $1.2M
  play.skybornmc.net              play.skybornmc.net
```
- A missing layout falls back: Depths variant → `depths`, `island-visit` → `island`, any place → world-named layout → `default`.
- The board switches on the tick after a teleport or respawn and right away on a world change; walking onto another island or into another Depths zone switches on the next refresh (1 s). Only changed lines are sent, so switching never flickers.
- Conditional lines: `"if:<placeholder>|<line>"` only shows while the placeholder has a value (not empty, not `-`); hidden lines take no space.
- Built-in `<island_here>`: name of the island you stand on (visit board).
- Section headers `<bold><c:header>| Name`, rows `<c:bar>|</c:bar> <c:text>Label:</c:text> value`, values white or their currency color, one leading space. Layouts that would pass 9 lines drop the blank line between sections.
- No separator bars, no online count, no objective line (the objective returns with the tutorial/quests as its own layout).
- Max 10 lines with the title, conditional lines included (`SidebarLinesTest` checks every bundled layout). Events add their own layout through `HudService#addLayout` (wins over places).

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

**Teleport warmups (F4)** look the same everywhere (`/spawn`, `/tpa`, `/is home`, `/is visit`, `/is warp`, `/dungeon`), because one core service shows them (`messages/core.yml → teleport.*`, settings `teleport.yml`):
- Start (chat, with prefix): `Teleporting to <destination> in 3s. Don't move.`
- Countdown (action bar, once per second + `click` sound): `⌛ Teleporting to <destination> in 3...` (ember hourglass, value-colored name and seconds).
- Arrival: `teleport` sound + the feature's own arrival text (e.g. `Welcome to spawn`, the Depths tier on the action bar).
- Cancel (chat, error format + `error` sound), one text per reason: `Teleport cancelled: you moved.` / `you got hit.` / `you're in combat.` / `you were frozen by staff.` / `you changed worlds.`, plus `[Try again]` when the feature offers a command (`/dungeon`).
- Refusals use the error format with the fix: cooldown (`Slow down. Try again in 4s.`), combat, frozen, no safe spot, failed teleport.
- Modules only pass a destination name (plain, or a styled one from their messages file such as `dungeon.destination`); they never write their own warmup texts.

### 6.4 Boss bar
Event and timer countdowns (KOTH, envoys, world boss HP, boosters). Color = event color.

### 6.4b Holograms (V2)
Shared formats in `messages/items.yml → hologram.*` for every module that spawns one (crates, leaderboards):
title bold in its rarity color, `Right-Click: Open` / `Left-Click: View Rewards` in bold action color, leaderboard places
`#1` / `#2` / `#3` bold in `top1` / `top2` / `top3`, other places muted. `/baltop` uses the same place colors.

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
