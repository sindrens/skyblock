package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.economy.Currency;

import java.util.List;

/** Typed settings of {@code economy.yml}. Economy values are owner-level in the in-game editor. */
public final class EconomyConfig extends ModuleConfig {

    // ------------------------------------------------------------------ starting balances (given once per season)

    @Setting(path = "start.money", desc = "Money a new player starts with (once per season).", min = 0, level = Setting.Level.OWNER)
    public long startMoney = 1_000;

    @Setting(path = "start.crystals", desc = "Island crystals a new player starts with (once per season).", min = 0,
            level = Setting.Level.OWNER)
    public long startCrystals = 0;

    @Setting(path = "start.gems", desc = "Gems a new player starts with (once per season).", min = 0, level = Setting.Level.OWNER)
    public long startGems = 0;

    @Setting(path = "start.shards", desc = "Shards a new player starts with (once per season).", min = 0, level = Setting.Level.OWNER)
    public long startShards = 0;

    // ------------------------------------------------------------------ which currencies players can pay/trade

    @Setting(path = "tradeable.money", desc = "Players can /pay and trade money.", level = Setting.Level.OWNER)
    public boolean tradeableMoney = true;

    @Setting(path = "tradeable.crystals", desc = "Players can /pay and trade island crystals (owner decision CR3: no).",
            level = Setting.Level.OWNER)
    public boolean tradeableCrystals = false;

    @Setting(path = "tradeable.gems", desc = "Players can /pay and trade gems (owner decision: no).", level = Setting.Level.OWNER)
    public boolean tradeableGems = false;

    @Setting(path = "tradeable.shards", desc = "Players can /pay and trade shards (owner decision: yes).", level = Setting.Level.OWNER)
    public boolean tradeableShards = true;

    // ------------------------------------------------------------------ /pay

    @Setting(path = "pay.min.money", desc = "Smallest /pay amount in money.", min = 1, level = Setting.Level.OWNER)
    public long payMinMoney = 10;

    @Setting(path = "pay.min.crystals", desc = "Smallest /pay amount in crystals.", min = 1, level = Setting.Level.OWNER)
    public long payMinCrystals = 1;

    @Setting(path = "pay.min.gems", desc = "Smallest /pay amount in gems.", min = 1, level = Setting.Level.OWNER)
    public long payMinGems = 1;

    @Setting(path = "pay.min.shards", desc = "Smallest /pay amount in shards.", min = 1, level = Setting.Level.OWNER)
    public long payMinShards = 1;

    @Setting(path = "pay.confirm-above.money", desc = "/pay asks to confirm at or above this much money. 0 = never.", min = 0)
    public long payConfirmMoney = 1_000_000;

    @Setting(path = "pay.confirm-above.crystals", desc = "/pay asks to confirm at or above this many crystals. 0 = never.", min = 0)
    public long payConfirmCrystals = 10_000;

    @Setting(path = "pay.confirm-above.gems", desc = "/pay asks to confirm at or above this many gems. 0 = never.", min = 0)
    public long payConfirmGems = 100;

    @Setting(path = "pay.confirm-above.shards", desc = "/pay asks to confirm at or above this many shards. 0 = never.", min = 0)
    public long payConfirmShards = 1_000;

    @Setting(path = "pay.new-account-minutes", desc = "Playtime (minutes) needed before /pay works (owner decision #40: "
            + "30). Stops fresh alts from passing money on. 0 = off.", min = 0, level = Setting.Level.OWNER)
    public int payMinPlaytimeMinutes = 30;

    @Setting(path = "pay.confirm-seconds", desc = "How long a /pay confirmation stays valid (seconds).", min = 5, max = 120,
            restart = true)
    public int payConfirmSeconds = 30;

    // ------------------------------------------------------------------ /baltop

    @Setting(path = "baltop.refresh-seconds", desc = "How old a /baltop list may get before it is read again (seconds). "
            + "Refreshed in the background, and when someone runs /baltop on an older list.", min = 5, max = 3600)
    public int baltopRefreshSeconds = 30;

