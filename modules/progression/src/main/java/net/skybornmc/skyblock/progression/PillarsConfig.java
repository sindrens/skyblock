package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of {@code pillars.yml} (PHASE-5 M5.1). Lists of {@code key: value} entries are parsed by
 * {@link PillarRules}; broken entries are reported at load and skipped. Pace targets and the curve base are tuned in
 * M5.9 (BAL3) with {@code tools/economy_model.py}.
 */
public final class PillarsConfig extends ModuleConfig {

    @Override
    public String fileName() {
        return "pillars.yml";
    }

    @Override
    public String header() {
        return """
                Island pillars (Phase 5, M5.1): Mining, Foraging, Farming, Combat and Conquest (hidden until it has a source).
                Pillar XP = the BASE skill XP island members earn (before rank, booster and personal boosts), plus island
                crops, island trees and spawner mobs killed by a player. Coop players and visitors give nothing.
                XP for the next level = curve.base x curve.growth^level (no cap). Texts: messages/progression.yml.
                Reload: /skyblock reload progression""";
    }

    // ------------------------------------------------------------------ season

    @Setting(path = "season-id", desc = "Current season; pillar rows are stored per season (must match the islands module's season).", min = 0, restart = true, level = Setting.Level.OWNER)
    public int seasonId = 0;

    // ------------------------------------------------------------------ curve

    @Setting(path = "curve.base", desc = "Pillar XP from level 0 to 1. Pace targets (regular solo): pillar 10 in 1-2 h, 25 on day 2-3, 40 in week 2, 60 in month 2.", min = 1, max = 1_000_000, level = Setting.Level.OWNER)
    public double curveBase = 250;

    @Setting(path = "curve.growth", desc = "Each level costs this much more than the one before (1.12 = 12%). Also damps team size: 5x the XP = ln5/ln1.12 = ~14 levels more.", min = 1.0, max = 2.0, level = Setting.Level.OWNER)
    public double curveGrowth = 1.12;

    // ------------------------------------------------------------------ conquest

    @Setting(path = "conquest.enabled", desc = "Show the Conquest pillar and count it for 'all pillars'. Keep off until KOTH, envoys or events feed it (owner P5-8).", level = Setting.Level.OWNER)
    public boolean conquestEnabled = false;

    // ------------------------------------------------------------------ skill XP

    @Setting(path = "skill-sources", desc = "Which skill feeds which pillar, as 'skill: pillar multiplier' (multiplier optional, default 1). Skills not listed feed nothing.", level = Setting.Level.OWNER)
    public List<String> skillSources = List.of("mining: mining 1.0", "foraging: foraging 1.0", "combat: combat 1.0",
            "farming: farming 1.0", "fishing: farming 1.0");

    @Setting(path = "excluded-sources", desc = "Skill XP sources that never give pillar XP (stack_kill = extra kills of stacked batch kills, anti-AFK farm, owner P5-7).", level = Setting.Level.OWNER)
    public List<String> excludedSources = List.of("stack_kill");

    // ------------------------------------------------------------------ island XP family

