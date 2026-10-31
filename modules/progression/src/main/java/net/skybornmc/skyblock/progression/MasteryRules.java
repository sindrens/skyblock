package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.bonus.BonusFamily;

import java.util.Map;
import java.util.UUID;

/**
 * The rules of island masteries and Ascension (PHASE-5 M5.5) without Bukkit: what the next rank needs, island level
 * points, the effect values the hooks read (bonus families, Treasury fee step, Wings, homes, satchel) and the keys that
 * make a purchase happen exactly once.
 */
final class MasteryRules {

    /** Ledger reason (and economy audit action) of a mastery or Ascension payment from the island bank. */
    static final String LEDGER_REASON = "island_mastery";
    /** The stored id of Ascension in {@code island_masteries} (its "rank" is the number of Ascension levels). */
    static final String ASCENSION_ID = "ascension";

    private MasteryRules() {
    }

    /** State of a mastery's next rank (or the next Ascension level) for one viewer, in the order the checks run. */
    enum Status {
        /** Switched off (bought ranks keep working). */
        DISABLED,
        /** The feature behind it does not exist yet (hidden in the menu). */
        COMING_SOON,
        /** Every rank bought (Ascension: the optional max reached). */
        MAXED,
        /** An upgrade must be maxed first (Market Contacts: Sell Multiplier; Ascension: everything). */
        NEEDS_MAXED,
        /** The pillar is below the rank's {@code pillar-level}. */
        PILLAR_LEVEL,
        /** The island level is below the rank's {@code island-level}. */
        ISLAND_LEVEL,
        /** The challenge book tasks of the next rank are not all done (or the task engine is not loaded yet). */
        TASKS_OPEN,
        /** The viewer may not spend the island bank (island permission "upgrades"). */
        NO_PERMISSION,
        /** The island bank holds less than the price (only when the balance is known). */
        NO_MONEY,
        /** Everything is met: one click buys it. */
        READY;

        /** Every island requirement is met (money and permission may still be missing): "ready to buy" for the island. */
        boolean unlocked() {
            return this == NO_PERMISSION || this == NO_MONEY || this == READY;
        }
    }

    /**
     * What the island and the viewer have now.
     *
     * @param requiresMet every {@code requires-maxed} upgrade is maxed
     * @param pillarLevel the level of the mastery's pillar (ignored without a pillar)
     * @param islandLevel the island's level now
     * @param tasksDone   every task of the next rank is done
     * @param permission  the viewer has the island permission "upgrades"
     * @param bank        the island bank balance, or a negative number when not known yet
     */
    record Gate(boolean requiresMet, int pillarLevel, int islandLevel, boolean tasksDone, boolean permission, long bank) {
    }

    static Status status(MasteryCatalog.Def def, int current, Gate gate) {
        if (!def.enabled()) {
            return Status.DISABLED;
        }
        if (!def.effect().live()) {
            return Status.COMING_SOON;
        }
        MasteryCatalog.Rank next = def.rank(current + 1);
        if (next == null) {
            return Status.MAXED;
        }
        if (!gate.requiresMet()) {
            return Status.NEEDS_MAXED;
        }
        if (def.pillar() != null && gate.pillarLevel() < next.pillarLevel()) {
            return Status.PILLAR_LEVEL;
        }
        if (gate.islandLevel() < next.islandLevel()) {
            return Status.ISLAND_LEVEL;
        }
        if (!gate.tasksDone()) {
            return Status.TASKS_OPEN;
        }
        return money(gate, next.cost());
    }

    private static Status money(Gate gate, long cost) {
        if (!gate.permission()) {
            return Status.NO_PERMISSION;
        }
        if (gate.bank() >= 0 && gate.bank() < cost) {
            return Status.NO_MONEY;
        }
        return Status.READY;
    }

    /**
     * The next Ascension level: switched on, everything maxed (every shown upgrade and mastery), below the optional max,
     * a price that fits a long. {@code gate.requiresMet} = everything maxed; levels and tasks are not used.
     */
    static Status ascensionStatus(boolean enabled, int current, int maxLevels, long cost, Gate gate) {
        if (!enabled) {
            return Status.DISABLED;
        }
        if ((maxLevels > 0 && current >= maxLevels) || cost < 0) {
            return Status.MAXED;
        }
        if (!gate.requiresMet()) {
            return Status.NEEDS_MAXED;
        }
        return money(gate, cost);
    }

    /** True when the next rank's tasks are done: the task engine must be loaded; a rank no task lists is open. */
    static boolean tasksDone(MasteryCatalog.Def def, int current, boolean engineReady, java.util.function.Predicate<String> unlocked) {
        return engineReady && unlocked.test(def.unlockKey(current + 1));
    }

