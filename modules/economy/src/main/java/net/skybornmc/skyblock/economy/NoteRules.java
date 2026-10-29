package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.Optional;

/**
 * The checks {@code /withdraw} and {@code /eco note} make before touching the database (M3.2). Pure logic, unit-tested.
 * Inventory space and the cooldown are checked by the caller (they need the live player).
 */
final class NoteRules {

    /** Why a note is refused; the message key is {@code note.<key>}. */
    enum Problem {
        FROZEN("frozen"),
        NOT_ALLOWED("currency-not-allowed"),
        BELOW_MINIMUM("below-minimum"),
        ABOVE_MAXIMUM("above-maximum"),
        NEW_ACCOUNT("new-account");

        final String key;

        Problem(String key) {
            this.key = key;
        }
    }

    private NoteRules() {
    }

    /** Whether notes can be made in this currency: listed in {@code notes.currencies} and tradeable. */
    static boolean allowed(EconomyConfig config, Currency currency) {
        if (!config.tradeable(currency)) {
            return false;
        }
        for (String key : config.noteCurrencies) {
            if (key != null && key.trim().equalsIgnoreCase(currency.key())) {
                return true;
            }
        }
        return false;
    }

    /** The first problem with a {@code /withdraw}, if any. */
    static Optional<Problem> withdraw(EconomyConfig config, long amount, Currency currency, boolean frozen, long playtimeMillis) {
        if (frozen) {
            return Optional.of(Problem.FROZEN);
        }
        if (!allowed(config, currency)) {
            return Optional.of(Problem.NOT_ALLOWED);
        }
        if (amount < config.noteMin(currency)) {
            return Optional.of(Problem.BELOW_MINIMUM);
        }
        if (amount > config.noteMax(currency)) {
            return Optional.of(Problem.ABOVE_MAXIMUM);
        }
        if (playtimeLeft(config, playtimeMillis) > 0) {
            return Optional.of(Problem.NEW_ACCOUNT);
        }
        return Optional.empty();
    }

    /** The first problem with a staff {@code /eco note} (money only, no minimum, same maximum as players). */
    static Optional<Problem> admin(EconomyConfig config, long amount) {
        if (amount > config.noteMax(Currency.MONEY)) {
            return Optional.of(Problem.ABOVE_MAXIMUM);
        }
        return Optional.empty();
    }

    /** Playtime still needed before a new account may make notes (0 = allowed or the rule is off). */
    static long playtimeLeft(EconomyConfig config, long playtimeMillis) {
        long needed = config.noteMinPlaytimeMinutes * 60_000L;
        return needed <= 0 ? 0 : Math.max(0, needed - Math.max(0, playtimeMillis));
    }
}
