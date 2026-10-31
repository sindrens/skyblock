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
    /** "Undo last buy" in /shop (F14): the full price of a purchase given back once, within 15 s, items returned. */
    public static final String SHOP_UNDO = "shop_undo";
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
    /** Staff removed money as a sanction (Phase P spec 13.1); leaves the economy, restorable. */
    public static final String SANCTION_REMOVE = "sanction_remove";
    /** Money removed by a sanction was given back ({@code /psanction restore}). */
    public static final String SANCTION_RESTORE = "sanction_restore";
    /** A 15-minute reward of the AFK pool (modules/afk, O10/RW3); new money, a source on the economy dashboard. */
    public static final String AFK_POOL = "afk_pool";

    /** The one-time reward for finishing the join tutorial (TUTORIAL.md §5 Finish, TQ7); new money and crystals. */
    public static final String TUTORIAL_REWARD = "tutorial_reward";

    /** Money, crystals and shards from crate opens (PHASE-10 M10.1); a source on the economy dashboard (M10.13). */
    public static final String CRATE_REWARD = "crate_reward";

    /** The gem a player's thanks gives the starter of a global booster (PHASE-10 M10.2, RW2); new gems. */
    public static final String BOOSTER_THANKS = "booster_thanks";

    /** Money, crystals and gems from the daily calendar and its streak (PHASE-10 M10.6); new currency. */
    public static final String DAILY_REWARD = "daily_reward";

    /** Crystals from active playtime (PHASE-10 M10.6, R6); new currency. */
    public static final String PLAYTIME_REWARD = "playtime_reward";

    /** Money and crystals from daily/weekly quests and island missions (PHASE-10 M10.7); new currency. */
    public static final String QUEST_REWARD = "quest_reward";

    /** Shards (or gems for an Island Charter) paid at the Black Market (PHASE-10 M10.11); a sink. */
    public static final String BLACK_MARKET = "black_market";

    /** A Black Market price given back because the bought item could not be handed out (PHASE-10 M10.11). */
    public static final String BLACK_MARKET_REFUND = "black_market_refund";

    private static final Pattern VALID = Pattern.compile("[a-z0-9_]{1,32}");

    private TxReason() {
    }

    public static boolean isValid(String reason) {
        return reason != null && VALID.matcher(reason).matches();
    }
}
