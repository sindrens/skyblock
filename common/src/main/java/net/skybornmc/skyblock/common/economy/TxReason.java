package net.skybornmc.skyblock.common.economy;

import java.util.regex.Pattern;

/**
 * Standard ledger reasons (PHASE-3 M3.1). A reason is a short snake_case key ({@code [a-z0-9_]{1,32}}); it is also the
 * audit action (category {@code economy}), so {@code /logs pay} finds payments. Modules may use their own keys, but
 * prefer these so player history and staff filters group them (filters match {@code ah} → {@code ah_buy}, {@code ah_sale} …).
 */
public final class TxReason {

    public static final String PAY = "pay";
    public static final String SELL = "sell";
    public static final String SHOP_BUY = "shop_buy";
    public static final String AH_BUY = "ah_buy";
    public static final String AH_SALE = "ah_sale";
    public static final String AH_FEE = "ah_fee";
    public static final String TRADE = "trade";
    public static final String INTEREST = "interest";
    public static final String BANK_DEPOSIT = "bank_deposit";
    public static final String BANK_WITHDRAW = "bank_withdraw";
    /** The early-withdrawal fee of a bank withdrawal (M3.3); leaves the economy. */
    public static final String BANK_FEE = "bank_fee";
    /** A personal bank tier bought with money (M3.3). */
    public static final String BANK_TIER = "bank_tier";
    /** What was left in an island bank paid out when the island was removed (M3.3). */
    public static final String BANK_PAYOUT = "bank_payout";
    public static final String NOTE_WITHDRAW = "note_withdraw";
    public static final String NOTE_REDEEM = "note_redeem";
    /** Staff made a money note out of nothing ({@code /eco note}); audit action only, the money moves on redeem. */
    public static final String ADMIN_NOTE = "admin_note";
    public static final String START_BALANCE = "start_balance";
    public static final String ADMIN_GIVE = "admin_give";
    public static final String ADMIN_TAKE = "admin_take";
    public static final String ADMIN_SET = "admin_set";
    public static final String ADMIN_RESET = "admin_reset";

    private static final Pattern VALID = Pattern.compile("[a-z0-9_]{1,32}");

    private TxReason() {
    }

    public static boolean isValid(String reason) {
        return reason != null && VALID.matcher(reason).matches();
    }
}
