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

    @Setting(path = "values.gems", desc = "Placeholder key for the formatted gem balance.")
    public String gemsKey = "gems_formatted";

    @Setting(path = "values.island-level", desc = "Placeholder key for the island level.")
    public String islandLevelKey = "island_level";

    @Setting(path = "values.island-rank", desc = "Placeholder key for the island's leaderboard position.")
    public String islandRankKey = "island_rank";

    @Setting(path = "values.objective", desc = "Placeholder key for the current objective (tutorial/quests).")
    public String objectiveKey = "objective";

    /** Version 2: tokens were replaced by island crystals (owner decision O8). */
    @Override
    public int version() {
        return 2;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, String>> migrations() {
        return java.util.Map.of(2, java.util.Map.of("values.tokens", "values.crystals"));
    }

    @Override
    public String fileName() {
        return "hud.yml";
    }

    @Override
    public String header() {
        return """
                HUD: the sidebar scoreboard. Lines and layouts per world are in messages/hud.yml.
                Values come from placeholders, so new features (economy, islands) show up without changing this module.
                Reload: /skyblock reload hud""";
    }
}
