# Phase 0: Foundation

**Goal:** a framework so solid that every later feature is fast to build, consistent, safe and logged. Players see almost nothing in this phase, which is intended.

**Already done:** repo, CI, deploy, module system, worlds module, MOTD module, `/unban`, staff ranks, server configs.

---

## M0.1 Config framework
**What:** one way to load, validate, reload and edit every config file.
- Each module owns `plugins/PhoenixSkyblock/<module>.yml` (live copy tracked in `server/plugins/PhoenixSkyblock/`).
- **Typed config classes**: a module declares its settings as fields with defaults, ranges and descriptions (`@Setting(min=0, max=100, desc="...")`).
- **Merge on update:** new keys from a new plugin version are added with defaults, and existing values are never overwritten. A `config-version` key plus migration steps for renamed keys.
- **Validation:** wrong type/out-of-range value → clear console error naming file, key, value and the allowed range; the **old value stays active**.
- **Reload:** `/skyblock reload [module]`; modules get a `reload()` callback.
- **Editable in-game later** (`/admin`, Phase 1): descriptions and ranges come from the typed classes, so the editor is generated automatically.

**Done when:** a broken YAML value on reload shows a precise error and nothing breaks; a new key appears in the file after an update without losing edits.

## M0.2 Style and messages
- `style.yml`: the palette from `UI_STYLE.md` as MiniMessage tags: `<c:brand>`, `<c:ember>`, `<c:value>`, `<c:error>`, `<c:money>`, `<rarity:epic>`, `<pillar:mining>`, plus symbols (`<sym:money>` = `$`) and the standard prefix.
- `messages/<module>.yml`: every player-facing text, MiniMessage, named placeholders (`<player>`, `<amount>`), with **no text in code**.
- Missing key → logged once, shows `[missing: key]` instead of crashing.
- Helpers: `msg.send(player, "island.created", Placeholder…)`, action bar, title, boss bar, sound by key (`sounds.yml`).
- Number/time formatting from `common` (`$1.2M`, `2h 15m`) used everywhere.
- **Bedrock:** every color has its legacy fallback, and the message system never sends unsupported glyphs (checked against the allowed symbol list).

## M0.3 Database layer
- **MariaDB** on the VPS, **H2 (MySQL mode)** for local runs. The same SQL works on both.
- HikariCP pool (size 10, tunable), shaded and relocated together with the drivers.
- **Async by default:** all queries run on a dedicated executor (Java 25 virtual threads). Results return to the main thread only when touching the world/players.
- **Migrations:** `db/<module>/V001__init.sql`, `V002__…`, tracked in `schema_version`. Applied on start; failure = module disabled with a clear error (server keeps running).
- Conventions: every gameplay table has `season_id`; times in UTC milliseconds; UUIDs as `BINARY(16)`; money as `BIGINT`.
- Small DAO helpers (prepared statements only, no string-built SQL).

## M0.4 Player data
- `players` table: uuid, last name, first/last seen, total playtime, Bedrock flag, Discord id (later), settings JSON.
- `name_history` table (staff can look up old names).
- **Load** in `AsyncPlayerPreLoginEvent`; if the DB is slow (>5 s) the player gets a friendly "try again" message instead of joining with broken data.
- **Cache** while online; dirty fields saved every 60 s, on quit and on shutdown (flushes before the plugin disables).
- API: `players.get(uuid)`, `players.offline(name)` (async, for offline targets in commands).

## M0.5 Command framework
On top of Paper's Brigadier API, every command gets:
- Permission check, player-only/console-only flags, cooldowns, confirmation for dangerous actions.
- Argument types: online player, **offline player** (DB-backed suggestions), world, duration (`30d`, `2h30m`), amount with suffixes (`1.5k`, `2M`, `3B`), enum choices.
- Consistent usage and error messages (from messages), clickable examples.
- Registration into the help index (feeds `/help` and the command list in `COMMANDS.md`).
- Aliases configurable per command.

