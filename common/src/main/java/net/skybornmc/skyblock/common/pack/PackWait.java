package net.skybornmc.skyblock.common.pack;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

/**
 * Lets join code (welcome, tutorial camera) wait until the icon pack prompt is answered (at most
 * {@code join.wait-seconds}), so the prompt never sits over a cinematic. Without the pack module it runs at once.
 * Modules use this instead of depending on the pack module.
 */
public final class PackWait {

    private PackWait() {
    }

    public static void run(ServiceRegistry services, Player player, Runnable action) {
        PackService packs = services == null ? null : services.optional(PackService.class).orElse(null);
        run(packs, player, action);
    }

    /** Same with the service already looked up ({@code null} = no pack module: run now). Pure, unit-testable. */
    public static void run(PackService packs, Player player, Runnable action) {
        if (packs == null) {
            action.run();
        } else {
            packs.afterAnswer(player, action);
        }
    }
}
