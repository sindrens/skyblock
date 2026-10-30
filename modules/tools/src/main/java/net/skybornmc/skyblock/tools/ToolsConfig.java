package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.tools.ToolKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed settings of {@code tools.yml}. The catalog sections of the same file (tiers, abilities, blueprints, skins,
 * island-xp, smelt) are read by {@link ToolCatalog}.
 */
public final class ToolsConfig extends ModuleConfig {

    // ------------------------------------------------------------------ levels (ECONOMY_BALANCE §3.5)

    @Setting(path = "curve.base", desc = "XP from tool level 1 to 2. Each next level costs curve.growth times more.", min = 1, max = 1_000_000, level = Setting.Level.OWNER)
    public double curveBase = 400;

    @Setting(path = "curve.growth", desc = "Cost growth per tool level (owner-approved 1.21: slot 1 ~1 h, slot 4 ~week 4 for an active player).", min = 1.0, max = 3.0, level = Setting.Level.OWNER)
    public double curveGrowth = 1.21;

    @Setting(path = "curve.max-level", desc = "Highest tool level (prestige opens there).", min = 2, max = 200, level = Setting.Level.OWNER)
    public int maxLevel = 50;

    @Setting(path = "perk-slots.levels", desc = "Tool levels that open perk slots 1-4 (owner: 5, 15, 30, 45). The prestige gives slot 5.", level = Setting.Level.OWNER)
    public List<String> slotLevels = List.of("5", "15", "30", "45");

    @Setting(path = "prestige.enabled", desc = "Tools at the max level can prestige (level 1 again, keeps perks and stats, +1 perk slot, a star).")
    public boolean prestigeEnabled = true;

    @Setting(path = "prestige.per-season", desc = "Prestiges per tool per season (owner: 1).", min = 0, max = 10, level = Setting.Level.OWNER)
    public int prestigePerSeason = 1;

    // ------------------------------------------------------------------ XP

    @Setting(path = "xp.tier-xp", desc = "Tool XP per block/log in Depths tier 1, 2, 3 ... (WORLD.md: same as skill XP).", level = Setting.Level.OWNER)
    public List<String> tierXp = List.of("1", "1.5", "2.5", "4", "6", "9", "13", "19", "28", "40", "58", "85");