    @Setting(path = "baltop.size", desc = "Players listed in /baltop.", min = 3, max = 50)
    public int baltopSize = 10;

    // ------------------------------------------------------------------ /transactions

    @Setting(path = "transactions.reasons", desc = "Reason filters the /transactions menu cycles through (a group like 'ah' "
            + "also matches ah_buy, ah_sale ...). Names: messages/economy.yml -> reasons.")
    public List<String> transactionReasons = List.of("pay", "sell", "shop", "ah", "trade", "interest", "bank", "note", "admin",
            "start_balance");

    @Setting(path = "transactions.ranges", desc = "Time filters the /transactions menu cycles through (durations like 24h, 7d; "
            + "'all' = everything).")
    public List<String> transactionRanges = List.of("all", "24h", "7d", "30d");

    @Setting(path = "transactions.page-size", desc = "Entries per page in the /transactions menu.", min = 7, max = 28)
    public int transactionPageSize = 28;

    // ------------------------------------------------------------------ money notes (M3.2: /withdraw, /eco note)

    @Setting(path = "notes.currencies", desc = "Currencies players can /withdraw as a note (must also be tradeable). Owner "
            + "decision: money only.", level = Setting.Level.OWNER)
    public List<String> noteCurrencies = List.of("money");

    @Setting(path = "notes.min.money", desc = "Smallest money note.", min = 1, level = Setting.Level.OWNER)
    public long noteMinMoney = 100;

    @Setting(path = "notes.min.crystals", desc = "Smallest crystals note (only if crystals are in notes.currencies).", min = 1,
            level = Setting.Level.OWNER)
    public long noteMinCrystals = 1;

    @Setting(path = "notes.min.gems", desc = "Smallest gems note (only if gems are in notes.currencies).", min = 1,
            level = Setting.Level.OWNER)
    public long noteMinGems = 1;

    @Setting(path = "notes.min.shards", desc = "Smallest shards note (only if shards are in notes.currencies).", min = 1,
            level = Setting.Level.OWNER)
    public long noteMinShards = 1;

    @Setting(path = "notes.max.money", desc = "Largest money note (also the limit of /eco note).", min = 1, level = Setting.Level.OWNER)
    public long noteMaxMoney = 1_000_000_000_000L;

    @Setting(path = "notes.max.crystals", desc = "Largest crystals note.", min = 1, level = Setting.Level.OWNER)
    public long noteMaxCrystals = 1_000_000_000L;

    @Setting(path = "notes.max.gems", desc = "Largest gems note.", min = 1, level = Setting.Level.OWNER)
    public long noteMaxGems = 1_000_000L;

    @Setting(path = "notes.max.shards", desc = "Largest shards note.", min = 1, level = Setting.Level.OWNER)
    public long noteMaxShards = 1_000_000_000L;

    @Setting(path = "notes.withdraw-cooldown-seconds", desc = "Seconds between two /withdraw of the same player. 0 = none.",
            min = 0, max = 3600)
    public int noteWithdrawCooldownSeconds = 10;

    @Setting(path = "notes.new-account-minutes", desc = "Playtime (minutes) needed before /withdraw works; notes are a way "
            + "to pay, and new accounts can't pay (owner decision #40: 30). 0 = off.", min = 0, level = Setting.Level.OWNER)
    public int noteMinPlaytimeMinutes = 30;

    @Setting(path = "notes.item.material", desc = "Material of the money note item (a resource-pack model can replace the "
            + "look later).", restart = true)
    public String noteMaterial = "PAPER";

    @Setting(path = "notes.item.rarity", desc = "Rarity shown in the note's footer: common, uncommon, rare, epic, legendary, "
            + "mythic, special.", restart = true)
    public String noteRarity = "rare";

    @Setting(path = "notes.item.glint", desc = "Money notes have the enchant glint.", restart = true)
    public boolean noteGlint = true;

    @Setting(path = "notes.date-format", desc = "Date on the note (java DateTimeFormatter pattern, UTC).")
    public String noteDateFormat = "yyyy-MM-dd";

    // ------------------------------------------------------------------ selling (used by every sell system)

