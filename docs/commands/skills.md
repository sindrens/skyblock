# Skills module (`modules/skills`): M4.5

Skill XP and levels (Mining, Foraging, Combat; Farming and Fishing locked until Phase 7). Settings, level curve and XP tables: `skills.yml`; texts: `messages/skills.yml`; data: table `skills`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/skills [player]` | Skills menu: level, progress bar, XP in the level, total XP, next unlock and how to earn XP per skill; another player's skills live (online) or loaded (offline) | `skyblock.skills` (everyone); others `skyblock.skills.others` (everyone) | ✅ |

Not built (not approved yet, see `docs/OPEN_QUESTIONS.md`): staff `/skills set|add <player> <skill> <amount>`.

For other modules: `core.services().optional(SkillService.class)` (`common/skills`) with `addXp`, `level`, `xp`, `progress`, `xpForLevel`, `levelFor`, `loadXp`, `setXp` (audited, staff); events `SkillXpGainEvent` (before XP is added: multipliers, cancel) and `SkillLevelUpEvent`. Entities with the scoreboard tag `skyblock_no_skill_xp` give no automatic Combat XP (for modules that pay their own). Mining/Foraging XP comes from the depths module's `ResourceBreakEvent` (the only cross-module class use: `compileOnly`, see `DepthsXpListener`).

Placeholders: `<ph:skill_mining_level>`, `<ph:skill_mining_progress>` (percent, 0-100), same for `foraging`, `combat`, `farming`, `fishing`.

`/settings` toggle: `skill-xp-bar` (Display): XP gains in the action bar.

Audit: level-ups (`world`, action `skill_level_up`), staff XP changes through the service (`staff`, action `skill_set`).

Permission definitions: `modules/skills/src/main/resources/permissions/skills.yml`. Rank grants: `server/permissions/skills.txt`.
