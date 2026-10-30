package net.skybornmc.skyblock.common.wardrobe;

import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolSlots;

import java.util.List;

/**
 * Armor enchant slots on wardrobe set pieces (ENCHANTS §2.1 EN12, WARDROBE §3.3), pure and shared by the wardrobe (lore)
 * and the enchants module (rules, effects). Every piece has slot 1, slot 2 opens at {@link #secondSlotLevel}; the
 * chestplate also has the mythic slot ({@link #MYTHIC_SLOT}) from {@link #mythicSlotLevel}. Mythic enchants go only in
 * the mythic slot (EN2). The enchant level gate uses the set level like the tool level (EN4): at most
 * {@code ceil(max × setLevel / maxLevel)}, so a level 0 set (0× bonus) holds no working enchant yet.
 *
 * <p>An enchant stays on the piece when its slot is not open (yet) or its level is above the gate (after a merge resets
 * the level to 0): it is then <b>dormant</b> (gray in the lore, no effect) until the set level catches up (WARDROBE §5).</p>
 *
 * @param secondSlotLevel set level that opens slot 2 on every piece ({@code wardrobe.yml → enchants.second-slot-level})
 * @param mythicSlotLevel set level that opens the chestplate's mythic slot ({@code enchants.mythic-slot-level})
 * @param maxLevel        the highest set level (the gate is full there)
 */
public record ArmorSlots(int secondSlotLevel, int mythicSlotLevel, int maxLevel) {

    /** The chestplate's mythic slot number. */
    public static final int MYTHIC_SLOT = 3;

    /** Defaults (EN12): 2nd slot at 25, mythic slot at 50, max level 50. */
    public static final ArmorSlots DEFAULTS = new ArmorSlots(25, 50, 50);

    public ArmorSlots {
        secondSlotLevel = Math.max(0, secondSlotLevel);
        mythicSlotLevel = Math.max(0, mythicSlotLevel);
        maxLevel = Math.max(1, maxLevel);
    }

    /** The slot numbers a piece has: 1 and 2, plus the mythic slot on the chestplate. */
    public List<Integer> slots(ArmorPiece piece) {
        return piece == ArmorPiece.CHEST ? List.of(1, 2, MYTHIC_SLOT) : List.of(1, 2);
    }

    /** Whether a piece has this slot at all. */
    public boolean exists(ArmorPiece piece, int slot) {
        return slot == 1 || slot == 2 || (slot == MYTHIC_SLOT && piece == ArmorPiece.CHEST);
    }

    /** The set level that opens a slot (slot 1: 0). */
    public int unlockLevel(int slot) {
        return switch (slot) {
            case 1 -> 0;
            case 2 -> secondSlotLevel;
            default -> mythicSlotLevel;
        };
    }

    /** Whether a slot of a piece is open at a set level. */
    public boolean unlocked(ArmorPiece piece, int slot, int setLevel) {
        return exists(piece, slot) && setLevel >= unlockLevel(slot);
    }

    /** Normal (non-mythic) slots open at a set level (the number {@code WardrobeService#enchantSlots} reports). */
    public int openSlots(ArmorPiece piece, int setLevel) {
        return setLevel >= secondSlotLevel ? 2 : 1;
    }

    /** True for the mythic slot. */
    public static boolean mythic(int slot) {
        return slot == MYTHIC_SLOT;
    }

    /** EN4: the highest level of an enchant with {@code enchantMax} levels a set at {@code setLevel} allows (0 at level 0). */
    public int gate(int enchantMax, int setLevel) {
        return ToolSlots.maxEnchantLevel(enchantMax, Math.min(setLevel, maxLevel), maxLevel);
    }

    /** The lowest set level that allows {@code level} of an enchant with {@code enchantMax} levels (max level + 1 if never). */
    public int requiredLevel(int enchantMax, int level) {
        for (int l = 0; l <= maxLevel; l++) {
            if (gate(enchantMax, l) >= level) {
                return l;
            }
        }
        return maxLevel + 1;
    }

    /**
     * Whether an enchant on a piece works now: its slot is open and its level is within the gate. A dormant enchant stays
     * on the piece and shows gray. {@code enchantMax} 0 (unknown enchant) never works.
     */
    public boolean working(ArmorPiece piece, ToolPerk perk, int setLevel, int enchantMax) {
        return enchantMax > 0 && unlocked(piece, perk.slot(), setLevel) && perk.level() <= gate(enchantMax, setLevel);
    }
}
