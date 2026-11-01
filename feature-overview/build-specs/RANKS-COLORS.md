> **SUPERSEDED on 1 Nov 2026 by `RANKS-V2.md`** (owner answers EP-Q10 to EP-Q17: bold staff words, white names, no brackets, weights 600-999, donor groups). Do not build this file; its colour lines were copied into RANKS-V2 sections 3 and 4.

# Build spec RANKS-COLORS: staff rank colours (small, exact)

Owner request, 31 Oct 2026: staff ranks must be bold and use these colours: **OWNER `&e&l` (yellow), ADMIN `&c&l` (red), MOD `&b&l` (aqua), HELPER `&a&l` (green), BUILDER `&6&l` (gold)**. The LuckPerms groups must exist with the right prefixes and the same colours must show in chat, tab, nameplate and `/list`.

Cards: F2 (Review v2 section "Staff colours"), G8, G1. Ledger/owner answers touched: I7-R1-04 ("base.txt is not changed"; this request overrides that sentence for the five staff groups only), G8-R1-01 (distinct colours; see section 7). Donor ranks are NOT part of this spec (planned in F2 v2).

Size: S. One branch `q/ranks-colors`, two commits (`fix(chat): staff rank looks bold and legacy colours`, `chore(permissions): staff prefixes and colours in base.txt`). `./gradlew build` must pass. Never start a test server.

## 1. What players see today (verified in the code, branch q/gui-phase1 = deployed)

Chat, tab, nameplate (scoreboard team), `/list` and the sidebar all draw the rank as a **badge**: `[` + LuckPerms group **display name** + `]`, coloured by `chat.yml ranks.looks.<group id>` (`RankLooks`, `Badges.render`). The name colour is the same look colour (`RankProvider.colorSource`). The LuckPerms **prefix** is only read as a cache fingerprint and for other plugins; the LuckPerms `color` meta is only the fallback for a group with no look line.

| Group | LuckPerms prefix today (`base.txt`) | LuckPerms `color` meta today | `ranks.looks` today (chat.yml, code default) | Java badge today |
|---|---|---|---|---|
| owner | `&4&lOWNER &4` (dark red) | `#C21F2B` | `owner=#FFC21A|&e&l` | `[Owner]` amber, NOT bold |
| admin | `&c&lADMIN &c` | `#FF3D2E` | `admin=#FF6B6B|&c&l` | `[Admin]` pale red, NOT bold |
| mod | `&6&lMOD &6` (gold) | `#FF8A1F` | `mod=#6CC4FF|&9&l` (Bedrock is dark blue) | `[Moderator]` light blue, NOT bold |
| helper | `&b&lHELPER &b` (aqua) | `#63B8FF` | `helper=#7CEB8A|&a&l` | `[Helper]` light green, NOT bold |
| builder | `&e&lBUILDER &e` (yellow) | `#FFD23F` | `builder=#FF9A3D|&6&l` | `[Builder]` orange, NOT bold |

Findings:
1. The two sources disagree for four of five groups (owner dark red vs amber, mod gold vs blue, helper aqua vs green, builder yellow vs orange). The owner sees the `chat.yml` look in game and the LuckPerms prefix in other places, hence "fix the colours".
2. **Bold only exists on Bedrock.** `RankLooks.Look` holds colours only; `&l` lives in the Bedrock legacy string and `Badges.simple` (Java) never makes text bold. So no staff badge is bold on Java today.
3. Display names are `Owner`, `Admin`, `Moderator`, `Helper`, `Builder` (mixed case). The owner's list is caps (`OWNER`, `MOD`).
4. `ranks.looks` is a typed setting in `chat.yml`. The file on the VPS keeps the values it was first written with; changing the code default alone does not change a running server (the config framework adds missing keys only).

## 2. Target look (decided colours, consistent hex)

One hex per legacy code, so Java and Bedrock show the same colour:

| Legacy | Hex | Rank |
|---|---|---|
| `&e` | `#FFFF55` | OWNER |
| `&c` | `#FF5555` | ADMIN |
| `&b` | `#55FFFF` | MOD |
| `&a` | `#55FF55` | HELPER |
| `&6` | `#FFAA00` | BUILDER |

Badge text becomes bold caps: `[OWNER]`, `[ADMIN]`, `[MOD]`, `[HELPER]`, `[BUILDER]` (F2 plan default "staff use bold caps"; the owner may veto in EP-Q14, then keep the mixed-case display names and only skip step 3 of section 3).

## 3. Code change (module chat, common)

