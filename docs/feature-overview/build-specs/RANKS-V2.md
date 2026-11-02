# Build spec RANKS-V2: ranks, rank looks, LuckPerms groups, perks (supersedes RANKS-COLORS.md)

Written 1 Nov 2026 by the `planner` for the `coder`. Real code checked read-only in `../skyblock-wt-tutfix` (branch `q/gui-phase2`, deployed). Cards: F2 (Review), F1, F4, I6; credits C1 (Locked). Owner answers: `rounds/OWNER_ANSWERS_2026-11-01.md` (EP-Q10 to EP-Q17, HT-Q7, HT-Q8 and the EP-Q14 note); unanswered questions use the suggested answer of `questions/ECON-RANKS-PACE.json`.

## 0. STATUS AND BLOCKERS (read first)
- **F2 is NOT Locked** (status `Review 1`). The planner's standing rule is to spec only Locked cards. This spec was written on the explicit instruction of the lead because the owner has now answered EP-Q10 to EP-Q17. **Do not build until F2 is Locked** (`/lock F2`). Open ripples that point TO F2 (they block the lock): `C1-R1-04b`, `C1-R1-32`, `C1-R2-15`, `C1-R2-30`, `B17-R1-15`, `C6-slots`, `GUI-P4`, `HT-P12` (RIPPLES.md lines 37, 49, 67, 84, 116, 139, 152, 197). Every one of them is answered by this spec; tick them when F2 is edited.
- This spec **replaces** `RANKS-COLORS.md` (not built; the deployed code still has the pack-v1 looks). Its exact staff colour lines are kept in sections 3 and D1.
- Conflict resolved here (write to the ledger, section 13): `G8-R1-01` / `HT-Q8` said "short coloured `[RANK]` word"; the owner's EP-Q14 note says no brackets and a white name: `&e&lOWNER &fPouched`. The EP-Q14 note wins for tab, chat, nameplate and `/list`.
- Size: **L** (about 3 days). Branch `q/ranks-v2`. Commits: (1) `feat(chat): rank words without brackets, bold looks, white names` (2) `feat(perks): Ashborn after tutorial and 30 minutes` (3) `feat(perks): rank rewards (weekly kit, monthly cosmetic)` (4) `chore(permissions): rank groups, weights and perks in ranks.txt` (5) docs. **No tests are run, no test server is started by the coder** (owner rule for this wave); `./gradlew compileJava compileTestJava` only. Test files below are written (names only listed in section 11) but not executed.

## 1. Goal and assumptions
Goal: one clear rank system the owner can read at a glance, identical on tab, chat, nameplate, `/list` and sidebar; every LuckPerms group exists with weight, parent, display name and look; Ashborn arrives by itself; donors get convenience and cosmetics, never paid sell % or XP %.

Assumptions (all checked in code):
1. Looks are drawn by `RankLooks`/`Badges` from `chat.yml ranks.looks` (one line per LuckPerms group id). The LuckPerms prefix and `color` meta are only a fallback and for other plugins. `Badges.simple` draws `[Name]` today; no bold on Java.
2. The tab list sorts through scoreboard team names `pxs<999 - weight><name>` (`TabService.teamName`), so weights 0 to 999 already sort staff first, no code change. The nameplate prefix is the badge (`TabService.apply`, team prefix = `Display.rankFor`), the name colour is the team colour (`Display.teamColor`, nearest named colour). The tab entry is built by `Display.tabEntry` from `format.tab`. `/list` is `SocialModule.list()` (grouped by group id, names in the rank colour). Sidebar rank is `HudService.rankComponent`.
3. `core.ranks().addGroup(uuid, group)` exists (adds a parent in LuckPerms, online or offline, never removes anything). `removeGroup` does not exist (added here, needed by the credit shop).
4. `/freerank` (perks module) already does the safe claim: `perks_freerank` row (primary key = one per account) then `addGroup`, undone when LuckPerms fails. The group is `freerank.group` = `ember`.
5. Config upgrade framework: `ModuleConfig.valueChanges()` changes a default only while the file still holds the old default; `ChatConfig.CHANGED_DEFAULTS` does the same for strings. So the live `chat.yml` upgrades itself unless someone edited it.
6. Permission files: nodes are declared in `modules/<name>/src/main/resources/permissions/<name>.yml`, granted in `server/permissions/<name>.txt`; `PermissionFilesTest` fails on an undeclared granted `skyblock.*` node. `apply-permissions.sh` applies `base.txt` first, then the other files **alphabetically**; `ranks.txt` therefore runs after `auction.txt`, `chat.txt`, `perks.txt`, `profiles.txt`, `quests.txt` and before `rewards.txt`. **Rule: no other module file may grant to a donor, won or free group** (it would run before the group exists). The one existing offender, `chat.txt` (`lp group immortal ...`), moves into `ranks.txt`.

## 2. Decisions (owner = from the answers file, dev = developer default, the owner can veto)
| # | Decision | Source |
|---|---|---|
| D1 | Staff words bold caps: OWNER `&e&l`, ADMIN `&c&l`, MOD `&b&l`, HELPER `&a&l`, BUILDER `&6&l`; the name is white | owner EP-Q14 |
| D2 | Tab: `OWNER Pouched` (word coloured bold, space, white name). Chat: `OWNER Pouched »» message` (word, white name, gray `»»`, white text) | owner EP-Q14 |
| D3 | Names "Phoenix life": Hatchling, Fledgling, Firewing, Flamecrest, Sunfeather, Pyrelord, Immortal; free rank **Ashborn**; won ranks Torchbearer, Flameheart, Firecrown | owner EP-Q10, EP-Q11 |
| D4 | Credit price ladder **700 / 1,400 / 2,700 / 4,000 / 6,000 / 9,000 / 13,500** (rank total; an upgrade pays the difference). The owner picked the "even ladder" (F2 had 8,800 for Pyrelord); **8,800 becomes 9,000** because the owner wants round prices (EP-Q1 note); 2,700 stays: it is exactly one 2,700-credit pack ($25). Every price is a multiple of 100. Prices live in `creditshop.yml` (spec CREDIT-SHOP) | owner EP-Q12 + dev rounding |
| D5 | Ashborn is given automatically when **the tutorial is complete AND total playtime is 30 minutes**; before that the badge reads `New` | owner EP-Q15 |
| D6 | Ashborn gets its own clear colour (grey `#AAAAAA`, the only grey of the line-up; `New` is dark grey `#555555`) | owner "fix colour" |
| D7 | A free profile slot with Firewing (slot 2) and Pyrelord (slot 3) | owner EP-Q16 |
| D8 | Donors get a monthly cosmetic and a weekly kit. No paid sell bonus, no paid XP bonus | owner EP-Q17, EP-Q13 (no answer, suggested a) |
| D9 | AH listing slots grow with rank: base 5 (New), Ashborn 6, Hatchling 7, Fledgling 8, Firewing 9, Flamecrest 10, Sunfeather 12, Pyrelord 14, Immortal 16; won ranks inherit the tier they copy | owner C6-Q3 note; numbers dev |
| D10 | **No brackets for any rank**, donors included (the owner only showed staff; one look for everyone) | dev |
| D11 | Group id `ember` stays the Ashborn group (renaming a LuckPerms group does not carry users); display name `Ashborn` | F2 (kept) |
| D12 | `default` group shows `New` (dark grey). The owner's "default Ashborn" means: everyone who finished the tutorial and played 30 min shows Ashborn | dev reading of EP-Q15 |
| D13 | A purchase does **not** remove the lower donor group (F2 said it should): the highest weight wins the badge anyway, and a chargeback of an upgrade must leave the lower rank in place | dev, reverses a F2 line |
| D14 | Bedrock: staff look hex equals the classic code hex exactly. Ashborn, `New` and Firecrown also exactly. Other donor/won hexes are the F2 palette and Bedrock gets the listed classic code (a phone check by the owner). Reason: 16 classic colours cannot give 16 distinct looks that pass the 30 Lab distance rule of `G8-R1-01` | dev |
| D15 | Monthly cosmetic: no playtime condition (owner principle 1: no busywork; F2 had "5 h that month"). Weekly kit per profile, monthly cosmetic per account | dev |

