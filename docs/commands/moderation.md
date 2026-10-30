# Punishments (`modules/moderation`, M1.8; replace vanilla `/ban /kick /pardon`)

Flags in the reason: `-s` silent (staff only see it), `-ip` IP-wide. Durations: `30m`, `2h`, `7d`, `1w`. Templates/ladders, `/p` layout and warn rules: `punishments.yml` (texts in `messages/moderation.yml`). Length limits per rank: `moderation.yml → limits`.

Every way of punishing (menu, `/p <player> <reason>`, the direct commands below, [Apply] on a suggestion, automatic rules) goes through one decision pipeline: exempt → permission → length limit → duplicate (P2) → never shorten (an active longer mute/ban is kept; staff are told) → apply.

P2 extras (`punishments.yml → extras`, texts `messages/moderation.yml → undo.*, stats.*`):
- **[Undo]**: after every punishment you apply (menu, `/p`, [Apply], direct commands) you get a chat line with a clickable `[Undo]` for `undo-window` (2m). It is a normal pardon with the reason "Undone by <you> (mistake)": an undone punishment is not an offence and shows as reversed in `/pstats`. Undoing a warn also removes the pending suggestion it caused. Bedrock: the head in `/p` shows "Last action: ... (click to undo)" while the window is open. Only your own punishments, or anyone's with `skyblock.staff.punish.undo.others`. Kicks can't be undone. After the window: use `/unmute`, `/unban` or `/unwarn`.
- **Double-punish protection**: the same player + the same reason again within `duplicate-window` (15s, by anyone, also yourself) is blocked ("Steve was just punished for Spam by Alex 6s ago"); click/type it again within `duplicate-confirm` (5s) to apply anyway, or add `-force` (`skyblock.staff.punish.force`). Applies to the menu, More reasons and `/p <player> <reason>`; [Apply] is already first-click-wins.
- **Offences expire**: a ladder counts only earlier punishments for that reason younger than `offence-expire` (90d; cheating reasons `perm`; each reason can override). The menu says "(1 older offence no longer counts)". History is never deleted.
- **Public announcements**: bans and mutes are announced to all players unless `-s` (owner decision 2026-10-30; `moderation.yml → announce.public`).

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/p <player>` (`/punish`) | Single-screen punishment menu: mutes top-left, warn buttons bottom-left, bans top-right, small things bottom-right, head + warn counter in the middle; bottom row: Sanctions (P7), Kick, Bulk (P5), History, Close, More reasons, Dry run (P8). One click applies the next ladder step and the menu refreshes. Offline players work | `skyblock.staff.punish.gui` | ✅ |
| `/p <player> <reason-id> [-s] [-ip] [-force]` | Apply a reason (ladder) or a warn button directly. Tab completes players (online, offline, Bedrock), reason ids and the flags you may use | `skyblock.staff.punish.gui` (+ `.silent` for `-s`, `.ipban` for `-ip`, `.force` for `-force`) | ✅ |
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
| `/pstats [24h\|7d\|30d\|all]` (`/mystats`) | Your own numbers: warns, mutes, bans, kicks given, reversed (undone/pardoned) with %, net, applied from automatic suggestions. Default period `extras.stats.default-period` (7d); 7d/30d are whole days in Europe/Oslo | `skyblock.staff.punish.stats` | ✅ |
| `/pstats <staff> [period]` | Another staff member's numbers (tab completes staff who ever punished) | `skyblock.staff.punish.stats.others` | ✅ |
| `/pstats top [period]` | Ranking of all staff (given, reversed %, net); click a name for details | `skyblock.staff.punish.stats.others` | ✅ |

Warn rules (`punishments.yml → warn-rules`): 2 active warns suggest a 1 h mute; another warn or any mute within 7 days of a trigger suggests 1 d. Staff with `skyblock.staff.notify` get `[Apply] [Dismiss] [Open /p]`; unhandled suggestions show in `/p` and `/warns` for 24 h. Mode `auto` applies at once as "System (auto)". Broken `punishments.yml` edits are rejected (old config kept) and reported to the console and to `skyblock.admin.reload`.

Other: `skyblock.staff.punish.undo.others` (undo other staff's punishments), `skyblock.staff.punish.force` (`-force`), `skyblock.staff.punish.silent` (`-s`), `skyblock.staff.punish.exempt` / `.exempt.bypass`, `skyblock.staff.punish.max.<tier>` (length tiers). Alt alerts on join go to `skyblock.staff.notify`.

Permission definitions: `modules/moderation/src/main/resources/permissions/moderation.yml`. Rank grants: `server/permissions/moderation.txt`.
