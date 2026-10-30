package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Merge rules (WARDROBE §5, WD22/WD23): max level, no shared family, tier I-III, one merge per set; fee table. */
class MergeRulesTest {

    private static MergeRules.Side side(String set, int level) {
        return new MergeRules.Side(UUID.randomUUID(), Fixtures.def(set), level, false, true);
    }

    @Test
    void goodMergesAtMaxLevel() {
        assertEquals(Optional.empty(), MergeRules.check(side("deepforge", 50), side("coinmaster", 50), 50));
        assertEquals(Optional.empty(), MergeRules.check(side("dreadguard", 50), side("lorekeeper", 50), 50));
        assertEquals(Optional.empty(), MergeRules.check(side("stormkeel", 50), side("coinmaster", 50), 50));
    }

    @Test
    void bothMustBeMaxLevel() {
        assertEquals(Optional.of(MergeRules.Refusal.NOT_MAX), MergeRules.check(side("deepforge", 49), side("coinmaster", 50), 50));
        assertEquals(Optional.of(MergeRules.Refusal.NOT_MAX), MergeRules.check(side("deepforge", 50), side("coinmaster", 0), 50));
    }

    @Test
    void sharedFamilyIsRefused() {
        assertEquals(Optional.of(MergeRules.Refusal.SHARED_FAMILY), MergeRules.check(side("delver", 50), side("deepforge", 50), 50));
        assertEquals(Set.of(BonusFamily.CRYSTALS), MergeRules.shared(Fixtures.def("delver"), Fixtures.def("deepforge")));
    }

    @Test
    void tierFourCantMerge() {
        assertEquals(Optional.of(MergeRules.Refusal.TIER_IV), MergeRules.check(side("voidforged", 50), side("coinmaster", 50), 50));
        assertEquals(Optional.of(MergeRules.Refusal.TIER_IV), MergeRules.canStart(side("phoenix", 50), 50));
    }

    @Test
    void oneMergePerSetAndOtherRefusals() {
        MergeRules.Side merged = new MergeRules.Side(UUID.randomUUID(), Fixtures.def("deepforge"), 50, true, true);
        assertEquals(Optional.of(MergeRules.Refusal.ALREADY_MERGED), MergeRules.check(merged, side("lorekeeper", 50), 50));
        assertEquals(Optional.of(MergeRules.Refusal.ALREADY_MERGED), MergeRules.canStart(merged, 50));
        MergeRules.Side a = side("deepforge", 50);
        assertEquals(Optional.of(MergeRules.Refusal.SAME_ENTRY), MergeRules.check(a, a, 50));
        assertEquals(Optional.of(MergeRules.Refusal.SAME_SET), MergeRules.check(side("deepforge", 50), side("deepforge", 50), 50));
        MergeRules.Side gone = new MergeRules.Side(UUID.randomUUID(), Fixtures.def("coinmaster"), 50, false, false);
        assertEquals(Optional.of(MergeRules.Refusal.GONE), MergeRules.check(side("deepforge", 50), gone, 50));
        assertEquals(Optional.of(MergeRules.Refusal.NOT_MAX), MergeRules.canStart(side("deepforge", 10), 50));
        assertEquals(Optional.empty(), MergeRules.canStart(side("deepforge", 50), 50));
    }

    @Test
    void disabledSetsCantMerge() {
        SetDef d = Fixtures.def("coinmaster");
        SetDef off = new SetDef(d.id(), false, d.enabledFrom(), d.name(), d.shortName(), d.activity(), d.tier(), d.look(), d.source(),
                d.gate(), d.two(), d.four());
        assertEquals(Optional.of(MergeRules.Refusal.DISABLED), MergeRules.check(side("deepforge", 50),
                new MergeRules.Side(UUID.randomUUID(), off, 50, false, true), 50));
    }

    @Test
    void feeTable() {
        assertEquals(5_000, MergeRules.fee(1, 1, 5_000));
        assertEquals(10_000, MergeRules.fee(1, 2, 5_000));
        assertEquals(15_000, MergeRules.fee(2, 2, 5_000));
        assertEquals(15_000, MergeRules.fee(1, 3, 5_000));
        assertEquals(20_000, MergeRules.fee(2, 3, 5_000));
        assertEquals(25_000, MergeRules.fee(3, 3, 5_000));
        assertEquals(0, MergeRules.fee(3, 3, 0));
        assertEquals(Long.MAX_VALUE, MergeRules.fee(3, 3, Long.MAX_VALUE / 2));
    }

    @Test
    void refusalKeysAreMessageKeys() {
        assertEquals("not-max", MergeRules.Refusal.NOT_MAX.key());
        assertEquals("tier-iv", MergeRules.Refusal.TIER_IV.key());
    }
}
