package net.skybornmc.skyblock.common.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Where offline-player arguments get their names from. Default: online players first, then players Bukkit knows,
 * cached for 60 seconds. The player-data module (M0.4) replaces it with a DB-backed source via {@link #use}.
 */
public final class OfflineNames {

    private static final long CACHE_MILLIS = 60_000;
    private static volatile Supplier<Collection<String>> source = OfflineNames::bukkitNames;
    private static volatile List<String> cached = List.of();
    private static volatile long cachedAt;

    private OfflineNames() {
    }

    /** Replaces the name source (must be fast and thread-safe: it runs on tab completion). */
    public static void use(Supplier<Collection<String>> newSource) {
        source = Objects.requireNonNull(newSource, "source");
    }

    public static Collection<String> names() {
        return source.get();
    }

    private static Collection<String> bukkitNames() {
        long now = System.currentTimeMillis();
        if (now - cachedAt > CACHE_MILLIS) {
            Set<String> names = new LinkedHashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                if (p.getName() != null) {
                    names.add(p.getName());
                }
            }
            cached = List.copyOf(names);
            cachedAt = now;
        }
        return cached;
    }
}
