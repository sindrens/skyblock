package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.bonus.BonusFamily;

import java.util.Locale;
import java.util.Optional;

/**
 * What the boost points of a personal category give (RANKS_TITLES_ACHIEVEMENTS §C3, AC2). Every percent boost goes into
 * one {@code BonusService} family, so the family cap still applies on top of this boost's own small cap. Never sell or
 * money (owner RTA-39): there is deliberately no SELL entry. Steps and caps: {@code achievements-settings.yml → boosts.*}.
 */
enum PersonalBoost {
    /** Skill XP family (default +0.3% per point, cap +10%). */
    SKILL_XP(BonusFamily.SKILL_XP),
    /** Custom tool XP family (+0.4%, cap +10%). */
    TOOL_XP(BonusFamily.TOOL_XP),
    /** Damage to mobs (+0.3%, cap +8%). The family is applied to mobs only, never in PvP. */
    MOB_DAMAGE(BonusFamily.MOB_DAMAGE),
    /** Rare finds family (+0.3%, cap +8%). */
    RARE_FINDS(BonusFamily.RARE_FINDS),
    /** Crystals family (+0.25%, cap +6%). */
    CRYSTALS(BonusFamily.CRYSTALS),
    /** Extra auction listing slots at point thresholds (default +1 at 12, +2 at 30). Not a bonus family. */
    AH_SLOTS(null),
    /** The category gives no boost. */
    NONE(null);

    private final BonusFamily family;

    PersonalBoost(BonusFamily family) {
        this.family = family;
    }

    /** The bonus family this boost adds to, or null (AH slots, none). */
    BonusFamily family() {
        return family;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<PersonalBoost> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.of(NONE);
        }
        String key = id.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        for (PersonalBoost b : values()) {
            if (b.id().equals(key)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    /** Hot path (every bonus lookup, per block/kill): one EnumMap read, no array copy. */
    static PersonalBoost byFamily(BonusFamily family) {
        return family == null ? null : BY_FAMILY.get(family);
    }

    private static final java.util.Map<BonusFamily, PersonalBoost> BY_FAMILY = byFamilyMap();

    private static java.util.Map<BonusFamily, PersonalBoost> byFamilyMap() {
        java.util.Map<BonusFamily, PersonalBoost> out = new java.util.EnumMap<>(BonusFamily.class);
        for (PersonalBoost b : values()) {
            if (b.family != null) {
                out.put(b.family, b);
            }
        }
        return out;
    }
}
