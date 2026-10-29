# Depths commands

`/depths` itself needs `skyblock.depths.command`; each sub-command also needs its own node.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/depths debug [player]` | Region at your feet, targeted block, depleted counts, timers, break-handling time, fast-break flags | `skyblock.depths.debug` | ✅ |
| `/depths generate [zone\|all]` | Builds the placeholder layout from `zones.yml` by code in throttled slices (`depths.yml → generate.*`): The Gate (`gate`), a tier zone with its causeway (`mine_t01` …), the boss arena (`boss_arena`) or everything (`all`, default, asks to confirm). Idempotent (a second run changes nothing); never touches anything marked `built: true`; audited (`world`) | `skyblock.depths.generate` | ✅ |
| `/depths tp <zone>` | Teleport to the spawn of The Gate (`gate`), a tier zone or the boss arena (`boss_arena`); ignores tier locks | `skyblock.depths.tp` | ✅ |
