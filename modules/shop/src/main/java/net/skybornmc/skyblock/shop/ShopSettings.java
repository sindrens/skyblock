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

    @Setting(path = "spawners.max-tier", desc = "Highest spawner tier (stacking.yml spawners.tier-N) the shop may sell. "
            + "ECON-SPAWNERS: tiers 1 to 4 are sold for money; tier 5 (premium) is Black Market only.", min = 1, max = 5,
            level = Setting.Level.OWNER)
    public int spawnerMaxTier = 4;

    @Setting(path = "spawners.dynamic-factor", desc = "ECON-SPAWNERS: the average dynamic sell price of spawner drops as a "
            + "share of their base price; only for the payback time in the shop lore (income = drop price x this).",
            min = 0.05, max = 2.0, level = Setting.Level.OWNER)
    public double spawnerDynamicFactor = 0.85;

    @Setting(path = "spawners.payback-good-hours", desc = "Owner F2: a spawner that pays back within this many hours "
            + "shows its payback time in green.", min = 1, level = Setting.Level.OWNER)
    public long paybackGoodHours = 100;

    @Setting(path = "spawners.payback-ok-hours", desc = "Owner F2: up to this many hours the payback is yellow; above "
            + "it is red with \"Poor deal\".", min = 1, level = Setting.Level.OWNER)
    public long paybackOkHours = 300;

    @Setting(path = "sell.min-gap-ms", desc = "GUI-P3: minimum milliseconds between two shop sales of one player (sell GUI, "
            + "middle click, Shift + Right-click): stops click spam. One sale is in flight at a time anyway.", min = 100, max = 5_000)
    public int sellMinGapMs = 400;

    @Setting(path = "sell.note-min-percent", desc = "GUI-P3: the live price note on a shop tile (Falling / Rising) shows only "
            + "when the sell price is at least this many percent away from its base price.", min = 0, max = 100)
    public int noteMinPercent = 2;

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

    @Setting(path = "buy.price-multiplier", desc = "ECON-SELL-SHOP-AH B1: every shop price (owner prices and spawners too) "
            + "x this, kept to 2 significant figures. Move it together with economy.yml sell.price-multiplier, or items whose "
            + "price falls under 7.5x their sell price stop being sold. Block worth (island level) does not change.",
            min = 0.1, max = 10.0, level = Setting.Level.OWNER)
    public double priceMultiplier = 1.0;

    @Setting(path = "shop.auto-markup", desc = "ECON-SELL-SHOP-AH B1: an item with 'price: auto' in shop.yml costs this many "
            + "times its sell.yml base price, rounded up (10: always above the 7.5x rule).", min = 7.6, max = 100,
            level = Setting.Level.OWNER)
    public double autoMarkup = 10.0;

    @Override
    public int version() {
        return 2;
    }

    /** ECON-SPAWNERS step 11: the shop now sells tiers 1 to 4 (an owner-chosen value stays). */
    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of("spawners.max-tier", ValueChange.of(2, 4)));
    }

    /** REVIEW3 §3.6: the buy confirm screen is gone (SELL2 quick-buy); its threshold key is deleted on load. */
    @Override
    public java.util.Map<String, String> removedKeys() {
        return java.util.Map.ofEntries(
                java.util.Map.entry("spawners.income-per-hour", "ECON-SPAWNERS: income comes from the spawner drop's sell price "
                        + "(stacking + sell.yml), not a fixed number per tier"),
                java.util.Map.entry("buy.confirm-above", "the buy confirm screen was removed (quick-buy, R3-29/F14 plan the replacement)"),
                java.util.Map.entry("quick-buy.left-amount", "Shop v2: a click opens the buy page (Buy 1/16/64, Fill inventory, Type an amount)"),
                java.util.Map.entry("quick-buy.right-amount", "Shop v2: a click opens the buy page; Java shift-click buys one stack"),
                java.util.Map.entry("quick-buy.start-amount", "Shop v2: the amount screen became the buy page"),
                java.util.Map.entry("buy.picker-steps", "Shop v2: the buy page has fixed Buy 1/16/64 buttons"));
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
}
