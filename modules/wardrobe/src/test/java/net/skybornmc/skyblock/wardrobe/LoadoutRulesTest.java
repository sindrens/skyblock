package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slot rules (L2), names, the swap guards (L4: combat, warzone, freeze, cooldown) and the preset JSON codec. */
class LoadoutRulesTest {

    @Test
    void slotsFreeRankBoughtAndCap() {
        assertEquals(2, LoadoutRules.slots(2, 0, 0, 3, 8), "2 free");
        assertEquals(3, LoadoutRules.slots(2, 3, 0, 3, 8), "Knight 3");
        assertEquals(5, LoadoutRules.slots(2, 5, 0, 3, 8), "Monarch 5");
        assertEquals(2, LoadoutRules.slots(2, 1, 0, 3, 8), "a rank grant below the free count changes nothing");
        assertEquals(4, LoadoutRules.slots(2, 0, 2, 3, 8), "bought add on top");
        assertEquals(5, LoadoutRules.slots(2, 0, 9, 3, 8), "at most +3 bought");
        assertEquals(8, LoadoutRules.slots(2, 8, 3, 3, 8), "max 8");
        assertEquals(0, LoadoutRules.slots(-1, -1, -1, -1, 8));
    }

    @Test
    void rankSlotsTakeTheHighestGrant() {
        Set<Integer> granted = Set.of(3, 5);
        assertEquals(5, LoadoutRules.rankSlots(8, granted::contains));
        assertEquals(0, LoadoutRules.rankSlots(8, n -> false));
    }

    @Test
    void namesAreOneSafeWord() {
        assertEquals("Deep-mine", LoadoutRules.cleanName("  Deep   mine "));
        assertEquals(Optional.empty(), LoadoutRules.checkName("Mining_2", 16, List.of("Combat")));
        assertEquals(Optional.of(LoadoutRules.NameProblem.EMPTY), LoadoutRules.checkName("", 16, List.of()));
        assertEquals(Optional.of(LoadoutRules.NameProblem.TOO_LONG), LoadoutRules.checkName("abcdefghijklmnopq", 16, List.of()));
        assertEquals(Optional.of(LoadoutRules.NameProblem.CHARACTERS), LoadoutRules.checkName("<red>x", 16, List.of()));
        assertEquals(Optional.of(LoadoutRules.NameProblem.CHARACTERS), LoadoutRules.checkName("&cMine", 16, List.of()));
        assertEquals(Optional.of(LoadoutRules.NameProblem.TAKEN), LoadoutRules.checkName("MINING", 16, List.of("mining")));
    }

    @Test
    void freeIndex() {
        assertEquals(0, LoadoutRules.freeIndex(List.of(), 2));
        assertEquals(1, LoadoutRules.freeIndex(List.of(0, 2), 3));
        assertEquals(-1, LoadoutRules.freeIndex(List.of(0, 1), 2));
    }

    private static LoadoutRules.State state(boolean frozen, boolean warzone, long combat, long cooldown, boolean bypass) {
        return new LoadoutRules.State(true, false, false, frozen, warzone, combat, cooldown, bypass);
    }

    @Test
    void guards() {
        assertEquals(Optional.empty(), LoadoutRules.problem(state(false, false, 0, 0, false)), "anywhere else is fine (L4)");
        assertEquals(Optional.of("in-combat"), LoadoutRules.problem(state(false, false, 4_000, 0, false)));
        assertEquals(Optional.of("loadout.blocked-world"), LoadoutRules.problem(state(false, true, 0, 0, false)), "not in the warzone");
        assertEquals(Optional.of("frozen"), LoadoutRules.problem(state(true, false, 0, 0, false)));
        assertEquals(Optional.of("cooldown"), LoadoutRules.problem(state(false, false, 0, 1_500, false)), "2 s cooldown");
        assertEquals(Optional.empty(), LoadoutRules.problem(state(false, false, 0, 1_500, true)), "cooldown bypass");
        assertEquals(Optional.of("in-combat"), LoadoutRules.problem(state(false, false, 4_000, 0, true)), "no bypass for combat");
        assertEquals(Optional.of("loading"), LoadoutRules.problem(new LoadoutRules.State(false, false, false, false, false, 0, 0, false)));
        assertEquals(Optional.of("busy"), LoadoutRules.problem(new LoadoutRules.State(true, true, false, false, false, 0, 0, false)),
                "one swap at a time");
        assertEquals(Optional.of("busy"), LoadoutRules.problem(new LoadoutRules.State(true, false, true, false, false, 0, 0, false)),
                "not during a profile switch");
    }

    @Test
    void codecRoundTripAndBadJson() {
        UUID entry = UUID.randomUUID();
        UUID tool = UUID.randomUUID();
        Map<ArmorPiece, Loadout.Armor> armor = new EnumMap<>(ArmorPiece.class);
        armor.put(ArmorPiece.HEAD, new Loadout.Armor(entry));
        armor.put(ArmorPiece.FEET, Loadout.Armor.NONE);
        Map<Integer, Loadout.Tool> hotbar = new TreeMap<>();
        hotbar.put(3, new Loadout.Tool(tool, "Iron \"Pick\" ;:", "iron_pickaxe"));
        assertEquals(armor, Loadout.parseArmor(Loadout.armorJson(armor)));
        assertEquals(hotbar, Loadout.parseHotbar(Loadout.hotbarJson(hotbar)));
        assertTrue(Loadout.parseArmor("not json").isEmpty(), "bad JSON = keep everything");
        assertTrue(Loadout.parseHotbar("[1,2]").isEmpty());
        assertTrue(Loadout.parseHotbar(null).isEmpty());
        String twice = "{\"0\":{\"id\":\"" + tool + "\"},\"4\":{\"id\":\"" + tool + "\"},\"12\":{\"id\":\"" + UUID.randomUUID() + "\"},\"x\":1}";
        Map<Integer, Loadout.Tool> parsed = Loadout.parseHotbar(twice);
        assertEquals(Set.of(0), parsed.keySet(), "the same item twice keeps the lowest slot; slot 12 is not a hotbar slot");
    }

    @Test
    void mergeRepointsPresets() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID merged = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Map<ArmorPiece, Loadout.Armor> armor = new EnumMap<>(ArmorPiece.class);
        armor.put(ArmorPiece.HEAD, new Loadout.Armor(a));
        armor.put(ArmorPiece.CHEST, new Loadout.Armor(b));
        armor.put(ArmorPiece.LEGS, new Loadout.Armor(other));
        armor.put(ArmorPiece.FEET, Loadout.Armor.NONE);
        Loadout l = new Loadout(UUID.randomUUID(), UUID.randomUUID(), 0, "X", "stone", armor, null, Map.of(), 0);
        Loadout r = Loadouts.repointed(List.of(l), a, b, merged).getFirst();
        assertEquals(merged, r.armor().get(ArmorPiece.HEAD).entryId());
        assertEquals(merged, r.armor().get(ArmorPiece.CHEST).entryId());
        assertEquals(other, r.armor().get(ArmorPiece.LEGS).entryId());
        assertTrue(r.armor().get(ArmorPiece.FEET).empty());
    }
}
