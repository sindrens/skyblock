# Inspiration: MC Hub screenshots (owner, 31 Oct 2026), analysed image by image

Owner brief: **start over** on menus, GUI and resource pack; **remove the old resource pack (pack v1)**; **vanilla+ and simple**; **no custom writing** (no custom font, no font-glyph tricks); good questions with clickable suggestions. These are references for feel and logic, not to copy.

## Chat (image 1)
- Name colours climb a rainbow (red, orange, yellow, green, cyan, blue) so each player is easy to tell apart at a glance; short messages, one line each. Takeaway: colour carries meaning (rank / role), text stays plain vanilla.

## Chests as menus (images 2, 3, 4, 5, 21, 23)
- Shop and spawner shop: a **light-grey panel with a title plaque on top** and **compact content**: items sit in a centred block (7 wide, 1-4 rows) with empty slots left empty, not a wall of filler panes. Title is a short noun ("SHOP", "SPAWNER SHOP", "COIN SHOP"). The panel is shorter than a vanilla chest for small menus. (The panel and plaque need a custom container texture; the title text itself uses a pixel font in these shots, which we do NOT want.)
- A star icon in the last hotbar slot = the "menu item" that opens the hub. Always the same slot, always the same icon.
- Lunar-client shots (images 4, 21, 22, 23): plain vanilla "Shop" / "Auction House" chests with dark text; works, but dense and uncoloured. The auction house is a full 6-row wall of items with the price in the lore: fine for a list, but no hierarchy.

## Selecting and statistics (images 6, 7, 8, 9)
- "SELECT ITEM TYPE": icons are **grouped in columns by meaning** (tools in one column, armour in the next, bow/mace on the side), with big empty space between groups. Hover = dark tooltip: bold coloured name (purple "Chestplates"), a grey "Used in: ..." line, one yellow action line "Click to enchant your chestplates". No other text.
- "ISLAND STATISTICS": icons laid out **symmetrically** (sword, pickaxe, hoe in a tree/diamond), each tooltip = cyan name + one grey sentence + "Total: 0 crops". Bottom row = fixed navigation: orange **left arrow, magnifier (search/filter), right arrow**. Takeaway: same bottom row on every paged menu.

## Scoreboard (images 10, 11, 14, 19, 20 right side)
- Header is a logo (custom image, we skip it; a text header in the red phoenix colour does the job). Date line grey.
- **Bold section headers** in the accent colour ("Pouched", "Island Stats #72"), 3-5 short lines under each, a **small icon before each line** (coin, shard, XP arrow). A **thin progress bar** line ("Cobblestone ||||||| 0%") and a live timer ("Booster: Credits (37:19)"). Takeaway: two sections, never more than ~10 lines, icons optional (they need a custom font, so ours would be text only or Bedrock-safe symbols).

## Tutorial bar (image 12, and top of most shots)
- A vanilla **boss bar** at the top: bold light-blue "Tutorial", a thin separator, the current instruction in white ("Claim the Farmer kit with /kit"), a thin dark progress bar under it. Always visible, never in the way, shows progress. Takeaway: one bar, one instruction, one progress line.

## Island control panel and island menu (images 13, 14, 15, 16)
- 3-row control panel with **three or four big icons spaced out in the middle row**, each tooltip `[Members]` / `[Island Menu]` (coloured bracket name) plus a yellow `* Click to view members *` line.
- Island menu: decorative **checker pattern of purple and grey tinted slots** around 6-7 icons (grass block = island, redstone = settings, spawner, bucket, ...) and a **red "forbidden" icon for Delete Island** with a red name and a clear yellow "Click to delete your island". Takeaway: danger actions use one fixed red icon, far from the safe buttons; a subtle background pattern gives the menu identity without text.
- Spawner tooltip adds a second grey line with a blue highlight ("Interact with Spawn Egg: Sets Mob Type"): help text lives in the lore, not in chat.

## Coin shop / store (images 17, 18, 19)
- Row of **category icons with a gap in the middle** (swords / ender chest / torch on the left = Ranks, Subscriptions, ...; chests / gold bar on the right = keys, coins). Tooltip title coloured per category ("Ranks" red on dark, "Subscriptions" yellow with a one-line hint "Open your ender chest to enable preview"). Takeaway: store = one row of category icons; previews open from the item itself.

## Buying and selling (images 20, 21, 22) - the logic to study
- Block shop: **7 x 4 grid of plain blocks**, bottom row = back (redstone dust icon), a book, **Next Page** (white paper, green label). Hover on an item shows exactly this:
  - `BUY (Left-Click)` red, `| Price: 25,000 (coin)`;
  - `SELL (Right-Click)` green, `| Price: Not Sellable` (red when impossible);
  - a pink note line `* Buy price got reduced by 1.0%! *` (live price change).
- Takeaway for us: **left = buy, right = sell, price shown in both directions, a one-line live price note**, no extra confirm page for single items. It maps to our shop v2 (buy page, shift-click for stacks).

## Spawn world and holograms (images 23, 24, 25, 26 left)
- Portal: big **yellow bold hologram title** ("ISLAND PORTAL") plus two plain white lines ("Jump inside the portal to start your adventure now!"). Clear, one action.
- NPC: **name in brackets + a command hint** ("[ HELPFUL MAN ]  /help") floating over a distinct villager-style NPC. Another hologram: "REALM ACCESS (THE REALM)", grey stats lines (Visits, Location), then a bold "Click Me!".
- Chat events are one-liners with a **short coloured category tag**: `[Rank] name won item and 4 other rewards from the CRATE`, `STORE $ name purchased 5x KEYS`, `(/cf) name made a Coinflip for: 3 GC`.

## What the owner said that matters
- **No custom writing**: use vanilla font and vanilla text rendering everywhere (title, lore, scoreboard, bars, holograms).
- **Vanilla+**: vanilla look and sounds, upgraded with a small, consistent set of custom item icons and (maybe) one container background. Simple, good, easy for 1,000 players. Bedrock must still work. The old pack (v1) is deleted, not extended.
