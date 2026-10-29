# Chat, tab list, nametags (`modules/chat`)

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/chat clear` · `/clearchat` (`/cc`) | Clear the chat for everyone (staff keep theirs) | `skyblock.staff.chat.manage` | ✅ |
| `/chat lock` · `/mutechat` (`/lockchat`) | Lock/unlock the chat | `skyblock.staff.chat.manage` | ✅ |
| `/chat slow <seconds>` | Slow mode (0 = off) | `skyblock.staff.chat.manage` | ✅ |
| `/sc [message]` (`/staffchat`) | Staff chat; without text toggles staff-chat mode | `skyblock.staff.chat` | ✅ |

Chat permissions: `skyblock.chat.color` (classic `&` colors), `skyblock.chat.format` (`&l &o &n &m`), `skyblock.chat.hex` (`&#RRGGBB`), `skyblock.chat.links` (links/IPs), `skyblock.chat.item` (`[item]`), `skyblock.chat.bypass` (lock, slow mode, rate limit, filters), `skyblock.staff.tab` (TPS/ping in the tab footer). Formats live in `chat.yml → format.*` (staff GUI editor in M1.11).

Permission definitions: `modules/chat/src/main/resources/permissions/chat.yml`. Rank grants: `server/permissions/chat.txt`.
