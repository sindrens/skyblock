package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.economy.Currency;

import java.util.List;

/**
 * Typed settings of {@code economy-watch.yml} (M3.8): the economy dashboard ({@code /eco admin}) and the anomaly alerts.
 * Thresholds follow docs/ECONOMY_BALANCE.md (§7.2 sink ratio zones on the 14-day average, R6 "3x the normal income per
 * hour"). Alerts never punish: they tell staff and point to the evidence.
 */
public final class WatchConfig extends ModuleConfig {

    @Setting(path = "enabled", desc = "Economy watch on: summary tables, dashboard numbers and anomaly alerts. Off = the "
            + "background job stops (it catches up from where it was when switched back on).", level = Setting.Level.OWNER)
    public boolean enabled = true;

    // ------------------------------------------------------------------ background job

    @Setting(path = "job.interval-seconds", desc = "Seconds between runs of the background job that reads new ledger rows "
            + "(off the main thread, in batches).", min = 10, max = 3600, restart = true)
    public int intervalSeconds = 60;

    @Setting(path = "job.batch-size", desc = "Ledger rows read per batch.", min = 100, max = 50_000)
    public int batchSize = 5_000;

    @Setting(path = "job.max-batches-per-run", desc = "Batches per run at most (after a long downtime the job catches up "
            + "over several runs instead of all at once).", min = 1, max = 1_000)
    public int maxBatchesPerRun = 20;

    @Setting(path = "job.gap-grace-seconds", desc = "A missing ledger id (a transaction still committing) is waited for this "
            + "long before it is treated as rolled back and skipped.", min = 5, max = 3600)
    public int gapGraceSeconds = 120;

    @Setting(path = "job.supply-minutes", desc = "Minutes between snapshots of the money supply (all balances + money notes "
            + "not cashed yet) for the trend.", min = 5, max = 1440)
    public int supplyMinutes = 60;

    // ------------------------------------------------------------------ retention

    @Setting(path = "retention.flow-days", desc = "Days the hourly created/removed sums are kept.", min = 20, max = 3650)
    public int flowDays = 120;

    @Setting(path = "retention.account-days", desc = "Days the hourly per-profile sums are kept (top earners, earn-rate "
            + "baseline).", min = 15, max = 365)
    public int accountDays = 30;

    @Setting(path = "retention.supply-days", desc = "Days the money supply snapshots are kept.", min = 15, max = 3650)
    public int supplyDays = 120;

    @Setting(path = "retention.alert-days", desc = "Days reviewed or dismissed alerts are kept (open alerts are kept).",
            min = 7, max = 3650)
    public int alertDays = 180;

    // ------------------------------------------------------------------ how money is counted

    @Setting(path = "flows.transit-groups", desc = "Reasons where money only passes through the server: one line per group, "
            + "'name reason reason ...'. They are left out of created/removed; only the group's difference counts (auction: "
            + "what buyers paid minus what sellers got = the sales tax, a sink; notes: cashed minus made).",
            level = Setting.Level.OWNER)
    public List<String> transitGroups = List.of("auction ah_buy ah_sale", "notes note_withdraw note_redeem");

    @Setting(path = "earnings.ignore-reasons", desc = "Reasons that are not earnings or spending of a profile (moving "
            + "money to the own bank, staff changes, the start balance). Used for top earners and the earn-rate alert.",
            level = Setting.Level.OWNER)
    public List<String> ignoreReasons = List.of("bank_deposit", "bank_withdraw", "bank_payout", "start_balance",
            "admin_give", "admin_take", "admin_set", "admin_reset", "sanction_remove", "sanction_restore", "profile_delete",
            "island_kick_restore");

    @Setting(path = "baseline-days", desc = "Days the baselines look back (BAL2: the dashboard uses the 14-day average).",
            min = 1, max = 60, level = Setting.Level.OWNER)
    public int baselineDays = 14;

    @Setting(path = "baseline-min-hours", desc = "Hours of data needed before a baseline is used; before that only the "
            + "minimum amounts below count.", min = 1, max = 720)
    public int baselineMinHours = 24;

    // ------------------------------------------------------------------ inflation risk badge (ECONOMY_BALANCE §7.2)

    @Setting(path = "risk.watch-below-percent", desc = "14-day sink ratio (removed / created) below this = Watch "
            + "(ECONOMY_BALANCE §7.2: 75).", min = 1, max = 100, level = Setting.Level.OWNER)
    public double watchBelowPercent = 75;

    @Setting(path = "risk.act-below-percent", desc = "14-day sink ratio below this for risk.act-days days in a row = Act "
            + "(ECONOMY_BALANCE §7.2: 60 for 3 days). Staff get an alert.", min = 1, max = 100, level = Setting.Level.OWNER)
    public double actBelowPercent = 60;

