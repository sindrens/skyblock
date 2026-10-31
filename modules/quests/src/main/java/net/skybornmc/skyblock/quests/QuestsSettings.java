package net.skybornmc.skyblock.quests;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the quests module ({@code quests.yml}, PHASE-10-REWARDS.md M10.7, P10-21, P10-22, RX23). Every number is
 * a default the owner can change; texts are in {@code messages/quests.yml}.
 */
public final class QuestsSettings extends ModuleConfig {

    @Setting(path = "enabled", desc = "Quests on (/quests, the Quest Master NPC, progress and rewards).")
    public boolean enabled = true;

    @Setting(path = "zone", desc = "Time zone of the resets: daily at 00:00, weekly and island missions Monday 00:00 (RX19: Europe/Oslo).",
            level = Setting.Level.OWNER)
    public String zone = "Europe/Oslo";

    // ------------------------------------------------------------------ daily

    @Setting(path = "daily.count", desc = "Daily quests per profile per day (P10-21: 3).", min = 0, max = 5, level = Setting.Level.OWNER)
    public int dailyCount = 3;

    @Setting(path = "daily.minutes", desc = "Minutes of play one daily quest is sized for (PHASE-10: ~10-15).", min = 1, max = 120,
            level = Setting.Level.OWNER)
    public int dailyMinutes = 12;

    @Setting(path = "daily.free-rerolls", desc = "Free swaps per day: click a daily quest to swap it for another (P10-21: 1).",
            min = 0, max = 5, level = Setting.Level.OWNER)
    public int dailyRerolls = 1;

    @Setting(path = "daily.money-minutes", desc = "Money per daily quest in minutes of income at your tier (P10-21: 3).", min = 0,
            max = 120, level = Setting.Level.OWNER)
    public double dailyMoneyMinutes = 3;

    @Setting(path = "daily.crystals", desc = "Crystals per daily quest: base x crystal-growth^(tier-1) (Crystal Pouch S = 20).",
            min = 0, max = 100_000, level = Setting.Level.OWNER)
    public long dailyCrystals = 20;

