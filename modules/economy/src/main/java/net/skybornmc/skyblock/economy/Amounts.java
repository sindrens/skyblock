package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.util.Format;

/** Number formatting for balances (UI_STYLE §1.3): exact with commas below 100k, compact ({@code 1.2M}) above. */
final class Amounts {

    /** Below this, amounts are shown exactly. */
    static final long EXACT_BELOW = 100_000;

    private Amounts() {
    }

    /** {@code 12500} → {@code 12,500}; {@code 1_234_567} → {@code 1.2M}. No currency symbol (the HUD adds it). */
    static String plain(long value) {
        long abs = Math.abs(value);
        String text = abs < EXACT_BELOW ? Format.number(abs) : Format.compact(abs);
        return value < 0 ? "-" + text : text;
    }

    /**
     * Plain text with the currency symbol from style.yml ({@code symbols.<currency>}): {@code $12,500}, {@code ✦ 1.2M}.
     * Money has no space after the symbol, the others do (same as the HUD).
     */
    static String withSymbol(String symbol, Currency currency, long value) {
        String sign = value < 0 ? "-" : "";
        String text = plain(Math.abs(value));
        return currency == Currency.MONEY ? sign + symbol + text : sign + symbol + " " + text;
    }
}
