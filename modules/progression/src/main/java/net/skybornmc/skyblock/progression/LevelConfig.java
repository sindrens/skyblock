package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/**
 * Typed settings of {@code progression.yml}: the dynamic island level (PHASE-5 M5.2, owner P5-1). Island Level =
 * floor(total level points / divisor). Eight sources; capped sources can't be more than their share of the total
 * (owner O3: block worth at most 20%). Numbers are tuned in M5.9 (BAL3). Block values: {@code block-values.yml}.
 */
public final class LevelConfig extends ModuleConfig {

    @Override
    public String fileName() {
        return "progression.yml";
    }

    @Override
    public String header() {
        return """
                Dynamic island level (Phase 5, M5.2). Island Level = floor(total level points / level.divisor).
                Sources: pillars, upgrades + masteries, island achievements, collections (earned, never go down in a season)
                and members' skills, Depths tiers, island bank, block worth (live, follow the island as it is now).
                A capped source can't be more than its cap-percent of the TOTAL (caps applied once, smallest first).
                Coop players never count. Members' skills and Depths tiers count members with 3+ days on the island only.
                Block values: block-values.yml. Texts: messages/progression.yml. Reload: /skyblock reload progression""";
    }

    // ------------------------------------------------------------------ level

    @Setting(path = "level.divisor", desc = "Island Level = floor(total points / this).", min = 1, max = 100_000, level = Setting.Level.OWNER)
    public double divisor = 10;

    // ------------------------------------------------------------------ source points