## 3. The line-up (every LuckPerms group)
Java hex and Bedrock code are the `ranks.looks` line; "meta color" = LuckPerms `color` meta (same hex, fallback only); "prefix" = LuckPerms prefix at priority = weight (for other plugins; the first colour code equals the look's first Bedrock code, `RankConsistencyTest`).

| Group id | Display name | Weight | Parents | Java hex | Bedrock | Prefix (priority = weight) | Kind |
|---|---|---|---|---|---|---|---|
| `default` | New | 0 | - | `#555555` | `&8` | none | before Ashborn; also the look of any unknown group |
| `ember` | Ashborn | 10 | default | `#AAAAAA` | `&7` | `&7Ashborn &f` | free |
| `hatchling` | Hatchling | 100 | ember | `#E3BE7A` | `&e` | `&eHatchling &f` | bought |
| `fledgling` | Fledgling | 110 | hatchling | `#C8702A` | `&6` | `&6Fledgling &f` | bought |
| `firewing` | Firewing | 120 | fledgling | `#FF7F9E` | `&c` | `&cFirewing &f` | bought |
| `flamecrest` | Flamecrest | 130 | firewing | `#D63FB0` | `&d` | `&dFlamecrest &f` | bought |
| `sunfeather` | Sunfeather | 140 | flamecrest | `#C27BFF` | `&5` | `&5Sunfeather &f` | bought |
| `pyrelord` | Pyrelord | 150 | sunfeather | `#2F5BFF` | `&9` | `&9Pyrelord &f` | bought |
| `immortal` | Immortal | 160 | pyrelord | `#FFE27A,#FF8C1A,#FF2D3F` | `&e&l>&6&l>&c&l` | `&e&lImmortal &f` | bought (top) |
| `torchbearer` | Torchbearer | 115 | fledgling | `#2A9D8F` | `&3` | `&3Torchbearer &f` | won (season) |
| `flameheart` | Flameheart | 135 | flamecrest | `#8FB8FF` | `&b` | `&bFlameheart &f` | won |
| `firecrown` | Firecrown | 155 | pyrelord | `#FFFFFF` | `&f&l` | `&f&lFirecrown &f` | won |
| `builder` | BUILDER | 600 | - | `#FFAA00` | `&6&l` | `&6&lBUILDER &f` | staff |
| `helper` | HELPER | 700 | default | `#55FF55` | `&a&l` | `&a&lHELPER &f` | staff |
| `mod` | MOD | 800 | helper | `#55FFFF` | `&b&l` | `&b&lMOD &f` | staff |
| `admin` | ADMIN | 900 | mod, builder | `#FF5555` | `&c&l` | `&c&lADMIN &f` | staff |
| `owner` | OWNER | 999 | admin | `#FFFF55` | `&e&l` | `&e&lOWNER &f` | staff |

Notes: the prefix ends in `&f` (the name is white now; RANKS-COLORS ended it in the rank colour). Staff parents, inheritance and permissions are unchanged. Weights must stay 0 to 999 (tab team name) and every weight above 0 stays lockdown exempt (`Lockdown.rankExempt`); `PerksModule` compares weights for `/setnick` (monotone: ok). The staff weights change from 5/10/20/30/100: no code reads the old numbers (checked: only `Display.rankView`, `SocialModule.list`, `PerksModule:489`, `LockdownService:287` read weights, all relative).

Distinct-colour rule (`G8-R1-01`): all single-colour Java hexes above are at least 30 apart in CIE Lab (closest pairs: Ashborn to Firecrown 30.4, New to Ashborn 33.5, Firewing to ADMIN 33.6); staff are bold caps and always differ by shape from donors. Verified by the planner with the sRGB to Lab formula; the test recomputes it.

## 4. Steps (do in this order)

### Step A: common (module `common`, `common/src/main/java/net/skybornmc/skyblock/common/`)
**A1. `rank/RankLooks.java`.**
- Replace `DEFAULTS` with exactly these 17 lines (the `default` line is the look of unknown groups):
```
"default=#555555|&8",
"ember=#AAAAAA|&7",
"hatchling=#E3BE7A|&e",
"fledgling=#C8702A|&6",
"firewing=#FF7F9E|&c",
"flamecrest=#D63FB0|&d",
"sunfeather=#C27BFF|&5",
"pyrelord=#2F5BFF|&9",
"immortal=#FFE27A,#FF8C1A,#FF2D3F|&e&l>&6&l>&c&l",
"torchbearer=#2A9D8F|&3",
"flameheart=#8FB8FF|&b",
"firecrown=#FFFFFF|&f&l",
"owner=#FFFF55|&e&l",
"admin=#FF5555|&c&l",
"mod=#55FFFF|&b&l",
"helper=#55FF55|&a&l",
"builder=#FFAA00|&6&l"
```
- Add `static final List<String> OLD_DEFAULTS_V1` = the 16 old lines exactly as they are today (copy from the current `DEFAULTS`: `default=#B5ABA4|&7` ... `builder=#FF9A3D|&6&l`), used only by the config upgrade (A5). Update the javadoc ("copied from pack v1" becomes "Ranks V2, 1 Nov 2026").
- `Look` gets `public boolean bold()`: true when every segment of `bedrockSegments()` contains `&l` (RANKS-COLORS idea, kept). Immortal and Firecrown are bold on Java too, as they always were on Bedrock.
- Add `public static String legacyHex(String code)`: the classic colour hex of a `&x` code (`&0 #000000, &1 #0000AA, &2 #00AA00, &3 #00AAAA, &4 #AA0000, &5 #AA00AA, &6 #FFAA00, &7 #AAAAAA, &8 #555555, &9 #5555FF, &a #55FF55, &b #55FFFF, &c #FF5555, &d #FF55FF, &e #FFFF55, &f #FFFFFF`), used by the tests and `rank preview`.

**A2. `rank/Badges.java`: no brackets, bold.**
- `simple(...)` and `bedrock(...)`: the text is the clean display name only (`String text = name;` instead of `'[' + name + ']'`). Everything else (gradient per character, Bedrock segments) stays.
- `simple`: when `look.bold()`, every character/the whole text gets `TextDecoration.BOLD` true. `bedrock` already carries `&l` in its codes.
- Staff display names come from LuckPerms in caps (step D1); `cleanName` is unchanged (it keeps case).
- Javadoc: "Java: the rank word in the rank colour (bold for staff, Immortal gradient)".

**A3. `rank/BadgeService.java`: one name colour.** Add `Optional<TextColor> nameColor(Player player)`: the colour of the player's NAME on chat, tab, nameplate, `/list`, sidebar (white by default). Default method returning `color(player)` so other implementers compile.

**A4. `hook/RankProvider.java` + `hook/LuckPermsRanks.java`: `removeGroup`.**
- `RankProvider`: `default CompletableFuture<Boolean> removeGroup(UUID player, String group)` failing with `IllegalStateException("No rank plugin (LuckPerms) installed")` (mirror of `addGroup`). Completes `true` when removed, `false` when the player did not have it.
- `LuckPermsRanks`: implement like `addGroup`: `lp.getGroupManager().loadGroup(group)` then `lp.getUserManager().modifyUser(player, user -> removed[0] = user.data().remove(InheritanceNode.builder(found.get()).build()).wasSuccessful())`. Used by the credit shop revoke only; **never called by any automatic rank flow in this spec.**

**A5. `tutorial/TutorialFinishedEvent.java` (new).** `public final class TutorialFinishedEvent extends PlayerEvent` (same shape as `ItemsSoldEvent`: `HandlerList`, constructor `(Player player, boolean rewarded)`). Fired on the main thread by `TutorialEngine` in two places (tutorial module, `modules/tutorial/src/main/java/net/skybornmc/skyblock/tutorial/TutorialEngine.java`): inside `finish(...)` right after `StepRules.finished(p, now);` (about line 613, `rewarded = paid`), and in the staff skip path after `StepRules.staffSkip` returns true (`change(player, p -> StepRules.staffSkip(...), false, "staff_skip")`, about line 698; `rewarded = false`). `callEvent` only when the player is online. Nothing else in the tutorial changes.

**A6. `rank/RankPerks.java` (new, read-only service for the credit shop and `/help`).** `public interface RankPerks { Optional<Info> info(String group); record Info(int ahSlots, int profileSlots, int loadoutSlots, String kitSize, List<String> highlights) {} }`, published by the perks module (`core.services().register(ID, RankPerks.class, impl)`), values read from `perks.yml ranks.<group>.*` (display numbers, section E3). The credit shop (spec CREDIT-SHOP) shows `highlights` on the rank tiles; it never reads `perks.yml` itself.

### Step B: chat module (`modules/chat/src/main/java/net/skybornmc/skyblock/chat/`)
**B1. `ChatConfig.java`.**
- New setting `ranks.name-color` (String, default `"#FFFFFF"`): "Colour of every player name next to a rank word (chat, tab, nameplate, /list, sidebar). White (owner EP-Q14). Empty = the name takes the rank colour as before."
- Update the `ranks.looks` desc: remove "[Name]" wording; add "A '&l' in the Bedrock part makes the Java rank word bold too. The rank word has no brackets."
- `format.chat` default becomes `"<title><rank><name><tag> <gray>»»</gray> <white><message>"`. `format.tab` default stays `"<rank><name><tag><afk>"` (the rank part already ends with a space; the name is white through `Display`). Standard MiniMessage `<gray>`/`<white>` are allowed (`Messages` parses with `TagResolver.standard()`), and map to `&7`/`&f` on Bedrock, exactly the owner's colours.
- `CHANGED_DEFAULTS`: `format.chat` array becomes `{"<title><rank><name> <c:muted>»</c:muted> <c:text><message>", "<title><rank><name><tag> <c:muted>»</c:muted> <c:text><message>", "<title><rank><name><tag> <gray>»»</gray> <white><message>"}` (last = new default). `changedDefaultValues()` unchanged.
- `version()` 2 to 3; `valueChanges()` adds step 3: `"ranks.looks"` -> `ValueChange.of(RankLooks.OLD_DEFAULTS_V1, RankLooks.DEFAULTS)` next to the existing step 2 entries (keep them). A `chat.yml` whose list is still the old default becomes the new default on the first start; an edited list is kept (section 8 step 3 checks it).
- `header()`: replace "Ranks show as [Name]" with "Ranks show as a coloured word (OWNER, Hatchling ...) before a white name".

**B2. `RankBadges.java`.** Implement `nameColor(Player)`: parse `config.get().rankNameColor` with `TextColor.fromHexString`; blank or invalid (log once per reload) = `color(player)`. `colorOrNull` keeps returning the **rank** colour (used by `RankProvider.colorSource`, join messages, placeholders `rank_color`, FormatPreview: those stay rank-coloured on purpose).

**B3. `Display.java`.** `color(Player)` (used by `rankView`, `name(player)`) returns `badges.nameColor(player)` (fallback `DEFAULT_NAME`). Consequences, no other change needed: the tab entry name, the chat name (`name(player)`), the team colour (`teamColor(rank)` = nearest named colour of white = WHITE) are all white; the fingerprint `key(...)` still contains the colour, so a config reload rebuilds teams. `RankView.color` documentation: "name colour".

**B4. `TabService.java`: no logic change.** Weights 600 to 999 sort staff first because `teamName` is `pxs<999-weight>`; Ashborn (10) sorts after every donor (100 to 160), New (0) last. Add a code comment with the order: OWNER 999, ADMIN 900, MOD 800, HELPER 700, BUILDER 600, Immortal 160, Firecrown 155, Pyrelord 150, Sunfeather 140, Flameheart 135, Flamecrest 130, Firewing 120, Torchbearer 115, Fledgling 110, Hatchling 100, Ashborn 10, New 0, then A to Z.

**B5. `ChatModule.java`.** `/rank preview` already prints Simple/Java/Bedrock through `BadgeService`; also print `ranks.name-color` sample: add one line key `rank-preview.line` ("`<badge> <white>Steve <gray>»» <white>Hello!`") to `messages/chat.yml` (`plugin/src/main/resources/messages/chat.yml`) using the same badge and the viewer's variant. Tab completion of `/rank preview <group>` is already `badges.looks().groups()`.

### Step C: social and hud (name colour only)
- `modules/social/.../SocialModule.java` `list(...)`: the per-name colour `color` becomes `badge.flatMap(b -> b.nameColor(e.player())).orElse(rank colour)`; the group heading keeps `badge.get().badge(first.player(), sender)` (rank word, now without brackets). Groups stay one line per group id; order stays weight descending (so staff first).
- `modules/hud/.../HudService.java` `values(...)`: `Msg.c("name", Component.text(player.getName(), nameColor))` with `nameColor` from `BadgeService.nameColor(player)` (fallback `color`). `rankComponent` unchanged (the rank word).
- `JoinFlow` (essentials) keeps the rank colour for join/leave lines (not a rank surface in the owner's list).

### Step D: LuckPerms files (`server/permissions/`)
**D1. `base.txt`** (keeps people, inheritance, staff permissions exactly). Replace these blocks:
```
# --- Groups and weights (higher = more important). Staff 600-999, donors 100-160 (ranks.txt), free 10, default 0. ---
lp creategroup builder
lp creategroup helper
lp creategroup mod
lp creategroup admin
lp creategroup owner
lp group default setweight 0
lp group builder setweight 600
lp group helper setweight 700
lp group mod setweight 800
lp group admin setweight 900
lp group owner setweight 999
# Free rank Ashborn (group id stays ember, owner EP-Q11/EP-Q15): given automatically after the tutorial and 30 minutes.
lp creategroup ember
lp group ember setweight 10
lp group ember parent add default
lp group ember setdisplayname Ashborn
lp group default setdisplayname New

# --- Rank colours (hex) = fallback only: SAME hex as chat.yml ranks.looks (RankConsistencyTest) ---
lp group default meta set color "#555555"
lp group ember meta set color "#AAAAAA"
lp group builder meta set color "#FFAA00"
lp group helper meta set color "#55FF55"
lp group mod meta set color "#55FFFF"
lp group admin meta set color "#FF5555"
lp group owner meta set color "#FFFF55"

# --- Display names (the rank word) and prefixes (priority = weight; old priorities are removed first) ---
lp group builder setdisplayname BUILDER
lp group helper setdisplayname HELPER
lp group mod setdisplayname MOD
lp group admin setdisplayname ADMIN
lp group owner setdisplayname OWNER
lp group ember meta removeprefix 1
lp group ember meta removeprefix 10
lp group ember meta addprefix 10 "&7Ashborn &f"
lp group builder meta removeprefix 5
lp group builder meta removeprefix 600
lp group builder meta addprefix 600 "&6&lBUILDER &f"
lp group helper meta removeprefix 10
lp group helper meta removeprefix 700
lp group helper meta addprefix 700 "&a&lHELPER &f"
lp group mod meta removeprefix 20
lp group mod meta removeprefix 800
lp group mod meta addprefix 800 "&b&lMOD &f"
lp group admin meta removeprefix 30
lp group admin meta removeprefix 900
lp group admin meta addprefix 900 "&c&lADMIN &f"
lp group owner meta removeprefix 100
lp group owner meta removeprefix 999
lp group owner meta addprefix 999 "&e&lOWNER &f"
```
Edit the header comment: "Donor ranks and the three won ranks are in ranks.txt (applied after this file)". Caveat: `lp group helper meta removeprefix 10` runs after `ember ... addprefix 10` but on a different group, so no clash. LuckPerms answers "no prefix at that priority" for the removals that find nothing: harmless.

**D2. `ranks.txt` (new file, exact content).** One command per line, every line safe to run twice.
```
# Donor, won and free-rank groups and every perk grant (card F2, build spec RANKS-V2). Applied by scripts/apply-permissions.sh
# AFTER base.txt (groups default, ember and the staff groups exist). Module files that sort before "ranks.txt" (auction, chat, perks,
# profiles ...) run earlier and must NOT grant to these groups. Nodes are declared by their owner module (PermissionFilesTest).
# Looks (chat.yml ranks.looks), weights and display names are kept in sync by RankConsistencyTest.

# --- Groups ---
lp creategroup hatchling
lp creategroup fledgling
lp creategroup firewing
lp creategroup flamecrest
lp creategroup sunfeather
lp creategroup pyrelord
lp creategroup immortal
lp creategroup torchbearer
lp creategroup flameheart
lp creategroup firecrown

# --- Weights (higher wins the badge; staff 600-999 are above all of these) ---
lp group hatchling setweight 100
lp group fledgling setweight 110
lp group firewing setweight 120
lp group flamecrest setweight 130
lp group sunfeather setweight 140
lp group pyrelord setweight 150
lp group immortal setweight 160
lp group torchbearer setweight 115
lp group flameheart setweight 135
lp group firecrown setweight 155

# --- Inheritance: each donor rank has the one below as parent; won ranks copy the tier they match (owner: not sold, so allowed) ---
lp group hatchling parent add ember
lp group fledgling parent add hatchling
lp group firewing parent add fledgling
lp group flamecrest parent add firewing
lp group sunfeather parent add flamecrest
lp group pyrelord parent add sunfeather
lp group immortal parent add pyrelord
lp group torchbearer parent add fledgling
lp group flameheart parent add flamecrest
lp group firecrown parent add pyrelord

# --- Display names (the rank word) ---
lp group hatchling setdisplayname Hatchling
lp group fledgling setdisplayname Fledgling
lp group firewing setdisplayname Firewing
lp group flamecrest setdisplayname Flamecrest
lp group sunfeather setdisplayname Sunfeather
lp group pyrelord setdisplayname Pyrelord
lp group immortal setdisplayname Immortal
lp group torchbearer setdisplayname Torchbearer
lp group flameheart setdisplayname Flameheart
lp group firecrown setdisplayname Firecrown

# --- Colours (fallback meta = the look hex) and prefixes (priority = weight, for other plugins) ---
lp group hatchling meta set color "#E3BE7A"
lp group fledgling meta set color "#C8702A"
lp group firewing meta set color "#FF7F9E"
lp group flamecrest meta set color "#D63FB0"
lp group sunfeather meta set color "#C27BFF"
lp group pyrelord meta set color "#2F5BFF"
lp group immortal meta set color "#FFE27A"
lp group torchbearer meta set color "#2A9D8F"
lp group flameheart meta set color "#8FB8FF"
lp group firecrown meta set color "#FFFFFF"
lp group hatchling meta removeprefix 100
lp group hatchling meta addprefix 100 "&eHatchling &f"
lp group fledgling meta removeprefix 110
lp group fledgling meta addprefix 110 "&6Fledgling &f"
lp group firewing meta removeprefix 120
lp group firewing meta addprefix 120 "&cFirewing &f"
lp group flamecrest meta removeprefix 130
lp group flamecrest meta addprefix 130 "&dFlamecrest &f"
lp group sunfeather meta removeprefix 140
lp group sunfeather meta addprefix 140 "&5Sunfeather &f"
lp group pyrelord meta removeprefix 150
lp group pyrelord meta addprefix 150 "&9Pyrelord &f"
lp group immortal meta removeprefix 160
lp group immortal meta addprefix 160 "&e&lImmortal &f"
lp group torchbearer meta removeprefix 115
lp group torchbearer meta addprefix 115 "&3Torchbearer &f"
lp group flameheart meta removeprefix 135
lp group flameheart meta addprefix 135 "&bFlameheart &f"
lp group firecrown meta removeprefix 155
lp group firecrown meta addprefix 155 "&f&lFirecrown &f"

# --- Perks: AH listing slots (base 5 in auction.yml listing.slots; the highest node wins, C6-Q3) ---
lp group ember permission set skyblock.auction.slots.6 true
lp group hatchling permission set skyblock.auction.slots.7 true
lp group fledgling permission set skyblock.auction.slots.8 true
lp group firewing permission set skyblock.auction.slots.9 true
lp group flamecrest permission set skyblock.auction.slots.10 true
lp group sunfeather permission set skyblock.auction.slots.12 true
lp group pyrelord permission set skyblock.auction.slots.14 true
lp group immortal permission set skyblock.auction.slots.16 true

# --- Perks: free profile slots (owner EP-Q16: Firewing = slot 2, Pyrelord = slot 3; credits also buy them) ---
lp group firewing permission set skyblock.profiles.slots.2 true
lp group pyrelord permission set skyblock.profiles.slots.3 true

# --- Perks: commands and looks (built) ---
lp group hatchling permission set skyblock.perks.hat true
lp group fledgling permission set skyblock.perks.craft true
lp group fledgling permission set skyblock.join.message true
lp group firewing permission set skyblock.perks.enderchest true
lp group firewing permission set skyblock.perks.nick true
lp group flamecrest permission set skyblock.island.fly true
lp group sunfeather permission set skyblock.perks.nick.color true
lp group immortal permission set skyblock.perks.nick.hex true
# Immortal picks the colours of its badge in /settings (moved here from chat.txt).
lp group immortal permission set skyblock.chat.rankcolor true

# --- Perks: loadout slots (base 2; nodes 3 to 5 exist in the wardrobe module) ---
lp group fledgling permission set skyblock.loadout.slots.3 true
lp group flamecrest permission set skyblock.loadout.slots.4 true
lp group pyrelord permission set skyblock.loadout.slots.5 true

# --- Perks: rank rewards (weekly kit S to XL, monthly cosmetic; Rank rewards screen) ---
lp group hatchling permission set skyblock.rank.kit true
lp group hatchling permission set skyblock.rank.monthly true
lp group hatchling permission set skyblock.rank.kit.size.1 true
lp group firewing permission set skyblock.rank.kit.size.2 true
lp group sunfeather permission set skyblock.rank.kit.size.3 true
lp group immortal permission set skyblock.rank.kit.size.4 true
```
- Note the Immortal `meta set color` is only the first gradient stop (fallback; `RankConsistencyTest` compares only single-colour looks and the first stop).
- Staff and the owner do **not** get donor perks through ranks.txt: they already have `*`/their own grants.
- `/trash` is never granted to a donor (owner answer 376).

**D3. Other `.txt` files.** `chat.txt`: delete the two lines `# Immortal picks ...` and `lp group immortal permission set skyblock.chat.rankcolor true` (moved to ranks.txt). `profiles.txt`: replace the commented knight/warlord lines with `# Free profile slots come with Firewing (slot 2) and Pyrelord (slot 3): granted in ranks.txt (card F2).` `auction.txt`: replace the line "Extra listing slots per donor rank ... come with the store ranks" with "granted in ranks.txt". `perks.txt`: edit the comments ("donor groups get them in Phase 11" becomes "donor groups get them in ranks.txt; /freerank gives ember = Ashborn automatically, see AshbornAuto").

**D4. Declare the new nodes** (the grants fail `PermissionFilesTest` otherwise):
- `modules/auction/src/main/resources/permissions/auction.yml`: add `skyblock.auction.slots.9`, `.12`, `.14`, `.16` (same shape as the existing `.6` to `.20`; `default: false`). Existing 6, 7, 8, 10, 15, 20 stay.
- `modules/perks/src/main/resources/permissions/perks.yml`: add `skyblock.rank.kit` (Open Rank rewards and claim the weekly kit, `default: false`), `skyblock.rank.monthly` (claim the monthly cosmetic, `false`), `skyblock.rank.kit.size.1` to `.4` (kit size S, M, L, XL; highest wins, `false`).
- `modules/profiles/src/main/resources/permissions/profiles.yml`: descriptions of `skyblock.profiles.slots.2/.3` become "rank Firewing and up" / "rank Pyrelord and up" (was Knight/Warlord). Also fix the two `ProfilesConfig` desc strings (`slots.price-2`, `slots.price-3`: "Knight+" / "Warlord+").
- Stale old names (F2 reality check 8): comments in `server/permissions/wardrobe.txt` and `docs/` that say Squire to Monarch, Knight, Warlord are rewritten to the new names.

### Step E: perks module (`modules/perks/src/main/java/net/skybornmc/skyblock/perks/`)

**E1. Automatic Ashborn: new `AshbornAuto.java` + a small `AshbornRules.java` (pure) + changes to `FreeRankService`, `FreeRankRules`, `PerksConfig`, `PerksModule`.**

*Rules (pure, `AshbornRules`, unit-tested):* `static boolean due(boolean enabled, boolean ranksAvailable, boolean tutorialComplete, long playtimeMillis, int minMinutes, boolean alreadyClaimed)`: true only when enabled, LuckPerms is there, the tutorial is complete (or the tutorial module is off), playtime >= minMinutes and no `perks_freerank` row exists. `FreeRankRules.State` gets `TUTORIAL` (before `TOO_EARLY`): "finish the tutorial first"; `state(...)` takes the extra boolean `tutorialComplete` (callers updated; all existing states stay).

*Service (`AshbornAuto`, main thread tick, owned by `PerksModule`):*
1. Triggers: `PlayerJoinEvent` (check 100 ticks after join, the profile/tutorial state is loaded by then), `TutorialFinishedEvent` (check at once), and a repeating task every `freerank.auto.check-seconds` (30) that walks the online players **not yet in the in-memory `done` set**, at most `freerank.auto.batch` (5) per run. The check reads only memory: `TutorialService.state(uuid).map(State::complete)` (module absent = complete), `PlayerData.playtimeMillis()`; no database or LuckPerms call until `due(...)` is true.
2. When due: call the **existing safe claim** `FreeRankService.claim(player)` (refactor its body into `claim(Player player, boolean automatic)`): `perks_freerank` insert (one per account, primary key) then `core.ranks().addGroup(id, freerank.group)`, undone if LuckPerms fails. Result `CLAIMED` or `ALREADY` puts the UUID in `done`. `RANK_FAILED` leaves it out and counts an attempt (`freerank.auto.max-attempts` 5 per session, then one WARNING in the console and stop until the next join). Never throws into the tick.
3. **Never downgrade:** only `addGroup` (adds a parent). It never calls `setGroup`/`parent set`/`removeGroup`; a player who is staff or a donor gets `ember` as an extra, lower parent and keeps the higher badge (highest weight wins). A unit test asserts `AshbornRules` has no remove path and the source-scan test `AshbornNoDowngradeTest` fails if `AshbornAuto` or `FreeRankService` contain `removeGroup`, `parent set` or `setGroup`.
4. Idempotent: restarts, rejoins, two servers, a double event and the repeating task can all fire; the claim row makes the second one `ALREADY`. Players who already claimed with `/freerank` (group `ember`) are `ALREADY`: nothing changes for them.
5. Message on success (online player): chat line `freerank.auto-claimed`, sound `success`, audit `auth / freerank_claim` with `automatic: true` (existing action). Staff-skipped and legacy accounts count as tutorial-complete.
6. A mass promotion after the deploy (everyone who qualifies) is spread by `batch` and `check-seconds`: at most 5 LuckPerms writes every 30 s.
7. `/freerank` stays as the one-time **tag pick**: it opens the picker while the claim exists and no tag is chosen; before the claim it says why (new keys `freerank.need-tutorial`, `freerank.too-early` as today). The auto message carries a clickable `[Pick your tag]` (runs `/freerank`).

**E2. Rank rewards: new `RankRewards.java` (service), `RankRewardsStore.java`, `RankRewardsRules.java` (pure), `RankRewardsMenu.java`, `db/perks/V003__rank_rewards.sql`.** Command `/rankrewards` (alias `/rewards`? no, `rewards` belongs to the rewards module): **`/rankrewards`** only, permission `skyblock.rank.kit`. Also reachable from `/menu` -> You (step F).
- **Weekly kit (per profile, bound).** Once per ISO week (Europe/Oslo, week key `yyyy-Www`, resets Monday 00:00, `Format.zone()`) **per profile**. Kit size = the highest `skyblock.rank.kit.size.<n>` the player holds (`player.getEffectivePermissions()` read once at the click; none = no kit). Contents from `perks.yml rank-rewards.kits.S|M|L|XL` (defaults below). Delivery follows the profile rules ("nothing lost", principle 3): (1) one DB transaction inserts the claim row `perks_rank_kit(profile_key, week_key, player_uuid, kit_size, state='claiming', claimed_at)` (primary key = a second claim in the week fails); (2) on the main thread the items are built, **bound to the claiming profile** (`BoundItems.markAll`, never tradeable or auctionable, usable and sellable to the server) and handed out with `ItemBox.give(core.services(), player, BoxSource.PERKS, items)` (inventory first, the rest to `/box`; the `/box` write is its own transaction and has a spool file fallback); (3) the player data is saved (`PlayerSaves`), then the row moves to `delivered`. Fuel: `ProductionService.of(core.services())` present -> `createFuelCells(profileKey, minutes, "rank_kit")` added to the stack list; absent (fuel off) -> the fuel line is skipped silently and `kit.fuel-skipped` is audited.
  - **Crash safety:** a row left in `claiming` (crash between 1 and 3) is re-delivered **once** at the next join of that profile: items go straight to the profile's `/box` (`ItemBox.deliver(profileKey, items, BoxSource.PERKS, Actor.system("rank_kit"))`), the row becomes `delivered`, audit `perks / rank_kit_redeliver` (staff can see it). Rule chosen: **never lost, in the crash window at most doubled once** (the kit is low value food, blocks and a little fuel; losing a paid perk is worse). The claim never spends money.
  - **Tools:** the kit holds no tools and no weapons, so none of the tools-per-profile rules (A4, tools never leave the inventory) are touched. A config validator refuses a kit line that names a `tool_*` custom id, a tool, sword, armour or spawner item (`kits.*` is checked on load; refused lines are logged and skipped).
  - **Per profile:** each profile of a donor can claim its own kit each week (F2: kits are per profile, PR3). Bound to that profile so nothing moves between own profiles (principle 2).
- **Monthly cosmetic (per account).** `skyblock.rank.monthly`; once per calendar month (`yyyy-MM`, Oslo) per account. The cosmetic is the first id in `perks.yml rank-rewards.monthly.pool` (a list of **tag ids** from `tags-titles.yml`) that the player does not own yet, granted with `Cosmetics.grantTag(uuid, id, "rank_monthly")` (idempotent, audited, works offline). The claim row `perks_rank_monthly(player_uuid, month_key, cosmetic_id, claimed_at)` is inserted first (primary key), then the grant; if the grant fails the row is deleted so the player can try again (same undo pattern as `/freerank`). Pool empty or fully owned = the tile says "Nothing new this month" and **no row is burned**.
- **Screen (`RankRewardsMenu`, P3, plaque `REWARDS`).** Slot 4 summary: the rank word in its colour and "Perks of <rank>". Row 1 centred pattern for 2 tiles (columns 3 and 5): Weekly kit (icon `chest`; lore: "Food, blocks and a little fuel."/"Size <S>. Bound to this profile."/"| Next kit: Monday" or "| Ready"/"» CLICK to claim"), Monthly cosmetic (icon `name_tag`; "One new tag every month."/"| Ready" or "| Claimed for <Month>"/"» CLICK to claim"). Back slot 18 (to You), Close 22. Plain click, Bedrock-safe, no typing. The button is hidden for players without `skyblock.rank.kit`.
- Kit contents (**placeholders, owner tunes in `perks.yml`**; vanilla items that exist in 1.21.5): 
  `S`: cooked_beef 16, cobblestone 64. `M`: cooked_beef 32, cobblestone 128, oak_planks 64, fuel 15 min. `L`: cooked_beef 48, stone_bricks 128, oak_planks 128, fuel 30 min. `XL`: golden_carrot 48, cooked_beef 48, stone_bricks 256, oak_planks 256, fuel 60 min. Format in the file: `item: material amount` lines plus `fuel-minutes: N`.
- Mojang note (logged in F2 by the card editor): the kit's island fuel is minor progress sold with a rank; fuel is capped by the Fuel Tank and bound, the owner accepted "progress to a degree" for donations (principle 9). Food and blocks are convenience.

**E3. `PerksConfig.java` new keys** (defaults; all in `perks.yml`, `/skyblock reload perks`):
| Key | Default | Meaning |
|---|---|---|
| `freerank.group` | `ember` | the Ashborn group id (unchanged) |
| `freerank.min-playtime-minutes` | `30` | playtime for Ashborn (unchanged key, owner EP-Q15) |
| `freerank.auto.enabled` | `true` | automatic Ashborn |
| `freerank.auto.check-seconds` | `30` | repeating check |
| `freerank.auto.batch` | `5` | players checked per run |
| `freerank.auto.max-attempts` | `5` | retries per session after a LuckPerms failure |
| `rank-rewards.enabled` | `true` | the Rank rewards screen |
| `rank-rewards.weekly.enabled` | `true` | weekly kit |
| `rank-rewards.kits.S` ... `XL` | lines above | kit contents (validated) |
| `rank-rewards.monthly.enabled` | `true` | monthly cosmetic |
| `rank-rewards.monthly.pool` | `[]` | tag ids; **empty = the tile is hidden** (owner supplies the list, EP-Q17) |
| `ranks.<group>.kit-size` | `hatchling S, fledgling S, firewing M, flamecrest M, sunfeather L, pyrelord L, immortal XL` | **display only** (the shop preview); the real size comes from `skyblock.rank.kit.size.<n>` in ranks.txt; `RankConsistencyTest` keeps them equal |
| `ranks.<group>.ah-slots`, `.profile-slots`, `.loadout-slots` | the numbers of section 5 | display only, kept equal to ranks.txt by the same test |
| `ranks.<group>.highlights` | hatchling `[Weekly kit S, /hat, 7 AH slots]`; fledgling `[/craft, Join message, 8 AH slots]`; firewing `[/ec, /nick, +1 profile, 9 AH slots]`; flamecrest `[Island fly, 4 loadouts, 10 AH slots]`; sunfeather `[Nick colours, Kit L, 12 AH slots]`; pyrelord `[+1 profile, 5 loadouts, 14 AH slots]`; immortal `[Hex nicks, Kit XL, 16 AH slots]` | short words the credit shop prints on the rank tile (joined with `, `, at most 36 characters; `RankHighlightsFitTest`) |
Version bump `PerksConfig.version()` to 2 (new keys are added by the framework; nothing renamed).

**E4. `PerksModule.java`**: register `AshbornAuto` (listener + repeating task, stopped in `disable`), `RankRewards` (command `/rankrewards`, no aliases), no published service. Existing `/freerank` registration unchanged. Migrations: `V003__rank_rewards.sql` (H2 and MariaDB valid, `IF NOT EXISTS`):
```sql
-- Rank rewards (card F2, RANKS-V2). Weekly kit per profile, monthly cosmetic per account. Times in epoch milliseconds.
CREATE TABLE IF NOT EXISTS perks_rank_kit (
  profile_key BINARY(16) NOT NULL,
  week_key VARCHAR(10) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  kit_size VARCHAR(2) NOT NULL,
  state VARCHAR(10) NOT NULL,
  claimed_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, week_key)
);
CREATE INDEX IF NOT EXISTS idx_perks_rank_kit_state ON perks_rank_kit (state, claimed_at);
CREATE TABLE IF NOT EXISTS perks_rank_monthly (
  player_uuid BINARY(16) NOT NULL,
  month_key VARCHAR(7) NOT NULL,
  cosmetic_id VARCHAR(64) NOT NULL,
  claimed_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, month_key)
);
```
**E5. Messages** (`plugin/src/main/resources/messages/perks.yml`, same style as the existing `freerank:` block; English only):
```
freerank:
  claimed: "<prefix><c:success><sym:check></c:success> <c:text>You're now <c:value>Ashborn</c:value><c:text>! Pick your tag."
  auto-claimed: "<prefix><c:success><sym:check></c:success> <c:text>You're now <c:value>Ashborn</c:value><c:text>. <click:run_command:'/freerank'><hover:show_text:'<c:text>Pick your free tag'><c:action>[Pick your tag]</c:action></hover></click>"
  need-tutorial: "<prefix><c:error><sym:cross></c:error> <c:text>Finish the tutorial first. <click:run_command:'/tutorial'><c:action>[Tutorial]</c:action></click>"
rank-rewards:
  title: "Rank rewards"                       # plain crumb; plaque (title.short) "REWARDS"
  summary:
    name: "<c:value><rank></c:value>"
    lore: ["<c:text>Perks of your rank."]
  weekly:
    name: "<area:reward>Weekly Kit</area:reward>"
    lore-ready: ["<c:text>Food, blocks, some fuel.", "<c:text>Bound to this profile.", "<c:bar>|</c:bar> <c:text>Size: <c:value><size>", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to claim"]
    lore-claimed: ["<c:text>Food, blocks, some fuel.", "<c:text>Bound to this profile.", "<c:bar>|</c:bar> <c:text>Next kit: <c:value>Monday", "", "<c:muted>✔ Claimed this week"]
  monthly:
    name: "<area:reward>Monthly Tag</area:reward>"
    lore-ready: ["<c:text>One new tag every month.", "<c:text>Yours for good.", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to claim"]
    lore-claimed: ["<c:text>One new tag every month.", "<c:bar>|</c:bar> <c:text>Claimed for <c:value><month>", "", "<c:muted>✔ Claimed this month"]
    lore-nothing: ["<c:text>Nothing new this month."]
  kit-claimed: "<prefix><c:success><sym:check></c:success> <c:text>Weekly kit claimed: <c:value><items></c:value><c:text>. What didn't fit is in <c:action>/box</c:action>."
  kit-already: "<prefix><c:error><sym:cross></c:error> <c:text>This profile already claimed this week's kit. The next one comes on Monday."
  kit-none: "<prefix><c:error><sym:cross></c:error> <c:text>Your rank has no weekly kit. See <c:action>/credit shop</c:action>."
  kit-box-full: "<prefix><c:error><sym:cross></c:error> <c:text>Your <c:action>/box</c:action> <c:text>is full. Empty it first, nothing was used up."
  monthly-claimed: "<prefix><c:success><sym:check></c:success> <c:text>New tag for <c:value><month></c:value><c:text>: <tag>"
  monthly-already: "<prefix><c:error><sym:cross></c:error> <c:text>You already took this month's tag."
  failed: "<prefix><c:error><sym:cross></c:error> <c:text>Something went wrong. Nothing was used up, try again."
```
Tile lines follow I9/FORMAT §4 (name in the area colour, one grey sentence, one `» CLICK` line last, at most 36 characters per line). No number or colour is hard-coded; `freerank.claimed` text changes from `Ember` to `Ashborn`.

### Step F: menus (`modules/menus/src/main/resources/menu.yml`, `.../messages/navigation.yml`)
`you:` section: add `rank-rewards: {icon: chest, command: "rankrewards", service: RankRewards? }` -> use `permission: skyblock.rank.kit` and `show-locked: false` (a player without a donor rank never sees it). Because the module publishes no service, omit `service:`. Texts: `navigation.yml you.buttons.rank-rewards` name "Rank Rewards", one sentence "Your weekly kit and monthly cosmetic.", action "OPEN". The `you` menu has room (nine buttons today in a 4-row panel; the layout code centres up to 14). If the live `menu.yml` on the VPS does not pick up new default entries after the deploy, add the line by hand through the admin config (run sheet step 6).

## 5. Perks table: what is built "now" and what is "stub"
Numbers are the ones in ranks.txt (enforcement) and `perks.yml ranks.<group>.*` (display). The highest value among held groups wins (a won rank never lowers a bought one). `-` = base.

| Perk | New | Ashb. | Hatch. | Fledg. | Firew. | Flamec. | Sunf. | Pyre. | Immo. | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| Rank word + colour | yes | yes | yes | yes | yes | yes | yes | yes | gradient bold | **now** |
| One free tag (pick 1 of 3) | - | yes | yes | yes | yes | yes | yes | yes | yes | now (`/freerank`, exists) |
| AH listing slots (base 5) | 5 | 6 | 7 | 8 | 9 | 10 | 12 | 14 | 16 | **now** (nodes + ranks.txt) |
| Free profile slot | - | - | - | - | slot 2 | slot 2 | slot 2 | slot 3 | slot 3 | **now** (nodes exist, were ungranted) |
| `/hat` | - | - | yes | yes | yes | yes | yes | yes | yes | now |
| `/craft`, join/leave message | - | - | - | yes | yes | yes | yes | yes | yes | now |
| `/ec`, plain `/nick` | - | - | - | - | yes | yes | yes | yes | yes | now |
| `&` nick colours | - | - | - | - | - | - | yes | yes | yes | now |
| hex and gradient nick | - | - | - | - | - | - | - | - | yes | now |
| Island fly | - | - | - | - | - | yes | yes | yes | yes | now (also earned by the Wings mastery) |
| Loadout slots (base 2) | 2 | 2 | 2 | 3 | 3 | 4 | 4 | 5 | 5 | now (nodes 3 to 5 exist) |
| Immortal badge colour picker | - | - | - | - | - | - | - | - | yes | now (exists) |
| Weekly bound kit S/M/L/XL | - | - | S | S | M | M | L | L | XL | **now** (new, section E2) |
| Monthly cosmetic (one tag) | - | - | yes | yes | yes | yes | yes | yes | yes | **now**; tile hidden until `monthly.pool` has tags |
| `/pv` vaults per profile | - | - | 1 | 1 | 2 | 2 | 3 | 3 | 4 | **stub**: not built (own card, F2 says new feature); no node, no grant, not shown in the shop |
| `skyblock.join.full` (join a full server) | - | - | - | - | yes | yes | yes | yes | yes | **stub**: not built; no node, no grant |
| Border colours, particle trails | - | - | - | 1 | 1 | 2 | 2+trail | 3+trail | 4+2 trails | **stub**: cosmetics not built |
| Bigger name-colour picker (6/12 colours) | - | - | - | - | - | 6 | 6 | 12 | 12+gradients | **stub**: only Immortal has the picker today |
| Paid sell % / skill XP % | none, ever (owner, R3-7) | | | | | | | | | **rejected** |
| `/trash` | admin and owner only | | | | | | | | | never a donor perk |
Lockdown bypass: every weight above 0 (Ashborn and up) already skips lockdown.

## 6. Commands, tab completion, permissions
| Command | What | Permission | Tab completion |
|---|---|---|---|
| `/freerank` | claim (if not yet automatic) and pick the free tag | `skyblock.perks.freerank` (default true, exists) | no arguments |
| `/rankrewards` | Rank rewards screen | `skyblock.rank.kit` (new) | no arguments; the command is a menu opener |
| `/rank preview <group> [name]` | preview a look (exists) | `skyblock.rank.preview` | groups from `ranks.looks` (exists; now 17 ids) |
New nodes (declared in `perks.yml`): `skyblock.rank.kit`, `skyblock.rank.monthly`, `skyblock.rank.kit.size.1` to `.4`. New auction nodes `.9 .12 .14 .16` (auction.yml). Grants only in `server/permissions/ranks.txt` (section D2). No new staff command in this spec (staff change ranks with LuckPerms; the owner hands out staff ranks, principle 8).
`docs/commands/perks.md`: add `/rankrewards`, rewrite the `/freerank` row (automatic, `ember` = Ashborn), refresh the intro ("donor ranks receive them with ranks.txt"); `docs/commands/chat.md`: format strings and `ranks.name-color`; then `./gradlew commandsIndex`.

## 7. Exact format strings and the classes that build them
| Surface | Result (Java, example) | Built by |
|---|---|---|
| Tab | `OWNER Pouched` (OWNER bold yellow, space, name white); donor `Firewing Steve` (word coral, name white); AFK/tag parts as today | `Display.tabEntry` with `format.tab` = `<rank><name><tag><afk>`; `<rank>` = `Display.tabRank` = `Badges.render` (PLAIN) + space; name = `Display.name(player, rank.color())` (white via `RankBadges.nameColor`) |
| Chat | `OWNER Pouched »» message` (gray `»»`, white text) | `ChatModule.onChatRender` with `format.chat` = `<title><rank><name><tag> <gray>»»</gray> <white><message>`; `<rank>` per viewer = `Display.rank(viewer, player)` (Bedrock gets the legacy codes) |
| Nameplate | rank word as team prefix, white name | `TabService.apply` -> `team.prefix(Display.rankFor(...))`, `team.color(Display.teamColor(rank))` = WHITE |
| `/list` | one line per rank group, heading = the rank word, names white | `SocialModule.list()` (`BadgeService.badge`, `nameColor`) |
| Sidebar `\| You` | rank word and white name | `HudService.rankComponent` / `values` |
| Hover card, join line, `rank_color` placeholder | unchanged (rank colour) | |
Tab order = team name `pxs<999-weight><name>` (`TabService.teamName`): OWNER, ADMIN, MOD, HELPER, BUILDER, Immortal, Firecrown, Pyrelord, Sunfeather, Flameheart, Flamecrest, Firewing, Torchbearer, Fledgling, Hatchling, Ashborn, New; A to Z inside a rank. A staff member who also bought a rank shows the staff word (higher weight).
Bedrock: `Badges.bedrock` writes `&e&lOWNER` (legacy codes, no brackets); the trailing space is a separate component; `<gray>`/`<white>` become `&7`/`&f`.

## 8. VPS run sheet (owner allowed: deploy key, tmux; restore what you change)
Run as `minecraft` on the VPS. Helper:
```bash
lpc() { tmux send-keys -t skyblock -l "$1"; tmux send-keys -t skyblock Enter; sleep 0.6; }
LOG=/opt/skyblock/server/logs/latest.log
CFG=/opt/skyblock/server/plugins/PhoenixSkyblock
```
1. **Snapshot before.** `lpc "lp listgroups"`; for each of `default ember builder helper mod admin owner` run `lpc "lp group $g info"`, `meta info`, `parent info`; `lpc "lp user Pouched info"`; `cp $CFG/chat.yml $CFG/chat.yml.bak-ranks-v2`; `cp $CFG/perks.yml $CFG/perks.yml.bak-ranks-v2`; `cp /opt/skyblock/server/permissions/.applied.sha /tmp/applied.sha.before`. Save the LuckPerms export for rollback: `lpc "lp export /tmp/lp-before-ranks-v2"` (LuckPerms writes `/opt/skyblock/server/plugins/LuckPerms/...` or the given file name; note the printed path).
2. **Deploy** with `server/scripts/deploy.sh`; wait for `== Released.`; `md5sum` the jar. The restart runs `apply-permissions.sh` (hash changed) which applies `base.txt` then every module file and `ranks.txt` (about 420 commands: 309 today plus about 110 new; at 0.2 s each about 85 s; wait for `Applied N permission commands`).
3. **Live `chat.yml`.** The version bump upgrades an untouched file by itself: `grep -n "default of 'ranks.looks' changed" $LOG` shows the log line. Verify: `grep -n -E "^\s*-\s*(default|ember|owner|mod|helper|builder|immortal)=" $CFG/chat.yml` must show `owner=#FFFF55|&e&l`, `mod=#55FFFF|&b&l`, `ember=#AAAAAA|&7`, `default=#555555|&8`. If the old lines are still there (somebody edited the list): stop, show the lines to the owner and replace the whole list by hand (admin config editor or `$EDITOR`), then `lpc "skyblock reload chat"`. Same check for `format.chat`: `grep -n "format:" -A6 $CFG/chat.yml` must show `<gray>»»</gray> <white><message>`; an edited format is left alone (tell the owner).
4. **LuckPerms verification (console).**
```bash
lpc "lp listgroups"      # 17 groups
for g in default ember hatchling fledgling firewing flamecrest sunfeather pyrelord immortal torchbearer flameheart firecrown builder helper mod admin owner; do lpc "lp group $g info"; lpc "lp group $g meta info"; lpc "lp group $g parent info"; done
for g in default ember hatchling immortal firecrown owner; do lpc "rank preview $g"; done
tail -n 400 $LOG | sed 's/\x1b\[[0-9;]*m//g' > /tmp/ranks-after.txt
```
Expected: weights and parents exactly as section 3; exactly ONE prefix per group, priority = weight, no old priority (5, 10 on builder/helper old, 20, 30, 100, 1) left (`ember`: prefix at 10 only; `helper`: prefix at 700 only; check the old helper prefix at priority 10 is gone: `lp group helper meta info`); `lp user Pouched parent info` still owner; no `Unknown group` errors in the log (they would mean a module file granted before ranks.txt created the group).
5. **Test accounts** (never the owner's own): create `RankTest1`, `RankTest2` as the owner's alts or a Bedrock tester. `lp user <test> parent set <group>` for each group in turn, look at tab, chat, nameplate and `/list` on Java and a Bedrock phone, then `lp user <test> parent set default`. Check tab order with several test accounts at once.
6. **Menu button.** `/menu` -> You as a donor test account shows Rank Rewards; as a normal player it is absent. If absent for the donor: add `rank-rewards: {icon: chest, command: "rankrewards", permission: skyblock.rank.kit, show-locked: false}` to `$CFG/menu.yml` under `you:` and `lpc "skyblock reload menus"`.
7. **Ashborn check** with a fresh account: before the tutorial ends the word is `New` dark grey; after the tutorial and 30 minutes the chat line `You're now Ashborn` appears and `lp user <test> parent info` lists `ember`. To shorten: temporarily set `freerank.min-playtime-minutes: 1` in `$CFG/perks.yml`, `skyblock reload perks`, **then restore 30 and reload**.
8. **Restore plan.** Looks and formats are config: `cp chat.yml.bak-ranks-v2 chat.yml`, `skyblock reload chat`. LuckPerms: `git revert` the permissions commit, redeploy (base.txt re-applies the old weights/prefixes; the new groups stay but nobody holds them; remove with `lp deletegroup <id>` only if the owner asks) or `lp import` the export of step 1. Player data is never changed by this release except `perks_freerank` rows (kept, harmless) and the two new tables.

## 9. Safety, abuse and edge cases
- **Rank spoof:** a nickname cannot imitate a rank word (existing reserved list in `perks.yml nick.reserved`: owner, admin, staff, moderator, helper; add `mod`, `builder`, `ashborn`, `hatchling`, `fledgling`, `firewing`, `flamecrest`, `sunfeather`, `pyrelord`, `immortal`, `torchbearer`, `flameheart`, `firecrown`). `Badges.cleanName` already strips `&`, `§` and private-use characters from display names.
- **Double Ashborn:** primary key on `perks_freerank`; `addGroup` returns false when present (no error).
- **LuckPerms down at claim time:** claim row undone, retry (max 5 per session), no player-visible error except nothing happening; one console warning.
- **Weekly kit spam/double click:** primary key (profile, week); `busy` set per player for the in-flight claim (as `FreeRankService`); a full inventory goes to `/box`; a full box at the hard cap (`ItemBox.refuses`) refuses the claim before the row is written (message `kit-box-full`, nothing used).
- **Alt farming kits:** kits are bound to the profile; a donor with 3 profiles gets 3 kits (decided, per profile); donor ranks are paid, low risk.
- **Rank removal:** a chargeback removes the group with `RankProvider.removeGroup` (credit shop spec); the weekly kit already claimed stays (bound food/blocks), no claw-back.
- **Won ranks (seasons, I5):** only the groups exist now; the season service that adds and removes them is built with I5 (not in this spec). They are not purchasable and not shown in the shop.
- **Tab for 300 players:** no new per-tick work; weights only change team names; the name colour is one cached `TextColor`.

## 10. Messages for the rank surfaces
`messages/chat.yml`: `rank-preview.line` (see B5). No other text changes: the rank word comes from the LuckPerms display name, so renaming a rank is `lp group <id> setdisplayname <Word>` plus the `ranks.looks` line.

## 11. Tests to write (names only; the coder writes them, does NOT run them unless told; `compileTestJava` must pass)
Update existing: `common/.../rank/BadgesTest` (expects `[Hatchling]` today: now `Hatchling`; hex of owner/mod/helper/builder per section 3; bold true for staff, immortal and firecrown; Bedrock text `&e&lOWNER`), `modules/chat/.../TabPacingTest` (team colour test), `modules/chat/.../RankBadgeKeyTest`, `ChatLogicTest` if it asserts the old chat format.
New:
- `common/.../rank/RankLooksTest`: `defaultsParseSeventeenLines`, `staffLooksAreExactClassicHex`, `freeAndNewLooksAreExactClassicHex`, `boldOnlyForStaffImmortalFirecrown`, `oldDefaultsV1StillParse`.
- `plugin/.../RankConsistencyTest` (reads `server/permissions/base.txt` + `ranks.txt` from the repo root like `PermissionFilesTest.root()`): `everyLookGroupHasLuckPermsGroup`, `weightsMatchSection3AndStayBelow1000`, `weightOrderStaffAboveWonAboveDonorAboveFreeAboveDefault` (won rank weight is between the tiers it copies), `displayNamesMatchTheTable` (staff caps), `metaColorEqualsLookFirstHex`, `prefixFirstCodeEqualsLookFirstBedrockCode`, `prefixPriorityEqualsWeight`, `parentsFormTheDonorChain`, `bedrockCodeNearLookHex` (Lab distance code to hex at most 60; exact for staff, default, ember, firecrown), `singleColourLooksAtLeast30Apart` (CIE Lab, all single-colour looks incl. staff), `bedrockCodesUniqueAmongNonStaffNormalLooks`, `perkNodesGrantedOnlyInRanksTxt` (no module file other than ranks.txt grants to a donor/won/free group), `ahSlotsStrictlyIncrease`, `perksYmlDisplayNumbersEqualRanksTxt` (kit size, AH slots, profile slots, loadout slots).
- `modules/chat/.../TabTeamNameTest`: `staffSortsBeforeDonorsBeforeAshbornBeforeNew`, `weightsAbove999AreClamped`.
- `modules/perks/.../AshbornRulesTest`: `dueOnlyWhenTutorialDoneAnd30Minutes`, `notDueWhenAlreadyClaimed`, `notDueWithoutLuckPerms`, `tutorialModuleOffCountsAsDone`; `AshbornNoDowngradeTest` (source scan for `removeGroup`, `parent set`, `setGroup`); `FreeRankRulesTest` updated for `TUTORIAL`.
- `modules/perks/.../RankRewardsRulesTest`: `weekKeyIsoOslo`, `mondayRollover`, `kitSizeIsHighestNode`, `kitConfigRefusesToolsAndSpawners`, `monthlyPicksFirstUnowned`, `monthlyEmptyPoolBurnsNothing`; `RankHighlightsFitTest` (every highlights line joined is at most 36 characters), `RankRewardsStoreTest` on H2: `secondClaimSameWeekFails`, `differentProfilesClaimSeparately`, `claimingRowIsRedeliveredOnce`.
- `modules/auction/.../LogicTest` extend: `slotsHighestNodeWinsWithNewNodes` (9, 12, 14, 16, and 16 > 15 nodes order).

## 12. Manual test checklist (Java + Bedrock; the owner or the lead runs it on the VPS, section 8)
Java: 1) tab shows `OWNER Pouched` bold yellow word + white name, then ADMIN, MOD, HELPER, BUILDER, donors high to low, Ashborn, New; 2) chat line `OWNER Pouched »» hi` with gray arrows and white text; 3) nameplate over the head shows the rank word and a white name; 4) `/list` groups by rank, white names; 5) sidebar name white; 6) a nickname `~Steve` keeps the rank word of the real rank; 7) `/rank preview owner|firewing|immortal|firecrown|default`; 8) a new account: `New`, tutorial, 30 minutes (test shortcut in run sheet), `You're now Ashborn`, grey word; 9) `/freerank` after the auto grant opens the tag picker, second pick refused; 10) donor test account: `/rankrewards` (kit claim, second claim refused, next Monday text), kit items are bound (cannot be auctioned or traded), full inventory sends the rest to `/box`; 11) monthly tile hidden while the pool is empty, appears and grants after the owner fills `rank-rewards.monthly.pool`; 12) Firewing test account: `/ah` shows 9 slots, `/profiles` slot 2 free; Pyrelord: slot 3 free, 14 slots; 13) `/hat /craft /ec /nick` per rank as in section 5; 14) a staff account that is also Hatchling shows the staff word; 15) restart: nothing changes; second claim paths do nothing.
Bedrock (Geyser phone): the same words with legacy colours (bold staff), Hatchling shows yellow (same code as OWNER but not bold and not caps), name white, `»»` gray, `/rankrewards` is click-only, no typing needed anywhere; the Rank Rewards button is in You.

