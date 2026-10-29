package net.skybornmc.skyblock.depths.zones;

import net.skybornmc.skyblock.common.skills.SkillType;

/**
 * Whether a player may enter a zone, and why (for "Mining 15 needed · 9/15" texts).
 *
 * @param allowed  true if the player may enter
 * @param skill    the skill that unlocks the zone
 * @param required level needed
 * @param level    the player's level (0 if unknown)
 * @param checked  false when no skill system is running: zones are then open (locked = false)
 */
public record ZoneAccess(boolean allowed, SkillType skill, int required, int level, boolean checked) {

    /**
     * @param level the player's level, or {@code null} when skills are not available (then the zone is open)
     */
    public static ZoneAccess of(Zone zone, Integer level) {
        SkillType skill = zone.aspect().skill();
        if (level == null) {
            return new ZoneAccess(true, skill, zone.requiredLevel(), 0, false);
        }
        return new ZoneAccess(level >= zone.requiredLevel(), skill, zone.requiredLevel(), level, true);
    }
}
