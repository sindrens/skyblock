# Scoreboard (M1.4, module `hud`)

No commands. Every bundled layout starts with one blank line under the title (owner, FIXES1; max 10 lines + title). Layouts in `messages/hud.yml`, settings in `hud.yml` (`places`: which layout on your own island, visiting, The Depths and its zones, spawn, warzone; SB). Service `HudService`: `setEnabled(player, bool)` (sets the `/settings` toggle `scoreboard`, registered by hud; the sidebar hides/shows right away), `addLayout(owner, layout, predicate)` (events; wins over `places`), `scoreboard(player)` (nametag teams for M1.5).

No permissions.
