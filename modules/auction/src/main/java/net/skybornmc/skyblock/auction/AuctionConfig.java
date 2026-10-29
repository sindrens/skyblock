package net.skybornmc.skyblock.auction;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Typed settings of {@code auction.yml}. Money settings are owner-level in the in-game editor. */
public final class AuctionConfig extends ModuleConfig {

    // ------------------------------------------------------------------ money (decided: fee 1%, tax 5%)

    @Setting(path = "fee.percent", desc = "Listing fee in % of the price, paid when listing (decided: 1%).", min = 0, max = 50,
            level = Setting.Level.OWNER)
    public double feePercent = 1.0;

    @Setting(path = "fee.min", desc = "Smallest listing fee (money). 0 = a fee below $1 is free.", min = 0, level = Setting.Level.OWNER)
    public long feeMin = 1;

    @Setting(path = "fee.refund-on-cancel", desc = "Give the listing fee back when the seller cancels (not on expiry). "
            + "Default no (open question).", level = Setting.Level.OWNER)
    public boolean feeRefundOnCancel = false;

    @Setting(path = "tax.percent", desc = "Sales tax in % of the price, taken from what the seller receives (decided: 5%).",
            min = 0, max = 50, level = Setting.Level.OWNER)
    public double taxPercent = 5.0;

    @Setting(path = "tax.min", desc = "Smallest sales tax (money).", min = 0, level = Setting.Level.OWNER)
    public long taxMin = 0;

    @Setting(path = "price.min", desc = "Lowest price a listing can have (money).", min = 1, level = Setting.Level.OWNER)
    public long priceMin = 10;

    @Setting(path = "price.max", desc = "Highest price a listing can have (money).", min = 1, level = Setting.Level.OWNER)
    public long priceMax = 1_000_000_000_000L;

    // ------------------------------------------------------------------ listings

    @Setting(path = "listing.durations", desc = "Durations a seller can choose (12h, 24h, 48h decided). Max 7d.")
    public List<String> durations = List.of("12h", "24h", "48h");

    @Setting(path = "listing.default-duration", desc = "Duration used when the seller doesn't choose one (must be in durations).")
    public String defaultDuration = "24h";

    @Setting(path = "listing.slots", desc = "Listings a player can have at once (decided: 5). More with "
            + "skyblock.auction.slots.<n>.", min = 1, max = 54, level = Setting.Level.OWNER)
    public int slots = 5;

    @Setting(path = "listing.max-slots", desc = "Highest number a skyblock.auction.slots.<n> permission can give.", min = 1, max = 200,
            level = Setting.Level.OWNER)
    public int maxSlots = 54;

    // ------------------------------------------------------------------ anti-abuse

    @Setting(path = "anti-abuse.listing-cooldown-seconds", desc = "Seconds between two listings by the same player.", min = 0,
            max = 3600)
    public int listingCooldownSeconds = 10;

    @Setting(path = "anti-abuse.relist.max-same-item", desc = "How often the same item type may be listed within the window "
            + "(relist spam). 0 = no limit.", min = 0, max = 100)
    public int relistMaxSameItem = 3;

    @Setting(path = "anti-abuse.relist.window-minutes", desc = "Window of the relist limit (minutes).", min = 1, max = 1440)
    public int relistWindowMinutes = 30;

    @Setting(path = "anti-abuse.new-account.min-playtime-minutes", desc = "Playtime needed before using the auction house "
            + "(balance sheet R3: 2 h). 0 = no limit.", min = 0, max = 10_000, level = Setting.Level.OWNER)
    public int minPlaytimeMinutes = 120;

    @Setting(path = "anti-abuse.new-account.block-listing", desc = "New accounts can't list items.", level = Setting.Level.OWNER)
    public boolean newBlockListing = true;

    @Setting(path = "anti-abuse.new-account.block-buying", desc = "New accounts can't buy items.", level = Setting.Level.OWNER)
    public boolean newBlockBuying = true;

    // ------------------------------------------------------------------ what can be listed

    @Setting(path = "items.blocked-ids", desc = "Custom item ids that can't be listed; * at the end = any id starting with it. "
            + "Starter kit tools must never be listable.", level = Setting.Level.OWNER)
    public List<String> blockedIds = List.of("starter_*", "tool_starter_*");

