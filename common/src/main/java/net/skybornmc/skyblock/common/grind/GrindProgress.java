package net.skybornmc.skyblock.common.grind;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Owner 6 Nov 2026 (plans/TOOLS_V3.md): what the personal grind enchants may do to the {@code /farm} and {@code /mine}
 * progress. Published by the essentials module (the grind areas); the island tools use it.
 *
 * <p>Blast/Explode break blocks around the player's block "drops only" ({@link #dropsOnly(Player, Runnable)}): the
 * areas then count no unlock progress, roll no money bonus and fire no {@link GrindBreakEvent} (no skill XP, quests or
 * contracts), so an area enchant can never multiply everything else. Main thread only.</p>
 */
public interface GrindProgress {

    /** Quick Study: the player's break counts {@code blocks} extra times toward the next crop ({@code farm}) or ore ({@code mine}). */
    void addProgress(Player player, String area, long blocks);

    /** Geode / Harvest Basket: the items of every crop ({@code farm}) or ore ({@code mine}) the player has unlocked. */
    java.util.List<org.bukkit.Material> unlockedDrops(Player player, String area);

    /** Owner 7 Nov 2026: the next crop / ore / water and how far ("Carrots: 1,240 / 3,000"); empty at the last one. */
    default String nextUnlock(Player player, String area) {
        return "";
    }

    /** Players whose breaks right now are drops only (main thread). */
    Set<UUID> DROPS_ONLY = new HashSet<>();

    /** Runs {@code breaks} (the player's own {@code breakBlock} calls) as drops-only breaks. Main thread. */
    static void dropsOnly(Player player, Runnable breaks) {
        boolean added = DROPS_ONLY.add(player.getUniqueId());
        try {
            breaks.run();
        } finally {
            if (added) {
                DROPS_ONLY.remove(player.getUniqueId());
            }
        }
    }

    /** True while the player's break is a drops-only break. */
    static boolean dropsOnly(Player player) {
        return player != null && DROPS_ONLY.contains(player.getUniqueId());
    }

    /** Adds Quick Study progress when the grind areas run. */
    static void add(ServiceRegistry services, Player player, String area, long blocks) {
        if (services != null && blocks > 0) {
            services.optional(GrindProgress.class).ifPresent(g -> g.addProgress(player, area, blocks));
        }
    }
}
