package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the rewards module ({@code daily.yml}): the daily calendar, its streak and playtime rewards
 * (PHASE-10-REWARDS.md M10.6). Votes (M10.5) get their own {@code votes.yml}. Every number is a default the owner can
 * change; texts are in {@code messages/rewards.yml}.
 */
public final class RewardsSettings extends ModuleConfig {

    // ------------------------------------------------------------------ calendar

    @Setting(path = "calendar.enabled", desc = "Daily calendar on (/daily, the Daily Rewards NPC).")
    public boolean calendarEnabled = true;

    @Setting(path = "calendar.zone", desc = "Time zone of the daily reset (00:00 there; RX19: Europe/Oslo). Read at every claim.",
            level = Setting.Level.OWNER)
    public String zone = "Europe/Oslo";

    @Setting(path = "calendar.days", desc = "The calendar, one line per day: '<day>: <reward>'. Rewards: money <minutes of income "
            + "at your tier> | crystals <pouch base> | credits <n> (earned credits; old word gems) | key <crate> [n] | fuel <minutes> | booster <sell|skill_xp|tool_xp|"
            + "crystals> <percent> <minutes> | book <rarity> [level] | scroll [n] | tome. End a line with 'fallback <crystals>' for "
            + "the crystal base paid when that system is not there yet. After the last day it starts at day 1 again (P10-17).",
            level = Setting.Level.OWNER)
    public List<String> days = List.of(
            "1: money 3",
            "2: crystals 20",
            "3: key ember",
            "4: fuel 120 fallback 20",
            "5: money 5",
            "6: booster sell 25 15 fallback 25",
            "7: key cinder",
            "8: crystals 40",
            "9: booster skill_xp 25 15 fallback 25",
            "10: key ember 2",
            "11: money 5",
            "12: fuel 240 fallback 40",
            "13: crystals 40",
            "14: key blaze",
            "15: book common fallback 30",
            "16: money 8",
            "17: key ember",
            "18: booster sell 50 15 fallback 40",
            "19: crystals 80",
            "20: scroll fallback 60",
            "21: key cinder 2",
            "22: money 8",
            "23: fuel 360 fallback 60",
            "24: key ember 2",
            "25: tome fallback 120",
            "26: crystals 80",
            "27: booster skill_xp 50 30 fallback 60",
            "28: key inferno",
            "29: money 10",
            "30: key eclipse");

    @Setting(path = "calendar.default-fallback", desc = "Crystal base (scaled by tier like a pouch) paid for a reward whose system "
            + "is missing and that has no own 'fallback'. Also fills broken calendar lines.", min = 1, max = 100_000,
            level = Setting.Level.OWNER)
    public long defaultFallback = 30;

    @Setting(path = "calendar.items-bound", desc = "Items from the calendar and playtime (books, scrolls, boosters) are bound to the "
            + "profile that earned them (no trade, no AH; alts can't farm them). Keys are always bound (RX6).",
            level = Setting.Level.OWNER)
    public boolean itemsBound = true;

    @Setting(path = "calendar.join-reminder", desc = "Tell players on join when today's reward is not claimed yet (they can turn "
            + "it off in /settings).")
    public boolean joinReminder = true;

    @Setting(path = "calendar.reminder-delay-ticks", desc = "Ticks after joining before the reminder (the profile loads first).",
            min = 1, max = 1_200)
    public int reminderDelayTicks = 60;

    // ------------------------------------------------------------------ streak

    @Setting(path = "streak.credits-every", desc = "Every this many streak days pays the streak credits (earned; §9.2: every 7 "
            + "days). 0 = off.",
            min = 0, max = 365, level = Setting.Level.OWNER)
    public int creditsEvery = 7;

    @Setting(path = "streak.credits", desc = "Earned credits paid every 'credits-every' streak days (§9.2: 10).", min = 0, max = 1_000,
            level = Setting.Level.OWNER)
    public long credits = 10;

    @Setting(path = "streak.titles", desc = "Streak titles, '<streak days>: <title id>' (titles are account-wide). Display names: "
            + "messages/rewards.yml -> titles.<id>.", level = Setting.Level.OWNER)
    public List<String> streakTitles = List.of("30: devoted", "100: unbroken");

    @Setting(path = "streak.save-enabled", desc = "Show the 'Save streak' button (save a broken streak for 100 credits within 2 days, R5). "
            + "Stays hidden until the credit shop exists (Phase 11) even when on.", level = Setting.Level.OWNER)
    public boolean saveEnabled = false;

    // ------------------------------------------------------------------ playtime

    @Setting(path = "playtime.enabled", desc = "Playtime rewards on (R6): active play only, per profile.")
    public boolean playtimeEnabled = true;

    @Setting(path = "playtime.idle-minutes", desc = "Minutes without any action after which a player counts as AFK and their time "
            + "stops counting (PHASE-10: 5).", min = 1, max = 60)
    public int idleMinutes = 5;

