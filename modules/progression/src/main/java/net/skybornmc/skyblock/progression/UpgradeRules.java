package net.skybornmc.skyblock.progression;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The rules of island upgrades (PHASE-5 M5.4) without Bukkit: what the next level needs, island level points, the effect
 * values the islands, stacking, economy and bonus hooks read, and the keys that make a purchase happen exactly once.
 */
final class UpgradeRules {

    /** Ledger reason (and economy audit action) of an upgrade payment from the island bank. */
    static final String LEDGER_REASON = "island_upgrade";

    private UpgradeRules() {
    }

    /** State of an upgrade's next level for one viewer, in the order the checks run. */
    enum Status {
        /** Switched off in upgrades.yml (bought levels keep working). */
        DISABLED,
        /** The feature behind it does not exist yet. */
        COMING_SOON,
        /** Every level bought. */
        MAXED,
        /** The challenge book tasks of the next level are not all done (or the task engine is not loaded yet). */
        TASKS_OPEN,
        /** The island level is below the level's {@code min-island-level}. */
        ISLAND_LEVEL,
        /** The viewer may not spend the island bank on upgrades (island permission "upgrades"). */
        NO_PERMISSION,
        /** The island bank holds less than the price (only when the balance is known). */
        NO_MONEY,
        /** Everything is met: one click buys it. */
        READY;

        /** Tasks and island level are met (money and permission may still be missing): "ready to buy" for the island. */
        boolean unlocked() {
            return this == NO_PERMISSION || this == NO_MONEY || this == READY;
        }
    }

    /**
     * @param current     the bought level
     * @param tasksDone   every task of the next level is done
     * @param islandLevel the island's level now
     * @param permission  the viewer has the island permission "upgrades"
     * @param bank        the island bank balance, or a negative number when not known yet
     */
    static Status status(UpgradeCatalog.Def def, int current, boolean tasksDone, int islandLevel, boolean permission, long bank) {
        if (!def.enabled()) {
            return Status.DISABLED;
        }
        if (!def.effect().live()) {
            return Status.COMING_SOON;
        }
        UpgradeCatalog.Level next = def.level(current + 1);
        if (next == null) {
            return Status.MAXED;
        }
        if (!tasksDone) {
            return Status.TASKS_OPEN;
        }
        if (islandLevel < next.minIslandLevel()) {
            return Status.ISLAND_LEVEL;
        }
        if (!permission) {
            return Status.NO_PERMISSION;
        }
        if (bank >= 0 && bank < next.cost()) {
            return Status.NO_MONEY;
        }
        return Status.READY;
    }

    /** True when the next level's tasks are done: the task engine must be loaded; a level no task lists is open. */
    static boolean tasksDone(UpgradeCatalog.Def def, int current, boolean engineReady, Predicate<String> unlocked) {
        return engineReady && unlocked.test(def.unlockKey(current + 1));
    }

    // ------------------------------------------------------------------ island level points (owner P5-17)

    /** Island level points of one level by its task tier: tier 1-3 low, 4-6 mid, 7+ high (0.5 / 1 / 1.5 by default). */
    record Points(double low, double mid, double high, int midFromTier, int highFromTier) {

        double of(int tier) {
            return tier >= highFromTier ? high : tier >= midFromTier ? mid : low;
        }
    }

    /** Raw island level points (before caps) of every bought level of every upgrade of the island. */
    static double points(UpgradeCatalog catalog, Map<String, Integer> bought, Points points) {
        if (bought == null || bought.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Map.Entry<String, Integer> e : bought.entrySet()) {
            UpgradeCatalog.Def def = catalog.get(e.getKey()).orElse(null);
            if (def == null) {
                continue;
            }
            int level = Math.min(e.getValue(), def.maxLevel());
            for (int n = 1; n <= level; n++) {
                sum += points.of(def.level(n).tier());
            }
        }
        return sum;
    }

    // ------------------------------------------------------------------ effects

    /**
     * The value of an effect on an island: the highest value among the upgrades with that effect (switched-off upgrades
     * included, so a bought level keeps working), 0 when none is bought.
     */
    static double value(UpgradeCatalog catalog, Map<String, Integer> bought, UpgradeEffect effect) {
        if (bought == null || bought.isEmpty()) {
            return 0;
        }
        double best = 0;
        for (Map.Entry<String, Integer> e : bought.entrySet()) {
            UpgradeCatalog.Def def = catalog.get(e.getKey()).orElse(null);
            if (def != null && def.effect() == effect && e.getValue() > 0) {
                best = Math.max(best, def.valueAt(e.getValue()));
            }
        }
        return best;
    }

    /** Spawners per stacked block: empty without the upgrade (the stacking start value applies). */
    static OptionalInt spawnerStack(UpgradeCatalog catalog, Map<String, Integer> bought) {
        double v = value(catalog, bought, UpgradeEffect.SPAWNER_STACK);
        return v <= 0 ? OptionalInt.empty() : OptionalInt.of((int) Math.min(Integer.MAX_VALUE, Math.round(v)));
    }

    /** Spawner output multiplier: 1 + percent / 100. */
    static double spawnerRate(UpgradeCatalog catalog, Map<String, Integer> bought) {
        return 1.0 + value(catalog, bought, UpgradeEffect.SPAWNER_RATE) / 100.0;
    }

    /** A whole-number effect (bank levels, entity limit percent, slots). */
    static int whole(UpgradeCatalog catalog, Map<String, Integer> bought, UpgradeEffect effect) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(value(catalog, bought, effect)));
    }

    /**
     * The value an islands-module setting must have for the bought upgrades, or -1 when the upgrade does not raise it
     * (nothing bought, or the island already has as much or more, e.g. from staff or a paid size). Never lowers.
     */
    static int raiseTo(UpgradeCatalog catalog, Map<String, Integer> bought, UpgradeEffect effect, int now) {
        int target = whole(catalog, bought, effect);
        return target > 0 && target > now ? target : -1;
    }

    /** The bonus family value of an upgrade effect for one of the island's members (percent). */
    static double bonusPercent(UpgradeCatalog catalog, Map<String, Integer> bought, UpgradeEffect effect) {
        return value(catalog, bought, effect);
    }

    // ------------------------------------------------------------------ keys

    /** The idempotency key of the payment: one level of one upgrade is paid once per island and season. ≤ 96 chars. */
    static String idempotencyKey(int season, UUID island, String upgradeId, int level) {
        return "upgrade:" + season + ":" + island + ":" + upgradeId + ":" + level;
    }

    /** Ledger reference shown in the bank history. ≤ 64 chars. */
    static String reference(String upgradeId, int level) {
        return "upgrade:" + upgradeId + ":" + level;
    }

    /** Roman numeral of a level (I-XXX) for names like "Island Size II"; plain digits above 39. */
    static String roman(int n) {
        if (n <= 0 || n >= 40) {
            return String.valueOf(n);
        }
        String[] tens = {"", "X", "XX", "XXX"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return tens[n / 10] + ones[n % 10];
    }
}
