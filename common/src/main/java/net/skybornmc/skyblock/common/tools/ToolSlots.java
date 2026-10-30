package net.skybornmc.skyblock.common.tools;

import java.util.List;

/**
 * Perk slot rules shared by the tools module (M4.2) and the enchants module (Phase E, ENCHANTS §2.1 and §2.4).
 *
 * <ul>
 *   <li>Slots 1-4 unlock at tool levels 5 / 15 / 30 / 45 (owner decision; {@code tools.yml → perk-slots.levels}).</li>
 *   <li>Slot 5 is the prestige slot: it opens with the tool's first prestige. A prestiged tool keeps its four level slots
 *       (it counts as max level for slots and enchant gates) even though its level starts again at 1.</li>
 *   <li>Enchant gate: an enchant can be at most level {@code ceil(max × toolLevel / maxToolLevel)}.</li>
 * </ul>
 */
public final class ToolSlots {

    /** Tool levels that unlock slots 1-4 (default; the live values come from {@link ToolService#slotLevels()}). */
    public static final List<Integer> DEFAULT_SLOT_LEVELS = List.of(5, 15, 30, 45);
    /** The prestige slot number. */
    public static final int PRESTIGE_SLOT = 5;
    /** Highest slot number a tool can have. */
    public static final int MAX_SLOTS = 5;
    /** Default max tool level. */
    public static final int DEFAULT_MAX_LEVEL = 50;

    private ToolSlots() {
    }

    /**
     * Unlocked perk slots.
     *
     * @param level      current tool level
     * @param prestige   times the tool prestiged (0 = never)
     * @param slotLevels levels that unlock the level slots, ascending
     */
    public static int unlocked(int level, int prestige, List<Integer> slotLevels) {
        if (prestige > 0) {
            return Math.min(MAX_SLOTS, slotLevels.size() + 1);
        }
        int count = 0;
        for (int needed : slotLevels) {
            if (level >= needed) {
                count++;
            }
        }
        return Math.min(MAX_SLOTS, count);
    }

    /** Whether a 1-based slot is open on this tool. */
    public static boolean isUnlocked(int slot, int level, int prestige, List<Integer> slotLevels) {
        return slot >= 1 && slot <= unlocked(level, prestige, slotLevels);
    }

    /** Level that opens a 1-based level slot, or -1 for the prestige slot (or a slot that doesn't exist). */
    public static int unlockLevel(int slot, List<Integer> slotLevels) {
        return slot >= 1 && slot <= slotLevels.size() ? slotLevels.get(slot - 1) : -1;
    }

    /** The next level that opens a slot above {@code level}, or -1 when all level slots are open. */
    public static int nextUnlock(int level, List<Integer> slotLevels) {
        for (int needed : slotLevels) {
            if (needed > level) {
                return needed;
            }
        }
        return -1;
    }

    /** The level enchant gates use: a prestiged tool counts as max level (ENCHANTS §2.4). */
    public static int effectiveLevel(int level, int prestige, int maxLevel) {
        return prestige > 0 ? maxLevel : Math.min(level, maxLevel);
    }

    /** ENCHANTS §2.4: the highest enchant level allowed for an enchant with {@code enchantMax} on this tool. */
    public static int maxEnchantLevel(int enchantMax, int effectiveLevel, int maxToolLevel) {
        if (enchantMax <= 0 || effectiveLevel <= 0 || maxToolLevel <= 0) {
            return 0;
        }
        int lvl = Math.min(effectiveLevel, maxToolLevel);
        return (int) Math.min(enchantMax, Math.ceil((double) enchantMax * lvl / maxToolLevel - 1e-9));
    }
}
