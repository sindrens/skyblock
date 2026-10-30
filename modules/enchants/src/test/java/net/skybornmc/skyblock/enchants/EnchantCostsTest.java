package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.item.Rarity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every row of ENCHANTS §5 (EN14), the apply fee (EN10), combine fee (EN11) and shatter refund (EN13). */
class EnchantCostsTest {

    private final EnchantCosts costs = EnchantCosts.defaults();

    /** A made-up enchant with a rarity and max level (costs only depend on those). */
    private static EnchantDef fake(Rarity rarity, int max) {
        return new EnchantDef("x", rarity, max, Set.of(EnchantTarget.PICKAXE), 1, List.of(), Set.of(), 1, null, null, Map.of(),
                Map.of(), 0, 1, true);
    }

    private List<Long> levels(Rarity rarity, int max) {
        EnchantDef d = fake(rarity, max);
        List<Long> out = new ArrayList<>();
        for (int n = 1; n <= max; n++) {
            out.add(costs.levelCost(d, n));
        }
        return out;
    }

    @Test
    void tableRows() {
        assertEquals(List.of(50L, 162L, 945L, 3_061L, 9_918L), levels(Rarity.COMMON, 5));
        assertEquals(List.of(50L, 292L, 1_701L, 9_918L), levels(Rarity.COMMON, 4));
        assertEquals(List.of(945L), levels(Rarity.COMMON, 1));
        assertEquals(List.of(75L, 243L, 1_417L, 4_592L, 14_877L), levels(Rarity.UNCOMMON, 5));
        assertEquals(List.of(75L, 437L, 2_551L, 14_877L), levels(Rarity.UNCOMMON, 4));
        assertEquals(List.of(75L, 1_417L, 14_877L), levels(Rarity.UNCOMMON, 3));
        assertEquals(List.of(100L, 180L, 324L, 583L, 1_050L, 1_890L, 3_401L, 6_122L, 11_020L, 19_836L), levels(Rarity.RARE, 10));
        assertEquals(List.of(100L, 324L, 1_890L, 6_122L, 19_836L), levels(Rarity.RARE, 5));
        assertEquals(List.of(100L, 583L, 3_401L, 19_836L), levels(Rarity.RARE, 4));
        assertEquals(List.of(100L, 1_890L, 19_836L), levels(Rarity.RARE, 3));
        assertEquals(List.of(1_890L), levels(Rarity.RARE, 1));
        assertEquals(List.of(150L, 2_834L, 29_754L), levels(Rarity.EPIC, 3));
        assertEquals(List.of(150L, 29_754L), levels(Rarity.EPIC, 2));
        assertEquals(List.of(250L, 4_724L, 49_590L), levels(Rarity.LEGENDARY, 3));
        assertEquals(List.of(79_344L), levels(Rarity.MYTHIC, 1));
    }

    @Test
    void totalsToMax() {
        assertEquals(14_136, costs.totalTo(fake(Rarity.COMMON, 5), 5));
        assertEquals(11_961, costs.totalTo(fake(Rarity.COMMON, 4), 4));
        assertEquals(21_204, costs.totalTo(fake(Rarity.UNCOMMON, 5), 5));
        assertEquals(17_940, costs.totalTo(fake(Rarity.UNCOMMON, 4), 4));
        assertEquals(16_369, costs.totalTo(fake(Rarity.UNCOMMON, 3), 3));
        assertEquals(44_506, costs.totalTo(fake(Rarity.RARE, 10), 10));
        assertEquals(28_272, costs.totalTo(fake(Rarity.RARE, 5), 5));
        assertEquals(23_920, costs.totalTo(fake(Rarity.RARE, 4), 4));
        assertEquals(21_826, costs.totalTo(fake(Rarity.RARE, 3), 3));
        assertEquals(32_738, costs.totalTo(fake(Rarity.EPIC, 3), 3));
        assertEquals(29_904, costs.totalTo(fake(Rarity.EPIC, 2), 2));
        assertEquals(54_564, costs.totalTo(fake(Rarity.LEGENDARY, 3), 3));
        // §12.2 partial totals
        assertEquals(1_157, costs.totalTo(fake(Rarity.COMMON, 5), 3));
        assertEquals(604, costs.totalTo(fake(Rarity.RARE, 10), 3));
        assertEquals(2_237, costs.totalTo(fake(Rarity.RARE, 10), 5));
        assertEquals(7_528, costs.totalTo(fake(Rarity.RARE, 10), 7));
    }

    @Test
    void realEnchantsUseTheirRarity() {
        assertEquals(50, costs.levelCost(Fixtures.def("swiftness"), 1));
        assertEquals(19_836, costs.levelCost(Fixtures.def("slaughter"), 10));
        assertEquals(29_754, costs.levelCost(Fixtures.def("vein_rend"), 3));
        assertEquals(79_344, costs.levelCost(Fixtures.def("worldbreaker"), 1));
        assertEquals(945, costs.levelCost(Fixtures.def("night_eye"), 1), "max-I non-mythic = step 6");
    }

    @Test
    void applyFeeIsLevelOneOrAQuarterOfTheBookLevel() {
        EnchantDef vein = Fixtures.def("vein_rend"); // epic III: 150 / 2,834 / 29,754
        assertEquals(150, costs.applyFee(vein, 1), "level I cost");
        assertEquals(709, costs.applyFee(vein, 2), "25% of 2,834 (the plan's example: Vein Rend II costs 709)");
        assertEquals(7_439, costs.applyFee(vein, 3), "25% of 29,754 rounded");
        EnchantDef swift = Fixtures.def("swiftness"); // common V: 50 / 162 / …
        assertEquals(50, costs.applyFee(swift, 2), "25% of 162 = 41 is below level I (50)");
    }

    @Test
    void upgradeCombineAndShatter() {
        EnchantDef slaughter = Fixtures.def("slaughter");
        assertEquals(180, costs.upgradeCost(slaughter, 1));
        assertEquals(19_836, costs.upgradeCost(slaughter, 9));
        assertEquals(45, costs.combineFee(slaughter, 2), "25% of 180");
        assertEquals(4_959, costs.combineFee(slaughter, 10), "25% of 19,836");
        assertEquals(0, costs.shatterRefund(0));
        assertEquals(141, costs.shatterRefund(709), "20% of 709 rounded down");
        assertEquals(6_547, costs.shatterRefund(32_738), "20% of 32,738 rounded down");
    }

    @Test
    void configChangesTheNumbers() {
        EnchanterConfig cfg = new EnchanterConfig();
        cfg.stepBase = 200;
        cfg.factorRare = 2;
        EnchantCosts changed = cfg.costs();
        assertEquals(400, changed.levelCost(Fixtures.def("slaughter"), 1));
        assertEquals(Map.of(BonusFamily.CRYSTALS, 200.0).get(BonusFamily.CRYSTALS), cfg.caps().get(BonusFamily.CRYSTALS));
        assertEquals(List.of(), cfg.validate(), "default enchanter.yml has no problems");
    }
}
