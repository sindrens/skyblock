package net.skybornmc.skyblock.economy.bank;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.util.List;

/**
 * Typed settings of {@code bank.yml} (M3.3). Numbers follow the balance sheet (docs/ECONOMY_BALANCE.md §6, BAL2): interest
 * stays about 2% of a regular player's season income because every payout is capped.
 */
public final class BankConfig extends ModuleConfig {

    @Setting(path = "enabled", desc = "Banks on (/bank, the island menu's Bank button, interest).", level = Setting.Level.OWNER)
    public boolean enabled = true;

    // ------------------------------------------------------------------ interest

    @Setting(path = "interest.period-hours", desc = "Hours between interest payouts (DESIGN 4.4: 3). Payouts happen at fixed "
            + "times (every N hours from midnight UTC), also while you are offline.", min = 1, max = 24, restart = true,
            level = Setting.Level.OWNER)
    public int periodHours = 3;

    @Setting(path = "interest.personal-rate-percent", desc = "Interest per payout on a personal bank, in percent (0.15).",
            min = 0, max = 5, level = Setting.Level.OWNER)
    public double personalRatePercent = 0.15;

    @Setting(path = "interest.island-rate-percent", desc = "Interest per payout on an island bank, in percent (0.25).",
            min = 0, max = 5, level = Setting.Level.OWNER)
    public double islandRatePercent = 0.25;

    @Setting(path = "interest.activity-minutes", desc = "Only accounts played at least this many minutes in the last "
            + "interest.activity-days days earn interest (owner #38: 2 h in 7 days). Island banks: one member must have played "
            + "that long. 0 = no activity rule.", min = 0, max = 10_080, level = Setting.Level.OWNER)
    public int activityMinutes = 120;

    @Setting(path = "interest.activity-days", desc = "Days the activity rule looks back (owner #38: 7).", min = 1, max = 60,
            level = Setting.Level.OWNER)
    public int activityDays = 7;

    @Setting(path = "interest.catch-up-periods", desc = "After a server downtime at most this many missed payouts are paid "
            + "per account (older ones are skipped).", min = 0, max = 80, level = Setting.Level.OWNER)
    public int catchUpPeriods = 8;

    @Setting(path = "interest.sweep-minutes", desc = "Minutes between background runs that pay due interest (off the main "
            + "thread, in small batches). Accounts are also settled when someone opens the bank.", min = 1, max = 60, restart = true)
    public int sweepMinutes = 5;

    @Setting(path = "interest.sweep-batch", desc = "Accounts paid per background run at most (the rest wait for the next run).",
            min = 10, max = 5_000)
    public int sweepBatch = 200;

    @Setting(path = "interest.activity-flush-minutes", desc = "Minutes between saves of online time for the activity rule.",
            min = 1, max = 30, restart = true)
    public int activityFlushMinutes = 5;

    // ------------------------------------------------------------------ deposits and withdrawals

    @Setting(path = "deposit.min", desc = "Smallest deposit.", min = 1, level = Setting.Level.OWNER)
    public long depositMin = 1;

    @Setting(path = "withdraw.min", desc = "Smallest withdrawal.", min = 1, level = Setting.Level.OWNER)
    public long withdrawMin = 100;

    @Setting(path = "withdraw.fee-percent", desc = "Early-withdrawal fee in percent of the amount taken out (owner E4: keep "
            + "2%). The fee leaves the economy. A withdrawal with a fee also forfeits the interest of the current period.",
            min = 0, max = 50, level = Setting.Level.OWNER)
    public double withdrawFeePercent = 2.0;

    @Setting(path = "withdraw.free-minutes", desc = "Minutes right after each interest payout in which withdrawals are free "
            + "(no fee, the period's interest is not forfeited; the lowest-balance rule still applies). 0 = always a fee.",
            min = 0, max = 1_440, level = Setting.Level.OWNER)
    public int withdrawFreeMinutes = 10;

