package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.achievements.IslandBoost;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/**
 * Typed settings of the achievements module ({@code achievements-settings.yml}). The achievements themselves are in
 * {@code achievements.yml} (read by {@link AchievementRegistry}); that file holds only the two catalog sections.
 */
public final class AchievementsConfig extends ModuleConfig {

    // ------------------------------------------------------------------ tracking

    @Setting(path = "tracking.gauge-poll-seconds", desc = "How often skill levels, sets, tools and balances of online players are read for achievements.", min = 5, max = 600)
    public int gaugePollSeconds = 30;

    @Setting(path = "tracking.island-poll-seconds", desc = "How often island stats (blocks mined, logs, crops, placed, mobs, visitors) are read for achievements. One read per island.", min = 30, max = 3600)
    public int islandPollSeconds = 300;

    @Setting(path = "tracking.playtime", desc = "Count minutes played per profile for the playtime achievements.")
    public boolean playtime = true;

    @Setting(path = "tracking.hotbar-tools", desc = "Look at the hotbar for the best custom tool level and prestige (every gauge poll).")
    public boolean hotbarTools = true;

    // ------------------------------------------------------------------ storage

    @Setting(path = "storage.save-interval-seconds", desc = "Changed progress of online players is saved in one batch this often (and always on quit, profile switch and shutdown).", min = 5, max = 600)
    public int saveIntervalSeconds = 60;

    // ------------------------------------------------------------------ unlock toast

    @Setting(path = "toast.title", desc = "Show a screen title when an achievement tier unlocks.")
    public boolean toastTitle = true;

    @Setting(path = "toast.chat", desc = "Send a chat line when an achievement tier unlocks.")
    public boolean toastChat = true;

    @Setting(path = "toast.sound", desc = "Sound name from sounds.yml played on an unlock (empty = none).")
    public String toastSound = "unlock";

    @Setting(path = "toast.max-lines", desc = "Most unlock lines shown at once (e.g. the first join after achievements came out); the rest is one '+N more' line.", min = 1, max = 20)
    public int toastMaxLines = 3;

    // ------------------------------------------------------------------ menu and titles

    @Setting(path = "menu.bar-length", desc = "Segments of the progress bars in the achievements menu.", min = 5, max = 20)
    public int barLength = 10;

    @Setting(path = "menu.hidden-icon", desc = "Item shown for secret achievements that are not unlocked yet.")
    public String hiddenIcon = "gray_dye";

    @Setting(path = "titles.add-missing", desc = "Add reward titles that have a display in achievements.yml to tags-titles.yml when they are missing there (existing titles are never changed).")
    public boolean addMissingTitles = true;

    // ------------------------------------------------------------------ boost points (AC2, RANKS_TITLES_ACHIEVEMENTS §C2)

    @Setting(path = "points.tier-1", desc = "Boost points for finishing tier I of an achievement (owner AC2: 1/2/3/5/8).", min = 0, max = 100, level = Setting.Level.OWNER)
    public int pointsTier1 = 1;

    @Setting(path = "points.tier-2", desc = "Boost points for tier II.", min = 0, max = 100, level = Setting.Level.OWNER)
    public int pointsTier2 = 2;

    @Setting(path = "points.tier-3", desc = "Boost points for tier III.", min = 0, max = 100, level = Setting.Level.OWNER)
    public int pointsTier3 = 3;

    @Setting(path = "points.tier-4", desc = "Boost points for tier IV.", min = 0, max = 100, level = Setting.Level.OWNER)
    public int pointsTier4 = 5;

    @Setting(path = "points.tier-5", desc = "Boost points for tier V and every tier above it.", min = 0, max = 100, level = Setting.Level.OWNER)
    public int pointsTier5 = 8;

    // ------------------------------------------------------------------ personal boosts (AC2, §C3; never sell or money)

    @Setting(path = "boosts.enabled", desc = "Personal boost points give their boosts (off = points are still shown, boosts are 0).", level = Setting.Level.OWNER)
    public boolean boostsEnabled = true;

