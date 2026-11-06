package net.skybornmc.skyblock.trade;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/** The rules of who can trade with whom and what can be offered (PHASE-3 M3.7). Pure logic, unit-tested. */
final class TradeRules {

    /** Why a request, accept or trade is refused; {@link #key()} is the message key under {@code rules.}. */
    enum Problem {
        SELF("self"),
        YOU_BUSY("you-busy"),
        TARGET_BUSY("target-busy"),
        FROZEN("frozen"),
        TARGET_FROZEN("target-frozen"),
        STAFF_MODE("staff-mode"),
        TARGET_STAFF_MODE("target-staff-mode"),
        COMBAT("combat"),
        TARGET_COMBAT("target-combat"),
        OTHER_WORLD("other-world"),
        TOO_FAR("too-far"),
        REQUESTS_OFF("requests-off"),
        COOLDOWN("cooldown"),
        OWN_PROFILE("own-profile");

        private final String key;

        Problem(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }
    }

    /** What we know about one player when checking a request. */
    record PlayerState(boolean inTrade, boolean frozen, boolean staffMode, long combatMillisLeft) {
    }

    private TradeRules() {
    }

    /**
     * Whether a currency can be offered in a trade. Crystals and earned credits never (CR3, C1-R2-02, also if a config says
     * otherwise);
     * every other currency needs {@code economy.yml → tradeable.<currency>} and must be listed in {@code money.currencies}.
     */
    static boolean currencyAllowed(Currency currency, boolean economyTradeable, Collection<String> configured) {
        if (currency == null || !currency.transferable() || !economyTradeable) { // crystals, earned credits: never
            return false;
        }
        for (String raw : configured) {
            if (raw != null && Currency.byKey(raw).orElse(null) == currency) {
                return true;
            }
        }
        return false;
    }

    /** The currencies that can be offered, in enum order (money first). */
    static List<Currency> allowedCurrencies(Predicate<Currency> economyTradeable, Collection<String> configured) {
        List<Currency> out = new ArrayList<>();
        for (Currency c : Currency.values()) {
            if (currencyAllowed(c, economyTradeable.test(c), configured)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Distance rule. With {@code maxBlocks > 0} both must be in the same world and at most that far apart; with 0 only
     * {@code requireSameWorld} applies.
     *
     * @param distanceSquared squared distance (ignored when the worlds differ)
     */
    static Optional<Problem> distance(boolean sameWorld, double distanceSquared, int maxBlocks, boolean requireSameWorld) {
        if (!sameWorld && (maxBlocks > 0 || requireSameWorld)) {
            return Optional.of(Problem.OTHER_WORLD);
        }
        if (maxBlocks > 0 && distanceSquared > (double) maxBlocks * maxBlocks) {
            return Optional.of(Problem.TOO_FAR);
        }
        return Optional.empty();
    }

    /** True when an open trade must end because the players are too far apart ({@code cancelBlocks} 0 = never). */
    static boolean tooFarDuringTrade(boolean sameWorld, double distanceSquared, int cancelBlocks, boolean requireSameWorld,
                                     int maxBlocks) {
        if (!sameWorld) {
            return requireSameWorld || maxBlocks > 0 || cancelBlocks > 0;
        }
        return cancelBlocks > 0 && distanceSquared > (double) cancelBlocks * cancelBlocks;
    }

    /** The first problem with {@code sender} asking {@code target} to trade (or accepting their request), if any. */
    static Optional<Problem> request(boolean self, PlayerState sender, PlayerState target, boolean targetAcceptsRequests,
                                     long cooldownMillisLeft) {
        if (self) {
            return Optional.of(Problem.SELF);
        }
        if (sender.inTrade()) {
            return Optional.of(Problem.YOU_BUSY);
        }
        if (sender.frozen()) {
            return Optional.of(Problem.FROZEN);
        }
        if (sender.staffMode()) {
            return Optional.of(Problem.STAFF_MODE);
        }
        if (sender.combatMillisLeft() > 0) {
            return Optional.of(Problem.COMBAT);
        }
        if (target.inTrade()) {
            return Optional.of(Problem.TARGET_BUSY);
        }
        if (target.frozen()) {
            return Optional.of(Problem.TARGET_FROZEN);
        }
        if (target.staffMode()) {
            return Optional.of(Problem.TARGET_STAFF_MODE);
        }
        if (target.combatMillisLeft() > 0) {
            return Optional.of(Problem.TARGET_COMBAT);
        }
        if (!targetAcceptsRequests) {
            return Optional.of(Problem.REQUESTS_OFF);
        }
        if (cooldownMillisLeft > 0) {
            return Optional.of(Problem.COOLDOWN);
        }
        return Optional.empty();
    }

    /** Why a money offer is refused ({@code money.<key>} in the messages), or empty. */
    enum MoneyProblem {
        NOT_TRADEABLE("not-tradeable"),
        NEGATIVE("negative"),
        TOO_MUCH("too-much");

        private final String key;

        MoneyProblem(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }
    }

    /**
     * Checks a money offer. Lowering an offer (or removing it) is always allowed; raising it needs enough balance (the
     * swap itself checks the real balance again, atomically). No new-account limit since owner answer 131.
     */
    static Optional<MoneyProblem> money(boolean allowed, long amount, long current, long balance) {
        if (!allowed) {
            return Optional.of(MoneyProblem.NOT_TRADEABLE);
        }
        if (amount < 0) {
            return Optional.of(MoneyProblem.NEGATIVE);
        }
        if (amount <= current) {
            return Optional.empty();
        }
        if (amount > balance) {
            return Optional.of(MoneyProblem.TOO_MUCH);
        }
        return Optional.empty();
    }

    /** True if a custom item id matches a blocked pattern ({@code starter_*} = prefix). */
    static boolean idBlocked(String id, Collection<String> patterns) {
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        for (String raw : patterns) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String p = raw.trim().toLowerCase(Locale.ROOT);
            if (p.endsWith("*") ? lower.startsWith(p.substring(0, p.length() - 1)) : lower.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /** Milliseconds of combat left after the last hit at {@code lastHit} ({@code seconds} 0 = off). */
    static long combatLeft(long lastHit, long now, int seconds) {
        if (seconds <= 0 || lastHit <= 0) {
            return 0;
        }
        return Math.max(0, lastHit + seconds * 1000L - now);
    }
}
