# Build spec GUI-P4-HUD: scoreboard, tutorial bar, holograms/NPC labels and chat event tags (pure vanilla text)

Cards: I9 v2 (Locked, sections 8 and 13), plus the decided review sections of G7 (`G7-R1-01`, GUI-Q12), A3 (`A3-R1-02`, GUI-Q13), A5 (`A5-R1-01`, GUI-Q14), G1 (`G1-R1-01`, GUI-Q15, **no owner answer: the suggested answer applies**) and G8 (`G8-R1-01`, colour part only). Ledger: I9-R1-06 (currency look), C1-R1-13, C1-R2-22. Ripples this build closes: GUI-P18 (HUD half), GUI-P19 (booster bar colour), GUI-P20 (tags exist, G5 adopts them later), GUI-P22 (tutorial holograms). Written 31 Oct 2026. **No code in this file.**

Branch: `q/gui-hud`, created from `q/gui-phase0` (pack v1 already removed). Independent of the menus/shop lane (GUI-P1..P3). No pack, no custom font, no glyph, no negative-space text: every string below is vanilla text from the Bedrock-safe list (`✦ ✧ ◆ ● ▸ ▶ ★ ✔ ✘ ⚔ ⛏ ❤ ⌛ » « │ · •`).

## 0. Goal, scope, status of the cards
**Goal:** the owner's "MC Hub inspiration, vanilla+, our own Phoenix personality" for the four always-visible text surfaces: the sidebar scoreboard (two sections, progress line, live timer), the tutorial boss bar (yellow, `Tutorial n/11 · instruction`, fill = steps done), NPC labels and holograms (`[ Banker ]` / `/bank` / `Click me`) and one-line server event messages with a short coloured tag (`[Crate]`, `[Store]`, `[Market]`, `[Event]`, `[Booster]`).

**Out of scope (do not start):** any menu, tooltip, nav row or icon (other lane); the tab list and nametags (G8 colours were done in phase 0: `chat.yml ranks.looks`); the G5 broadcaster, the C4 price news, store purchase lines (I6), KOTH/envoy lines (I1, I3): their cards are unbuilt, this build only ships the **shared tag styles** they will use; the action-bar gain popup (C1-R2-17: this build leaves the action bar free); crate holograms (`messages/items.yml hologram.*`, F5 decides, GUI-P23); changing any rank colour.

**Card status note (not a blocker):** A3 is `Draft`, G7/A5/G1 are `Built` and G8 `Draft` on their own status lines; only their GUI-PACK review sections are decided, and I9 (Locked) points at them. This spec builds exactly those decided sections and nothing else of those cards. G1's tag table has **no owner answer**; it is built as the default and is one table in `style.yml` + the message lines, so the owner can still change it cheaply.

**Blockers: none.** Every ambiguity has a safe default in section 12.

## 1. Assumptions (verified in the `q/gui-phase0` worktree)
- Credits are already renamed in the code: `credits_formatted`, `credits_owned`, `crystals_owned` exist (`EconomyModule#registerPlaceholders`), `style.yml symbols.credits = ✦`, `currencies.credits = #FF8C1A`, `hud.yml values.credits`, `<gems>`/`<tokens>` only survive as aliases in `HudService#values`. This build uses `<credits>` only.
- The sidebar is the Bukkit scoreboard API with one team prefix per line (`Sidebar`), refreshed by `HudService#tick` every `scoreboard.update-ticks` (20), each player on their own tick, only changed lines sent. Scheduler is the Bukkit scheduler as today; nothing in this build adds a thread, packet or scheduler.
- `LayoutLines` conditions are `if:<placeholder>|text`; hidden lines take no space; `messages/hud.yml` is a plugin-level resource (`plugin/src/main/resources/messages/hud.yml`) with a tracked copy in `server/plugins/PhoenixSkyblock/messages/hud.yml` (the existing `SidebarLinesTest` reads both). **Every yml edited below is edited in the bundled file and the tracked copy in the same commit** (`tools/sync_server_configs.py`: the newer commit wins).
- `RankBadges`/`<rank>` is plain `[Name]` text in the rank colour since phase 0; the G1/G8 "coloured `[RANK]` words" requirement is therefore already true. This build adds a test, not code, for it (step 9).
- "White" in A5 means the `text` colour (`#E0E0E0`, gray on Bedrock) as built in `messages/npcs.yml`; commands inside a line use `value` (pure white).

## 2. Ordered steps (each its own commit, `./gradlew build` green after each; never start a test server)

### Step 1. Tag colours in `style.yml` (common + both yml copies)
- `common/.../common/text/Style.java`: `COLOR_SECTIONS` gets `"tag"` (`List.of("c", "rarity", "pillar", "cur", "tag")`); no other code (`sectionKey("tag")` already returns `tag`, an unknown name falls back to white). Update the class javadoc (`<tag:crate>`).
- `plugin/src/main/resources/style.yml` and `server/plugins/PhoenixSkyblock/style.yml`:
  - `colors.steel: "#5AB4E5 aqua"` (steel sky, I9 section 8; if the other lane already added the same hex under another name, still add this one line, both can stay).
  - new section after `currencies:` (comment: event tags in chat, one colour each, never clickable, G1-R1-01):