    @Setting(path = "island-xp.cap-percent", desc = "Cap of the ISLAND_XP family (balance bonus + island achievements + other island boosts).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double islandXpCapPercent = 25;

    @Setting(path = "balance-bonus.per-pillar-percent", desc = "Balance bonus: percent per shown pillar within balance-bonus.window-levels of the highest one.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double balancePerPillarPercent = 2;

    @Setting(path = "balance-bonus.window-levels", desc = "A pillar counts for the balance bonus when it is at most this many levels below the highest pillar.", min = 0, max = 1000, level = Setting.Level.OWNER)
    public int balanceWindowLevels = 10;

    @Setting(path = "balance-bonus.max-percent", desc = "Most the balance bonus can give (it still counts in the ISLAND_XP cap).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double balanceMaxPercent = 10;

    // ------------------------------------------------------------------ synergy milestones

    @Setting(path = "milestones.levels", desc = "Synergy milestones: every shown pillar at this level or higher. Phase 7 hangs outposts and biomes on them.", level = Setting.Level.OWNER)
    public List<String> milestoneLevels = List.of("10", "25", "40", "60");

    @Setting(path = "milestones.crystals-per-member", desc = "Island crystals paid once to every member (at that moment) when the island reaches a milestone, as 'level: crystals'.", level = Setting.Level.OWNER)
    public List<String> milestoneCrystals = List.of("10: 500", "25: 1500", "40: 4000", "60: 10000");

    // ------------------------------------------------------------------ island sources (not skill XP)

    @Setting(path = "island-sources.crops.enabled", desc = "Ripe crops broken by a member on their own island give Farming pillar XP.")
    public boolean cropsEnabled = true;

    @Setting(path = "island-sources.crops.pause-while-farming-skill-pays", desc = "Stop the crop source while the Farming skill gives XP (Phase 7), so crops are not counted twice.")
    public boolean cropsPauseWithFarmingSkill = true;

    @Setting(path = "island-sources.crops.xp", desc = "Pillar XP per fully grown crop block, as 'block: xp'. Only crops with a growth age count (they can't be placed ripe).", level = Setting.Level.OWNER)
    public List<String> cropXp = List.of("wheat: 0.25", "carrots: 0.25", "potatoes: 0.25", "beetroots: 0.25",
            "nether_wart: 0.3", "cocoa: 0.3", "torchflower_crop: 0.3", "pitcher_crop: 0.3");

    @Setting(path = "island-sources.logs.enabled", desc = "Logs a member chops on their own island give Foraging pillar XP (logs placed by players give nothing).")
    public boolean logsEnabled = true;

    @Setting(path = "island-sources.logs.xp-per-log", desc = "Pillar XP per log chopped on the island.", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double logXp = 1.0;

    @Setting(path = "island-sources.logs.placed-memory", desc = "Anti place-and-break: how many placed log positions each island remembers (broken again = no XP).", min = 0, max = 100_000)
    public int placedMemory = 2000;

    @Setting(path = "island-sources.spawner-kills.enabled", desc = "Spawner mobs a member kills on their own island give Combat pillar XP. Turn off if skills.yml combat.spawner-mobs-give-xp is on (counted twice).")
    public boolean spawnerKillsEnabled = true;

    @Setting(path = "island-sources.spawner-kills.xp-per-kill", desc = "Pillar XP per spawner mob killed by a player (mobs not listed in xp-per-mob).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double spawnerKillXp = 1.0;

    @Setting(path = "island-sources.spawner-kills.xp-per-mob", desc = "Pillar XP per kill for single mobs, as 'entity: xp' (e.g. 'blaze: 1.5').", level = Setting.Level.OWNER)
    public List<String> spawnerMobXp = List.of();

    @Setting(path = "island-sources.spawner-kills.damage-causes", desc = "Last damage that must have killed the mob (a player's own hit or arrow). Fall, lava and stack kills give 0 (owner P5-7).", level = Setting.Level.OWNER)
    public List<String> spawnerKillCauses = List.of("ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK", "PROJECTILE");

    // ------------------------------------------------------------------ storage and notices

    @Setting(path = "flush-seconds", desc = "Pillar XP is kept in memory and written in batches this often (and on shutdown).", min = 2, max = 300)
    public int flushSeconds = 10;

    @Setting(path = "week-time-zone", desc = "Time zone of the weekly pillar gain buckets (weeks start Monday 00:00).", restart = true)
    public String weekTimeZone = "Europe/Oslo";

    @Setting(path = "notify.level-up-chat", desc = "Tell online members in chat when a pillar levels up (each player can hide it in /settings).")
    public boolean notifyLevelUpChat = true;

    @Setting(path = "notify.title-every-levels", desc = "Also show a title to online members every this many pillar levels (0 = never).", min = 0, max = 1000)
    public int notifyTitleEvery = 10;

    @Setting(path = "notify.level-up-sound", desc = "Sound key (sounds.yml) for a pillar level-up.")
    public String levelUpSound = "notify";

    @Setting(path = "notify.milestone-sound", desc = "Sound key (sounds.yml) for a synergy milestone.")
    public String milestoneSound = "unlock";
}
