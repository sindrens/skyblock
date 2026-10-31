package net.skybornmc.skyblock.depths.engine;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A Depths v2 tier palette (DV2-SPIKE): the slot blocks of some regions (one zone's ore/log fields) show each player
 * the block of their active tier. The real block in the world is {@link #base()}, the block of tier 1 (the softest),
 * so tier-1 players need no packets.
 *
 * @param id        palette id (the pilot zone id, e.g. {@code mine_t01})
 * @param regionIds engine regions whose {@code base} blocks are slots
 * @param base      the real slot block (tier 1's block)
 * @param tiers     tier 1 first
 */
public record TierPalette(String id, Set<String> regionIds, Material base, List<Tier> tiers) {

    /**
     * One tier of the palette.
     *
     * @param wingTier      tier number in the wing (drop tables, tool XP, quests: the same rewards as that tier's zone)
     * @param zoneId        the zone this tier's rewards and look come from (e.g. {@code mine_t03})
     * @param name          what the player sees ("Iron"), for the unlock title
     * @param requiredLevel skill level that opens the tier
     * @param resource      shown block, depleted look and respawn time of this tier
     * @param shown         the full block state sent to the player
     */
    public record Tier(int wingTier, String zoneId, String name, int requiredLevel, ResourceBlock resource, BlockData shown) {
        public Tier {
            Objects.requireNonNull(zoneId, "zoneId");
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(shown, "shown");
        }
    }

    public TierPalette {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(base, "base");
        regionIds = Set.copyOf(regionIds);
        tiers = List.copyOf(tiers);
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("A palette needs at least one tier");
        }
    }

    /** Tier by 1-based index, clamped into range. */
    public Tier tier(int index) {
        return tiers.get(Math.max(1, Math.min(index, tiers.size())) - 1);
    }

    public int size() {
        return tiers.size();
    }

    /** Required levels by tier (index 0 = tier 1), for {@link PaletteMath}. */
    public int[] requiredLevels() {
        int[] out = new int[tiers.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = tiers.get(i).requiredLevel();
        }
        return out;
    }
}