```
tag:
  crate: "#FFD21A yellow"     # flame gold
  store: "#FF2D3F red"        # brand red
  market: "#4DFF88 green"     # money green
  event: "#FF8C1A gold"       # ember orange
  booster: "#5AB4E5 aqua"     # steel sky
```
- The five Bedrock fallbacks are all different (yellow, red, green, gold, aqua) so the tags stay distinguishable on Bedrock's 16 colours. A tag is always written `<tag:crate><bold>[Crate]</bold></tag> ` (ASCII brackets, bold, then one space).
- Test: `StyleTest.tagSectionResolves` (a `<tag:crate>` span gets `#FFD21A`, Bedrock resolver gets yellow, an unknown tag name is white, no problem line for the bundled file).

### Step 2. Event tags on the lines that already exist
Replace the leading `<prefix>...` frame with the tag; body text, placeholders and clickable parts stay word for word (no new copy). A tag is never inside `<click>`/`<hover>`.
| File (bundled + tracked copy) | Key | New start of the line |
|---|---|---|
| `modules/crates/src/main/resources/messages/crates.yml` | `broadcast.top`, `broadcast.top-many` | `<tag:crate><bold>[Crate]</bold></tag> <c:value><player></c:value> <c:text>won ...` (replaces `<prefix><c:flame><sym:star></c:flame> `) |
| `modules/boosters/src/main/resources/messages/boosters.yml` | `global.started`, `global.started-queued`, `global.queued-all`, `global.ended` | `<tag:booster><bold>[Booster]</bold></tag> ` (replaces the prefix and the star/clock mark) |
| `modules/blackmarket/src/main/resources/messages/blackmarket.yml` | `announce` | `<tag:market><bold>[Market]</bold></tag> <c:text>The Black Market restocked! ...` |
- **Not tagged (direct replies to one player keep the prefix, UI_STYLE §3.1):** `global.started-own`, `queued`, `disabled`, `queue-full`, thanks lines, the booster boss bar text.
- **Not tagged, no tag fits (open question 6):** `progression.yml global` (island reached level N) and `essentials.yml first-broadcast` (join welcome).
- `[Store]` and `[Event]` and the C4 `[Market]` price news have no existing line; their cards use the tags when built (`GUI-P20`, G5 owns the formats).
- New test `plugin/src/test/java/net/skybornmc/skyblock/EventTagLinesTest.java`: (a) every bundled and tracked `messages/*.yml` value that contains `<tag:x>` names a tag from `style.yml`; (b) a literal `[Crate]`, `[Store]`, `[Market]`, `[Event]`, `[Booster]` appears only together with the matching `<tag:...>`; (c) the nine keys in the table start with their tag, contain no `<prefix>` and no `<click`/`<hover` before the end of the tag; (d) both copies of each file are equal for those keys. Names: `everyTagNameIsDefined`, `bracketWordsUseTheirOwnColour`, `taggedEventLinesStartWithTheirTag`, `tagsAreNeverClickable`, `trackedCopiesMatch`.
- Abuse: players cannot fake an event line: public chat always renders `[RANK] name »`, player text is plain (colour codes need a permission, G1) and placeholders are `Msg.p` (never parsed). No code change; the test in (c) pins that the tag text is a constant.

### Step 3. Shared timer seam (`common`) and its registry (`hud`)
New package `net.skybornmc.skyblock.common.hud` (does not exist yet):
- `BoardTimers` (interface, published by the hud module): 
  - `record Timer(Component label, long remainingMillis)`; `label` is built with the caller's own messages, no colour (the board colours it).
  - `void register(String owner, Function<Player, Optional<Timer>> provider)` (replaces an earlier one of the same owner), `void unregister(String owner)`, `Optional<Timer> soonest(Player player)`.
  - `static Optional<Timer> soonest(Collection<Timer> timers)` (pure: smallest `remainingMillis` above 0; ties keep the first).
  - Contract in the javadoc: main thread, called at most once per player per sidebar refresh (1 s), **map reads only, no I/O, no allocation of lists per call**; a provider that throws is skipped and logged once per owner.
- `modules/hud/.../BoardTimerRegistry.java`: the implementation (`CopyOnWriteArrayList` of providers, a `Set<String>` of owners already logged). Created once in `HudModule#enable` and published with `core.services().register(id(), BoardTimers.class, registry)`; kept across `reload()` (providers are not lost on `/skyblock reload hud`). Known limit: toggling the hud module off and on at runtime drops providers until their own modules enable again (documented in the javadoc).
- Tests (`common/src/test/.../hud/BoardTimersTest`, `modules/hud/src/test/.../BoardTimerRegistryTest`): `soonestPicksTheSmallestPositiveRemaining`, `zeroAndNegativeAreIgnored`, `emptyGivesNothing`, `throwingProviderIsSkippedAndLoggedOnce`, `unregisterRemovesIt`, `sameOwnerReplaces`.

### Step 4. Sidebar engine in `modules/hud` (no text changes yet)
**`LayoutLines` (pure, `modules/hud/.../LayoutLines.java`)** gets line kinds and board rules:
- `Line` gets `Kind kind` (`BLANK`, `SOFT_BLANK`, `HEADER`, `ROW`, `OTHER`), computed once in `parse` (so a refresh never re-inspects strings): `""` = BLANK; the exact text `<soft-blank>` = SOFT_BLANK (consumed by the engine, never reaches MiniMessage; rendered as an empty line); text whose first non-space characters are `<bold><c:header>` = HEADER; text starting (after spaces) with `<c:bar>` = ROW; everything else OTHER (footer, free text). `blank()` is true for BLANK and SOFT_BLANK.
- New `record Rules(int maxLines, int sectionRows, int softBlankMaxLines)` and `List<Line> visible(Predicate<String> shown, Rules rules)`; the old `visible(shown, max)` stays as an overload meaning `new Rules(max, Integer.MAX_VALUE, Integer.MAX_VALUE)` and **without** header dropping (the existing tests keep passing). Algorithm of the new one, in this order:
  1. keep lines whose `if:` condition passes (SOFT_BLANK always passes);
  2. **row cap per section:** a section is a HEADER followed by consecutive ROW lines; ROW lines beyond `sectionRows` are dropped (first in layout order win; the footer ends the section so it is never dropped);
  3. **empty section:** a HEADER with no row after it is dropped (so layouts need no hand-written `if:` on headers);
  4. **soft blank:** count the visible lines that are not SOFT_BLANK; keep every SOFT_BLANK only while that count is `<= softBlankMaxLines`;
  5. truncate to `maxLines`.
