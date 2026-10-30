package net.skybornmc.skyblock.common.achievements;

import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Achievements (F3), published by the achievements module: {@code core.services().optional(AchievementService.class)}.
 * Progress belongs to the player's <b>active profile</b>; reward titles go to the account (owner decision PR1). Other
 * menus (e.g. {@code /tags}) link to the achievements menu through {@link #open}. Main thread.
 */
public interface AchievementService {

    /** Opens the achievements menu of the player's active profile; Back returns to {@code parent} (may be null). */
    void open(Player player, Menu parent);

    /** Achievements the online player's active profile completed (every tier); 0 while loading or offline. */
    int completed(UUID player);

    /** Achievements that can be earned now (enabled ones). */
    int total();
}