## 13. Ledger lines to append (done by the planner in `LEDGER.md`; ids `F2-R2-nn`)
See `LEDGER.md` end: names/prices, format, Ashborn rule, perks, bracket and name-colour reversal of G8-R1-01.

## 14. Risks and rollback
| Risk | Level | Mitigation / rollback |
|---|---|---|
| `chat.yml` of the VPS holds edited looks or format (not upgraded) | low | run sheet step 3 checks and fixes by hand; restore from `.bak-ranks-v2` |
| Weight renumbering confuses an old prefix (two prefixes on one group) | medium | every prefix line removes the old and the new priority first; step 4 checks one prefix per group |
| A module file granted to a missing donor group | low | rule + `perkNodesGrantedOnlyInRanksTxt` test; the only offender (`chat.txt`) is fixed |
| Mass Ashborn promotion on deploy | low | batched (5 per 30 s), idempotent, claim row first |
| Bedrock sees Hatchling in OWNER's yellow | cosmetic | owner phone check; one line in `ranks.looks` (`hatchling=#E3BE7A|&6`) changes it, but `&6` is then shared with Fledgling: prefer re-picking both together |
| Kit crash window doubles once | low | audited `rank_kit_redeliver`; rejected alternative: lose the kit |
| The lead reads "donor perks" as progress | policy | list in section 5; no paid sell % or XP %; Mojang note logged |
Rollback is config + permissions only (section 8 step 8); the two new tables stay empty/unused.

## 15. Questions the planner could not settle with a safe default (owner may veto; none blocks the build)
1. **No brackets for donors and free ranks** (D10): owner only showed staff. Default: no brackets anywhere.
2. **Pyrelord 9,000 instead of 8,800** (D4): rounder, +200 credits; ask only if the owner wants the exact 8,800.
3. **Hatchling on Bedrock is yellow** (shares `&e` with OWNER, not bold): needs a phone look (D14).
4. **Kit contents and sizes** are placeholders (EP-Q17: "bound weekly kit sized S to XL"); the owner tunes `perks.yml rank-rewards.kits.*`.
5. **Monthly cosmetic pool** is empty until the owner names the tags (EP-Q17: "a list you write"); the tile stays hidden.
6. EP-Q13 had no answer: default applied (a), no paid progress. EP-Q24/EP-Q26/EP-Q27 do not touch ranks.
