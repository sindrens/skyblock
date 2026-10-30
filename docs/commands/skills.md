# Skills module (`modules/skills`): M4.5

Skill XP and levels (Mining, Foraging, Combat; Farming and Fishing locked until Phase 7). Settings, level curve and XP tables: `skills.yml`; texts: `messages/skills.yml`; data: table `skills`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/skills [player]` | Skills menu: level, progress bar, XP in the level, total XP, next unlock and how to earn XP per skill; another player's skills live (online) or loaded (offline) | `skyblock.skills` (everyone); others `skyblock.skills.others` (everyone) | ✅ |
| `/skills set <player> <skill> <levels>` | Staff (owner #68): set an online player's skill to that level (start of the level), clamped to 0..`curve.max-level` (150). Audited (`staff`, `skill_set`); the player is told | `skyblock.skills.admin` (admin + owner) | ✅ |
| `/skills add <player> <skill> <levels>` | Staff: add levels (keeps the progress inside the level), capped at the max level. Audited; the player is told | `skyblock.skills.admin` (admin + owner) | ✅ |

Level curve: max level 150 (owner #65). Tiers unlock up to level 66 (unchanged); after `curve.end-break-level` (66) each level costs `curve.end-growth` (1.02) times the one before. See `docs/ECONOMY_BALANCE.md` §3.4.

For other modules: `core.services().optional(SkillService.class)` (`common/skills`) with `addXp`, `level`, `xp`, `progress`, `xpForLevel`, `levelFor`, `loadXp`, `setXp` (audited, staff); events `SkillXpGainEvent` (before XP is added: multipliers, cancel) and `SkillLevelUpEvent`. Entities with the scoreboard tag `skyblock_no_skill_xp` give no automatic Combat XP (for modules that pay their own). Mining/Foraging XP comes from the depths module's `ResourceBreakEvent` (the only cross-module class use: `compileOnly`, see `DepthsXpListener`).

Placeholders: `<ph:skill_mining_level>`, `<ph:skill_mining_progress>` (percent, 0-100), same for `foraging`, `combat`, `farming`, `fishing`.

`/settings` toggle: `skill-xp-bar` (Display): XP gains in the action bar.

Audit: level-ups (`world`, action `skill_level_up`), staff XP changes through the service (`staff`, action `skill_set`).

Permission definitions: `modules/skills/src/main/resources/permissions/skills.yml`. Rank grants: `server/permissions/skills.txt`.

Profiles (PROF): skill XP belongs to the active profile (table `skills`, column `player` = profile key; profile 1 = the player UUID, so old rows are profile 1). A switch saves the old profile's XP and loads the new one's; `/skills <player>` of an offline player shows their last active profile. A deleted profile's rows are removed.
