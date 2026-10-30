package net.skybornmc.skyblock.trade;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import org.bukkit.Material;

import java.util.List;

/** Typed settings of {@code trade.yml} (secure trade, M3.7). */
public final class TradeConfig extends ModuleConfig {

    // ------------------------------------------------------------------ requests

    @Setting(path = "requests.timeout-seconds", desc = "Seconds before a trade request expires.", min = 10, max = 600)
    public int requestTimeoutSeconds = 60;

    @Setting(path = "requests.cooldown-seconds", desc = "Seconds between two trade requests from the same player.", min = 0, max = 600)
    public int requestCooldownSeconds = 5;

    // ------------------------------------------------------------------ distance and world (PHASE-3 M3.7)

    @Setting(path = "distance.max-blocks", desc = "Players must be this close (same world) to send/accept a request. "
            + "0 = any distance (then distance.same-world decides).", min = 0, max = 10_000)
    public int maxBlocks = 16;

    @Setting(path = "distance.cancel-blocks", desc = "An open trade is cancelled when the players get this far apart. "
            + "0 = never by distance.", min = 0, max = 10_000)
    public int cancelBlocks = 24;

    @Setting(path = "distance.same-world", desc = "Both players must be in the same world (always true when max-blocks > 0).")
    public boolean sameWorld = true;

    // ------------------------------------------------------------------ trade window

    @Setting(path = "confirm.countdown-seconds", desc = "Seconds between both players confirming and the swap. Any change "
            + "in that time stops it.", min = 1, max = 30)
    public int countdownSeconds = 3;

    @Setting(path = "session.max-minutes", desc = "An open trade is cancelled after this many minutes.", min = 1, max = 120)
    public int maxMinutes = 10;

    @Setting(path = "session.max-items", desc = "Item stacks each player can offer (the window has room for 12).", min = 1, max = 12)
    public int maxItems = 12;

    @Setting(path = "cancel.on-damage", desc = "Taking damage cancels an open trade.")
    public boolean cancelOnDamage = true;

    @Setting(path = "cancel.on-teleport", desc = "Teleporting cancels an open trade.")
    public boolean cancelOnTeleport = true;

    @Setting(path = "combat.seconds", desc = "No trade requests or trades for this many seconds after hitting or being hit "
            + "by a player or mob. 0 = off.", min = 0, max = 300)
    public int combatSeconds = 10;

    // ------------------------------------------------------------------ money

    @Setting(path = "money.currencies", desc = "Currencies that can be offered (also need economy.yml tradeable). Crystals "
            + "are never tradeable (CR3); gems only if economy.yml allows it.", level = Setting.Level.OWNER)
    public List<String> currencies = List.of("money", "shards");

    // Owner answers 131/203 (round 1): no new-account playtime limit for money offers (old key money.new-account-minutes
    // is ignored). The join tutorial locks /trade until it is finished (TutorialGate in TradeModule#request/#accept).

    // ------------------------------------------------------------------ what can be offered

    @Setting(path = "items.blocked-ids", desc = "Custom item ids that can't be traded; * at the end = any id starting with it. "
            + "Starter kit pieces ARE tradeable (PHASE-4 #1: trade yes, auction house no); add starter_* to block them.",
            level = Setting.Level.OWNER)
    public List<String> blockedIds = List.of("wardrobe_piece");

    @Setting(path = "items.blocked-data-keys", desc = "Items carrying any of these persistent data keys can't be traded "
            + "(namespace:key): locked wardrobe armor (sets are bound to the profile, WD7).", level = Setting.Level.OWNER)
    public List<String> blockedDataKeys = List.of("phoenixskyblock:wardrobe_piece");

    @Setting(path = "items.allow-filled-containers", desc = "Shulker boxes and bundles with items inside can be traded "
            + "(off: their contents can't be checked).", level = Setting.Level.OWNER)
    public boolean allowFilledContainers = false;

    @Setting(path = "items.blocked-materials", desc = "Vanilla items that can't be traded.")
    public List<String> blockedMaterials = List.of("BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "LIGHT",
            "DEBUG_STICK", "KNOWLEDGE_BOOK");

    // ------------------------------------------------------------------ menu

    @Setting(path = "menu.icons", desc = "Menu icons: name:MATERIAL.")
    public List<String> icons = List.of("divider:IRON_BARS", "money:GOLD_INGOT", "ready:LIME_CONCRETE", "not-ready:RED_CONCRETE",
            "status:CLOCK", "cancel:BARRIER", "empty:LIGHT_GRAY_STAINED_GLASS_PANE", "money-menu:GOLD_INGOT",
            "money-currency:SUNFLOWER", "storing:HOPPER", "locked-slot:BLACK_STAINED_GLASS_PANE");

    @Setting(path = "menu.money-steps", desc = "The -/+ buttons of the money picker for money (3 steps, smallest first).")
    public List<String> moneySteps = List.of("1000", "100000", "10000000");

    @Setting(path = "menu.other-steps", desc = "The -/+ buttons of the money picker for other currencies (shards …).")
    public List<String> otherSteps = List.of("1", "10", "100");

    /** The picker steps for a currency (bad entries skipped; defaults if none is valid). */
    long[] steps(net.skybornmc.skyblock.common.economy.Currency currency) {
        List<String> raw = currency == net.skybornmc.skyblock.common.economy.Currency.MONEY ? moneySteps : otherSteps;
        return parseSteps(raw, currency == net.skybornmc.skyblock.common.economy.Currency.MONEY
                ? new long[]{1_000, 100_000, 10_000_000} : new long[]{1, 10, 100});
    }

    static long[] parseSteps(List<String> raw, long[] fallback) {
        long[] out = raw.stream().map(String::trim).filter(v -> v.matches("[0-9]{1,15}")).mapToLong(Long::parseLong)
                .filter(v -> v > 0).limit(3).sorted().toArray();
        return out.length == 0 ? fallback : out;
    }

    @Setting(path = "safety.save-player-data", desc = "Save a player's data file right after items leave or enter their "
            + "inventory through a trade, so a server crash can't bring back an old inventory (dupe/loss guard).")
    public boolean savePlayerData = true;

    // ------------------------------------------------------------------ background work

    @Setting(path = "tasks.check-ticks", desc = "How often open trades check distance, freeze, staff mode and the countdown.",
            min = 2, max = 40, restart = true)
    public int checkTicks = 5;

    @Setting(path = "tasks.delivery-seconds", desc = "How often items waiting for an online player are delivered again "
            + "(after a full inventory, a profile switch or staff mode).", min = 5, max = 600, restart = true)
    public int deliverySeconds = 15;

    @Setting(path = "notify.join-delay-ticks", desc = "Ticks after joining before waiting trade items are delivered.", min = 0,
            max = 600)
    public int joinDelayTicks = 60;

    /** The menu icon {@code name} from {@code menu.icons}, or {@code fallback} if missing/invalid. */
    Material icon(String name, Material fallback) {
        for (String entry : icons) {
            int colon = entry.indexOf(':');
            if (colon > 0 && entry.substring(0, colon).trim().equalsIgnoreCase(name)) {
                Material m = Material.matchMaterial(entry.substring(colon + 1).trim());
                return m == null || !m.isItem() ? fallback : m;
            }
        }
        return fallback;
    }

    @Override
    public String fileName() {
        return "trade.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock secure trade (/trade). Texts: messages/trade.yml. Reload: /skyblock reload trade
                Offered items live in the database (escrow) until the trade ends; nothing can be lost or copied.""";
    }
}
