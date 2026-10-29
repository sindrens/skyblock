package net.skybornmc.skyblock.depths.zones;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The default tier data used to generate the shipped {@code zones.yml} (WORLD.md §4.2–4.4, BUILDER_BRIEF §9–§11):
 * names, unlock levels, resource and depleted blocks, biomes and placeholder palettes. At runtime only zones.yml
 * counts; this table is the seed.
 */
public final class TierTable {

    /**
     * One tier's defaults.
     *
     * @param resource real block of the field (ore, log); {@code null} for combat
     * @param depleted what a player sees after breaking it
     * @param palette  placeholder blocks by role
     */
    public record Tier(Aspect aspect, int tier, String name, String resourceName, int level, String resource,
                       String depleted, String biome, Map<String, String> palette) {
    }

    private TierTable() {
    }

    /** Every default tier: Mine 1–12, Forest 1–11, Combat 1–10. */
    public static List<Tier> defaults() {
        List<Tier> t = new ArrayList<>();
        // ---------------------------------------------------------------- Mine (WORLD.md §4.2, BUILDER_BRIEF §9.3)
        mine(t, 1, "The Old Shaft", "Coal", 0, "coal_ore", "cobblestone", null,
                "stone", "cobblestone", "oak_log", "lantern", "stone");
        mine(t, 2, "Verdigris Cut", "Copper", 5, "copper_ore", "granite", null,
                "polished_granite", "stone", "waxed_weathered_cut_copper", "lantern", "granite");
        mine(t, 3, "The Ironworks", "Iron", 10, "iron_ore", "diorite", null,
                "polished_diorite", "stone_bricks", "iron_block", "lantern", "stone");
        mine(t, 4, "The Sunken Vault", "Gold", 15, "gold_ore", "smooth_sandstone", null,
                "cut_sandstone", "sandstone", "gold_block", "lantern", "sandstone");
        mine(t, 5, "The Red Depths", "Redstone", 20, "redstone_ore", "cobbled_deepslate", null,
                "polished_deepslate", "deepslate_bricks", "polished_blackstone", "redstone_lamp[lit=true]", "deepslate");
        mine(t, 6, "The Flooded Mine", "Lapis", 25, "lapis_ore", "deepslate_tiles", null,
                "dark_prismarine", "prismarine_bricks", "deepslate_tiles", "sea_lantern", "deepslate");
        mine(t, 7, "The Glittering Deep", "Diamond", 30, "deepslate_diamond_ore", "tuff", null,
                "calcite", "deepslate", "smooth_basalt", "soul_lantern", "deepslate");
        mine(t, 8, "The Lush Hollow", "Emerald", 35, "deepslate_emerald_ore", "mossy_cobblestone", "lush_caves",
                "moss_block", "mossy_stone_bricks", "deepslate_bricks", "lantern", "deepslate");
        mine(t, 9, "The Geode", "Amethyst", 42, "amethyst_block", "calcite", null,
                "smooth_basalt", "calcite", "polished_tuff", "pearlescent_froglight", "smooth_basalt");
        mine(t, 10, "The Ember Rift", "Quartz", 50, "nether_quartz_ore", "netherrack", "crimson_forest",
                "nether_bricks", "red_nether_bricks", "crimson_stem", "shroomlight", "blackstone");
        mine(t, 11, "The Forge Heart", "Netherite", 58, "ancient_debris", "blackstone", null,
                "polished_blackstone_bricks", "basalt", "gilded_blackstone", "lantern", "blackstone");
        mine(t, 12, "The Void Edge", "Voidstone", 66, "crying_obsidian", "end_stone", null,
                "end_stone_bricks", "purpur_block", "obsidian", "end_rod", "end_stone");
        // ---------------------------------------------------------------- Forest (WORLD.md §4.3, BUILDER_BRIEF §10.3)
        forest(t, 1, "Hearthwood Glade", "Oak", 0, "oak", "oak_leaves", "grass_block", "forest",
                "grass_block", "mossy_cobblestone", "lantern");
        forest(t, 2, "Silverbark Grove", "Birch", 5, "birch", "birch_leaves", "grass_block", "birch_forest",
                "grass_block", "stone_bricks", "lantern");
        forest(t, 3, "Frostpine Hollow", "Spruce", 10, "spruce", "spruce_leaves", "podzol", "snowy_taiga",
                "podzol", "cobblestone", "lantern");
        forest(t, 4, "Emerald Canopy", "Jungle", 15, "jungle", "jungle_leaves", "grass_block", "jungle",
                "grass_block", "mossy_stone_bricks", "ochre_froglight");
        forest(t, 5, "Sunset Savanna", "Acacia", 20, "acacia", "acacia_leaves", "coarse_dirt", "savanna",
                "coarse_dirt", "orange_terracotta", "lantern");
        forest(t, 6, "Duskwood", "Dark Oak", 25, "dark_oak", "dark_oak_leaves", "podzol", "dark_forest",
                "podzol", "mossy_cobblestone", "shroomlight");
        forest(t, 7, "Rootmire", "Mangrove", 30, "mangrove", "mangrove_leaves", "mud", "mangrove_swamp",
                "mangrove_planks", "mud_bricks", "verdant_froglight");
        forest(t, 8, "Petalfall", "Cherry", 35, "cherry", "cherry_leaves", "grass_block", "cherry_grove",
                "grass_block", "cherry_planks", "lantern");
        forest(t, 9, "The Pale Garden", "Pale Oak", 42, "pale_oak", "pale_oak_leaves", "pale_moss_block", "pale_garden",
                "pale_moss_block", "pale_oak_planks", "soul_lantern");
        forestFungus(t, 10, "Crimson Wilds", "Crimson", 50, "crimson", "nether_wart_block", "crimson_nylium",
                "crimson_forest", "netherrack", "nether_bricks", "shroomlight");
        forestFungus(t, 11, "Warped Reach", "Warped", 58, "warped", "warped_wart_block", "warped_nylium",
                "warped_forest", "soul_soil", "polished_blackstone_bricks", "soul_lantern");
        // ---------------------------------------------------------------- Combat (WORLD.md §4.4, BUILDER_BRIEF §11.3)
        // Unlock levels: M3.0 balance sheet proposal 0/6/12/18/24/30/38/46/55/66 (OPEN_QUESTIONS).
        combat(t, 1, "Graveyard", "Zombies", 0, "coarse_dirt", "podzol", "cracked_stone_bricks", "dark_oak_log", "lantern");
        combat(t, 2, "Bone Crypt", "Skeletons", 6, "stone_bricks", "polished_deepslate", "bone_block", "polished_basalt", "lantern");
        combat(t, 3, "Spider Den", "Spiders", 12, "tuff", "cobbled_deepslate", "deepslate", "tuff_bricks", "lantern");
        combat(t, 4, "Slime Bog", "Slimes", 18, "packed_mud", "mud_bricks", "mud_bricks", "mangrove_log", "verdant_froglight");
        combat(t, 5, "Sunken Ruins", "Drowned", 24, "prismarine_bricks", "dark_prismarine", "dark_prismarine", "prismarine", "sea_lantern");
        combat(t, 6, "Witch Hollow", "Witches", 30, "podzol", "moss_block", "dark_oak_planks", "spruce_log", "pearlescent_froglight");
        combat(t, 7, "Pillager Outpost", "Pillagers", 38, "cobblestone", "spruce_planks", "dark_oak_log", "dark_oak_planks", "lantern");
        combat(t, 8, "Blaze Forge", "Blazes", 46, "blackstone", "nether_bricks", "nether_bricks", "iron_block", "shroomlight");
        combat(t, 9, "Wither Keep", "Wither Skeletons", 55, "polished_blackstone", "blackstone", "polished_blackstone_bricks", "chiseled_polished_blackstone", "lantern");
        combat(t, 10, "Warlord's Citadel", "Ravagers", 66, "polished_blackstone_bricks", "red_nether_bricks", "red_nether_bricks", "gold_block", "lantern");
        return t;
    }

