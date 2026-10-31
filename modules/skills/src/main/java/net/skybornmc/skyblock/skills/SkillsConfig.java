package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of {@code skills.yml}. The {@code xp} and {@code unlocks} sections of the same file (XP per block, log
 * and mob; unlock levels) are read by {@link SkillTables}.
 */
public final class SkillsConfig extends ModuleConfig {

    // ------------------------------------------------------------------ curve (ECONOMY_BALANCE §1.2 / §3.4)

    @Setting(path = "curve.base", desc = "XP from level 0 to 1. Each next level costs more (see the growth settings).", min = 1, max = 1_000_000, level = Setting.Level.OWNER)
    public double curveBase = 600;

    @Setting(path = "curve.early-growth", desc = "Cost growth per level up to curve.break-level (1.2 = each level costs 20% more).", min = 1.0, max = 3.0, level = Setting.Level.OWNER)
    public double curveEarlyGrowth = 1.2;

    @Setting(path = "curve.late-growth", desc = "Cost growth per level after curve.break-level (flatter, because late tiers are further apart).", min = 1.0, max = 3.0, level = Setting.Level.OWNER)
    public double curveLateGrowth = 1.13;

    @Setting(path = "curve.break-level", desc = "Level where the early growth switches to the late growth.", min = 0, max = 500, level = Setting.Level.OWNER)
    public int curveBreakLevel = 35;

    @Setting(path = "curve.end-break-level", desc = "Level where the late growth switches to the end growth (66 = the last tier unlock).", min = 0, max = 500, level = Setting.Level.OWNER)
    public int curveEndBreakLevel = 66;

    @Setting(path = "curve.end-growth", desc = "Cost growth per level after curve.end-break-level (1.02 = each level costs 2% more).", min = 1.0, max = 3.0, level = Setting.Level.OWNER)
    public double curveEndGrowth = 1.02;

    @Setting(path = "curve.max-level", desc = "Highest skill level (owner: 150). XP keeps counting after it, for leaderboards.", min = 1, max = 500, level = Setting.Level.OWNER)
    public int maxLevel = 150;

    // ------------------------------------------------------------------ skills

    @Setting(path = "available", desc = "Skills that give XP now. The others show as 'Coming soon' in /skills (farming and fishing come in Phase 7).")
    public List<String> available = List.of("mining", "foraging", "combat");

    @Setting(path = "combat.spawner-mobs-give-xp", desc = "Mobs from spawners give Combat XP when killed (off: no XP farms from island spawners).")
    public boolean spawnerMobsGiveXp = false;

    @Setting(path = "combat.only-in-depths", desc = "R3-24: Combat XP only from kills inside Depths combat zones (no Combat XP from island, spawner or stacked mobs).")
    public boolean combatOnlyInDepths = true;

    @Setting(path = "combat.depths-sources", desc = "R3-24: XP sources that still give Combat XP with combat.only-in-depths (depths_mob = Depths combat zone kills and mini-bosses).")
    public List<String> combatDepthsSources = List.of("depths_mob");

    // ------------------------------------------------------------------ feedback

    @Setting(path = "action-bar.enabled", desc = "Show XP gains in the action bar ('+12 Mining XP (68%)'). Players can turn it off in /settings.")
    public boolean actionBarEnabled = true;

    @Setting(path = "action-bar.interval-ms", desc = "Minimum time between two XP lines; gains in between are added up (UI style: 1500).", min = 250, max = 10_000)
    public int actionBarIntervalMs = 1500;

    @Setting(path = "level-up.title", desc = "Show a title on level-up.")
    public boolean levelUpTitle = true;

    @Setting(path = "level-up.chat", desc = "Send a chat line on level-up (with the next unlock).")
    public boolean levelUpChat = true;

    @Setting(path = "level-up.sound", desc = "Sound name from sounds.yml played on level-up (empty = none).")
    public String levelUpSound = "level-up";

    @Setting(path = "menu.bar-length", desc = "Segments of the progress bar in /skills.", min = 5, max = 20)
    public int barLength = 10;

    // ------------------------------------------------------------------ storage

    @Setting(path = "storage.save-interval-seconds", desc = "Changed XP of online players is saved in one batch this often (and always on quit and shutdown).", min = 5, max = 600)
    public int saveIntervalSeconds = 60;

    @Setting(path = "storage.season-id", desc = "Season the XP belongs to (skills reset each season). Stays 0 until the season system exists.", min = 0, max = 100_000, restart = true, level = Setting.Level.OWNER)
    public int seasonId = 0;

    @Override
    public String fileName() {
        return "skills.yml";
    }

    @Override
    public String header() {
        return """
                Skills (M4.5): XP and levels for Mining, Foraging and Combat (Farming, Fishing in Phase 7). Perks come in Phase 6.
                xp:      XP per action (block id / log id / entity id) per skill.
                unlocks: levels with an unlock per skill; the text is in messages/skills.yml → unlock.<skill>.<level>.
                Texts are in messages/skills.yml. Reload: /skyblock reload skills""";
    }
}
