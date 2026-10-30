package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Turns the rolls of one batch into concrete grants (pure, unit-tested): pouch amounts at the opener's tier, the enchant
 * of a book, the set of a token, the spawner type, and which grants pay crystals instead (a reward whose system is not
 * live yet, or a cosmetic the opener already owns all of). Everything the server needs is decided here, before the
 * transaction, so what is saved is exactly what is shown.
 */
final class OpenPlan {

    /** Kind stored for a crystal fallback (coming soon, all cosmetics owned, reward system off). */
    static final String FALLBACK = "fallback";

    /**
     * What the plan needs from the server, snapshotted on the main thread before the batch.
     *
     * @param owns         owned cosmetics ({@code tag:<id>}, {@code title:<id>})
     * @param available    true when a reward's system is live (enchants module for books, a valid material ...)
     * @param bookIds      enabled enchant ids of a rarity pool
     * @param spawnerTypes spawner types of a tier
     */
    record Context(int tier, long[] refPerMinute, double crystalGrowth, Predicate<String> owns,
                   Predicate<CrateCatalog.Reward> available, Function<String, List<String>> bookIds,
                   IntFunction<List<String>> spawnerTypes) {
    }

    /** One open's reward. {@code kind} = a {@link RewardType} key or {@link #FALLBACK}; {@code bound} = opened with a bound key. */
    record Grant(UUID openId, CrateCatalog.Reward reward, String kind, String ref, long amount, boolean bound, boolean top,
                 boolean forced) {

        /** Paid inside the open's transaction (currencies, keys, crystal fallback). */
        boolean inTransaction() {
            return currency() != null || kind.equals(RewardType.KEY.key());
        }

        /** The currency this grant pays, or null. */
        Currency currency() {
            if (kind.equals(RewardType.MONEY.key())) {
                return Currency.MONEY;
            }
            if (kind.equals(RewardType.CRYSTALS.key()) || kind.equals(FALLBACK)) {
                return Currency.CRYSTALS;
            }
            if (kind.equals(RewardType.SHARDS.key())) {
                return Currency.SHARDS;
            }
            return null;
        }

        boolean fallback() {
            return kind.equals(FALLBACK);
        }
    }

    /** Keys of one crate a batch gives: bound and tradeable counts. */
    record KeyGrant(String crate, long bound, long tradeable) {
    }

    /** A whole batch. */
    record Plan(UUID batchId, CrateCatalog.Crate crate, CrateMath.KeyUse keys, List<Grant> grants, int pityAfter) {

        long total(Currency currency) {
            long sum = 0;
            for (Grant g : grants) {
                if (g.currency() == currency) {
                    sum = g.amount() > Long.MAX_VALUE - sum ? Long.MAX_VALUE : sum + g.amount();
                }
            }
            return sum;
        }

        /**
         * Keys won, per crate. R3-2: a key won with a bound key is bound too; crate chains from tradeable keys stay
         * tradeable.
         */
        List<KeyGrant> keyGrants() {
            Map<String, long[]> sums = new LinkedHashMap<>();
            for (Grant g : grants) {
                if (g.kind().equals(RewardType.KEY.key())) {
                    long[] s = sums.computeIfAbsent(g.ref(), k -> new long[2]);
                    s[g.bound() ? 0 : 1] += g.amount();
                }
            }
            List<KeyGrant> out = new ArrayList<>();
            sums.forEach((crate, s) -> out.add(new KeyGrant(crate, s[0], s[1])));
            return out;
        }

        /** Grants handed out after the commit (items, boosters, cosmetics, fuel). */
        List<Grant> handouts() {
            return grants.stream().filter(g -> !g.inTransaction()).toList();
        }

        long topCount() {
            return grants.stream().filter(Grant::top).count();
        }
    }

    private OpenPlan() {
    }

