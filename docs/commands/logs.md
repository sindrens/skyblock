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

Permission definitions: `modules/logs/src/main/resources/permissions/logs.yml`. Rank grants: `server/permissions/logs.txt`.