    @Setting(path = "boosts.skill-xp.step-percent", desc = "Skill XP per Skills point (percent).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double skillXpStep = 0.3;

    @Setting(path = "boosts.skill-xp.cap-percent", desc = "Most skill XP from achievements (percent; the skill XP family cap applies on top).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double skillXpCap = 10;

    @Setting(path = "boosts.tool-xp.step-percent", desc = "Tool XP per Gear point (percent).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double toolXpStep = 0.4;

    @Setting(path = "boosts.tool-xp.cap-percent", desc = "Most tool XP from achievements (percent).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double toolXpCap = 10;

    @Setting(path = "boosts.mob-damage.step-percent", desc = "Damage to mobs per Combat point (percent; never in PvP).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double mobDamageStep = 0.3;

    @Setting(path = "boosts.mob-damage.cap-percent", desc = "Most mob damage from achievements (percent).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double mobDamageCap = 8;

    @Setting(path = "boosts.rare-finds.step-percent", desc = "Rare finds per Exploring point (percent).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double rareFindsStep = 0.3;

    @Setting(path = "boosts.rare-finds.cap-percent", desc = "Most rare finds from achievements (percent).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double rareFindsCap = 8;

    @Setting(path = "boosts.crystals.step-percent", desc = "Crystals per Island (own stats) point (percent).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double crystalsStep = 0.25;

    @Setting(path = "boosts.crystals.cap-percent", desc = "Most crystals from achievements (percent).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double crystalsCap = 6;

    @Setting(path = "boosts.ah-slots.first-at-points", desc = "Economy points for +1 auction slot (0 = off).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public int ahSlotFirst = 12;

    @Setting(path = "boosts.ah-slots.second-at-points", desc = "Economy points for +2 auction slots (0 = off; the most achievements give).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public int ahSlotSecond = 30;

    // ------------------------------------------------------------------ island achievements (AC3, §C4)

    @Setting(path = "island.enabled", desc = "Island achievements: shared progress of the island's members, island boosts and island level points.", level = Setting.Level.OWNER)
    public boolean islandEnabled = true;

    @Setting(path = "island.title-min-days", desc = "Island titles go only to members who have been on the island this many days (owner AC7: 3). Members who reach it later get the title then.", min = 0, max = 60, level = Setting.Level.OWNER)
    public double islandTitleMinDays = 3;

    @Setting(path = "island.team-active-minutes", desc = "Team Flame: minutes a member must play on one day to count as active that day.", min = 1, max = 600, level = Setting.Level.OWNER)
    public int teamActiveMinutes = 30;

    @Setting(path = "island.time-zone", desc = "Time zone of the Team Flame day.", restart = true)
    public String islandTimeZone = "Europe/Oslo";

    @Setting(path = "island.level-points-per-point", desc = "Island level points per island achievement point (source 'achievements'; progression.yml caps it at caps.achievements-percent of the total).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double levelPointsPerPoint = 1;

    @Setting(path = "island.boosts.production.step-percent", desc = "Island production per island point (percent).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double productionStep = 0.25;

    @Setting(path = "island.boosts.production.cap-percent", desc = "Most island production from island achievements (percent, owner AC6: 8).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double productionCap = 8;

    @Setting(path = "island.boosts.crystals.step-percent", desc = "Island crystals per island point (percent).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double islandCrystalsStep = 0.25;

    @Setting(path = "island.boosts.crystals.cap-percent", desc = "Most island crystals from island achievements (percent, AC6: 6).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double islandCrystalsCap = 6;

    @Setting(path = "island.boosts.island-xp.step-percent", desc = "Island (pillar) XP per island point (percent).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double islandXpStep = 0.2;

    @Setting(path = "island.boosts.island-xp.cap-percent", desc = "Most island XP from island achievements (percent, AC6: 5; the ISLAND_XP family cap in pillars.yml applies on top).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double islandXpCap = 5;

    @Setting(path = "island.boosts.fuel.step-percent", desc = "Fuel efficiency per island point (percent).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double fuelStep = 0.5;

    @Setting(path = "island.boosts.fuel.cap-percent", desc = "Most fuel efficiency from island achievements (percent, AC6: 10).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double fuelCap = 10;

    // ------------------------------------------------------------------ helpers

    /** Points per tier, tier I first. */
    int[] perTier() {
        return new int[]{pointsTier1, pointsTier2, pointsTier3, pointsTier4, pointsTier5};
    }

    double step(PersonalBoost boost) {
        return switch (boost) {
            case SKILL_XP -> skillXpStep;
            case TOOL_XP -> toolXpStep;
            case MOB_DAMAGE -> mobDamageStep;
            case RARE_FINDS -> rareFindsStep;
            case CRYSTALS -> crystalsStep;
            case AH_SLOTS, NONE -> 0;
        };
    }

    double cap(PersonalBoost boost) {
        return switch (boost) {
            case SKILL_XP -> skillXpCap;
            case TOOL_XP -> toolXpCap;
            case MOB_DAMAGE -> mobDamageCap;
            case RARE_FINDS -> rareFindsCap;
            case CRYSTALS -> crystalsCap;
            case AH_SLOTS -> ahSlotSecond > 0 ? 2 : ahSlotFirst > 0 ? 1 : 0;
            case NONE -> 0;
        };
    }

    double islandStep(IslandBoost boost) {
        return switch (boost) {
            case PRODUCTION -> productionStep;
            case CRYSTALS -> islandCrystalsStep;
            case ISLAND_XP -> islandXpStep;
            case FUEL -> fuelStep;
        };
    }

    double islandCap(IslandBoost boost) {
        return switch (boost) {
            case PRODUCTION -> productionCap;
            case CRYSTALS -> islandCrystalsCap;
            case ISLAND_XP -> islandXpCap;
            case FUEL -> fuelCap;
        };
    }

    @Override
    public String fileName() {
        return "achievements-settings.yml";
    }

    @Override
    public String header() {
        return """
                Achievements (F3): settings. The achievements and categories are in achievements.yml; texts in messages/achievements.yml.
                Progress is per profile; reward titles are account-wide (owner decision PR1). Island achievements are per island.
                Every tier gives boost points (points.*); personal boosts go into the bonus families with their own caps
                (boosts.*, never sell or money), island points give island boosts and island level points (island.*).
                Reload: /skyblock reload achievements""";
    }
}
