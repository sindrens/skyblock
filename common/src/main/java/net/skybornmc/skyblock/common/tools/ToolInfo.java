package net.skybornmc.skyblock.common.tools;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of one custom tool (M4.2), read from the item by {@link ToolService#read}.
 *
 * @param id           unique tool id (= the item's unique id; also the {@code tools} table key)
 * @param kind         pickaxe, axe, sword, hoe or rod
 * @param tier         Forge tier (1 = starter kit)
 * @param level        tool level, 1 to {@link ToolService#maxLevel()}
 * @param xp           XP since level 1 of the current prestige cycle
 * @param prestige     times prestiged (adds the prestige slot and a star)
 * @param slots        unlocked perk slots (see {@link ToolSlots#unlocked})
 * @param perks        filled perk slots, ordered by slot
 * @param skin         applied skin id, or null
 * @param ability      blueprint ability id (special tools), or null
 * @param starter      starter kit piece (not listable on the auction house)
 * @param stats        lifetime counters: {@code blocks}, {@code logs}, {@code kills}, {@code xp}, {@code forged}
 */
public record ToolInfo(UUID id, ToolKind kind, int tier, int level, double xp, int prestige, int slots,
                       List<ToolPerk> perks, String skin, String ability, boolean starter, Map<String, Long> stats) {

    public ToolInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        perks = List.copyOf(perks);
        stats = Map.copyOf(stats);
    }

    /** Slots already used by perks (a 2-slot enchant counts twice). */
    public int usedSlots() {
        return perks.stream().mapToInt(ToolPerk::size).sum();
    }

    public int freeSlots() {
        return Math.max(0, slots - usedSlots());
    }

    public boolean prestiged() {
        return prestige > 0;
    }

    public Optional<ToolPerk> perk(String perkId) {
        return perks.stream().filter(p -> p.id().equals(perkId)).findFirst();
    }

    public long stat(String key) {
        return stats.getOrDefault(key, 0L);
    }
}
