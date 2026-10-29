# Punishments (`modules/moderation`, M1.8; replace vanilla `/ban /kick /pardon`)

Flags in the reason: `-s` silent (staff only see it), `-ip` IP-wide. Durations: `30m`, `2h`, `7d`, `1w`. Templates/ladders: `punishments.yml`. Length limits per rank: `moderation.yml → limits`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/punish <player> [reason] [-s]` | Menu: category → reason (ladder shows the next step) → confirm; or apply a reason directly | `skyblock.staff.punish.gui` | ✅ |
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

Other: `skyblock.staff.punish.silent` (`-s`), `skyblock.staff.punish.exempt` / `.exempt.bypass`, `skyblock.staff.punish.max.<tier>` (length tiers). Alt alerts on join go to `skyblock.staff.notify`.

Permission definitions: `modules/moderation/src/main/resources/permissions/moderation.yml`. Rank grants: `server/permissions/moderation.txt`.
