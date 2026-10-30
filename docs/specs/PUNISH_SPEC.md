> **Source:** owner spec, pasted 2026-10-29, stored verbatim (sections 13 and 14 came as plain text). This is its own phase: **Phase P (Punishment system v2)**, see docs/QUEUE.md. Main-session notes and corrections are in `docs/specs/PUNISH_SPEC_NOTES.md` and override this file where they conflict.

# Spec: `/p <player>` punishment menu + automatic warn system

> **Status:** v1, all owner questions answered, ready to build, 2026-10-29. Written by the owner's request so an AI (or a developer) can build it without guessing.
> **Module:** `modules/moderation` (extends M1.8). **Do not rewrite the module**: extend what exists.
> **Rule from CLAUDE.md:** if something here is unclear, do **not** guess. Add a question to `docs/OPEN_QUESTIONS.md` and use the default written here.

---

## 1. The idea in one paragraph

A staff member types `/p Steve`. One menu opens with everything on one screen. The **left side** has two halves: **top-left = mutes** (toxicity, spam, advertising, ...) and **bottom-left = warns**. The **right side** has two halves too: **top-right = bans** (hacked client, x-ray, macros, ...) and **bottom-right = small stuff** that does not need its own section (kick, bad name/skin/build, history, ...). In the middle is the player's head with a summary (warns, mutes, bans). A **warn counter** is remembered per player. When a player reaches the configured number of warns (**2**), the staff get a message with the suggested mute and **decide** to apply it or dismiss it. **Everything (which punishment is on which slot, ladders, warn limits, messages) is edited in the GitHub files** and ends up on the server through the normal deploy. No code change is needed to change a ladder, add a reason or move a button.

---

## 2. What already exists (do NOT rebuild this)

Read these first:

| File | What it does today |
|---|---|
| `modules/moderation/.../PunishMenus.java` | `/punish <player>` menu: category → reason → confirm. Also `/history` |
| `modules/moderation/.../PunishmentService.java` | Applies ban/mute/warn/kick, sends the warn title + sound, pardons |
| `modules/moderation/.../Templates.java` | Loads `punishments.yml` (categories, reasons, ladders) |
| `modules/moderation/.../PunishmentStore.java` + `db/moderation/V001__punishments.sql` | Database storage of every punishment |
| `server/plugins/PhoenixSkyblock/punishments.yml` | The tracked config: 4 categories, ladders per reason |
| `server/plugins/PhoenixSkyblock/messages/moderation.yml` | All texts |
| `docs/COMMANDS.md` → "Punishments" | Commands and permissions |

Already working: ladders (1st offence = 1st step, 2nd = 2nd step, last step repeats), `-s` silent flag, `-ip` flag, pardons with reason, history, alt detection, length limits per rank, audit log.

**What is new in this spec:**
1. `/p` command (short for `/punish`) with a **single-screen two-sided layout** (section 4).
2. **Configurable menu layout** (which reason sits on which slot) in the YAML.
3. **Warn counter + automatic action** (section 5).
4. **GitHub workflow + validation** so a bad edit can never break the server (section 6).

---

## 3. Commands and permissions

| Command | What it does | Permission |
|---|---|---|
| `/p <player>` | Opens the punishment menu for that player. Alias of `/punish <player>` | `skyblock.staff.punish.gui` (existing) |
| `/p <player> <reason-id>` | Applies that reason directly, using the ladder (same as `/punish <player> <reason>`) | same |
| `/pstats` · `/pnote` · `/ack` · `/mypunishments` | See section 12 | see 12.13 |
| `/warns <player>` | Shows the warn counter and the automatic rules in chat (and a button to open the menu) | `skyblock.staff.punish.history` (existing) |

Rules:
- **Tab completion on everything** (CLAUDE.md non-negotiable): player names (online **and** offline, Bedrock names too), then reason ids for the second argument, then flags `-s` and `-ip`.
- `/p` with no name: show the usage line, never an error stack.
- Staff cannot punish players with `skyblock.staff.punish.exempt` (existing rule, keep it).
- Offline players must work (the menu opens with their stored history).
- The old `/punish` keeps working exactly as before. `/p` is only an additional alias, registered through the existing `CommandSpec` system in `common` so help, tab completion and permissions behave the same.

---

## 4. The menu (single screen, left / middle / right)

### 4.1 Layout

6 rows (54 slots), Bedrock-safe (no click types that Bedrock lacks: **left click only**, no shift-click or number keys).

```
        LEFT SIDE                 MIDDLE                 RIGHT SIDE

 ┌───┬───┬───┬───┬───┬───┬───┬───┬───┐
 │ . │ . │ . │ . │ H │ . │ . │ . │ . │  row 1: H = player head + summary
 ├───┼───┼───┼───┼───┼───┼───┼───┼───┤
 │ M │ M │ M │ M │ | │ B │ B │ B │ B │  row 2   M = MUTES (top-left)
 ├───┼───┼───┼───┼───┼───┼───┼───┼───┤                B = BANS  (top-right)
 │ M │ M │ M │ M │ | │ B │ B │ B │ B │  row 3
 ├───┼───┼───┼───┼───┼───┼───┼───┼───┤
 │ W │ W │ W │ W │ C │ S │ S │ S │ S │  row 4   W = WARNS (bottom-left)
 ├───┼───┼───┼───┼───┼───┼───┼───┼───┤                C = warn counter
 │ W │ W │ W │ W │ | │ S │ S │ S │ S │  row 5   S = SMALL STUFF (bottom-right)
 ├───┼───┼───┼───┼───┼───┼───┼───┼───┤
 │ . │ K │ . │ Hi│ X │ . │ . │ . │ . │  row 6: K = kick, Hi = history, X = close
 └───┴───┴───┴───┴───┴───┴───┴───┴───┘
```


(`|` = divider glass pane, `.` = filler glass pane, both configurable.)

The four quarters (owner decision, 2026-10-29):

| Quarter | Rows | Content |
|---|---|---|
| **Top-left: MUTES** | rows 2-3, columns 1-4 (slots 9-12, 18-21) | Chat reasons whose ladder is mute-based (toxicity, spam, advertising, hate, personal-info, disrespect-staff) |
| **Bottom-left: WARNS** | rows 4-5, columns 1-4 (slots 27-30, 36-39) | Plain **warn buttons** (section 7 `warn-buttons`): each applies one warn with a preset reason text |
| **Top-right: BANS** | rows 2-3, columns 6-9 (slots 14-17, 23-26) | Cheating and other ban-based reasons (hacking, xray, macro, alt-abuse, exploiting, rmt, scamming, griefing) |
| **Bottom-right: SMALL STUFF** | rows 4-5, columns 6-9 (slots 32-35, 41-44) | Things that do not need their own section: kick, afk-farming, inappropriate name/skin/build, anything else minor |

Which reason sits where is only a default: it is all in the YAML.
- **Middle**: head with summary (name, total bans/mutes/warns/kicks, active mute/ban with remaining time), the **warn counter** (section 5.4), divider panes.
- **Bottom row**: Kick, History (opens the existing history menu), Close. Warns are not here: they are the bottom-left quarter.

### 4.2 What one reason button shows

