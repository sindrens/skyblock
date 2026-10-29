# Standards: UX, Staff, Code, Testing

Every feature must meet **all** of these before it counts as done. They exist so 300 players (Java and Bedrock) can play without confusion, and so staff can run the server without a developer.

---

## 1. Player experience (user-friendliness)

Visual details (colors, lore template, GUI slots, sounds, HUD) are specified in [`UI_STYLE.md`](UI_STYLE.md).

### 1.1 Discoverability
- **`/menu`** (also opened by a menu item in the hotbar, and the `F` swap-hand key where possible) is the hub for *everything*: island, The Depths, shop, sell, bank, crates, quests, skills, sets, events, settings, help.
- **`/help`** opens a categorized GUI help with short descriptions and clickable "Take me there" buttons. `/help <topic>` jumps straight to a topic.
- **No feature may exist only as a command.** Every command has a GUI path, and every GUI has a command shortcut.
- **Two-click rule:** any common action is reachable in ≤ 2 clicks from `/menu`.
- Every GUI has a **Back** and **Close** button in the same slot, and an info item explaining the screen.

### 1.2 Teleporting
- `/spawn`, `/is` (home), `/is visit`, `/warp`, `/warps`, `/dungeon`, `/tpa`, `/tpahere`, `/back`.
- Every teleport target is also clickable in GUIs.
- Teleport warmup only when in combat; otherwise instant. Safe-location checks always.

### 1.3 Feedback
- Every action gets feedback: chat (MiniMessage, consistent prefix), action bar for progress, boss bar for events/timers, titles for big moments (level up, unlock).
- **Errors tell the player how to fix it** ("You need Island Size II. Open /is upgrade to see the tasks.") and offer a clickable fix.
- Consistent colors: success, error, info, warning, and currency colors defined once in config.
- Numbers are formatted (1.2k, 3.4M), and times are human-readable.

### 1.4 Commands
- Short and predictable: `/<feature>` opens its GUI, and subcommands follow `/<feature> <verb> [args]`.
- Full **tab completion** with permission filtering.
- Aliases are configurable.
- Wrong usage shows usage plus a clickable example.

### 1.5 Bedrock (Geyser/Floodgate)
- Every GUI must be usable on Bedrock: no reliance on shift-click combos, item names short enough for mobile, no tiny click targets.
- Test every GUI on a Bedrock client before marking done.
- Custom textures/models need Geyser mappings.

### 1.6 New players
- The tutorial is optional, under 3 minutes, and re-openable.
- The scoreboard always shows **one clear next objective**.

---

## 2. Staff and admin

- **`/admin`** in-game GUI: edit every module's config (values, messages, items, rewards, schedules, schematics) live, with validation, undo and a change log.
- **Permission levels:** Helper (view, basic moderation), Mod (moderation, teleport, inspect), Admin (config edit), Owner (economy edits, season reset). Implemented with LuckPerms permission nodes `skyblock.staff.*`, `skyblock.admin.*`.
- **`/staff`** mode: vanish, fly, inspect player (inventory, ender chest, island, balances, history), freeze, teleport menus, spectate.
- **Audit log** of every staff/admin action and every economy transaction above a threshold (database + Discord webhook).
- **Automation:** anticheat auto-punish rules, `/report` → Discord, chat filter, auto-mute rules, Discord ticket bot, automatic restart warnings, automatic backups.
- Staff never need console or file access for normal operations.

### External plugins
See `DESIGN.md` §14 for the decided list. Everything else is custom.

---

## 3. Code

- **Java 21, Paper 1.21.11 API, Gradle (Kotlin DSL).** Adventure + MiniMessage for all text.
- **Architecture:** one plugin jar. `common` = shared framework, each feature = a **module** under `modules/` implementing `SkyblockModule` and registered in the plugin. Modules talk through **services/interfaces and events**, never by reaching into each other's internals.
- **Nothing hardcoded:** values → `config.yml` of the module, text → `messages.yml`, items → item definitions in config.
- **Main thread is sacred:** all database and file I/O is async. Target: the server stays at 20 TPS / MSPT < 40 at 300 players. Profile with spark.
- **Database:** MySQL/MariaDB via HikariCP. Versioned migrations. Player and island data cached with write-behind.
- **No silent failures:** log with context, and fail loudly in development.
- Small classes, clear names, Javadoc on public APIs.
- Conventional commits: `feat(island): ...`, `fix(economy): ...`, `test: ...`, `docs: ...`.

---

## 4. Testing (required)

Every feature ships with:
1. **Unit tests** (JUnit 5 + MockBukkit) for logic: formulas, economy math, permissions, state transitions.
2. **A manual test checklist** in the PR (`.github/PULL_REQUEST_TEMPLATE.md`) run on a local test server, **Java and Bedrock**.
3. **Edge cases:** offline players, server restart mid-action, full inventory, concurrent actions (two members clicking at once), duplication attempts.
4. **Performance check** for anything that runs per tick, per block or per player.
5. **`./gradlew build` green** locally and in CI before merging.

**Definition of Done:** spec followed · configurable in-game · GUI + command · `/help` entry · messages in `messages.yml` · permissions documented in `COMMANDS.md` · tests pass · manual checklist done · docs updated · CHANGELOG entry.
