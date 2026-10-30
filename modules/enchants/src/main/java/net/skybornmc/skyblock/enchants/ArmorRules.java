package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.ArmorSlots;
import net.skybornmc.skyblock.common.wardrobe.PieceEnchants;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Slot, conflict and level-gate rules for armor enchants on wardrobe set pieces (E5; ENCHANTS §2.1 EN12, §2.4 EN4),
 * pure and unit-tested. Same answers as {@link EnchantRules} for tools ({@link EnchantRules.Verdict}), with the set level
 * in place of the tool level: {@link EnchantRules.Verdict#needLevel} is a <b>set</b> level here.
 *
 * <p>Enchants live on the wardrobe row of an entry, never on loose vanilla armor; a piece needs no special item to be
 * enchanted (worn or not).</p>
 */
final class ArmorRules {

    /** What a slot of a piece shows. */
    enum SlotState { FILLED, EMPTY, LOCKED }

    private final ArmorSlots slots;
    private final boolean mythicSlotMythicOnly;

    ArmorRules(ArmorSlots slots, boolean mythicSlotMythicOnly) {
        this.slots = slots;
        this.mythicSlotMythicOnly = mythicSlotMythicOnly;
    }

    ArmorSlots slots() {
        return slots;
    }

    /** The enchant target of a piece. */
    static EnchantTarget target(ArmorPiece piece) {
        return switch (piece) {
            case HEAD -> EnchantTarget.HELMET;
            case CHEST -> EnchantTarget.CHESTPLATE;
            case LEGS -> EnchantTarget.LEGGINGS;
            case FEET -> EnchantTarget.BOOTS;
        };
    }

    static boolean fits(EnchantDef def, ArmorPiece piece) {
        return def.targets().contains(target(piece));
    }

    SlotState slotState(ArmorPiece piece, List<ToolPerk> enchants, int setLevel, int slot) {
        if (PieceEnchants.at(enchants, slot).isPresent()) {
            return SlotState.FILLED;
        }
        return slots.unlocked(piece, slot, setLevel) ? SlotState.EMPTY : SlotState.LOCKED;
    }

    /** Highest level of {@code def} a set at {@code setLevel} allows (EN4). */
    int gate(EnchantDef def, int setLevel) {
        return slots.gate(def.maxLevel(), setLevel);
    }

    /** Whether an enchant on a piece works at this set level (slot open, level within the gate, enchant on). */
    boolean working(ArmorPiece piece, ToolPerk perk, int setLevel, Function<String, Optional<EnchantDef>> lookup) {
        Optional<EnchantDef> def = lookup.apply(perk.id());
        return def.isPresent() && def.get().enabled() && fits(def.get(), piece)
                && slots.working(piece, perk, setLevel, def.get().maxLevel());
    }

    // ------------------------------------------------------------------ apply

    /** Whether a book of {@code def} at {@code level} may go into {@code slot} of a piece. */
    EnchantRules.Verdict canApply(ArmorPiece piece, List<ToolPerk> enchants, int setLevel, int slot, EnchantDef def, int level,
                                  Function<String, Optional<EnchantDef>> lookup) {
        if (!fits(def, piece)) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.WRONG_TOOL);
        }
        if (!def.enabled()) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.DISABLED);
        }
        if (PieceEnchants.find(enchants, def.id()).isPresent()) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.ALREADY_ON_TOOL);
        }
        for (ToolPerk perk : enchants) {
            Optional<EnchantDef> other = lookup.apply(perk.id());
            if (other.isPresent() && def.conflictsWith(other.get())) {
                return new EnchantRules.Verdict(EnchantRules.Problem.CONFLICT, perk.id(), 0);
            }
        }
        if (!slots.exists(piece, slot)) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.SLOT_LOCKED);
        }
        SlotState state = slotState(piece, enchants, setLevel, slot);
        if (state == SlotState.LOCKED) {
            return new EnchantRules.Verdict(EnchantRules.Problem.SLOT_LOCKED, null, slots.unlockLevel(slot));
        }
        if (state != SlotState.EMPTY) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.SLOT_USED);
        }
        boolean mythicSlot = ArmorSlots.mythic(slot);
        if (def.mythic() && !mythicSlot) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.PRESTIGE_ONLY);
        }
        if (!def.mythic() && mythicSlot && mythicSlotMythicOnly) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.MYTHIC_ONLY_SLOT);
        }
        if (def.slotCost() != 1) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.NEEDS_TWO_SLOTS);
        }
        if (level > def.maxLevel()) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.MAX_LEVEL);
        }
        if (level > gate(def, setLevel)) {
            return new EnchantRules.Verdict(EnchantRules.Problem.LEVEL_GATE, null, slots.requiredLevel(def.maxLevel(), level));
        }
        return EnchantRules.Verdict.OK;
    }

    /** The piece's enchants after applying (sorted by slot). */
    static List<ToolPerk> withApplied(List<ToolPerk> enchants, int slot, EnchantDef def, int level, long invested) {
        List<ToolPerk> out = new ArrayList<>(enchants);
        out.add(new ToolPerk(slot, def.id(), level, 1, invested));
        return PieceEnchants.sorted(out);
    }

    // ------------------------------------------------------------------ upgrade / combine

    /**
     * One level up with crystals. A dormant enchant whose slot is not open can't be upgraded (the gate would not let it
     * work anyway).
     */
    EnchantRules.Verdict canUpgrade(ArmorPiece piece, ToolPerk perk, int setLevel, EnchantDef def) {
        if (!def.enabled()) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.DISABLED);
        }
        if (!slots.unlocked(piece, perk.slot(), setLevel)) {
            return new EnchantRules.Verdict(EnchantRules.Problem.SLOT_LOCKED, null, slots.unlockLevel(perk.slot()));
        }
        int next = perk.level() + 1;
        if (next > def.maxLevel()) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.MAX_LEVEL);
        }
        if (next > gate(def, setLevel)) {
            return new EnchantRules.Verdict(EnchantRules.Problem.LEVEL_GATE, null, slots.requiredLevel(def.maxLevel(), next));
        }
        return EnchantRules.Verdict.OK;
    }

    /** A book of the same enchant and level onto the applied enchant (+1 level). */
    EnchantRules.Verdict canCombineOn(ArmorPiece piece, ToolPerk perk, int setLevel, EnchantDef def, String bookEnchant, int bookLevel) {
        if (!def.id().equals(bookEnchant) || perk.level() != bookLevel) {
            return EnchantRules.Verdict.of(EnchantRules.Problem.NOT_A_PAIR);
        }
        return canUpgrade(piece, perk, setLevel, def);
    }
}