    @Setting(path = "daily.pass-xp", desc = "Battle pass XP per daily quest (P10-21: 100). Paid once the battle pass exists.",
            min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int dailyPassXp = 100;

    @Setting(path = "daily.bonus-crate", desc = "Crate of the key for finishing all daily quests (P10-21: cinder). Tradeable key "
            + "(source quest_daily, P10-6). Empty = no bonus.", level = Setting.Level.OWNER)
    public String dailyBonusCrate = "cinder";

    @Setting(path = "daily.pool", desc = "Daily quest pool, one '<type> [target]' per line. Types: mine, chop, kill (Depths, target from "
            + "the play rate at your tier), sell (money, from your income), tool_levels, forge, enchant, visit, like (these need a "
            + "number). Only types you have unlocked are offered; a set never has a type twice or two of visit/like.",
            level = Setting.Level.OWNER)
    public List<String> dailyPool = List.of("mine", "chop", "kill", "sell", "tool_levels 1", "visit 3", "like 1");

    // ------------------------------------------------------------------ weekly

    @Setting(path = "weekly.count", desc = "Weekly quests per profile per week (P10-22: 5).", min = 0, max = 7, level = Setting.Level.OWNER)
    public int weeklyCount = 5;

    @Setting(path = "weekly.minutes", desc = "Minutes of play one weekly quest is sized for (PHASE-10: ~45).", min = 1, max = 600,
            level = Setting.Level.OWNER)
    public int weeklyMinutes = 45;

    @Setting(path = "weekly.money-minutes", desc = "Money per weekly quest in minutes of income at your tier (P10-22: 8).", min = 0,
            max = 600, level = Setting.Level.OWNER)
    public double weeklyMoneyMinutes = 8;

    @Setting(path = "weekly.crystals", desc = "Crystals per weekly quest: base x crystal-growth^(tier-1).", min = 0, max = 100_000,
            level = Setting.Level.OWNER)
    public long weeklyCrystals = 60;

    @Setting(path = "weekly.pass-xp", desc = "Battle pass XP per weekly quest (P10-22: 400). Paid once the battle pass exists.",
            min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int weeklyPassXp = 400;

    @Setting(path = "weekly.bonus-crate", desc = "Crate of the key for finishing all weekly quests (P10-22: blaze). Tradeable key "
            + "(source quest_weekly). Empty = no bonus.", level = Setting.Level.OWNER)
    public String weeklyBonusCrate = "blaze";

    @Setting(path = "weekly.pool", desc = "Weekly quest pool, same format as daily.pool.", level = Setting.Level.OWNER)
    public List<String> weeklyPool = List.of("mine", "chop", "kill", "sell", "tool_levels 3", "forge 1", "enchant 1", "visit 10", "like 3");

    // ------------------------------------------------------------------ island missions

    @Setting(path = "island.count", desc = "Island missions per island per week, shared by its members, never coop players (RX23: 3).",
            min = 0, max = 7, level = Setting.Level.OWNER)
    public int islandCount = 3;

    @Setting(path = "island.minutes", desc = "Minutes of play one island mission is sized for (all members together).", min = 1,
            max = 1_200, level = Setting.Level.OWNER)
    public int islandMinutes = 60;

    @Setting(path = "island.crystals", desc = "Crystals for every member who helped: base x crystal-growth^(tier-1) (RX23).", min = 0,
            max = 100_000, level = Setting.Level.OWNER)
    public long islandCrystals = 60;

    @Setting(path = "island.pass-xp", desc = "Battle pass XP for every member who helped (PHASE-10: 150).", min = 0, max = 100_000,
            level = Setting.Level.OWNER)
    public int islandPassXp = 150;

    @Setting(path = "island.helper-min-percent", desc = "A member counts as a helper with at least this share of the mission (%). "
            + "Stops a 1-block share from paying a full reward.", min = 0, max = 50, level = Setting.Level.OWNER)
    public double helperMinPercent = 5;

    @Setting(path = "island.pool", desc = "Island mission pool, same format as daily.pool (only mine, chop, kill and sell make sense "
            + "for a whole island).", level = Setting.Level.OWNER)
    public List<String> islandPool = List.of("mine", "chop", "kill", "sell");

    // ------------------------------------------------------------------ sizing

    @Setting(path = "rates.mine", desc = "Blocks a regular breaks per played minute in mine tiers 1-12 (ECONOMY_BALANCE §3.1 per hour / 60).",
            level = Setting.Level.OWNER)
    public List<String> rateMine = List.of("31.8", "37.5", "44", "48.4", "53.2", "60.2", "65.7", "68.3", "77.3", "83.6", "92.4", "97");

    @Setting(path = "rates.forest", desc = "Logs per played minute in forest tiers 1-11 (parity with the mine, §3.2).",
            level = Setting.Level.OWNER)
    public List<String> rateForest = List.of("31.8", "37.5", "44", "48.4", "53.2", "60.2", "65.7", "68.3", "77.3", "83.6", "92.4");

    @Setting(path = "rates.combat", desc = "Kills per played minute in combat tiers 1-10 (§3.2 kills per hour / 60).",
            level = Setting.Level.OWNER)
    public List<String> rateCombat = List.of("6.1", "6.4", "6.6", "6.9", "7.1", "7.4", "7.6", "7.8", "8.1", "8.3");

    @Setting(path = "rates.sell-share", desc = "Sell quests: the part of your income per minute (REF) you sell in that time.",
            min = 0.05, max = 5, level = Setting.Level.OWNER)
    public double sellShare = 0.8;

    @Setting(path = "rates.tier-offset", desc = "Mine/chop/kill quests count from your best open Depths tier minus this (1 = one tier "
            + "lower or deeper, so you can pick).", min = 0, max = 5, level = Setting.Level.OWNER)
    public int tierOffset = 1;

    @Setting(path = "tier.stage-skills", desc = "Skills whose highest level decides the tier for money and crystals (the same rule as "
            + "crates, the daily calendar and the AFK pool).")
    public List<String> stageSkills = List.of("mining", "foraging", "combat");

    @Setting(path = "tier.tier-levels", desc = "Skill level that unlocks tiers 1-12 (the Depths unlock levels, ECONOMY_BALANCE §3.4).",
            level = Setting.Level.OWNER)
    public List<String> tierLevels = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58", "66");

    @Setting(path = "pouch.ref-per-minute", desc = "Reference income per minute (REF(t)/60, ECONOMY_BALANCE §1.2) for tiers 1-12. Same "
            + "numbers as daily.yml and crates-settings.yml.", level = Setting.Level.OWNER)
    public List<String> refPerMinute = List.of("827", "1467", "2560", "4507", "7920", "13867", "24347", "42667", "122133",
            "349333", "1000000", "2853333");

    @Setting(path = "pouch.crystal-growth", desc = "Crystal growth per tier: base x growth^(tier-1) (ECONOMY_BALANCE §9.1).",
            min = 1, max = 3, level = Setting.Level.OWNER)
    public double crystalGrowth = 1.3;

    // ------------------------------------------------------------------ rules

    @Setting(path = "rules.excluded-sell-sources", desc = "Sale sources that never count for sell quests (afk = the AFK pool).",
            level = Setting.Level.OWNER)
    public List<String> excludedSellSources = List.of("afk");

    @Setting(path = "rules.like-min-playtime-minutes", desc = "Like quests are only offered after this much playtime (islands only "
            + "allow likes after 1 hour, owner 292).", min = 0, max = 10_080)
    public int likeMinPlaytimeMinutes = 60;

    @Setting(path = "notify.step-percent", desc = "Action bar progress every this many percent (PHASE-10: 25).", min = 5, max = 50)
    public int stepPercent = 25;

    @Setting(path = "notify.sound-done", desc = "Sound group of a finished quest (style.yml sounds).")
    public String soundDone = "success";

    // ------------------------------------------------------------------ technical

    @Setting(path = "save.seconds", desc = "How often quest progress is saved (seconds). A crash loses at most this much progress, "
            + "never a reward. Read at server start.", min = 5, max = 300, restart = true)
    public int saveSeconds = 30;

    @Setting(path = "delivery.retry-seconds", desc = "How often waiting hand-outs (keys while the crates module was busy ...) are tried "
            + "again. Read at server start.", min = 30, max = 3_600, restart = true)
    public int retrySeconds = 300;

    @Setting(path = "history.keep-days", desc = "Days old quest windows and delivered hand-outs are kept (history stays in /logs).",
            min = 8, max = 365)
    public int keepDays = 21;

    @Setting(path = "npc.role", desc = "NPC role whose click opens /quests (the Tavern's Quest Master; sneak-click keeps the help text).")
    public String npcRole = "quests";

    @Override
    public String fileName() {
        return "quests.yml";
    }

    @Override
    public String header() {
        return "Quests (PHASE-10 M10.7): daily and weekly quests per profile, island missions per island. Texts: messages/quests.yml.\n"
                + "Reload: /skyblock reload quests";
    }
}
