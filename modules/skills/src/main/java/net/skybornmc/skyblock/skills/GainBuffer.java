package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rate limit of the action bar XP line (UI_STYLE §6.3: at most one message per 1.5 s). Gains in between are summed and
 * shown together at the next allowed moment. Main thread only; time is passed in (testable).
 */
final class GainBuffer {

    private static final class Pending {
        final Map<SkillType, Double> xp = new EnumMap<>(SkillType.class);
        long lastShown = Long.MIN_VALUE / 2;
    }

    private final Map<UUID, Pending> players = new HashMap<>();

    void add(UUID player, SkillType skill, double amount) {
        if (!(amount > 0)) {
            return;
        }
        players.computeIfAbsent(player, k -> new Pending()).xp.merge(skill, amount, Double::sum);
    }

    /**
     * The summed gains to show now, or an empty map if nothing is pending or the last line was less than
     * {@code intervalMs} ago. Showing resets the sums.
     */
    Map<SkillType, Double> poll(UUID player, long nowMs, long intervalMs) {
        Pending p = players.get(player);
        if (p == null || p.xp.isEmpty() || nowMs - p.lastShown < intervalMs) {
            return Map.of();
        }
        Map<SkillType, Double> out = Collections.unmodifiableMap(new EnumMap<>(p.xp));
        p.xp.clear();
        p.lastShown = nowMs;
        return out;
    }

    /** Players with gains waiting. */
    List<UUID> waiting() {
        List<UUID> out = new ArrayList<>();
        players.forEach((uuid, p) -> {
            if (!p.xp.isEmpty()) {
                out.add(uuid);
            }
        });
        return out;
    }

    void remove(UUID player) {
        players.remove(player);
    }

    void clear() {
        players.clear();
    }
}