    /** Default palette of The Gate hub (BUILDER_BRIEF §8.3). */
    public static Map<String, String> hubPalette() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("floor", "tuff_bricks");
        p.put("inlay", "red_nether_bricks");
        p.put("base", "deepslate");
        p.put("wall", "polished_tuff");
        p.put("trim", "polished_blackstone");
        p.put("light", "lantern");
        p.put("pad", "chiseled_stone_bricks");
        p.put("fire", "campfire");
        p.put("gate-mine", "deepslate_tiles");
        p.put("gate-forest", "oak_log");
        p.put("gate-combat", "cracked_stone_bricks");
        return p;
    }

    /** Default palette of the world boss arena (BUILDER_BRIEF §11.4). */
    public static Map<String, String> arenaPalette() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("floor", "polished_blackstone_bricks");
        p.put("inlay", "red_nether_bricks");
        p.put("base", "blackstone");
        p.put("wall", "cracked_polished_blackstone_bricks");
        p.put("trim", "gilded_blackstone");
        p.put("light", "lantern");
        p.put("pad", "chiseled_polished_blackstone");
        p.put("fire", "campfire");
        return p;
    }

    private static void mine(List<Tier> t, int tier, String name, String res, int level, String ore, String depleted,
                             String biome, String floor, String wall, String trim, String light, String base) {
        Map<String, String> p = common(floor, base, wall, trim, light);
        t.add(new Tier(Aspect.MINE, tier, name, res, level, ore, depleted, biome, p));
    }

    private static void forest(List<Tier> t, int tier, String name, String res, int level, String wood, String leaves,
                               String ground, String biome, String floor, String wall, String light) {
        Map<String, String> p = common(floor, "dirt", wall, wood + "_log", light);
        p.put("ground", ground);
        p.put("leaves", leaves + "[persistent=true]");
        t.add(new Tier(Aspect.FOREST, tier, name, res, level, wood + "_log", "stripped_" + wood + "_log", biome, p));
    }

    private static void forestFungus(List<Tier> t, int tier, String name, String res, int level, String wood,
                                     String wart, String ground, String biome, String floor, String wall, String light) {
        Map<String, String> p = common(floor, "netherrack", wall, wood + "_stem", light);
        p.put("ground", ground);
        p.put("leaves", wart);
        t.add(new Tier(Aspect.FOREST, tier, name, res, level, wood + "_stem", "stripped_" + wood + "_stem", biome, p));
    }

    private static void combat(List<Tier> t, int tier, String name, String mobs, int level, String floor, String arena,
                               String wall, String trim, String light) {
        Map<String, String> p = common(floor, "stone", wall, trim, light);
        p.put("arena", arena);
        t.add(new Tier(Aspect.COMBAT, tier, name, mobs, level, null, null, null, p));
    }

    private static Map<String, String> common(String floor, String base, String wall, String trim, String light) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("floor", floor);
        p.put("base", base);
        p.put("wall", wall);
        p.put("trim", trim);
        p.put("light", light);
        p.put("pad", "chiseled_stone_bricks");
        return p;
    }
}
