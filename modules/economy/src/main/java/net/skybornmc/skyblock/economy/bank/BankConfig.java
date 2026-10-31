package net.skybornmc.skyblock.economy.bank;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed settings of {@code bank.yml} (M3.3, BANK2). Numbers follow the balance sheet (docs/ECONOMY_BALANCE.md §6): owner
 * answer 169 asked for higher interest paid every 8 hours, so the rates and payout caps are 4x the M3.3 values per payout
 * (3 payouts a day instead of 8 = +50% per day). Interest stays under ~8% of season income because every payout is capped.
 */
public final class BankConfig extends ModuleConfig {

    @Setting(path = "enabled", desc = "Banks on (/bank, the island menu's Bank button, interest).", level = Setting.Level.OWNER)
    public boolean enabled = true;

    // ------------------------------------------------------------------ interest

    @Setting(path = "interest.period-hours", desc = "Hours between interest payouts (owner #169: 8). Payouts happen at fixed "
            + "times (every N hours from midnight UTC: 00, 08, 16 UTC), also while you are offline. A change takes effect "
            + "after a restart; bank states are converted to the new period length then (no time is ever paid twice).",
            min = 1, max = 24, restart = true, level = Setting.Level.OWNER)
    public int periodHours = 8;

    @Setting(path = "interest.personal-rate-percent", desc = "Interest per payout on a personal bank, in percent (owner #169: "
            + "0.6 every 8 h).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double personalRatePercent = 0.6;

    @Setting(path = "interest.island-rate-percent", desc = "Interest per payout on an island bank, in percent (owner #169: "
            + "1.0 every 8 h).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double islandRatePercent = 1.0;

    @Setting(path = "interest.activity-minutes", desc = "Only accounts played at least this many minutes in the last "
            + "interest.activity-days days earn interest (owner #38: 2 h in 7 days). Island banks: one member must have played "
            + "that long. Owner R3-25: minutes in the AFK pool don't count. 0 = no activity rule.", min = 0, max = 10_080,
            level = Setting.Level.OWNER)
    public int activityMinutes = 120;

    @Setting(path = "interest.activity-days", desc = "Days the activity rule looks back (owner #38: 7).", min = 1, max = 60,
            level = Setting.Level.OWNER)
    public int activityDays = 7;

    @Setting(path = "interest.catch-up-periods", desc = "After a server downtime at most this many missed payouts are paid "
            + "per account (older ones are skipped). 3 payouts of 8 h = one day.", min = 0, max = 80, level = Setting.Level.OWNER)
    public int catchUpPeriods = 3;

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

    @Setting(path = "withdraw.fee-percent", desc = "Withdrawal fee in percent of the amount taken out, before island "
            + "masteries (owner E4 + #169: 2%). Personal and island bank. The fee leaves the economy. Owner R3-41: this fee is "
            + "the only withdrawal rule (no lost interest).", min = 0, max = 50, level = Setting.Level.OWNER)
    public double withdrawFeePercent = 2.0;

    @Setting(path = "withdraw.mastery-fee-percent", desc = "Withdrawal fee in percent at island bank-fee mastery level 1, "
            + "2, 3 ... (owner #169: the fee gets lower as the island progresses, /is masteries). Level 0 pays "
            + "withdraw.fee-percent; levels above the list pay the last entry. Personal banks use the mastery of the profile's "
            + "island. A step above the one before is lowered to it.", level = Setting.Level.OWNER)
    public List<String> masteryFeePercent = List.of("1.5", "1.0", "0.5");

    @Setting(path = "withdraw.free-minutes", desc = "Minutes right after each interest payout in which withdrawals cost no fee "
            + "(the lowest-balance rule still applies). Owner R3-41: 0 = always the fee (the menu then says nothing about it).",
            min = 0, max = 1_440, level = Setting.Level.OWNER)
    public int withdrawFreeMinutes = 0;

    // ------------------------------------------------------------------ tiers and levels

    @Setting(path = "personal.tiers", desc = "Personal bank tiers 1-10, one line each: capacity, unlock cost, payout cap per "
            + "interest payout (amounts like 100k, 2.5M). Tier 1 is free (balance sheet 6.1; cost = 2% of capacity, owner #42; "
            + "UX3: x3-5 per tier up to $10B).",
            level = Setting.Level.OWNER)
    public List<String> personalTiers = List.of(
            "100k 0 600",
            "500k 10k 1.6k",
            "2M 40k 4.4k",
            "7.5M 150k 11.6k",
            "25M 500k 27.5k",
            "75M 1.5M 61k",
            "250M 5M 145k",
            "750M 15M 320k",
            "2.5B 50M 770k",
            "10B 200M 2.1M");

    @Setting(path = "island.levels", desc = "Island bank by level 0-10, one line each: capacity (Bank Capacity upgrade level) "
            + "and payout cap per interest payout (Bank Interest upgrade level). Balance sheet 6.2 (UX3: about x3 per level up to "
            + "$50B). /is upgrade shows these amounts.", level = Setting.Level.OWNER)
    public List<String> islandLevels = List.of(
            "1M 10k",
            "3M 24k",
            "10M 62k",
            "30M 150k",
            "100M 380k",
            "300M 900k",
            "1B 2.4M",
            "3B 5.7M",
            "8B 12M",
            "20B 26M",
            "50B 53M");

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

    // Owner answer 263 (round 1): island bank money belongs to the island and is deleted on disband; there is no
    // "pay the leader" option any more (the old key island.pay-leader-on-disband is ignored). See BankRules#onRemoved.

    // ------------------------------------------------------------------ menu

    @Setting(path = "history.page-size", desc = "Entries per page in the bank history.", min = 7, max = 28)
    public int historyPageSize = 28;

    @Override
    public String fileName() {
        return "bank.yml";
    }

    /**
     * Version 2 (BANK2, owner #169): interest every 8 h with higher rates and caps. Version 3 (UX3): rebalanced tier
     * tables (personal tier 10 $195.3B → $10B, island level 10 $1T → $50B). See {@link #upgradeValues}.
     */
    @Override
    public int version() {
        return 3;
    }

    /** The BANK2 tier tables that UX3 replaces in an existing bank.yml (only when still untouched). */
    static final Map<String, Object> BANK2_TIERS = Map.of(
            "personal.tiers", List.of("100k 0 600", "500k 10k 1920", "2.5M 50k 6144", "12.5M 250k 19661", "62.5M 1.25M 62915",
                    "312.5M 6.25M 201327", "1.5625B 31.25M 644245", "7.8125B 156.25M 2061584", "39.0625B 781.25M 6597070",
                    "195.3125B 3.90625B 21110623"),
            "island.levels", List.of("1M 10k", "4M 30k", "16M 90k", "64M 270k", "256M 810k", "1.024B 2.43M", "4.096B 7.29M",
                    "16.384B 21.87M", "65.536B 65.61M", "262.144B 196.83M", "1.048576T 590.49M"));

    /** M3.3 defaults that BANK2 replaces in an existing bank.yml (only when still untouched). */
    static final Map<String, Object> OLD_DEFAULTS = oldDefaults();

    private static Map<String, Object> oldDefaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("interest.period-hours", 3);
        m.put("interest.personal-rate-percent", 0.15);
        m.put("interest.island-rate-percent", 0.25);
        m.put("interest.catch-up-periods", 8);
        m.put("personal.tiers", List.of("100k 0 150", "500k 10k 480", "2.5M 50k 1536", "12.5M 250k 4915", "62.5M 1.25M 15729",
                "312.5M 6.25M 50332", "1.5625B 31.25M 161061", "7.8125B 156.25M 515396", "39.0625B 781.25M 1649267",
                "195.3125B 3.90625B 5277655"));
        m.put("island.levels", List.of("1M 2.5k", "4M 7.5k", "16M 22.5k", "64M 67.5k", "256M 202.5k", "1.024B 607.5k",
                "4.096B 1.8225M", "16.384B 5.4675M", "65.536B 16.4025M", "262.144B 49.2075M", "1.048576T 147.6225M"));
        return java.util.Collections.unmodifiableMap(m);
    }

    /**
     * Value changes the key renames can't do, applied to the raw file before it is loaded: every key that still holds
     * an untouched old default (M3.3 for version 1, the BANK2 tier tables for versions 1-2) gets today's default;
     * owner-chosen values are kept. Returns the paths that were changed (empty = nothing to do).
     */
    static List<String> upgradeValues(org.bukkit.configuration.file.YamlConfiguration yaml) {
        int from = yaml.getInt("config-version", 1);
        if (yaml.getKeys(false).isEmpty() || from >= 3) {
            return List.of();
        }
        BankConfig fresh = new BankConfig();
        Map<String, Object> now = Map.of(
                "interest.period-hours", fresh.periodHours,
                "interest.personal-rate-percent", fresh.personalRatePercent,
                "interest.island-rate-percent", fresh.islandRatePercent,
                "interest.catch-up-periods", fresh.catchUpPeriods,
                "personal.tiers", fresh.personalTiers,
                "island.levels", fresh.islandLevels);
        List<String> changed = new java.util.ArrayList<>();
        if (from < 2) {
            for (Map.Entry<String, Object> e : OLD_DEFAULTS.entrySet()) {
                if (sameValue(yaml.get(e.getKey()), e.getValue())) {
                    yaml.set(e.getKey(), now.get(e.getKey()));
                    changed.add(e.getKey());
                }
            }
        }
        for (String key : List.of("personal.tiers", "island.levels")) {
            if (!changed.contains(key) && sameValue(yaml.get(key), BANK2_TIERS.get(key))) {
                yaml.set(key, now.get(key));
                changed.add(key);
            }
        }
        return changed;
    }

    private static boolean sameValue(Object raw, Object old) {
        if (raw == null) {
            return false;
        }
        if (old instanceof List<?> list) {
            return raw instanceof List<?> r && r.stream().map(o -> String.valueOf(o).trim()).toList().equals(list);
        }
        if (raw instanceof Number n && old instanceof Number o) {
            return Math.abs(n.doubleValue() - o.doubleValue()) < 1e-9;
        }
        return false;
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
