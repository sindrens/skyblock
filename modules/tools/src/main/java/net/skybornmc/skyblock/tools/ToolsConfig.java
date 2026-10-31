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

    @Setting(path = "xp.crop-factor", desc = "Hoe tool XP per ripe crop harvested with a custom hoe (with a hoe, no bone meal, on your "
            + "own island) = the crop tier's xp.tier-xp value times this. 1 = crops level a hoe like Depths blocks level a pickaxe. "
            + "Only while farming is on (crops.yml → enabled).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double cropXpFactor = 1.0;

    // ------------------------------------------------------------------ harvester hoe (PHASE-7 M7.4)

    @Setting(path = "harvester.enabled", desc = "Right-click a crop with a custom hoe on your own island to harvest the ripe crops around it "
            + "and replant them (PHASE-7 M7.4, owner V6). Only while farming is on (crops.yml → enabled).")
    public boolean harvesterEnabled = true;

    @Setting(path = "harvester.size-levels", desc = "Hoe levels that each add one ring to the harvest area (owner PD13: 1, 15, 30 = "
            + "3x3 from Lv 1, 5x5 at Lv 15, 7x7 at Lv 30).", level = Setting.Level.OWNER)
    public List<String> harvesterSizeLevels = List.of("1", "15", "30");

    @Setting(path = "harvester.max-size", desc = "Largest harvest area side, with enchant rings (Wide Reap) included (PD13: 9 = 9x9).",
            min = 1, max = 15, level = Setting.Level.OWNER)
    public int harvesterMaxSize = 9;

    @Setting(path = "harvester.cooldown-ms", desc = "Shortest time between two area harvests of one player (stops auto-clickers; a swing "
            + "breaks up to 81 blocks).", min = 0, max = 10_000)
    public int harvesterCooldownMs = 400;

    @Setting(path = "harvester.replant", desc = "Crops harvested at full age and replanted at age 0 from one of their own seeds "
            + "('crop_block=seed_item'). If the drops have no seed, one is taken from the inventory; with none the spot stays empty.")
    public List<String> harvesterReplant = List.of("wheat=wheat_seeds", "carrots=carrot", "potatoes=potato", "beetroots=beetroot_seeds",
            "nether_wart=nether_wart", "cocoa=cocoa_beans");

    @Setting(path = "harvester.columns", desc = "Column crops: the block above the bottom one is broken (2+ high), so the bottom block "
            + "stays and grows again.")
    public List<String> harvesterColumns = List.of("sugar_cane", "cactus");

    @Setting(path = "harvester.fruits", desc = "Fruit crops ('fruit_block=attached_stem_block'): only fruits an attached stem points at "
            + "are harvested (never placed decoration); the stem stays.")
    public List<String> harvesterFruits = List.of("melon=attached_melon_stem", "pumpkin=attached_pumpkin_stem");

    @Setting(path = "harvester.no-bonus-items", desc = "Drops that never get the hoe's extra crop drops (seeds).")
    public List<String> harvesterNoBonusItems = List.of("wheat_seeds", "beetroot_seeds", "melon_seeds", "pumpkin_seeds", "torchflower_seeds",
            "pitcher_pod");

    @Setting(path = "harvester.max-drop-multiplier", desc = "Most a hoe's extra crop drops can multiply one harvest (ENCHANTS: max 3 "
            + "drops per action).", min = 1, max = 10, level = Setting.Level.OWNER)
    public double harvesterMaxDropMultiplier = 3;

    // ------------------------------------------------------------------ stats

    @Setting(path = "speed.per-level-percent", desc = "Break speed bonus per tool level, in percent (attribute block_break_speed).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double speedPerLevel = 1.0;

    @Setting(path = "fortune.per-tier-percent", desc = "Extra drops per Forge tier above 1, in percent (ECONOMY_BALANCE: 5). Used by Depths drops (M4.9) and hoe crop drops (M7.4).", min = 0, max = 100, level = Setting.Level.OWNER)
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

    /** Dead-key rule (REVIEW3 §6): NPC places come from the applied builds now (npcs module); the old keys are deleted on load. */
    @Override
    public java.util.Map<String, String> removedKeys() {
        return java.util.Map.of("npc", "NPCs stand at the applied builds' points (npcs module), never read here");
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
