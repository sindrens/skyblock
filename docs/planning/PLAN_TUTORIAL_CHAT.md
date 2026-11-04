# Plan: tutorial, NPC dialog, chat look and grind action bar (owner, 4 Nov 09:50)

The inspiration is the owner's screenshots of another server: their action bar, chat broadcasts, quest book and talking
NPCs. We take the ideas, not the content, and make them fit Phoenix Skyblock (island, /farm, /mine, shop, bank, AH).
This is the planning step only. Nothing is built before the owner answers the questions at the bottom.

---

## 1. One clean action bar while grinding (png 1)
Today several modules write to the action bar (shards, bonus, combo, auto-sell, warnings), so it flickers. Plan:
- **One `ActionBarService` in `common`.** Each module sends *parts* to it with a priority and a lifetime, and the
  service draws one line every 250 ms. Nothing else writes to the action bar directly.
- **Default grind line** (at /farm, /mine, and with island tools):
  `+38 Farming XP  ▰▰▰▰▱▱▱▱▱▱  39% to Lv 5`
  The XP is added up over the last second, so it doesn't jump per block. The bar is 10 segments (green done, gray
  left), and the skill's color is used (Farming yellow, Mining aqua).
- **Payout moment:** every 3 s, *if* something was earned, the line shows for 1.5 s:
  `+$1,240  ·  +12 Farming Shards  ·  Combo x1.20`, then goes back to the XP line.
- **Warnings win:** "Storage full", "Inventory full" and "Rare find!" show first, in red/gold, for 2 s.
- The colors come from the palette in plan A (money green, shards aqua, XP gold). There is no "Coming soon" text and no
  raw numbers (NiceNumbers everywhere).

## 2. Chat look (png 2, 3)
- **Broadcast "cards":** a block of centered lines, with an empty line above and below:
  ```
             SHOP              (gradient title, bold)
     Need blocks for your island?
   Buy BUILDING MATERIALS instantly with
             » /shop «          (clickable, hover: "Click to open the shop")
  ```
  A `ChatCard` helper in `common` centers lines by pixel width (the Minecraft font table). Cards are defined in
  `messages/broadcasts.yml`: title, lines, click command or link, and hover.
- **Auto-broadcasts:** one card every `broadcasts.interval-minutes` (default 7), rotating through: shop, /farm and
  /mine, store, vote, discord, daily reward, island upgrades, friends, and tips. They never repeat back-to-back, and
  they skip the player's current activity (no "/farm" card while you are at /farm). The /settings "Tips" toggle turns
  them off. This replaces the "Tips" "Coming soon" item in plan A.
- **System notices** use a short prefix: `PHOENIX → Items on the ground are cleared in 60 seconds.` (the gradient
  word "PHOENIX", then gray text with white values).
- **Player chat:** `[RANK] Name: message`. The rank badge is in the rank color, the name in the rank color, and the
  message in white (VIP+ can use chat colors). The island level stays on the nametag. This is already mostly in the
  chat module; we just check that ranks, `[Lv N]` and colors look like the reference.
- **Bedrock:** the Bedrock font has different widths, so cards are left-aligned for Bedrock players (Floodgate check).

## 3. `/tutorial`: a guided tour (png 4–11)
**Rowan starts it** (the start flow from phase 0). The tutorial is a set of short **chapters**. Each chapter is a list
of steps; most steps are "meet an NPC" or "do one real thing". It shows the player around and teaches one feature per
step.

### Chapters (proposal, all in `tutorial.yml`)
| # | Chapter | Steps | Reward |
|---|---|---|---|
| 1 | **Welcome to Phoenix** | Talk to Rowan · open `/menu` · go to your island (`/is`) | $500 |
| 2 | **Your Island** | Open the starter chest · plant seeds · build a cobblestone generator (ice + lava) · meet Kindle (island guide) · check `/is level` | $1,000 |
| 3 | **The Farm** | `/farm` · meet the Farm Merchant · harvest 50 crops with the harvester hoe · `/sell` · open the hoe menu (right-click in the air) · buy an upgrade | 100 Farming Shards |
| 4 | **The Mine** | `/mine` · meet the Mine Merchant · mine 50 coal · open the pickaxe menu · buy an upgrade | 100 Mining Shards |
| 5 | **Money Matters** | Meet the Shopkeeper (buy 1 item) · meet the Banker (deposit $100) · meet the Auctioneer (open `/ah`) | $2,500 |
| 6 | **Growing Together** | Buy an island upgrade · add a friend or invite a member · claim `/daily` · `/vote` | 1 crate key |
| 7 | **What's Next** | Open `/skills` · open `/milestones` · reach carrots at /farm (farm tier 2) | $5,000 + a tag |