    /** Every {@code requires-maxed} upgrade of the mastery is at its last level (an unknown upgrade id never is). */
    static boolean requiresMet(MasteryCatalog.Def def, UpgradeCatalog upgrades, Map<String, Integer> upgradeLevels) {
        for (String id : def.requiresMaxed()) {
            UpgradeCatalog.Def u = upgrades.get(id).orElse(null);
            if (u == null || level(upgradeLevels, id) < u.maxLevel()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ascension opens when everything is maxed: every buyable upgrade and every buyable (shown) mastery at its last
     * level. Hidden ones ("coming soon") don't block. An empty catalog never opens it.
     */
    static boolean allMaxed(UpgradeCatalog upgrades, Map<String, Integer> upgradeLevels, MasteryCatalog masteries,
                            Map<String, Integer> masteryRanks) {
        boolean any = false;
        for (UpgradeCatalog.Def u : upgrades.all()) {
            if (u.buyable()) {
                any = true;
                if (level(upgradeLevels, u.id()) < u.maxLevel()) {
                    return false;
                }
            }
        }
        for (MasteryCatalog.Def m : masteries.all()) {
            if (m.buyable()) {
                any = true;
                if (level(masteryRanks, m.id()) < m.maxRank()) {
                    return false;
                }
            }
        }
        return any;
    }

    private static int level(Map<String, Integer> levels, String id) {
        Integer v = levels == null ? null : levels.get(id);
        return v == null ? 0 : v;
    }

    // ------------------------------------------------------------------ Ascension price (ECONOMY_BALANCE §5.4)

    /**
     * Price of Ascension level {@code level} (1-based): {@code base × growth^(startLevel + level − 2)}, i.e. Ascension 1
     * costs like level {@code startLevel} of the endless curve (21 = after the 20 Market Contacts levels). Rounded to 3
     * significant digits like the catalogs. -1 when it no longer fits a long (no more levels can be bought).
     */
    static long ascensionCost(long base, double growth, int startLevel, int level) {
        if (level < 1 || base < 0 || growth < 1 || startLevel < 1) {
            return -1;
        }
        double v = base * Math.pow(growth, (double) startLevel + level - 2);
        if (!Double.isFinite(v) || v >= 9.0e18) {
            return -1;
        }
        return nice(v);
    }

    /** Rounds to 3 significant digits (same as the catalog generators). */
    static long nice(double v) {
        if (v <= 0) {
            return 0;
        }
        int digits = 3 - (int) Math.floor(Math.log10(v)) - 1;
        if (digits >= 0) {
            return Math.round(v);
        }
        double scale = Math.pow(10, -digits);
        return (long) (Math.round(v / scale) * scale);
    }

    // ------------------------------------------------------------------ island level points (owner P5-17, P5-24)

    /**
     * Raw island level points (before caps) of every bought mastery rank (by price tier, the upgrades' P5-17 rule) and
     * every Ascension level ({@code perAscension} each, plan: 1.5).
     */
    static double points(MasteryCatalog catalog, Map<String, Integer> bought, UpgradeRules.Points points, double perAscension) {
        if (bought == null || bought.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Map.Entry<String, Integer> e : bought.entrySet()) {
            if (ASCENSION_ID.equals(e.getKey())) {
                sum += Math.max(0, e.getValue()) * perAscension;
                continue;
            }
            MasteryCatalog.Def def = catalog.get(e.getKey()).orElse(null);
            if (def == null) {
                continue;
            }
            int rank = Math.min(e.getValue(), def.maxRank());
            for (int n = 1; n <= rank; n++) {
                sum += points.of(def.rank(n).tier());
            }
        }
        return sum;
    }

    // ------------------------------------------------------------------ effects

    /** The value of an effect on an island: the highest among the masteries with that effect, 0 when none is bought. */
    static double value(MasteryCatalog catalog, Map<String, Integer> bought, MasteryEffect effect) {
        if (bought == null || bought.isEmpty()) {
            return 0;
        }
        double best = 0;
        for (Map.Entry<String, Integer> e : bought.entrySet()) {
            MasteryCatalog.Def def = catalog.get(e.getKey()).orElse(null);
            if (def != null && def.effect() == effect && e.getValue() > 0) {
                best = Math.max(best, def.valueAt(e.getValue()));
            }
        }
        return best;
    }

    /** A whole-number effect (homes, fee steps). */
    static int whole(MasteryCatalog catalog, Map<String, Integer> bought, MasteryEffect effect) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(value(catalog, bought, effect)));
    }

    /**
     * The masteries' part of a bonus family for one of the island's members (percent, before the family cap, which the
     * BonusService applies on the total). Effects limited to an activity (Timber: foraging) only answer that activity.
     */
    static double bonusPercent(MasteryCatalog catalog, Map<String, Integer> bought, BonusFamily family, String activity) {
        if (family == null) {
            return 0;
        }
        double sum = 0;
        for (MasteryEffect effect : MasteryEffect.values()) {
            if (effect.family() != family || (effect.activity() != null && !effect.activity().equals(activity))) {
                continue;
            }
            sum += value(catalog, bought, effect);
        }
        return sum;
    }

    /** Deep Pockets: extra satchel items for a satchel of {@code capacity} items (percent of it, rounded down). */
    static int satchelBonus(long capacity, double percent) {
        if (capacity <= 0 || percent <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.floor(capacity * percent / 100.0));
    }

    /**
     * Shared island homes (Homestead, owner P5-21): how many named homes the island may have, on top of the island home.
     * Base 1 = the island home, +1 per Homestead rank.
     */
    static int extraHomes(MasteryCatalog catalog, Map<String, Integer> bought) {
        return Math.max(0, whole(catalog, bought, MasteryEffect.HOMESTEAD));
    }

    // ------------------------------------------------------------------ keys

    /** The idempotency key of the payment: one rank of one mastery is paid once per island and season. ≤ 96 chars. */
    static String idempotencyKey(int season, UUID island, String masteryId, int rank) {
        return "mastery:" + season + ":" + island + ":" + masteryId + ":" + rank;
    }

    /** Ledger reference shown in the bank history. ≤ 64 chars. */
    static String reference(String masteryId, int rank) {
        return "mastery:" + masteryId + ":" + rank;
    }
}
