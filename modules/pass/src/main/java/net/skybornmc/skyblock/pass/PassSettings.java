package net.skybornmc.skyblock.pass;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the battle pass ({@code pass.yml}, PHASE-10-REWARDS.md M10.8, P10-24, P10-25, P10-26 with R3-44, P10-28).
 * Every number is a default the owner can change; texts are in {@code messages/pass.yml}.
 */
public final class PassSettings extends ModuleConfig {

    @Setting(path = "enabled", desc = "Battle pass on (/pass, pass XP from quests, votes, the calendar and playtime, claims).")
    public boolean enabled = true;

    @Setting(path = "zone", desc = "Time zone of the season day count (catch-up; RX19: Europe/Oslo).", level = Setting.Level.OWNER)
    public String zone = "Europe/Oslo";

    // ------------------------------------------------------------------ season

    @Setting(path = "season.id", desc = "Season id (letters, digits, - and _; at most 32). XP, claims and premium are stored per "
            + "season, so a new id starts everyone at level 0 (Phase 12 seasons will set this). Read at every action.",
            level = Setting.Level.OWNER)
    public String seasonId = "s1";

    @Setting(path = "season.start", desc = "First day of the season (yyyy-mm-dd, in the zone above). Used for the catch-up day line "
            + "and the countdown. Empty = no catch-up and no countdown.", level = Setting.Level.OWNER)
    public String seasonStart = "";

    @Setting(path = "season.days", desc = "Season length in days (R1: 90).", min = 1, max = 366, level = Setting.Level.OWNER)
    public int seasonDays = 90;

    // ------------------------------------------------------------------ levels

    @Setting(path = "levels.max", desc = "Levels of the pass (P10-24: 100).", min = 1, max = 500, level = Setting.Level.OWNER)
    public int maxLevel = 100;

    @Setting(path = "levels.xp-per-level", desc = "Pass XP per level, the same for every level (P10-24: 800, no wall at the end).",
            min = 1, max = 1_000_000, level = Setting.Level.OWNER)
    public int xpPerLevel = 800;

