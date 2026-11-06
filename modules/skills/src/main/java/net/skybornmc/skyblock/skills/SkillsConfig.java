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
    public double curveBase = 6; // owner 3 Nov 2026: Mining 5 (Depths tier 2) after about 50 blocks

    @Setting(path = "curve.early-growth", desc = "Cost growth per level up to curve.break-level (1.2 = each level costs 20% more).", min = 1.0, max = 3.0, level = Setting.Level.OWNER)
    public double curveEarlyGrowth = 1.24;

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

    @Setting(path = "available", desc = "Skills that give XP now; the others are hidden in /skills. Farming also opens by itself while island farming is on (crops.yml → enabled, PHASE-7 M7.3).")
    public List<String> available = List.of("mining", "foraging", "combat", "farming", "fishing"); // 7 Nov 2026: /farm and /fish train them

    @Setting(path = "combat.spawner-mobs-give-xp", desc = "Mobs from spawners give Combat XP when killed (off: no XP farms from island spawners).")
    public boolean spawnerMobsGiveXp = false;

    @Setting(path = "combat.only-in-depths", desc = "R3-24: Combat XP only from kills inside Depths combat zones (no Combat XP from island, spawner or stacked mobs).")
    public boolean combatOnlyInDepths = true;

    @Setting(path = "combat.depths-sources", desc = "R3-24: XP sources that still give Combat XP with combat.only-in-depths (depths_mob = Depths combat zone kills and mini-bosses).")
    public List<String> combatDepthsSources = List.of("depths_mob");

    @Setting(path = "grind.farm-xp", desc = "Farming XP per crop harvested at /farm (only while the Farming skill is open). Mining XP at /mine uses the xp.mining table (the ore you saw).", min = 0, max = 1000)
    public double grindFarmXp = 1.0;

    @Setting(path = "grind.fish-xp", desc = "Owner 6 Nov 2026: Fishing XP per catch at /fish (a catch takes much longer than a crop).", min = 0, max = 10_000)
    public double grindFishXp = 8.0;

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

    /** Version 2 (owner 2 Nov 2026: "Depths requirements are super slow"): a cheaper start (600 to 300) and flatter early levels (1.2 to 1.15); level 35 needs about a sixth of the XP. Owner-edited values stay. */
    @Override
    public int version() {
        return 4;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        // v3 (owner 3 Nov 2026 "depths mine tier 2 after 50 blocks, way less to progress"): level 5 at ~48 XP (was ~2,000),
        // level 35 at ~46k XP (was ~264k); the late growth is unchanged.
        return java.util.Map.of(2, java.util.Map.of("curve.base", ValueChange.of(600, 300), "curve.early-growth", ValueChange.of(1.2, 1.15)),
                3, java.util.Map.of("curve.base", ValueChange.of(300, 6), "curve.early-growth", ValueChange.of(1.15, 1.24)),
                4, java.util.Map.of("available", ValueChange.of(List.of("mining", "foraging", "combat"), new SkillsConfig().available)));
    }

    @Override
    public String fileName() {
        return "skills.yml";
    }

    @Override
    public String header() {
        return """
                Skills (M4.5): XP and levels for Mining, Foraging and Combat; Farming while island farming is on (crops.yml). Perks come in Phase 6.
                xp:      XP per action (block id / log id / entity id) per skill.
                unlocks: levels with an unlock per skill; the text is in messages/skills.yml → unlock.<skill>.<level>.
                Texts are in messages/skills.yml. Reload: /skyblock reload skills""";
    }
}
