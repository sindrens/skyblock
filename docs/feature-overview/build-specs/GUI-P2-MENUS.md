# Build spec GUI-P2-MENUS: menu framework and the menus module to the I9 standard, icons everywhere

Cards: I9 v2 (Locked), G9 v3 (Locked), I7 v3 (icons only). Ledger: I9-R1-01..08, G9-R1-01..03, I7-R1-05, GUI-P14/P15/P16/P17. Written 31 Oct 2026. No code in this file. Build order: P1 (GUI-P1-PACK.md) -> **P2 (this)** -> P3 (GUI-P3-SHOP.md), one worktree `q/gui-phase1`.

## 0. Goal and scope
**Goal:** every chest menu looks and behaves like I9: sizes 3/4/6 rows, centred buttons, empty slots stay empty (no frame, no filler panes), a minimal fixed bottom row with our icons, one tooltip template, one red Danger tile rule, vanilla titles, middle-click support in the framework. The `/menu` hub is cleaned as G9 says. Hub, You, Travel and island menus use the pack icons through `MenuService#icon` (plain base item when the viewer has no pack).

**In scope:** `common/menu` framework (section 3), `style.yml` `area` colours, the `menus` module (hub, You, Travel, staff tile, Help tile, menu item), the island menu icons and danger tile, the currency base items, mechanical edits in every other module that calls removed framework APIs (section 5, exact list), tests, docs.

**Out of scope (do not start):** re-laying out the other ~140 menus (each module keeps its content positions; they just lose the frame; module-by-module compaction is the later pass in section 9), the shop (P3), auction search (C6), mastery tree, profiles GUI compaction, staff screens, container background.

**Card status note:** C4/C5 (Review) are not touched here; only I9/G9/I7 (Locked).

## 1. Assumes from P1 (must exist before this step)
`common.icon.Icons` + `IconCatalog` (catalogue bundled as `icons/icons.json`), `MenuService#icons()`, `#icon(Player, String key)`, `#icon(Player, String, Component, List<Component>)`, `common.pack.PackService` (+ `PackStatusEvent`), the icon keys of GUI-P1-PACK section 3 (slice 1 + 2 are used here), `PackWait`. With the pack module off everything below still works and shows plain base items.

## 2. Ordered steps (each step = one commit; `./gradlew build` only if the owner says so)
1. `style.yml` `area` colours and tag (section 3.1).
2. Framework core in `common/menu`: `Centered`, `MenuLayout`, `Menu`, `MenuService`, `MenuButton`, `ClickRoute`, `MenuClick`, `MenuConfig`, delete `MenuHeads` (3.2 to 3.8).
3. Framework menus: `PagedMenu`, `PickListMenu`/`PickListLayout`, `ConfirmMenu`/`ConfirmFlow`, `NumberPickerMenu`, `OfferMenu`, `LoreBuilder` additions (3.9 to 3.13).
4. Mechanical module edits: `frame();` -> `navBar();` everywhere and the `navItem` call sites (section 5).
5. Menus module: `ButtonLayout`, `HubMenu`, `YouMenu`, `TravelMenu`, `Nav`, `MenusModule`, `menu.yml`, `navigation.yml`, migration, `MenuItem` icon (section 6).
6. Island menu icons + danger tile, currency base items (section 7).
7. Docs, CHANGELOG, tracked config sync (section 9).

## 3. Framework (`common/src/main/java/net/skybornmc/skyblock/common/menu`)

### 3.1 `style.yml` + `Style` (plugin resources and tracked copy `server/plugins/PhoenixSkyblock/style.yml`)
Add this section (names are I9 section 8; bedrock fallback after the hex):
```
area:
  money: "#4DFF88 green"      # Shop, Sell, Bank, Auction House, prices
  reward: "#FFD21A yellow"    # Rewards, Daily, Crates, ready counts
  move: "#FF8C1A gold"        # Travel, Back and page arrows, timers, Events
  depths: "#5AB4E5 aqua"      # The Depths, info, crystals
  skills: "#B36BFF dark_purple"  # Skills, Enchanter, Wardrobe, Forge, Collection
  people: "#3DA5FF blue"      # Friends, Members, Parties, Visit
  home: "#7CFF4D green"       # Home, Island
  danger: "#FF3B3B red"       # delete, reset, disband, leave only
  neutral: "#FFFFFF white"    # Settings, Help, Rules, Profiles
  locked: "#5E5E66 dark_gray" # locked entries
```
`Style.COLOR_SECTIONS` becomes `List.of("c", "rarity", "pillar", "cur", "area")` (`sectionKey("area")` already returns `area`). Use in messages as `<area:money>text</area:money>`. Tests: `StyleTest.areaTagsResolveAndHaveBedrockFallbacks` (every area name has a hex and a legacy colour, `<area:home>` renders the hex for Java and the named colour for Bedrock). No module writes a hex value in a menu text (message-audit test, 4.5).

