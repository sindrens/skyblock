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

    @Setting(path = "start.tokens", desc = "Tokens a new player starts with (once per season).", min = 0, level = Setting.Level.OWNER)
    public long startTokens = 0;

    @Setting(path = "start.gems", desc = "Gems a new player starts with (once per season).", min = 0, level = Setting.Level.OWNER)
    public long startGems = 0;

    @Setting(path = "start.shards", desc = "Shards a new player starts with (once per season).", min = 0, level = Setting.Level.OWNER)
    public long startShards = 0;

    // ------------------------------------------------------------------ which currencies players can pay/trade

    @Setting(path = "tradeable.money", desc = "Players can /pay and trade money.", level = Setting.Level.OWNER)
    public boolean tradeableMoney = true;

    @Setting(path = "tradeable.tokens", desc = "Players can /pay and trade tokens.", level = Setting.Level.OWNER)
    public boolean tradeableTokens = false;

    @Setting(path = "tradeable.gems", desc = "Players can /pay and trade gems (owner decision: no).", level = Setting.Level.OWNER)
    public boolean tradeableGems = false;

    @Setting(path = "tradeable.shards", desc = "Players can /pay and trade shards (owner decision: yes).", level = Setting.Level.OWNER)
    public boolean tradeableShards = true;

    // ------------------------------------------------------------------ /pay

    @Setting(path = "pay.min.money", desc = "Smallest /pay amount in money.", min = 1, level = Setting.Level.OWNER)
    public long payMinMoney = 10;

    @Setting(path = "pay.min.tokens", desc = "Smallest /pay amount in tokens.", min = 1, level = Setting.Level.OWNER)
    public long payMinTokens = 1;

    @Setting(path = "pay.min.gems", desc = "Smallest /pay amount in gems.", min = 1, level = Setting.Level.OWNER)
    public long payMinGems = 1;

    @Setting(path = "pay.min.shards", desc = "Smallest /pay amount in shards.", min = 1, level = Setting.Level.OWNER)
    public long payMinShards = 1;

    @Setting(path = "pay.confirm-above.money", desc = "/pay asks to confirm at or above this much money. 0 = never.", min = 0)
    public long payConfirmMoney = 1_000_000;

    @Setting(path = "pay.confirm-above.tokens", desc = "/pay asks to confirm at or above this many tokens. 0 = never.", min = 0)
    public long payConfirmTokens = 10_000;

    @Setting(path = "pay.confirm-above.gems", desc = "/pay asks to confirm at or above this many gems. 0 = never.", min = 0)
    public long payConfirmGems = 100;

    @Setting(path = "pay.confirm-above.shards", desc = "/pay asks to confirm at or above this many shards. 0 = never.", min = 0)
    public long payConfirmShards = 1_000;

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

    @Setting(path = "notes.min.tokens", desc = "Smallest tokens note (only if tokens are in notes.currencies).", min = 1,
            level = Setting.Level.OWNER)
    public long noteMinTokens = 1;

    @Setting(path = "notes.min.gems", desc = "Smallest gems note (only if gems are in notes.currencies).", min = 1,
            level = Setting.Level.OWNER)
    public long noteMinGems = 1;

    @Setting(path = "notes.min.shards", desc = "Smallest shards note (only if shards are in notes.currencies).", min = 1,
            level = Setting.Level.OWNER)
    public long noteMinShards = 1;

    @Setting(path = "notes.max.money", desc = "Largest money note (also the limit of /eco note).", min = 1, level = Setting.Level.OWNER)
    public long noteMaxMoney = 1_000_000_000_000L;

    @Setting(path = "notes.max.tokens", desc = "Largest tokens note.", min = 1, level = Setting.Level.OWNER)
    public long noteMaxTokens = 1_000_000_000L;

    @Setting(path = "notes.max.gems", desc = "Largest gems note.", min = 1, level = Setting.Level.OWNER)
    public long noteMaxGems = 1_000_000L;

    @Setting(path = "notes.max.shards", desc = "Largest shards note.", min = 1, level = Setting.Level.OWNER)
    public long noteMaxShards = 1_000_000_000L;

    @Setting(path = "notes.withdraw-cooldown-seconds", desc = "Seconds between two /withdraw of the same player. 0 = none.",
            min = 0, max = 3600)
    public int noteWithdrawCooldownSeconds = 10;

    @Setting(path = "notes.new-account-minutes", desc = "Playtime (minutes) needed before /withdraw works; notes are a way "
            + "to pay, and new accounts can't pay (balance sheet R3). 0 = off.", min = 0, level = Setting.Level.OWNER)
    public int noteMinPlaytimeMinutes = 120;

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

    @Setting(desc = "Icon per currency in menus: currency:MATERIAL.")
    public List<String> icons = List.of("money:GOLD_INGOT", "tokens:AMETHYST_SHARD", "gems:DIAMOND", "shards:PRISMARINE_SHARD");

    @Override
    public String fileName() {
        return "economy.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock economy (currencies, /pay, /baltop, /transactions). Texts: messages/economy.yml.
                /pay cooldown: commands.yml. Reload: /skyblock reload economy""";
    }

    long start(Currency currency) {
        return switch (currency) {
            case MONEY -> startMoney;
            case TOKENS -> startTokens;
            case GEMS -> startGems;
            case SHARDS -> startShards;
        };
    }

    boolean tradeable(Currency currency) {
        return switch (currency) {
            case MONEY -> tradeableMoney;
            case TOKENS -> tradeableTokens;
            case GEMS -> tradeableGems;
            case SHARDS -> tradeableShards;
        };
    }

    long payMin(Currency currency) {
        return switch (currency) {
            case MONEY -> payMinMoney;
            case TOKENS -> payMinTokens;
            case GEMS -> payMinGems;
            case SHARDS -> payMinShards;
        };
    }

    /** The menu icon of a currency ({@code icons}), or {@code fallback} if missing/invalid. */
    org.bukkit.Material icon(Currency currency, org.bukkit.Material fallback) {
        for (String entry : icons) {
            int colon = entry.indexOf(':');
            if (colon > 0 && entry.substring(0, colon).trim().equalsIgnoreCase(currency.key())) {
                org.bukkit.Material m = org.bukkit.Material.matchMaterial(entry.substring(colon + 1).trim());
                return m == null || !m.isItem() ? fallback : m;
            }
        }
        return fallback;
    }

    long noteMin(Currency currency) {
        return switch (currency) {
            case MONEY -> noteMinMoney;
            case TOKENS -> noteMinTokens;
            case GEMS -> noteMinGems;
            case SHARDS -> noteMinShards;
        };
    }

    long noteMax(Currency currency) {
        return switch (currency) {
            case MONEY -> noteMaxMoney;
            case TOKENS -> noteMaxTokens;
            case GEMS -> noteMaxGems;
            case SHARDS -> noteMaxShards;
        };
    }

    long payConfirmAbove(Currency currency) {
        return switch (currency) {
            case MONEY -> payConfirmMoney;
            case TOKENS -> payConfirmTokens;
            case GEMS -> payConfirmGems;
            case SHARDS -> payConfirmShards;
        };
    }
}
