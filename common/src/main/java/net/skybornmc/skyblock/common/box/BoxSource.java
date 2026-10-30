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
    /** Starter chest contents, Starter pack and finish rewards of the join tutorial. */
    public static final String TUTORIAL = "tutorial";
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