    @Setting(path = "sell.multiplier-cap-percent", desc = "Global cap on the total sell bonus in percent (owner decision "
            + "#46: 300 = at most x4.0). Rank, upgrades, sets, boosters and events are added up, then capped here.", min = 0,
            max = 10_000, level = Setting.Level.OWNER)
    public double sellMultiplierCapPercent = net.skybornmc.skyblock.common.economy.MultiplierCap.DEFAULT_CAP_PERCENT;

    // ------------------------------------------------------------------ /sell (M3.4). Prices: sell.yml (owner approves them)

    @Setting(path = "sell.dynamic.enabled", desc = "Dynamic prices on: every sale lowers the price a little, it recovers "
            + "over time, unsold items drift above base. Off = always the base price.", level = Setting.Level.OWNER)
    public boolean sellDynamicEnabled = true;

    @Setting(path = "sell.dynamic.floor-percent", desc = "Lowest price in percent of base (PHASE-3: 40).", min = 1, max = 100,
            level = Setting.Level.OWNER)
    public double sellFloorPercent = 40;

    @Setting(path = "sell.dynamic.ceiling-percent", desc = "Highest price in percent of base (PHASE-3: 150).", min = 100,
            max = 1_000, level = Setting.Level.OWNER)
    public double sellCeilingPercent = 150;

