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

    @Setting(path = "listing.durations", desc = "Durations a seller can choose (12h, 24h, 48h, 7d decided; C6-Q4). Max 7d. "
            + "The fee does not depend on the duration.")
    public List<String> durations = List.of("12h", "24h", "48h", "7d");

    @Setting(path = "listing.default-duration", desc = "Duration used when the seller doesn't choose one (must be in durations).")
    public String defaultDuration = "24h";

    @Setting(path = "listing.slots", desc = "Listings a player can have at once (decided: 5). More with "
            + "skyblock.auction.slots.<n>.", min = 1, max = 54, level = Setting.Level.OWNER)
    public int slots = 5;

    @Setting(path = "listing.max-slots", desc = "Highest number a skyblock.auction.slots.<n> permission can give.", min = 1, max = 200,
            level = Setting.Level.OWNER)
    public int maxSlots = 54;

    // ------------------------------------------------------------------ anti-abuse

    @Setting(path = "anti-abuse.listing-cooldown-seconds", desc = "Seconds between two listings by the same player. 0 = none "
            + "(owner C6-Q3: no pacing, only the slots).", min = 0, max = 3600)
    public int listingCooldownSeconds = 0;

    @Setting(path = "anti-abuse.relist.max-same-item", desc = "How often the same item type may be listed within the window "
            + "(relist spam). 0 = no limit (owner C6-Q3: no pacing, only the slots).", min = 0, max = 100)
    public int relistMaxSameItem = 0;

    @Setting(path = "anti-abuse.relist.window-minutes", desc = "Window of the relist limit (minutes).", min = 1, max = 1440)
    public int relistWindowMinutes = 30;

    // Owner answers 131/203 (round 1): no new-account playtime limit (the old anti-abuse.new-account.* keys are ignored).

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

    @Setting(path = "menu.icons", desc = "Menu icons: name:MATERIAL (categories, sort, search, own listings, sell, refresh).")
    public List<String> icons = List.of("all:CHEST", "tools:DIAMOND_PICKAXE", "armor:IRON_CHESTPLATE", "blocks:GRASS_BLOCK",
            "materials:IRON_INGOT", "consumables:GOLDEN_APPLE", "cosmetics:NAME_TAG", "enchant_books:ENCHANTED_BOOK", "special:NETHER_STAR", "sort:HOPPER",
            "search:OAK_SIGN", "clear-search:BARRIER", "mine:GOLD_BLOCK", "sell:EMERALD",
            "refresh:SPYGLASS", "staff-remove:LAVA_BUCKET");
    // "collection:" lines of older files are ignored (the bin button is gone; items go to /box).

    @Setting(path = "staff.remove-reasons", desc = "Reasons staff pick from when removing a listing (logged, shown to the seller).")
    public List<String> staffRemoveReasons = List.of("Scam listing", "Bugged item", "Duped item", "Offensive name", "Testing");

    // ------------------------------------------------------------------ background work

    @Setting(path = "tasks.expire-check-seconds", desc = "How often ended listings end (their items go to /box).", min = 5,
            max = 600, restart = true)
    public int expireCheckSeconds = 20;

    @Setting(path = "tasks.recover-after-seconds", desc = "A listing stuck in pending/buying this long (crash, database error) "
            + "is resolved automatically.", min = 30, max = 3600)
    public int recoverAfterSeconds = 120;

    @Setting(path = "tasks.cache-resync-seconds", desc = "How often the browse cache is reloaded from the database.", min = 10,
            max = 3600, restart = true)
    public int cacheResyncSeconds = 60;

    @Setting(path = "notify.popup", desc = "Online sellers see an action bar line when an item sells (C7).")
    public boolean notifyPopup = true;

    // ------------------------------------------------------------------ buying gate (C1-R1-07) and price hints (C6)

    @Setting(path = "buy-gate.enabled", desc = "Buying needs the earned amount of economy.yml send-gate.required ($10k) first, "
            + "unless the seller is on the buyer's island. Fails closed: no economy gate = no buying.", level = Setting.Level.OWNER)
    public boolean buyGateEnabled = true;

    @Setting(path = "hints.enabled", desc = "Show the typical price of an item (median of recent sales) on listings and when listing.")
    public boolean hintsEnabled = true;

    @Setting(path = "hints.median-days", desc = "Days of sales the typical price looks at.", min = 1, max = 90)
    public int hintsMedianDays = 7;

    @Setting(path = "hints.min-sales", desc = "Sales an item needs in that window before a typical price shows.", min = 1, max = 1000)
    public int hintsMinSales = 5;

    @Setting(path = "hints.min-distinct-sellers", desc = "Different sellers needed before a typical price shows.", min = 1, max = 1000)
    public int hintsMinSellers = 5;

    @Setting(path = "hints.min-distinct-buyers", desc = "Different buyers needed before a typical price shows.", min = 1, max = 1000)
    public int hintsMinBuyers = 5;

    @Setting(path = "hints.warn-factor", desc = "A price this many times above or below the typical price asks for a second confirm.",
            min = 1.5, max = 1000)
    public double hintsWarnFactor = 10;

    @Setting(path = "hints.refresh-minutes", desc = "How often the typical prices are recomputed (async).", min = 1, max = 1440,
            restart = true)
    public int hintsRefreshMinutes = 5;

    @Setting(path = "hints.confirm-seconds", desc = "How long the second click/command of a far-from-typical price stays valid.",
            min = 5, max = 300)
    public int hintsConfirmSeconds = 30;

    @Setting(path = "tasks.box-drain-seconds", desc = "How often items still waiting in the old collection table are moved to /box "
            + "(retry after a box failure).", min = 10, max = 3600, restart = true)
    public int boxDrainSeconds = 60;

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
    public int version() {
        return 2;
    }

    /** C6 (ECON-SELL-SHOP-AH C1): no pacing, 7-day listings. Owner-edited values stay. */
    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of(
                "anti-abuse.listing-cooldown-seconds", ValueChange.of(10, 0),
                "anti-abuse.relist.max-same-item", ValueChange.of(3, 0),
                "listing.durations", ValueChange.of(List.of("12h", "24h", "48h"), List.of("12h", "24h", "48h", "7d"))));
    }

    /** R3-16: the 30-minute / new-account rule is gone (owner answers 131/203); its old keys are deleted on load. */
    @Override
    public java.util.Map<String, String> removedKeys() {
        return java.util.Map.of("anti-abuse.new-account", "owner answers 131/203: no new-account limit (R3-16)");
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
