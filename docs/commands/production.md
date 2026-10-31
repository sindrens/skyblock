# Production (`modules/production`)

Island production (PHASE-7-PRODUCTION.md). Built so far: **M7.1 Island Fuel**. Settings: `production.yml`; texts: `messages/production.yml`. Reload: `/skyblock reload production` (Fuel Cell item look: restart).

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/is production` | Production overview (fuel, collectors, generator, crops, spawners, last offline report). **Not built**: a new command needs the owner's OK (PD34). Until then the fuel gauge sits in the island menu (`/is`, slot 28) | `skyblock.production.menu` (everyone) | ⏸ waiting for owner |
| `/isadmin fuel <player> add\|set <minutes>` | Staff fuel fix after bugs, audited with a reason. **Not built** (PD35) | `skyblock.production.admin.fuel` (admin) | ⏸ waiting for owner |
| `/isadmin production <player>` | Staff view: state, fuel, activity. **Not built** (PD35) | `skyblock.production.admin.view` (mod) | ⏸ waiting for owner |

No commands yet, so there is nothing to tab-complete.

**Switch: OFF by default.** `production.yml → fuel.enabled: false` until the owner enables production; then `/skyblock reload production` (or a restart). While off, nothing changes anywhere: spawners and crops work as before, the AFK pool keeps its fuel banked, the daily calendar and crates pay their fallback, the Fuel Tank upgrade shows "Coming soon" and the Fuel Efficiency mastery stays hidden. `fuel.pause-when-asleep` (default on) decides whether an Asleep island really pauses spawners and crops.

**Island Fuel** (owner DESIGN §5.4, PD1–PD5, R5 / rule 9)
- **States** (checked every `fuel.check-seconds`, 60): **Active** = a stored member is on the island of their **active profile** and did something real in the last `fuel.active-minutes` (15): break/place, hit a mob, harvest, fish, use a menu, chat, or walk `fuel.walk-blocks` (5) blocks not in water, a vehicle or a bubble column. The island earns **+1 min of fuel per minute** (per island, never per member). **Fueled** = nobody active, tank > 0: the tank burns. **Asleep** = nobody active, tank empty: **spawners stop (even next to a player) and crops don't grow** (vanilla growth and the stacking module's dark growth). Coop players, visitors, The Depths and spawn never earn fuel; standing AFK burns it. A member's action on an Asleep/Fueled island makes it Active right away.
- **Tank:** 12 h + 2 h per **Fuel Tank** upgrade level (36 h at level 12; `/is upgrade`, Production tab). **Burn:** `fuel.burn-rate` (1.0) ÷ (1 + Fuel Efficiency%). Fuel Efficiency = the **Fuel Efficiency** mastery (MA8: +10 / +20 / +30%) + island achievements (fuel boost, max +10%), one family `FUEL_EFFICIENCY`, cap **+40%** (`enchanter.yml → caps.fuel-efficiency`).
- **Fuel Cells** (1 h / 3 h / 6 h, `cells.*`): bound to the profile (never tradeable), unique (dupe scanner). **USE** (right-click) while standing on your own island. A cell is only used when it **fits completely** (otherwise "room for 2h 10m", item kept). The cell is taken first, then one transaction marks its id used and adds the fuel; a refusal or error gives it back (inventory, else `/box`). A copy of a used cell is removed and logged. Sources: the daily calendar's fuel days (4, 12, 23) and crate fuel rewards turn into cells while Island Fuel is on (largest cells first, the rest as one 1 h cell).
- **AFK pool:** its banked fuel (15 min per reward) is added to the island tank as soon as Island Fuel is on (on join, on the next reward, and once for everyone online when it switches on). A full tank refuses; the grant stays banked.
- **Shown:** gauge in the island menu (state, fuel / tank, burn rate, how to get fuel); placeholders `<ph:island_fuel>` ("Active", "7h 20m" or "Asleep") and `<ph:island_fuel_state>`; a join message when your island ran out of fuel ("ran out of fuel 2h ago") and one line to online members when it falls asleep. The scoreboard has no fuel line yet (the island layout is full); add `| Fuel: <ph:island_fuel>` to `messages/hud.yml` if wanted.

**Safety**
- Tanks live in memory and are saved every `fuel.save-seconds` (30) and on stop; the time since the last save burns at the next start (nobody was active while the server was down). Every write (saves, grants, cells) runs in one ordered chain.
- Grants: one transaction writes the current value, then `UPDATE … SET fuel_ms = LEAST(fuel_ms + ?, cap) WHERE … AND fuel_ms < cap`. Cells: `… SET fuel_ms = fuel_ms + ? WHERE … AND fuel_ms + ? <= cap` plus the used-cell row (`production_used_cells`); a cell that doesn't fit rolls back completely.
- Audit (category `production`, `/logs`): `fuel_add` (island, minutes, source, actor, tank after, cell id) and `fuel_asleep`.