    @Setting(path = "items.blocked-data-keys", desc = "Items carrying any of these persistent data keys can't be listed "
            + "(namespace:key; e.g. the starter kit marker).", level = Setting.Level.OWNER)
    public List<String> blockedDataKeys = List.of("phoenixskyblock:starter_kit");

    @Setting(path = "items.allow-money-notes", desc = "Money notes (item type note) can be listed. Default no until the notes "
            + "module decides.", level = Setting.Level.OWNER)
    public boolean allowMoneyNotes = false;

    @Setting(path = "items.allow-filled-containers", desc = "Shulker boxes and bundles with items inside can be listed "
            + "(off: their contents can't be checked).", level = Setting.Level.OWNER)
    public boolean allowFilledContainers = false;

    @Setting(path = "items.blocked-materials", desc = "Vanilla items that can't be listed.")
    public List<String> blockedMaterials = List.of("BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "LIGHT",
            "DEBUG_STICK", "KNOWLEDGE_BOOK", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "END_PORTAL_FRAME", "REINFORCED_DEEPSLATE",
            "TEST_BLOCK", "TEST_INSTANCE_BLOCK");

    // ------------------------------------------------------------------ menus

    @Setting(path = "menu.search-max-length", desc = "Longest search text players can type.", min = 3, max = 64)
    public int searchMaxLength = 32;

    @Setting(path = "menu.icons", desc = "Menu icons: name:MATERIAL (categories, sort, search, own listings, collection bin).")
    public List<String> icons = List.of("all:CHEST", "tools:DIAMOND_PICKAXE", "armor:IRON_CHESTPLATE", "blocks:GRASS_BLOCK",
            "materials:IRON_INGOT", "consumables:GOLDEN_APPLE", "cosmetics:NAME_TAG", "special:NETHER_STAR", "sort:HOPPER",
            "search:OAK_SIGN", "clear-search:BARRIER", "mine:GOLD_BLOCK", "collection:ENDER_CHEST", "sell:EMERALD",
            "refresh:SPYGLASS", "staff-remove:LAVA_BUCKET");

    @Setting(path = "staff.remove-reasons", desc = "Reasons staff pick from when removing a listing (logged, shown to the seller).")
    public List<String> staffRemoveReasons = List.of("Scam listing", "Bugged item", "Duped item", "Offensive name", "Testing");

    // ------------------------------------------------------------------ background work

    @Setting(path = "tasks.expire-check-seconds", desc = "How often ended listings are moved to the collection bin.", min = 5,
            max = 600, restart = true)
    public int expireCheckSeconds = 20;

    @Setting(path = "tasks.recover-after-seconds", desc = "A listing stuck in pending/buying this long (crash, database error) "
            + "is resolved automatically.", min = 30, max = 3600)
    public int recoverAfterSeconds = 120;

    @Setting(path = "tasks.cache-resync-seconds", desc = "How often the browse cache is reloaded from the database.", min = 10,
            max = 3600, restart = true)
    public int cacheResyncSeconds = 60;

    @Setting(path = "notify.join-delay-ticks", desc = "Ticks after joining before sales/expiry news is shown.", min = 0, max = 600)
    public int joinNoticeDelayTicks = 60;

    /** The menu icon {@code name} from {@code menu.icons}, or {@code fallback} if missing/invalid. */
    org.bukkit.Material icon(String name, org.bukkit.Material fallback) {
        for (String entry : icons) {
            int colon = entry.indexOf(':');
            if (colon > 0 && entry.substring(0, colon).trim().equalsIgnoreCase(name)) {
                org.bukkit.Material m = org.bukkit.Material.matchMaterial(entry.substring(colon + 1).trim());
                return m == null || !m.isItem() ? fallback : m;
            }
        }
        return fallback;
    }

    @Override
    public String fileName() {
        return "auction.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock auction house (/ah). Texts: messages/auction.yml. Reload: /skyblock reload auction
                Money values are whole dollars. Extra listing slots: permission skyblock.auction.slots.<n>.""";
    }
}
