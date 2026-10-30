package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Typed settings of the builder shop ({@code shop-settings.yml}). The items and prices live in {@code shop.yml}. */
public final class ShopSettings extends ModuleConfig {

    @Setting(path = "buy.amounts", desc = "Quick-buy amounts on an item page (PHASE-3 M3.5: 1, 16, 64). Up to 3; "
            + "\"Custom amount\" opens the number picker.")
    public List<String> amounts = List.of("1", "16", "64");

    @Setting(path = "buy.max-amount", desc = "Most items one purchase can buy (also limited by free inventory space). "
            + "2304 = 36 full stacks.", min = 1, max = 100_000)
    public int maxAmount = 2_304;

    @Setting(path = "buy.confirm-above", desc = "Purchases costing at least this much money ask to confirm first. "
            + "0 = always ask.", min = 0, level = Setting.Level.OWNER)
    public long confirmAbove = 100_000;

    @Setting(path = "buy.picker-steps", desc = "Step buttons of the custom amount picker (smallest first, up to 3).")
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

    @Override
    public String fileName() {
        return "shop-settings.yml";
    }

    @Override
    public String header() {
        return "Builder shop settings (M3.5). Items and prices: shop.yml. Texts: messages/shop.yml.\n"
                + "Reload: /skyblock reload shop";
    }

    /** The quick-buy amounts that make sense (1 .. max-amount), at most 3, in order. */
    public List<Integer> quickAmounts() {
        return numbers(amounts).stream().filter(a -> a >= 1 && a <= maxAmount).distinct().limit(3).toList();
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
