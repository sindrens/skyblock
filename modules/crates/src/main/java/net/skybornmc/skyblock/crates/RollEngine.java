package net.skybornmc.skyblock.crates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * The crate roll (pure, unit-tested; PHASE-10 M10.1). The server rolls and saves the result before anything is shown.
 *
 * <ul>
 *   <li><b>Weighted pick</b> by each reward's chance.</li>
 *   <li><b>Pity:</b> a counter per profile and crate counts opens without a top-band reward ({@code top: true}). When the
 *       next open would reach the crate's pity number, it is forced into the top band (weighted among the top rewards).
 *       A top-band reward resets the counter to 0.</li>
 *   <li><b>Duplicates:</b> a cosmetic (tag, title) re-rolls into one the opener does not own: first another id of the same
 *       reward, then another cosmetic reward of the same band. If they own them all, the reward pays its crystal value
 *       ({@link Roll#fallback}). Ids won earlier in the same batch count as owned.</li>
 * </ul>
 */
final class RollEngine {

    /**
     * One roll. {@code reward} = the reward finally given (after a duplicate re-roll), {@code cosmeticId} = the tag/title id
     * for cosmetics, {@code fallback} = pay the crystal value instead, {@code forced} = the pity forced the top band.
     */
    record Roll(CrateCatalog.Reward reward, String cosmeticId, boolean fallback, boolean forced) {

        boolean top() {
            return reward.top();
        }
    }

    /** The rolls of one batch and the pity counter after them. */
    record Batch(List<Roll> rolls, int pityAfter) {
    }

    private RollEngine() {
    }

    /** True when the next open (with {@code counter} opens since the last top reward) is forced into the top band. */
    static boolean forced(CrateCatalog.Crate crate, int counter) {
        return crate.pity() > 0 && crate.hasTop() && counter + 1 >= crate.pity();
    }

    /** The counter after a roll. */
    static int next(int counter, boolean top) {
        return top ? 0 : counter + 1;
    }

    /**
     * Rolls {@code count} opens.
     *
     * @param counter opens since the last top-band reward before this batch
     * @param owns    true for owned cosmetics, keys {@code tag:<id>} / {@code title:<id>}
     */
    static Batch roll(CrateCatalog.Crate crate, int counter, int count, RandomGenerator rng, Predicate<String> owns) {
        List<Roll> rolls = new ArrayList<>(Math.max(0, count));
        Set<String> won = new HashSet<>();
        Predicate<String> owned = key -> won.contains(key) || owns.test(key);
        int c = Math.max(0, counter);
        for (int i = 0; i < count; i++) {
            Roll r = rollOne(crate, c, rng, owned);
            if (r.cosmeticId() != null) {
                won.add(cosmeticKey(r.reward(), r.cosmeticId()));
            }
            c = next(c, r.top());
            rolls.add(r);
        }
        return new Batch(rolls, c);
    }

    static Roll rollOne(CrateCatalog.Crate crate, int counter, RandomGenerator rng, Predicate<String> owns) {
        boolean forced = forced(crate, counter);
        List<CrateCatalog.Reward> pool = forced ? crate.topRewards() : crate.rewards();
        CrateCatalog.Reward picked = pick(pool, rng);
        if (!picked.type().cosmetic()) {
            return new Roll(picked, null, false, forced);
        }
        String id = unowned(picked, rng, owns);
        if (id != null) {
            return new Roll(picked, id, false, forced);
        }
        // Duplicate: another cosmetic reward of the same band with something new in it.
        List<CrateCatalog.Reward> others = crate.rewards().stream()
                .filter(r -> r != picked && r.type().cosmetic() && r.top() == picked.top() && hasUnowned(r, owns))
                .toList();
        if (!others.isEmpty()) {
            CrateCatalog.Reward other = pick(others, rng);
            return new Roll(other, unowned(other, rng, owns), false, forced);
        }
        return new Roll(picked, null, true, forced);
    }

    /** {@code tag:<id>} or {@code title:<id>}. */
    static String cosmeticKey(CrateCatalog.Reward reward, String id) {
        return (reward.type() == RewardType.TITLE ? "title:" : "tag:") + id;
    }

    private static boolean hasUnowned(CrateCatalog.Reward reward, Predicate<String> owns) {
        return reward.ids().stream().anyMatch(id -> !owns.test(cosmeticKey(reward, id)));
    }

    private static String unowned(CrateCatalog.Reward reward, RandomGenerator rng, Predicate<String> owns) {
        List<String> free = reward.ids().stream().filter(id -> !owns.test(cosmeticKey(reward, id))).toList();
        return free.isEmpty() ? null : free.get(rng.nextInt(free.size()));
    }

    /** Weighted pick by chance; {@code pool} must not be empty. */
    static CrateCatalog.Reward pick(List<CrateCatalog.Reward> pool, RandomGenerator rng) {
        double total = 0;
        for (CrateCatalog.Reward r : pool) {
            total += r.chance();
        }
        double x = rng.nextDouble() * total;
        for (CrateCatalog.Reward r : pool) {
            x -= r.chance();
            if (x < 0) {
                return r;
            }
        }
        return pool.getLast();
    }
}