## M0.6 Menu (GUI) framework
- Standard 6-row frame from `UI_STYLE.md` §5: red top/bottom rows, black sides, fixed slots (45 back, 48 info, 49 close, 50 help, 53 next).
- Building blocks: paged lists, confirm dialog, number picker (+1/+10/+100/custom), player picker, text input via chat prompt (Bedrock-friendly), toggles.
- **Total click protection:** no item can leave a menu (shift, number keys, drag, double-click, offhand, drop).
- Click debounce (anti double-click dupes), async loading item, live-updating slots, sounds.
- The "?" info item on every screen links to the right `/help` topic.

## M0.7 Item framework
- Registry of custom items by id (`omnitool`, `money_note`, `crate_key_rare`, …).
- Every custom item carries a PDC id; unique items also get a UUID (dupe detection: two identical UUIDs online → both flagged, staff alerted, logged).
- **Lore builder** implementing the `UI_STYLE.md` §4 template (name, subtitle, description, stats, enchants, actions, rarity footer; no italics; hidden vanilla tooltips).
- Resource-pack ready: `item_model` component per item id (empty until the pack exists).

## M0.8 Audit log (the logging backbone)
**Every module logs every meaningful action here.** This is what makes the staff log viewer (Phase 1) possible.
- Table `audit_log`: id, time, season, **category** (`economy`, `trade`, `island`, `punishment`, `staff`, `command`, `chat`, `item`, `auth`, `config`, `world`), **action** (`pay`, `ah_buy`, `island_invite`, `ban`, …), **actor** (uuid or console/system), **target** (uuid/island id/item uuid), **amount** (nullable), **world + x/y/z** (nullable), **details JSON** (everything else, e.g. item NBT summary, old/new values).
- Written **async in batches** (every 2 s or 500 rows); never blocks gameplay.
- Indexes on (actor, time), (target, time), (category, action, time).
- **Retention** (configurable): economy/trade/punishment/staff/config **forever** (or until owner deletes); chat 90 days; commands 90 days; item pickups/drops 30 days.
- API: `audit.log(category, action, actor, target, amount, location, details)`.
- Chat and commands are logged here too (Phase 1 wires them).

## M0.9 Services, placeholders, utilities
- **Service registry:** modules publish/consume services by interface (`EconomyService`, `IslandService`, …). Modules never touch each other's classes directly.
- **Placeholders:** internal registry (`island_level`, `balance`, `rank`, …) used by scoreboard, tab, chat, menus. **PlaceholderAPI expansion** `%phoenix_<key>%` registered only if PlaceholderAPI is installed.
- Scheduler helpers (main/async/delayed/repeating), cooldown utility, slow-operation timer (logs anything >5 ms on the main thread with its name).
- LuckPerms + Floodgate wrappers (softdepend): rank prefix/weight/group, is-Bedrock.

## M0.10 Developer workflow
- Local server with H2 (`run-local.sh`), documented.
- `CHANGELOG.md`, `COMMANDS.md`, `ROADMAP.md` kept current.

---

## Release / test checklists (owner)
| Milestone | You check |
|---|---|
| M0.1–M0.2 | `/skyblock reload` works; break a value in `motd.yml` → console shows a clear error; the old MOTD keeps working |
| M0.3–M0.4 | Join → leave → join: no errors; server restart keeps your data |
| M0.5–M0.7 | `/skyblock` commands show proper usage messages; a demo menu (`/skyblock demo-menu`, staff only) shows the red/black frame and can't be looted |
| M0.8–M0.9 | Run a few commands → `/skyblock audit recent` (temporary staff command) lists them |

## Open questions (defaults apply unless the owner changes them)
1. Audit retention: chat/commands 90 days, item pickups 30 days, economy/punishments forever. OK?
2. Should players ever be able to see their **own** logs (e.g. `/transactions` for their money history)? Default: **yes**, own economy history only (Phase 3).