    @Setting(path = "levels.overflow-xp", desc = "After the last level: pass XP per overflow reward (RX31: 2,000). 0 = no overflow "
            + "rewards.", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public int overflowXp = 2_000;

    // ------------------------------------------------------------------ catch-up

    @Setting(path = "catch-up.enabled", desc = "RX30: extra pass XP while you are far behind the day line (needs season.start).",
            level = Setting.Level.OWNER)
    public boolean catchUpEnabled = true;

    @Setting(path = "catch-up.levels-per-day", desc = "The day line: day of the season x this many levels (PHASE-10: 1.1).",
            min = 0.1, max = 10, level = Setting.Level.OWNER)
    public double catchUpLevelsPerDay = 1.1;

    @Setting(path = "catch-up.behind-levels", desc = "Catch-up starts when you are this many levels (or more) behind the day line "
            + "(PHASE-10: 15).", min = 1, max = 500, level = Setting.Level.OWNER)
    public int catchUpBehind = 15;

    @Setting(path = "catch-up.multiplier", desc = "Pass XP multiplier while behind (PHASE-10: +50% = 1.5).", min = 1, max = 5,
            level = Setting.Level.OWNER)
    public double catchUpMultiplier = 1.5;

    // ------------------------------------------------------------------ tracks

    @Setting(path = "free.every-level", desc = "Free track (per profile): the small reward of every level, in turn (level 1 = the "
            + "first line, level 2 the second, ...; PHASE-10: money 2 min of income or crystals). Rewards: money <minutes of income at "
            + "your tier> | crystals <pouch base> | key <crate> [n] | booster <sell|skill_xp|tool_xp|crystals> <percent> <minutes> | "
            + "title <id> [rarity] | tag <id>. Empty = no small rewards.", level = Setting.Level.OWNER)
    public List<String> freeEveryLevel = List.of("money 2", "crystals 20");

    @Setting(path = "free.milestones", desc = "Free track extras on top of the small reward, one '<level>: <reward>' per line (a level "
            + "may have several lines). P10-25: Ember x2 at 5, 15, 35 ...; Cinder at 10, 20, 30, 40, 60, 70, 80, 90; Blaze at 25 "
            + "and 75; Inferno at 50; Phoenix at 100; titles at 50 and 100. Pass keys are tradeable (key source 'pass', P10-6).",
            level = Setting.Level.OWNER)
    public List<String> freeMilestones = List.of(
            "5: key ember 2", "10: key cinder", "12: booster sell 25 30", "15: key ember 2", "20: key cinder", "25: key blaze",
            "30: key cinder", "35: key ember 2", "37: booster skill_xp 25 30", "40: key cinder", "45: key ember 2",
            "50: key inferno", "50: title pass_climber epic", "55: key ember 2", "60: key cinder", "62: booster sell 25 30",
            "65: key ember 2", "70: key cinder", "75: key blaze", "80: key cinder", "85: key ember 2",
            "87: booster skill_xp 25 30", "90: key cinder", "95: key ember 2", "100: key phoenix",
            "100: title pass_champion legendary");

    @Setting(path = "free.overflow", desc = "RX31: the reward of every overflow step after the last level (Crystal Pouch M + 1 "
            + "bound Ember key; overflow keys use the bound key source 'pass_overflow'). Same reward words as free.every-level.",
            level = Setting.Level.OWNER)
    public List<String> freeOverflow = List.of("crystals 60", "key ember");

    @Setting(path = "premium.milestones", desc = "Premium track (per account, P10-28): '<level>: <reward>' lines. COSMETICS ONLY "
            + "(R3-44, Mojang rules): only title <id> [rarity] and tag <id> are accepted; anything else is skipped with a warning. "
            + "A tag id must exist in tags-titles.yml; titles are added there by the pass if missing (names: messages/pass.yml "
            + "titles).", level = Setting.Level.OWNER)
    public List<String> premiumMilestones = List.of(
            "5: title pass_spark rare", "10: title pass_kindled rare", "15: title pass_ashwalker rare",
            "20: title pass_cinderborn rare", "25: title pass_flamecaller epic", "30: title pass_smokeweaver epic",
            "35: title pass_emberheart epic", "40: title pass_firebrand epic", "45: title pass_pyrekeeper epic",
            "50: title pass_blazeborn legendary", "55: title pass_heatwave epic", "60: title pass_wildfire epic",
            "65: title pass_sunforged epic", "70: title pass_flameheart epic", "75: title pass_infernal legendary",
            "80: title pass_starfire legendary", "85: title pass_ashen_crown legendary", "90: title pass_everflame legendary",
            "95: title pass_risen legendary", "100: title pass_ascendant mythic");

    // ------------------------------------------------------------------ money and crystals

    @Setting(path = "tier.stage-skills", desc = "Skills whose highest level decides the tier for money and crystals (the same rule as "
            + "crates, the daily calendar and quests).")
    public List<String> stageSkills = List.of("mining", "foraging", "combat");

    @Setting(path = "tier.tier-levels", desc = "Skill level that unlocks tiers 1-12 (the Depths unlock levels, ECONOMY_BALANCE §3.4).",
            level = Setting.Level.OWNER)
    public List<String> tierLevels = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58", "66");

    @Setting(path = "pouch.ref-per-minute", desc = "Reference income per minute (REF(t)/60, ECONOMY_BALANCE §1.2) for tiers 1-12. Same "
            + "numbers as quests.yml, daily.yml and crates-settings.yml.", level = Setting.Level.OWNER)
    public List<String> refPerMinute = List.of("827", "1467", "2560", "4507", "7920", "13867", "24347", "42667", "122133",
            "349333", "1000000", "2853333");

    @Setting(path = "pouch.crystal-growth", desc = "Crystal growth per tier: base x growth^(tier-1) (ECONOMY_BALANCE §9.1).",
            min = 1, max = 3, level = Setting.Level.OWNER)
    public double crystalGrowth = 1.3;

    // ------------------------------------------------------------------ claims

    @Setting(path = "claim.auto", desc = "P10-29 (open question): claim new free and premium tiers by themselves at every level-up "
            + "instead of clicking them in /pass. Off = click a tier or 'Claim all' (also the /menu Rewards button).",
            level = Setting.Level.OWNER)
    public boolean autoClaim = false;

    @Setting(path = "claim.max-per-click", desc = "Most tiers one 'Claim all' claims in one go (the rest stay ready).", min = 1,
            max = 500)
    public int maxPerClaim = 100;

    @Setting(path = "notify.level-up-sound", desc = "Sound group of a pass level-up (style.yml sounds).")
    public String levelUpSound = "unlock";

    // ------------------------------------------------------------------ technical

    @Setting(path = "delivery.retry-seconds", desc = "How often waiting hand-outs (keys while the crates module was busy ...) are tried "
            + "again. Read at server start.", min = 30, max = 3_600, restart = true)
    public int retrySeconds = 300;

    @Setting(path = "history.keep-days", desc = "Days XP event keys and delivered hand-outs are kept (retries arrive within minutes; "
            + "history stays in /logs).", min = 7, max = 365)
    public int keepDays = 30;

    @Setting(path = "max-xp-per-event", desc = "Most pass XP one event may add (guards against a broken source). Bigger amounts are "
            + "cut to this.", min = 1, max = 1_000_000)
    public int maxXpPerEvent = 10_000;

    @Override
    public String fileName() {
        return "pass.yml";
    }

    @Override
    public String header() {
        return "Battle pass (PHASE-10 M10.8): pass XP per profile, free track per profile, cosmetic-only premium track per account.\n"
                + "Texts: messages/pass.yml. Reload: /skyblock reload pass";
    }
}
