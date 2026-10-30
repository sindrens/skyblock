# Depths commands

`/depths` itself needs `skyblock.depths.command`; each sub-command also needs its own node.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/depths debug [player]` | Region at your feet, targeted block, depleted counts, timers, break-handling time, fast-break flags | `skyblock.depths.debug` | ✅ |
| `/depths generate [zone\|all]` | Builds the placeholder layout from `zones.yml` by code in throttled slices (`depths.yml → generate.*`): The Gate (`gate`), a tier zone with its causeway (`mine_t01` …), the boss arena (`boss_arena`) or everything (`all`, default, asks to confirm). Idempotent (a second run changes nothing); never touches anything marked `built: true`; audited (`world`) | `skyblock.depths.generate` | ✅ |
| `/depths tp <zone>` | Teleport to the spawn of The Gate (`gate`), a tier zone or the boss arena (`boss_arena`); ignores tier locks | `skyblock.depths.tp` | ✅ |
| `/dungeon` | Tier menu: tabs Mine / Forest / Combat, every tier of the wing; unlocked tiers teleport to their arrival pad after a `dungeon.warmup-seconds` countdown (moving or damage cancels), locked tiers show "✘ Mining 15 needed · 9/15" with a progress bar. Opens on the wing you stand in | `skyblock.depths.dungeon` (everyone) | ✅ |

Tier locks (M4.6, no command): a tier's zone needs its wing's skill level (`zones.yml → required-level`). Players below it see bars in that gate (only them), are pushed back when they walk, teleport or pearl in, get no XP or drops there and are sent to The Gate if they stand in a zone that became locked. `skyblock.depths.bypass` (builders, admins) ignores all locks and the `/dungeon` warmup; `/depths tp` gives a one-zone pass. First unlock of a tier is remembered per season and audited (`world`, `depths_tier_unlock`); the first visit shows a title. Placeholders: `depths_zone`, `depths_tier`.

Profiles (PROF): Depths tier unlocks/first visits belong to the active profile (`depths_tier_unlocks.player` = profile key); access still follows the live skill level of the active profile. Deleted profiles' rows are removed.
