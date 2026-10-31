package net.skybornmc.skyblock.common.quest;

import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Quests (PHASE-10 M10.7) as seen by other modules: published by the quests module,
 * {@code core.services().optional(QuestService.class)}. The {@code /menu → You} Quests button is shown only while it is
 * published. Main thread.
 */
public interface QuestService {

    static Optional<QuestService> of(ServiceRegistry services) {
        return services.optional(QuestService.class);
    }

    /** Opens {@code /quests} for the player's active profile; Back returns to {@code parent} (may be null). */
    void open(Player player, Menu parent);

    /** Daily quests the player's active profile finished today (0 while loading). */
    int dailyDone(Player player);
}
