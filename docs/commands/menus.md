# Menus module (`modules/menus`): M1.6 + M1.7 + staff hub and `/admin` (M1.12)

Layout: `menu.yml` (hub), `help.yml` (help categories, topics, pages), `navigation.yml` (menu item), texts: `messages/navigation.yml`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/menu` (`/gui`) | Main hub: every feature; not-yet-built ones shown locked "Coming soon". Also the **menu item** (Nether Star, hotbar slot 9; right-click/tap) | `skyblock.menu` (everyone) | ✅ |
| `/help [topic\|category\|commands]` (`/guide`) | Help menu: categories, topics with pages, Take me there, Show in chat; command lists generated from the commands you may use. Console/chat gets the chat version. Every menu's "?" opens `/help <topic>` | `skyblock.help` (everyone) | ✅ |
| `/settings [setting] [on\|off\|toggle]` (`/options`, `/prefs`) | Personal settings menu (Chat, Social, Display, Sounds); chat form for quick changes, Tab lists them | `skyblock.settings` (everyone) | ✅ |
| `/staff` (`/staffmenu`) | Staff hub: Display, Config editor, Titles & Tags, staff tools (unbuilt ones "Coming soon") | `skyblock.staff.menu` | ✅ |
| `/admin` (`/config`) | Config editor generated from every module's typed config: toggle, cycle, number picker, chat input, list editor; validated, saved, applied live (or "needs restart"), logged `config/edit` old→new. database.yml hidden | `skyblock.admin.config`; owner-level settings `skyblock.owner.config` | ✅ |

Settings now: **menu item** (works). Placeholders shown "Coming soon" until their module takes them over through the `PlayerSettings` service: private-messages, mentions-sound, join-messages, chat-colors, chat-timestamps, tpa-requests, trade-requests, island-invites, afk-broadcast, scoreboard, tips, particles, depths-visibility, ui-sounds, event-sounds.

Permission definitions: `modules/menus/src/main/resources/permissions/menus.yml`. Rank grants: `server/permissions/menus.txt`.
`/admin` uses the plugin-level `skyblock.admin.config` / `skyblock.owner.config` (see [core](core.md)).