1. `RankLooks.DEFAULTS`: replace the five staff lines (keep all 11 other lines exactly):
```
"owner=#FFFF55|&e&l",
"admin=#FF5555|&c&l",
"mod=#55FFFF|&b&l",
"helper=#55FF55|&a&l",
"builder=#FFAA00|&6&l"
```
2. **Bold on Java from the same string.** `RankLooks.Look` gets `boolean bold()`: true when every Bedrock segment contains `&l`. `Badges.simple` applies `TextDecoration.BOLD` (true) to the badge text and to every gradient character when `look.bold()`. `Badges.bedrock` is unchanged. One source of truth (the legacy code), no new config syntax. Side effect: the unheld ranks `immortal` and `firecrown` (their codes contain `&l`) also become bold on Java, which matches their Bedrock look today.
3. Display names (done through LuckPerms, section 4): `OWNER`, `ADMIN`, `MOD`, `HELPER`, `BUILDER`. `Badges.cleanName` already keeps caps.
4. Name colour in chat/tab stays the look colour (no bold name, only the badge is bold).
5. `ChatConfig` desc of `ranks.looks`: add "A '&l' in the Bedrock part makes the Java badge bold too."
6. Tests (pure, no server):
   - `RankLooksTest`: the 16 default lines parse; owner/admin/mod/helper/builder hex and code equal the table above; `bold()` true for those five, immortal and firecrown, false for the rest.
   - `BadgesTest`: Java badge of `owner` has colour `#FFFF55` and BOLD on the whole text; Bedrock text is `&e&l[OWNER]`; a non-bold look has no bold.
   - **New `RankConsistencyTest`** (guards this whole class of bug): reads `server/permissions/base.txt` from the repo; for each group that has a line in `RankLooks.DEFAULTS` AND a `meta set color` line, the hex must be equal (ignoring case), and the legacy colour code in `meta addprefix` must equal the first colour code of the look's Bedrock string. Fails the build on any difference. (Groups without a base.txt line, such as donors, are skipped until F2 v2 adds them.)
7. Docs: `docs/commands/chat.md` (`/rank preview` text unchanged), `CHANGELOG.md`, `feature-overview/features/F2.md` status line stays (the owner locks later).

## 4. `base.txt`: exact lines (server/permissions/base.txt)

Replace the colour block, the display-name block and the prefix block. Weights, inheritance, permissions and the People section stay exactly as they are (weights 0/1/5/10/20/30/100: no migration needed).

```
# --- Rank colors (hex) for our chat/tab/nametags: SAME hex as chat.yml ranks.looks (RankConsistencyTest) ---
lp group default meta set color "#C9C9C9"
lp group builder meta set color "#FFAA00"
lp group helper meta set color "#55FF55"
lp group mod meta set color "#55FFFF"
lp group admin meta set color "#FF5555"
lp group owner meta set color "#FFFF55"

# --- Display names (the badge text) and chat prefixes (Bedrock-safe classic colors) ---
lp group builder setdisplayname BUILDER
lp group helper setdisplayname HELPER
lp group mod setdisplayname MOD
lp group admin setdisplayname ADMIN
lp group owner setdisplayname OWNER
lp group builder meta removeprefix 5
lp group builder meta addprefix 5 "&6&lBUILDER &6"
lp group helper meta removeprefix 10
lp group helper meta addprefix 10 "&a&lHELPER &a"
lp group mod meta removeprefix 20
lp group mod meta addprefix 20 "&b&lMOD &b"
lp group admin meta removeprefix 30
lp group admin meta addprefix 30 "&c&lADMIN &c"
lp group owner meta removeprefix 100
lp group owner meta addprefix 100 "&e&lOWNER &e"
```
Also fix the header comment line "Donor ranks (7, names to be planned...)" only if F2 locks; leave it now. Idempotent: every line is safe to run twice.

## 5. Groups: what exists, what is missing

| Group | Declared in `base.txt` | Needed for this fix | Note |
|---|---|---|---|
| default | yes (weight 0, display name `Ashborn`, colour `#C9C9C9`) | no change | **Finding:** `default` shows the badge `[Ashborn]` for every player with no rank, while the free rank group `ember` shows `[Ember]` (prefix `&7[&6Ember&7] `, no look line, so it draws in the default grey). F2 v2 decides the free rank; do not touch here. |
| ember | yes (weight 1, parent default) | no | the free rank from `/freerank` |
| builder | yes (5) | colour, prefix, display name | parent default is NOT set (builder inherits nothing; admin inherits builder) |
| helper | yes (10, parent default) | same | |
| mod | yes (20, parent helper) | same | |
| admin | yes (30, parents mod and builder) | prefix unchanged | colour hex changes |
| owner | yes (100, parent admin) | same | user `Pouched` is in `owner` |
| 7 donor groups, 3 won groups | **missing** | no | planned in F2 v2; `chat.txt` already grants `lp group immortal permission set skyblock.chat.rankcolor true` to a group that does not exist (the command fails silently today) |