    @Setting(path = "risk.act-days", desc = "Days in a row below the act line before the badge says Act.", min = 1, max = 14,
            level = Setting.Level.OWNER)
    public int actDays = 3;

    @Setting(path = "risk.min-days", desc = "Days of data before the badge shows anything other than 'not enough data'.",
            min = 1, max = 14)
    public int riskMinDays = 3;

    // ------------------------------------------------------------------ alerts: general

    @Setting(path = "alerts.cooldown-minutes", desc = "The same alert (rule + profile or pair) is sent at most once in this "
            + "many minutes.", min = 1, max = 1440)
    public int cooldownMinutes = 60;

    @Setting(path = "alerts.max-age-hours", desc = "Only alert about hours this recent. When the job catches up on old ledger "
            + "rows (first start, after a downtime) the sums are filled but old hours make no alerts.", min = 1, max = 168)
    public int alertMaxAgeHours = 2;

    @Setting(path = "alerts.currencies", desc = "Currencies the profile alerts look at (money, crystals, gems, shards).",
            level = Setting.Level.OWNER)
    public List<String> alertCurrencies = List.of("money", "crystals", "shards");

    @Setting(path = "alerts.suggest-reason", desc = "Reason id of punishments.yml used when staff press 'Suggest punishment' "
            + "on an alert (a suggestion never applies by itself).")
    public String suggestReason = "exploiting";

    @Setting(path = "alerts.auto-suggest-rules", desc = "Rules whose alerts also file a punishment suggestion at once (staff "
            + "still decide with Apply / Dismiss). Rules: earn-rate, big-transfer, repeat-transfer, note-redeem, sell-spike, "
            + "balance-mismatch. Empty = only by hand.", level = Setting.Level.OWNER)
    public List<String> autoSuggestRules = List.of();

    @Setting(path = "alerts.discord-channel", desc = "Discord webhook name for economy alerts (off until a webhook with "
            + "this name is set up on the server).")
    public String discordChannel = "economy";

    @Setting(path = "alerts.evidence-rows", desc = "Ledger rows kept as evidence per alert.", min = 1, max = 40)
    public int evidenceRows = 21;

    // ------------------------------------------------------------------ rule: earn rate

    @Setting(path = "rules.earn-rate.enabled", desc = "Alert when a profile earns far more in one hour than normal.")
    public boolean earnRateEnabled = true;

    @Setting(path = "rules.earn-rate.multiplier", desc = "Alert above this many times the baseline (the average a profile "
            + "earned per active hour over baseline-days; ECONOMY_BALANCE R6: 3).", min = 1.1, max = 1000,
            level = Setting.Level.OWNER)
    public double earnMultiplier = 3.0;

    @Setting(path = "rules.earn-rate.min.money", desc = "Never alert below this much money earned in one hour.", min = 0)
    public long earnMinMoney = 5_000_000;

    @Setting(path = "rules.earn-rate.min.crystals", desc = "Never alert below this many crystals earned in one hour.", min = 0)
    public long earnMinCrystals = 50_000;

    @Setting(path = "rules.earn-rate.min.gems", desc = "Never alert below this many gems earned in one hour.", min = 0)
    public long earnMinGems = 1_000;

    @Setting(path = "rules.earn-rate.min.shards", desc = "Never alert below this many shards earned in one hour.", min = 0)
    public long earnMinShards = 5_000;

    // ------------------------------------------------------------------ rule: big transfer

    @Setting(path = "rules.big-transfer.enabled", desc = "Alert on one very large transfer between two accounts (pay, "
            + "trade) or one very large amount created at once (not staff).")
    public boolean bigTransferEnabled = true;

    @Setting(path = "rules.big-transfer.supply-percent", desc = "Alert at or above this percent of the money supply of that "
            + "currency.", min = 0.01, max = 100)
    public double bigTransferSupplyPercent = 0.5;

    @Setting(path = "rules.big-transfer.min.money", desc = "Never alert below this much money.", min = 1)
    public long bigMinMoney = 50_000_000;

    @Setting(path = "rules.big-transfer.min.crystals", desc = "Never alert below this many crystals.", min = 1)
    public long bigMinCrystals = 500_000;

    @Setting(path = "rules.big-transfer.min.gems", desc = "Never alert below this many gems.", min = 1)
    public long bigMinGems = 10_000;

    @Setting(path = "rules.big-transfer.min.shards", desc = "Never alert below this many shards.", min = 1)
    public long bigMinShards = 50_000;