    // ------------------------------------------------------------------ tiers and levels

    @Setting(path = "personal.tiers", desc = "Personal bank tiers 1-10, one line each: capacity, unlock cost, payout cap per "
            + "interest payout (amounts like 100k, 2.5M). Tier 1 is free (balance sheet 6.1; cost = 2% of capacity, owner #42).",
            level = Setting.Level.OWNER)
    public List<String> personalTiers = List.of(
            "100k 0 150",
            "500k 10k 480",
            "2.5M 50k 1536",
            "12.5M 250k 4915",
            "62.5M 1.25M 15729",
            "312.5M 6.25M 50332",
            "1.5625B 31.25M 161061",
            "7.8125B 156.25M 515396",
            "39.0625B 781.25M 1649267",
            "195.3125B 3.90625B 5277655");

    @Setting(path = "island.levels", desc = "Island bank by level 0-10, one line each: capacity (Bank Capacity upgrade level) "
            + "and payout cap per interest payout (Bank Interest upgrade level). Balance sheet 6.2.", level = Setting.Level.OWNER)
    public List<String> islandLevels = List.of(
            "1M 2.5k",
            "4M 7.5k",
            "16M 22.5k",
            "64M 67.5k",
            "256M 202.5k",
            "1.024B 607.5k",
            "4.096B 1.8225M",
            "16.384B 5.4675M",
            "65.536B 16.4025M",
            "262.144B 49.2075M",
            "1.048576T 147.6225M");

    @Setting(path = "island.withdraw-limit-percent.leader", desc = "Most the Leader may take out of the island bank per day, "
            + "in percent of the bank's capacity. -1 = no limit.", min = -1, max = 100, level = Setting.Level.OWNER)
    public int limitLeader = -1;

    @Setting(path = "island.withdraw-limit-percent.admin", desc = "Daily island bank withdraw limit of Admins (percent of the "
            + "capacity, -1 = no limit). Only roles with the bank-withdraw permission can withdraw at all.", min = -1, max = 100,
            level = Setting.Level.OWNER)
    public int limitAdmin = -1;

    @Setting(path = "island.withdraw-limit-percent.mod", desc = "Daily island bank withdraw limit of Mods (percent of the "
            + "capacity, -1 = no limit).", min = -1, max = 100, level = Setting.Level.OWNER)
    public int limitMod = 25;

    @Setting(path = "island.withdraw-limit-percent.member", desc = "Daily island bank withdraw limit of Members (percent of "
            + "the capacity, -1 = no limit).", min = -1, max = 100, level = Setting.Level.OWNER)
    public int limitMember = 10;

    @Setting(path = "island.pay-leader-on-disband", desc = "When an island is disbanded or reset, what is left in its bank "
            + "goes to the leader. Staff deletes always keep the money in the (closed) bank for staff to decide.",
            level = Setting.Level.OWNER)
    public boolean payLeaderOnDisband = true;

    // ------------------------------------------------------------------ menu

    @Setting(path = "history.page-size", desc = "Entries per page in the bank history.", min = 7, max = 28)
    public int historyPageSize = 28;

    @Override
    public String fileName() {
        return "bank.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock banks (M3.3): personal bank per profile, island bank per island, interest. /bank
                Texts: messages/economy.yml (bank.*). Island bank rights: /is permissions (bank-deposit, bank-withdraw,
                bank-history). Reload: /skyblock reload economy""";
    }

    /** The daily withdraw limit of a role in percent of the capacity (-1 = none; coop: never allowed anyway). */
    int limitPercent(Role role) {
        if (role == null) {
            return 0;
        }
        return switch (role) {
            case LEADER -> limitLeader;
            case ADMIN -> limitAdmin;
            case MOD -> limitMod;
            case MEMBER -> limitMember;
            case COOP -> 0;
        };
    }

    long periodMillis() {
        return periodHours * 3_600_000L;
    }
}
