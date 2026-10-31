package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;


/** Typed settings of the builder shop ({@code shop-settings.yml}). The items and prices live in {@code shop.yml}. */
public final class ShopSettings extends ModuleConfig {

    @Setting(path = "feedback.chat-from", desc = "Purchases costing at least this much also post a chat line; cheaper ones "
            + "only show the action bar line (SELL2 default: 100,000).", min = 0)
    public long chatFrom = 100_000;

    @Setting(path = "feedback.sound", desc = "Sound (sounds.yml key) played after a purchase. A soft click by default.")
    public String buySound = "click";

    @Setting(path = "buy.max-amount", desc = "Most items one purchase can buy (also limited by free inventory space). "
            + "2304 = 36 full stacks.", min = 1, max = 100_000)
    public int maxAmount = 2_304;

    @Setting(path = "undo.seconds", desc = "F14: how long Undo last buy gives the full price back (while every item "
            + "is still in the inventory). Players can turn undo off in /settings.", min = 1, max = 120)
    public int undoSeconds = 15;

    @Setting(path = "big-buy.percent", desc = "F14: a buy costing more than this percent of the player's money needs a "
            + "second click (the button turns orange). Players can turn it off in /settings. 100 = never.", min = 1, max = 100)
    public int bigBuyPercent = 50;

    @Setting(path = "big-buy.arm-seconds", desc = "F14: how long an orange (armed) buy button waits for the second click.",
            min = 1, max = 30)
    public int bigBuyArmSeconds = 5;

    @Setting(path = "spawners.enabled", desc = "Owner S5: sell spawners in the Spawners tab. Off hides the tab's items "
            + "(the tab says so).", level = Setting.Level.OWNER)
    public boolean spawnersEnabled = true;

    @Setting(path = "spawners.max-tier", desc = "Owner S5: highest spawner tier (stacking.yml spawners.tier-N) the shop "
            + "may sell; higher tiers in shop.yml show as not for sale.", min = 1, max = 5, level = Setting.Level.OWNER)
    public int spawnerMaxTier = 2;

    @Setting(path = "spawners.income-per-hour.tier-1", desc = "Money one tier 1 spawner earns per hour at full output "
            + "(ECONOMY_BALANCE §4.3, 1 mob per spawn); only for the payback time in the lore.", min = 1)
    public long incomeTier1 = 1_900;

    @Setting(path = "spawners.income-per-hour.tier-2", desc = "Money one tier 2 spawner earns per hour at full output.", min = 1)
    public long incomeTier2 = 3_500;

    @Setting(path = "spawners.income-per-hour.tier-3", desc = "Money one tier 3 spawner earns per hour at full output.", min = 1)
    public long incomeTier3 = 6_500;

    @Setting(path = "spawners.income-per-hour.tier-4", desc = "Money one tier 4 spawner earns per hour at full output.", min = 1)
    public long incomeTier4 = 11_800;

    @Setting(path = "spawners.income-per-hour.tier-5", desc = "Money one tier 5 spawner earns per hour at full output.", min = 1)
    public long incomeTier5 = 35_000;

    @Setting(path = "spawners.payback-good-hours", desc = "Owner F2: a spawner that pays back within this many hours "
            + "shows its payback time in green.", min = 1, level = Setting.Level.OWNER)
    public long paybackGoodHours = 100;

    @Setting(path = "spawners.payback-ok-hours", desc = "Owner F2: up to this many hours the payback is yellow; above "
            + "it is red with \"Poor deal\".", min = 1, level = Setting.Level.OWNER)
    public long paybackOkHours = 300;

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
        return java.util.Map.of(
                "buy.confirm-above", "the buy confirm screen was removed (quick-buy, R3-29/F14 plan the replacement)",
                "quick-buy.left-amount", "Shop v2: a click opens the buy page (Buy 1/16/64, Fill inventory, Type an amount)",
                "quick-buy.right-amount", "Shop v2: a click opens the buy page; Java shift-click buys one stack",
                "quick-buy.start-amount", "Shop v2: the amount screen became the buy page",
                "buy.picker-steps", "Shop v2: the buy page has fixed Buy 1/16/64 buttons");
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

    /** Income per hour of one spawner of {@code tier} (1-5) at full output; 0 for an unknown tier. */
    public long incomePerHour(int tier) {
        return switch (tier) {
            case 1 -> incomeTier1;
            case 2 -> incomeTier2;
            case 3 -> incomeTier3;
            case 4 -> incomeTier4;
            case 5 -> incomeTier5;
            default -> 0;
        };
    }
}