    @Setting(path = "playtime.tick-seconds", desc = "How often active time is counted (seconds). Read at server start.",
            min = 5, max = 120, restart = true)
    public int tickSeconds = 20;

    @Setting(path = "playtime.save-seconds", desc = "How often counted time is saved and milestones are paid (seconds). Read at "
            + "server start.", min = 30, max = 900, restart = true)
    public int saveSeconds = 60;

    @Setting(path = "playtime.hourly-crystals", desc = "Crystal Pouch S base paid for every hour of active play (scaled by tier "
            + "x growth^(t-1); PHASE-10: Crystal Pouch S = 20). No money: it would add up too fast.", min = 0, max = 100_000,
            level = Setting.Level.OWNER)
    public long hourlyCrystals = 20;

    @Setting(path = "playtime.hourly-pass-xp", desc = "Battle pass XP for every hour of active play (PHASE-10: 20). Given only once "
            + "the battle pass exists.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public long hourlyPassXp = 20;

    @Setting(path = "playtime.key-every-hours", desc = "Every this many active hours pays a bound key (PHASE-10: 10). 0 = off.",
            min = 0, max = 1_000, level = Setting.Level.OWNER)
    public int keyEveryHours = 10;

    @Setting(path = "playtime.key-crate", desc = "Crate of the playtime key (PHASE-10: cinder).", level = Setting.Level.OWNER)
    public String keyCrate = "cinder";

    @Setting(path = "playtime.key-fallback", desc = "Crystal base paid instead of the key when the crates module is not there.",
            min = 0, max = 100_000, level = Setting.Level.OWNER)
    public long keyFallback = 40;

    @Setting(path = "playtime.titles", desc = "Playtime titles, '<active hours>: <title id>' (account titles, PR1). Display names: "
            + "messages/rewards.yml -> titles.<id>.", level = Setting.Level.OWNER)
    public List<String> playtimeTitles = List.of("50: regular", "100: veteran", "250: dedicated", "500: timeless");

    @Setting(path = "playtime.max-hours-per-payout", desc = "Most hourly rewards one payout gives (the rest follows at the next save).",
            min = 1, max = 1_000)
    public int maxHoursPerPayout = 24;

    // ------------------------------------------------------------------ pouches

    @Setting(path = "tier.stage-skills", desc = "Skills whose highest level decides the tier for money and crystal pouches (the same "
            + "rule as crates and the AFK pool).")
    public List<String> stageSkills = List.of("mining", "foraging", "combat");

    @Setting(path = "tier.tier-levels", desc = "Skill level that unlocks tiers 1-12 (the Depths unlock levels, ECONOMY_BALANCE §3.4).",
            level = Setting.Level.OWNER)
    public List<String> tierLevels = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58", "66");

    @Setting(path = "pouch.ref-per-minute", desc = "Reference income per minute (REF(t)/60, ECONOMY_BALANCE §1.2) for tiers 1-12; "
            + "'money N' pays N x this at the player's tier. Same numbers as crates-settings.yml.", level = Setting.Level.OWNER)
    public List<String> refPerMinute = List.of("827", "1467", "2560", "4507", "7920", "13867", "24347", "42667", "122133",
            "349333", "1000000", "2853333");

    @Setting(path = "pouch.crystal-growth", desc = "Crystal Pouch growth per tier: base x growth^(tier-1) (ECONOMY_BALANCE §9.1).",
            min = 1, max = 3, level = Setting.Level.OWNER)
    public double crystalGrowth = 1.3;

    // ------------------------------------------------------------------ hand-outs

    @Setting(path = "delivery.save-player-data", desc = "Save the player's data right after reward items are handed over, so a crash "
            + "can't roll the inventory back (SECFIX1 PlayerSaves).")
    public boolean savePlayerData = true;

    @Setting(path = "delivery.retry-seconds", desc = "How often waiting hand-outs (keys while the crates module was busy ...) are "
            + "tried again. Read at server start.", min = 30, max = 3_600, restart = true)
    public int retrySeconds = 300;

    @Setting(path = "delivery.keep-days", desc = "Days delivered hand-out rows are kept (history stays in /logs).", min = 1, max = 365)
    public int keepDays = 14;

    @Setting(path = "npc.daily-role", desc = "NPC role whose click opens /daily (sneak-click keeps the help text).")
    public String npcRole = "daily";

    /** Version 2 (card C1): gems were renamed credits (earned credits); the gem keys are moved. */
    @Override
    public int version() {
        return 2;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, String>> migrations() {
        return java.util.Map.of(2, java.util.Map.of("streak.gem-every", "streak.credits-every", "streak.gems", "streak.credits"));
    }

    @Override
    public String fileName() {
        return "daily.yml";
    }

    @Override
    public String header() {
        return "Daily calendar, streak and playtime rewards (PHASE-10 M10.6). Texts: messages/rewards.yml.\n"
                + "Reload: /skyblock reload rewards";
    }
}
