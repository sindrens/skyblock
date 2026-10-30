package net.skybornmc.skyblock.common.tools;

import java.util.Objects;

/**
 * One filled perk slot of a custom tool: a custom enchant (Phase E, docs/plans/ENCHANTS.md) at a level.
 *
 * @param slot  1-based slot number: 1-4 unlock at the {@link ToolSlots#DEFAULT_SLOT_LEVELS}, {@link ToolSlots#PRESTIGE_SLOT}
 *              is the prestige slot. An enchant that costs two slots (ENCHANTS §2.2) is stored once, at its first slot.
 * @param id    enchant id ({@code [a-z0-9_]+}), e.g. {@code vein_rend}
 * @param level enchant level, 1 or more
 * @param size  slots it takes (1, or 2 for Dragonfire Blast / Wildfire)
 * @param invested crystals put into this enchant on this tool (apply fee + upgrades + combine fees); a shatter refunds
 *              a share of it (ENCHANTS §4.5). 0 for perks from before Phase E.
 */
public record ToolPerk(int slot, String id, int level, int size, long invested) {

    public ToolPerk {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-z0-9_]{1,48}")) {
            throw new IllegalArgumentException("Invalid perk id '" + id + "'");
        }
        if (slot < 1 || slot > ToolSlots.MAX_SLOTS) {
            throw new IllegalArgumentException("Perk slot must be 1-" + ToolSlots.MAX_SLOTS + ": " + slot);
        }
        if (level < 1) {
            throw new IllegalArgumentException("Perk level must be at least 1");
        }
        if (size < 1 || size > 2) {
            throw new IllegalArgumentException("Perk size must be 1 or 2");
        }
        if (invested < 0) {
            throw new IllegalArgumentException("Invested crystals can't be negative");
        }
    }

    public ToolPerk(int slot, String id, int level, int size) {
        this(slot, id, level, size, 0);
    }

    public ToolPerk(int slot, String id, int level) {
        this(slot, id, level, 1, 0);
    }

    /** The same perk at another level with more crystals invested. */
    public ToolPerk upgraded(int newLevel, long addedCrystals) {
        return new ToolPerk(slot, id, newLevel, size, Math.addExact(invested, Math.max(0, addedCrystals)));
    }

    /** The last slot this perk covers. */
    public int lastSlot() {
        return slot + size - 1;
    }
}
