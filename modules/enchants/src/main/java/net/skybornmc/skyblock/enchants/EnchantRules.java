package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolSlots;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Slot, conflict and level-gate rules (ENCHANTS §2), pure and unit-tested. Every check returns a {@link Verdict} so the
 * menus can show exactly why something does not fit ({@code ✘ Needs tool Lv 34}) instead of hiding it.
 */
final class EnchantRules {

    /** Why something is not possible; {@link #OK} when it is. */
    enum Problem {
        OK,
        /** The enchant is turned off (EN19). */
        DISABLED,
        /** The enchant does not go on this tool type. */
        WRONG_TOOL,
        /** The tool already has this enchant. */
        ALREADY_ON_TOOL,
        /** Same conflict group as an enchant on the tool ({@link Verdict#other}). */
        CONFLICT,
        /** The slot is not unlocked yet ({@link Verdict#needLevel} = level that opens it, -1 = prestige). */
        SLOT_LOCKED,
        /** The slot is filled. */
        SLOT_USED,
        /** A 2-slot enchant needs this slot and the next one free (and not the prestige slot). */
        NEEDS_TWO_SLOTS,
        /** Mythics go in the prestige slot only (EN2). */
        PRESTIGE_ONLY,
        /** The level is above the tool-level gate (EN4); {@link Verdict#needLevel} = tool level needed. */
        LEVEL_GATE,
        /** Already at the highest level. */
        MAX_LEVEL,
        /** Combine: not the same enchant and level. */
        NOT_A_PAIR,
        /** E5: the chestplate's mythic slot takes Mythic enchants only. */
        MYTHIC_ONLY_SLOT
    }

    /**
     * @param problem   what is wrong
     * @param other     the conflicting enchant id (CONFLICT), or null
     * @param needLevel tool level needed (LEVEL_GATE, SLOT_LOCKED), or 0
     */
    record Verdict(Problem problem, String other, int needLevel) {
        static final Verdict OK = new Verdict(Problem.OK, null, 0);

        static Verdict of(Problem p) {
            return new Verdict(p, null, 0);
        }

        boolean ok() {
            return problem == Problem.OK;
        }
    }

    /** What a perk slot of a tool shows. */
    enum SlotState { FILLED, COVERED, EMPTY, LOCKED }

    private final List<Integer> slotLevels;
    private final int maxToolLevel;
    private final boolean twoSlotInPrestige;

    EnchantRules(List<Integer> slotLevels, int maxToolLevel, boolean twoSlotInPrestige) {
        this.slotLevels = List.copyOf(slotLevels);
        this.maxToolLevel = maxToolLevel;
        this.twoSlotInPrestige = twoSlotInPrestige;
    }

    int maxToolLevel() {
        return maxToolLevel;
    }

    // ------------------------------------------------------------------ slots

    /** The perk covering a 1-based slot (a 2-slot perk covers its slot and the next). */
    static Optional<ToolPerk> perkAt(ToolInfo tool, int slot) {
        return tool.perks().stream().filter(p -> slot >= p.slot() && slot <= p.lastSlot()).findFirst();
    }

    SlotState slotState(ToolInfo tool, int slot) {
        Optional<ToolPerk> perk = perkAt(tool, slot);
        if (perk.isPresent()) {
            return perk.get().slot() == slot ? SlotState.FILLED : SlotState.COVERED;
        }
        return ToolSlots.isUnlocked(slot, tool.level(), tool.prestige(), slotLevels) ? SlotState.EMPTY : SlotState.LOCKED;
    }

    /** The level that opens a slot, or -1 for the prestige slot. */
    int unlockLevel(int slot) {
        return slot == ToolSlots.PRESTIGE_SLOT ? -1 : ToolSlots.unlockLevel(slot, slotLevels);
    }

    // ------------------------------------------------------------------ gate (EN4)

    /** Highest level of {@code def} this tool allows. */
    int gate(ToolInfo tool, EnchantDef def) {
        int effective = ToolSlots.effectiveLevel(tool.level(), tool.prestige(), maxToolLevel);
        return ToolSlots.maxEnchantLevel(def.maxLevel(), effective, maxToolLevel);
    }

    /** The lowest tool level that allows {@code level} of an enchant with {@code enchantMax} (max level + 1 if never). */
    int requiredToolLevel(int enchantMax, int level) {
        for (int l = 1; l <= maxToolLevel; l++) {
            if (ToolSlots.maxEnchantLevel(enchantMax, l, maxToolLevel) >= level) {
                return l;
            }
        }
        return maxToolLevel + 1;
    }

    // ------------------------------------------------------------------ apply (§4.2)