Total reward: about one hour of early income. It's useful but not game-breaking. Every number goes in config.

### The quest book (png 4–11, improved)
`/tutorial` opens a written book (the vanilla book screen):
- **Page 1 – current chapter:** title, `Chapter 3 · Started`, a progress row `■■□□ 2/4 done`, then the steps:
  - `✔` done (struck through, green)
  - `➤` next (bold, with one italic hint line below: where to go, what to do)
  - `✖` locked (gray)
  - Clicking a step with a place teleports you there (only spawn/farm/mine, with the warm-up).
- **Last pages – all chapters:** `All Chapters [▰▰▱▱▱▱▱] 28%`, with a list of chapters marked `✔ / ➤ / ✖`. Hovering a locked
  one shows a short reason ("Finish *The Farm* first"). The reference shows a whole sentence of requirements; we only
  show the next one.
- **Bedrock:** books can't be clicked on Bedrock, so Bedrock players get the same content as a simple chest menu
  (Floodgate check).

### Tracking
- The **boss bar** shows the current step (`➤ Meet the Farm Merchant at /farm`) and fills as the chapter advances. It
  replaces the phase-0 "Talk to Rowan" bar. It hides itself after 30 s without progress and comes back on a step change.
  `/tutorial hide` turns it off.
- The scoreboard `Next:` line shows the same step.
- `/tutorial track` sends a short particle trail toward the next NPC for 10 s (only when the NPC is in the same world).

### Talking NPCs (the last 3 pngs)
- When a tutorial step involves an NPC, clicking it plays a **dialog**: 1–4 short lines in `tutorial.yml`.
- **Typewriter text:** a TextDisplay above the NPC's head (seen *only by that player*) writes the line one letter at a
  time (`dialog.chars-per-second: 40`, word-wrapped at 30 characters, max 2 lines). It stays 2.5 s after the last letter,
  then the next line. The NPC name is above it in bold color, like "HAZEL".
- **The same line goes to chat** when it finishes: `HAZEL: Your island is never far away. /is is all it takes.`,
  with commands in the line clickable.
- Clicking the NPC again skips to the end of the line, and a second click skips the whole dialog. Walking away (>8
  blocks) ends it.
- An optional soft tick sound every 3 letters (`dialog.sound`, **off by default**, since the owner wants fewer
  annoying sounds).
- **Bedrock:** Geyser doesn't show text displays well, so the text goes to the action bar (typed out) and to chat.
- **Every NPC** (not only tutorial ones) can get idle lines in `npcs.yml` (`dialog.idle`), used when you click it
  outside a step and before its menu opens. It can be turned off per NPC.

### Rules and safety
- **Progress is saved per account**, not per profile, so wiping a profile (leaving an island) doesn't let you farm the
  rewards again. You get the rewards once per account.
- Steps check real events (GrindBreakEvent, sell, shop buy, bank deposit, upgrade bought, NPC click), never commands
  typed.
- `/tutorial skip` (with a confirm screen) for players who know the game: no rewards.
- Staff: `/tutorial admin reset <player>`, `/tutorial admin stats` (how many players finished each chapter, to see
  where new players quit), and `/tutorial admin step <player> <chapter> <step>`.
- Tab completion everywhere. Help entry. Every text in messages.

---

## Questions for the owner
1. **/quests:** today `/quests` is the daily/weekly quest system. Does it stay as it is, with `/tutorial` added as the
   tour? Or should `/quests` become `/tutorial` and the daily/weekly quests be removed?
2. **NPC names:** the spawn NPCs need names (Farm Merchant, Mine Merchant, Shopkeeper, Banker, Auctioneer …). Should I
   pick friendly names like "Hazel" or "Mabel", or keep role names?
3. **Auto-broadcast cards every 7 minutes:** is that OK, or more or less often?
4. **Chapter list and rewards above:** OK, or add or remove some?
