package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.skills.SkillType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REVIEW3 R3-24: Combat XP only from kills inside Depths combat zones. */
class CombatXpRuleTest {

    private static final List<String> SOURCES = new SkillsConfig().combatDepthsSources;

    @Test
    void onlyDepthsKillsGiveCombatXp() {
        assertTrue(new SkillsConfig().combatOnlyInDepths, "on by default (owner R3-24)");
        assertTrue(CombatXpRule.allowed(SkillType.COMBAT, "depths_mob", true, SOURCES));
        assertFalse(CombatXpRule.allowed(SkillType.COMBAT, "mob_kill", true, SOURCES), "island / vanilla mob kills");
        assertFalse(CombatXpRule.allowed(SkillType.COMBAT, "stack_kill", true, SOURCES), "stacked spawner kills");
        assertFalse(CombatXpRule.allowed(SkillType.COMBAT, null, true, SOURCES));
        assertFalse(CombatXpRule.allowed(SkillType.COMBAT, "unknown", true, SOURCES));
    }

    @Test
    void otherSkillsAndTheSwitchAreUntouched() {
        assertTrue(CombatXpRule.allowed(SkillType.MINING, "depths_block", true, SOURCES));
        assertTrue(CombatXpRule.allowed(SkillType.FORAGING, "anything", true, SOURCES));
        assertTrue(CombatXpRule.allowed(SkillType.COMBAT, "mob_kill", false, SOURCES), "off = old behaviour");
    }

    @Test
    void theDepthsSourceMatchesTheDepthsModule() {
        assertTrue(SOURCES.contains("depths_mob"), "CombatZones.XP_SOURCE");
    }
}
