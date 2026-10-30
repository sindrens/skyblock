package net.skybornmc.skyblock.common.booster;

import net.skybornmc.skyblock.common.bonus.BonusFamily;

import java.util.Locale;
import java.util.Optional;

/**
 * Booster types (PHASE-10-REWARDS.md M10.2). Every type adds to exactly one {@link BonusFamily}, so boosters always go
 * through the family caps (the +300% sell cap for {@link #SELL}, owner decision O11).
 */
public enum BoosterType {
    /** Sell prices. */
    SELL(BonusFamily.SELL),
    /** Skill XP. */
    SKILL_XP(BonusFamily.SKILL_XP),
    /** Custom tool XP. */
    TOOL_XP(BonusFamily.TOOL_XP),
    /** Crystals earned by actions. */
    CRYSTALS(BonusFamily.CRYSTALS);

    private final BonusFamily family;

    BoosterType(BonusFamily family) {
        this.family = family;
    }

    /** The bonus family this type adds to. */
    public BonusFamily family() {
        return family;
    }

    /** Lowercase id used in configs, messages, items and the database, e.g. {@code skill_xp}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The type of an id ({@code sell}, {@code skill_xp} / {@code skill-xp} ...), case-insensitive. */
    public static Optional<BoosterType> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (BoosterType t : values()) {
            if (t.id().equals(key)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /** The type that adds to {@code family}, if any. */
    public static Optional<BoosterType> of(BonusFamily family) {
        for (BoosterType t : values()) {
            if (t.family == family) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
