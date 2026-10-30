package net.skybornmc.skyblock.common.visibility;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of {@code visibility.yml} (REVIEW3 D4): where the players' {@code Show players} choice applies, its
 * default, and how often positions are checked for entering or leaving those places. Reload: {@code /skyblock reload core}.
 */
public final class VisibilityConfig extends ModuleConfig {

    @Setting(path = "setting.worlds", desc = "Worlds where the players' 'Show players' choice applies everywhere (owner D4: the Depths). "
            + "Add 'spawn' to let players hide others at spawn too.")
    public List<String> settingWorlds = List.of("depths");

    @Setting(path = "setting.regions", desc = "Region ids from the applied builds' metadata (regions list) where the choice applies "
            + "(owner D4: the AFK pool). Players outside these places and worlds always see everyone.")
    public List<String> settingRegions = List.of("afk_pool");

    @Setting(path = "setting.default", desc = "Choice for players who never changed it: everyone, friends or nobody (owner D4: everyone).")
    public ShowMode defaultMode = ShowMode.EVERYONE;

    @Setting(path = "staff-always-shown", desc = "Staff with skyblock.staff.visible (helpers and up) stay visible to everyone while not vanished.")
    public boolean staffAlwaysShown = true;

    @Setting(path = "region-check-ticks", desc = "How often (ticks) each player's position is checked for entering or leaving a place above. "
            + "Only a change re-applies visibility. World changes apply at once.", min = 5, max = 200)
    public int regionCheckTicks = 20;

    @Override
    public String fileName() {
        return "visibility.yml";
    }

    @Override
    public String header() {
        return """
                Player visibility (REVIEW3 D4): one service decides who sees whom (vanish, the AFK pool, the players'
                'Show players' choice in /settings: everyone / friends + island / nobody). The choice applies only in the
                worlds and regions below; everywhere else everyone is visible. Staff that is not vanished is always visible.
                Reload: /skyblock reload core""";
    }
}
