package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.economy.Currency;

import java.util.List;

/** Typed settings of {@code economy.yml}. Economy values are owner-level in the in-game editor. */
public final class EconomyConfig extends ModuleConfig {

    // ------------------------------------------------------------------ starting balances (given once per season)

    @Setting(path = "start.money", desc = "Money every new profile starts with (once per season and profile; owner answer 121).", min = 0, level = Setting.Level.OWNER)
    public long startMoney = 100_000;

    @Setting(path = "start.crystals", desc = "Island crystals a new player starts with (once per season).", min = 0,
            level = Setting.Level.OWNER)
    public long startCrystals = 0;

    @Setting(path = "start.credits_earned", desc = "Earned credits a new player starts with (once per season, account-wide). "
            + "Bought credits never have a start balance (only the store and the owner create them, C1-R2-02).", min = 0,
            level = Setting.Level.OWNER)
    public long startCreditsEarned = 0;

    @Setting(path = "start.shards", desc = "Shards a new player starts with (once per season).", min = 0, level = Setting.Level.OWNER)
    public long startShards = 0;

    // ------------------------------------------------------------------ which currencies players can pay/trade

    @Setting(path = "tradeable.money", desc = "Players can /pay and trade money.", level = Setting.Level.OWNER)
    public boolean tradeableMoney = true;

    @Setting(path = "tradeable.crystals", desc = "Players can /pay and trade island crystals (owner decision CR3: no). "
            + "R3-5b: crystals are never transferable, whatever this says (hard rule in code).",
            level = Setting.Level.OWNER)
    public boolean tradeableCrystals = false;

    @Setting(path = "tradeable.credits", desc = "Bought credits can be offered in /trade (owner, C1 wave 1b; also listed in "
            + "trade.yml money.currencies). They are never sent with /pay (that will be /credit pay) and never become notes. "
            + "Earned credits are never tradeable, whatever this says (hard rule in code, C1-R2-02).", level = Setting.Level.OWNER)
    public boolean tradeableCredits = true;

    @Setting(path = "tradeable.shards", desc = "Players can /pay and trade shards (owner decision: yes).", level = Setting.Level.OWNER)
    public boolean tradeableShards = true;

    // ------------------------------------------------------------------ /pay

    @Setting(path = "pay.min.money", desc = "Smallest /pay amount in money.", min = 1, level = Setting.Level.OWNER)
    public long payMinMoney = 1_000;

    @Setting(path = "pay.min.crystals", desc = "Smallest /pay amount in crystals.", min = 1, level = Setting.Level.OWNER)
    public long payMinCrystals = 1;

    @Setting(path = "pay.min.credits", desc = "Smallest amount of bought credits that can be sent (for /credit pay, coming "
            + "later; /pay never sends credits).", min = 1, level = Setting.Level.OWNER)
    public long payMinCredits = 1;

    @Setting(path = "pay.min.shards", desc = "Smallest /pay amount in shards.", min = 1, level = Setting.Level.OWNER)
    public long payMinShards = 1;

    @Setting(path = "pay.leave-flag.amount", desc = "Owner 4 Nov 2026 (island = profile): the audit log flags a profile wipe "
            + "(leave, kick, disband) when that profile sent at least this much money with /pay shortly before. 0 = off.", min = 0)
    public long leavePayFlagAmount = 10_000_000;

    @Setting(path = "pay.leave-flag.minutes", desc = "How far back before a profile wipe /pay transfers are added up for the flag.",
            min = 1, max = 1440)
    public int leavePayFlagMinutes = 10;

    @Setting(path = "pay.confirm-above.money", desc = "/pay asks to confirm at or above this much money. 0 = never.", min = 0)
    public long payConfirmMoney = 100_000_000;

    @Setting(path = "pay.confirm-above.crystals", desc = "/pay asks to confirm at or above this many crystals. 0 = never.", min = 0)
    public long payConfirmCrystals = 10_000;

    @Setting(path = "pay.confirm-above.credits", desc = "Sending credits asks to confirm at or above this many (for /credit pay, "
            + "coming later; C1-R2-04 confirms every credit payment anyway). 0 = never.", min = 0)
    public long payConfirmCredits = 100;

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

