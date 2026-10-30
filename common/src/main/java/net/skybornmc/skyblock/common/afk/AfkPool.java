package net.skybornmc.skyblock.common.afk;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * The AFK pool as seen by other modules, published by the afk module ({@code core.services().optional(AfkPool.class)}).
 * Playtime rewards (PHASE-10 M10.6, R6) count only active time outside the pool. Main thread.
 */
public interface AfkPool {

    /** The published service, if the AFK pool module runs. */
    static Optional<AfkPool> of(ServiceRegistry services) {
        return services.optional(AfkPool.class);
    }

    /** True while the player stands in the AFK pool (earning or not). */
    boolean inPool(Player player);
}
