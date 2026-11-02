# Next batch (owner request 2 Nov 2026, 15:33)

Owner: "enable milestones, finish island tools, Eclipse crate, farming, build order and /shop with better relevant blocks
(mob drops for spawners), icon pack (shop arrows, scoreboard, tab), quick fixes, GUIs like mchub (with resource pack:
building blocks, farming, mob drops, redstone, clean and sorted), easier Depths requirements, island level without
pillars. Plan synergy and implement. Full season wipe is planned later."

| # | Item | State |
|---|---|---|
| 1 | Milestones on | **done** 2edc4dbe (`milestones.enabled: true`, settings v2) |
| 2 | Personal milestones never reset when leaving an island; island ones are per island | already so (engine: personal = profile scope) |
| 3 | Easier Depths requirements | **done** 2edc4dbe: skill curve base 300, early growth 1.15 (level 35 about 6x faster) |
| 4 | Island level without pillars | **done** 2edc4dbe: `points.per-pillar-level` 0, `blocks-factor` 1.0. Next: add milestone points and collections as level sources (synergy) |
| 5 | /shop: Mob Drops tab (spawner drops, vanilla mob drops) and a cleaner category set: Building Blocks, Nature, Farming, Mob Drops, Redstone, Decoration, Colors, Essentials, Spawners | TODO. Mob drops must stay above 7.5x sell (BuySellInvariantTest) |
| 6 | Shop GUI like mchub: resource-pack panel, title plaque, clean grid, own arrows | TODO (pack art: tools/build_panels.py, icons.json) |
| 7 | Icon pack: shop arrows, scoreboard and tab logo | TODO |
| 8 | Island tools, Eclipse Crate, farming, build order | TODO. Spec sections 14-24 and 10 were cut off in chat; build from section 1.2 decisions (D1-D12, T1-T13) and 0.5 notes, owner checks |
| 9 | Full season wipe | later (owner) |
