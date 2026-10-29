package net.skybornmc.skyblock.depths.zones;

import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.Locale;
import java.util.Optional;

/** The three Depths wings. Each wing's tiers unlock with one skill. */
public enum Aspect {
    MINE(SkillType.MINING),
    FOREST(SkillType.FORAGING),
    COMBAT(SkillType.COMBAT);

    private final SkillType skill;

    Aspect(SkillType skill) {
        this.skill = skill;
    }

    /** The skill whose level unlocks this wing's tiers. */
    public SkillType skill() {
        return skill;
    }

    /** Lowercase id used in zones.yml and messages ({@code mine}, {@code forest}, {@code combat}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Aspect> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (Aspect a : values()) {
            if (a.id().equalsIgnoreCase(id.trim())) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }
}
