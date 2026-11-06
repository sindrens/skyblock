# Tab list

**Status:** needs owner input

You asked for: the tab list shows each player's rank, with a cleaner look.

## What you already decided
- **UX1b (30 Oct):** remove the `/menu · /help · /discord · /store` footer line; nicer TPS and `play.skybornmc.net`; the lines beside SKYBORN match the colors.
- **FIXES1:** tab ranks fixed; the title stays in chat, not in tab.
- **303:** you said **No** to hiding the dot in Bedrock names. Bedrock names keep their dot.
- **RTA-2 / RTA-3:** donor ranks Hatchling, Fledgling, Firewing, Flamecrest, Sunfeather, Pyrelord, Immortal; free rank renamed from Ember to Ashborn.
- **Principle 7:** a forced resource pack later (rank badges can become small icons).

## What exists in the game now
- **Header:** `SKYBORNMC` with lines left and right, `Phoenix Skyblock`, `12 online · Season 1`.
- **Footer:** `» play.skybornmc.net «`. Staff also see `TPS 20.0 · Ping 34 ms`.
- **Each name:** `[rank] name (tag) [AFK]` (`chat.yml → format.tab`). Name in the rank color. Nicknames start with `~`.
- **Sorting:** by rank weight, then A to Z (one scoreboard team per player).
- **Performance:** each player has their own scoreboard, so every board holds a team for every player. At 300 players this is the biggest tab cost (PERFORMANCE.md P-07). The fix there: sort with `setPlayerListOrder` and keep nametag teams only for nearby players.
- **Limits:** Java shows at most 80 names (4 columns of 20). With 300 online, most players are not visible. Bedrock shows a simple scrolling list and (to verify) no header or footer.

## The problem / what to decide
1. Players with no rank show only a name. You want everyone to show a rank.
2. With 300 players, only 80 fit. Who is shown first?
3. The header and footer show little that helps the player.
4. Staff need more info (vanished staff, TPS, MSPT), players need less.
5. Bedrock shows names only.

## Our proposal
**Each entry:** `RANK name (tag)`
- Rank as a short word in its color: `MOD`, `ADMIN`, `FIREWING`, `ASHBORN`. Later a small badge icon from the pack.
- Every player has a rank to show: players without a bought rank show **Ashborn** (the free rank). Give Ashborn automatically when the tutorial is finished (the 30-minute claim no longer fits after answer 131).
- AFK players: name in gray + `AFK`.
- Staff in staff mode: no change for players. Vanished staff are hidden from players and shown in gray with `(V)` to staff.

**Sorting (top to bottom):** Staff (Owner, Admin, Mod, Helper, Builder) → won season ranks → donor ranks (high to low) → Ashborn → A to Z. Staff are always visible.

**Header (3 short lines):**
```
SKYBORNMC
Phoenix Skyblock
187 online · Season 1 · Day 12
```
**Footer (per player, 2 short lines):**
```
Island Lv 24 · $1.2M · Ping 34 ms
play.skybornmc.net
```
**Staff footer:** `TPS 20.0 · MSPT 31 · 3 vanished · 2 reports`.

**Bedrock:** names keep rank and color. Nothing important lives only in the header or footer.

**Performance:** use the P-07 fix (list order, nearby nametag teams). Header and footer update at most every 5 s.

## Questions for you
1. Show a rank on every entry, with **Ashborn** for players who have no bought rank? **Suggested: Yes.**
2. Give Ashborn automatically when the tutorial is finished, instead of `/freerank`? **Suggested: Yes** (keep `/freerank` to pick the tag).
3. Rank as a short colored word now (`FIREWING`), and a badge icon once the pack exists? **Suggested: Yes.**
4. Sort: staff → won ranks → donor ranks → Ashborn, then A to Z? **Suggested: Yes.**
5. Header: `SKYBORNMC`, `Phoenix Skyblock`, online + season + day? **Suggested: Yes.**
6. Personal footer with your island level, money and ping? **Suggested: Yes.**
7. Keep tags in tab (`(Phoenix)`) after the name? **Suggested: Yes.**
8. Keep titles out of tab (chat only)? **Suggested: Yes** (as now).
9. Staff footer with TPS, MSPT, vanished count and open reports? **Suggested: Yes.**
10. Show vanished staff to other staff in gray with `(V)`? **Suggested: Yes.**
11. Show island members first for each player (needs per-player sorting, more work)? **Suggested: No** for now. Friends and island members are easier to find in `/is` and the friends menu.
12. Use the P-07 performance fix even if the list order is built a new way? **Suggested: Yes**, as long as it looks the same.

## Your notes (write here)