    @Setting(path = "xp.kill-multiplier", desc = "A kill gives this many times the tier XP of its combat tier (kills take longer than blocks).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double killMultiplier = 5;

    @Setting(path = "xp.island-factor", desc = "Share of the XP for blocks and kills outside The Depths (ENCHANTS: islands count 25%).", min = 0, max = 1, level = Setting.Level.OWNER)
    public double islandFactor = 0.25;

    @Setting(path = "xp.spawner-mobs", desc = "Mobs from spawners give tool XP and kills when killed.")
    public boolean spawnerMobsXp = false;

    @Setting(path = "xp.tier-bonus-percent", desc = "Extra tool XP per Forge tier above 1 (0 = XP depends only on the block, like the balance sheet).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double tierBonusPercent = 0;

    // ------------------------------------------------------------------ stats

    @Setting(path = "speed.per-level-percent", desc = "Break speed bonus per tool level, in percent (attribute block_break_speed).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double speedPerLevel = 1.0;

    @Setting(path = "fortune.per-tier-percent", desc = "Extra drops per Forge tier above 1, in percent (ECONOMY_BALANCE: 5). Used by Depths drops (M4.9).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double fortunePerTier = 5;

    @Setting(path = "fortune.per-level-percent", desc = "Extra drops per tool level, in percent (0 = levels give speed only, like the balance sheet).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double fortunePerLevel = 0;

    @Setting(path = "abilities.lifesteal-max-heal", desc = "Most health (half hearts) one hit can heal with the lifesteal ability.", min = 0, max = 20)
    public double lifestealMaxHeal = 2;

    // ------------------------------------------------------------------ starter kit, forge, menu

    @Setting(path = "starter-kit.cooldown-hours", desc = "Hours between two starter kit claims (owner: 24). Only missing pieces are given.", min = 0, max = 720)
    public int starterCooldownHours = 24;

    @Setting(path = "starter-kit.kinds", desc = "Tool types in the starter kit (tier 1 of each).")
    public List<String> starterKinds = List.of("pickaxe", "axe", "hoe", "sword", "rod");

    @Setting(path = "starter-kit.give-on-first-join", desc = "Give the starter kit automatically on a player's first join (off: the tutorial/Quartermaster does it).")
    public boolean starterOnFirstJoin = false;

    @Setting(path = "npc.quartermaster", desc = "Where the Quartermaster NPC will stand: world,x,y,z,yaw,pitch (NPCs come later; empty = not placed).")
    public String quartermasterLocation = "";

    @Setting(path = "npc.blacksmith", desc = "Where the Blacksmith NPC (opens /forge) will stand: world,x,y,z,yaw,pitch (NPCs come later).")
    public String blacksmithLocation = "";

    @Setting(path = "menu.shift-right-click", desc = "Shift + right-click with a custom tool opens its tool menu (Bedrock players can also use /tool).")
    public boolean shiftRightClick = true;

    @Setting(path = "death.keep-tools", desc = "Custom tools stay in the inventory on death (REVIEW B7).")
    public boolean keepOnDeath = true;

    @Setting(path = "blueprints.drops-enabled", desc = "Blueprints drop in The Depths with the chances in the blueprints section.")
    public boolean blueprintDrops = true;

    // ------------------------------------------------------------------ feedback

    @Setting(path = "render.interval-ms", desc = "Minimum time between two lore refreshes of a tool while it gains XP (level-ups refresh at once).", min = 250, max = 60_000)
    public int renderIntervalMs = 2000;

    @Setting(path = "sounds.level-up", desc = "Sound from sounds.yml on a tool level-up (empty = none).")
    public String soundLevelUp = "level-up";

    @Setting(path = "sounds.forge", desc = "Sound from sounds.yml when a tool is forged or crafted.")
    public String soundForge = "unlock";

    @Setting(path = "sounds.prestige", desc = "Sound from sounds.yml on a prestige.")
    public String soundPrestige = "unlock";

    // ------------------------------------------------------------------ storage

    @Setting(path = "storage.save-interval-seconds", desc = "Changed tools are saved to the database in one batch this often (and on quit and shutdown).", min = 5, max = 600)
    public int saveIntervalSeconds = 30;

    @Setting(path = "storage.season-id", desc = "Current season (prestige limit and blueprint finds are per season). Stays 0 until the season system exists.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int seasonId = 0;

    // ------------------------------------------------------------------ parsed views

    /** Slot levels as ascending ints; broken entries are dropped, an empty result falls back to 5/15/30/45. */
    List<Integer> slotLevels() {
        List<Integer> out = new ArrayList<>();
        for (String s : slotLevels) {
            try {
                int v = Integer.parseInt(s.trim());
                if (v >= 1 && !out.contains(v)) {
                    out.add(v);
                }
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        out.sort(Integer::compare);
        while (out.size() > 4) {
            out.removeLast();
        }
        return out.isEmpty() ? net.skybornmc.skyblock.common.tools.ToolSlots.DEFAULT_SLOT_LEVELS : List.copyOf(out);
    }

    /** XP per action in a Depths tier (1-based); tiers past the list use the last value. */
    double tierXp(int tier) {
        List<Double> values = new ArrayList<>();
        for (String s : tierXp) {
            try {
                double v = Double.parseDouble(s.trim());
                if (v >= 0) {
                    values.add(v);
                }
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        if (values.isEmpty()) {
            return 1;
        }
        return values.get(Math.max(0, Math.min(values.size() - 1, tier - 1)));
    }

    List<ToolKind> starterKinds() {
        List<ToolKind> out = new ArrayList<>();
        for (String s : starterKinds) {
            ToolKind.byId(s).filter(k -> !out.contains(k)).ifPresent(out::add);
        }
        return out;
    }

    @Override
    public String fileName() {
        return "tools.yml";
    }

    @Override
    public String header() {
        return """
                Tools (M4.2): starter kit, the Forge, living tools (levels 1-50, perk slots, prestige), blueprints, skins.
                Catalog sections below the settings: tiers, abilities, blueprints, skins, island-xp, smelt
                (missing sections are filled from the defaults). Texts: messages/tools.yml. Reload: /skyblock reload tools""";
    }
}
