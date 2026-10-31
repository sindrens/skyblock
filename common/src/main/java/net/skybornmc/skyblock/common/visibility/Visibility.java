package net.skybornmc.skyblock.common.visibility;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Static access to {@link VisibilityService#knows} for code without a {@code Core} (command arguments). Until the core
 * installs the service it falls back to {@code Player#canSee}.
 */
public final class Visibility {

    private static volatile VisibilityService service;

    private Visibility() {
    }

    /** Set by the core on start (and {@code null} on stop). */
    public static void use(VisibilityService visibility) {
        service = visibility;
    }

    /** True if {@code target} exists for {@code sender} (console: always). Vanish only; rules and settings never hide a name. */
    public static boolean knows(CommandSender sender, Player target) {
        if (!(sender instanceof Player viewer) || viewer.equals(target)) {
            return true;
        }
        VisibilityService s = service;
        return s == null ? viewer.canSee(target) : s.knows(viewer, target);
    }
}
