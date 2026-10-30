# Punishments (`modules/moderation`, M1.8; replace vanilla `/ban /kick /pardon`)

Flags in the reason: `-s` silent (staff only see it), `-ip` IP-wide. Durations: `30m`, `2h`, `7d`, `1w`. Templates/ladders, `/p` layout and warn rules: `punishments.yml` (texts in `messages/moderation.yml`). Length limits per rank: `moderation.yml → limits`.

Every way of punishing (menu, `/p <player> <reason>`, the direct commands below, [Apply] on a suggestion, automatic rules) goes through one decision pipeline: exempt → permission → length limit → never shorten (an active longer mute/ban is kept; staff are told) → apply.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/p <player>` (`/punish`) | Single-screen punishment menu: mutes top-left, warn buttons bottom-left, bans top-right, small things bottom-right, head + warn counter in the middle; bottom row: Sanctions (P7), Kick, Bulk (P5), History, Close, More reasons, Dry run (P8). One click applies the next ladder step and the menu refreshes. Offline players work | `skyblock.staff.punish.gui` | ✅ |
| `/p <player> <reason-id> [-s] [-ip]` | Apply a reason (ladder) or a warn button directly. Tab completes players (online, offline, Bedrock), reason ids and flags | `skyblock.staff.punish.gui` (+ `.silent` for `-s`, `.ipban` for `-ip`) | ✅ |
| `/warns <player>` | Warn counter (active warns since the last trigger), the automatic rules, when each warn expires, the repeat window and a waiting suggestion with [Apply] / [Dismiss] | `skyblock.staff.punish.history` | ✅ |
| `/warns <player> apply\|dismiss <id>` | Handle a waiting suggestion (what the clickable [Apply] / [Dismiss] run; first click wins) | `skyblock.staff.punish.gui` (+ the permission for the suggested punishment) | ✅ |
| `/ban <player> [reason]` · `/tempban <player> <time> [reason]` | Ban (offline + Bedrock names work), styled ban screen with ID and appeal link | `skyblock.staff.punish.ban` | ✅ |
| `/ipban <player\|ip> [reason]` (`/ban-ip`) | Ban the account and its last IP (or an IP) | `skyblock.staff.punish.ipban` | ✅ |
| `/mute <player> [reason]` · `/tempmute <player> <time> [reason]` | Mute: blocks chat, private messages, signs and books | `skyblock.staff.punish.mute` | ✅ |
| `/warn <player> <reason>` | Warning (chat + title) | `skyblock.staff.punish.warn` | ✅ |
| `/kick <player> [reason]` | Kick (kept in history) | `skyblock.staff.punish.kick` | ✅ |
| `/unban <player\|ip> <reason>` (`/pardon`) | Unban a player (tab-completes banned names) or IP; reason required; staff are notified | `skyblock.staff.unban` | ✅ |
| `/unmute <player> <reason>` | Unmute; reason required | `skyblock.staff.punish.unmute` | ✅ |
| `/unwarn <player> <reason>` | Remove the latest warning; reason required | `skyblock.staff.punish.unwarn` | ✅ |
| `/history <player> [chat\|menu]` (`/punishments`) | All punishments (active/expired/removed, who, why) | `skyblock.staff.punish.history` | ✅ |
| `/alts <player>` | Accounts sharing an IP, with ban/mute status | `skyblock.staff.punish.alts` | ✅ |

Warn rules (`punishments.yml → warn-rules`): 2 active warns suggest a 1 h mute; another warn or any mute within 7 days of a trigger suggests 1 d. Staff with `skyblock.staff.notify` get `[Apply] [Dismiss] [Open /p]`; unhandled suggestions show in `/p` and `/warns` for 24 h. Mode `auto` applies at once as "System (auto)". Broken `punishments.yml` edits are rejected (old config kept) and reported to the console and to `skyblock.admin.reload`.

Other: `skyblock.staff.punish.silent` (`-s`), `skyblock.staff.punish.exempt` / `.exempt.bypass`, `skyblock.staff.punish.max.<tier>` (length tiers). Alt alerts on join go to `skyblock.staff.notify`.

Permission definitions: `modules/moderation/src/main/resources/permissions/moderation.yml`. Rank grants: `server/permissions/moderation.txt`.
