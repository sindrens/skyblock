# Chat, tab list, nametags (`modules/chat`)

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/chat clear` · `/clearchat` (`/cc`) | Clear the chat for everyone (staff keep theirs) | `skyblock.staff.chat.manage` | ✅ |
| `/chat lock` · `/mutechat` (`/lockchat`) | Lock/unlock the chat | `skyblock.staff.chat.manage` | ✅ |
| `/chat slow <seconds>` | Slow mode (0 = off) | `skyblock.staff.chat.manage` | ✅ |
| `/sc [message]` (`/staffchat`) | Staff chat; without text toggles staff-chat mode | `skyblock.staff.chat` | ✅ |

Chat permissions: `skyblock.chat.color` (classic `&` colors), `skyblock.chat.format` (`&l &o &n &m`), `skyblock.chat.hex` (`&#RRGGBB`), `skyblock.chat.links` (links/IPs), `skyblock.chat.item` (`[item]`), `skyblock.chat.bypass` (lock, slow mode, rate limit, filters), `skyblock.staff.tab` (TPS/ping in the tab footer). Formats live in `chat.yml → format.*` (staff GUI editor in M1.11).

Filter (owner answers 301/304/305): `chat.yml → protection.words.replace` (mild swearing → `***`) and `protection.words.block` (slurs and threats: message blocked, staff alerted) ship with an English-only starter list waiting for the owner's approval (an untouched empty list of an old file is filled once). Signs, book text and anvil names use the same filter (`protection.filter-signs-books-anvils`; blocked text undoes the edit, logged `text_filtered`), and so do warp names and nicknames (refused when the filter would change them). Public chat is English only as a rule (rule 7) enforced by staff, with no automatic language detection; private messages may be any language. The name hover card shows only rank, island and playtime (owner answer 302). Bedrock names keep their dot (owner answer 303).

Channel chat (M2.5): other modules publish a `ChatChannel` service (common/chat; island chat, `/is chat`). The chat module checks it for every message after mute, freeze and menu prompts; a captured message runs the same rate limit, duplicate, word/link filter, `&` color and `[item]` checks, is audited as `chat` with `channel` in the details and goes to the channel instead of everyone. One-shot channel messages use the `ChatChannels` service (same checks, mute via the moderation `muted` placeholder, `blocked.muted`). `chat.yml → channels.respect-lock` / `channels.respect-slow-mode` (default off): whether `/mutechat` and slow mode also stop channel chat. Staff chat mode wins over a channel.

Permission definitions: `modules/chat/src/main/resources/permissions/chat.yml`. Rank grants: `server/permissions/chat.txt`.