### 3.2 `Centered` (new, pure, unit-tested)
- `static int[] columns(int count)`: 1 = `{4}`; 2 = `{3,5}`; 3 = `{2,4,6}`; 4 = `{1,3,5,7}`; 5 = `{0,2,4,6,8}`; 6 = `{1,2,3,5,6,7}`; 7 = `{1,2,3,4,5,6,7}`; 8 = `{0,1,2,3,5,6,7,8}`; 9 = `{0..8}`; 0 = empty; more than 9 throws. (1-5 are I9 section 2; 6-9 are this spec's symmetric extension: gap in the middle, never a lopsided row.)
- `record Layout(int rows, int[] slots)` and `static Layout layout(int count)`: `count` 0..8 -> `rows 3`, one row at row 1 (slots 9 + column); 9..14 -> `rows 4`, two rows (row 1 gets `ceil(count/2)`, row 2 the rest, each with `columns(n)`, row 2 slots start at 18); more than 14 throws ("use the 6-row grid"). Slots are returned in reading order (the caller puts its n-th visible entry in the n-th slot).
- `static int[] gridSlot(int index)`: the 7x4 grid of lists: `10..16, 19..25, 28..34, 37..43` (same as `PagedMenu.CONTENT_SLOTS`).

### 3.3 `MenuLayout` (rewrite; pure)
Bottom row offset `bar = (rows-1)*9` for 4 and 6 rows; table:
| Rows | Back | Action A | Action B | Close | Search | Previous | Next | Danger corner |
|---|---|---|---|---|---|---|---|---|
| 6 | 45 | 46 | 47 | 49 | 51 | 52 | 53 | 8 |
| 4 | 27 | 28 | 29 | 31 | NONE | 34 | 35 | 8 |
| 3 | 18 | 19 | 20 | 22 | NONE | NONE | NONE | 8 |
| 5 (legacy, until migrated) | 36 | 37 | 38 | 40 | NONE | 43 | 44 | 8 |
| 1-2 | NONE for all |
Methods: `back`, `actionA`, `actionB`, `close`, `search`, `previous`, `next`, `danger(rows)` (8 for rows 3..6, else NONE), `empty(rows)` (22 for 5-6 rows, 13 for 3-4... as built: keep `rows >= 5 ? 22 : rows >= 3 ? 13 : NONE`), `standard(rows, slot)` = Back, Close, Previous, Next only (the old `info`/`mainMenu` slots are removed, 48 and 50 are free now; 46, 47, 51 stay free for screen actions; 51 is documented as the auction-house Search slot, enforced by test 4.5). Delete `info(int)` and `mainMenu(int)`.

### 3.4 `Menu`
- Constants: keep `SLOT_BACK 45`, `SLOT_PREVIOUS 52`, `SLOT_NEXT 53`, `SLOT_CLOSE 49`, `SLOT_SMALL_BACK 18`, `SLOT_SMALL_CLOSE 22`; add `SLOT_SUMMARY 4` (the optional summary tile of the top row), `SLOT_ACTION_A 46`, `SLOT_ACTION_B 47`, `SLOT_SEARCH 51`, `SLOT_DANGER 8`; delete `SLOT_INFO`, `SLOT_MAIN_MENU`.
- Rename `protected final void frame()` to **`navBar()`**: sets only Back (if `parent() != null` and the menu is not the main menu) and Close, for the menu's row count, using `service.backItem`/`closeItem`; **no panes at all, no `?`, no Main menu**. The Back item is cached per opening like before (`frameButtons` array renamed `navItems`, 2 entries).
- Remove `setMarked`'s pane use; keep `setMarked` only for the cached nav items.
- `infoKey()` and `helpTopic()` stay as **`@Deprecated` no-op methods** (135 menus still override them; the framework ignores them; removing the overrides is the later per-module pass, section 9). `messageModule()` stays (used for `setEmpty` texts). `isMainMenu()` stays: it only means "no Back".
- Javadoc: replace the old frame paragraph with the I9 layout table.

### 3.5 `MenuService`
Remove: `applyFrame`, `frameRole`/`FrameRole`, `framePanes`, `innerFiller`, `material(String, Material)` if unused afterwards, `heads` field and its `reload` lines, `openMainMenu`, `runHelp` (the hub does its own Help click), the `frame.info` use, `warnStandardSlot` stays (PagedMenu uses it).
Add / change:
- `public ItemStack backItem(Player v)`: `icon(v, "nav/back", text("frame.back.name"), lines("frame.back.lore"))`; `closeItem(v)` with `nav/close`; `pageItem(Player v, boolean next, int targetPage, int pages)`: `nav/prev` or `nav/next` on `paper`, `setAmount(clamp(targetPage, 1, 64))`, texts `frame.previous` / `frame.next` with `<page>`/`<pages>`.
- `public ItemStack dangerItem(Player v, String module, String key, TagResolver... tags)`: `icon(v, "nav/danger", name, lore)` from `<key>.name` / `<key>.lore` in `module` (TNT base through the catalogue).
- `public List<Component> javaLines(Player v, String module, String key, TagResolver...)`: `lines(...)` for Java viewers, **empty list for Bedrock** (`messages.isBedrock(v)`, cached lookup). This is I9's "Java-only hint line".
- States with icons (same looks as UI_STYLE 5.1, now through `icon`): `loadingItem` = `icon(v, "state/loading", ...)` (clock); `emptyItem` = `state/empty` (cobweb); `locked` = `state/locked` (gray dye); `toggle`: on = `state/on` (lime dye), **off = `state/off` on GRAY_DYE (was RED_DYE: red is reserved for danger)**; `errorItem` stays red stained glass (a pane marking a state is allowed, I9 section 3).
- `onClick`: `plain` now also true for `ClickType.MIDDLE`. After the `top` and `slot` checks, compute `ClickRoute route = ClickRoute.of(type, bedrock, hasRight, hasShift, hasMiddle)` **before** the debounce; `IGNORED` -> `Bukkit.getScheduler().runTask(plugin, player::updateInventory)` (a creative middle click would otherwise leave a cloned cursor item) and return without consuming the debounce. After a handled `MIDDLE` also schedule `player::updateInventory`. Everything else unchanged (all clicks stay cancelled, drag blocked, debounce `click-debounce-ms`, leak purge).
- `reload()`: also `icons.reload()`.

### 3.6 `MenuButton` and `ClickRoute` (middle click, Java only)
- `MenuButton` record gets a fifth component `onMiddle`; keep the 4-arg constructor (`onMiddle = null`); the constraint "a shortcut needs a plain click" covers `onMiddle` too; add `withMiddle(Consumer<MenuClick>)`; `handler(route)` returns `onMiddle` for `MIDDLE` (falls back to plain if null? **no**: `MIDDLE` is only produced when a middle handler exists).
- `ClickRoute` gets constant `MIDDLE`. New `of(ClickType type, boolean bedrock, boolean hasRight, boolean hasShift, boolean hasMiddle)` (the 4-arg version delegates with `false`). Rules, in this order:
  1. Bedrock: `LEFT/RIGHT/SHIFT_*` -> `PLAIN`; anything else `IGNORED` (Bedrock never needs right/middle).
  2. `MIDDLE`: `hasMiddle ? MIDDLE : IGNORED`.
  3. `SHIFT_RIGHT` and `hasMiddle` -> `MIDDLE` (**Shift + Right-click does what middle click does** because a vanilla survival client does not send middle click).
  4. other shift click and `hasShift` -> `SHIFT` (unchanged: crates keep shift for both shift-left and shift-right when they have no middle handler).
  5. right click and `hasRight` -> `RIGHT`; else `PLAIN`.
  6. every other click type (number keys, drop, double click, offhand swap, creative drop...) -> `IGNORED`.
- `MenuClick` gets `isMiddle()` (`type == MIDDLE`).

### 3.7 `MenuConfig` (typed `menus.yml`)
Delete the fields `frame.border`, `frame.accent`, `frame.accent-glow`, `frame.inner`, `heads.back`, `heads.previous`, `heads.next`, `bar.main-menu-command` and list them in `removedKeys()` with the reason "I9: no frame, no filler, vanilla arrow/paper icons, no Main menu button" (same pattern as `ShopSettings#removedKeys`). **No new keys** (compact menus and no filler are decided, there is no option).

### 3.8 Delete `MenuHeads` and its test lines
`MenuHeads.java` deleted; `MenuLogicTest` lines 104-107 (`isPlayerName`, `looksLikeTexture`) deleted. Heads stay only for people (profile, members, visit lists) through their own code.

### 3.9 `PagedMenu`
`build()`: `navBar()`, `decorate()`, entries on `CONTENT_SLOTS` (left-to-right fill, **grids are not centred**: a half-empty last row stays left aligned like the MC Hub shop), empty state via `emptyItem`. Previous/Next: `service.pageItem(viewer, false, page, total)` at 52 and `pageItem(viewer, true, page + 2, total)` at 53, only when there is more than one page; title suffix `(2/5)` unchanged (`title.page`). Page size stays 28.

### 3.10 `PickListMenu` / `PickListLayout`
`PickListLayout.rows(count)`: 3 rows up to 7 choices, **4 rows up to 14**, 6 rows above (28 per page); **delete the 5-row case** (I9: 5 rows are not used). `slot(index, shown)` unchanged for 3 rows; 4 rows use `Centered`'s two-row split; the 6-row list keeps `PER_PAGE 28` with `Centered.gridSlot`. Page buttons through `pageItem`. `PickListLayoutTest` updated.

### 3.11 `ConfirmMenu` / `ConfirmFlow`
3 rows exactly as I9: confirm tile at **11** (`icon "state/confirm"`, base `lime_concrete`), details item at **13**, cancel tile at **15** (`state/cancel`, `red_concrete`); the second screen of `twoStep()` swaps the sides (confirm 15, cancel 11) and shows "Last check: this can't be undone" on the middle item (as built). No panes. Back 18, Close 22 via `navBar()`. `ConfirmFlow`: `confirmSlots()` returns one slot (`{11}` / `{15}`), `cancelSlots()` the other, `DETAILS_SLOT 13`; delete `LEFT/RIGHT` triples and `bigButton`. One-shot behaviour and `ConfirmFlow.Result` unchanged. Details and costs stay on slot 13.

### 3.12 `NumberPickerMenu` (4 rows)
Layout: slot 4 = value tile (as built `number.value`, `sunflower`); row 1 (slots 9..17): `10 -100`, `11 -10`, `12 -1`, **`13 Confirm`** (lime concrete, `state/confirm`, name shows the value), `14 +1`, `15 +10`, `16 +100`; row 2: `20 Minimum`, `22 Type an amount`, `24 Maximum`; row 3: Back 27, Close 31 via `navBar()`. Step sizes from the constructor (up to 3, smallest first) fill 12/11/10 and 14/15/16 as built; fewer steps leave their slots empty. `rows()` returns 4; `infoKey()` removed (no `?`). Logic (`clamp`, `add`, prompt) untouched. Bedrock keeps using this picker for "Type an amount".

### 3.13 `OfferMenu`, `LoreBuilder`
- `OfferMenu`: `frame()` -> `navBar()`; nothing else.
- `LoreBuilder` (`common/item`): add `dangerAction(String input, String text)` (label `lore.action-danger` = `<bold><c:error>» {input}</c:error></bold> <c:text>{text}`) and `hint(String plainText)` (label `lore.hint` = `<c:muted>{text}`), both appended in the existing section order (hint lines sit just above the action line; `dangerAction` replaces `action`). Add the two labels to `plugin/src/main/resources/messages/items.yml` and its tracked copy.

### 3.14 Framework message changes (`plugin/src/main/resources/messages/menus.yml` + tracked copy)
- `frame.back`: name `<area:move><bold>Back`, lore `["<c:text>Back to the last menu.", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to go back"]`.
- `frame.close`: name `<area:neutral><bold>Close` (**no red cross**: red is danger only), lore `["", "<bold><c:action>» CLICK</c:action></bold> <c:text>to close"]`.
- `frame.previous` / `frame.next`: names `<area:move><bold>Previous page` / `<area:move><bold>Next page`, lore as built (`Go to page <page> of <pages>`).
- `frame.help` (hub only): name `<area:neutral><bold>Help`, lore `["<c:text>Every feature, step by step.", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to open"]`.
- **Delete** `frame.main-menu`, `frame.info.*` (dead after this build; the message audit must find no use).
- Titles keep `title.format` (red `▸` marker, grey crumbs) and `title.page`.
- Note: the confirm and number picker texts keep their keys; only their layout comment lines change.

## 4. Tests (framework)
| Test class | What |
|---|---|
| `CenteredTest` | `columns(1..9)` exact arrays above; symmetric (`c + mirror = 8`); `layout(0)`=3 rows empty, `layout(8)`=3 rows row 1, `layout(9)`=4 rows 5+4, `layout(14)`=4 rows 7+7, `layout(15)` throws; slots all inside rows 1-2; no duplicates |
| `MenuLayoutTest` (replaces `MenuFrameworkTest` layout parts) | the table of 3.3 for 3/4/5/6 rows; Back never equals Previous/Next; `standard` only Back/Close/Prev/Next; `search(6)=51`, `search(4/3)=NONE`; `danger=8` for rows 3-6 |
| `ClickRouteTest` | every `ClickType` x {bedrock, java} x {has right/shift/middle combinations}: LEFT -> PLAIN; RIGHT with handler -> RIGHT; SHIFT_LEFT with shift -> SHIFT; SHIFT_RIGHT with middle -> MIDDLE; MIDDLE with handler -> MIDDLE, without -> IGNORED; Bedrock never RIGHT/SHIFT/MIDDLE; NUMBER_KEY, DROP, DOUBLE_CLICK, SWAP_OFFHAND -> IGNORED; crates-style (shift only, no middle) keeps SHIFT for both shift clicks |
| `MenuButtonTest` | a middle handler without a plain click throws; `withMiddle` keeps the others |
| `ConfirmFlowTest` | one-step confirm slots `{11}`/`{15}`, two-step swaps, one-shot, details 13 |
| `PickListLayoutTest` | 7 -> 3 rows, 8 and 14 -> 4 rows, 15 -> 6 rows, never 5; slots centred |
| `NumberPickerLayoutTest` (pure constants) | the 4-row slot table of 3.12, no clash with Back 27 / Close 31 |
| `NoFramePanesTest` (source scan, `common/src/main`) | no `STAINED_GLASS_PANE` in `common/menu` except `MenuService.errorItem` (red glass) and `filler()`; no `frame.` config key; no `MenuHeads` |
| `NoSearchOutsideAuctionTest` (scan of all `modules/*/src/main`) | `Material.SPYGLASS` and the icon key `nav/search` appear only in `modules/auction` (when C6 uses it), `PlayerPickerMenu` (kept, name narrowing) and the icon catalogue; `MenuLayout.search` only on 6 rows |
| `MessageAuditTest` (extend the existing message tests or add one in `common`) | no hex colour (`#RRGGBB`) inside menu message files other than `style.yml`; every `frame.*` key used in code exists |

## 5. Mechanical edits in other modules (exact, compile-driven)
1. **`frame();` -> `navBar();`**: 111 call sites in these 102 classes (`^\s*frame\(\);` only; command: `grep -rlE "^\s*frame\(\);" --include='*.java' common/src/main modules/*/src/main | xargs sed -i '' -E 's/^(\s*)frame\(\);/\1navBar();/'`). Files by module:
   achievements: AchievementsMenu; auction: SellMenu, StaffListingMenu; blackmarket: BlackMarketMenu; boosters: BoostersMenu; common/menu: ConfirmMenu, NumberPickerMenu, OfferMenu, PagedMenu, PickListMenu (these five are rewritten in 3.9-3.13); cosmetics: DisplayMenu, EditorEntryMenu; crates: CratesMenu, PreviewMenu, ResultMenu, SpinnerMenu, StaffCratesMenu; depths: satchel/SatchelItemMenu, tiers/DungeonMenu; economy: admin/WatchMenu, bank/BankHistoryMenu, bank/BankMenu, bank/BankTierMenu, sell/SellMenu, TransactionsMenu; enchants: ArmorEnchantMenu, ArmorEntryMenu, EnchanterMenu, RemoveMenu, ShopMenu, SlotMenu, UpgradeMenu; islands: ChatColorsMenu, InviteActionsMenu, IslandMenu, IslandStatsMembersMenu, IslandStatsMenu, MemberActionsMenu, NoteActionsMenu, PermissionsMenu, PermPresetsMenu, PortalChoiceMenu, SettingsMenu, WarpEditMenu; logs: LogEntryMenu, LogResultsMenu, LogsHubMenu; menus: HelpMenu, HelpTopicMenu, HubMenu, SettingsMenu, StaffMenu, TopMenu, TravelMenu, YouMenu; moderation: AckMenu, AppealMenus, BulkMenus, LockdownMenu, NotifyMenu, PunishMenus, ReportMenu, SanctionMenus; npcs: HelpMenu; pass: PassMenu; perks: FreeRankMenu; profiles: ProfileMenu, ProfilesMenu; progression: LevelMenu, MasteryDetailMenu, TaskBookMenu, TaskDeliverMenu, TopMenu, UpgradeDetailMenu, UpgradeMenu; quests: QuestsMenu; rewards: DailyMenu, VoteMenu; shop: BuyMenu (rewritten in P3); skills: SkillsMenu; stacking: SpawnerMenu; staff: InspectMenu, OutcomeMenu, ReportMenu, ReportReasonMenu; tools: ForgeMenu, ToolMenu; trade: TradeMenu, TradeMoneyMenu; tutorial: OptionalMenu, StaffTutorialMenu, StatsMenu, StepsMenu, TutorialMenu, WhereNextMenu; wardrobe: LoadoutEditMenu, LoadoutMenu, MergeLookMenu, SetMenu, StaffEntryMenu.
   Behavioural effect on all of them: the black/red/orange frame and gray inner panes disappear, `?` (48) and Main menu (50) disappear, Back/Close are our icons. **Content slots are not moved** (module-by-module compaction later). Slots 48 and 50 become free.
2. **`navItem` call sites** (the method `MenuService#navItem` is deleted): replace with `service.pageItem(...)` / `service.backItem(...)`:
   - `economy/bank/BankHistoryMenu` (lines ~99, 103), `economy/admin/WatchMenu` (~84, 89), `economy/TransactionsMenu` (~124, 128), `islands/PermissionsMenu` (~155, 159), `logs/LogResultsMenu` (~133, 137), `pass/PassMenu` (~148, 152): previous/next page buttons -> `service.pageItem(viewer, false, page, pages)` / `pageItem(viewer, true, page + 2, pages)` (keep each menu's own click handlers and slots).
   - `moderation/SanctionMenus` (~295): `set(SLOT_SMALL_BACK, service.navItem("back", ...))` -> `service.backItem(viewer)`.
   - `moderation/ReportMenu` (~87, 91) builds its own ARROW page buttons with `service.item(Material.ARROW...)`: switch to `pageItem` too.
3. **Removed constants**: `Menu.SLOT_INFO`, `Menu.SLOT_MAIN_MENU` and `MenuLayout.info/mainMenu`: fix `common/.../MenuFrameworkTest`, `trade/.../TradeWindowTest` (lines 58-59 compare `TradeMenu.SLOT_CANCEL` to standard slots: keep the Back/Close/Prev/Next comparison, drop the removed ones), `moderation/PunishMenus` line ~741 (`MenuLayout.standard(6, slot)` stays valid).
4. `MenuLayoutTest` replaces the layout assertions of `MenuFrameworkTest`; `MenuLogicTest` loses the `MenuHeads` lines; `rewards/DailyMenuLayoutTest` (asserts `!MenuLayout.standard(6, slot)` for its slots) must still pass: re-check `DailyMenu.SLOT_STREAK` is not 45/49/52/53.
5. Menus that still use `filler(...)` as functional dividers (sell chest, bank, trade window, pass, punish, trash) keep it: those panes are layout dividers inside windows (Trade: divider) not frames. They move in the module-by-module pass.

## 6. Menus module (`modules/menus`)

### 6.1 `menu.yml` (bundled + tracked copy) - new defaults (exact)
```
hub:
  you:       {slot: 4,  action: you,      icon: player_head}
  # Play (lime)
  home:      {slot: 10, group: play,  icon: feature/home,      command: "is home",   service: IslandService}
  island:    {slot: 12, group: play,  icon: feature/island,    command: "is",        service: IslandService}
  depths:    {slot: 14, group: play,  icon: feature/depths,    command: "dungeon",   service: Depths}
  travel:    {slot: 16, group: play,  icon: feature/travel,    action: travel}
  # Money (green)
  sell:      {slot: 19, group: money, icon: feature/sell,      command: "sell",      service: SellService}
  shop:      {slot: 21, group: money, icon: feature/shop,      command: "shop",      service: ShopPrices}
  bank:      {slot: 23, group: money, icon: feature/bank,      command: "bank",      service: BankService}
  auction:   {slot: 25, group: money, icon: feature/auction,   command: "ah"}
  # Power (violet)
  forge:     {slot: 28, group: power, icon: feature/forge,     command: "forge",     service: ToolService}
  skills:    {slot: 30, group: power, icon: feature/skills,    command: "skills",    service: SkillService}
  enchanter: {slot: 32, group: power, icon: feature/enchanter, command: "enchanter", service: EnchantService}
  wardrobe:  {slot: 34, group: power, icon: feature/wardrobe,  command: "wardrobe",  service: WardrobeService}
  # More (flame gold)
  rewards:   {slot: 39, group: more,  icon: feature/rewards,   action: rewards, command: "daily", service: RewardsService}
  top:       {slot: 41, group: more,  icon: feature/top,       action: top,      service: IslandService}
  # Staff tile (G9): only players with the permission see it (hidden, not locked)
  staff:     {slot: 8, icon: feature/staff, action: staff, command: "staff", permission: "skyblock.staff.hub", show-locked: false}
hub-groups:            # a row is a group by its spacing; `color` = the area colour its names take (G9). `mark` panes are gone.
  play:  {color: home}
  money: {color: money}
  power: {color: skills}
  more:  {color: reward}
you:                   # slot is ignored: entries are placed by Centered (file order = reading order, rows of 5+4)
  profiles:     {icon: feature/profiles,     command: "profiles",     service: ProfileService}
  titles:       {icon: feature/titles,       command: "tags",         service: Cosmetics}
  achievements: {icon: feature/achievements, command: "achievements", service: AchievementService}
  quests:       {icon: feature/quests,       command: "quests",       service: QuestService}
  collection:   {icon: feature/collection,   command: "collection",   service: CollectionLog}
  settings:     {icon: feature/settings,     action: settings}
  rules:        {icon: feature/rules,        command: "rules"}
  store:        {icon: feature/store,        command: "store"}
  credits:      {icon: currency/credits,     command: "credit bal",   service: EconomyService}
travel:                # slot ignored too; Centered row of up to 7
  spawn:         {icon: travel/spawn,       target: "command spawn"}
  my-island:     {icon: feature/island,     target: "command is home"}
  depths-gate:   {icon: feature/depths,     target: "location depths 7.5 100 14.5 165 0", reason: dungeon}
  crate-yard:    {icon: travel/crate_yard,  target: "location spawn 46.5 100 -35.5 -90 0"}
  market:        {icon: travel/market,      target: "location spawn 0.5 99 33.5 0 0"}
  forge-quarter: {icon: feature/forge,      target: "point entries forge_road"}
  visit-islands: {icon: travel/visit,       target: "command is visit"}
```
`staff-entries` unchanged (staff screens last). The header comment of the file is updated: `icon:` accepts a vanilla item id (old files) **or** an icon key (`group/name`); an item id shows the plain item for everyone.
- Hub groups are kept in order Play, Money, Power, More; **all 14 buttons are kept** (G9). If the owner names a feature button he finds useless later it moves to You; nothing is cut now.

### 6.2 `ButtonLayout`, `WarpLayout`, `Nav`
- `ButtonLayout`: new action `STAFF = "staff"` (opens `/staff` via `service.navigate`); `ACTIONS` gains it; `Button` gets `boolean showLocked` (key `show-locked`, default true): a button without permission is **hidden** when `showLocked` is false (staff tile) instead of shown locked. `HUB_SLOTS` = the 28 inner slots + 4 + **8**; `YOU_SLOTS`/`SMALL_SLOTS` are removed: for `you` and `travel`, `slot` is optional and ignored (a one-line info if present: "slot is placed automatically"), duplicate-slot checks only apply to the hub. Icon validation: a value is valid if it is a vanilla item id **or** `core.menus().icons().has(value)`; the predicate is injected (`hub(yaml, items, problem)` overloads already exist). `groups()` now reads `hub-groups.<id>.color` (an `area` name, validated against `Style.colors().get("area")`, fallback `neutral`); an old `mark:` key is ignored with **one** console line per file load ("menu.yml hub-groups.<id>.mark is no longer used (I9: no marker panes)").
- `WarpLayout.parse(yaml, items, problem)`: same icon rule; `slot` no longer required.
- `Nav`: new `ItemStack icon(Player viewer, String spec, Component name, List<Component> lore)`: if `spec` is a catalogue key -> `core.menus().icon(viewer, spec, name, lore)`; else the old `Nav.material` path. Delete `Nav.material` callers except the fallback. `textOr` colours a fallback name with the group's area colour when the button has a group. `comingSoon(...)` takes the same `spec`.

### 6.3 `HubMenu` (6 rows, exact changes)
- `build()`: `navBar()` (Close at 49; the hub has no Back); **delete** the filler loop over slots 9..44 and the marker-pane block and `border()`.
- Slot 4 head: unchanged (`nav.head`, PLAYER_HEAD is the people exception).
- Every button: `nav.icon(viewer, button.icon(), name, lore)` instead of `Nav.material`.
- **Names take the row colour**, lore = one grey sentence, then the yellow action line (G9 table). New texts in `messages/navigation.yml` (replace `hub.buttons.*.lore` and add `action` per button; bold name with the group's `area` tag):
  | Button | Name tag | Sentence | Action line (`hub.action*`) |
  |---|---|---|---|
  | Home | `<area:home>` | Teleport to your island. | `» CLICK to go` |
  | Island | home | Manage your island. | `» CLICK to open` |
  | The Depths | home | Mine, chop and fight for loot. | `» CLICK to go` |
  | Travel | home | Spawn, markets and more. | `» CLICK to open` |
  | Sell | `<area:money>` | Sell items for money. | `» CLICK to open` |
  | Shop | money | Buy blocks and basics. | `» CLICK to open` |
  | Bank | money | Keep your money safe. | `» CLICK to open` |
  | Auction House | money | Trade with other players. | `» CLICK to open` |
  | Forge | `<area:skills>` | Make better tools. | `» CLICK to open` |
  | Skills | skills | See your levels. | `» CLICK to open` |
  | Enchanter | skills | Upgrade your gear. | `» CLICK to open` |
  | Wardrobe | skills | Change how you look. | `» CLICK to open` |
  | Rewards | `<area:reward>` | Daily and vote rewards. | `» CLICK to claim` when something is ready, else `» CLICK to open` |
  | Top | reward | See the best islands and players. | `» CLICK to open` |
  | You (head) | flame | Rank, money, crystals, playtime (the `| ` bar section stays) | `» CLICK to open` |
  Keys: `hub.action` (`open`, as built), new `hub.action-go` and `hub.action-claim`; per-button override key `hub.buttons.<id>.action` (a list) chosen first when present (home and depths use `hub.action-go`). `hub.groups.*` texts and `hub.info` are deleted.
- **Rewards ready count** (G9): name `<area:reward><bold>Rewards` (count shown by the stack count and a glint), lore: sentence, then `● <count> ready` (flame, `●` is Bedrock-safe) and the existing source list on a second muted line, then `» CLICK to claim`; nothing ready: sentence + `» CLICK to open`. `hub.rewards.ready-name` is deleted; `hub.rewards.ready` becomes `["<c:flame>● <count> ready", "<c:muted><list>", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to claim"]` (the cached `RewardsService#readyCount` path and the 40-tick check stay as built; never a DB read on open).
- **Staff tile (slot 8):** a normal hub entry with `action: staff`; rendered only when `viewer.hasPermission("skyblock.staff.hub")`; click: `service.navigate(player, this, () -> player.performCommand("staff"))`. Icon `feature/staff`, name `<area:neutral><bold>Staff`, lore "Staff tools." + `» CLICK to open`.
- **Help tile (slot 50):** `set(50, service.icon(viewer, "nav/help", name, lore), click -> service.navigate(click.player(), this, () -> click.player().performCommand("help")))` using `frame.help`. (`isMainMenu()` stays true; the framework no longer draws it.)
- `infoKey()`/`helpTopic()` overrides can stay (deprecated no-ops).

### 6.4 `YouMenu` (4 rows when 9 entries are visible, 3 rows for 8 or fewer)
- `rows()` = `Centered.layout(visibleCount).rows()`; `build()`: `navBar()`, head at slot 4 (`PLAYER_HEAD`, display only), each visible entry in the next slot of `Centered.layout(n).slots()` in file order. Icons through `Nav.icon`. Names keep their text; the action line becomes the I9 `» CLICK to open` (as `hub.action`). Title `Menu » You` stays (2 parts).
- `you.credits` shows the credits icon (`currency/credits`, base `diamond`); Store is also `diamond` (I9 section 9): see question 2.

### 6.5 `TravelMenu` (3 rows)
`rows()` 3 (7 places = `Centered.columns(7)` = slots 10-16; fewer places re-centre automatically); build uses `Centered`, `Nav.icon`, `navBar()`. Hidden (unbuilt) places no longer leave holes.

### 6.6 `MenusModule`, `MenuV3Migration`, `MenuItem`
- `MenusModule.loadFiles()`: pass the icon predicate (`s -> ButtonLayout.isItem(s) || core.menus().icons().has(s)`) to `ButtonLayout.hub/you` and `WarpLayout.parse`; after `migrateHub` run **`MenuV3Migration.apply(yaml)`** (new, pure like `HubV2Migration`): runs once when any `hub-groups.<id>.mark` exists; backup `menu.yml.before-gui-p2`; for each hub/you/travel entry whose live `icon:` equals the **old default** of that id (red_bed, grass_block, iron_pickaxe, ender_pearl, gold_nugget, emerald, barrel, gold_block, smithing_table, experience_bottle, enchanting_table, leather_chestplate, chest, golden_helmet; you: armor_stand, name_tag, totem_of_undying, comparator, diamond, lectern, writable_book, bookshelf, emerald=credits; travel: campfire, grass_block, iron_pickaxe, tripwire_hook, emerald, smithing_table, compass) set the new key from 6.1 (an owner-edited icon is kept); replace each `hub-groups.<id>.mark` by the new `color`; remove the `slot` of `you`/`travel` entries only when **all** their slots equal the old defaults; add the `hub.staff` entry when missing. Console line + audit row `menu_yml_p2_migrate` (category `CONFIG`).
- `MenuItem.create(Player)`: handled in P1 step 7 (icon `menu` through `menus.icon`); P2 adds nothing.

### 6.7 Other `menus` classes (only `navBar()` rename + deprecated overrides stay)
HelpMenu, HelpTopicMenu, SettingsMenu, StaffMenu, TopMenu, HelpCategoryMenu, HelpCommandsMenu, AdminMenu, AdminListMenu, AdminFileMenu: `frame();` -> `navBar();` (HelpCategoryMenu, HelpCommandsMenu, AdminMenu, AdminListMenu, AdminFileMenu are PagedMenu subclasses: nothing to edit). `SettingsMenu` toggles use `MenuService.toggle` (now `state/on`/`state/off`). `StaffMenu` keeps its 6-row `staff-entries` layout (`HubLayout.RESERVED` still reserves edge slots) and Material-only icons (staff screens last).

## 7. Island menu and currency base items

### 7.1 `modules/islands/.../IslandMenu.java` (6 rows kept; B8 compacts later)
Replace each `new ItemStack(Material.X)` / `button(Material.X, ...)` by `service.icon(viewer, key, name, lore)` (the helper `button(Material, String, handler)` becomes `button(String iconKey, String key, handler)`):
| Slot | Old base | Icon key |
|---|---|---|
| 4 island info | GRASS_BLOCK | `feature/island` |
| 10 home | RED_BED | `feature/home` |
| 11 members | PLAYER_HEAD | unchanged (people) |
| 12 permissions | COMPARATOR | `island/permissions` |
| 13 warps | ENDER_PEARL | `island/warps` |
| 14 visitors | OAK_FENCE_GATE / IRON_BARS | `island/visitors_open` / `island/visitors_locked` |
| 15 settings | REPEATER | `island/settings` |
| 16 invites | PAPER | `island/invites` |
| 19 notes | WRITABLE_BOOK / BOOK(locked) | `island/notes` / `state/locked` |
| 20 chat | GLOW_ITEM_FRAME / OAK_SIGN | `island/chat_on` / `island/chat_off` |
| 21 chat colors | ORANGE_DYE | `island/chat_colors` |
| 22 visit | FILLED_MAP | `island/visit` |
| 23 stats | GOLD_INGOT | `island/stats` |
| 24 bank | ENDER_CHEST | `island/bank` |
| 25 upgrades | ANVIL | `island/upgrades` |
| 28 fuel | (as built) | unchanged |
| leave/disband | TNT (disband), IRON_DOOR (leave) at **40** | **`service.dangerItem(...)` (TNT, `nav/danger`) at `SLOT_DANGER` = 8** for both (leader: disband; others: leave) |
Danger tile texts (`menu.disband`, `menu.leave` in `modules/islands/src/main/resources/messages/islands.yml` + tracked copy): name `<area:danger><bold>Disband island` / `Leave island`, two grey lines saying exactly what is lost, then `<bold><c:error>» CLICK</c:error></bold> <c:text>to disband` (red action line); the click still goes to the existing confirm flow (`ConfirmMenu` with `twoStep()` for disband, as built; leave keeps its confirm). `SLOT_LEAVE` constant renamed `SLOT_DANGER`; the layout test `IslandMenu.CONTENT` updated. Every other islands menu: `navBar()` rename only (list in section 5).

### 7.2 Currency base items (I9 section 9)
`modules/economy/.../EconomyConfig#icons` default becomes `["money:GOLD_INGOT", "crystals:AMETHYST_SHARD", "shards:PRISMARINE_SHARD", "credits:DIAMOND", "credits_earned:DIAMOND"]` (was crystals PRISMARINE_CRYSTALS, credits EMERALD); the tracked `server/plugins/PhoenixSkyblock/economy.yml` (if it lists `icons:`) is edited in the same commit. Wherever economy menus show a currency item they call `menus.icon(viewer, "currency/<id>", ...)` through the existing `EconomyConfig#icon(...)` helper (the helper returns the plain Material; add `ItemStack EconomyConfig#iconItem(Player, Currency)` that wraps `menus.icon`). `currency/credits_earned` has no own icon: it uses `currency/credits`.

## 8. Tests (menus / islands / economy)
| Test | What |
|---|---|
| `HubLayoutP2Test` (pure, parses the bundled `menu.yml`) | exactly 14 feature buttons + head (4) + staff (8); slots 10,12,14,16 / 19,21,23,25 / 28,30,32,34 / 39,41; groups play/money/power/more; every `icon` is a catalogue key; **no two buttons of one screen share a base item** (hub, You, Travel: look the base up in the catalogue; documented exception You: Store and Credits both `diamond`, see question 2, asserted explicitly) |
| `ButtonLayoutTest` (extend `HubButtonsTest`) | staff button hidden without permission when `show-locked: false`; icon accepts item id or catalogue key; `mark:` ignored with one problem line; `slot` ignored for you/travel; fix the two `Button(...)` constructors (new `showLocked` component) |
| `YouTravelLayoutTest` (replaces `TravelLayoutTest` slot assertions) | 9 entries -> 4 rows 5+4; 8 -> 3 rows; 7 travel places -> slots 10-16; hidden entries re-centre; `Centered` used |
| `MenuV3MigrationTest` (like `HubV2MigrationTest`) | old defaults -> new keys; owner-edited icon kept; runs once; backup name; staff entry added; marker removed |
| `TooltipLimitsTest` | for every key under `hub.buttons`, `you.buttons`, `travel.places`: after stripping tags, at most 6 lines, at most 36 visible characters per line, the last non-empty line contains `» CLICK` (hub/you/travel), no `[Name]` bracket names, only Bedrock-safe symbols (`style.yml bedrock-safe-symbols`) |
| `RewardsCountLoreTest` | the ready lore has `● <count> ready`; none-ready has no count |
| `IslandDangerTileTest` | `SLOT_DANGER == 8` and not next to Back (45)/Close/pages/the main action; not in `CONTENT` twice |
| `EconomyIconsTest` | default `icons` list = the five I9 base items |
| `MenuItemKeyTest` (existing) | still passes (PDC key unchanged) |

## 9. Docs, config sync, later pass
- Rewrite `docs/UI_STYLE.md` sections 5.1 (frame -> "no frame, empty slots stay empty"), 5.2 (confirm: tiles at 11/15), 5.5 (icon map = I9 section 9 + the catalogue keys), and add the tooltip template (I9 section 6) and the Danger rule (I9 section 5); header "superseded by I9 v2" on `docs/design/GUI_DESIGN.md` and `docs/planning/MENU_GUI.md`; update `docs/commands/menus.md` (hub = 14 buttons + head + Staff tile + Help; You = up to 9 entries in 4 rows; Travel; no `?`, no Main menu button; `skyblock.staff.hub`); `CHANGELOG.md`.
- New permission node `skyblock.staff.hub` in `modules/menus/src/main/resources/permissions/menus.yml` (`default: op`, description "See the Staff tile on the /menu hub") and `server/permissions/menus.txt`: `lp group helper permission set skyblock.staff.hub true` (same groups as `skyblock.staff.menu`).
- `python3 tools/sync_server_configs.py` and commit the tracked copies of `menu.yml`, `messages/navigation.yml`, `messages/menus.yml`, `messages/items.yml`, `messages/islands.yml`, `style.yml`.
- **Later per-module pass (not in this build, tracked in docs/design/MENU_FIXES.md):** compact each module's menus to 3/4/6 rows with `Centered`, delete the deprecated `infoKey()`/`helpTopic()` overrides and their `*.info` message keys, replace `filler()` dividers, give every Danger action the corner tile, swap remaining `Material` icons for catalogue keys, add `nav/search` to the auction house (C6).

## 10. Edge cases and abuse cases
- Items can never leave a menu: all clicks cancelled; **MIDDLE and Shift+Right now reach button handlers only on buttons that declare `onMiddle`**; every other button ignores them and resyncs the client.
- Creative middle click clones nothing: `updateInventory` resync after every middle click (handled or ignored).
- A menu whose `rows()` changes between builds (You menu when an entry appears) is reopened instead of redrawn in place (`redrawInPlace` already compares the inventory size).
- A viewer without the pack, Bedrock, loading or declined: `icon()` returns the plain base item; names carry the meaning.
- Hub staff tile: permission checked at build and again in the click handler; a player who lost the node cannot use a stale menu.
- Old owner `menu.yml` (item ids, `mark`): works, with one console line each (no crash).
- Middle-click debounce: the framework debounce (120 ms) stays; ignored clicks do not consume it.

## 11. Manual test checklist (owner; Java and Bedrock)
Java:
1. `/menu`: no frame, no panes, empty slots empty; 14 buttons in four spaced rows (Play lime names, Money green, Power violet, More gold), head on 4, Close 49, Help 50; staff account shows the Staff tile on 8, a normal account does not. Hover texts: coloured bold name, one grey sentence, yellow `» CLICK ...`.
2. With the pack loaded the icons are ours; decline-free test: `pack.enabled: false` + `/pack reload` shows plain items with the same menu.
3. Rewards: with something claimable the count and `● N ready` show; click claims all; with nothing ready it opens `/daily`.
4. Head -> You (4 rows, 5+4 centred), Back returns to the hub; Travel (7 places in the middle row); every Back/Close/page arrow is correct on a 6-row paged menu (arrows are paper with the page number) and a 3-row menu.
5. `/is`: our icons; the TNT danger tile sits in the top right corner (slot 8), not next to Back; Disband/Leave still ask (two steps for disband).
6. A confirm screen (e.g. disband): lime tile left, details middle, red tile right; second screen swapped.
7. Number picker (Bedrock "Type an amount" path or any module using it): 4 rows, Confirm between minus and plus.
8. Settings toggles: On = lime dye `✔ On`, Off = gray dye `✘ Off` (no red dye).
9. Old menus (`/crates`, `/bank`, `/trade`, `/tools`): still work, no frame, no `?`/Main menu button.
10. Middle-click or Shift+Right-click on a plain menu button does nothing (no error, no cloned item in creative).
Bedrock: same menus on plain items, names readable, Close/Back/page work, confirm tiles tappable, no pack mention.

## 12. Risks and rollback
| Risk | Mitigation |
|---|---|
| 102 files change by sed | compile-driven; only `frame();` lines; review the diff stat; content slots untouched |
| Menus that relied on the black border for contrast look sparse | accepted by owner (GUI-Q4); module pass later |
| Confirm tiles are single slots on a phone | Bedrock slots are large; if the owner finds them too small, widen to 3 slots like before (one constant in `ConfirmFlow`) |
| Live `menu.yml` customised by owner | `MenuV3Migration` keeps edits and writes a backup |
| `rows()` of You menu depends on visible entries | in-place redraw falls back to reopen |
Rollback: `git revert` of this build's commits (framework and menus are one logical group; the pack from P1 keeps working with the old frame because icons are optional). `menu.yml.before-gui-p2` restores layouts.

## 13. Size estimate
**XL** (framework 10 classes + 102 one-line sed edits + 14 hand edits + 2 yml files + 6 message files + about 14 test classes). 3-4 working days.

## 14. Questions the coder cannot settle (safe default applies)
1. The staff tile node: G9 says new node `skyblock.staff.hub`, but `skyblock.staff.menu` already guards `/staff`. Default: follow G9 (new node, same grants as `skyblock.staff.menu`); owner may merge them later.
2. You menu: Store and Credits both have base item `diamond` (I9 section 9 gives both the same base). Default: keep both per I9 (the pack icons differ on Java; Bedrock tells them apart by name); the owner can change `store` to another base in I9.
3. Base item for the Staff tile is not specified in G9/I9. Default: `shield` (key `feature/staff`).
4. Help base item: G9/I7 say `book`; the old frame used `knowledge_book`. Default: `book` (key `nav/help`); `knowledge_book` goes to `ui/info`.
