package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.skills.SkillType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gates (WD1) and which button a set shows (WARDROBE §2.4, §6.2). */
class OffersTest {

    private static final WardrobeConfig CONFIG = new WardrobeConfig();
    private static final Function<String, List<Integer>> TIERS = CONFIG::tiers;

    private static Map<SkillType, Integer> skills(int mining, int foraging, int combat, int fishing) {
        Map<SkillType, Integer> m = new EnumMap<>(SkillType.class);
        m.put(SkillType.MINING, mining);
        m.put(SkillType.FORAGING, foraging);
        m.put(SkillType.COMBAT, combat);
        m.put(SkillType.FARMING, 0);
        m.put(SkillType.FISHING, fishing);
        return m;
    }

    @Test
    void tierFromSkillLevel() {
        List<Integer> mining = CONFIG.tiers("mining");
        assertEquals(1, Offers.tier(mining, 0));
        assertEquals(5, Offers.tier(mining, 20));
        assertEquals(6, Offers.tier(mining, 25));
        assertEquals(12, Offers.tier(mining, 150));
    }

    @Test
    void wingGate() {
        SetDef deepforge = Fixtures.def("deepforge");
        Offers.GateStatus low = Offers.gate(deepforge.gate(), skills(20, 99, 99, 0), TIERS);
        assertFalse(low.passes(), "forest tier does not count for a mining gate");
        assertEquals("tier", low.kind());
        assertEquals(6, low.need());
        assertEquals(5, low.have());
        assertTrue(Offers.gate(deepforge.gate(), skills(25, 0, 0, 0), TIERS).passes());
    }

    @Test
    void anyWingOrAnySkill() {
        SetDef lore = Fixtures.def("lorekeeper");
        assertTrue(Offers.gate(lore.gate(), skills(0, 20, 0, 0), TIERS).passes(), "forest tier 5 counts for any wing");
        assertTrue(Offers.gate(lore.gate(), skills(0, 0, 0, 25), TIERS).passes(), "or any skill 25 (fishing 25 = band 2 only)");
        Offers.GateStatus no = Offers.gate(lore.gate(), skills(10, 10, 10, 10), TIERS);
        assertFalse(no.passes());
        assertEquals("tier", no.kind(), "shows the first requirement");
    }

    @Test
    void skillGate() {
        SetDef storm = Fixtures.def("stormkeel");
        assertFalse(Offers.gate(storm.gate(), skills(99, 99, 99, 39), TIERS).passes());
        Offers.GateStatus ok = Offers.gate(storm.gate(), skills(0, 0, 0, 40), TIERS);
        assertTrue(ok.passes());
        assertEquals("skill", ok.kind());
        assertTrue(Offers.gate(Fixtures.def("delver").gate(), skills(0, 0, 0, 0), TIERS).passes());
    }

    private static Offers.Offer offer(String id, boolean owned, boolean sigil, boolean claimed, boolean gate, boolean feature) {
        Offers.GateStatus g = gate ? Offers.GateStatus.OPEN : new Offers.GateStatus(false, "tier", "any", 5, 1);
        return Offers.offer(Fixtures.def(id), new Offers.State(owned, sigil, claimed), g, feature);
    }

    @Test
    void offers() {
        Offers.Offer buy = offer("delver", false, false, false, true, true);
        assertEquals(Offers.Kind.BUY, buy.kind());
        assertEquals(Currency.MONEY, buy.currency());
        assertEquals(20_000, buy.price());
        assertEquals(Currency.CRYSTALS, offer("deepforge", false, false, false, true, true).currency());
        assertEquals(Offers.Kind.OWNED, offer("delver", true, false, false, true, true).kind());
        assertEquals(Offers.Kind.LOCKED, offer("coinmaster", false, false, false, false, true).kind());
        assertEquals(Offers.Kind.ARRIVES, offer("banneret", false, false, false, true, false).kind());
        assertEquals(Offers.Kind.CRATE_ONLY, offer("phoenix", false, false, false, true, true).kind());
        assertEquals(Offers.Kind.CRATE_ONLY, offer("phoenix", false, false, false, false, true).kind(), "crate sets show their source first");
    }

    @Test
    void creditSetsAndSigils() {
        Offers.Offer gems = offer("lorekeeper", false, false, false, true, true);
        assertEquals(Offers.Kind.BUY, gems.kind());
        assertEquals(Currency.CREDITS, gems.currency(), "paid with credits: earned first, then bought");
        assertEquals(400, gems.price());
        assertEquals(Offers.Kind.CLAIM, offer("lorekeeper", false, true, false, false, true).kind(), "a Sigil skips the gate (met when bought)");
        assertEquals(Offers.Kind.CLAIMED, offer("lorekeeper", false, true, true, true, true).kind());
        assertEquals(Offers.Kind.OWNED, offer("lorekeeper", true, true, true, true, true).kind());
    }

    @Test
    void redeem() {
        SetDef phoenix = Fixtures.def("phoenix");
        assertTrue(Offers.canRedeem(phoenix, false, Offers.GateStatus.OPEN, true));
        assertFalse(Offers.canRedeem(phoenix, true, Offers.GateStatus.OPEN, true), "one copy per profile");
        assertFalse(Offers.canRedeem(phoenix, false, new Offers.GateStatus(false, "tier", "any", 7, 3), true));
        assertFalse(Offers.canRedeem(Fixtures.def("conqueror"), false, Offers.GateStatus.OPEN, false));
    }
}