- Defaults 3 / 8: one header + up to three rows per section, two sections plus the footer. The blank line under the title therefore stays exactly while the board has **5 rows or fewer** (2 headers + 5 rows + footer = 8) and goes at 6 rows (9 lines below the title = 10 with the title, the decided maximum).

**`ProgressBar` (new, pure):** `static int filled(int percent, int length)` = `floor(clamp(percent,0,100) * length / 100)` (a bar is never fuller than the number; 46 % with 10 dots = 4); `static OptionalInt percent(String text)` accepts `46`, `46%`, ` 46 `, rejects empty, `-`, non numbers; clamps 0 to 100.
**`TimerText` (new, pure):** `static String format(long remainingMillis)`: under 60 s `45s`; under 1 h whole minutes rounded **up** `37m`; under 1 day `2h 15m` (`1h` when minutes are 0); from 1 day `3d 4h` (`3d` when hours are 0). Rounding up to the minute means the timer line changes once a minute (fewer team updates) and never says `0m` while time is left. Returns `""` for 0 or less.

**`HudConfig` (`modules/hud/.../HudConfig.java`), new typed settings** (all editable in `/admin`, tracked `hud.yml` gets them too, no `config-version` bump needed):
| Key | Default | Range | Meaning |
|---|---|---|---|
| `scoreboard.section-rows` | `3` | 1 to 8 | most rows per `\| Header` section |
| `scoreboard.blank-under-title-max-lines` | `8` | 0 to 15 | the soft blank line under the title shows only while the other lines are at most this many |
| `scoreboard.progress-length` | `10` | 5 to 20 | dots in the progress line |
`scoreboard.max-lines` stays 15 (Bedrock limit); the bundled layouts never exceed 9 lines below the title.

