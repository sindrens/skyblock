# Tutorial module (`modules/tutorial`): the guided tour /tutorial (docs/planning/PLAN_TUTORIAL_CHAT.md)

| Command | Description | Permission | Status |
|---|---|---|---|
| `/tutorial` (`/quest`) | The quest book: your chapter, its steps (✔ done, ▶ next with a hint, • to do; click a step to go there), then every chapter with the overall progress. Bedrock players get the same as a menu | `skyblock.tutorial.use` | ✅ |
| `/tutorial menu` | The tutorial as a menu: chapter, steps and every chapter | `skyblock.tutorial.use` | ✅ |
| `/tutorial skip` | End the tour now (confirm; no rewards) | `skyblock.tutorial.use` | ✅ |
| `/tutorial hide` / `show` | Hide or show the tutorial boss bar | `skyblock.tutorial.use` | ✅ |
| `/tutorial track` | A particle trail toward the next NPC for a few seconds | `skyblock.tutorial.use` | ✅ |
| `/tutorial admin reset <player>` | Start a player's tour over (rewards are never paid twice) | `skyblock.tutorial.admin` | ✅ |
| `/tutorial admin step <player> <step>` | Mark one step done (tab-completes `chapter.step`) | `skyblock.tutorial.admin` | ✅ |
| `/tutorial admin stats` | Players who started, skipped and finished each chapter | `skyblock.tutorial.admin` | ✅ |

`/start` (essentials) opens the tutorial's start pages when this module runs: the chapter, its steps and Island, Farm,
Mine and the book. Talking to an NPC of an open step plays its dialog (typewriter text above the NPC, the line in
chat; click again to finish the line, twice to skip) and then opens what the NPC does.

**Placing the spawn NPCs (staff):** the NPCs come from the spawn build's NPC points (npcs module). Give each point the
role in `npcs.yml → npcs.roles` (`id: role`): `guide` (Rowan), `farm_merchant` (Bramble, at /farm), `mine_merchant`
(Flint, at /mine), `shop` (Juniper), `bank` (The Banker), `auction` (Ollie), `crates`, `tinker`, `sage`. A role
without a placed NPC counts as done in the tour, so a missing NPC never blocks anyone.
