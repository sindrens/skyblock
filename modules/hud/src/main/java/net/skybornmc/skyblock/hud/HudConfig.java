package net.skybornmc.skyblock.hud;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Typed settings of {@code hud.yml}. Layouts and texts are in {@code messages/hud.yml}. */
public final class HudConfig extends ModuleConfig {

    @Setting(path = "scoreboard.enabled", desc = "Show the sidebar scoreboard (players can still turn it off for themselves).")
    public boolean scoreboardEnabled = true;

    @Setting(path = "scoreboard.update-ticks", desc = "How often each player's scoreboard refreshes (20 ticks = 1 second). Players are spread over these ticks.", min = 5, max = 200)
    public int updateTicks = 20;

    @Setting(path = "scoreboard.max-lines", desc = "Maximum lines (Bedrock shows at most 15).", min = 1, max = 15)
    public int maxLines = 15;

    @Setting(path = "scoreboard.disabled-worlds", desc = "Worlds without a sidebar.")
    public List<String> disabledWorlds = List.of();

    @Setting(path = "values.money", desc = "Placeholder key that gives the formatted money balance (shows a dash while no module provides it).")
    public String moneyKey = "balance_formatted";

    @Setting(path = "values.crystals", desc = "Placeholder key for the formatted island crystals balance.")
    public String crystalsKey = "crystals_formatted";

    @Setting(path = "values.credits", desc = "Placeholder key for the formatted credits (bought + earned, always exact; C1).")
    public String creditsKey = "credits_formatted";

    @Setting(path = "values.island-level", desc = "Placeholder key for the island level.")
    public String islandLevelKey = "island_level";

    @Setting(path = "values.island-rank", desc = "Placeholder key for the island's leaderboard position.")
    public String islandRankKey = "island_rank";

    @Setting(path = "values.objective", desc = "Placeholder key for the current objective (tutorial/quests).")
    public String objectiveKey = "objective";

    // ------------------------------------------------------------------ places (SB): which layout where

    @Setting(path = "places.island.worlds", desc = "Island worlds (only used when the islands module is off; otherwise its world counts).")
    public List<String> islandWorlds = List.of("islands");

    @Setting(path = "places.island.layout", desc = "Layout (messages/hud.yml scoreboard.<name>) on your own island and in the island world outside any island.")
    public String islandLayout = "island";

    @Setting(path = "places.island.visit-layout", desc = "Layout on someone else's island (coop players count as visitors). Falls back to the own-island layout.")
    public String islandVisitLayout = "island-visit";

    @Setting(path = "places.depths.worlds", desc = "Worlds of The Depths.")
    public List<String> depthsWorlds = List.of("depths");

    @Setting(path = "places.depths.layout", desc = "Layout in The Depths when no variant below matches.")
    public String depthsLayout = "depths";

    @Setting(path = "places.depths.variant-placeholder", desc = "Placeholder whose plain text picks a Depths variant (depths_tier is 'Mine · Tier 3' in a zone, 'The Gate' at the hub).")
    public String depthsVariantPlaceholder = "depths_tier";

    @Setting(path = "places.depths.variants", desc = "Depths variants as 'text=layout': the first whose text is in the placeholder value wins (ignoring case). Missing layouts fall back to places.depths.layout.")
    public List<String> depthsVariants = List.of("The Gate=depths-gate", "Forest=depths-forest", "Combat=depths-combat");

    @Setting(path = "places.spawn.worlds", desc = "Spawn worlds.")
    public List<String> spawnWorlds = List.of("spawn");

    @Setting(path = "places.spawn.layout", desc = "Layout at spawn.")
    public String spawnLayout = "spawn";

    @Setting(path = "places.warzone.worlds", desc = "Warzone worlds (placeholder layout until the warzone is built).")
    public List<String> warzoneWorlds = List.of("warzone");

    @Setting(path = "places.warzone.layout", desc = "Layout in the warzone.")
    public String warzoneLayout = "warzone";

    /**
     * Version 2: tokens were replaced by island crystals (owner decision O8).
     * Version 3 (card C1): gems were renamed credits ({@code values.gems} → {@code values.credits}).
     */
    @Override
    public int version() {
        return 3;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, String>> migrations() {
        return java.util.Map.of(2, java.util.Map.of("values.tokens", "values.crystals"),
                3, java.util.Map.of("values.gems", "values.credits"));
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(3, java.util.Map.of("values.credits", ValueChange.of("gems_formatted", "credits_formatted")));
    }

    @Override
    public String fileName() {
        return "hud.yml";
    }

    @Override
    public String header() {
        return """
                HUD: the sidebar scoreboard. "places" picks a layout per place (own island, visiting, The Depths
                and its zones, spawn, warzone); the layouts themselves (title + lines) are in messages/hud.yml.
                Values come from placeholders, so new features (economy, islands) show up without changing this module.
                Reload: /skyblock reload hud""";
    }
}