    @Setting(path = "baltop.check-ticks", desc = "How often the background task checks whether a /baltop list is older than "
            + "refresh-seconds (ticks; only a time compare, off the main thread; C1-R3-03).", min = 20, max = 6_000, restart = true)
    public int baltopCheckTicks = 100;

    @Setting(path = "baltop.currencies", desc = "Currencies with a public /baltop list (and leaderboard holograms). Credits count "
            + "the total of bought and earned (C1-R1-08). Crystals are never public (C1-R1-28), whatever this says.",
            level = Setting.Level.OWNER)
    public List<String> baltopCurrencies = List.of("money", "shards", "credits");

    // ------------------------------------------------------------------ number display (UI_STYLE §1.3, C1-R3-03)

    @Setting(path = "display.exact-below", desc = "Amounts below this are shown with every digit (12,500); bigger ones compact "
            + "(1.2M). Credits are always exact (C1-R2-22).", min = 1_000, max = 1_000_000_000_000L)
    public long displayExactBelow = 10_000_000;

    // ------------------------------------------------------------------ credits (card C1)

    @Setting(path = "legacy-gems-alias-until", desc = "Until this date (yyyy-MM-dd, UTC) the old currency name 'gems' still "
            + "works in commands and configs and means earned credits (C1-R1-03: 90 days after the rename). Store grants never "
            + "accept it.", level = Setting.Level.OWNER)
    public String legacyGemsAliasUntil = "2027-01-29";

    // ------------------------------------------------------------------ Tebex credit store (CREDIT-SHOP 5.1)

    @Setting(path = "credit-store.packages", desc = "Credit packages sold on Tebex, one per line: '<id>=<credits>|<usd>' "
            + "(C1-R2-28). The Tebex command 'credit grant {id} <id> {transaction}' names the id; the amount always comes from "
            + "here. Ids a-z, 0-9, _ (max 32). A bad line is skipped with a console warning.", level = Setting.Level.OWNER)
    public List<String> creditStorePackages = List.of("c500=500|5", "c1000=1000|10", "c2700=2700|25", "c14000=14000|130");

    @Setting(path = "credit-store.max-grant", desc = "A package above this many credits is refused (typo guard).",
            min = 1, max = 10_000_000, level = Setting.Level.OWNER)
    public long creditStoreMaxGrant = 20_000;

    @Setting(path = "credit-store.alert-failures", desc = "A failed store grant (unknown package, database error) alerts "
            + "online staff (skyblock.staff.economy.anomalies), the console and the Discord 'alerts' channel. A success is silent.")
    public boolean creditStoreAlertFailures = true;

    // ------------------------------------------------------------------ database retries (C1-R3-03)

    @Setting(path = "database.retry.attempts", desc = "Tries of one balance change when the database reports a deadlock or "
            + "lock timeout (MariaDB 1213/1205). Every try is a full rollback, so a retry never pays twice.", min = 1, max = 10)
    public int dbRetryAttempts = 4;

    @Setting(path = "database.retry.step-ms", desc = "Wait before retry n is n times this (milliseconds), plus a random part.",
            min = 0, max = 1_000)
    public int dbRetryStepMs = 5;

