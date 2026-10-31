package net.skybornmc.skyblock.common.box;

/**
 * Source keys of {@link ItemBox} deliveries: shown in the item lore of {@code /box} as {@code sources.<key>} in
 * {@code messages/box.yml} (an unknown key is shown capitalized). Lowercase letters, digits, {@code -} and {@code _}, at
 * most 32 characters.
 */
public final class BoxSource {

    public static final String SELL = "sell";
    public static final String SHOP = "shop";
    public static final String FORGE = "forge";
    public static final String TOOLS = "tools";
    public static final String ENCHANTER = "enchanter";
    public static final String WARDROBE = "wardrobe";
    public static final String LOADOUT = "loadout";
    public static final String AFK = "afk";
    public static final String TRADE = "trade";
    public static final String AUCTION = "auction";
    public static final String STAFF = "staff";
    public static final String SANCTION = "sanction";
    public static final String COSMETICS = "cosmetics";
    public static final String PERKS = "perks";
    public static final String NOTES = "notes";
    public static final String SPAWNER = "spawner";
    public static final String CRATE = "crate";
    public static final String GIFT = "gift";
    /** A booster item given back after it could not be started (PHASE-10 M10.2). */
    public static final String BOOSTERS = "boosters";
    /** Daily calendar, playtime and (later) vote rewards (PHASE-10 M10.5/M10.6). */
    public static final String REWARDS = "rewards";
    /** Starter chest contents, Starter pack and finish rewards of the join tutorial. */
    public static final String TUTORIAL = "tutorial";
    /** R3-22: Depths drops (mining and combat) past a full satchel and a full inventory. */
    public static final String DEPTHS = "depths";
    /** R3-13b: frozen stacked spawners a staff member gave back (inspect menu). */
    public static final String SPAWNER_RESTORE = "spawner-restore";
    /** Items bought at the Black Market (PHASE-10 M10.11). */
    public static final String BLACK_MARKET = "black-market";
    /** Fuel Cells given back after a refused or failed use (PHASE-7 M7.1). */
    public static final String PRODUCTION = "production";
    /** An item given back into the box after a take failed (keeps its original source when known). */
    public static final String RETURNED = "returned";

    private BoxSource() {
    }

    /** {@code key} cleaned to a stored source key (unknown/blank → {@code "other"}). */
    public static String clean(String key) {
        if (key == null) {
            return "other";
        }
        String k = key.trim().toLowerCase(java.util.Locale.ROOT);
        if (k.isEmpty() || !k.matches("[a-z0-9_-]{1,32}")) {
            return "other";
        }
        return k;
    }
}
