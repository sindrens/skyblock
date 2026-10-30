# Phase 1: Core Player Experience

**Goal:** the server feels like a finished, professional network before any skyblock gameplay exists: spawn, teleports, chat, ranks shown everywhere, scoreboard, tab, nametags, menus, help, settings, and a complete staff toolkit with logs, punishments and reports.

Depends on Phase 0 (config, messages, DB, commands, menus, audit log).

---

## M1.1 Spawn and join flow
- **First join:** teleport to spawn, welcome title (`PHOENIX SKYBLOCK` fire gradient + "Welcome, <name>"), brand sound, chat welcome with clickable `/menu` and `/help`, broadcast "✦ Welcome <name> to Phoenix Skyblock! (#1,234)" (unique join count). Menu item given.
- **Join:** teleport to spawn (until islands exist, then: last location/island, configurable). Join/quit messages: **ranks only** (donor ranks + staff, owner decision); the first-join welcome is always shown.
- **Spawn protection:** no breaking/placing/interacting except whitelisted blocks (doors, buttons, NPCs, crates). No damage, no hunger, no item drops (drops returned), no mob spawning. Builders bypass with `skyblock.build.spawn` (+ `/build` toggle so builders don't break things by accident).
- Void below spawn → back to spawn. `/spawn` (instant; 3 s warmup only if in combat in PvP zones later). `/setspawn` (admin, updates `worlds.yml`).

## M1.2 Teleports
- `/tpa <player>`, `/tpahere <player>`, `/tpaccept [player]`, `/tpdeny [player]`, `/tpcancel`: clickable [Accept]/[Deny] chat buttons, expire after 60 s, max 1 outgoing request, cooldown 15 s (rank-reducible), blocked if the target has requests off (`/settings`) or ignores you.
- `/back`: last location before a teleport or death (death-back only in safe worlds; not into PvP zones).
- **Server warps:** `/warp <name>`, `/warps` GUI (icons, descriptions), `/setwarp`, `/delwarp`, `/warpicon` (admin).
- All teleports: safe-location check, cross-world support, cancelled teleports explained, logged (`world/teleport` with from/to).

## M1.3 Chat, ranks and identity (everywhere the same)
**Rank data:** LuckPerms group + prefix + weight + display name (staff ranks now; donor ranks Squire→Monarch in a later phase; titles from Phase 10). One `RankService` resolves "how this player looks" and everything uses it:

| Place | Format |
|---|---|
| **Chat** | `[Title] [RANK] Name » message` (owner decision) |
| **Tab list** | `[RANK] Name [Title] [Tag]`: rank first, name in the rank color, then title and `/tags` tag if the player has them (owner decision). Sorted by rank weight, then name |
| **Nametag** (above head) | `[RANK] Name` in rank color |
| **Join/leave, broadcasts, menus** | Rank-colored name |

- **Chat line details:** title in its rarity color, rank prefix from LuckPerms, name in rank color, `»` muted, message in `text` color (staff/ranks may use colors via permission). **Hover card** on the name: rank, island + island level (later), playtime, joined date, Discord linked ✔/✘. **Click** name → suggests `/msg <name> `.
- **Mentions:** `@Name` or plain name → highlighted for the target + sound (togglable).
- **Item showing:** `[item]` in chat shows the held item with hover tooltip (anti-abuse: cooldown, no renamed-slur names).
- **Private messages:** `/msg`, `/r`, `/ignore`, `/ignorelist`; **socialspy** for staff (`/socialspy`).
- **Chat protection (configurable):** rate limit, duplicate-message block, anti-caps (>70% caps → lowercased), link/IP/advert filter (allow-list for our domains), swear/slur filter with severity (block vs. replace vs. auto-mute + staff alert), personal info filter (phone numbers), new-player chat delay (e.g. 30 s after first join), slow mode `/chat slow <seconds>`, `/chat lock`, `/chat clear`.
- **Channels:** global (default), staff chat `/sc`, island chat `/is chat` (Phase 2), announcements.
- Everything logged (`chat/*`), including filtered messages (with the reason).

## M1.4 Scoreboard (sidebar)
- Fire-gradient title, max 12 lines, updates every 1 s (only changed lines resent, no flicker), contextual by world (exact layouts in [UI mockups](#ui-mockups)).
- **Detailed layout without fuel or member count** (owner decision): objective, player name, rank, money, crystals, gems, island level + position, online. Fuel lives in `/is fuel`.
- Lines show "—" for features not built yet, so the layout never jumps.
- Toggle in `/settings`; Bedrock tested (Bedrock shows 15 lines max).

## M1.5 Tab list and nametags
- Header: SkybornMC logo text + `Phoenix Skyblock` gradient + online count + season/day. Footer: `/menu · /help · /discord · /store` + ping/TPS for staff only.
- Sorting: rank weight (owner → default), then alphabetical; vanished staff hidden for non-staff.
- Nametags via scoreboard teams: rank prefix + name color; Bedrock-compatible.
- Ping/latency shown in tab (vanilla bars) and in the hover card.

## M1.6 `/menu` and `/help`
- `/menu` (and the **menu item in hotbar slot 9**, owner decision: Nether Star with red glint "Menu · Right-click", undroppable, can't be moved into containers, can be disabled in `/settings`): hub with every feature (built ones active, future ones shown as "Coming soon" so the layout stays the same). Layout in mockups.
- `/help`: category GUI from `help.yml` (Getting started, Islands, The Depths, Economy, Progression, Gear, Combat & Events, Rewards, Community, Commands); each topic 3–6 short pages with **Take me there**; `/help <topic>`; FAQ topic; staff can edit help texts in `/admin`.
- `/rules` (paginated, accept on first join), `/discord`, `/store`, `/vote` (clickable links).

## M1.7 `/settings`
Per-player toggles saved in the profile, grouped: **Chat** (mentions sound, PMs, join/leave messages, chat timestamps), **Social** (tpa requests, trade requests, island invites), **Display** (scoreboard, tips, particles, menu item), **Sounds** (UI sounds, event sounds). Modules can register their own settings.

## M1.8 Punishments (custom, owner decision)
- Commands: `/ban`, `/tempban`, `/ipban`, `/mute`, `/tempmute`, `/warn`, `/kick`, `/unban`, `/unmute`, `/unwarn`. Flags: `-s` silent (staff only see it), `-ip` (IP-wide).
- **Templates:** `/punish <player>` opens a GUI with reasons (Hacking, X-ray, Toxicity, Spam, Advertising, Exploiting, Inappropriate build/name…). Each reason has an **escalation ladder** (e.g. Toxicity: warn → 1 h mute → 24 h mute → 7 d mute → perm) based on the player's history. Configurable.
- Works on **offline** players and Bedrock names; ban screen shows reason, staff (optional), expiry, ban id, and appeal link (Discord).
- **Alt detection:** on join, accounts sharing an IP with a banned/muted account are flagged to staff (never auto-banned unless the owner enables it); `/alts <player>`.
- `/history <player>` GUI: all punishments (active/expired/removed), who issued and removed them, reasons, evidence notes.
- Our database is the source of truth (vanilla ban list kept in sync for safety). `/unban` from the current moderation module migrates to this system.
- All logged (`punishment/*`), staff notified, optional Discord webhook.

## M1.9 Staff mode and staff tools
- `/staff`: toggles **staff mode**: own inventory saved and swapped for a staff kit; vanish (hidden from players, tab, player count and ping list), fly, no damage, no item pickup, night vision toggle. Kit items: **Inspect** (right-click player → inspect menu), **Freeze**, **Random teleport to a player**, **Vanish toggle**, **Punish**, **Logs here** (block/area history), **Exit**.
- `/vanish` separately (with levels: helpers can't see admins in vanish).
- `/freeze <player>`: frozen player can't move/interact/teleport, sees a title + chat instructions, chat only to staff; logging out while frozen triggers a configurable action (default: alert staff + note in history).
- `/inspect <player>` GUI (online or offline): profile (rank, playtime, first/last seen, IP region, Bedrock/Java, client version), inventory, ender chest, island (later), balances (later), punishments, alts, **recent logs** shortcut.
- `/invsee`, `/endersee` (online and offline, edit permission separate from view).
- `/sc <message>` staff chat (toggle `/sc` without text), `/staffs` (online staff list, vanished hidden by level).
- `/report <player> <reason>`: reason picker GUI, cooldown, goes to staff chat with [Teleport] [Inspect] [Claim] buttons and to Discord later; `/reports` GUI (open/claimed/closed), report counts per player.
- `/maintenance on|off [message]` with whitelist bypass; auto-restart scheduler with countdown titles; auto-announcer (`announcements.yml`, interval, random/ordered, clickable); `/broadcast`.

## M1.10 Staff log viewer (`/logs`)
The audit log (M0.8) made searchable in-game. **This is how staff check "who paid whom", trades, commands, chat, island actions, items.**
- `/logs` GUI: choose **player** (or island/item/location), **category** (all, economy, trade, island, punishment, staff, command, chat, item, auth, world), **time range** (1 h, 24 h, 7 d, 30 d, custom), then a paged, newest-first list. Each entry is an item: icon by category, lore with time ("3m ago" + exact UTC), actor → target, amount, location (click to teleport), details.
- Quick commands:
  - `/logs player <name> [category] [time]`: everything a player did (as actor) or had done to them (as target).
  - `/logs pay <player> [otherPlayer]`: money between players (`/pay`, trades, auction purchases).
  - `/logs trade <player>`: secure trades with full item lists on both sides.
  - `/logs chat <player> [time]` and `/logs commands <player> [time]`.
  - `/logs island <player|islandId>`: invites, kicks, role changes, bank actions, upgrades.
  - `/logs item <itemUuid>`: the full life of a unique item (crafted/won → traded → sold …), for dupe investigations.
  - `/logs near [radius] [time]`: actions near you (block logs come from CoreProtect when available).
  - `/logs staff <staffName>`: everything a staff member did (accountability).
- **Export:** `/logs export <query>` writes a CSV to `plugins/PhoenixSkyblock/exports/` for appeals/evidence (owner/admin only).
- **Permissions by level:** helper (chat/commands of players), mod (+economy/trade/island), admin (+staff actions, export), owner (everything incl. config changes).
- Viewing logs is itself logged (`staff/logs_view`).

## M1.11 Display formats (staff-editable) and `/tags`
- **Display format editor** (owner decision): in the staff menu (`/staff` → Display) admins edit how players look in **chat, tab and nametags**: which parts are shown and in which order (rank, name, title, tag, island level), separators and colors, with a live preview line. Saved to `display.yml`, applied instantly, logged.
- **Tags & Titles menu** (`/tags`, also `/titles`; owner decision: one menu for both):
  - **Titles** are *earned*: lots of achievements (cool and secret ones, not only winning), season tops, collection milestones, tool prestige. Kept across seasons. Titles not earned yet show as locked with how to earn them.
  - **Tags** are *owned* cosmetics (e.g. `[Phoenix]`, `[OG]`), won from crates or bought in the store. They arrive as a **tag item**: right-click to redeem it to your account (confirm screen). Tag items can be traded and **sold on the auction house** (extra copies, duplicates). Owned tags are kept across seasons.
  - The menu has two tabs (Titles | Tags), preview of your chat/tab line, select/unequip one of each.
  - Staff define tags and titles in a GUI (id, display text with colors, rarity, how to obtain).
- Staff can give/remove tags and titles: `/tags give|take <player> <tag>`, `/titles give|take <player> <title>`, `/tags item <tag> [amount]` (creates tag items); all logged.

## M1.12 `/admin` config editor (base)
- `/admin` GUI: modules → settings generated from the typed config classes (M0.1). Booleans toggle; numbers use the number picker; text via chat prompt; lists add/remove; every edit is validated, saved to YAML, applied live (or flagged "needs restart"), and logged with old/new values (`config/edit`).
- Permission levels: admin edits gameplay settings; owner edits economy/season settings.

---

## UI mockups

### Scoreboard (owner decision: detailed, no fuel, no member count, shows name + rank)
```
  ✦ PHOENIX SKYBLOCK ✦        ← fire gradient, bold
 ──────────────────           ← dark gray
  ▸ Next: Explore /menu       ← current objective

  Pouched                     ← player name in rank color
  Rank      Owner             ← label ember, value rank color
  Money     $1.2M             ← money green ("—" until Phase 3)
  Crystals  ◆ 4,250           ← aqua ("—" until Phase 4)
  Gems      ✦ 120             ← emerald ("—" until the store phase)

  Island    Lv 34 (#12)       ← "—" until Phase 2/5

  Online    212
 ──────────────────
  play.skybornmc.net          ← ember
```
In The Depths, "Island" is replaced by Zone/Satchel; during events the event timer replaces "Online".

### Tab list
```
        ▬▬▬▬▬▬  SKYBORNMC  ▬▬▬▬▬▬           ← white/red
           Phoenix Skyblock                  ← fire gradient
        12 online · Season 1 · Day 23        ← gray/white

  OWNER Pouched [Founder] [Phoenix]   ← rank (dark red bold), name in rank color, title, tag
  ADMIN …                              ← red
  MOD Steve [Miner]                    ← gold
  HELPER …                             ← aqua
  BUILDER …                            ← yellow
  Alex [OG]                            ← default: gray name + tag
  (format editable by admins in /staff → Display)

      /menu · /help · /discord · /store      ← gray, white commands
      TPS 20.0 · 18 ms                       ← staff only
```

### Chat line
```
[Founder] OWNER Pouched » hey everyone!
  ^title    ^rank  ^name   ^message (gray-white)
hover on name → ┌ Pouched ─────────────┐
                │ Rank: Owner          │
                │ Island: Pouched's (Lv 34)  (Phase 2+)
                │ Playtime: 12h 40m    │
                │ Discord: ✔ linked    │
                │ Click to message     │
                └──────────────────────┘
```

### `/menu` (6 rows)
```
Row 1  [red pane × 4] [PLAYER HEAD: profile] [red pane × 4]
Row 2  [black] Island  Depths  Shop   Sell   Bank   AH     [black]
Row 3  [black] Skills  Sets    Enchant Quests Crates Events [black]
Row 4  [black] Warps   Pets    Collection Leaderboards Store Vote [black]
Row 5  [black] [ ]     [ ]     Settings  Help   Discord [ ]  [black]
Row 6  45 –   46 red  47 red  48 Info  49 Close  50 Help  51 red  52 red  53 –
```
Future features show their real icon, gray name "(Coming soon)" and no action.

### Inspect menu (staff)
```
Row 1  frame with PLAYER HEAD in the center (name, rank, online/offline)
Row 2  Profile · Inventory · Ender chest · Island · Balances
Row 3  Punishments · Alts · Logs · Reports · Teleport
Row 6  standard navigation
```

---

## Commands and permissions (summary)
| Command | Permission (default rank) |
|---|---|
| `/spawn`, `/tpa*`, `/back`, `/warp(s)`, `/msg`, `/r`, `/ignore`, `/menu`, `/help`, `/settings`, `/rules`, `/report` | everyone |
| `/setspawn`, `/setwarp`, `/delwarp`, `/build` | admin / builder (`/build`) |
| `/sc`, `/staffs`, `/reports`, `/socialspy` (helper+), `/freeze`, `/inspect`, `/invsee` view, `/staff`, `/vanish`, `/mute`, `/warn`, `/kick`, `/history`, `/alts` | helper / mod |
| `/ban`, `/tempban`, `/ipban`, `/unban`, `/punish` | mod |
| `/logs` (scope by level), `/maintenance`, `/broadcast`, `/chat slow|lock|clear`, `/admin` | helper→owner by scope |
| `/logs export`, `/invsee` edit, economy/season settings in `/admin` | admin / owner |

## Data (tables)
`warps`, `punishments`, `punishment_templates` (config), `reports`, `ignores`, `player_settings` (in `players.settings`), `staff_mode_state`, plus `audit_log` from Phase 0.

## Release / test checklists (owner)
| Milestone | You check |
|---|---|
| M1.1 | First join flow (use an alt or ask a friend), spawn protection, `/build` |
| M1.2 | tpa/tpahere/accept/deny/expire, `/back`, warps GUI |
| M1.3 | Your chat line, hover card, mentions, `/msg`/`/r`/`/ignore`, filters (try a link) |
| M1.4–M1.5 | Scoreboard and tab look like the mockups on Java **and** Bedrock; rank sorting |
| M1.6–M1.7 | `/menu`, `/help` topics and Take me there, `/settings` toggles persist after relog |
| M1.8 | Punish an alt with templates (warn → mute ladder), ban screen, `/history`, `/unban` |
| M1.9 | Staff mode (inventory restored after exit), freeze, inspect, reports flow |
| M1.10 | `/logs player`, `/logs chat`, filters, teleport from a log entry |
| M1.11 | Change the tab/chat format in `/staff` → Display; give yourself a tag and select it in `/tags` |
| M1.12 | Change a value in `/admin`, see it applied and logged |

## Open questions (defaults apply unless changed)
See the owner question rounds; anything not answered yet is listed in `OPEN_QUESTIONS.md` under "Phase 1".
