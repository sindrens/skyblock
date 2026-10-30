# Social (`modules/social`)

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/msg <player> <message>` (`/w`, `/tell`, `/whisper`, `/m`, `/pm`) | Private message; click a name to reply; rate-limited; logged (30 days) | `skyblock.social.msg` | ✅ |
| `/r <message>` (`/reply`) | Reply to your last private message | `skyblock.social.msg` | ✅ |
| `/ignore <player>` / `/ignore list` | Block a player's private messages (staff can't be ignored) | `skyblock.social.ignore` | ✅ |
| `/unignore <player>` | Stop ignoring | `skyblock.social.ignore` | ✅ |
| `/afk` | Toggle AFK (auto after `social.yml → afk.auto-minutes`) | `skyblock.social.afk` | ✅ |
| `/list` (`/online`, `/who`) | Online players grouped by rank | `skyblock.social.list` | ✅ |
| `/ping [player]` | Connection delay | `skyblock.social.ping` | ✅ |
| `/playtime [player]` (`/pt`) | Total play time | `skyblock.social.playtime` | ✅ |
| `/seen <player>` | Last online / first joined | `skyblock.social.seen` (+ `skyblock.staff.seen.details`) | ✅ |
| `/discord`, `/store`, `/vote`, `/website`, `/rules` | Clickable links (URLs in `social.yml`). `/rules` lists rules 1-9 (owner answers 321/322: appeal line, English only in public chat, no alt abuse, no macros/AFK farming outside the AFK pool) and the "logged for 30 days" privacy line (owner answer 319) | `skyblock.social.links` | ✅ |
| `/socialspy` | Staff: see all private messages | `skyblock.staff.socialspy` | ✅ |
| `/whois <player>` | Staff: UUID, rank, platform, times, location, names; IP + alts with `skyblock.staff.whois.ip` | `skyblock.staff.whois` | ✅ |
| `/broadcast <message>` (`/bc`, `/announce`) | Staff: announcement in chat + title + sound (MiniMessage) | `skyblock.staff.broadcast` | ✅ |

Other: `skyblock.staff.unignorable` (private messages always delivered), `skyblock.staff.vanish.see` (see vanished players in `/list` and tab completion; defined by the vanish feature).

Permission definitions: `modules/social/src/main/resources/permissions/social.yml`. Rank grants: `server/permissions/social.txt`.
