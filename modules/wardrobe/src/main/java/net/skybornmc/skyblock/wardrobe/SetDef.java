package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.item.Rarity;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * One wardrobe set from {@code sets.yml} (WARDROBE §2). Pure data, validated by {@link SetRegistry}.
 *
 * @param enabledFrom feature that must exist before the set can be bought/redeemed ({@code conquest}, {@code fishing}),
 *                    or empty; until then the set shows "Arrives with …"
 * @param activity    what the set boosts (filter group and set XP source): mining, foraging, farming, combat, fishing,
 *                    conquest, selling, skills, gathering, all
 * @param two         2-piece bonuses
 * @param four        4-piece bonuses (added on top of the 2-piece ones)
 */
record SetDef(String id, boolean enabled, String enabledFrom, String name, String shortName, String activity, int tier,
              Look look, Source source, Gate gate, List<Bonus> two, List<Bonus> four) {

    /** Highest tier; tier IV sets cover 3+ families and can never be merged (WD22). */
    static final int MAX_TIER = 4;

    SetDef {
        two = List.copyOf(two);
        four = List.copyOf(four);
        enabledFrom = enabledFrom == null ? "" : enabledFrom;
    }

    /** Rarity color by tier (WD1): I uncommon, II rare, III epic, IV legendary. */
    Rarity rarity() {
        return switch (tier) {
            case 1 -> Rarity.UNCOMMON;
            case 2 -> Rarity.RARE;
            case 3 -> Rarity.EPIC;
            default -> Rarity.LEGENDARY;
        };
    }

    /** Tier I–III can be merged (W2). */
    boolean mergeable() {
        return tier < MAX_TIER;
    }

    /** Every bonus family the set feeds (merge rule "no shared family", W2). */
    Set<BonusFamily> families() {
        Set<BonusFamily> out = EnumSet.noneOf(BonusFamily.class);
        two.forEach(b -> out.add(b.family()));
        four.forEach(b -> out.add(b.family()));
        return out;
    }

    /** The filter group in /wardrobe: the activity when it is one of the six, otherwise {@code other}. */
    String group() {
        return switch (activity) {
            case "mining", "foraging", "farming", "combat", "fishing", "conquest" -> activity;
            default -> "other";
        };
    }

    // ------------------------------------------------------------------ parts

    /**
     * The vanilla look (WD2): base material family, leather dye, trim pattern and material; the upgraded trim material
     * is used from set level 10 on (W2).
     *
     * @param dye RGB color for leather looks, or -1
     */
    record Look(Base base, int dye, String trimPattern, String trimMaterial, String upgradedTrimMaterial) {
    }

    /** Vanilla armor families a look can use. */
    enum Base {
        LEATHER, CHAINMAIL, IRON, GOLDEN, DIAMOND, NETHERITE;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<Base> byKey(String key) {
            if (key == null) {
                return Optional.empty();
            }
            String k = key.trim().toLowerCase(Locale.ROOT);
            if (k.equals("gold")) {
                k = "golden";
            }
            for (Base b : values()) {
                if (b.key().equals(k)) {
                    return Optional.of(b);
                }
            }
            return Optional.empty();
        }
    }

    /** Where the set comes from (WD4). */
    enum SourceType {
        MONEY(Currency.MONEY), CRYSTALS(Currency.CRYSTALS), GEMS(Currency.GEMS), CRATE(null);

        private final Currency currency;

        SourceType(Currency currency) {
            this.currency = currency;
        }

        /** The currency it is bought with, or null for crate-only sets. */
        Currency currency() {
            return currency;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<SourceType> byKey(String key) {
            if (key == null) {
                return Optional.empty();
            }
            for (SourceType t : values()) {
                if (t.key().equalsIgnoreCase(key.trim())) {
                    return Optional.of(t);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * @param price  whole units of the source currency (0 for crate-only sets)
     * @param crates crate entries for the crates plan, shown as "Also from: …" (e.g. {@code blaze})
     */
    record Source(SourceType type, long price, List<String> crates) {
        Source {
            crates = List.copyOf(crates);
        }

        boolean buyable() {
            return type.currency() != null && price > 0;
        }
    }

    /**
     * What a player needs to buy or redeem the set (WD1). Any one configured part is enough; nothing configured = open.
     *
     * @param wing       {@code any} or a wing (mining, foraging, combat, farming, fishing) for {@code tier}
     * @param tier       Depths tier needed in that wing (0 = not used)
     * @param anySkill   alternative: any skill at this level (0 = not used)
     * @param skill      alternative: this skill (empty = not used) …
     * @param skillLevel … at this level
     */
    record Gate(String wing, int tier, int anySkill, String skill, int skillLevel) {
        static final Gate NONE = new Gate("any", 0, 0, "", 0);

        boolean open() {
            return tier <= 0 && anySkill <= 0 && (skill.isEmpty() || skillLevel <= 0);
        }
    }

    /**
     * One bonus line at 1× (WD3).
     *
     * @param activities the activities it counts for (the bonus context's activity); empty = everywhere
     * @param text       key under {@code bonus-text.} in messages/wardrobe.yml (with a {@code <value>} placeholder)
     */
    record Bonus(BonusFamily family, double value, List<String> activities, String text) {
        Bonus {
            activities = List.copyOf(activities);
        }

        /** Whether this bonus counts for a context activity (null activity: only bonuses that count everywhere). */
        boolean appliesTo(String activity) {
            return activities.isEmpty() || (activity != null && activities.contains(activity));
        }
    }
}