    /** Whether a book of {@code def} at {@code level} may go into {@code slot} of {@code tool}. */
    Verdict canApply(ToolInfo tool, int slot, EnchantDef def, int level, Function<String, Optional<EnchantDef>> lookup) {
        if (!def.fits(tool.kind())) {
            return Verdict.of(Problem.WRONG_TOOL);
        }
        if (!def.enabled()) {
            return Verdict.of(Problem.DISABLED);
        }
        if (tool.perk(def.id()).isPresent()) {
            return Verdict.of(Problem.ALREADY_ON_TOOL);
        }
        for (ToolPerk perk : tool.perks()) {
            Optional<EnchantDef> other = lookup.apply(perk.id());
            if (other.isPresent() && def.conflictsWith(other.get())) {
                return new Verdict(Problem.CONFLICT, perk.id(), 0);
            }
        }
        SlotState state = slotState(tool, slot);
        if (state == SlotState.LOCKED) {
            return new Verdict(Problem.SLOT_LOCKED, null, unlockLevel(slot));
        }
        if (state != SlotState.EMPTY) {
            return Verdict.of(Problem.SLOT_USED);
        }
        boolean prestigeSlot = slot == ToolSlots.PRESTIGE_SLOT;
        if (def.mythic() && !prestigeSlot) {
            return Verdict.of(Problem.PRESTIGE_ONLY);
        }
        if (def.slotCost() == 2) {
            int next = slot + 1;
            boolean nextIsPrestige = next == ToolSlots.PRESTIGE_SLOT;
            if (prestigeSlot || next > ToolSlots.MAX_SLOTS || (nextIsPrestige && !twoSlotInPrestige)
                    || slotState(tool, next) != SlotState.EMPTY) {
                return Verdict.of(Problem.NEEDS_TWO_SLOTS);
            }
        }
        if (level > def.maxLevel()) {
            return Verdict.of(Problem.MAX_LEVEL);
        }
        if (level > gate(tool, def)) {
            return new Verdict(Problem.LEVEL_GATE, null, requiredToolLevel(def.maxLevel(), level));
        }
        return Verdict.OK;
    }

    /** The perk list after applying (sorted by slot). */
    static List<ToolPerk> withApplied(List<ToolPerk> perks, int slot, EnchantDef def, int level, long invested) {
        List<ToolPerk> out = new ArrayList<>(perks);
        out.add(new ToolPerk(slot, def.id(), level, def.slotCost(), invested));
        out.sort(Comparator.comparingInt(ToolPerk::slot));
        return out;
    }

    /** The perk list with one enchant replaced by {@code replacement} (same slot), or removed when it is null. */
    static List<ToolPerk> replaced(List<ToolPerk> perks, String enchantId, ToolPerk replacement) {
        List<ToolPerk> out = new ArrayList<>();
        for (ToolPerk p : perks) {
            if (p.id().equals(enchantId)) {
                if (replacement != null) {
                    out.add(replacement);
                }
            } else {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingInt(ToolPerk::slot));
        return out;
    }

    // ------------------------------------------------------------------ upgrade (§4.3) and combine (§4.4)

    /** One level up with crystals. */
    Verdict canUpgrade(ToolInfo tool, ToolPerk perk, EnchantDef def) {
        if (!def.enabled()) {
            return Verdict.of(Problem.DISABLED);
        }
        int next = perk.level() + 1;
        if (next > def.maxLevel()) {
            return Verdict.of(Problem.MAX_LEVEL);
        }
        if (next > gate(tool, def)) {
            return new Verdict(Problem.LEVEL_GATE, null, requiredToolLevel(def.maxLevel(), next));
        }
        return Verdict.OK;
    }

    /** A book onto the applied enchant: same enchant, same level, then like an upgrade. */
    Verdict canCombineOnTool(ToolInfo tool, ToolPerk perk, EnchantDef def, String bookEnchant, int bookLevel) {
        if (!def.id().equals(bookEnchant) || perk.level() != bookLevel) {
            return Verdict.of(Problem.NOT_A_PAIR);
        }
        return canUpgrade(tool, perk, def);
    }

    /** Two books: same enchant, same level, below max. */
    static Verdict canCombineBooks(EnchantDef def, String enchantA, int levelA, String enchantB, int levelB) {
        if (!def.id().equals(enchantA) || !enchantA.equals(enchantB) || levelA != levelB) {
            return Verdict.of(Problem.NOT_A_PAIR);
        }
        if (!def.enabled()) {
            return Verdict.of(Problem.DISABLED);
        }
        if (levelA + 1 > def.maxLevel()) {
            return Verdict.of(Problem.MAX_LEVEL);
        }
        return Verdict.OK;
    }
}