    @Setting(path = "points.per-pillar-level", desc = "Points per shown pillar level (source 1, uncapped).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double perPillarLevel = 1;

    @Setting(path = "points.skills-divisor", desc = "Members' skills points = average total skill level of counted members / this (source 5).", min = 0.01, max = 100_000, level = Setting.Level.OWNER)
    public double skillsDivisor = 10;

    @Setting(path = "points.per-depths-tier", desc = "Points per unlocked Depths tier, best counted member per aspect (source 6).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double perDepthsTier = 1;

    @Setting(path = "points.bank-factor", desc = "Bank points = bank-factor x log10(lowest balance of the window / bank-base), never below 0 (source 7).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double bankFactor = 4;

    @Setting(path = "points.bank-base", desc = "Money where the bank starts to give points (log scale).", min = 1, max = 1_000_000_000_000L, level = Setting.Level.OWNER)
    public long bankBase = 100_000;

    @Setting(path = "points.blocks-factor", desc = "Block points = blocks-factor x square root(block worth / blocks-unit) (source 8).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double blocksFactor = 0.5;

    @Setting(path = "points.blocks-unit", desc = "Money unit of the block worth square root.", min = 1, max = 1_000_000_000L, level = Setting.Level.OWNER)
    public double blocksUnit = 1000;

    // ------------------------------------------------------------------ caps (percent of the total, 0 = no cap)

    @Setting(path = "caps.achievements-percent", desc = "Most island achievements can be of the total (percent, 0 = no cap).", min = 0, max = 99, level = Setting.Level.OWNER)
    public double capAchievements = 10;

    @Setting(path = "caps.collections-percent", desc = "Most collections can be of the total (percent, 0 = no cap).", min = 0, max = 99, level = Setting.Level.OWNER)
    public double capCollections = 5;

    @Setting(path = "caps.skills-percent", desc = "Most members' skills can be of the total (percent, 0 = no cap).", min = 0, max = 99, level = Setting.Level.OWNER)
    public double capSkills = 8;

    @Setting(path = "caps.depths-percent", desc = "Most Depths progress can be of the total (percent, 0 = no cap).", min = 0, max = 99, level = Setting.Level.OWNER)
    public double capDepths = 6;

    @Setting(path = "caps.bank-percent", desc = "Most the island bank can be of the total (percent, 0 = no cap).", min = 0, max = 99, level = Setting.Level.OWNER)
    public double capBank = 5;

    @Setting(path = "caps.blocks-percent", desc = "Most block worth can be of the total (percent, owner O3: 20).", min = 0, max = 99, level = Setting.Level.OWNER)
    public double capBlocks = 20;

    // ------------------------------------------------------------------ anti-abuse

    @Setting(path = "members.min-days", desc = "Members' skills and Depths tiers count only members on the island for at least this many days (owner P5-4). Coop players never count.", min = 0, max = 365, level = Setting.Level.OWNER)
    public double memberMinDays = 3;

    @Setting(path = "bank.window-hours", desc = "The bank counts its LOWEST balance of this many hours, so a deposit just before a check does nothing (owner P5-5). Until the island bank was seen for this long it counts 0.", min = 1, max = 24 * 14, level = Setting.Level.OWNER)
    public int bankWindowHours = 24;

    @Setting(path = "bank.sample-minutes", desc = "The island bank balance is sampled at most this often per island (a lower balance is always sampled).", min = 1, max = 24 * 60)
    public int bankSampleMinutes = 15;

    // ------------------------------------------------------------------ recalculation

    @Setting(path = "recalc.debounce-seconds", desc = "A change waits this long for more changes before the level is calculated again.", min = 0, max = 600)
    public int debounceSeconds = 5;

    @Setting(path = "recalc.max-wait-seconds", desc = "Constant changes (building) recalculate at least this often.", min = 1, max = 3600)
    public int maxWaitSeconds = 30;

    @Setting(path = "recalc.per-tick", desc = "Most island levels started per tick (the database reads run off the main thread).", min = 1, max = 100)
    public int perTick = 4;

    @Setting(path = "recalc.refresh-minutes", desc = "Islands with an online member are recalculated this often (members leaving, bank withdrawals, 3-day rule).", min = 1, max = 1440)
    public int refreshMinutes = 10;

    @Setting(path = "recalc.skills-cache-minutes", desc = "Skill levels of OFFLINE members are read from the database at most this often per member.", min = 0, max = 1440)
    public int skillsCacheMinutes = 10;

    // ------------------------------------------------------------------ block worth

    @Setting(path = "blocks.track-live", desc = "Track block worth live from place, break, explosion, burn and piston events.")
    public boolean trackLive = true;

    @Setting(path = "blocks.rescan-hour", desc = "Hour of the nightly full rescan (0-23, level time zone) of islands that changed that day. -1 = never.", min = -1, max = 23)
    public int rescanHour = 4;

    @Setting(path = "blocks.time-zone", desc = "Time zone of the nightly rescan and the daily level history.", restart = true)
    public String timeZone = "Europe/Oslo";

    @Setting(path = "blocks.chunks-per-tick", desc = "Rescan speed: chunks loaded and copied per tick (the block count runs off the main thread).", min = 1, max = 64)
    public int chunksPerTick = 2;

    @Setting(path = "blocks.recalculate-cooldown-minutes", desc = "The Recalculate button: one full rescan per island this often (owner P5-34: 10 min).", min = 0, max = 1440)
    public int recalculateCooldownMinutes = 10;

    @Setting(path = "blocks.flush-seconds", desc = "Changed chunk worths are written to the database this often (and on shutdown).", min = 2, max = 600)
    public int blocksFlushSeconds = 30;

    // ------------------------------------------------------------------ leaderboards

    @Setting(path = "top.refresh-seconds", desc = "The island boards (/is top, Hall of Heroes) are ranked again this often (from memory, no database).", min = 10, max = 3600)
    public int topRefreshSeconds = 300;

    // ------------------------------------------------------------------ notices

    @Setting(path = "notify.level-up", desc = "Tell online members (title, chat line, sound) when the island reaches a new highest level this season.")
    public boolean notifyLevelUp = true;

    @Setting(path = "notify.global-every-levels", desc = "Global chat line every this many levels (new season highs only, owner P5-32). 0 = never.", min = 0, max = 10_000)
    public int globalEvery = 10;

    @Setting(path = "notify.level-up-sound", desc = "Sound key (sounds.yml) for an island level-up.")
    public String levelUpSound = "unlock";

    @Setting(path = "notify.recalculate-sound", desc = "Sound key (sounds.yml) when a member starts a rescan.")
    public String recalculateSound = "click";
}