Verify what the VPS really has (it may differ from the repo): `lp listgroups`, section 6 step 1.

## 6. VPS run sheet (deploy key, tmux; the owner allowed console commands, restore what you change)

Run as `minecraft` on the VPS (`ssh -i ~/Development/.skyborn-claude/deploy_key minecraft@<host>`). Console helper:
```bash
lpc() { tmux send-keys -t skyblock -l "$1"; tmux send-keys -t skyblock Enter; sleep 0.6; }
LOG=/opt/skyblock/server/logs/latest.log
```
**Step 1: snapshot before (so it can be restored).**
```bash
lpc "lp listgroups"
for g in default ember builder helper mod admin owner; do lpc "lp group $g info"; lpc "lp group $g meta info"; lpc "lp group $g parent info"; done
lpc "lp user Pouched info"
tail -n 200 $LOG | sed 's/\x1b\[[0-9;]*m//g' > /tmp/ranks-before.txt
cp /opt/skyblock/server/plugins/PhoenixSkyblock/chat.yml /opt/skyblock/server/plugins/PhoenixSkyblock/chat.yml.bak-ranks
```
**Step 2: deploy the build** (`server/scripts/deploy.sh`). It restarts and `apply-permissions.sh` re-applies `base.txt` (the hash changed). Wait for `== Released.`; check the jar with `md5sum` on the VPS.

**Step 3: live `chat.yml`** (the old looks stay in the VPS file). Show the lines first, then replace exactly the five old values (the old text is known):
```bash
F=/opt/skyblock/server/plugins/PhoenixSkyblock/chat.yml
grep -n -E "^\s*-?\s*\"?(owner|admin|mod|helper|builder)=" $F
sed -i \
 -e 's/owner=#FFC21A|&e&l/owner=#FFFF55|\&e\&l/' \
 -e 's/admin=#FF6B6B|&c&l/admin=#FF5555|\&c\&l/' \
 -e 's/mod=#6CC4FF|&9&l/mod=#55FFFF|\&b\&l/' \
 -e 's/helper=#7CEB8A|&a&l/helper=#55FF55|\&a\&l/' \
 -e 's/builder=#FF9A3D|&6&l/builder=#FFAA00|\&6\&l/' $F
grep -n -E "(owner|admin|mod|helper|builder)=" $F
lpc "skyblock reload chat"
```
If the grep in the first command shows nothing (the key was never written, so the code default is live), skip the `sed` and the reload is enough after deploy. If a line differs from the old text above, stop and ask (somebody edited it).

**Step 4: verify (console, then in game).**
```bash
for g in owner admin mod helper builder; do lpc "lp group $g meta info"; done
lpc "lp group owner parent info"; lpc "lp group admin parent info"; lpc "lp user Pouched parent info"
for g in owner admin mod helper builder; do lpc "rank preview $g"; done
tail -n 120 $LOG | sed 's/\x1b\[[0-9;]*m//g'
```
Expected: `lp group owner meta info` lists prefix `&e&lOWNER &e` at priority 100 and `color=#FFFF55`; admin `&c&lADMIN &c` 30 `#FF5555`; mod `&b&lMOD &b` 20 `#55FFFF`; helper `&a&lHELPER &a` 10 `#55FF55`; builder `&6&lBUILDER &6` 5 `#FFAA00`; exactly ONE prefix per group (no old priority left); parents unchanged (admin: mod, builder; owner: admin; mod: helper; helper: default). `rank preview` prints Simple, Java and Bedrock lines with the new look; the owner checks it on a Java client and a Bedrock phone: `[OWNER]` bold yellow in chat, tab (sorted owner, admin, mod, helper, builder, then the rest) and above the head.

Optional with a test account (never the owner's own): `lp user <test> parent set <rank>` for each rank, look in chat/tab, then `lp user <test> parent set default`.

**Step 5: restore plan.** Colours are config: `cp chat.yml.bak-ranks chat.yml` then `skyblock reload chat`; LuckPerms: `git revert` of the base.txt commit and the next deploy re-applies the old lines. Nothing in player data changes.

## 7. Known follow-ups (not in this build)
- **Distinct colours (G8-R1-01):** donor colours must differ from these five (the old `sunfeather #FFD21A` is yellow like OWNER, `hatchling #FF9A4D` is close to BUILDER gold, `pyrelord #FF3B4A` is close to ADMIN red). F2 v2 proposes a new donor palette and the `RankConsistencyTest` extension that also checks every pair of looks has a minimum colour distance. Until donors exist nobody can clash.
- `default` shows `[Ashborn]` and the free group `ember` shows `[Ember]`: asked in F2 v2 (EP-Q15).
- Staff weights stay 5/10/20/30/100 here; F2 v2 proposes a renumbering (staff 600-999, donors 100-160) with a migration of the prefix priorities.
