package net.skybornmc.skyblock.common.bonus;

import java.util.Locale;
import java.util.Optional;

/**
 * Bonus families (ENCHANTS §8.1, EN16). Every bonus from enchants, masteries, sets, skills, ranks, boosters and events
 * belongs to exactly one family. Inside a family the values <b>add up</b>, then the family cap applies once (the same
 * math as {@link net.skybornmc.skyblock.common.economy.MultiplierCap} for selling).
 *
 * <p>Values are percents ({@code 25} = +25%), except {@link #HEALTH} (extra hearts). The default caps here are only the
 * fallback: the live caps come from {@code enchanter.yml → caps.*} and, for {@link #SELL}, always from
 * {@code economy.yml → sell.multiplier-cap-percent} (owner decision #46: +300%).</p>
 */
public enum BonusFamily {
    /** Sell prices. Cap: the economy's sell cap (+300%). */
    SELL(300),
    /** Crystals earned by actions. */
    CRYSTALS(200),
    /** Extra drops per action (expected value). Also max 3 drops per action (enforced by the drop code). */
    EXTRA_DROPS(200),
    /** Break/chop speed and fishing wait. The hard cap of 6 blocks/s is enforced by the block engine, not here. */
    SPEED(200),
    /** Skill XP. */
    SKILL_XP(300),
    /** Custom tool XP. */
    TOOL_XP(100),
    /** Rare finds (blueprints, key fragments, geodes, rare fish). */
    RARE_FINDS(150),
    /** Area enchant throughput (one area enchant per tool by rule; this cap is a safety net). */
    AREA(100),
    /** Damage to mobs. */
    MOB_DAMAGE(200),
    /** Damage reduction from custom sources (percent of damage taken). */
    DAMAGE_REDUCTION(60),
    /** Extra max hearts from custom sources. */
    HEALTH(10),
    /** Crop growth speed near the player (WARDROBE §2.5, WD9: Cropwarden Robes, growth upgrades, events). */
    CROP_GROWTH(100),
    /**
     * Conquest stats (WARDROBE §2.5, WD9): the context's activity says which one ({@code envoy_luck}, {@code capture_speed},
     * {@code conquest_xp}, {@code death_loss}); the cap applies per stat. The warzone death-loss reduction is limited to
     * 50% by the code that applies it.
     */
    CONQUEST(60);

    private final double defaultCap;

    BonusFamily(double defaultCap) {
        this.defaultCap = defaultCap;
    }

    /** Fallback cap when no config set one. */
    public double defaultCap() {
        return defaultCap;
    }

    /** Lowercase id used in configs and messages, e.g. {@code extra_drops}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<BonusFamily> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (BonusFamily f : values()) {
            if (f.id().equals(key)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