    @Setting(path = "rules.big-transfer.include-created", desc = "Also alert when one transaction creates that much (a huge "
            + "sale, an auction payout). Staff /eco changes are never included (they have their own alert).")
    public boolean bigIncludeCreated = true;

    // ------------------------------------------------------------------ rule: repeated near-identical transfers

    @Setting(path = "rules.repeat-transfer.enabled", desc = "Alert when one profile sends another the same amount again and "
            + "again (money moved to an alt, a macro).")
    public boolean repeatEnabled = true;

    @Setting(path = "rules.repeat-transfer.count", desc = "Transfers in the window that make an alert.", min = 2, max = 1000)
    public int repeatCount = 5;

    @Setting(path = "rules.repeat-transfer.window-minutes", desc = "The window in minutes.", min = 1, max = 1440)
    public int repeatWindowMinutes = 10;

    @Setting(path = "rules.repeat-transfer.tolerance-percent", desc = "Amounts within this percent of each other count as "
            + "the same.", min = 0, max = 50)
    public double repeatTolerancePercent = 5;

    @Setting(path = "rules.repeat-transfer.min-amount", desc = "Transfers below this amount are ignored (small tips).", min = 0)
    public long repeatMinAmount = 10_000;

    // ------------------------------------------------------------------ rule: note redemptions

    @Setting(path = "rules.note-redeem.enabled", desc = "Alert when one profile cashes many money notes in one hour.")
    public boolean noteEnabled = true;

    @Setting(path = "rules.note-redeem.count", desc = "Notes cashed in one hour that make an alert.", min = 2, max = 10_000)
    public int noteCount = 10;

    // ------------------------------------------------------------------ rule: sell spike

    @Setting(path = "rules.sell-spike.enabled", desc = "Alert when the whole server sells far more in one hour than the "
            + "hourly average (a dupe being sold off).")
    public boolean sellSpikeEnabled = true;

    @Setting(path = "rules.sell-spike.multiplier", desc = "Alert above this many times the average money from /sell per hour.",
            min = 1.1, max = 1000, level = Setting.Level.OWNER)
    public double sellSpikeMultiplier = 4.0;

    @Setting(path = "rules.sell-spike.min-amount", desc = "Never alert below this much money from selling in one hour.",
            min = 0)
    public long sellSpikeMin = 50_000_000;

    @Setting(path = "rules.sell-spike.reason", desc = "Ledger reason of server sales.")
    public String sellReason = "sell";

    // ------------------------------------------------------------------ rule: balance continuity

    @Setting(path = "rules.balance-mismatch.enabled", desc = "Alert when an account's balance changed without a ledger row "
            + "(its balance before a transaction is not what the previous transaction left): money from nowhere.")
    public boolean mismatchEnabled = true;

    @Setting(path = "rules.balance-mismatch.tracked-accounts", desc = "Accounts whose last balance is remembered (memory "
            + "bound; the oldest are forgotten first).", min = 1_000, max = 1_000_000)
    public int mismatchTracked = 50_000;

    // ------------------------------------------------------------------ dashboard

    @Setting(path = "dashboard.top-size", desc = "Profiles in the top earners list.", min = 5, max = 28)
    public int topSize = 28;

    @Setting(path = "dashboard.big-size", desc = "Transactions in the biggest transactions list.", min = 5, max = 28)
    public int bigSize = 28;

    @Setting(path = "dashboard.cache-seconds", desc = "Seconds the dashboard numbers are reused before they are read again.",
            min = 0, max = 600)
    public int cacheSeconds = 30;

    @Override
    public String fileName() {
        return "economy-watch.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock economy watch (M3.8): /eco admin dashboard and anomaly alerts for staff.
                Alerts never punish anyone; staff open the evidence and decide. Texts: messages/economy.yml (watch.*).
                Reload: /skyblock reload economy""";
    }

    /** Minimum hourly earning for an earn-rate alert. */
    long earnMin(Currency currency) {
        return switch (currency) {
            case MONEY -> earnMinMoney;
            case CRYSTALS -> earnMinCrystals;
            case GEMS -> earnMinGems;
            case SHARDS -> earnMinShards;
        };
    }

    /** Minimum amount for a big-transfer alert. */
    long bigMin(Currency currency) {
        return switch (currency) {
            case MONEY -> bigMinMoney;
            case CRYSTALS -> bigMinCrystals;
            case GEMS -> bigMinGems;
            case SHARDS -> bigMinShards;
        };
    }

    /** Currencies the profile rules look at. */
    java.util.Set<Currency> alertCurrencySet() {
        java.util.Set<Currency> out = java.util.EnumSet.noneOf(Currency.class);
        for (String key : alertCurrencies) {
            Currency.byKey(key).ifPresent(out::add);
        }
        return out;
    }
}
