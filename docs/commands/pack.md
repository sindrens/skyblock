# Icon pack (`modules/pack`): I7 v3, pack v2 phase 1

A small Java pack that only repaints menu icons (`resourcepack/icons.json`, 75 icons). Every button stays its real vanilla base item; Java players with the pack **loaded** see our own picture on it. Bedrock, declined, failed, still loading, pack host down or pack off: the plain base item. Settings: `pack.yml` (typed, editable in `/admin`); kick, alert and command texts: `messages/pack.yml`. Reload: `/pack reload` or `/skyblock reload pack`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/pack status` | One line: online players loaded / loading / Bedrock / other, and whether the pack host is up | `skyblock.pack.status` | ✅ |
| `/pack status <player>` | Status of one online player (`loaded`, `pending`, `declined`, `failed`, `bedrock`, `disabled`, `skipped`) with a short explanation; tab completes online players | `skyblock.pack.status` | ✅ |
| `/pack reload` | Reload `pack.yml` and `messages/pack.yml`, re-host the zip and offer the pack again to every online Java player (never enforced: declining that offer does not kick) | `skyblock.pack.reload` | ✅ |

There is no player command (a player switch is planned in GUI-P9).

## How it works
- **Sending:** after the player is in the world (`PlayerJoinEvent`), as a non-vanilla-required pack with the `prompt` text. A decline or a failed download (or no answer within `timeout-seconds`, 60) on that first offer kicks with `kick.declined` / `kick.failed`, which explain how to turn Server Resource Packs on. Only when `required: true` and the host is healthy.
- **Waiting:** the first-join welcome (essentials) and the tutorial start/resume wait until the pack is answered, at most `join.wait-seconds` (15), so the prompt never covers the camera.
- **Hosting:** the zip is inside the plugin jar; a tiny web server serves only `GET /phoenix-icons-<hash>.zip` on TCP `host.port` (25580, `ufw allow 25580/tcp`). URL = `url` if set, else `http://<host.public-host>:25580/<file>`. A copy is written to `plugins/PhoenixSkyblock/pack/`.
- **Fail-open:** every `host.check-seconds` (60) the server downloads its own URL (`host.check-url` overrides, e.g. loopback) and compares the SHA-1. Until the first success and after `host.check-failures` (2) misses the pack is not sent and nobody is kicked; players with `skyblock.pack.alerts` get **one** alert per outage (`alert.host-down`, with the reason) and one `alert.host-up` line; both are in the audit log (`pack_host_down` / `pack_host_up`, category CONFIG). When the host comes up, players who were skipped are offered the pack (not enforced).
- **Statuses** are memory only (no tables). Other modules use `PackService` / `PackWait` from `common` and `MenuService#icon`.

Permissions: `skyblock.pack.status` (helper), `skyblock.pack.reload` (admin), `skyblock.pack.alerts` (mod, admin); all default op. Grants: `server/permissions/pack.txt`.
