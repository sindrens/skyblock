package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.Locale;
import java.util.Optional;

/**
 * Book pools by activity (ENCHANTS §3.1, EN6). A book drop or an Enchanter roll picks a rarity first, then an enchant of
 * that rarity from the pool. The tier that gates enchants ("From" column) comes from the skill of the pool's wing.
 */
enum Pool {
    MINING(SkillType.MINING),
    FORAGING(SkillType.FORAGING),
    FARMING(SkillType.FARMING),
    COMBAT(SkillType.COMBAT),
    FISHING(SkillType.FISHING),
    /** Island stacked mobs (E3): only the stacked-mob enchants and Plunder. Tier = Combat. */
    STACKS(SkillType.COMBAT);

    private final SkillType skill;

    Pool(SkillType skill) {
        this.skill = skill;
    }

    SkillType skill() {
        return skill;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<Pool> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (Pool p : values()) {
            if (p.id().equals(key)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
