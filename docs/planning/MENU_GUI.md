# Menu GUI (`/menu`)

**Status:** needs owner input

You said: "change menu gui". This file shows the menu today, its problems, and three options.

## What you already decided
- **33:** chest menus for Bedrock too, no pop-up forms. A forced resource pack will skin **all** GUIs later, so vanilla menus need little design; pick items that can be retextured.
- **41:** unfinished features show "Coming soon" while testing and are **hidden at launch**.
- **44:** everything reachable from menus; Bedrock never needs long commands.
- **50:** the menu item (Nether Star) is a per-profile setting.
- **230:** `/is` opens the island menu.
- **TUT-1 step 5:** the tutorial teaches the menu.

## What exists in the game now
- `/menu` and the Nether Star item open a 6-row menu (`modules/menus/.../menu.yml`).
- Your profile head in slot 4 (rank, playtime, join date; click = settings).
- **29 buttons** in 4 rows of 7:
  - Row 2: Island, Depths, Shop, Sell, Bank, Auction House, Black Market
  - Row 3: Skills, Wardrobe, Enchanter, Quests, Crates, Events, Pets
  - Row 4: Warps, Collection, Leaderboards, Tags, Store, Vote, Discord
  - Row 5: Forge, Spawn, Rules, Settings, Help, Website, Profiles
- Bottom row: Back, Info, Close, Help (standard frame, UI_STYLE §5.1).
- Unbuilt features are gray "Coming soon" (about 10 today).
- QUALITY_OF_LIFE #19 already proposes a **Recent** row and **pinned** buttons in free slots 46, 47, 51, 52.

## The problem / what to decide
1. **Too many buttons at once.** 29 equal buttons; nothing says what matters today.
2. **Daily and rare things are mixed.** Sell and Island sit next to Website and Rules.
3. **No status.** You can't see that a reward is ready, a quest is done or the bank paid out.
4. **Gray buttons** fill a third of the menu while testing.
5. **Two hubs:** `/menu` and `/is` both have island buttons.
6. The art comes later, so the layout must stay fixed for the background textures (RESOURCE_PACK §6.2).

## Options
**A. Keep the grid, clean it up.** Same 29 buttons, reordered, gray ones hidden. Least work, same problems 1 and 3.

**B. Category hub.** 7 big buttons: Island, Depths & Tools, Economy, Progress, Rewards, Community, Settings & Help. Each opens a small sub-menu. Very clean, but one more click for everything.

**C. Smart hub (our pick).** The things you use daily are one click, the rest sits in categories, and buttons show what is waiting.

## Our proposal (option C)
```
 R  O  B  B [You] B  B  O  R      row 1: profile head (rank, money, island level, playtime)
 B [Island][Depths][Sell][Shop][AH] B   row 2: daily, one click
 B [Progress][Rewards●3][Island+][Community][Events] B   row 3: categories (sub-menus)
 B [Recent 1][Recent 2][Pin 1][Pin 2][Pin 3] B   row 4: recent + pinned (QOL #19)
 B [Bank][Forge][Spawn][Warps][Profiles] B   row 5: places and money
 ←  O [Settings] ? ✘ H [Store] O  →      row 6: frame + Settings, Help, Store
```
- **Categories:**
  - Progress: Skills, Wardrobe, Enchanter, Collection, Achievements, Leaderboards.
  - Rewards: Daily, Vote, Quests, Crates, Battle Pass, Black Market.
  - Island+: Upgrades, Members, Visit, Warps browser, Notes (or opens `/is`).
  - Community: Friends, Parties, Tags & Titles, Discord, Rules, Website.
- **Status as a number:** a button shows how many things wait by its item count (Rewards with 3 = 3 rewards ready). This works on Bedrock. Plus one lore line: `● 3 ready`.
- **Hidden, not gray:** unbuilt features are hidden on the live server; on the test server they show in the categories, not on the main page.
- **Island buttons:** the main menu has one Island button (home + island menu). `/is` stays the island hub.
- **Fixed slots** so the pack's background art lines up later.

## Questions for you
1. Use the smart hub (option C)? **Suggested: Yes.** Write A or B below if you prefer one.
2. Row 2 = Island, Depths, Sell, Shop, Auction House as one-click buttons? **Suggested: Yes.** Change the five if you want.
3. Four category buttons: Progress, Rewards, Island+, Community (+ Events)? **Suggested: Yes.**
4. Show waiting things as the item count + one lore line (`● 3 ready`)? **Suggested: Yes.**
5. Add the Recent row and up to 3 pinned buttons (QOL #19)? **Suggested: Yes.**
6. Profile head shows rank, money, island level and playtime? **Suggested: Yes.**
7. Hide unbuilt features on the main page even on the test server? **Suggested: Yes** (they stay in the categories as "Coming soon").
8. Keep `/is` as its own island menu, with one Island button in `/menu`? **Suggested: Yes.**
9. Keep the Nether Star menu item in hotbar slot 9 for new players? **Suggested: Yes** (can be turned off in `/settings`).
10. Store button on the main page? **Suggested: Yes, small, bottom row.** Not in row 2.
11. Should staff see a Staff button in `/menu` (opens `/staff`)? **Suggested: Yes**, staff only.
12. Any button you miss or want gone? Write below.

## Your notes (write here)

