package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.util.Format;

/**
 * Number formatting for balances (UI_STYLE §1.3): exact with commas below {@code display.exact-below} (economy.yml,
 * default 100k), compact ({@code 1.2M}) above. Credits are always exact (C1-R2-22: 12,500, never 12.5k).
 */
final class Amounts {

    /** Default of {@code display.exact-below}. */
    static final long DEFAULT_EXACT_BELOW = 100_000;

    /** Below this, amounts are shown exactly; set from economy.yml on load and reload. */
    private static volatile long exactBelow = DEFAULT_EXACT_BELOW;

    private Amounts() {
    }

    /** Applies {@code display.exact-below} (economy.yml). */
    static void exactBelow(long value) {
        exactBelow = Math.max(1, value);
    }

    static long exactBelow() {
        return exactBelow;
    }

    /** {@code 12500} → {@code 12,500}; {@code 1_234_567} → {@code 1.2M}. No currency symbol (the HUD adds it). */
    static String plain(long value) {
        long abs = Math.abs(value);
        String text = abs < exactBelow ? Format.number(abs) : Format.compact(abs);
        return value < 0 ? "-" + text : text;
    }

    /** {@link #plain(long)}, except credits, which are always shown with every digit (C1-R2-22). */
    static String plain(Currency currency, long value) {
        if (currency != null && currency.isCredits()) {
            long abs = Math.abs(value);
            return value < 0 ? "-" + Format.number(abs) : Format.number(abs);
        }
        return plain(value);
    }

    /**
     * Plain text with the currency symbol from style.yml ({@code symbols.<currency>}): {@code $12,500}, {@code ✦ 1.2M}.
     * Money has no space after the symbol, the others do (same as the HUD).
     */
    static String withSymbol(String symbol, Currency currency, long value) {
        String sign = value < 0 ? "-" : "";
        String text = plain(currency, Math.abs(value));
        return currency == Currency.MONEY ? sign + symbol + text : sign + symbol + " " + text;
    }
}
