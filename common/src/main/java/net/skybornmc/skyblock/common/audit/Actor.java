package net.skybornmc.skyblock.common.audit;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/**
 * Who did something: a player (uuid + name at that time), the console, or the system (timers, plugins).
 *
 * @param uuid player uuid, {@code null} for console/system
 * @param name player name, {@code "CONSOLE"} or {@code "SYSTEM"} (or a named system part, e.g. {@code "SYSTEM:interest"})
 */
public record Actor(UUID uuid, String name) {

    public static final Actor CONSOLE = new Actor(null, "CONSOLE");
    public static final Actor SYSTEM = new Actor(null, "SYSTEM");

    public Actor {
        Objects.requireNonNull(name, "name");
        if (name.length() > 32) {
            name = name.substring(0, 32);
        }
    }

    public static Actor player(Player player) {
        return new Actor(player.getUniqueId(), player.getName());
    }

    public static Actor player(UUID uuid, String name) {
        return new Actor(Objects.requireNonNull(uuid, "uuid"), name == null ? uuid.toString().substring(0, 8) : name);
    }

    /** A player sender becomes a player actor; anything else (console, command block, RCON) the console. */
    public static Actor of(CommandSender sender) {
        return sender instanceof Player p ? player(p) : CONSOLE;
    }

    /** A system part, e.g. {@code Actor.system("interest")} → {@code SYSTEM:interest}. */
    public static Actor system(String part) {
        return new Actor(null, "SYSTEM:" + part);
    }

    public boolean isPlayer() {
        return uuid != null;
    }
}
