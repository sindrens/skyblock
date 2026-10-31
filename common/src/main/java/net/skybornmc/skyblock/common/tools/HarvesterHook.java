package net.skybornmc.skyblock.common.tools;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Hook for the hoe enchants (E6, ENCHANTS §6.4) into the harvester hoe (PHASE-7 M7.4). The tools module asks the
 * registered hook (if any) on every area swing; without one both answers are 0. Register it with
 * {@code core.services().register(moduleId, HarvesterHook.class, impl)}. Main thread, called per swing / per replanted
 * crop, so answers must be cheap (no database).
 */
public interface HarvesterHook {

    /** Extra rings around the harvester area (Wide Reap: +1 / +2). The tools module caps the area at its max size. */
    default int extraRings(Player player, ToolInfo hoe) {
        return 0;
    }

    /**
     * Growth stages a replanted crop starts ahead (Green Thumb). {@code maxAge} is the crop's last stage; the tools module
     * keeps the result below it (a replanted crop is never fully grown).
     */
    default int replantStages(Player player, ToolInfo hoe, Material crop, int maxAge) {
        return 0;
    }
}