    @Setting(path = "sell.dynamic.recovery-percent", desc = "Part of the gap to base that closes every interval (PHASE-3: 2% "
            + "per 10 min, half-life ~5.7 h).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double sellRecoveryPercent = 2;

    @Setting(path = "sell.dynamic.interval-minutes", desc = "Minutes between two recovery/drift steps.", min = 1, max = 1_440,
            restart = true, level = Setting.Level.OWNER)
    public int sellIntervalMinutes = 10;

    @Setting(path = "sell.dynamic.target-percent", desc = "Where a price settles when an item keeps selling at its usual "
            + "24 h volume (ECONOMY_BALANCE §8: 85). The impact per item is recomputed every hour to hit it.", min = 1, max = 100,
            level = Setting.Level.OWNER)
    public double sellTargetPercent = 85;

    @Setting(path = "sell.dynamic.default-impact-ppm", desc = "Price drop per item sold, in millionths of base, while there "
            + "is too little volume to compute it (1 = selling 10,000 items lowers the price by 1%).", min = 0, max = 100_000,
            level = Setting.Level.OWNER)
    public double sellDefaultImpactPpm = 1;

    @Setting(path = "sell.dynamic.min-impact-ppm", desc = "Smallest computed impact per item (millionths of base).", min = 0,
            max = 100_000, level = Setting.Level.OWNER)
    public double sellMinImpactPpm = 0.01;

    @Setting(path = "sell.dynamic.max-impact-ppm", desc = "Largest computed impact per item (millionths of base); keeps rare "
            + "items from crashing after one big sale.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double sellMaxImpactPpm = 100;

    @Setting(path = "sell.dynamic.min-volume", desc = "Items sold in 24 h before the impact is computed from volume (below: "
            + "default-impact-ppm).", min = 0, level = Setting.Level.OWNER)
    public long sellMinVolume = 1_000;

    @Setting(path = "sell.dynamic.drift-quiet-minutes", desc = "Minutes without any sale before an item at or above base starts "
            + "to drift up.", min = 0, max = 10_080, level = Setting.Level.OWNER)
    public int sellQuietMinutes = 60;

    @Setting(path = "sell.dynamic.drift-percent-per-day", desc = "How fast unsold items climb toward the ceiling, in percent "
            + "of base per day (ECONOMY_BALANCE §8: +10% after 24 h without sales).", min = 0, max = 100,
            level = Setting.Level.OWNER)
    public double sellDriftPercentPerDay = 10;

    @Setting(path = "sell.dynamic.trend-threshold-percent", desc = "Change in the last hour (percent of base) before a price "
            + "shows as rising or falling.", min = 0, max = 100)
    public double sellTrendThresholdPercent = 0.5;

    @Setting(path = "sell.dynamic.catch-up-max-hours", desc = "After a restart, prices recover/drift for the time the server "
            + "was off, up to this many hours.", min = 0, max = 2_160, level = Setting.Level.OWNER)
    public int sellCatchUpMaxHours = 168;

    @Setting(path = "sell.dynamic.save-seconds", desc = "How often changed prices are saved to the database (seconds).", min = 30,
            max = 3_600, restart = true)
    public int sellSaveSeconds = 300;

    @Setting(path = "sell.hot-item.enabled", desc = "A daily Hot Item sells for more (changes the price, not the sell bonus).",
            level = Setting.Level.OWNER)
    public boolean sellHotEnabled = true;

    @Setting(path = "sell.hot-item.percent", desc = "Hot Item price boost in percent (ECONOMY_BALANCE §8 proposal: 25).", min = 0,
            max = 500, level = Setting.Level.OWNER)
    public double sellHotPercent = 25;

    @Setting(path = "sell.hot-item.categories", desc = "sell.yml categories the Hot Item is picked from (empty = all).",
            level = Setting.Level.OWNER)
    public List<String> sellHotCategories = List.of();

    @Setting(path = "sell.hot-item.salt", desc = "Any number; changes which items become hot on which day.",
            level = Setting.Level.OWNER)
    public long sellHotSalt = 7_919;

    @Setting(path = "sell.blocked-ids", desc = "Custom item ids that can never be sold (* at the end = any id starting with it). "
            + "Unique items (tools, enchant books, money notes, set pieces) are never sellable anyway.", level = Setting.Level.OWNER)
    public List<String> sellBlockedIds = List.of("money_note", "starter_*", "tool_*");

    @Setting(path = "sell.blocked-data-keys", desc = "Items carrying any of these data keys (namespace:key) can't be sold, e.g. "
            + "the starter kit marker.", level = Setting.Level.OWNER)
    public List<String> sellBlockedDataKeys = List.of("phoenixskyblock:starter_kit", "phoenixskyblock:menu_item");

    @Setting(path = "sell.custom-types", desc = "Types of custom items that may be sold when they are in sell.yml (item types: "
            + "material, consumable ...). Default: materials only.", level = Setting.Level.OWNER)
    public List<String> sellCustomTypes = List.of("material");

    @Setting(path = "sell.save-player-data", desc = "Save the player's data right after the sold items are taken and before "
            + "the money is paid, so a server crash can never give both the items and the money back.")
    public boolean sellSavePlayerData = true;

    @Setting(path = "sell.cooldown-seconds", desc = "Seconds between two /sell hand or /sell all of the same player.", min = 0,
            max = 60, restart = true)
    public int sellCooldownSeconds = 2;

    @Setting(path = "sell.menu-refresh-ticks", desc = "How often the /sell menu updates prices and checks your items (ticks).",
            min = 5, max = 200)
    public int sellMenuRefreshTicks = 20;

    @Setting(path = "sell.menu-click-ms", desc = "Smallest time between two item clicks in your inventory while the /sell "
            + "menu is open (milliseconds).", min = 0, max = 2_000)
    public int sellMenuClickMs = 150;

    @Setting(desc = "Icon per currency in menus: currency:MATERIAL.")
    public List<String> icons = List.of("money:GOLD_INGOT", "crystals:PRISMARINE_CRYSTALS", "gems:EMERALD", "shards:PRISMARINE_SHARD");

    /**
     * Version 2: tokens were replaced by island crystals (owner decision O8); old keys are moved on load.
     * Version 3: the new-account limit went from 120 to 30 minutes (owner decision #40); see {@link #upgradeValues}.
     */
    @Override
    public int version() {
        return 3;
    }

    /** The old default of {@code notes.new-account-minutes} (before owner decision #40). */
    static final int OLD_NOTE_MINUTES_DEFAULT = 120;

    /**
     * Value changes the key renames can't do, applied to the raw file before it is loaded. Only an untouched old
     * default is changed (an owner-chosen value is kept). Returns true if something changed.
     */
    static boolean upgradeValues(org.bukkit.configuration.file.YamlConfiguration yaml) {
        if (yaml.getKeys(false).isEmpty() || yaml.getInt("config-version", 1) >= 3) {
            return false;
        }
        if (yaml.isInt("notes.new-account-minutes") && yaml.getInt("notes.new-account-minutes") == OLD_NOTE_MINUTES_DEFAULT) {
            yaml.set("notes.new-account-minutes", 30);
            return true;
        }
        return false;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, String>> migrations() {
        return java.util.Map.of(2, java.util.Map.of(
                "start.tokens", "start.crystals",
                "tradeable.tokens", "tradeable.crystals",
                "pay.min.tokens", "pay.min.crystals",
                "pay.confirm-above.tokens", "pay.confirm-above.crystals",
                "notes.min.tokens", "notes.min.crystals",
                "notes.max.tokens", "notes.max.crystals"));
    }

    @Override
    public String fileName() {
        return "economy.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock economy (currencies, /pay, /baltop, /transactions, /sell). Texts: messages/economy.yml.
                Sell prices per item: sell.yml (dynamic price rules: sell.dynamic.* below).
                /pay cooldown: commands.yml. Reload: /skyblock reload economy""";
    }

    /** The dynamic price settings as plain fractions (sell package). */
    public net.skybornmc.skyblock.economy.sell.DynamicSettings dynamicSettings() {
        return new net.skybornmc.skyblock.economy.sell.DynamicSettings(sellDynamicEnabled, sellFloorPercent / 100.0,
                sellCeilingPercent / 100.0, sellRecoveryPercent / 100.0, sellIntervalMinutes, sellTargetPercent / 100.0,
                sellDefaultImpactPpm / 1e6, sellMinImpactPpm / 1e6, sellMaxImpactPpm / 1e6, sellMinVolume, sellQuietMinutes,
                sellDriftPercentPerDay, sellTrendThresholdPercent, sellCatchUpMaxHours, sellHotEnabled, sellHotPercent);
    }

    long start(Currency currency) {
        return switch (currency) {
            case MONEY -> startMoney;
            case CRYSTALS -> startCrystals;
            case GEMS -> startGems;
            case SHARDS -> startShards;
        };
    }

    boolean tradeable(Currency currency) {
        return switch (currency) {
            case MONEY -> tradeableMoney;
            case CRYSTALS -> tradeableCrystals;
            case GEMS -> tradeableGems;
            case SHARDS -> tradeableShards;
        };
    }

    long payMin(Currency currency) {
        return switch (currency) {
            case MONEY -> payMinMoney;
            case CRYSTALS -> payMinCrystals;
            case GEMS -> payMinGems;
            case SHARDS -> payMinShards;
        };
    }

    /** The menu icon of a currency ({@code icons}), or {@code fallback} if missing/invalid. */
    org.bukkit.Material icon(Currency currency, org.bukkit.Material fallback) {
        String name = iconName(currency);
        if (name == null) {
            return fallback;
        }
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(name);
        return m == null || !m.isItem() ? fallback : m;
    }

    /** The material name configured for a currency in {@code icons}, or null. An old {@code tokens:} entry = crystals. */
    String iconName(Currency currency) {
        for (String entry : icons) {
            int colon = entry.indexOf(':');
            if (colon > 0 && Currency.byKey(entry.substring(0, colon)).orElse(null) == currency) {
                return entry.substring(colon + 1).trim();
            }
        }
        return null;
    }

    long noteMin(Currency currency) {
        return switch (currency) {
            case MONEY -> noteMinMoney;
            case CRYSTALS -> noteMinCrystals;
            case GEMS -> noteMinGems;
            case SHARDS -> noteMinShards;
        };
    }

    long noteMax(Currency currency) {
        return switch (currency) {
            case MONEY -> noteMaxMoney;
            case CRYSTALS -> noteMaxCrystals;
            case GEMS -> noteMaxGems;
            case SHARDS -> noteMaxShards;
        };
    }

    long payConfirmAbove(Currency currency) {
        return switch (currency) {
            case MONEY -> payConfirmMoney;
            case CRYSTALS -> payConfirmCrystals;
            case GEMS -> payConfirmGems;
            case SHARDS -> payConfirmShards;
        };
    }
}
