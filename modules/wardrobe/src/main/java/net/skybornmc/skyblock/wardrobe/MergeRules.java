package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.PieceEnchants;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Merging rules (WARDROBE §5, WD22/WD23, owner SE1 + DECISIONS 2026-10-30: merging two sets makes one set with both
 * sets' bonuses and resets the set level to 0). Pure and unit-tested; the menu (which partners are clickable and why
 * not) and the database transaction (re-checked on the locked rows) both ask here.
 */
final class MergeRules {

    /** Why two entries can't be merged; {@link #key()} is the message key under {@code merge.why.}. */
    enum Refusal {
        /** The same entry twice. */
        SAME_ENTRY,
        /** Two entries of the same set. */
        SAME_SET,
        /** An entry is not active (merged away, removed) or not this profile's. */
        GONE,
        /** An entry is a merged set already (max 2 sets in one). */
        ALREADY_MERGED,
        /** A tier IV set (already covers 3+ families). */
        TIER_IV,
        /** {@code enabled: false} in sets.yml. */
        DISABLED,
        /** Not at the max level yet. */
        NOT_MAX,
        /** Both sets feed a common bonus family. */
        SHARED_FAMILY;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** One side of a merge: the entry's state as far as the rules care. */
    record Side(UUID entry, SetDef def, int level, boolean merged, boolean active) {
    }

    private MergeRules() {
    }

    /** The first rule that refuses the pair, or empty when they may be merged. */
    static Optional<Refusal> check(Side a, Side b, int maxLevel) {
        if (a.entry().equals(b.entry())) {
            return Optional.of(Refusal.SAME_ENTRY);
        }
        if (!a.active() || !b.active()) {
            return Optional.of(Refusal.GONE);
        }
        if (a.merged() || b.merged()) {
            return Optional.of(Refusal.ALREADY_MERGED);
        }
        if (a.def().id().equals(b.def().id())) {
            return Optional.of(Refusal.SAME_SET);
        }
        if (!a.def().mergeable() || !b.def().mergeable()) {
            return Optional.of(Refusal.TIER_IV);
        }
        if (!a.def().enabled() || !b.def().enabled()) {
            return Optional.of(Refusal.DISABLED);
        }
        if (a.level() < maxLevel || b.level() < maxLevel) {
            return Optional.of(Refusal.NOT_MAX);
        }
        if (!shared(a.def(), b.def()).isEmpty()) {
            return Optional.of(Refusal.SHARED_FAMILY);
        }
        return Optional.empty();
    }

    /** Whether an entry can start a merge at all (before a partner is picked). */
    static Optional<Refusal> canStart(Side a, int maxLevel) {
        if (!a.active()) {
            return Optional.of(Refusal.GONE);
        }
        if (a.merged()) {
            return Optional.of(Refusal.ALREADY_MERGED);
        }
        if (!a.def().mergeable()) {
            return Optional.of(Refusal.TIER_IV);
        }
        if (!a.def().enabled()) {
            return Optional.of(Refusal.DISABLED);
        }
        if (a.level() < maxLevel) {
            return Optional.of(Refusal.NOT_MAX);
        }
        return Optional.empty();
    }

    /** Bonus families both sets feed. */
    static Set<BonusFamily> shared(SetDef a, SetDef b) {
        Set<BonusFamily> out = EnumSet.noneOf(BonusFamily.class);
        out.addAll(a.families());
        out.retainAll(b.families());
        return out;
    }

    /** One armor enchant that a merge returns as a book (E5). */
    record Returned(ArmorPiece piece, String enchant, int level) {
    }

    /**
     * E5 (WARDROBE §5.1): the merged pieces keep the look entry's enchants; every enchant of the other entry comes back as
     * one book at its level (pieces in armor order, slots in order). {@code maxLevel} clamps a level above the enchant's
     * current max (a lowered max in enchants.yml); unknown enchants (maxLevel 0) are returned too, so the caller refuses
     * the merge instead of losing them.
     */
    static List<Returned> returnedBooks(Map<ArmorPiece, List<ToolPerk>> other, java.util.function.ToIntFunction<String> maxLevel) {
        List<Returned> out = new ArrayList<>();
        for (ArmorPiece piece : ArmorPiece.values()) {
            for (ToolPerk p : PieceEnchants.sorted(other.getOrDefault(piece, List.of()))) {
                int max = maxLevel.applyAsInt(p.id());
                out.add(new Returned(piece, p.id(), max > 0 ? Math.min(p.level(), max) : p.level()));
            }
        }
        return out;
    }

    /** Same enchants on the same pieces (pieces without enchants count as absent). */
    static boolean sameEnchants(Map<ArmorPiece, List<ToolPerk>> a, Map<ArmorPiece, List<ToolPerk>> b) {
        for (ArmorPiece piece : ArmorPiece.values()) {
            List<ToolPerk> x = PieceEnchants.sorted(a.getOrDefault(piece, List.of()));
            List<ToolPerk> y = PieceEnchants.sorted(b.getOrDefault(piece, List.of()));
            if (!x.equals(y)) {
                return false;
            }
        }
        return true;
    }

    /** Fee (WD23): {@code perStep × (tier A + tier B − 1)}; never negative. */
    static long fee(int tierA, int tierB, long perStep) {
        if (perStep <= 0) {
            return 0;
        }
        long steps = Math.max(1, (long) tierA + tierB - 1);
        try {
            return Math.multiplyExact(perStep, steps);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
