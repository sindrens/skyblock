package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Typed settings of the builder shop ({@code shop-settings.yml}). The items and prices live in {@code shop.yml}. */
public final class ShopSettings extends ModuleConfig {

    @Setting(path = "quick-buy.left-amount", desc = "Items a left-click buys in the quick-buy grid (Java). SELL2 default: 1.",
            min = 1, max = 2_304)
    public int leftAmount = 1;

    @Setting(path = "quick-buy.right-amount", desc = "Items a right-click buys in the quick-buy grid (Java). SELL2 default: "
            + "64 (a stack).", min = 1, max = 2_304)
    public int rightAmount = 64;

    @Setting(path = "quick-buy.start-amount", desc = "The amount the amount screen starts at (shift-click on Java, tap on "
            + "Bedrock).", min = 1, max = 2_304)
    public int startAmount = 1;

    @Setting(path = "feedback.chat-from", desc = "Purchases costing at least this much also post a chat line; cheaper ones "
            + "only show the action bar line (SELL2 default: 100,000).", min = 0)
    public long chatFrom = 100_000;

    @Setting(path = "feedback.sound", desc = "Sound (sounds.yml key) played after a purchase. A soft click by default.")
    public String buySound = "click";

    @Setting(path = "buy.max-amount", desc = "Most items one purchase can buy (also limited by free inventory space). "
            + "2304 = 36 full stacks.", min = 1, max = 100_000)
    public int maxAmount = 2_304;

    @Setting(path = "buy.picker-steps", desc = "Step buttons of the amount screen (smallest first, up to 3): -64 -16 -1 / "
            + "+1 +16 +64 by default.")
    public List<String> pickerSteps = List.of("1", "16", "64");

    @Setting(path = "buy.save-player-data", desc = "Save the player's data right after the items are handed over, so a "
            + "crash can't roll the inventory back to before a paid purchase.")
    public boolean savePlayerData = true;

    @Setting(path = "delivery.join-delay-ticks", desc = "Ticks after joining before waiting purchases are handed over "
            + "(the profile module loads the active profile first).", min = 1, max = 1_200)
    public int joinDelayTicks = 40;

    @Setting(path = "safety.check-sell-prices", desc = "Refuse to sell items whose price is not above the most they "
            + "could bring with /sell (the economy's price, ceiling, Hot Item and sell cap). Keep this on: off allows "
            + "endless money if a price is too low.", level = Setting.Level.OWNER)
    public boolean checkSellPrices = true;

    /** REVIEW3 §3.6: the buy confirm screen is gone (SELL2 quick-buy); its threshold key is deleted on load. */
    @Override
    public java.util.Map<String, String> removedKeys() {
        return java.util.Map.of("buy.confirm-above", "the buy confirm screen was removed (quick-buy, R3-29/F14 plan the replacement)");
    }

    @Override
    public String fileName() {
        return "shop-settings.yml";
    }

    @Override
    public String header() {
        return "Builder shop settings (M3.5). Items and prices: shop.yml. Texts: messages/shop.yml.\n"
                + "Reload: /skyblock reload shop";
    }

    /** The picker steps as the number picker wants them (at least one; positive). */
    public long[] steps() {
        long[] out = numbers(pickerSteps).stream().filter(s -> s >= 1).limit(3).mapToLong(Integer::longValue).toArray();
        return out.length == 0 ? new long[]{1, 16, 64} : out;
    }

    /** Whole numbers from a config list; anything else is ignored. */
    static List<Integer> numbers(List<String> raw) {
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            try {
                out.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException | NullPointerException ignored) {
                // not a number: skipped
            }
        }
        return out;
    }
}
