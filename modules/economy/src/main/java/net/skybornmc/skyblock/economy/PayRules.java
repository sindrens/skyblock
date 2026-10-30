package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.Optional;

/**
 * The checks {@code /pay} makes before touching the database (owner decisions 2026-10-29: muted players can pay,
 * frozen players can't, offline targets are fine, gems are not tradeable; owner decision #40: accounts with less than
 * {@code pay.new-account-minutes} of playtime can't pay). Pure logic, unit-tested.
 */
final class PayRules {

    /** Why a payment is refused; the message key is {@code pay.<key>}. */
    enum Problem {
        FROZEN("frozen"),
        SELF("self"),
        NOT_TRADEABLE("not-tradeable"),
        BELOW_MINIMUM("below-minimum"),
        NEW_ACCOUNT("new-account");

        final String key;

        Problem(String key) {
            this.key = key;
        }
    }

    private PayRules() {
    }

    /**
     * The first problem with this payment, if any. {@code targetName} is compared ignoring case; {@code playtimeMillis}
     * is the payer's total playtime.
     */
    static Optional<Problem> check(EconomyConfig config, String payerName, String targetName, long amount, Currency currency,
                                   boolean frozen, long playtimeMillis) {
        if (frozen) {
            return Optional.of(Problem.FROZEN);
        }
        if (payerName.equalsIgnoreCase(targetName)) {
            return Optional.of(Problem.SELF);
        }
        if (!config.tradeable(currency)) {
            return Optional.of(Problem.NOT_TRADEABLE);
        }
        if (amount < config.payMin(currency)) {
            return Optional.of(Problem.BELOW_MINIMUM);
        }
        if (playtimeLeft(config, playtimeMillis) > 0) {
            return Optional.of(Problem.NEW_ACCOUNT);
        }
        return Optional.empty();
    }

    /** Playtime still needed before a new account may pay (0 = allowed or the rule is off). */
    static long playtimeLeft(EconomyConfig config, long playtimeMillis) {
        long needed = config.payMinPlaytimeMinutes * 60_000L;
        return needed <= 0 ? 0 : Math.max(0, needed - Math.max(0, playtimeMillis));
    }

    /** Whether {@code /pay} asks to confirm this amount (at or above the currency's threshold; 0 = never). */
    static boolean needsConfirm(EconomyConfig config, long amount, Currency currency) {
        long threshold = config.payConfirmAbove(currency);
        return threshold > 0 && amount >= threshold;
    }
}
