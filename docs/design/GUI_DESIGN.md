# Phoenix GUI design: one system for every menu

**Status:** draft v1 for the owner (31 Oct 2026). **Nothing here is built yet.** The owner reviews it first; then the developer implements it in the order of §9.

**Inputs:**
- **Sketches:** the "Phoenix GUI Sketches" artifact (v3). It has the clean /menu, the tabbed shop, the standard bottom bar, and every menu in the code drawn with notes.
- **Sources:** all 189 menus in `main` at update105 (f09475c), read class by class (`menus.json`, same folder). Also UI_STYLE, the resource pack plan and art brief, OWNER_PRINCIPLES (#0 "fun first, less is more"), and the owner's UX3 feedback.
- **Related files:**
  - `SHOP_DESIGN.md`: catalog, page layout and buy flow.
  - `UX_REVIEW.md`: feedback on everything else.
  - `MENU_FIXES.md`: the fix list for each of the 189 menus.

---

## 0. Principles (why the rules below exist)
1. **Less is more.** A screen shows what most players need now, grouped, with space between groups. Unbuilt features are hidden, not grey.
2. **One plain click does it.** Every action works with a normal click: left or right on Java, a tap on Bedrock. Right-click and shift-click are optional Java shortcuts, never required. Lore always says **CLICK**.
3. **You can never get lost.** Every screen has Back (except /menu itself), Close, and Main menu, always in the same slots.
4. **Same place every time.** Muscle memory beats any colour.
5. **Colour = meaning.** These meanings are the same on every screen:
   - Red: frame and danger.
   - Ember: timers and events.
   - Gold: ready and reward.
   - Green: money and success.
   - Steel/sky: the Depths and info.
   - Violet: progress.
   - Blue: social.
6. **Readable on a phone.** No information only in hover text, no chat link as the only way, no dragging needed. The key fact goes in the item name or on the middle item.
7. **Vanilla stays vanilla.** Any Minecraft item (blocks, ores, crops, mob drops, vanilla tools) keeps Minecraft's texture. Only menu buttons and our own items (tiered tools, keys, notes, set pieces, spawners, books) get pack art.

---

## 1. Layout standard

### 1.1 Six rows (54 slots)
```
 0  1  2  3 [4] 5  6  7  8     row 0: tabs (if the screen has tabs) or frame; 4 = summary (head / balance / item)
 9 [10 11 12 13 14 15 16] 17   rows 1-4: content, 28 slots: 10-16, 19-25, 28-34, 37-43
18 [19 ............... 25] 26
27 [28 ............... 34] 35
36 [37 ............... 43] 44
45 46 47 48 49 50 51 52 53     row 5: the bottom bar
```
| Slot | Always | Notes |
|---|---|---|
| 45 | **Back** (to the screen you came from) | Never replaced by "Previous page" (today's PagedMenu does this, so Back is lost on page 2+) |
| 46, 47 | Screen actions | e.g. shop: balance (46), Undo last buy (47) |
| 48 | **?** "What is this?" | 3-line guide to this screen; a click opens its help topic |
| 49 | **Close** | |
| 50 | **Main menu** | Opens /menu. On /menu itself: Help & guides |
| 51 | Screen action | e.g. Sell |
| 52 | **Previous page** | Only when there is one |
| 53 | **Next page** | Only when there is one |

### 1.2 Three rows (27 slots)
```
 0  1  2  3 [4] 5  6  7  8     4 = summary (optional)
 9 [10 11 12 13 14 15 16] 17   content / choices (confirm: 10-12 yes, 13 details, 14-16 no)
[18] 19 20 21 [22] 23 24 25 26  18 Back · 22 Close
```
Today small menus put Back at 22, 10 or nowhere, and many have no Close (dead ends). One rule fixes about 25 menus.

### 1.3 Four rows
Same as six rows, with the bottom bar on row 3: 27 Back, 30 ?, 31 Close, 32 Main menu.

### 1.4 Titles
- **Rule:** `Feature » Screen`, with at most 2 parts and the item or player name last. Examples: `Island » Members`, `Shop » Torch`, `Enchanter » Upgrade`.
- **Confirm titles** are a verb and its object: `Disband island?`, `Delete Profile 2?`.
- Sub-screens never reuse their parent's title (today `enchants.tool-pick` and `enchants.slots` share one).

### 1.5 Tabs
- Tabs sit in row 0.
- Normal screens have 3–5 tabs, centred on slot 4. The shop is the exception, with 9 tabs across the whole row.
- **Selected tab:** glint and "✔ You are here" (the background is static, so the selected look lives on the item; see RESOURCE_PACK_GUI.md §2).
- A tab click **refreshes in place** (same window, no cursor jump), and the title stays the same.

---

## 2. Buttons and icons

### 2.1 Standard buttons (custom pack icons, same everywhere)
| Button | Icon idea | Slot |
|---|---|---|
| Back | Ember arrow left | 45 / 18 |
| Previous / Next page | Small ember arrows | 52 / 53 |
| ? What is this | Blue round "?" | 48 |
| Close | Red ✘ on a dark round plate | 49 / 22 |
| Main menu | Ember plate with 4 gold squares | 50 |
| Confirm / Cancel | Green ✔ plate / red ✘ plate, side panes plain | 11 and 15 (10, 12 / 14, 16) |
| Toggle on / off | Green switch right / grey switch left | anywhere |
| Locked | Grey padlock | anywhere |
| Ready | The feature's own icon in a **gold well** + count | anywhere |
| Undo | Gold round plate with a return arrow, count = seconds left | 47 |
| Buy 1 / 16 / 64 / Fill / Type amount | Green plates with the number, a "fill" arrow, a name tag | buy page |
| Sell / Add my inventory | Coin with a down arrow / coin stack | sell chest |

### 2.2 Feature icons
- One icon per feature, the same everywhere (UI_STYLE §5.5). They are custom art: Island, Depths, Shop, Sell, Bank, AH, Forge, Skills, Enchanter, Wardrobe, Rewards, Top, Travel, Profiles, Titles, Achievements, Settings, Store, Rules.
- **Tab icons** (shop categories) are custom too; they are buttons, not items.

### 2.3 Vanilla items
- Shown with Minecraft's own texture: shop goods, sell-chest contents, satchel contents, AH listings of vanilla items, rewards that are vanilla items.
- The sketches use grey stand-in tiles with a 2-letter code, because Mojang's textures can't be copied into a sketch.

---

## 3. States (one look each, everywhere)
| State | Look | Click |
|---|---|---|
| **Ready** (reward, invite, task done) | Ready look on the item (Rewards: `rewards_ready` model, open chest + sparkles; others: glint), count = how many. The hub's Rewards well has a fixed gold rim. | Does the thing (claims, opens) |
| **Selected** (tab, filter, current choice) | Glint, "✔ You are here" / "✔ Showing" | Nothing |
| **Locked** | Grey padlock, `✘ Name`, then how to unlock: `Mining 15 ▸ 9/15` | Action-bar message + error sound |
| **Empty list** | Cobweb in slot 22: "Nothing here yet" + one hint | Nothing |
| **Loading** | Clock "⌛ Loading..." | Nothing |
| **Error** | Red pane "✘ Couldn't load this. Try again." | Retries |
| **Danger** (leave, delete, disband) | Red well, never next to a busy button | Confirm screen |
| **Coming soon** | **Hidden** on the live server (test server: inside categories only) | — |

---

## 4. Confirm rules
- **Confirm only when something is destructive or costly**, so confirms stay meaningful. No confirm for normal buys (answer 8); undo covers those.
- **What you lose goes on the middle item (13)**, in 2–3 lines. Today it sits in the green button's lore, which Bedrock players can't read before tapping.
- **Two-step for things you can't undo:** disband, delete profile, permanent ban, end of season reset. The second screen swaps the button sides.

**Missing confirms found in the code:**
- `perks.trash`: destroys on close and has no buttons. It needs "Destroy N items?".
- `perks.freerank`: permanent pick.
- `boosters.boosters`: uses a booster.
- `depths.satchel` and `depths.satchel-item`: Upgrade (up to $6.4M), Sell all.
- `stacking.spawner`: "Take all" removes the block.
- `moderation.punish`: permanent ban in one click.
- `moderation.lockdown`: start and end.
- `moderation.confiscate`.
- `moderation.bulk-result`: Undo all.
- `staff.outcome`: needs a warning that Invalid counts as a false report.

---

## 5. Clicks, wording and Bedrock
- **Lore action line:** `» CLICK to <verb>` (open, buy, sell, claim, go, switch, view). Never "Right-click", "Shift-click" or "tap".
  - The Java-only shortcuts (shift-click buys a stack) are mentioned once, in the ? guide.
- **Held items:** `» USE to …`. **NPCs:** "Click to talk". The owner reports right-click doesn't work today (UX3), so a plain click must work.
- **Forward-only cyclers become a small pick menu:**
  - Today: AH Category/Sort, wardrobe Filter, logs Time range, island Time/Weather, island chat colours, warp category.
  - Change: the button opens a 3-row list and one click picks.
- **Typing is the last option.** Always offer buttons (amount plates, presets); "Type an amount" is the extra.
- **Chat links are not enough on Bedrock:** TPA Accept/Deny, island invites, the store/vote/Discord links, help-command suggestions. Open a small menu for Bedrock players, as moderation already does.
- **Hover-only info:** help topics written in page lore, the sale breakdown hover. Move the key line into the item name or the middle item.

---

## 6. Colour system (from the sketches)
| Colour | Hex | Bedrock | Means | Use for |
|---|---|---|---|---|
| Phoenix Red | #FF2D3F | §c | identity, danger | frames, titles, leave/delete only |
| Ember | #FF8C1A | §6 | movement, time | navigation, separators, events and timers |
| Flame Gold | #FFD21A | §e | reward, ready | ready glow, rewards, `» CLICK` lines |
| Money Green | #4DFF88 | §a | gain, safe | money, success, buy |
| Steel Sky | #5AB4E5 / crystals #33E0FF | §b | calm, info | the Depths, info, crystals |
| Arcane Violet | #B36BFF | §5 | magic, mastery | skills, enchants, wardrobe, collection, rare |
| Social Blue | #3DA5FF | §9 | trust, people | community, friends, Mod staff |
| Charcoal / Parchment / Ash | #221A19 / #E8DCC0 / #5E5E66 | §0 / §f / §8 | ground | panels, text, locked |

**Rules:**
- At most about 5 accent colours on one screen.
- Every colour state also has a shape (✔ ✘ + − ★ padlock), for colour-blind players (about 1 in 12 men).
- Gold glow only for real rewards. The store stays calm: no flashing, no fake timers.
- **Group colours** (the left marks and well tints on /menu): Play green, Money gold, Power violet, More ember.

---

## 7. The main screens

### 7.1 /menu: the clean hub
It has 15 buttons in 4 groups, instead of 28.
```
 4  YOU (head: name, rank, money, crystals, playtime)            → You menu
PLAY  (green)   10 Home    12 Island    14 The Depths   16 Travel
MONEY (gold)    19 Sell    21 Shop      23 Bank         25 Auction House
POWER (violet)  28 Forge   30 Skills    32 Enchanter    34 Wardrobe
MORE  (ember)   39 Rewards (gold glow + count, one click claims all)   41 Top
Bottom          48 ?   49 Close   50 Help & guides
```
- **Removed from the hub:**
  - Moved: Spawn and Warps → Travel; Profiles, Titles, Settings, Store, Rules → You; Vote → Rewards; Discord and Website → Rules & links.
  - Duplicates removed: Help ×3, Settings ×2.
  - Hidden until built: Black Market, Quests, Events, Pets, Collection, Leaderboards.
- **Every screen opened from the hub has Back to the hub.** Today `HubMenu.open()` closes the menu and runs a command, so players land on a screen with no way back.
- **"Large Chest" title (UX3):** `HubMenu.title()` returns "Menu", so this is likely Bedrock/Geyser when one screen closes and another opens in the same tick. Test opening on the next tick.

### 7.2 /menu » You (3 rows)
- 4 head.
- 10 Profiles, 11 Titles & Tags, 12 Achievements, 13 Settings, 14 Store, 15 Rules & links.
- 18 Back, 22 Close.

### 7.3 /menu » Travel (3 rows), the owner's UX3 "warps menu"
- 10 Spawn, 11 My Island, 12 Depths Gate, 13 Crate Yard, 14 Market, 15 Forge Quarter, 16 Visit Islands.
- 18 Back, 22 Close.

### 7.4 /shop, buy page, /sell
See `SHOP_DESIGN.md`. In short:
- 9 tabs on every shop page and no category screen.
- Click an item to get Buy 1 / 16 / 64, Fill inventory or Type an amount.
- Undo last buy at 47. The second click for big buys turns the button orange.
- The sell chest's "Sell inventory" button is renamed **"Add my inventory"**, because it only moves items into the chest.

### 7.5 /is (island menu)
- Slots stay as built (10–16, 19–25), plus the standard bottom bar.
- Leave sits in a red well at 40.
- Members shows the 48 h slot lock: "A free slot opens in 31h 12m (Alex left)".

### 7.6 /top (new screen)
- **Why:** today Top is only `/is top` (typed) and its tiles "Weekly gain" and "Pillars" do nothing.
- Tabs in row 1: Solo islands, Team islands, This week, Richest players, Top skills.
- A gold/silver/bronze podium for #1–#3 at 21–23, then #4–#10 at 28–34.
- Click an island to visit it.

### 7.7 Confirm and amount picker
- **Confirm:** details on 13, green 10–12, red 14–16, Back 18, Close 22.
- **Amount picker:**
  - Red −50k/−10k/−1k at 19–21, the value at 22, green +1k/+10k/+50k at 23–25.
  - Min / Type / Max at 30–32, confirm bar at 39–41.
  - The fee is shown on the value item before you click.

---

## 8. Resource pack notes (what this changes in the art brief)
> **Done 31 Oct:** the GUI art v1 is in `resourcepack/`, documented in `docs/plans/RESOURCE_PACK_GUI.md` (backgrounds, icons, badges, every menu's background, wiring steps).

- **B08 hub background:** draw for the 15-button layout:
  - Wells at 4, 10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34, 39, 41.
  - Group marks painted in slots 9, 18, 27, 36.
- **Phoenix Night** (the owner's likely pick) needs **RP-D16 changed**: paint the player-inventory half too, or a dark top sits on a grey bottom.
- **New button art:**
  - `question`, `main_menu`, `undo`, `buy_1`, `buy_16`, `buy_64`, `fill`.
  - 9 shop tab icons.
  - `travel` and `top` feature icons.
- **Rank badges:** update §N to the real names (Ashborn, Hatchling … Immortal; Torchbearer, Flameheart, Firecrown; staff). It still lists Squire, Knight and the other old names.
- **Vanilla items get no art.** Remove any vanilla-item retexture ideas from the brief (§L Depths drops already keeps vanilla looks).

---

## 9. Build order (for the developer, after the owner says yes)
1. **Framework, in `common/menu`.** One change fixes most of the 189 menus:
   - the bottom bar (45 Back, 48 ?, 49 Close, 50 Main menu, 52/53 pages);
   - the 3-row and 4-row standards;
   - the title rule;
   - the locked, empty, loading and error items;
   - confirm details on 13;
   - tabs that refresh in place;
   - "CLICK" wording and both mouse buttons working everywhere.
2. **/menu, You, Travel,** and Back-to-hub for every screen opened from the hub.
3. **Shop v2** (`SHOP_DESIGN.md`): tabs, buy page, catalog migration and its tests.
4. **The per-menu list** in `MENU_FIXES.md`, module by module. Moderation and staff screens come last (only staff use them).
5. **Bedrock popups** for chat-link flows (TPA, invites, links).
6. **Then the pack art** (RP1–RP3) on top of the final slots.

Each step ships in a batch with one test checklist (Java + Bedrock).