    static Plan build(CrateCatalog.Crate crate, CrateMath.KeyUse keys, int counter, Context ctx, RandomGenerator rng,
                      Supplier<UUID> ids) {
        RollEngine.Batch batch = RollEngine.roll(crate, counter, keys.total(), rng, ctx.owns());
        List<Grant> grants = new ArrayList<>(batch.rolls().size());
        for (int i = 0; i < batch.rolls().size(); i++) {
            grants.add(resolve(batch.rolls().get(i), keys.boundAt(i), ctx, rng, ids.get()));
        }
        return new Plan(ids.get(), crate, keys, grants, batch.pityAfter());
    }

    static Grant resolve(RollEngine.Roll roll, boolean bound, Context ctx, RandomGenerator rng, UUID openId) {
        CrateCatalog.Reward r = roll.reward();
        boolean top = roll.top();
        boolean forced = roll.forced();
        if (roll.fallback() || r.type() == RewardType.COMING_SOON || !ctx.available().test(r)) {
            return fallback(roll, bound, ctx, openId);
        }
        return switch (r.type()) {
            case MONEY -> {
                long money = CrateMath.money(r.minutes(), ctx.tier(), ctx.refPerMinute());
                yield money <= 0 ? fallback(roll, bound, ctx, openId)
                        : new Grant(openId, r, RewardType.MONEY.key(), "money", money, bound, top, forced);
            }
            case CRYSTALS -> new Grant(openId, r, RewardType.CRYSTALS.key(), "crystals",
                    CrateMath.crystals(r.base(), ctx.tier(), ctx.crystalGrowth()), bound, top, forced);
            case SHARDS -> new Grant(openId, r, RewardType.SHARDS.key(), "shards", r.amount(), bound, top, forced);
            case KEY -> new Grant(openId, r, RewardType.KEY.key(), r.crate(), r.amount(), bound, top, forced);
            case ENCHANT_BOOK -> {
                List<String> pool = ctx.bookIds().apply(r.pool());
                yield pool == null || pool.isEmpty() ? fallback(roll, bound, ctx, openId)
                        : new Grant(openId, r, r.type().key(), pool.get(rng.nextInt(pool.size())) + ":" + r.level(), r.amount(),
                        bound, top, forced);
            }
            case SCROLL -> new Grant(openId, r, r.type().key(), "scroll", r.amount(), bound, top, forced);
            case SET_TOKEN -> new Grant(openId, r, r.type().key(), r.sets().get(rng.nextInt(r.sets().size())), r.amount(), bound,
                    top, forced);
            case SPAWNER -> {
                List<String> types = ctx.spawnerTypes().apply(r.tier());
                yield types == null || types.isEmpty() ? fallback(roll, bound, ctx, openId)
                        : new Grant(openId, r, r.type().key(), types.get(rng.nextInt(types.size())), r.amount(), bound, top, forced);
            }
            case ITEM -> new Grant(openId, r, r.type().key(), r.material(), r.amount(), bound, top, forced);
            case TAG, TITLE -> roll.cosmeticId() == null ? fallback(roll, bound, ctx, openId)
                    : new Grant(openId, r, r.type().key(), roll.cosmeticId(), 1, bound, top, forced);
            case FUEL -> new Grant(openId, r, r.type().key(), "fuel", r.minutes(), bound, top, forced);
            case BOOSTER, GLOBAL_BOOSTER -> new Grant(openId, r, r.type().key(), r.boosterRef(), r.amount(), bound, top, forced);
            case COMING_SOON -> fallback(roll, bound, ctx, openId);
        };
    }

    private static Grant fallback(RollEngine.Roll roll, boolean bound, Context ctx, UUID openId) {
        CrateCatalog.Reward r = roll.reward();
        return new Grant(openId, r, FALLBACK, "crystals", CrateMath.crystals(r.fallback(), ctx.tier(), ctx.crystalGrowth()), bound,
                roll.top(), roll.forced());
    }
}
