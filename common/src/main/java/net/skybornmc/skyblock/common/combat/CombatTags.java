package net.skybornmc.skyblock.common.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last moment each player was in combat (dealt or took damage from a player or a hostile mob). Pure logic, no
 * Bukkit, thread-safe; unit-tested. The listener in {@link DefaultCombatTagService} feeds it.
 */
public final class CombatTags {

    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    /** Marks a player as in combat at {@code now} (epoch millis). */
    public void tag(UUID player, long now) {
        if (player != null) {
            last.merge(player, now, Math::max);
        }
    }

    /** Last combat moment (epoch millis), or 0 if never (or forgotten). */
    public long last(UUID player) {
        Long t = player == null ? null : last.get(player);
        return t == null ? 0L : t;
    }

    /**
     * Milliseconds left of a tag that lasts {@code seconds}, or 0 if the player is not tagged. A clock that went back
     * never gives more than the full tag.
     */
    public long remaining(UUID player, long now, int seconds) {
        long t = last(player);
        if (t <= 0 || seconds <= 0) {
            return 0;
        }
        long length = seconds * 1000L;
        long left = t + length - now;
        return left <= 0 ? 0 : Math.min(left, length);
    }

    public boolean tagged(UUID player, long now, int seconds) {
        return remaining(player, now, seconds) > 0;
    }

    /** Forgets a player (quit). */
    public void forget(UUID player) {
        if (player != null) {
            last.remove(player);
        }
    }

    /** Drops tags older than {@code maxSeconds} (housekeeping). */
    public void prune(long now, int maxSeconds) {
        long cut = now - Math.max(1, maxSeconds) * 1000L;
        last.values().removeIf(t -> t < cut);
    }

    public int size() {
        return last.size();
    }
}