    @Setting(path = "database.retry.jitter-ms", desc = "Largest random extra wait before a retry (milliseconds), so two "
            + "transactions that collided do not collide again.", min = 0, max = 1_000)
    public int dbRetryJitterMs = 10;

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
            + "R3-5b: money and shards (crystals never).", level = Setting.Level.OWNER)
    public List<String> noteCurrencies = List.of("money", "shards");

    @Setting(path = "notes.min.money", desc = "Smallest money note.", min = 1, level = Setting.Level.OWNER)
    public long noteMinMoney = 10_000;

    @Setting(path = "notes.min.crystals", desc = "Smallest crystals note (only if crystals are in notes.currencies).", min = 1,
            level = Setting.Level.OWNER)
    public long noteMinCrystals = 1;

    @Setting(path = "notes.min.shards", desc = "Smallest shards note (only if shards are in notes.currencies).", min = 1,
            level = Setting.Level.OWNER)
    public long noteMinShards = 1;

    @Setting(path = "notes.max.money", desc = "Largest money note (also the limit of /eco note).", min = 1, level = Setting.Level.OWNER)
    public long noteMaxMoney = 100_000_000_000_000L;

    @Setting(path = "notes.max.crystals", desc = "Largest crystals note.", min = 1, level = Setting.Level.OWNER)
    public long noteMaxCrystals = 1_000_000_000L;

    @Setting(path = "notes.max.shards", desc = "Largest shards note.", min = 1, level = Setting.Level.OWNER)
    public long noteMaxShards = 1_000_000_000L;

    @Setting(path = "notes.max-per-minute", desc = "Owner R3-40: most notes one player can make in any 60 seconds (a soft "
            + "limit instead of a cooldown). 0 = no limit.", min = 0, max = 1_000)
    public int noteMaxPerMinute = 20;

    @Setting(path = "notes.purge-redeemed-days", desc = "Owner R3-40: cashed notes are deleted from the database this many "
            + "days after they were cashed (the ledger and audit log keep the money history). 0 = keep forever.", min = 0,
            max = 3_650, level = Setting.Level.OWNER)
    public int notePurgeDays = 90;

    @Setting(path = "notes.purge-start-seconds", desc = "Seconds after start before the first purge of cashed notes (C1-R3-03).",
            min = 1, max = 86_400, restart = true)
    public int notePurgeStartSeconds = 120;

    @Setting(path = "notes.purge-every-hours", desc = "Hours between two purges of cashed notes (C1-R3-03).", min = 1, max = 720,
            restart = true)
    public int notePurgeEveryHours = 6;

    @Setting(path = "notes.purge-batch", desc = "Cashed notes deleted per database round (the purge runs off the main thread, "
            + "once at start and then every few hours).", min = 50, max = 10_000)
    public int notePurgeBatch = 500;

    // ------------------------------------------------------------------ /eco safety (owner R3-6, R3-39)

    @Setting(path = "eco.approve-above.money", desc = "Owner R3-6: an /eco change of more money than this waits until an owner or "
            + "admin clicks Allow (smaller changes apply at once and are flagged). 0 = never wait.", min = 0,
            level = Setting.Level.OWNER)
    public long ecoApproveMoney = 100_000_000_000_000L;

    @Setting(path = "eco.approve-above.crystals", desc = "Same for crystals. 0 = never wait.", min = 0, level = Setting.Level.OWNER)
    public long ecoApproveCrystals = 1_000_000_000L;

    @Setting(path = "eco.approve-above.credits", desc = "Same for bought credits. Only the owner can change them anyway "
            + "(skyblock.economy.credits.grant, C1-R1-18), and the one who allows needs that node too. 0 = never wait.", min = 0,
            level = Setting.Level.OWNER)
    public long ecoApproveCredits = 0L;

    @Setting(path = "eco.approve-above.credits_earned", desc = "Same for earned credits. 0 = never wait.", min = 0,
            level = Setting.Level.OWNER)
    public long ecoApproveCreditsEarned = 1_000_000L;

    @Setting(path = "eco.approve-above.shards", desc = "Same for shards. 0 = never wait.", min = 0, level = Setting.Level.OWNER)
    public long ecoApproveShards = 1_000_000_000L;

    @Setting(path = "eco.pending-minutes", desc = "Owner R3-6: a waiting /eco change is dropped after this many minutes "
            + "without an Allow.", min = 1, max = 120, level = Setting.Level.OWNER)
    public int ecoPendingMinutes = 10;

    @Setting(path = "eco.block-same-ip", desc = "Owner R3-39: /eco can't change another account that ever joined from one "
            + "of your addresses or is online on your address now (alt/IP history of the social module).",
            level = Setting.Level.OWNER)
    public boolean ecoBlockSameIp = true;

    @Setting(path = "eco.allow-self", desc = "Owner 31 Oct 2026: staff with skyblock.economy.eco.self (admin and owner) may "
            + "use /eco give|take|set|reset and /eco note on their OWN account. Still flagged, still waits above the limit "
            + "for another owner or admin. Off = nobody can change their own balance (R3-39).", level = Setting.Level.OWNER)
    public boolean ecoAllowSelf = true;

    // ------------------------------------------------------------------ sending money (owner F1)

    @Setting(path = "send-gate.required", desc = "Owner F1: a profile can send money away (/pay, notes, /trade) only after it "
            + "earned this much itself. Sending to members of its own island is always allowed. 0 = off (owner 6 Nov 2026: "
            + "replaced by the playtime pay limit, pay-limit.tiers).", min = 0,
            level = Setting.Level.OWNER)
    public long sendGateRequired = 0;

    @Setting(path = "pay-limit.tiers", desc = "Owner 6 Nov 2026 (anti-boost): money a profile may send to other players per day "
            + "(/pay, /trade, auction buys) by its account's playtime, as 'hours:limit' lines (limit -1 = no limit). Members of "
            + "your own island never count. Empty = no limit.", level = Setting.Level.OWNER)
    public List<String> payLimitTiers = List.of("0:2M", "3:10M", "6:50M", "12:250M", "24:-1");

    @Setting(path = "pay-limit.zone", desc = "Time zone of the pay limit's day (it resets at midnight there).", level = Setting.Level.OWNER)
    public String payLimitZone = "Europe/Oslo";

    @Setting(path = "shards.island-only", desc = "Owner 6 Nov 2026: shards are personal and may only be paid (/pay, /trade) to "
            + "members of your own island.", level = Setting.Level.OWNER)
    public boolean shardsIslandOnly = true;

    @Setting(path = "send-gate.currencies", desc = "Currencies the F1 rule covers when sent (the earned amount is always "
            + "counted in money).", level = Setting.Level.OWNER)
    public List<String> sendGateCurrencies = List.of("money", "shards");

    @Setting(path = "send-gate.earned-reasons", desc = "Ledger reasons that count as earned money for F1 (income only: "
            + "money from other players, staff, notes, refunds and the start balance never counts).", level = Setting.Level.OWNER)
    public List<String> sendGateEarnedReasons = List.of("sell", "afk_pool", "interest", "crate_reward", "daily_reward",
            "playtime_reward", "tutorial_reward", "tutorial_optional", "depths_drop", "pillar_milestone", "island_task",
            "quest_reward", "pass_reward");

    @Setting(path = "notes.item.material", desc = "Material of the money note item (a vanilla item; "
            + "pick one that does nothing on right-click).", restart = true)
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

    @Setting(path = "sell.dynamic.enabled", desc = "The OLD global model (one price per item for the whole server that falls "
            + "with every sale). Replaced by sell.daily.* and sell.weekly.* (dynamic prices v2, 7 Nov 2026); keep it off.",
            level = Setting.Level.OWNER)
    public boolean sellDynamicEnabled = false;

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

    // ------------------------------------------------------------------ dynamic prices v2 (docs/plans/DYNAMIC_PRICES_V2.md)

    @Setting(path = "sell.daily.enabled", desc = "Per island and per item: the first items of the day pay 100%, then the price "
            + "falls a little, down to the floor. Recovers at the daily reset. Off = always 100%.", level = Setting.Level.OWNER)
    public boolean sellDailyEnabled = true;

    @Setting(path = "sell.daily.free-items", desc = "Items of one item an island sells per day at the full price.", min = 0,
            max = 10_000_000, level = Setting.Level.OWNER)
    public long sellDailyFree = 3_000;

    @Setting(path = "sell.daily.step-items", desc = "After the free items: every this many items pay step-percent less.", min = 1,
            max = 1_000_000, level = Setting.Level.OWNER)
    public long sellDailyStep = 100;

    @Setting(path = "sell.daily.step-percent", desc = "Percent lost per step.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double sellDailyStepPercent = 1;

    @Setting(path = "sell.daily.floor-percent", desc = "The lowest price in percent (60 = reached 4,000 items after the free ones).",
            min = 1, max = 100, level = Setting.Level.OWNER)
    public double sellDailyFloorPercent = 60;

    @Setting(path = "sell.daily.carry-percent", desc = "At the reset, this share of the items over free-items carries into the "
            + "next day (a heavy seller starts a bit lower). 0 = a full reset.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double sellDailyCarryPercent = 50;

    @Setting(path = "sell.daily.reset-hour", desc = "Hour of the daily reset (server time zone, Oslo).", min = 0, max = 23)
    public int sellDailyResetHour = 4;

    @Setting(path = "sell.daily.save-seconds", desc = "How often changed day counts are saved to the database (seconds).", min = 10,
            max = 3_600, restart = true)
    public int sellDailySaveSeconds = 30;

    @Setting(path = "sell.weekly.enabled", desc = "Every Monday 00:00 each item gets a price change for the week, the same for "
            + "everyone (\"Wheat +6% this week\").", level = Setting.Level.OWNER)
    public boolean sellWeeklyEnabled = true;

    @Setting(path = "sell.weekly.max-percent", desc = "The largest weekly change, up or down (percent).", min = 0, max = 50,
            level = Setting.Level.OWNER)
    public double sellWeeklyMaxPercent = 10;

    @Setting(path = "sell.weekly.step-percent", desc = "Weekly changes are a multiple of this (percent).", min = 0.1, max = 50)
    public double sellWeeklyStepPercent = 2;

    @Setting(path = "sell.weekly.salt", desc = "Any number; changes which items go up or down in which week.")
    public long sellWeeklySalt = 104_729;

    // ------------------------------------------------------------------ ECON-SELL-SHOP-AH part A (A7)

    @Setting(path = "sell.price-multiplier", desc = "Multiplies every price in sell.yml (listed, custom, roots and derived). Keep "
            + "shop-settings.yml buy.price-multiplier equal, or payback and the shop invariant change.", min = 0.01, max = 1000,
            level = Setting.Level.OWNER)
    public double sellPriceMultiplier = 1.0;

    @Setting(path = "sell.derive.enabled", desc = "Every block sellable: prices of crafted and smelted items come from their "
            + "parts (sell.yml derive.*: factors, exclude list). Off = only listed items and roots sell.", level = Setting.Level.OWNER)
    public boolean sellDeriveEnabled = true;

    @Setting(path = "sell.derive.max-depth", desc = "Rounds of the derived-price pass (ECON-TABLES E: 6; deeper recipes stay unsellable).",
            min = 1, max = 20)
    public int sellDeriveMaxDepth = 6;

    @Setting(path = "sell.hot-item.min-base", desc = "The Hot Item is only picked from items worth at least this (keeps dirt out).",
            min = 0, max = 1_000_000)
    public double hotMinBase = 500.0;

    @Setting(path = "sell.hot-item.announce", desc = "Tell everyone online once per day (00:00 Oslo) which item is hot.")
    public boolean hotAnnounce = true;

    @Setting(path = "sell.hot-item.join-line", desc = "One line on join (once per day per account) with today's Hot Item.")
    public boolean hotJoinLine = true;

    @Setting(path = "sell.menu.market-tips", desc = "Market Tips button in the sell chest (the items furthest above base right now).")
    public boolean marketTipsButton = true;

    @Setting(path = "sell.market-tips.min-base", desc = "Market Tips only list items worth at least this.", min = 0, max = 1_000_000)
    public double marketTipsMinBase = 500.0;

    @Setting(path = "sell.market-tips.min-percent", desc = "Market Tips only list items at least this many percent above base.",
            min = 0, max = 1_000)
    public double marketTipsMinPercent = 5.0;

    @Setting(path = "sell.market-tips.size", desc = "Most Market Tips shown.", min = 1, max = 7)
    public int marketTipsSize = 5;

    @Setting(path = "sell.market-tips.refresh-seconds", desc = "How often Market Tips are worked out again (from memory).", min = 5, max = 3_600)
    public int marketTipsRefreshSeconds = 60;

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

    @Setting(path = "sell.big-sale-chat-from", desc = "Sales paying at least this much post one chat line with a hover "
            + "breakdown (SELL2); smaller sales only show an action bar line.", min = 0)
    public long sellBigSaleChatFrom = 10_000_000;

    @Setting(path = "autosell.interval-seconds", desc = "Owner 4 Nov 2026: an Auto-Sell Chest sells what is in it this often (Black Market item).", min = 5, max = 3600)
    public int autosellIntervalSeconds = 30;

    @Setting(path = "autosell.max-per-island", desc = "Most Auto-Sell Chests on one island.", min = 1, max = 100)
    public int autosellMaxPerIsland = 1;

    @Setting(path = "sell.menu-enabled", desc = "Owner 4 Nov 2026: /sell sells everything sellable at once (off). On: /sell opens the sell menu as before (it is always /sell menu).")
    public boolean sellMenuEnabled = false;

    @Setting(path = "sell.all-title", desc = "Feedback 5 Nov 2026: selling everything at once (/sell, Sell all) shows the total big on screen (a title).")
    public boolean sellAllTitle = true;

    @Setting(path = "sell.big-sale-hover-lines", desc = "Most item lines in the hover breakdown of a big sale (the rest is "
            + "summed up as \"+N more\").", min = 1, max = 30)
    public int sellBigSaleHoverLines = 10;

    @Setting(path = "sell.chest.save-player-data-ticks", desc = "While the sell chest is open and its contents change, the "
            + "player's data (inventory + the chest's crash copy) is saved at most this often (ticks). 0 = only vanilla "
            + "autosave. Both are always saved together, so a crash can never dupe or lose chest items.", min = 0, max = 6_000)
    public int sellChestSaveTicks = 100;

    @Setting(path = "sell.chest.keep-hotbar-tools", desc = "\"Sell inventory\" leaves tools and weapons in the hotbar alone "
            + "(armor and the offhand are never touched).")
    public boolean sellChestKeepHotbarTools = true;

    @Setting(path = "sell.skip-hotbar-default", desc = "QUICK1 sell protection: the default of the /settings switch "
            + "\"Sell all skips hotbar\" (/sell all and \"Sell inventory\" leave hotbar slots 1-9 alone; /sell hand and "
            + "putting items into the sell chest by hand still sell them).")
    public boolean sellSkipHotbarDefault = false; // feedback 3 Nov 2026: /sell all also sells the hotbar

    /** The icon list before I9 (GUI-P2 7.2); an untouched live list moves to the new one (version 5). */
    static final List<String> OLD_ICONS = List.of("money:GOLD_INGOT", "crystals:PRISMARINE_CRYSTALS", "credits:EMERALD",
            "credits_earned:EMERALD", "shards:PRISMARINE_SHARD");
    /** I9 section 9: money gold ingot, crystals amethyst shard, shards prismarine shard, credits diamond. */
    static final List<String> NEW_ICONS = List.of("money:GOLD_INGOT", "crystals:AMETHYST_SHARD", "shards:PRISMARINE_SHARD",
            "credits:DIAMOND", "credits_earned:DIAMOND");

    @Setting(desc = "Base item per currency in menus: currency:MATERIAL (I9 section 9). Java players with the icon pack see "
            + "our own currency icon on these exact items (currency/<id>); another item shows plain.")
    public List<String> icons = NEW_ICONS;

    /**
     * Version 2: tokens were replaced by island crystals (owner decision O8); old keys are moved on load.
     * Version 3: the new-account limit went from 120 to 30 minutes (owner decision #40). Since owner answer 131 (round 1)
     * there is no new-account limit; the old keys {@code pay.new-account-minutes} and {@code notes.new-account-minutes}
     * are ignored.
     * Version 4 (card C1): gems were renamed credits; old gems are earned credits ({@code start.gems} →
     * {@code start.credits_earned}, the /pay and /eco gem keys → credits). Gem notes never existed and are removed.
     * Version 5 (I9 section 9, GUI-P2): currency base items crystals = amethyst shard, credits = diamond (an untouched
     * {@code icons} list is replaced; an edited one stays).
     */
    @Override
    public int version() {
        return 8;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(5, java.util.Map.of("icons", ValueChange.of(OLD_ICONS, NEW_ICONS)),
                6, java.util.Map.of("sell.skip-hotbar-default", ValueChange.of(true, false)),
                // owner 6 Nov 2026: the $10k earned rule is replaced by the playtime pay limit
                7, java.util.Map.of("send-gate.required", ValueChange.of(10_000L, 0L)),
                // dynamic prices v2 (7 Nov 2026): the old global model is retired; sell.daily.* and sell.weekly.* replace it
                8, java.util.Map.of("sell.dynamic.enabled", ValueChange.of(true, false)));
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, String>> migrations() {
        return java.util.Map.of(2, java.util.Map.of(
                "start.tokens", "start.crystals",
                "tradeable.tokens", "tradeable.crystals",
                "pay.min.tokens", "pay.min.crystals",
                "pay.confirm-above.tokens", "pay.confirm-above.crystals",
                "notes.min.tokens", "notes.min.crystals",
                "notes.max.tokens", "notes.max.crystals"),
                4, java.util.Map.of(
                "start.gems", "start.credits_earned",
                "pay.min.gems", "pay.min.credits",
                "pay.confirm-above.gems", "pay.confirm-above.credits",
                "eco.approve-above.gems", "eco.approve-above.credits_earned"));
    }

    /** R3-16: the old new-account keys (decision #40, then owner answer 131: no limit) are deleted on load. */
    @Override
    public java.util.Map<String, String> removedKeys() {
        return java.util.Map.of(
                "pay.new-account-minutes", "owner answer 131: no new-account limit for /pay (R3-16)",
                "notes.new-account-minutes", "owner answer 131: no new-account limit for /withdraw (R3-16)",
                "notes.withdraw-cooldown-seconds", "owner R3-40: replaced by notes.max-per-minute (20 notes a minute)",
                "tradeable.gems", "card C1: gems became credits; earned credits are never tradeable (tradeable.credits = bought)",
                "notes.min.gems", "card C1: credits never become notes",
                "notes.max.gems", "card C1: credits never become notes",
                "credits.spend-attempts", "card C1 wave 1b: a credit spend locks both rows in one transaction, no re-planning");
    }

    /** Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): the money keys of this file, scaled once on a live server. */
    @Override
    public java.util.Map<String, net.skybornmc.skyblock.common.config.MoneyScale.Rule> moneyKeys() {
        return java.util.Map.ofEntries(
                java.util.Map.entry("start.money", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("pay.min.money", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("pay.leave-flag.amount", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("pay.confirm-above.money", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("display.exact-below", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("notes.min.money", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("notes.max.money", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("eco.approve-above.money", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("send-gate.required", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("pay-limit.tiers", net.skybornmc.skyblock.common.config.MoneyScale.field(1, ":")),
                java.util.Map.entry("sell.hot-item.min-base", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("sell.market-tips.min-base", net.skybornmc.skyblock.common.config.MoneyScale.number()),
                java.util.Map.entry("sell.big-sale-chat-from", net.skybornmc.skyblock.common.config.MoneyScale.number()));
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

    /** Dynamic prices v2: the per-island day price (sell package). */
    public net.skybornmc.skyblock.economy.sell.DayPrice dayPrice() {
        return new net.skybornmc.skyblock.economy.sell.DayPrice(sellDailyEnabled, sellDailyFree, sellDailyStep,
                sellDailyStepPercent, sellDailyFloorPercent, sellDailyCarryPercent, sellDailyResetHour);
    }

    /** Dynamic prices v2: the weekly drift (sell package). */
    public net.skybornmc.skyblock.economy.sell.WeeklyDrift weeklyDrift() {
        return new net.skybornmc.skyblock.economy.sell.WeeklyDrift(sellWeeklyEnabled, sellWeeklyMaxPercent, sellWeeklyStepPercent,
                sellWeeklySalt);
    }

    long start(Currency currency) {
        return switch (currency) {
            case MONEY -> startMoney;
            case CRYSTALS -> startCrystals;
            case CREDITS -> 0L; // bought credits: only the store and the owner create them (C1-R2-02)
            case CREDITS_EARNED -> startCreditsEarned;
            case SHARDS -> startShards;
            case MINING_SHARDS, FISHING_SHARDS, COMBAT_SHARDS -> 0L; // owner 4 Nov 2026: only the island tools create Mining / Fishing Shards
        };
    }

    /** R3-5b: a non-transferable currency (crystals) is never tradeable, whatever the file says. */
    boolean tradeable(Currency currency) {
        if (!currency.transferable()) {
            return false;
        }
        return switch (currency) {
            case MONEY -> tradeableMoney;
            case CRYSTALS -> tradeableCrystals;
            case CREDITS -> tradeableCredits;
            case CREDITS_EARNED -> false;
            case SHARDS, MINING_SHARDS, FISHING_SHARDS, COMBAT_SHARDS -> tradeableShards;
        };
    }

    long payMin(Currency currency) {
        return switch (currency) {
            case MONEY -> payMinMoney;
            case CRYSTALS -> payMinCrystals;
            case CREDITS, CREDITS_EARNED -> payMinCredits;
            case SHARDS, MINING_SHARDS, FISHING_SHARDS, COMBAT_SHARDS -> payMinShards;
        };
    }

    /** The end of the {@code gems} alias (epoch millis, end of the configured UTC day); unlimited when the date is invalid. */
    long legacyGemsAliasUntilMillis() {
        try {
            return java.time.LocalDate.parse(legacyGemsAliasUntil.trim()).plusDays(1)
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }

    /** True if {@code currency} has a public /baltop list ({@code baltop.currencies}); crystals and earned credits never. */
    boolean baltopPublic(Currency currency) {
        if (currency == null || currency == Currency.CRYSTALS || currency == Currency.CREDITS_EARNED) {
            return false;
        }
        for (String key : baltopCurrencies) {
            if (key != null && Currency.byKey(key).orElse(null) == currency) {
                return true;
            }
        }
        return false;
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

    /**
     * The menu item of a currency for {@code viewer} (I7/I9): the configured base item; when it is the catalogue base of
     * {@code currency/<id>} (earned credits use {@code currency/credits}), viewers with the pack loaded see our icon.
     */
    org.bukkit.inventory.ItemStack iconItem(net.skybornmc.skyblock.common.menu.MenuService menus, org.bukkit.entity.Player viewer,
                                            Currency currency, org.bukkit.Material fallback) {
        org.bukkit.Material base = icon(currency, fallback);
        String key = "currency/" + (currency == Currency.CREDITS_EARNED ? "credits" : currency.name().toLowerCase(java.util.Locale.ROOT));
        if (menus.icons().has(key) && menus.icons().base(key) == base) {
            return menus.icon(viewer, key);
        }
        return new org.bukkit.inventory.ItemStack(base);
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

    /** The /eco amount above which a change waits for an Allow (0 = never). */
    long ecoApproveAbove(Currency currency) {
        return switch (currency) {
            case MONEY -> ecoApproveMoney;
            case CRYSTALS -> ecoApproveCrystals;
            case CREDITS -> ecoApproveCredits;
            case CREDITS_EARNED -> ecoApproveCreditsEarned;
            case SHARDS, MINING_SHARDS, FISHING_SHARDS, COMBAT_SHARDS -> ecoApproveShards;
        };
    }

    /** True if F1 covers sending this currency. */
    boolean sendGated(Currency currency) {
        for (String key : sendGateCurrencies) {
            if (key != null && Currency.byKey(key).orElse(null) == currency) {
                return true;
            }
        }
        return false;
    }

    long noteMin(Currency currency) {
        return switch (currency) {
            case MONEY -> noteMinMoney;
            case CRYSTALS -> noteMinCrystals;
            case CREDITS, CREDITS_EARNED -> Long.MAX_VALUE; // never a note (C1-R1-26)
            case SHARDS, MINING_SHARDS, FISHING_SHARDS, COMBAT_SHARDS -> noteMinShards;
        };
    }

    long noteMax(Currency currency) {
        return switch (currency) {
            case MONEY -> noteMaxMoney;
            case CRYSTALS -> noteMaxCrystals;
            case CREDITS, CREDITS_EARNED -> 0L; // never a note (C1-R1-26)
            case SHARDS, MINING_SHARDS, FISHING_SHARDS, COMBAT_SHARDS -> noteMaxShards;
        };
    }

    long payConfirmAbove(Currency currency) {
        return switch (currency) {
            case MONEY -> payConfirmMoney;
            case CRYSTALS -> payConfirmCrystals;
            case CREDITS, CREDITS_EARNED -> payConfirmCredits;
            case SHARDS, MINING_SHARDS, FISHING_SHARDS, COMBAT_SHARDS -> payConfirmShards;
        };
    }
}
