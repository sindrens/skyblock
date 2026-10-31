# Resource pack (`modules/pack`)

Sends the Phoenix resource pack to Java players **in the configuration phase** (before the world loads) as a required pack with a prompt, and hosts the zip itself. Bedrock (Floodgate) players are skipped and get the text fallback. Settings: `pack.yml` (editable in `/admin`); kick and command texts: `messages/pack.yml`. Reload: `/pack reload` or `/skyblock reload pack`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/pack reload` | Reload `pack.yml`, re-extract and re-host the zip, resend it to everyone online (the `variant` switch takes effect here) | `skyblock.pack.reload` | ✅ |
| `/pack status <player>` | Shows `LOADED`, `DECLINED`, `FAILED`, `BEDROCK`, `PENDING` or `DISABLED` for an online player | `skyblock.pack.status` | ✅ |

## How it works
- **Hosting:** the zips are inside the plugin jar. On start the chosen one (`variant: safe|shine`) is extracted to `plugins/PhoenixSkyblock/pack/`, its SHA-1 is checked against `pack-ids.yml`, and a small web server (`host.port`, default **25580**, TCP; open it in the firewall) serves only `GET /<file name>`. URL = `url` if set, else `http://<host.public-host>:<port>/<file name>`. The file name contains the hash, so an unchanged pack is cached by clients.
- **Sending:** `AsyncPlayerConnectionConfigureEvent`; the connection waits for the client's final answer (up to `timeout-seconds`).
  - **Declined** (and `required: true`): disconnected with `kick.declined`, which explains why and how to enable it (Multiplayer → Edit → Server Resource Packs: Enabled).
  - **Failed / timed out** (and `kick-on-fail: true`): disconnected with `kick.failed` ("Couldn't download the pack, please rejoin").
- **Rendering:** other modules ask `PackService#hasPack(viewer)`; only `LOADED` viewers get pack glyphs, backgrounds, badges and `pack-sound.*` sounds.
- **Reload:** resends to online Java players with the same pack id, so a new hash replaces the old copy; their status is tracked from the client's answers.

Permissions: `skyblock.pack.reload`, `skyblock.pack.status` (default op; granted in `server/permissions/pack.txt`: helper = status, admin = reload).
