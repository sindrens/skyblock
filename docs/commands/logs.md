# Logs (`modules/logs`, M1.10)

| Command | Description | Permission | Status |
|---|---|---|---|
| `/logs` | Staff log viewer: pick who (online/any player, staff member, near me, item, island, everything), category and time range; paged newest-first results, hover details, click to teleport | `skyblock.staff.logs` (+ `skyblock.staff.logs.category.<category>`) | ✅ |
| `/logs player <name> [category] [time]` | Everything a player did or had done to them | `skyblock.staff.logs` | ✅ |
| `/logs pay\|economy <name> [other]` | Money between players (pay, trades, auction) | `skyblock.staff.logs.category.economy` | ✅ |
| `/logs trade\|chat\|commands <name> [time]` | Trades / chat / commands of a player | `skyblock.staff.logs.category.<…>` | ✅ |
| `/logs island <name\|id> [time]` · `/logs item <uuid>` · `/logs near [radius] [time]` | Island actions, life of a unique item, actions near you | `skyblock.staff.logs` | ✅ |
| `/logs staff <name> [time]` | Everything a staff member did | `skyblock.staff.logs.staff` | ✅ |
| `/logs export <name\|last> [category] [time]` | CSV to `plugins/PhoenixSkyblock/exports/` (logged) | `skyblock.staff.logs.export` | ✅ |
| `/logs page <n>` | Next pages of your last search in chat (console, Bedrock option) | `skyblock.staff.logs` | ✅ |

**Punish from logs (Phase P, P5, spec 13.11):** in the results, a chat line (`chat` / `chat_blocked`) opens the punish menu `/p <player>` with that line (and the lines before it) as evidence: click it in the menu (left click, works on Bedrock; right click still prints it in chat), or click `[Punish]` after the line in the chat view. Shown only to staff with `skyblock.staff.punish.gui` while the moderation module runs; it runs `/punish <player> --evidence <id>` for you, so moderation checks everything again. The logs module provides the small `LogLookup` service (in `common/audit`): one entry by id, only if the viewer may see its category. No new command; every click is logged (`staff/logs_punish_open`).

Permission definitions: `modules/logs/src/main/resources/permissions/logs.yml`. Rank grants: `server/permissions/logs.txt`.