Every reason item shows, using the lore template of `docs/UI_STYLE.md`:
- Name and icon from the YAML.
- **"This is offence number N"** (counted from the player's history for that reason, as the existing `previous()` does).
- **"Next step: Mute 1d"** (the step of the ladder that will be applied now) and, dimmed, the step after it.
- Click hint: "Click to apply".

### 4.3 Click flow

1. Click a reason → the punishment is applied **immediately, no confirm menu** (owner decision). The code for the confirm step stays available behind `menu.confirm: true` but the default is `false`.
2. The punishment is applied through `PunishmentService` (same code path as today, so audit log, silent flag, length limits and exempt checks all still apply). Because there is no confirm click, staff can undo a mistake with `/unmute`, `/unban`, `/unwarn` (reason required, existing commands).
3. After applying, the menu **stays open** and refreshes the head summary and the warn counter, so a staff member can apply a second punishment (for example a ban for cheating plus nothing else) without retyping. A "Close" button closes it.

### 4.4 Where the layout comes from

Not from code. From the YAML (section 7). If a reason is not placed in the layout, it is **still reachable** through a "More reasons" button that opens the old category menu, so nothing configured is ever lost.

---

## 5. Warn system with automatic action

### 5.1 Behaviour

- Every warn (from the menu, from `/warn`, or from a ladder step `warn`) increases the player's **warn counter**.
- Only **active** warns count. A warn is active if it was not removed with `/unwarn` and has not expired (`warn-expire`: **30d**, owner decision; `perm` = never).
- After a warn is saved, the system checks the **warn rules** (section 7).
- Owner's rules:
  1. **1st warn:** only the warning, nothing else.
  2. **2nd warn (2 active warns): suggested mute of 1 hour.**
  3. **Repeat within a week:** after such a trigger, the counter starts from **0** again (section 5.5). If the player then gets **another warn or any mute within 7 days** of that trigger, the suggestion is a **mute of 1 day** (not 1 hour again). After the 7 days have passed without anything, the next trigger is the 1-hour mute again.
  4. There is **no 4-warn ban** by default (the owner dropped it). The YAML can still hold one if wanted later.
- All numbers come from the YAML.

### 5.2 What the automatic trigger does

Because the mode is `notify` (section 5.3), nothing is punished until staff decide:

1. Sends a **staff notification** to everyone with `skyblock.staff.notify`:
   `[Punish] Steve reached 2 warnings. Suggested: mute 1h.  [Apply]  [Dismiss]  [Open /p]`
   (clickable chat components; `[Open /p]` runs `/p Steve`).
2. **[Apply]** creates a normal punishment with `by = <the staff member who clicked>` and reason text like `Automatic rule: 2 warnings`, marked `source = auto:warn-rule:2` in the database. It shows in `/history` and the audit log. The player gets the usual mute message plus the reason ("You received 2 warnings, so you are muted for 1h.").
3. **[Dismiss]** applies nothing ("not too bad"). It is logged in the audit log (category `punishment`, who dismissed, which rule). The counter still restarts (section 5.5), so the same trigger does not nag on every later warn.
4. The first staff member to click wins; the other buttons then say "already handled by <name>" (atomic, see 5.5).
5. A trigger nobody handles stays in a small **pending list**, shown on the head summary in `/p` ("Pending: suggested mute 1h [Apply]") and in `/warns <player>`, so it is not lost when staff were offline. Pending triggers expire after `pending-expire` (default 24h).
6. Staff can revoke an applied mute any time with `/unmute <player> <reason>` (existing).

### 5.3 Modes (one setting)

`warn-rules.mode` in the YAML:
- `notify` (**default, owner decision**): staff gets the message with **[Apply] / [Dismiss]**, as in 5.2.
- `auto`: the punishment is applied immediately with `by = "System (auto)"` and staff is only informed. Kept as an option, **not** used by default.

### 5.4 Warn counter display

- In the menu middle: a `PAPER`/`YELLOW_CONCRETE` item "Warnings: **1 / 2**" with lore: next automatic action ("At 2 warnings: mute 1h"), and the dates when the active warns expire.
- `/warns <player>` shows the same in chat.
- The counter is **calculated from the stored punishments** (type WARN, not removed, not expired). Do **not** keep a separate number that can get out of sync.

### 5.5 Edge cases (must be handled and tested)

| Case | Required behaviour |
|---|---|
| Two staff warn the same player at the same moment | The rule fires **once** (do the check inside the same transaction/atomic step as saving the warn) |
| Warn count jumps past a rule (e.g. from 1 to 3 through an import) | Fire the highest rule `at <= count` that has **not fired since the last reset** |
| Does the counter restart after a trigger? | **Yes (owner decision)**, `reset-after-trigger: true`: when a trigger is applied **or dismissed**, the warns counted so far no longer count toward the next rule (the counter shows 0). They stay in `/history`. With `false` the counter keeps growing |
| "Another warn or mute within a week" (rule 3) | Counted from the time of the previous trigger. Any new warn, or any new mute (manual or from the menu), inside `repeat-window` (7d) makes the suggestion `repeat-action` (mute 1d) instead. Tests must cover: warn at day 3 → 1d; mute at day 6 → 1d; warn at day 8 → normal rule again |
| Player is already muted for longer | Do not shorten. Keep the longer one, still record the auto action as `skipped (already muted)` in the audit log and tell staff |
| Player has the exempt permission | No automatic action. Staff gets a message "would have been muted but is exempt" |
| `/unwarn` makes the count drop | Nothing is undone automatically. An already applied auto mute stays until pardoned by staff |
| Offline player warned | Rule still fires. Mute applies on next join; player sees the reason then |
| Bedrock player | Works the same (names with the Floodgate prefix are handled by the existing `OfflineNames` / `BanTargets` code) |
| Server restart between warn 1 and warn 2 | Counter is derived from the database, so it survives |
| Auto punishment must never trigger more warns | Auto actions are mutes/bans only. A rule's action may **not** be `warn` (reject at config load) so there is no infinite loop |

---

## 6. Editing on GitHub (how changes reach the server)

### 6.1 Flow (what the owner does)

1. Open `server/plugins/PhoenixSkyblock/punishments.yml` on GitHub → pencil icon → edit → commit to a branch → pull request → merge.
2. The existing deploy (`server/scripts/deploy.sh`, started by the GitHub deploy workflow in `.github/workflows/`) copies the tracked files to the VPS. Config files are applied at the next restart/deploy, or immediately with `/skyblock reload moderation`.
3. Texts live in `server/plugins/PhoenixSkyblock/messages/moderation.yml` and follow the same flow.

### 6.2 Safety (so a typo can never break the server)

- **Validation at load and on reload.** On any error the module keeps the **previous working config**, prints a clear error in the console and tells online staff with `skyblock.admin`: `punishments.yml line 42: unknown step "bann 3d" (did you mean "ban 3d"?)`. It never throws, never disables the module.
- Things to validate (all with the file path and line/reason id in the message):
  - ladder step syntax (`warn | kick | mute <time|perm> | ban <time|perm> | ipban <time|perm>`, the same parser the code uses today);
  - icon is a real material; reason ids unique and `[a-z0-9-]`;
  - every reason in the `menu` layout exists; no two reasons on the same slot; slots in range 0–53;
  - warn rules: `at` ≥ 1, strictly unique, action is mute or ban (not warn/kick);
  - `mode` is `auto` or `notify`; durations parse.
- **Unit test that parses the tracked file** (`server/plugins/PhoenixSkyblock/punishments.yml`) and the bundled default in `src/main/resources`. A bad edit then **fails the build/CI before it can be deployed**. Add this to `PunishmentLogicTest` (or a new `PunishmentConfigTest`) so `./gradlew build` catches it.
- Keep **one source of truth**: the tracked server file. The resource copy is only the first-run default. Document this at the top of both files.

---

## 7. The config file (target shape)

Extend `server/plugins/PhoenixSkyblock/punishments.yml`. **Keep the existing `categories:` block unchanged** (ladders stay as they are) and add:

```yaml
# ---------- /p menu layout ----------
# Slots are 0-53 (row 1 = 0-8, row 2 = 9-17, ...). Left side = columns 0-3, right side = columns 5-8.
menu:
  title: "<red>Punish <white>{player}"
  confirm: false                    # owner decision: no "are you sure?" click
  keep-open: true                   # stay open after applying
  filler: GRAY_STAINED_GLASS_PANE
  divider: RED_STAINED_GLASS_PANE   # column 4 (slots 13, 22, 40), the warn counter sits on slot 31
  summary-slot: 4                   # player head
  warn-counter-slot: 31
  buttons:                          # fixed bottom-row buttons
    kick:    { slot: 46 }
    history: { slot: 48 }
    close:   { slot: 49 }

  # Four quarters (owner decision). Reason ids come from categories: above. Slots 0-53.
  mutes:                            # TOP-LEFT   (slots 9-12, 18-21)
    toxicity: 9
    spam: 10
    advertising: 11
    hate: 12
    personal-info: 18
    disrespect-staff: 19
  warns:                            # BOTTOM-LEFT (slots 27-30, 36-39): plain warn buttons, no ladder
    warn-language:   { slot: 27, name: "Language",         icon: PAPER,        reason: "Inappropriate language" }
    warn-caps-spam:  { slot: 28, name: "Caps / light spam", icon: WRITABLE_BOOK, reason: "Caps or light spam" }
    warn-rules:      { slot: 29, name: "Rule reminder",   icon: BOOK,         reason: "Not following the rules" }
    warn-other:      { slot: 30, name: "Other warning",   icon: YELLOW_DYE,   reason: "Staff warning" }
  bans:                             # TOP-RIGHT  (slots 14-17, 23-26)
    hacking: 14
    xray: 15
    macro: 16
    alt-abuse: 17
    exploiting: 23
    rmt: 24
    scamming: 25
    griefing: 26
  small:                            # BOTTOM-RIGHT (slots 32-35, 41-44): minor things
    afk-farming: 32
    inappropriate-name: 33
    inappropriate-build: 34
    inappropriate-skin: 35
    # kick is a fixed button (slot 46 above)

# ---------- automatic warn system ----------
warn-rules:
  enabled: true
  mode: notify                # owner decision: staff decide ([Apply] / [Dismiss]). "auto" applies at once
  warn-expire: 30d            # owner decision: a warn stops counting after this (perm = never)
  reset-after-trigger: true   # owner decision: counter restarts from 0 after a trigger is applied OR dismissed
  pending-expire: 24h         # an unhandled suggestion disappears after this
  rules:
    - at: 2                   # 2 active warns -> suggest this
      action: "mute 1h"
      reason: "Automatic rule: 2 warnings"
  repeat-window: 7d           # after a trigger: another warn OR mute within this time ...
  repeat-action: "mute 1d"    # ... suggests this instead
  repeat-reason: "Automatic rule: repeated within a week"
  # No 4-warn ban (owner dropped it). Example if wanted later:
  #   - at: 4
  #     action: "ban 1d"
  #     reason: "Automatic rule: 4 warnings"
```

Texts (add to `messages/moderation.yml`, nothing hardcoded): menu title, button names and lore, offence/next-step lines, warn counter lore, the staff notification, the player message for an automatic action, all validation error messages, `/warns` output.

---

## 8. Other requirements (project rules, all apply)

- **No hardcoded** numbers, text or items. Everything above is YAML/messages.
- **No blocking I/O on the main thread.** History/warn counts are loaded **before** the menu opens (the existing `Target` record does this). The warn rule check runs on the DB thread, then applies on the main thread.
- **Audit log** entry for every manual and automatic action (category `punishment`).
- **Database:** prefer **no new table**. Mark automatic punishments with an existing field or one new nullable column (`source`: `manual` | `auto:warn-rule:<n>`) via a new migration `V002__...sql` in `db/moderation/`. Never edit `V001`.
- **Bedrock:** left-click only; every important thing also reachable through `/p <player> <reason>` commands.
- **Security:** a player must never be able to open the menu; re-check the permission on **every click** (not only on open); ignore clicks if the target became exempt in the meantime; atomic apply.
- **Docs to update when done:** `docs/COMMANDS.md` (`/p`, `/warns`, permissions), `CHANGELOG.md`, `docs/STATUS.md`, this file's status line.

---

## 9. Tests (write them, run `./gradlew build`, never start a test server without owner permission)

Unit tests (no server):
1. Warn counting: only active (not removed, not expired) warns count.
2. Rule firing: 1 warn → nothing; 2 warns → suggestion mute 1h; Apply / Dismiss both reset the counter; repeat inside 7 days → mute 1d.
3. `reset-after-trigger: true` vs `false`.
4. Concurrency: two simultaneous warns fire the rule once.
5. Already muted longer → not shortened.
6. Exempt player → no action, staff message.
7. Config validation: every error in section 6.2 is rejected with a readable message and the old config stays.
8. Layout validation: duplicate slot, unknown reason id, out-of-range slot.
9. The tracked `punishments.yml` parses cleanly.

Manual checklist for the owner (Java **and** Bedrock) is written by the AI after building:
- `/p Steve` opens the menu; left = chat/mutes, right = cheating, head in the middle.
- Click Toxicity → confirm → Steve gets the 1st step; menu refreshes.
- Warn Steve twice → he is auto-muted 1h; staff sees the message; `/history Steve` shows `System (auto)`.
- Edit `punishments.yml` on GitHub (change `at: 2` to `at: 3`), deploy, `/skyblock reload moderation` → new behaviour.
- Break the file on purpose (typo in a ladder) → console + staff get an error, server keeps working with the old config.

---

## 12. Extra features (all approved by the owner, 2026-10-29)

These are part of version 1. Everything is configurable in the same `punishments.yml` (block `extras:` at the end of section 12.13) and all texts go in `messages/moderation.yml`.

### 12.1 Undo button (replaces the confirm click)
- After every punishment applied from the menu, `/p <player> <reason>`, `[Apply]` or a command, the staff member who applied it gets a chat line with a clickable `[Undo]` (`undo-window`: **2m**).
- Clicking runs the normal pardon with the automatic reason `Undone by <staff> (mistake)`. Allowed for the staff member who applied it, or anyone with `skyblock.staff.punish.undo.others`.
- Warn undone → counter updates; if that warn caused a pending suggestion, the suggestion is removed. Mute/ban undone → lifted at once and the player is told.
- Undone punishments do **not** count as an offence for the ladder (existing "pardoned" rule) and show as **reversed** in the stats (12.10).
- After the window the button says "expired, use /unmute, /unban or /unwarn".
- Bedrock: clickable chat is unreliable, so the same action is also in the menu: the head summary item shows "Last action: <text> (click to undo)" while the window is open.

### 12.2 Double-punish protection
- If the same target already got a punishment for the **same reason** from **another staff member** in the last `duplicate-window` (**15s**), the second click is blocked: "Steve was just punished for Spam by Alex 6s ago." The button item shows "Click again within 5s to apply anyway."
- If the target already has an **active** mute or ban that is longer than the new one, staff see "Already muted for 3d" and nothing shorter is applied (never shorten).
- Applies to the menu, `/p <player> <reason>` and `[Apply]`. `/p ... -force` (permission `skyblock.staff.punish.force`) skips the check.
- Must be atomic (one transaction), so two clicks in the same tick cannot both pass.

### 12.3 Offences that expire
- A ladder only counts earlier punishments for the same reason that are **younger than `offence-expire`**. Default **90d** for chat and gameplay reasons; **`perm` (never expires)** for cheating reasons (hacking, xray, macro, alt-abuse, rmt). Each reason can override it: `offence-expire: 180d`.
- History is never deleted; only the counting for the ladder changes. The menu says "Offence #2 (1 older offence no longer counts)".
- `previous()` in `PunishMenus.Target` gets the cutoff time as input, so the logic stays unit-testable.

### 12.4 Evidence snapshot
- For every **mute** and **warn** reason, the last `evidence.chat-lines` (**10**) chat lines of the target (and `evidence.context-lines`, default 5 lines of others before them) are saved with the punishment. Source: the logs/chat module if it offers an API, otherwise a small in-memory ring buffer in the moderation module (last 30 lines per online player, never written to disk unless a punishment happens).
- Stored in a new table `punishment_evidence` (migration `V002__...sql` or later, never edit `V001`): punishment id, time, lines (text), size-limited.
- Shown in the history detail (`/history <player>` → click an entry → "Evidence").
- Privacy (docs/REVIEW.md privacy note): evidence is deleted automatically after `evidence.retention` (**180d**); only staff with `skyblock.staff.punish.history` can read it; IP addresses are never stored in evidence.

### 12.5 Staff notes on a player
- `/pnote <player> <text>` adds a private note. `/pnote <player>` lists them. `/pnote remove <id>` removes one (kept in the database as removed, with who removed it).
- Permission `skyblock.staff.punish.notes`. Notes are visible **only** to staff.
- The head summary in `/p` shows the latest 3 notes (author, date, text cut to 40 characters) and "N more, use /pnote <player>".
- Table `staff_notes` (id, target uuid, author uuid + name, text, created, removed flag, removed by). Max text length `notes.max-length` (default 200).
- Tab completion: players, then `remove`, then note ids of that player.

### 12.6 Warning acknowledgement
- When a player is warned they get the existing title + sound and a chat message with a clickable `[I understand]`, plus a link to the rules (`rules-link` in config).
- `/ack` does the same as clicking (works on Bedrock where clicking chat may not work). Bedrock/Floodgate players additionally get a small 3-row menu with one "I understand" button (`ack.bedrock-menu: true`).
- Not acknowledged: the message repeats every `ack.repeat` (**30s**) up to `ack.max-repeats` (**3**), and once on the next join. It **never** blocks gameplay by default (`ack.block-until-ack: false`).
- The history detail shows "Acknowledged 14:03" or "Not acknowledged".

### 12.7 Warn weight
- Each warn button (section 7 `warns:`) has `weight` (default **1**; example: Rule reminder `0.5`, serious warn `2`). A ladder step `warn` has weight 1; `warn 0.5` is allowed.
- The counter is the **sum of weights of active warns** (not removed, not expired, since the last reset). A rule with `at: 2` fires when the sum is **≥ 2**.
- Display: "Warnings: **1.5 / 2**". Use `BigDecimal` or integers in tenths to avoid floating point errors; unit-test 0.5 + 0.5 + 1 = 2.
- Database: new nullable column `weight` (default 1) on the punishments table, via a new migration.

### 12.8 Suggestions from the chat filter
- The moderation module offers a small API: `SuggestionService.suggest(UUID target, String reasonId, String source, String details)`. It creates a pending suggestion that looks and works exactly like the warn-rule suggestion (`[Apply] [Dismiss] [Open /p]`) and **never applies automatically**.
- Rate limit: one pending suggestion per target + reason; the same one is refreshed, not duplicated.
- The chat module (or a later anti-spam module) calls it. Config example: `chat-suggestions.spam: { hits: 3, within: 10s, reason: spam }`. If no detector exists yet, only the API and one unit test are delivered; hooking up the detector is a separate task in `docs/QUEUE.md`.
- Source text shows in the staff message: "Steve (chat filter: 3 spam hits in 10s) → suggested: Spam, mute 30m".

### 12.9 Report link
- When a punishment is applied to a player who has **reports claimed by that same staff member**, those reports are closed automatically with outcome **punished** and the reporters are informed (existing `reports` behaviour). `extras.close-reports: true`.
- Unclaimed reports are left alone, but the staff message says "2 open reports on Steve" and `[Open /reports player Steve]`.
- Undo of the punishment does not reopen the report (staff can reopen manually).
- Uses the existing staff module API; if it lacks a method, add it to the staff module properly (no hack).

### 12.10 Staff stats command (how many punishments did a staff member give)
| Command | What it does | Permission |
|---|---|---|
| `/pstats` | Your own numbers (alias `/mystats`) | `skyblock.staff.punish.stats` |
| `/pstats <period>` | Your numbers for `24h`, `7d`, `30d` or `all` (default `7d`) | same |
| `/pstats <staff> [period]` | Another staff member's numbers | `skyblock.staff.punish.stats.others` |
| `/pstats top [period]` | Ranking of all staff (given, reversed %) | `skyblock.staff.punish.stats.others` |

Output (chat, short, uses the UI_STYLE colors; optional GUI later):
```
Punishments by Alex (last 7 days)
  Warns 12 · Mutes 8 · Bans 3 · Kicks 2   = 25 given
  Reversed (undone / pardoned): 2 (8%)   Net: 23
  Applied from automatic suggestions: 6
```
Rules:
- Count punishments where the creator is that staff member. `System (auto)` is never a staff member. An applied suggestion counts for the staff member who clicked `[Apply]`; a dismissed one counts for nobody.
- **Reversed** = pardoned by anyone with a reason, including `[Undo]`. Shown separately; net = given − reversed.
- Time zone for periods: `stats.timezone` (default `Europe/Oslo`, owner #16).
- Tab completion: periods, `top`, and names of staff who ever punished (online and offline).
- Must not scan the whole table on every use: query with an index on (`by_uuid`, `created`) and cache results for `stats.cache` (**60s**).
- Staff with only the own-stats permission can never see other people's numbers (check on every call).

### 12.11 Discord log
- Every punishment, pardon and undo is posted to a Discord webhook as one embed: type, target, reason, length, staff (or "System"), punishment id, and for auto rules the rule. **No IP addresses, no evidence text.**
- The **webhook URL is a secret**: it is never put in a tracked YAML. It is applied on the server by `server/scripts/apply-secrets.sh` (read that script first and follow how other secrets are handled). Tracked config only has `discord.enabled: false` and the embed layout.
- Asynchronous with a queue and retry; respect Discord rate limits (stop and retry after the `Retry-After` header); a failing webhook must never delay or break a punishment. Log a console warning at most once per minute.

### 12.12 `/mypunishments` (for players)
- Any player can run `/mypunishments`: active mute (reason, time left, id, appeal link), active warnings "1 / 2" with the dates they expire, and the last 5 entries (type, reason, date).
- Staff names are **hidden** by default (`player-view.show-staff: false`) to avoid arguments with staff.
- Never shows IPs, alts, evidence or notes. Permission `skyblock.punish.view.own` (default true for everyone). A banned player cannot join, so the ban screen with id and appeal link remains the way for bans.

### 12.13 Config block and permissions for all extras

```yaml
extras:
  undo-window: 2m
  duplicate-window: 15s
  duplicate-confirm: 5s            # "click again to apply anyway"
  offence-expire: 90d              # default; cheating reasons set offence-expire: perm
  close-reports: true
  evidence:
    enabled: true
    chat-lines: 10
    context-lines: 5
    retention: 180d
  notes:
    max-length: 200
  ack:
    repeat: 30s
    max-repeats: 3
    block-until-ack: false
    bedrock-menu: true
  rules-link: "https://play.skybornmc.net/rules"   # owner: replace with the real link
  stats:
    timezone: Europe/Oslo
    cache: 60s
    default-period: 7d
  discord:
    enabled: false                 # the webhook URL is a secret, see 12.11
  player-view:
    show-staff: false
  chat-suggestions:
    spam: { hits: 3, within: 10s, reason: spam }
```

| New permission | For |
|---|---|
| `skyblock.staff.punish.undo.others` | Undo punishments of other staff |
| `skyblock.staff.punish.force` | `-force` flag, skip duplicate check |
| `skyblock.staff.punish.notes` | `/pnote` |
| `skyblock.staff.punish.stats` | `/pstats` (own) |
| `skyblock.staff.punish.stats.others` | `/pstats <staff>` and `/pstats top` |
| `skyblock.punish.view.own` | `/mypunishments` (default for all) |

New commands to add to `docs/COMMANDS.md`: `/pnote`, `/pstats` (alias `/mystats`), `/ack`, `/mypunishments`. All with tab completion.

### 12.14 Extra tests
1. Undo within and after the window; undo of a warn removes its pending suggestion; undone punishments don't count as offences and show as reversed.
2. Duplicate protection: second staff blocked inside 15s, allowed after; click-again works; `-force` works; two simultaneous clicks → only one punishment.
3. Never shortens an existing longer mute/ban.
4. Offence expiry: 89d old counts, 91d old does not; cheating reasons never expire; per-reason override.
5. Evidence saved for mutes/warns, deleted after retention, hidden without permission.
6. Notes: add, list, remove, limits, permission.
7. Ack: click, `/ack`, repeat limit, join reminder, never blocks.
8. Weights: 0.5 + 0.5 + 1 = 2 exactly; rule fires at ≥ 2.
9. Suggestion API: dedupe per target + reason, never auto-applies.
10. Report link: only the claiming staff's reports close; unclaimed ones stay.
11. Stats: counts per type, reversed %, system actions excluded, periods, permission checks, cache.
12. Discord: queue, rate limit, failure never blocks, no IP in payload.
13. `/mypunishments`: shows only own data, hides staff names by default.


---

## 13. More features (approved by the owner, 2026-10-29)

Approved: sanctions, island punishments, anticheat suggestions, approval for big punishments, watchlist, probation, bulk punish, raid lockdown (be careful), appeals (in-game and Discord), punish from chat/logs, network-ready, "mute ended" message. Not wanted: the trust score on the head item (do not build it).

### 13.0 Build order and dependencies (read first)

This spec is big. Build it in separate milestones, each released and tested on its own (CLAUDE.md "one thing at a time"):

Sections 1-11 (menu, warns, GitHub editing).
Section 12 (extras) in the order 12.1, 12.2, 12.3, 12.10, then the rest.
Section 13 in this order: 13.4 approval, 13.13 mute ended, 13.7 probation, 13.5 watchlist, 13.8 bulk, 13.11 punish from logs, 13.10 appeals (in-game first), 13.9 lockdown, 13.3 anticheat suggestions, 13.12 network-ready, then 13.1 sanctions, 13.2 island punishments and the Discord side of 13.10 once the economy, islands and Discord modules exist.

Modules that do not exist yet (economy, islands, the Discord bot): do not build those modules here. Define a small provider interface in common (for example SanctionProvider, IslandSanctionProvider, DiscordSink). Until a module implements it, the button or ladder step is shown as "Not available yet" (the same way the staff tools already say it) and everything else works. Add the missing implementations as items in docs/QUEUE.md.

### 13.1 Economy and item sanctions

For dupers, scammers and real-money trading, a ban alone is not enough.

New button "Sanctions" on the bottom row of /p (slot 50) opening a sub-menu:
Sanction	What it does	Undo
Freeze balance	Blocks spending, /pay, bank and shop use for a time or until lifted (personal and island bank)	Lift
Block trading	Blocks auction listings, /pay, trades. Existing auction listings are cancelled and the items returned to the seller's collection box, not deleted	Lift
Remove money	Staff enters an amount (NumberPickerMenu). Takes at most the balance (allow-negative: false). Logged as a sanction transaction	Restore money
Confiscate item	From the inventory or ender chest through the existing inspect view. The item is stored (serialized) in table confiscated_items with an id	Restore item (/psanction restore <id>)
Every sanction needs a reason, is written to the audit log, shows in /history, counts in /pstats as "sanctions", and is atomic (CLAUDE.md economy rule: no half-applied sanctions, no duplicated or lost items).
Permissions: skyblock.staff.sanction.freeze, .trade, .money, .confiscate, .restore (give the last three only to senior staff).
Needs SanctionProvider from the economy module (not built yet): until then "Not available yet", except confiscating items, which only needs the inventory and can be built now.
DupeDetector link (exists in common/item/DupeDetector.java): a detected dupe creates a suggestion via the Suggestion API (12.8): Duping detected → suggested: ban 7d + freeze balance. Never applied automatically.
### 13.2 Island punishments

For griefing in a co-op, lighter than a server ban.

New ladder step types (the same step parser, same punishments.yml): island-kick, island-ban <time|perm> (cannot enter that island), coop-remove (removed from the team), coop-block <time|perm> (cannot join or create teams or islands).
Example: reason griefing ladder: ["warn", "island-ban 1d", "coop-block 7d", "ban 7d"].
Rules: the owner of an island can never be island-banned from their own island. If the target is the owner, the island steps are skipped to coop-block. Only the specific island is affected by island-kick and island-ban.
Needs IslandSanctionProvider from the islands module (not built yet): until then these steps show "Not available yet" and the next available step in the ladder is used, with a staff message explaining it.
### 13.3 Anticheat suggestions (Grim)
New command /psuggest <player> <reason-id> [details...], for console and staff (skyblock.staff.punish.suggest). It calls the Suggestion API (12.8). It never applies anything.
No dependency on Grim's code. Grim can run console commands when a check is flagged: document in server/CONFIG_NOTES.md how to set Grim's punishment command to psuggest %player% hacking Grim: %check% vl %vl% with the violation thresholds chosen by the owner in Grim's own config (tracked under server/plugins/GrimAC/).
Staff message: Steve (Grim: Reach, vl 12) → suggested: Hacked client, ban 30d  [Apply] [Dismiss] [Open /p].
extras.never-auto: [hacking, xray, macro]: even in warn-rules.mode: auto these are never applied without a staff click. Dedupe: one pending suggestion per player and reason, details are appended (max 5 lines).
Grim false positives (lag, ping spikes) are the reason this stays suggestion-only.
### 13.4 Approval for big punishments
Config approval.rules lists what needs approval, for example: ban perm, ipban *, mute perm, ban >= 30d, any sanction of type money or confiscate.
A staff member who clicks such a punishment (menu, /p <player> <reason>, [Apply] or bulk) without the permission skyblock.staff.punish.approve creates a request instead (this replaces the old "limit exceeded" error where moderation.yml → limits applied; the rank limits stay as the hard maximum even for approvers).
Approvers (skyblock.staff.punish.approve) get: Alex requests: ban perm for Steve (Hacked client). [Approve] [Deny] [Open /p]. Approve applies it with by = Alex, approved_by = <approver>. Deny needs a short reason, and the requester is told.
An approver can not approve their own request (approval.self-approve: false). Requests expire after approval.expire (24h). Pending requests show on the head summary in /p and in /pstats (requests made / approved / denied).
The requested punishment is not applied, and the target is not told anything, until approved. A second request for the same player and reason is merged.
Table punishment_requests; the database schema of punishments gets nullable approved_by.
### 13.5 Watchlist
/watch <player> [reason] adds, /watch remove <player> removes, /watchlist lists. Permission skyblock.staff.watch. Optional expiry watchlist.expire (default 30d).
When a watched player joins, staff with skyblock.staff.notify get one line: Watching: Steve (reason: suspected macros, added by Alex 2d ago) [Open /p]. The player is told nothing.
Shows on the head summary in /p. Table watchlist (target, author, reason, created, expires, removed).
Tab completion: players, remove.
### 13.7 Probation (13.6 was the trust score and is not built)
After a mute or ban ends naturally (time ran out), the player is on probation for probation.duration (14d). Pardoned or undone punishments do not start probation (the punishment was wrong or forgiven).
During probation, any new offence uses the ladder step +1 (probation.step-bonus: 1), never past the last step. A step that needs approval (13.4) still needs it.
Visible to staff: the head item says "On probation until 12 Nov (+1 step)" and each reason shows the step that will really be applied. The player is told when it starts ("... a new offence will be punished more strictly until 12 Nov"), and not told the details of the bonus.
Per category switch probation: false (default off for the "small stuff" quarter).
Probation, offence expiry (12.3) and warn weight (12.7) are independent. Unit-test the combination: offence #1 + probation = step 2.
### 13.8 Bulk punish
/pbulk <reason-id> <player> <player> ... (max bulk.max = 20) and a button "Bulk" on the bottom row of /p that opens the player picker (existing PlayerPickerMenu, multi-select).
Bulk always asks for confirmation, whatever menu.confirm says: "Punish 5 players for Spam? Steve (mute 30m), Alex (mute 6h)... [Confirm] [Cancel]". Each player gets their own next ladder step (own offence count, probation, active punishments).
Skipped with a reason shown in the summary: exempt players, already muted/banned longer (12.2), unknown names. Everything applied is one audit entry group and one Discord embed with the count.
One [Undo all] button (undo window 12.1). Each punishment still counts in /pstats per player. Permission skyblock.staff.punish.bulk (senior staff). Bans of type perm in bulk need approval (13.4) per player.
Tab completion: reason ids, then player names repeatedly.
### 13.9 Raid lockdown (careful design, false positives are the main risk)

Goal: stop a bot raid or flood of new accounts from wrecking chat, without ever locking out normal players or staff.

What "new" means: an account whose total playtime is below lockdown.new-player-playtime (30 minutes) or which joined for the first time less than lockdown.new-player-age (24h) ago. Existing players, ranked players and staff are never affected. Permission skyblock.lockdown.bypass is given to every rank above the default.

Command	What it does	Limits
/lockdown chat [duration]	New players cannot chat or use /msg, /mail, signs, books. They see: "Chat is temporarily limited for new players (3 min left)". Everyone else is unaffected	default 10m, max 60m
/lockdown join [duration]	Accounts that have never joined before cannot join. Kick screen is friendly: "The server is busy. Try again in a few minutes." Returning players always can	default 5m, max 15m
/lockdown off	Ends it immediately	
/lockdown status	Shows what is active and who started it	

Safety rules (all must be implemented and tested):

Manual by default. raid.auto-lock: false. Staff start it.
Detection only alerts. raid.detect: true: if more than raid.accounts (15) accounts that have never joined before connect within raid.window (30s), staff with skyblock.staff.notify get Possible raid: 18 new accounts in 30s. [Start chat lockdown 10m] [Ignore]. Normal restarts and vote parties are existing players, so they are not counted.
Even if the owner later sets raid.auto-lock: true: chat lockdown only, max 5m, once per 30 minutes, staff notified immediately, never the join lockdown.
Lockdown never creates punishments, never bans and never touches existing players.
Every start and stop is in the audit log and Discord. A lockdown ends automatically, and is cleared on server restart.
Staff always have the status in the HUD/chat while active, so it is never forgotten.
Bedrock/Floodgate players are treated exactly like Java players (use the same "new" rules).
Edge: a YouTuber or event can create a legit flood of new players. That is why detection only alerts and the default is chat-only.
### 13.10 Appeals (in-game chat and Discord)

Rules (same everywhere):

Appeal what: appeal.appealable-types (default mute, ban); a reason can set appealable: false (default for alt-abuse, rmt). One open appeal per punishment. Cooldown after a denied appeal: 7d. Text length 20-500 characters. Table appeals (id, punishment id, player, text, status open|accepted|reduced|denied|closed, handler, response, created, handled).

In-game (chat), for muted players:

/appeal shows their active punishment (id, reason, time left) and how to appeal; /appeal <text> or /appeal <id> <text> sends it. A command instead of a text-input GUI, because it works the same on Java and Bedrock.
The player gets the answer in chat as soon as it is handled, or on next join if offline.

Discord:

Step 1 (no bot needed, build with 13.10): the webhook (12.11) posts every new appeal to an appeals channel and every decision, with the punishment id, reason, player text and a short history summary (no IPs).
Step 2 (needs the Discord bot and account linking, a later QUEUE item): banned players, who cannot join, appeal with a Discord command /appeal <ban-id> <text>. If their Discord is linked to the account the appeal is marked verified; otherwise unverified (the ban id plus the account name only), shown clearly to staff and rate-limited. The decision is sent back to the player on Discord (direct message when linked).

Staff:

/appeals opens a queue menu (open first, oldest first). Click an appeal: punishment, evidence snapshot (12.4), history, the player's text, and the buttons Accept (lifts it, reason Appeal accepted), Reduce (pick a shorter length with the NumberPickerMenu) and Deny (reason required). Notification to staff with skyblock.staff.notify: New appeal #14 by Steve (mute 1d). [Open].
Permission skyblock.staff.appeals. Accepting an appeal against a punishment from another staff member shows that staff member's name so staff can talk. An accepted appeal counts as reversed (appeal) in /pstats; handled appeals count for the handler.
Unhandled appeals older than appeal.reminder (48h) are listed to staff on login.
### 13.11 Punish from chat or logs
In the /logs chat view (logs module), clicking a chat line opens /p <player> with that line (plus context) attached as evidence instead of the ring buffer (12.4). The flag --evidence <log-entry-id> also works on the command line (tab completion: not needed for ids, only for the flag itself).
Needs a small API in the logs module to fetch entries by id. If it is missing, add it to the logs module properly (CLAUDE.md "no hacking around").
Staff mode (/staff) already has a Punish hotbar item; it uses the same entry point.
### 13.12 Network-ready (prepare, do not over-build)
Add to the punishments tables: server_id and scope (server or network) via a migration. Config network.server-id: phoenix, network.enabled: false, network.default-scope: network for bans and mutes.
With only one server nothing changes in behaviour. When network.enabled is true, join and chat checks look at punishments where scope = network or server_id = this server.
Do not build cross-server messaging, proxies or sync. The database is the sync. Unit-test the scope filter only.
### 13.13 "Mute ended" message
When a mute ends naturally, an online player gets: "Your mute has ended. Please follow the rules: <rules-link>" and a soft sound. If it ended while they were offline, they get it once on the next join (flag end_notice_sent on the punishment, so it never repeats).
Do not poll the database: schedule one task per online player's active mute at its expiry and reschedule on changes (the project already uses a timing wheel for timers; reuse it). After a ban ended, the join message is: "Welcome back. Please follow the rules: <rules-link>" (once).
This message is separate from the probation notice (13.7); if both apply, send them in one combined message.
### 13.14 New commands and permissions
Command	What it does	Permission
/psanction restore <id>	Restore a confiscated item or removed money	skyblock.staff.sanction.restore
/psuggest <player> <reason> [details]	Create a suggestion (console, Grim)	skyblock.staff.punish.suggest
/watch · /watchlist	Watchlist	skyblock.staff.watch
/pbulk <reason> <players...>	Bulk punish with confirmation	skyblock.staff.punish.bulk
/lockdown chat|join|off|status	Raid lockdown	skyblock.staff.lockdown
/appeal	Players appeal (muted players)	skyblock.punish.appeal (default true)
/appeals	Staff appeal queue	skyblock.staff.appeals

Also: skyblock.staff.sanction.* (13.1), skyblock.staff.punish.approve (13.4), skyblock.lockdown.bypass (13.9). Add all to docs/COMMANDS.md with tab completion on every argument.

### 13.15 Config block (add to punishments.yml under extras:)
yaml
  never-auto: [hacking, xray, macro]
  approval:
    enabled: true
    expire: 24h
    self-approve: false
    rules: ["ban perm", "ipban *", "mute perm", "ban >= 30d"]
  watchlist:
    expire: 30d
  probation:
    duration: 14d
    step-bonus: 1
  bulk:
    max: 20
  lockdown:
    new-player-playtime: 30m
    new-player-age: 24h
    chat-default: 10m
    chat-max: 60m
    join-default: 5m
    join-max: 15m
  raid:
    detect: true
    accounts: 15
    window: 30s
    auto-lock: false
  appeal:
    appealable-types: [mute, ban]
    cooldown: 7d
    min-length: 20
    max-length: 500
    reminder: 48h
  network:
    enabled: false
    server-id: phoenix
    default-scope: network
### 13.16 Extra tests
Sanctions: each sanction atomic; restore returns exactly the same money/item; no negative balance; cancelled auctions return items to the seller.
Island steps: owner never island-banned from own island; unavailable provider falls to the next step.
Anticheat: /psuggest from console creates a suggestion, dedupes, never applies; never-auto respected in auto mode.
Approval: request is created, not applied; approver cannot approve own request; expiry; limits from moderation.yml still enforced.
Watchlist: join message goes only to staff, never to the player; expiry.
Probation: starts only on natural end; step +1 capped; combined with offence expiry.
Bulk: confirm always asked; own step per player; skipped list; undo all; max 20.
Lockdown: only "new" accounts affected; staff and ranked players never; manual by default; auto-expiry; cleared on restart; join lockdown never kicks returning players; detection only alerts; Bedrock same as Java.
Appeals: one open per punishment; cooldown; non-appealable reasons; accept lifts the punishment; reduce; deny needs a reason; offline player gets the answer on join; Discord payload has no IPs.
Logs link: --evidence attaches the right lines.
Network scope filter with enabled: false and true.
Mute ended: sent once, also after being offline, never repeats; no database polling.
## 14. Final additions (approved by the owner, 2026-10-29)

Approved: rogue/hacked staff protection with rollback, rule numbers, false report penalty, training mode, monthly report, notification settings (chat and Discord, permission based), data deletion, "Honorable" title. Not wanted (do not build): ladder simulator, different mute types, Dutch/English messages (English only for now).

Build order for section 14: 14.2 rule numbers, 14.9 notifications, 14.6 training mode, 14.1 guard + rollback, 14.5 false reports, 14.10 data deletion, 14.8 monthly report, 14.11 title. Same rules as 13.0 (separate milestones, provider interfaces for modules that do not exist).

### 14.1 Rogue or hacked staff protection and rollback

Guard (limits per staff member):

A sliding window counts what each staff member applies: guard.window (5m), with limits max-bans (10), max-mutes (25), max-sanctions (3), max-total (40). Bulk punishments (13.8) count per player.
Limits per rank: guard.tiers.<tier> (senior staff higher). The tiers match moderation.yml → limits.
What happens when a limit is hit (guard.mode per tier): block (default for staff and moderators) = further punishments from that person are refused with "Punishment limit reached. Ask an admin."; alert (default for admins, so a legit mass cleanup is never blocked) = only the alert is sent.
The alert goes to everyone with skyblock.staff.notify.guard in chat and to Discord (14.9, cannot be switched off, notify.always: [guard]): Alex applied 11 bans in 5 min (limit 10). [Allow 30m] [Lock] [Open /pstats Alex] [Rollback 1h].
Commands (permission skyblock.admin.punish.guard): /pguard allow <staff> [duration] (lifts the block, default 30m), /pguard lock <staff> (blocks that person's punishing at once, no LuckPerms change needed, until /pguard unlock <staff>), /pguard status.
A locked or blocked staff member can still use read-only tools (/history, /alts).
Limits are a safety net, not a punishment. Every block, allow and lock is in the audit log.

Rollback:

/prollback <staff> <period> (period 15m to rollback.max-period = 7d), permission skyblock.admin.punish.rollback (admins only).
Always shows a preview first: "This will undo 37 punishments by Alex in the last 1h (12 bans, 25 mutes, 0 sanctions). Only punishments still active are lifted." and needs a confirm click or /prollback ... confirm.
Undoes: bans, mutes, warns (counter updates), sanctions (money and items restored, 13.1), island steps. The reason is Rollback: <staff> (account problem). Players are told their mute or ban was lifted. Pending suggestions that came from rolled back warns are removed.
Does not undo kicks (cannot) and does not touch punishments by other staff.
Rolled back punishments are marked reversed (rollback) in /pstats and do not count as offences.
One audit entry group, one Discord embed with the count. There is no "undo the rollback"; staff re-apply what was valid.
### 14.2 Rule numbers
punishments.yml gets a rules: block, for example:
yaml
rules:
  1: { title: "Respect everyone",        link: "https://play.skybornmc.net/rules#1" }
  2: { title: "No cheating or exploits", link: "https://play.skybornmc.net/rules#2" }
  3: { title: "No spam or advertising",  link: "https://play.skybornmc.net/rules#3" }
Every reason and every warn button gets rule: <number> (one or a list). Example: spam: { rule: 3 }.
Shown to the player in: the warn message, the mute message, the ban screen, /mypunishments, appeals, and to staff in the menu lore ("Rule 3: No spam or advertising") and in /history. Message placeholders: {rule}, {rule-title}, {rule-link}.
Validation (6.2): a reason may only point to a rule that exists. Unknown rule number = config error with the file and reason id. A reason without a rule is allowed (falls back to the general extras.rules-link).
The owner supplies the real rule list. The block above is a placeholder: the AI proposes a mapping of all existing reasons to numbers and the owner confirms it (add to docs/OPEN_QUESTIONS.md).
### 14.5 False report penalty
The reports module already has the outcomes punished, warned, no-evidence, invalid, duplicate. Only invalid counts as a false report. no-evidence and duplicate are honest mistakes and never count.
When a staff member closes a report as invalid, the message shows: "This is Steve's 3rd invalid report in 30 days." If the reporter reaches false-reports.threshold (3) invalid reports within false-reports.window (30d), a suggestion is created (12.8) for the new reason report-abuse: Steve (3 invalid reports in 30d) → suggested: warn  [Apply] [Dismiss].
New reason report-abuse in category chat: ladder ["warn", "report-block 3d", "report-block 14d", "mute 1d"] and rule set by the owner. New step type report-block <time|perm>: the player cannot use /report for that time. It needs a method in the reports module (blockReporting(uuid, until)): add it to the staff/reports module properly; until it exists, the step says "Not available yet" and the next step is used (same rule as 13.2).
Never automatic. Weekly cap: at most one suggestion per player per 7 days.
### 14.6 Training mode for new staff
A staff member with the permission skyblock.staff.punish.trainee (given with the LuckPerms group) is a trainee. For a trainee, training.requests-for (default: ban, mute, kick, sanction, island, bulk) turns every such action into a request (13.4) for a mentor/approver, even if the action is small. Warns are applied directly (warn is not in the list) but can be undone by any approver.
The request message shows who is a trainee: Alex (trainee) requests: mute 1d for Steve (Spam)  [Approve] [Deny] [Open /p]. Approved = applied with by = Alex.
Dry run: /p <player> <reason> -dry (permission skyblock.staff.punish.dry, default for all staff) shows what would happen ("would apply: mute 1d, offence #2, probation +1 step, needs approval") without applying anything. The menu has a toggle item "Dry run" on the bottom row (slot 52) that turns the whole menu into dry-run mode while open.
/pstats <trainee> also shows requests made, approved and denied, so a mentor sees progress. Becoming a full staff member is done by removing the trainee permission; no automation.
### 14.8 Monthly report
On the 1st of every month (09:00 in stats.timezone) the report of the previous month is generated and stored. It contains aggregated data only: no IPs, no evidence, no notes, no player names (staff names only in the staff section, which needs skyblock.staff.punish.report.staff).
Content: totals per type (warns, mutes, bans, kicks, sanctions); the 10 most used reasons; reversed % (undo, pardon, appeal, rollback separately); appeals received, accepted % and average handling time; reports received, average handling time; reoffence rate per reason and ladder step ("of the players who got step 2 of Spam, 41% got another Spam punishment within 30 days") so the owner can tune ladders; staff ranking (given, reversed %).
Delivery: stored in the database, viewable with /preport [month] (permission skyblock.staff.punish.report), posted to Discord (14.9 type report) and a chat notification to staff with that permission ("The punishment report for October is ready: /preport"). Also written as a CSV file in plugins/PhoenixSkyblock/reports/punishments-<year>-<month>.csv.
Computing it must not hurt performance: run it asynchronously with indexed queries, on the DB thread.
### 14.9 Notification settings (chat and Discord, permission based)

Types and permissions. Every notification of this spec has a type, and a staff member only gets the types they have the permission for (skyblock.staff.notify.<type>):

Type	Examples	Default for
punishments	every punishment, pardon, undo (live feed)	all staff
suggestions	warn rule, chat filter, Grim, dupe, false reports	moderators and up
approvals	requests of trainees and big punishments	approvers
appeals	new appeals, reminders	appeal handlers
watchlist	watched player joins	all staff
raid	raid alert, lockdown start/stop	moderators and up
guard	rate limit alerts	admins (cannot be turned off)
reports	open reports on a punished player	all staff
auto	automatic actions	moderators and up
report	monthly report ready	admins

Chat: /pnotify opens a small menu (existing menu framework, Bedrock-safe) where each staff member switches their own types on or off, only types they have permission for. /pnotify <type> on|off also works (tab completion). Types in notify.always (default [guard]) cannot be turned off. Settings are stored per staff member.

Discord: each type can be sent to its own Discord channel through a named webhook (12.11): discord.channels: { punishments: staff-log, appeals: appeals, guard: admin-alerts, ... }. Who can read a Discord channel is decided by Discord roles, not by this plugin, so:

Webhook URLs are secrets (applied by apply-secrets.sh), never in tracked YAML.
Never send sensitive types to a channel by default. Default: all Discord channels disabled. Types watchlist, appeals (player text), guard and approvals must be mapped to a private channel; the docs and the config comment must warn the owner.
No IP addresses or evidence text in any Discord message.
Same queue/retry/rate-limit rules as 12.11.
### 14.10 Data deletion (privacy)
/ppurge <player> preview shows what would be removed and kept; /ppurge <player> confirm does it. Permission skyblock.admin.punish.purge (owner and admins). Used when a player asks for their data to be removed (docs/REVIEW.md privacy note).
Removed: evidence snapshots, staff notes about the player, appeal texts, watchlist entries, stored IP data and alt links, chat lines in punishment evidence, Discord link.
Kept (minimal ledger): a record of each punishment with only the account id, type, reason id, start/end, and punishment id, for purge.keep-ledger (12 months; 0 = delete everything). This lets the server keep bans in force and detect ban evasion. The player's name is replaced by their id in the kept records.
The purge is itself audited (who ran it, the account id, what was removed), without personal data. A banned player with an active ban is told that the ban record stays until it ends.
The owner must check the rules and retention times with the privacy policy (the assistant is not a lawyer). Defaults can change in the YAML.
Automatic retention (no command needed): evidence 180d (12.4), IP data ip.retention (90d after the last login), notes and watchlist stay until removed.
### 14.11 "Honorable" title (clean streak)
The cosmetics module (M1.11, tags-titles.yml) gets a title honorable. It unlocks automatically when all are true: no warn, mute, ban or kick was created in the last clean-streak.days (60d); account older than that; and at least clean-streak.min-playtime (10h) played in that period (so inactive or alt accounts cannot farm it). Pardoned, undone, rolled back and accepted-appeal punishments do not break the streak.
It is lost (hidden, not deleted) when the player gets any new warn, mute, ban or kick, and can be earned again after another 60 clean days. Staff cannot give or take it directly.
Checked on join and every clean-streak.check (6h) for online players, never per tick. The player gets a message and a sound when it unlocks. Config in extras.clean-streak; the title's name, color and lore live in tags-titles.yml. Needs a small unlock call in the cosmetics API; if missing, add it to the cosmetics module properly.
### 14.12 New commands and permissions
Command	What it does	Permission
/pguard allow|lock|unlock|status	Guard controls	skyblock.admin.punish.guard
/prollback <staff> <period>	Undo everything a staff member did	skyblock.admin.punish.rollback
/pnotify [type] [on|off]	Own notification settings	skyblock.staff.notify (types need skyblock.staff.notify.<type>)
/preport [month]	Monthly report	skyblock.staff.punish.report (+ .staff for staff names)
/ppurge <player> preview|confirm	Delete a player's data	skyblock.admin.punish.purge
/p ... -dry	Dry run	skyblock.staff.punish.dry

Also skyblock.staff.punish.trainee (marks a trainee). Add all to docs/COMMANDS.md, all with tab completion.

### 14.13 Config block (add under extras:, plus the top-level rules: from 14.2)
yaml
  guard:
    window: 5m
    tiers:
      staff:     { mode: block, max-bans: 5,  max-mutes: 15, max-sanctions: 0, max-total: 25 }
      moderator: { mode: block, max-bans: 10, max-mutes: 25, max-sanctions: 3, max-total: 40 }
      admin:     { mode: alert, max-bans: 30, max-mutes: 60, max-sanctions: 10, max-total: 100 }
  rollback:
    max-period: 7d
  false-reports:
    threshold: 3
    window: 30d
  training:
    requests-for: [ban, mute, kick, sanction, island, bulk]
  report:
    day: 1
    time: "09:00"
  notify:
    always: [guard]
  purge:
    keep-ledger: 12m
  ip:
    retention: 90d
  clean-streak:
    enabled: true
    days: 60
    min-playtime: 10h
    check: 6h
    title: honorable

The tier numbers are first proposals; the owner adjusts them in the YAML.

### 14.14 Extra tests
Guard: limits per tier; block refuses the 11th ban, alert only alerts; bulk counts per player; allow and lock work; read-only commands still work; the window slides correctly.
Rollback: preview numbers are correct; only punishments by that staff in the period; sanctions restored; kicks untouched; pending suggestions removed; counts as reversed (rollback); not offences afterwards; period over 7d refused.
Rules: every reason maps to an existing rule; unknown rule = config error; placeholders appear in all player messages.
False reports: only invalid counts; threshold and window; one suggestion per player per 7 days; unavailable report-block falls to the next step.
Training: trainee actions become requests, warns apply directly; dry run applies nothing and shows the real outcome; non-trainees can use dry run.
Monthly report: numbers match known test data; no IPs/evidence/names in the output; the reoffence rate calculation; runs asynchronously.
Notifications: only permitted types are delivered; toggles work; always types cannot be turned off; Discord mapping disabled by default; no sensitive type is sent to an unmapped channel.
Purge: preview equals what is removed; ledger kept for 12 months; active ban stays enforced; the audit entry has no personal data.
Clean streak: unlock conditions (days, playtime), pardoned punishments do not break it, a new punishment hides the title, the 6h check works, no per-tick work.

---

## 10. Definition of done
 /p <player> and /p <player> <reason> work with full tab completion (online + offline + Bedrock).
 Menu matches section 4, layout read from YAML, "More reasons" fallback works.
 Confirm step, keep-open and refresh work; permission re-checked on every click.
 Warn counter shown in the menu and in /warns; calculated from the database.
 Automatic action works in auto and notify mode; all edge cases of 5.5 covered by tests.
 Validation + safe reload + build-time test of the tracked YAML.
 Texts all in messages/moderation.yml; docs and CHANGELOG updated.
 All extras of section 12 built and tested (12.14), including /pstats.
 Section 14 built in its own order and tested (14.14).
 Section 13 built in the order of 13.0 and tested (13.16); modules that do not exist yet are provider interfaces showing "Not available yet".
 ./gradlew build passes; manual checklist handed to the owner.
11. Decisions and open questions

Decided by the owner (2026-10-29):

Warns expire after 30 days.
1st warn = only a warning. 2nd warn = suggested mute 1h. Another warn or mute within a week after that = suggested mute 1d. No 4-warn ban.
Staff decide ([Apply] / [Dismiss]); an applied mute can be revoked with /unmute.
The warn counter restarts from 0 after a trigger.
No confirm click after choosing a punishment.
Menu quarters: top-left mutes, bottom-left warns, top-right bans, bottom-right small stuff.

Also confirmed by the owner (2026-10-29), nothing is open any more: 7. "Within a week" = another warn or any mute within 7 days of the last trigger gives mute 1d. After 7 days without anything, the next trigger is the normal mute 1h again. 8. Dismiss also restarts the counter. The counter restarts after every trigger, applied or dismissed. 9. The 4-warn ban is dropped. 10. The four warn buttons in the bottom-left (Language, Caps / light spam, Rule reminder, Other warning) are fine as written in section 7. 11. Unhandled suggestions stay for pending-expire (24h) and then disappear. 12. Defaults that stay: all warns count together (chat and cheating); no /mywarns in v1.