**`HudService` (`modules/hud/.../HudService.java`):**
- `lines(layout).visible(...)` uses the new rules from `HudConfig`; a SOFT_BLANK/BLANK line renders `Component.empty()`.
- The `shown` predicate handles the built-in key `board_timer` (true while the player has a timer) before it asks `core.placeholders()`.
- `render` resolves the timer **once** per refresh: `Optional<BoardTimers.Timer> timer = timers.soonest(player)`, only when the layout text contains `board_timer` or `<timer>` (precomputed flag in `LayoutLines`, not a string search per refresh), and passes it to `values(...)`.
- New built-in tags in `values(...)` (all lazy, like the existing ones):
  - `<progress:KEY>`: reads placeholder `KEY` as a percent (`ProgressBar.percent`), renders `messages/hud.yml values.progress` with `<filled>`, `<empty>`, `<percent>`; no number gives the `values.dash` component. 
  - `<plain:KEY>`: the placeholder's plain text inserted as `Component.text` (never parsed, no inner colour), so a header such as `| Copper Caves` keeps the header colour; empty gives nothing.
  - `<timer>`: renders `values.timer` with `<label>` (the provider's component) and `<time>` (`TimerText.format`).
- `<name>`, `<rank>`, `<online>`, `<objective>`, `<island>`, `<island_here>`, `<money>`, `<crystals>`, `<credits>` stay (staff may use them); the deprecated `<gems>`/`<tokens>` aliases stay but **no bundled layout uses them** (test below).
- `HudModule#enable`: create and publish `BoardTimerRegistry` (step 3) before `hud.start()`.
- Tests: `LayoutLinesTest` gains `rowAndHeaderKindsAreDetected`, `sectionRowCapKeepsTheFooter`, `noteBeatsTimerWhenTheSectionIsFull`, `emptySectionHeaderIsDropped`, `softBlankFollowsTheLineBudget` (kept at 8 other lines, gone at 9), `oldVisibleOverloadStillOnlyCapsTheTotal`, `softBlankIsEmptyAndNeverParsed`; new `ProgressBarTest` (`filledRoundsDown`, `percentIsClamped`, `badTextGivesNothing`, `lengthChanges`), `TimerTextTest` (`secondsBelowAMinute`, `minutesRoundUp`, `hoursAndMinutes`, `daysAndHours`, `zeroOrNegativeIsEmpty`), `BuiltinTagsTest` (`plainValueIsNeverParsed`: a value `<red>x` stays literal; `progressTagReadsAPercent`). The three pure tag renderers are static methods so the test needs no Bukkit.

### Step 5. The new layouts (`plugin/src/main/resources/messages/hud.yml` + tracked copy)
**Exact board shape (G7-R1-01):** title (gradient `PHOENIX SKYBLOCK`, the same on every board) + at most two sections + footer = **10 lines at most counting title and footer**. Section 1 is always `| You`; section 2 is named after the place. Header = bold `header` red (`<bold><c:header>| Name</c:header></bold>`), rows start with the dark red bar (`<c:bar>|</c:bar>`), gray label with colon, white or currency-coloured value, footer `play.skybornmc.net` in ice blue (unchanged). The blank line under the title is the `<soft-blank>` line (rule in step 4). No icon, no logo, no online count.

Reusable pieces (written out in every layout, YAML has no includes):
```
you-header:  " <bold><c:header>| You</c:header></bold>"
money:       " <c:bar>|</c:bar> <c:text>Money:</c:text> <money>"
crystals:    "if:crystals_owned| <c:bar>|</c:bar> <c:text>Crystals:</c:text> <crystals>"
credits:     "if:credits_owned| <c:bar>|</c:bar> <c:text>Credits:</c:text> <credits>"
next:        "if:objective| <c:bar>|</c:bar> <objective>"
timer:       "if:board_timer| <c:bar>|</c:bar> <timer>"
footer:      " <c:sky_ice>play.skybornmc.net"
```
Titles: all layouts `title: "<grad:phoenix><bold>PHOENIX SKYBLOCK</bold></grad>"` (replaces `THE DEPTHS` and `WARZONE` titles; the place is now the section header; open question 1).

**Per place** (the layouts and place rules of `hud.yml places` are unchanged; only these texts change). Lines in order; `soft` = `"<soft-blank>"` first line, then `you-header`, money, then the rest:
| Layout | `\| You` rows | Section 2 header and rows (in this order; the 3-row cap drops the later ones) |
|---|---|---|
| `island` | money, crystals, credits | `\| Island`: `if:island_level\| Level: <island>` (`Lv 34 #12`) · next · `if:island_level\| <progress:island_level_progress>` · `if:island_next_note\| Note: <c:value><ph:island_next_note>` · timer. So a note beats the timer; during the tutorial `Next:` takes the progress slot when the island has a level |
| `island-visit` | money, crystals | `\| Visiting`: ` Island: <island_here>` · `if:island_here_owner\| Owner: <c:value><ph:island_here_owner>` · timer |
| `spawn` | money, crystals, credits | `\| Spawn`: next · `if:afk_pool\| <c:bar>\|</c:bar><ph:afk_line>` (the AFK line already carries its leading space) · timer |
| `depths` | money, crystals | `\| <plain:depths_zone>`: Tier `<ph:depths_tier>` · Mining `Lv <ph:skill_mining_level>` · next · `<progress:skill_mining_progress>`. During the tutorial the `Next:` row replaces the progress row (cap) |
| `depths-forest` | money, crystals | same with Foraging (`skill_foraging_level`, `skill_foraging_progress`) |
| `depths-combat` | money, crystals | same with Combat (`skill_combat_level`, `skill_combat_progress`) |
| `depths-gate` | money, crystals | `\| <plain:depths_zone>` (`The Gate`): Mining Lv · Foraging Lv · Combat Lv (three rows, no progress, no timer) |
| `warzone` | money | `\| Warzone`: Combat `Lv <ph:skill_combat_level>` · `<progress:skill_combat_progress>` · timer |
| `default` | money, crystals, credits | `\| Island`: Level (if level) · next · progress (if level) · timer |
- The `Satchel` row of the old Depths boards is dropped (not in the decided rows; the satchel-full warning is on the action bar, UI_STYLE §6.3; open question 2). `Tier` keeps the existing `depths_tier` text (`Mine · Tier 2`).
- Currencies: money `$1.2M` (`<cur:money><sym:money>`), crystals `◆ 4,250`, credits `✦ 12,500` (exact with separators, C1-R2-22). No shards row (not decided).
- Worst case for every layout with every condition true is 9 lines below the title (the soft blank is dropped); with nothing optional it is `soft blank`, `| You`, money, footer.
- Example, own island at level 34 (46 % to the next level), crystals owned, a +50% Sell booster running, no note, tutorial finished:
```
 PHOENIX SKYBLOCK
 (blank)
 | You
 | Money: $1.2M
 | Crystals: ◆ 4,250
 | Island
 | Level: Lv 34 #12
 | ●●●●○○○○○○ 46%
 | Booster: +50% Sell 37m
 play.skybornmc.net
```
  (the filled dots are `flame`, the empty dots the same `●` in `locked` gray, exactly the UI_STYLE §4.1 level bar; `○` is **not** on the Bedrock-safe list and is never used).
- `values` block of `messages/hud.yml` gets: `progress: "<c:flame><filled></c:flame><c:locked><empty></c:locked> <c:value><percent>%"`, `progress-filled: "●"`, `progress-empty: "●"`, `timer: "<c:text><label></c:text> <c:ember><time></c:ember>"`. `values.objective`, `money`, `crystals`, `credits`, `island`, `island-here`, `dash` stay.
- The file's header comment is rewritten: the new built-ins (`<progress:key>`, `<plain:key>`, `<timer>`, key `board_timer`), the `<soft-blank>` line, the row cap and header rules, and "10 lines with the title".
- New placeholder **`island_here_owner`** in the islands module (next to `island_next_note`, `modules/islands/.../IslandNotes.java:143` registers that one; register this in `IslandsModule` where the other island placeholders are): plain text of `Island.ownerName()` for the island whose slot the player stands in, empty when there is none or the name is not loaded yet (the `Owner:` row then hides). Test: `IslandHereOwnerTest` (pure helper `ownerText(Island or null)`: null, blank name, normal name).
- `SidebarLinesTest.bundledLayoutsAreShort` is reduced to what it still proves (both files parse, every place layout named in `HudConfig` exists, no broken `if:` line, per-layout `contains` for the placeholders in the table above, updated). New **`HudLayoutsTest`** (reads both copies, uses `LayoutLines.visible` with the default `Rules`): `everyLayoutFitsTenLinesWithTheTitle` (every `if:` true: at most 9 lines below the title), `minimalBoardStillHasYouAndFooter`, `onePlaceSectionAfterYou` (exactly one `| You` first, at most one other header), `atMostOneProgressAndOneTimerLine` (count `<progress:` and `<timer>` per layout <= 1), `rowsPerPlaceMatchTheDecision` (the table above: credits only in island, spawn, default; warzone only money; visiting has no credits), `noGemsOrTokensTags`, `titleIsTheBrandGradient`, `onlyBedrockSafeSymbols` (message symbols vs `style.yml bedrock-safe-symbols`), `bothCopiesAreIdentical`, `softBlankIsTheFirstLine`.

### Step 6. Booster timer provider and booster bar colour (`modules/boosters`)
- `BoostersModule#enable`: `core.services().optional(BoardTimers.class).ifPresent(t -> t.register(ID, this::boardTimer))`; `disable` unregisters. (hud enables before boosters, `SkyblockPlugin#registerModules` order.)
- `boardTimer(Player)`: the soonest-ending of the player's running personal boosters on the active profile (`PersonalBoosters#state(player).runs`, remaining > 0) and the running global boosters (`GlobalBoosters` active entries); pure picker `BoosterRules.soonestTimer(List<Candidate(type, percent, remainingMillis)>)` (candidates are built in a reused small array, no stream). Label from new key `boosters.yml scoreboard.timer: "Booster: +<percent>% <type>"` (plain, uncoloured; `<type>` = the existing type name) so the line reads `Booster: +50% Sell 37m`. Bundled and tracked `boosters.yml`.
- `BoostersSettings`: `global.boss-bar-color` default `yellow` becomes `blue` (the tutorial bar is yellow now; ripple GUI-P19; `[Booster]` steel sky is closest to vanilla blue). `version()` 2 to 3 with `valueChanges()` `3: global.boss-bar-color: yellow -> blue` (an owner-edited value is kept). Boss and event bars stay red; nothing else about boosters changes.
- Events (I3) will register their own provider with the same call; none exists today, so no event timer shows yet. F11/I3 cards get the provider contract from `BoardTimers` javadoc (ripple GUI-P18 stays open for their text).
- Tests: `BoosterTimerPickTest` (`personalAndGlobalPickSoonest`, `endedAreIgnored`, `noneGivesEmpty`, `tieKeepsFirst`), `BoostersMessagesTest` (new key exists), `BoostersSettingsTest.barColorMovesOffYellowOnlyWhenUnedited`.

### Step 7. Tutorial boss bar (`modules/tutorial`)
**Format (A3-R1-02):** one vanilla boss bar, yellow, `PROGRESS` overlay by default.
```
Tutorial 3/11 · Plant wheat · 2 / 3          (counted step)
Tutorial 5/11 · » Starter Chest · 24m «       (walking to a target: compass text with the distance)
Tutorial 6/11 · Go back · /is home            (not at the step's place)
Tutorial 9/11 · Open the Forge                (plain)
```
- `Tutorial 3/11` is **bold `ember`** (word and count), then a `muted` `·` separator (dark gray on Bedrock), then the one instruction in `value` white. `3` = the number of the current step (`TutorialFlow#number`), `11` = `flow.size()`.
- **Fill = steps done out of the total, always** (never distance, never the count inside a step): `TutorialBar.fill(doneSet, flow)` = number of done steps that are in the flow, divided by `flow.size()`, clamped 0 to 1, **no minimum** (the first step shows an empty bar; open question 8). A step done early counts at once. At step 11 the bar shows 10/11 and disappears when the tutorial finishes.
- **Shows** while the player's tutorial state is `ACTIVE`, `tutorial.enabled` and `guide.bossbar` are on, and no camera shot is playing. **Hides** (not removed): while `Cameras#running` (the bar is hidden when the shot starts and shown again on the next tick after it ends, no flicker, same `BossBar` object); fully removed on finish, staff skip/reset, quit, module disable (existing `Guides#hide`). Bedrock gets the same bar through Geyser (colour yes; notched overlays may be ignored, manual check).
- `Guides.java`: `bar(...)` computes `progress = TutorialBar.fill(p.doneSet(), flow)` in all four branches (delete `BAR_STEP_MIN`, `Compass.distanceProgress` and its two asserts in `SmallRulesTest`); the base resolvers add `Msg.c("head", msg.component(player, "bar.head", number, total))`; `View` gets `boolean barHidden`; `tick` hides the bar when a camera runs (today the bar stays on screen during a shot), `bar()` shows it again when `barHidden`. The memo keys keep `head` (step, number, total) so texts are still parsed once per distinct value; because the fill no longer follows distance the bar object changes only when a step is done (fewer packets than today).
- `TutorialConfig`: `guide.bossbar-color` default `red` becomes `yellow`; new `guide.bossbar-overlay` as a typed enum `Overlay { PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20 }`, default `PROGRESS` (written `progress`, `notched-6` ...; `notched_6` is also accepted by `ConfigManager`), desc names all five. `version()` 3 to 4, `valueChanges()` gets `4: guide.bossbar-color: red -> yellow` (edited values kept). `Guides#color()` falls back to `YELLOW` (was `RED`); a new `overlay()` maps the enum to `BossBar.Overlay` and is applied to a new bar and when the config changes (`v.bar.overlay(...)`).
- `messages/tutorial.yml` (bundled + tracked): `bar.head: "<bold><c:ember>Tutorial <number>/<total></c:ember></bold> <c:muted><sym:dot></c:muted>"`; `bar.plain`, `bar.count`, `bar.away-island`, `bar.away-depths` and the five `compass.*` texts all start with `<head> ` and keep their current wording after it (compass arrows stay `»`/`«` in `ember`). `hologram.*` per-player target holograms move to the A5 pattern: `portal` = `<c:flame><bold>Your Island</bold></c:flame><newline><c:muted>Walk in`, `chest` = `Starter Chest` / `<c:muted>Open me`, `forge` = `Forge` / `<c:muted>Click me`, `help` = `Depths Guide` / `<c:muted>Click me` (GUI-P22; no arrow symbols).
- Tests: new `TutorialBarTest` (`fillIsDoneStepsOverTotal`, `earlyDoneStepsCount`, `stepsOutsideTheFlowAreIgnored`, `fillIsClamped`, `firstStepIsEmpty`, `lastStepIsTenOverEleven`), new `BarConfigTest` (`bossbarColorDefaultsToYellow`, `overlayParsesBothSpellings`, `unknownOverlayFallsBackToProgress`, `redIsMovedToYellowOnlyWhenUnedited`), `MessagesTest` extended (`bar.head` exists; every `bar.*` and `compass.*` text except `bar.head` starts with `<head> `; only Bedrock-safe symbols; the `hologram.*` texts have a bold flame first line), `SmallRulesTest` (remove the `distanceProgress` asserts).

### Step 8. NPC labels and holograms (`modules/npcs`)
**Format (A5-R1-01):**
```
[ Banker ]            bold flame, square brackets with inner spaces
/bank                 white, the command the NPC runs (value colour); only when the role has a command
Click me              muted; help-only NPCs: "Click for help"
```
- `messages/npcs.yml` (bundled + tracked): `npc.label: "<c:flame><bold>[ <name> ]</bold></c:flame><newline><command><hint>"`; `npc.command: "<c:value>/<command>"`; `npc.hint-action: "<c:muted>Click me"`; `npc.hint-help: "<c:muted>Click for help"`. New `hologram-click: "<c:muted>Click me"`.
- `NpcsConfig`: new `npcs.command-line` (boolean, default `true`, desc: show the command an NPC runs under its name) (the card calls it `npc.command-line`; it is a setting here so staff can switch it in `/admin`, the text is the message key above).
- `NpcsModule#npcLabel(RoleRules, Spots.Npc)`: `<command>` is the `npc.command` component plus a newline when `npcs.command-line` is on and `action.hasCommand()` (so `info: help help` shows `/help`, `guide: help menu` shows `/menu`, `black_market: help` shows no command line), else empty. Roles unchanged, names unchanged (`npc-names.*`, `roles.<role>.name`; one or two words).
- **Holograms:** title bold flame, body lines `<c:text>` (white), at most about 36 visible characters, `hologram-click` appended as the **last line only to holograms that are clickable** (`hologram-help.<id>` exists, i.e. the ones that get the click box). New pure `ClickHint.append(rawLines, clickable)` used by `NpcsModule#staticHologramContent` (line count + 1, so `Layout.scale` still fits the board). Portals and signposts are not clickable and get no hint; **leaderboards are unchanged** (open question 7). A build's own text (`hologram-fallback`) is unchanged.
- **Body copy is not rewritten.** The decided "3 lines at most" applies to new holograms; the existing `info_board`, `rules_board`, `blueprint_wall` and `dungeon_board` texts stay until the pre-launch text review (A5 open question 3, default keep). Only a line over 36 visible characters is shortened (the test lists any).
- Untouched on purpose: sneak-click help, `npcs.help-once`, `RoleRules`, the tutorial Guide NPC line, crate holograms (`items.yml`, `Right-Click: Open` stays until F5 decides, GUI-P23). The one action word is `Click` (the NPC texts already say it).
- Tests: new `NpcLabelTest` (`labelIsBracketNameCommandHint` renders the bundled `npc.label` with MiniMessage and fake `name`/`command`/`hint` tags to `[ Banker ]\n/bank\nClick me`; `helpRoleWithCommandShowsItsCommand`; `helpRoleWithoutCommandHasNoCommandLine`; `commandLineSwitchOff`), `ClickHintTest` (`clickableGetsOneMoreLine`, `plainBoardsStayTheSame`), `HologramStyleTest` (every `holograms.*` first line starts with `<c:flame><bold>`; no line over 36 visible characters; `hologram-click` is muted), `MessagesTest` (extend `KEY` to `hologram-click` and the `npc.command` key). The existing `NpcPlanTest` double for `label` is unchanged.

### Step 9. Rank words check, docs, changelog
- G1/G8 rank colour: a small test in `modules/chat` (`RankWordsTest`): `RankBadges` for a listed group returns `[Name]` in the `ranks.looks` colour on the Java form and the legacy code on the Bedrock form, for all 16 default groups, and no two staff groups share a Bedrock fallback. No production change (phase 0 did it); the test pins the G1 "coloured `[RANK]` words" requirement.
- Docs (own sections only, to avoid clashing with the menus lane): `docs/UI_STYLE.md` §6.1 (scoreboard rewritten to the layout above), §6.4 (tutorial bar: yellow, red stays for events and bosses; booster bar blue), §6.4b (NPC label and hologram standard), new §3.4 "Event tags" (the five tags with their colours and examples); §1.3 currency table is not touched (C1 build). `docs/commands/hud.md` (layout summary, built-ins, `BoardTimers`), `docs/commands/tutorial.md` (bar paragraph and the two `guide.*` keys), `docs/commands/npcs.md` (name text: bracket, command, hint; `npcs.command-line`; `hologram-click`), `docs/commands/boosters.md` (scoreboard timer line, bar colour). Run `./gradlew commandsIndex` only if a table row changed (none expected: no command added).
- `CHANGELOG.md`: "Scoreboard: two sections (`| You` + place) with a progress line and a live timer, 10 lines with title; tutorial bar yellow with `Tutorial n/11 ·`, fill = steps done; NPC labels `[ Name ]` / `/command` / `Click me`; event lines tagged `[Crate]` `[Booster]` `[Market]`; booster bar blue."
- Ledger/ripple housekeeping is done by the planner after merge: tick GUI-P19, GUI-P22; GUI-P18 and GUI-P20 stay open for F11/I3 and G5.
- `./gradlew build` green (all unit tests, including `DeadConfigKeysTest`, `YamlKeysTest`, `PermissionFilesTest`), then commit/merge per CLAUDE.md. No server was started; the manual checklist below is for the owner.

## 3. Config keys (all defaults)
| File | Key | Default | New / changed |
|---|---|---|---|
| `hud.yml` | `scoreboard.section-rows` | `3` | new |
| `hud.yml` | `scoreboard.blank-under-title-max-lines` | `8` | new |
| `hud.yml` | `scoreboard.progress-length` | `10` | new |
| `tutorial.yml` | `guide.bossbar-color` | `yellow` (was `red`) | changed, version 4 value change |
| `tutorial.yml` | `guide.bossbar-overlay` | `progress` | new (`progress`, `notched-6`, `notched-10`, `notched-12`, `notched-20`) |
| `npcs.yml` | `npcs.command-line` | `true` | new |
| `boosters.yml` | `global.boss-bar-color` | `blue` (was `yellow`) | changed, version 3 value change |
| `style.yml` | `tag.crate/store/market/event/booster`, `colors.steel` | see step 1 | new |
No SQL, no migration of data, no new permission, no new command.

## 4. Message keys (English text; bundled and tracked copies)
- `hud.yml` (messages): layouts as step 5; `values.progress`, `values.progress-filled`, `values.progress-empty`, `values.timer`.
- `tutorial.yml`: `bar.head` and the rewritten `bar.*`, `compass.*`, `hologram.*` of step 7.
- `npcs.yml`: `npc.label`, `npc.command`, `npc.hint-action` (`Click me`), `npc.hint-help` (`Click for help`), `hologram-click` (`Click me`).
- `boosters.yml`: `scoreboard.timer` (`Booster: +<percent>% <type>`), the four tagged `global.*` lines.
- `crates.yml`: `broadcast.top`, `broadcast.top-many` tagged. `blackmarket.yml`: `announce` tagged.

## 5. Permissions, commands, tab completion
None added. `/settings scoreboard on|off` and `/tutorial`, `/skyblock npcs ...` are unchanged and keep their tab completion; the reload targets already exist (`/skyblock reload hud|tutorial|npcs|boosters|crates|blackmarket|core` for the `style.yml` tag section) and tab-complete today. The scoreboard toggle (`skyblock` `/settings` > Scoreboard, per profile) works as before.

## 6. GUI flow (Bedrock-safe)
No menu is added or changed. Everything is text on three vanilla surfaces: sidebar (works on Bedrock through Geyser; at most 9 lines below the title, under Bedrock's 15), boss bar (colour travels, overlay may not), floating text above NPCs and boards (text displays; `npcs.body: villager` fallback exists for Bedrock). Nothing depends on hover, click or a glyph. Arrows are only `»`, `«`, `·`; dots `●`.

## 7. Performance rules
- Scoreboard: unchanged cadence (20 ticks, spread per player), unchanged changed-line-only sending. New work per refresh: line kinds are precomputed at parse time; `visible` allocates one list and does no string scans; `<progress>`, `<plain>`, `<timer>` are lazy (computed only when a visible line uses them); the timer is resolved once per refresh and only if the layout uses it; the timer text changes once a minute (round up), the progress line only when the percent changes, so most refreshes still send nothing. No per-tick work, no I/O, no new thread; the provider contract forbids both.
- Tutorial bar: still one task per tutorial player (every `guide.update-ticks`); texts still memoized (`TextMemo`); the bar progress now changes only on a step change (fewer packets).
- NPC labels: built once with the plan on the loader thread as today (PERF9); the command line adds no work.
- Folia/packet safety: nothing new touches packets or schedulers; the code keeps the Bukkit scheduler and Adventure bar API it uses today.

## 8. Edge cases and abuse cases
- Island with no level: no `Level` or progress row, `| Island` header disappears with them (answer 275). New player with 0 crystals and 0 credits sees only Money.
- Unknown or missing layout: falls back as today (wing, Depths base, world name, `default`). A custom layout without headers or bar-prefixed rows works (the cap and header rules only act on `<bold><c:header>` and `<c:bar>` lines).
- Timer provider throws or returns 0: row hidden, logged once per owner. Profile switch: provider reads the active profile.
- Placeholder values (island names, owner names, notes, objective text) are inserted as plain text (`Msg.p`, `<plain:>`); a name containing `<red>` stays literal (test).
- `/settings scoreboard off` and disabled worlds: sidebar hidden, nothing rendered, timer not resolved.
- Tutorial: done steps outside the flow (legacy data) are ignored in the fill; an early step counts; staff `step` jump counts earlier steps as done (existing); a camera that errors out still re-shows the bar on the next tick; rejoin builds a fresh bar.
- Spoofing: a player typing `[Crate] ...` in chat is always shown as `[RANK] name » ...` in gray text; event lines have no name colon format.
- Hologram text edited by builders: `npcs.command-line` off removes the command line; a missing `hologram-click` key shows the standard `[missing: ...]` marker, so `MessagesTest` pins the key.

## 9. Manual test checklist (owner, Java and Bedrock; restore anything you change)
Java:
1. New account, walk to the portal: yellow bar `Tutorial 1/11 · » Your Island · 40m «`, bar empty; fill grows by one eleventh per finished step, never with walking; counted steps show `Plant wheat · 2 / 3` with the fill unchanged. Camera shot: bar hidden, back after it.
2. Own island, then someone else's island, spawn, Depths mine, forest, combat wing, the Gate, warzone: always `PHOENIX SKYBLOCK`, `| You` first, one place section, progress line only where something fills, at most 10 lines with the title, footer last, blank line under the title only on short boards.
3. Start a personal booster, then a global one: `Booster: +N% Type 37m` row; it counts down per minute; a note on the island pushes the timer out (3-row cap).
4. Visit a friend's island: `Owner:` row with their name.
5. NPCs at spawn: `[ Banker ]` / `/bank` / `Click me`, `[ Info Clerk ]` / `/help` / `Click for help`; holograms: bold yellow title, rules/info boards end with `Click me`, portals have none; tutorial holograms `Starter Chest` / `Open me`.
6. Chat: open a crate with a top reward (as the owner), start a global booster, trigger a Black Market restock: `[Crate]` yellow, `[Booster]` blue-ish aqua, `[Market]` green, one line each, tag not clickable, player names keep their rank colours.
7. Booster boss bar and tutorial bar are different colours when shown together.
8. `/skyblock reload hud tutorial npcs core`: all apply; `/settings scoreboard off` hides the board at once.
Bedrock phone:
1. Board readable, no `?` boxes, at most 9 lines under the title, progress dots visible (filled yellow, empty dark gray).
2. Tutorial bar shows the text and fill (yellow); note whether notched overlays show (informational).
3. NPC labels and holograms show brackets, command and `Click me`; tapping an NPC works.
4. The five chat tags show in five different colours.

## 10. Size
M: about 14 production files (`Style`, `BoardTimers`, `BoardTimerRegistry`, `LayoutLines`, `ProgressBar`, `TimerText`, `HudConfig`, `HudService`, `HudModule`, `Guides`, `TutorialBar`, `TutorialConfig`, `NpcsModule`, `NpcsConfig`, `ClickHint`, `BoostersModule`, `BoosterRules`, `BoostersSettings`, `IslandsModule`), 8 message files and 3 config files each in two copies, about 16 new tests. Roughly 1 to 1.5 days.

## 11. Risks and rollback
| Risk | Mitigation |
|---|---|
| Merge clash with the menus lane in `style.yml`, `Style.java` (`COLOR_SECTIONS`, `area.*`) | Both changes are additive lines in different sections; resolve by keeping both. `colors.steel` may duplicate an `area` hex, harmless |
| Row cap or header rules hide a line a builder wanted | Rules act only on `<bold><c:header>` and `<c:bar>` lines; limits are `hud.yml` settings |
| Owner's live `messages/*.yml` edits keep old texts | Tracked copies carry the new defaults and win on deploy; a live file edited by hand keeps its text (existing sync rule) |
| `○`/other glyph on Bedrock | Not used; `●` only, tests check every message symbol against `style.yml` |
| Notched overlays not shown on Bedrock | Default `progress`; informational check |
| Existing hologram lines over 36 characters | `HologramStyleTest` names them; shorten wording minimally in the same commit |
Rollback: each step is one commit, `git revert` in reverse order; there is no SQL and no data migration. Config `valueChanges` already applied on a live server (tutorial bar yellow, booster bar blue) are harmless values and can be set back in `/admin`.

## 12. Open questions (safest default each; the owner only vetoes)
1. Same title `PHOENIX SKYBLOCK` on every board (drops `THE DEPTHS` / `WARZONE` titles, because the section header now names the place)? **Default: yes.**
2. Drop the `Satchel` row from the Depths boards (not in the decided rows)? **Default: dropped; the action-bar full warning stays; a builder can add the row back in `messages/hud.yml`.**
3. No timer row in the Depths wings and the Gate (all three rows are used)? **Default: no timer there; it shows wherever a row is free (island, visiting, spawn, warzone, default).**
4. Empty progress dots: card sketch shows `○`, which is not Bedrock-safe. **Default: `●` in `locked` gray, same as the lore bar.**
5. Dots round down (46 % = 4 of 10) while the lore bar rounds to nearest. **Default: round down on the board; the lore bar is not touched.**
6. Tag for the island-level global line (`progression.yml global`) and the welcome line: no tag fits the five. **Default: untagged, unchanged. Black Market restock = `[Market]`.** A sixth tag would break "less is more".
7. `Click me` on leaderboards that have a help box (mining, foraging, combat boards)? **Default: no, boards unchanged.**
8. Tutorial bar at step 1 is empty (no minimum sliver). **Default: exact done/total, no floor.**
9. Visit board shows the owner (the decided row table says "island name, owner"; the older G7 open question 2 defaulted to name only). **Default: the decided table wins; the row hides when the name is unknown.**
10. Booster boss bar new colour. **Default: blue** (F11 may pick another; the key is one value).
11. G1 tag table has no owner answer. **Default: as written in step 1; one `style.yml` section changes it.**
12. The "blank line under the title only for 5 rows or fewer" rule is implemented as a line budget (other lines at most 8), which is identical for two-section boards. **Default: line budget.**
