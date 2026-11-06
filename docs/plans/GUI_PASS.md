# GUI pass (plan, 7 Nov 2026 night)

Owner, 7 Nov 2026: "fix ALL GUIs ... do it properly", plus "&a colour on hoe and pickaxe, change names".
The owner is asleep, so the defaults below are used; each one is easy to change back.

## Done with this pass
1. **Tool names**: all four island tools have green (&a) names ("Island Hoe", "Island Pickaxe", "Island Rod",
   "Island Sword"); the prestige stars stay orange after the name. "Harvester Hoe" is now "Island Hoe".
2. **No glass panes as filler anywhere** (UI_STYLE §5.1): the /p punish menu fill and divider, the sell chest's divider row,
   the battle pass "nothing" tiles, the trash bin bar, the trade window's empty slots, divider and locked slots, the
   crate spin's dim slots.
3. **State tiles use dyes, not panes**: done = lime dye, now = yellow dye, later/locked = gray dye (daily, vote,
   milestones chain, pass, upgrade and mastery details, guide tasks, loadouts, enchant slots, boosters, top list,
   AH durations, permissions). Red stays only for danger and errors.
4. **Everything reachable from /menu**: Crates, Boosters, Vote and the Black Market get a tile in the You menu.
5. **No Depths/Forge text left in menus**: skills, settings, enchants, quests, mailbox, wardrobe, profiles, economy.
6. **Boss names** come from the messages file.

## Not changed (owner decides)
- 5-row menus (help topics, guide tasks) stay 5 rows; converting moves their buttons.
- "Coming soon" tiles in crates/pass/upgrades: they stay hidden when not built (as now).
- The Combat skill (can only be earned at /pve now) keeps its text "Fight at /pve".
