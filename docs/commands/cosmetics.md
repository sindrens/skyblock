# Cosmetics module (`modules/cosmetics`): M1.11

Titles & tags: `tags-titles.yml` (registry, editable in-game), `cosmetics.yml`, texts `messages/cosmetics.yml`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/tags` (`/tag`), `/titles` (`/title`) | One menu, two tabs: equip one title + one tag, locked titles show how to earn them, preview of your chat/tab line; staff get an Edit button (create/edit/delete titles and tags) | `skyblock.cosmetics` (everyone); editor `skyblock.admin.cosmetics.edit` | ✅ |
| `/tags give\|take <player> <tag>`, `/titles give\|take <player> <title>` | Give/remove (online or offline), logged | `skyblock.staff.cosmetics.give` | ✅ |
| `/tags item <tag> [amount] [player]` | Create unique tag items (right-click to redeem with confirm; tradeable, AH-sellable) | `skyblock.admin.cosmetics.item` | ✅ |
| `/display` (`/displayformat`) | Display editor: chat/tab/nametag formats (chat.yml) with live preview and confirm; applied instantly, logged | `skyblock.admin.display` | ✅ |

Achievements hook for other modules: `core.services().get(Cosmetics.class).grantTitle(uuid, "id", "reason")`. Quiet grant (caller shows its own message): `grant(uuid, Kind.TITLE, id, reason, false)`. `defineTitle(id, display, rarity, source, secret)` adds a missing title to `tags-titles.yml` (never changes an existing one; audited `config/title_define`); the achievements module uses it for its reward titles. When the achievements module runs, `/tags` shows an **Achievements** button (slot 52). Placeholders: `<ph:title>`, `<ph:tag>`, `<ph:title_id>`, `<ph:tag_id>`.

Permission definitions: `modules/cosmetics/src/main/resources/permissions/cosmetics.yml`. Rank grants: `server/permissions/cosmetics.txt`.
