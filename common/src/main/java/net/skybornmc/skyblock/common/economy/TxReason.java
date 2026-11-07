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
    /** Staff created currency ({@code /eco give}); for credits only earned credits (C1-R2-02). */
    public static final String ADMIN_GIVE = "admin_give";
    /**
     * The owner created <b>bought</b> credits by hand ({@code /eco give|set credits}, node
     * {@code skyblock.economy.credits.grant}, C1-R1-18 / C1-R2-02).
     */
    public static final String ADMIN_BUY = "admin_buy";
    /** Bought credits from a Tebex store purchase (console-only {@code /credit grant}, C1-R1-17, CREDIT-SHOP 5.2). */
    public static final String STORE_GRANT = "store_grant";
    /** Bought credits taken back after a Tebex chargeback or refund ({@code /credit chargeback}, CREDIT-SHOP 5.3). */
    public static final String STORE_CHARGEBACK = "store_chargeback";
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

    /** Crystals paid for an island tool tier-up or upgrade (Complete Developer Spec T7); a sink. */
    public static final String ISLAND_TOOL_UPGRADE = "island_tool_upgrade";

    /** Owner 6 Nov 2026: shards paid for a personal grind enchant level (Tools v4, plans/TOOLS_V3.md); a sink. */
    public static final String GRIND_ENCHANT = "grind_enchant";

    /** Owner 6 Nov 2026: money from the Cash Vein / Cash Crop grind enchants at /mine and /farm; new money, batched. */
    public static final String GRIND_ENCHANT_CASH = "grind_enchant_cash";

    /** Tools v6 (7 Nov 2026): crystals from the Crystal Find grind enchant; batched. */
    public static final String GRIND_ENCHANT_CRYSTALS = "grind_enchant_crystals";

    /** Owner 3 Nov 2026: shards found while grinding with an island tool (and its Shard Finder upgrade); batched. */
    public static final String ISLAND_TOOL_SHARD = "island_tool_shard";

    /**
     * Owner 4 Nov 2026: an island tool was prestiged, so every member's shards of that tool's type are taken (Farming Shards
     * for the hoe, Mining Shards for the pickaxe); a sink, in the same transaction as the prestige.
     */
    public static final String ISLAND_TOOL_PRESTIGE = "island_tool_prestige";

    /** Credits paid for an Eclipse key (owner F6/V5: the Eclipse credits purchase); a sink. */
    public static final String CRATE_KEY_PURCHASE = "crate_key_purchase";

    /** The credit a player's thanks gives the starter of a global booster (PHASE-10 M10.2, RW2); new earned credits. */
    public static final String BOOSTER_THANKS = "booster_thanks";

    /** Money, crystals and earned credits from the daily calendar and its streak (PHASE-10 M10.6); new currency. */
    public static final String DAILY_REWARD = "daily_reward";
    /** Weekly Reward claim (Reward Core, Complete Developer Spec §6). */
    public static final String WEEKLY_REWARD = "weekly_reward";
    /** Monthly Reward claim (Reward Core, Complete Developer Spec §6). */
    public static final String MONTHLY_REWARD = "monthly_reward";

    /** Crystals from active playtime (PHASE-10 M10.6, R6); new currency. */
    public static final String PLAYTIME_REWARD = "playtime_reward";

    /** Money and crystals from daily/weekly quests and island missions (PHASE-10 M10.7); new currency. */
    public static final String QUEST_REWARD = "quest_reward";

    /** Crystals from collection page milestones (batch 9, 7 Nov 2026); new currency. */
    public static final String COLLECTION_REWARD = "collection_reward";

    /** Money and crystals from battle pass tiers (PHASE-10 M10.8); new currency. */
    public static final String PASS_REWARD = "pass_reward";
    /** Shards (or credits for an Island Charter) paid at the Black Market (PHASE-10 M10.11); a sink. */
    public static final String BLACK_MARKET = "black_market";

    /** A Black Market price given back because the bought item could not be handed out (PHASE-10 M10.11). */
    public static final String BLACK_MARKET_REFUND = "black_market_refund";

    /**
     * ECON-SHARDS: a shard paid for breaking a shard block. Owner 3 Nov 2026: island tools also find shards
     * ({@link #ISLAND_TOOL_SHARD}); before that this was the ONLY shard income (owner 1 Nov 2026:
     * "shards only from blocks, no other rewards"); every other shard deposit is staff ({@code admin_*}) or a refund.
     */
    public static final String SHARD_BLOCK = "shard_block";

    /** ECON-SHARDS: crystals or shards paid for a shard block upgrade (a sink). */
    public static final String SHARD_BLOCK_UPGRADE = "shardblk_upgrade";

    /** Owner 7 Nov 2026 (plans/STORE_FULL_PLAN.md 1): something bought in the credit shop (/credit shop). */
    public static final String CREDIT_SHOP = "credit_shop";

    /** A credit shop purchase given back (the delivery failed). */
    public static final String CREDIT_SHOP_REFUND = "credit_shop_refund";

    /** {@code /credit pay}: bought credits sent to another player. */
    public static final String CREDIT_PAY = "credit_pay";

    /** "gg" after a purchase: one earned credit. */
    public static final String CREDIT_GG = "credit_gg";

    private static final Pattern VALID =Pattern.compile("[a-z0-9_]{1,32}");

    private TxReason() {
    }

    public static boolean isValid(String reason) {
        return reason != null && VALID.matcher(reason).matches();
    }
}
