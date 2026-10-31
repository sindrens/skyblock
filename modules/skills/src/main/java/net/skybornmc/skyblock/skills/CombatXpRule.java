package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.Collection;
import java.util.Locale;

/**
 * REVIEW3 R3-24: Combat XP only from kills inside Depths combat zones. With {@code combat.only-in-depths} on, a Combat
 * XP gain is only accepted from the listed sources ({@code depths_mob}: the depths module pays it for kills in a combat
 * zone); island kills, spawner mobs and stacked kills give none. Other skills are never touched. Pure; unit-tested.
 */
final class CombatXpRule {

    private CombatXpRule() {
    }

    static boolean allowed(SkillType skill, String source, boolean onlyInDepths, Collection<String> depthsSources) {
        if (skill != SkillType.COMBAT || !onlyInDepths) {
            return true;
        }
        if (source == null || depthsSources == null) {
            return false;
        }
        String s = source.strip().toLowerCase(Locale.ROOT);
        return depthsSources.stream().anyMatch(x -> x != null && x.strip().equalsIgnoreCase(s));
    }
}
