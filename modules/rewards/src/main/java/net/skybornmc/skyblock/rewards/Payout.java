package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * What one claim pays (pure, unit tested): currencies paid inside the claim's transaction, and grants handed out after
 * it (keys, items, fuel, titles, pass XP). Built from {@link RewardSpec}s with a snapshot of the player's tier and of
 * what other modules can give right now; a reward whose system is missing pays its crystal fallback instead.
 */
final class Payout {

    /** Grant kinds (stored in {@code reward_grants.grant_kind}). */
    static final String KEY = "key";
    static final String BOOK = "book";
    static final String SCROLL = "scroll";
    static final String BOOSTER = "booster";
    static final String FUEL = "fuel";
    static final String TITLE = "title";
    static final String PASS_XP = "pass_xp";

    /**
     * A snapshot taken on the main thread.
     *
     * @param tier            the player's stage tier (pouches scale with it)
     * @param live            whether a non-currency reward can be given now (crate exists, boosters module runs ...)
     * @param books           enabled enchant ids of a rarity (for {@code book <rarity>})
     * @param defaultFallback crystal base of rewards without their own {@code fallback}
     */
    record Context(int tier, long[] refPerMinute, double growth, long defaultFallback, Predicate<RewardSpec> live,
                   Function<String, List<String>> books, RandomGenerator random) {
    }

    /** One hand-out after the commit. {@code ref}: crate id, {@code enchant:level}, {@code type:percent:minutes}, title id. */
    record Grant(String kind, String ref, long amount) {
    }

    /**
     * One line of the claim message.
     *
     * @param value    resolved amount: money/crystals/gems paid, keys, fuel minutes, booster percent, items
     * @param fallback true when crystals were paid instead of {@code kind}
     */
    record Line(RewardSpec.Kind kind, String ref, long value, int minutes, boolean fallback) {
    }

    private final Map<Currency, Long> totals = new EnumMap<>(Currency.class);
    private final List<Grant> grants = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();

    Map<Currency, Long> totals() {
        return Collections.unmodifiableMap(totals);
    }

    long total(Currency currency) {
        return totals.getOrDefault(currency, 0L);
    }

    List<Grant> grants() {
        return Collections.unmodifiableList(grants);
    }

    List<Line> lines() {
        return Collections.unmodifiableList(lines);
    }

    boolean isEmpty() {
        return totals.isEmpty() && grants.isEmpty();
    }

    /** Adds one calendar reward. */
    Payout add(RewardSpec spec, Context ctx) {
        switch (spec.kind()) {
            case MONEY -> money(RewardTables.money(spec.amount(), ctx.tier(), ctx.refPerMinute()), spec);
            case CRYSTALS -> currency(Currency.CRYSTALS, RewardTables.crystals(spec.amount(), ctx.tier(), ctx.growth()), spec, false);
            case GEMS -> currency(Currency.GEMS, spec.amount(), spec, false);
            case KEY -> grantOrFallback(spec, ctx, new Grant(KEY, spec.ref(), spec.amount()), spec.amount());
            case FUEL -> grantOrFallback(spec, ctx, new Grant(FUEL, "", spec.amount()), spec.amount());
            case BOOSTER -> grantOrFallback(spec, ctx, new Grant(BOOSTER, spec.ref() + ":" + spec.amount() + ":" + spec.minutes(), 1),
                    spec.amount());
            case SCROLL -> grantOrFallback(spec, ctx, new Grant(SCROLL, "", spec.amount()), spec.amount());
            case BOOK -> {
                List<String> pool = ctx.live().test(spec) ? ctx.books().apply(spec.ref()) : List.of();
                if (pool == null || pool.isEmpty()) {
                    fallback(spec, ctx);
                } else {
                    String id = pool.get(ctx.random().nextInt(pool.size()));
                    grants.add(new Grant(BOOK, id + ":" + spec.amount(), 1));
                    lines.add(new Line(spec.kind(), id, 1, 0, false));
                }
            }
            case TOME -> grantOrFallback(spec, ctx, null, 1);
        }
        return this;
    }

    /** Adds gems (streak reward). */
    Payout gems(long amount) {
        if (amount > 0) {
            currency(Currency.GEMS, amount, new RewardSpec(RewardSpec.Kind.GEMS, "", amount, 0, -1), false);
        }
        return this;
    }

    /** Adds crystals that are not a calendar line (hourly playtime pouch), already scaled. */
    Payout crystals(long amount) {
        if (amount > 0) {
            currency(Currency.CRYSTALS, amount, new RewardSpec(RewardSpec.Kind.CRYSTALS, "", amount, 0, -1), false);
        }
        return this;
    }

    /** Adds keys of a crate (playtime), or the fallback when the crate can't be given. */
    Payout keys(String crate, long amount, long fallbackBase, Context ctx) {
        if (amount > 0) {
            add(new RewardSpec(RewardSpec.Kind.KEY, crate, amount, 0, fallbackBase), ctx);
        }
        return this;
    }

    /** Adds a title (granted account-wide after the commit; idempotent). */
    Payout title(String id) {
        grants.add(new Grant(TITLE, id, 1));
        return this;
    }

    /** Adds battle pass XP (only when the pass exists). */
    Payout passXp(long xp) {
        if (xp > 0) {
            grants.add(new Grant(PASS_XP, "", xp));
        }
        return this;
    }

    private void money(long amount, RewardSpec spec) {
        currency(Currency.MONEY, amount, spec, false);
    }

    private void currency(Currency currency, long amount, RewardSpec spec, boolean fallback) {
        if (amount <= 0) {
            return;
        }
        totals.merge(currency, amount, Payout::add);
        lines.add(new Line(spec.kind(), spec.ref(), amount, spec.minutes(), fallback));
    }

    private void grantOrFallback(RewardSpec spec, Context ctx, Grant grant, long shown) {
        if (grant == null || !ctx.live().test(spec)) {
            fallback(spec, ctx);
            return;
        }
        grants.add(grant);
        lines.add(new Line(spec.kind(), spec.ref(), shown, spec.minutes(), false));
    }

    private void fallback(RewardSpec spec, Context ctx) {
        long base = spec.fallback() >= 0 ? spec.fallback() : ctx.defaultFallback();
        long crystals = RewardTables.crystals(base, ctx.tier(), ctx.growth());
        currency(Currency.CRYSTALS, crystals, spec, true);
    }

    private static long add(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }
}
