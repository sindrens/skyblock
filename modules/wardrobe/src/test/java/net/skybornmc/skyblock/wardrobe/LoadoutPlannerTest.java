package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.wardrobe.LoadoutPlanner.Cell;
import net.skybornmc.skyblock.wardrobe.LoadoutPlanner.Move;
import net.skybornmc.skyblock.wardrobe.LoadoutPlanner.Plan;
import net.skybornmc.skyblock.wardrobe.LoadoutPlanner.Reason;
import net.skybornmc.skyblock.wardrobe.LoadoutPlanner.Store;
import net.skybornmc.skyblock.wardrobe.LoadoutPlanner.Take;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The swap planner (WARDROBE §7.4 step 1): every plan is replayed on a model inventory to prove that no item is ever
 * lost or doubled, that each preset item ends in its slot, and that skipped parts are reported.
 */
class LoadoutPlannerTest {

    private final UUID pick = UUID.randomUUID();
    private final UUID axe = UUID.randomUUID();
    private final UUID sword = UUID.randomUUID();
    private final UUID oldTool = UUID.randomUUID();
    private final UUID setA = UUID.randomUUID();
    private final UUID setB = UUID.randomUUID();

    private final Map<Integer, Cell> cells = new HashMap<>();
    private final Map<ArmorPiece, UUID> equipped = new EnumMap<>(ArmorPiece.class);
    private final Set<UUID> owned = new HashSet<>(Set.of(setA, setB));
    private final Set<UUID> chest = new HashSet<>();
    private int chestUsed;
    private int chestSize = 27;

    private static Cell u(UUID id) {
        return Cell.unique(id, true);
    }

    private Loadout preset(Map<ArmorPiece, Loadout.Armor> armor, Map<Integer, UUID> hotbar) {
        Map<Integer, Loadout.Tool> tools = new TreeMap<>();
        hotbar.forEach((slot, id) -> tools.put(slot, new Loadout.Tool(id, "Tool", "iron_pickaxe")));
        return new Loadout(UUID.randomUUID(), UUID.randomUUID(), 0, "Test", "iron_pickaxe", armor, null, tools, 0);
    }

    private Plan plan(Loadout preset) {
        chestUsed = Math.max(chestUsed, chest.size());
        return LoadoutPlanner.plan(new LoadoutPlanner.Input(Map.copyOf(cells), Map.copyOf(equipped), Set.copyOf(owned), Set.copyOf(chest),
                chestUsed, chestSize, preset));
    }

    /** Replays a plan; fails on an illegal step (move into a full cell, store/take of the wrong item). */
    private Map<Integer, Cell> replay(Plan plan, Set<UUID> chestAfter) {
        Map<Integer, Cell> inv = new HashMap<>(cells);
        for (LoadoutPlanner.Op op : plan.ops()) {
            switch (op) {
                case Move m -> {
                    Cell c = inv.getOrDefault(m.from(), Cell.EMPTY);
                    assertTrue(!c.empty(), "move from an empty cell " + m);
                    assertTrue(inv.getOrDefault(m.to(), Cell.EMPTY).empty(), "move into a full cell " + m);
                    inv.put(m.to(), c);
                    inv.put(m.from(), Cell.EMPTY);
                }
                case Store s -> {
                    assertEquals(s.id(), inv.getOrDefault(s.slot(), Cell.EMPTY).id(), "store of the wrong item " + s);
                    inv.put(s.slot(), Cell.EMPTY);
                    assertTrue(chestAfter.add(s.id()), "stored twice");
                }
                case Take t -> {
                    assertTrue(chestAfter.remove(t.id()), "take of an item not in the chest " + t);
                    assertTrue(inv.getOrDefault(t.to(), Cell.EMPTY).empty(), "take into a full cell " + t);
                    inv.put(t.to(), u(t.id()));
                }
            }
        }
        return inv;
    }

    /** Every item exists exactly once in inventory + chest, before and after. */
    private void assertConserved(Map<Integer, Cell> after, Set<UUID> chestAfter) {
        Map<UUID, Integer> before = new HashMap<>();
        int plainBefore = 0;
        for (Cell c : cells.values()) {
            if (c.kind() == LoadoutPlanner.Kind.UNIQUE) {
                before.merge(c.id(), 1, Integer::sum);
            } else if (c.kind() == LoadoutPlanner.Kind.PLAIN) {
                plainBefore++;
            }
        }
        chest.forEach(id -> before.merge(id, 1, Integer::sum));
        Map<UUID, Integer> now = new HashMap<>();
        int plainAfter = 0;
        for (Cell c : after.values()) {
            if (c.kind() == LoadoutPlanner.Kind.UNIQUE) {
                now.merge(c.id(), 1, Integer::sum);
            } else if (c.kind() == LoadoutPlanner.Kind.PLAIN) {
                plainAfter++;
            }
        }
        chestAfter.forEach(id -> now.merge(id, 1, Integer::sum));
        assertEquals(before, now, "unique items lost or doubled");
        assertEquals(plainBefore, plainAfter, "plain items lost");
        now.values().forEach(n -> assertEquals(1, n));
    }

    private Map<Integer, Cell> run(Plan plan) {
        Set<UUID> chestAfter = new HashSet<>(chest);
        Map<Integer, Cell> after = replay(plan, chestAfter);
        assertConserved(after, chestAfter);
        return after;
    }

    // ------------------------------------------------------------------ hotbar

    @Test
    void toolFromInventoryMovesIntoItsSlot() {
        cells.put(20, u(pick));
        Plan plan = plan(preset(Map.of(), Map.of(0, pick)));
        Map<Integer, Cell> after = run(plan);
        assertEquals(pick, after.get(0).id());
        assertEquals(1, plan.tools());
        assertTrue(plan.store().isEmpty() && plan.take().isEmpty());
    }

    @Test
    void toolFromChestComesOutAndTheOldToolGoesIn() {
        chest.add(pick);
        cells.put(0, u(oldTool));
        Plan plan = plan(preset(Map.of(), Map.of(0, pick)));
        Map<Integer, Cell> after = run(plan);
        assertEquals(pick, after.get(0).id());
        assertEquals(List.of(oldTool), plan.store(), "L3: the swapped-out tool goes to the wardrobe");
        assertEquals(List.of(pick), plan.take());
    }

    @Test
    void plainItemInTheSlotMovesToAFreeSlotNeverDropped() {
        chest.add(pick);
        cells.put(0, Cell.PLAIN);
        Plan plan = plan(preset(Map.of(), Map.of(0, pick)));
        Map<Integer, Cell> after = run(plan);
        assertEquals(pick, after.get(0).id());
        assertEquals(LoadoutPlanner.Kind.PLAIN, after.get(9).kind(), "moved to the first inventory slot");
        assertTrue(plan.store().isEmpty(), "plain items are never stored");
    }

    @Test
    void fullInventorySkipsTheSlotAndKeepsEverything() {
        chest.add(pick);
        for (int i = 0; i < 36; i++) {
            cells.put(i, Cell.PLAIN);
        }
        Plan plan = plan(preset(Map.of(), Map.of(0, pick)));
        run(plan);
        assertTrue(plan.nothing());
        assertEquals(List.of(new LoadoutPlanner.Skip(null, 0, Reason.NO_ROOM)), plan.skips());
        assertTrue(plan.take().isEmpty(), "the tool stays in the chest");
    }

    @Test
    void missingToolIsSkippedTheRestStillHappens() {
        cells.put(15, u(axe));
        Plan plan = plan(preset(Map.of(), Map.of(0, pick, 1, axe)));
        Map<Integer, Cell> after = run(plan);
        assertEquals(axe, after.get(1).id());
        assertEquals(List.of(new LoadoutPlanner.Skip(null, 0, Reason.ITEM_MISSING)), plan.skips());
    }

    @Test
    void swappedToolsUseOneFreeSlot() {
        cells.put(0, u(axe));
        cells.put(1, u(pick));
        Plan plan = plan(preset(Map.of(), Map.of(0, pick, 1, axe)));
        Map<Integer, Cell> after = run(plan);
        assertEquals(pick, after.get(0).id());
        assertEquals(axe, after.get(1).id());
        assertTrue(plan.store().isEmpty());
    }

    @Test
    void threeWayRotationAndOffhandSource() {
        cells.put(0, u(axe));
        cells.put(1, u(sword));
        cells.put(2, u(pick));
        cells.put(LoadoutPlanner.OFFHAND, u(oldTool));
        Plan plan = plan(preset(Map.of(), Map.of(0, pick, 1, axe, 2, sword, 3, oldTool)));
        Map<Integer, Cell> after = run(plan);
        assertEquals(pick, after.get(0).id());
        assertEquals(axe, after.get(1).id());
        assertEquals(sword, after.get(2).id());
        assertEquals(oldTool, after.get(3).id());
        assertTrue(after.getOrDefault(LoadoutPlanner.OFFHAND, Cell.EMPTY).empty());
    }

    @Test
    void swapCycleWithFullInventoryIsSkippedNotBroken() {
        for (int i = 0; i < 36; i++) {
            cells.put(i, Cell.PLAIN);
        }
        cells.put(0, u(axe));
        cells.put(1, u(pick));
        Plan plan = plan(preset(Map.of(), Map.of(0, pick, 1, axe)));
        Map<Integer, Cell> after = run(plan);
        assertEquals(2, plan.skips().size());
        assertEquals(axe, after.get(0).id(), "nothing moved");
    }

    @Test
    void keepSlotsAndToolsAlreadyInPlaceAreUntouched() {
        cells.put(0, u(pick));
        cells.put(5, u(oldTool));
        Plan plan = plan(preset(Map.of(), Map.of(0, pick)));
        assertTrue(plan.nothing());
        assertTrue(plan.skips().isEmpty());
    }

    @Test
    void chestFullMovesTheOldToolToTheInventoryInstead() {
        chestSize = 1;
        chest.add(pick); // chest is full with the incoming tool only: taking it frees the place
        cells.put(0, u(oldTool));
        Plan plan = plan(preset(Map.of(), Map.of(0, pick)));
        run(plan);
        assertEquals(List.of(oldTool), plan.store(), "net chest use stays at 1");

        chestSize = 1;
        chest.clear();
        chest.add(axe); // another item fills the chest
        chestUsed = 1;
        cells.clear();
        cells.put(0, u(oldTool));
        cells.put(20, u(pick));
        Plan full = plan(preset(Map.of(), Map.of(0, pick)));
        Map<Integer, Cell> after = run(full);
        assertTrue(full.store().isEmpty());
        assertEquals(oldTool, after.get(9).id(), "kept in the inventory");
        assertEquals(1, full.chestAfter(1));
    }

    @Test
    void nonStorableUniqueItemIsMovedNotStored() {
        chest.add(pick);
        cells.put(0, Cell.unique(oldTool, false));
        Plan plan = plan(preset(Map.of(), Map.of(0, pick)));
        Map<Integer, Cell> after = run(plan);
        assertTrue(plan.store().isEmpty());
        assertEquals(oldTool, after.get(9).id());
    }

    // ------------------------------------------------------------------ armor

    @Test
    void armorWearsTakesOffAndKeeps() {
        equipped.put(ArmorPiece.HEAD, setB);
        equipped.put(ArmorPiece.LEGS, setB);
        Map<ArmorPiece, Loadout.Armor> armor = new EnumMap<>(ArmorPiece.class);
        armor.put(ArmorPiece.HEAD, new Loadout.Armor(setA));
        armor.put(ArmorPiece.CHEST, new Loadout.Armor(setA));
        armor.put(ArmorPiece.LEGS, Loadout.Armor.NONE);
        cells.put(Equipment.armorSlotIndex(ArmorPiece.HEAD), Cell.PIECE);
        Plan plan = plan(preset(armor, Map.of()));
        assertEquals(Map.of(ArmorPiece.HEAD, setA, ArmorPiece.CHEST, setA), plan.wear());
        assertEquals(List.of(ArmorPiece.LEGS), plan.off());
        assertEquals(3, plan.pieces());
        assertTrue(plan.ops().isEmpty(), "pieces are virtual: no item moves");
    }

    @Test
    void vanillaArmorUnderANewPieceMovesToTheInventory() {
        int chestIdx = Equipment.armorSlotIndex(ArmorPiece.CHEST);
        cells.put(chestIdx, Cell.PLAIN);
        Plan plan = plan(preset(Map.of(ArmorPiece.CHEST, new Loadout.Armor(setA)), Map.of()));
        Map<Integer, Cell> after = run(plan);
        assertEquals(List.of(new Move(chestIdx, 9)), plan.ops());
        assertEquals(LoadoutPlanner.Kind.PLAIN, after.get(9).kind());
        assertEquals(Map.of(ArmorPiece.CHEST, setA), plan.wear());
    }

    @Test
    void vanillaArmorWithAFullInventoryKeepsTheVanillaPiece() {
        for (int i = 0; i < 36; i++) {
            cells.put(i, Cell.PLAIN);
        }
        cells.put(Equipment.armorSlotIndex(ArmorPiece.CHEST), Cell.PLAIN);
        Plan plan = plan(preset(Map.of(ArmorPiece.CHEST, new Loadout.Armor(setA)), Map.of()));
        assertTrue(plan.nothing());
        assertEquals(List.of(new LoadoutPlanner.Skip(ArmorPiece.CHEST, -1, Reason.NO_ROOM)), plan.skips());
    }

    @Test
    void setThatIsGoneIsSkipped() {
        owned.remove(setA);
        Plan plan = plan(preset(Map.of(ArmorPiece.HEAD, new Loadout.Armor(setA), ArmorPiece.FEET, new Loadout.Armor(setB)), Map.of()));
        assertEquals(Map.of(ArmorPiece.FEET, setB), plan.wear());
        assertEquals(List.of(new LoadoutPlanner.Skip(ArmorPiece.HEAD, -1, Reason.SET_GONE)), plan.skips());
    }

    @Test
    void emptySlotOverVanillaArmorLeavesTheVanillaPiece() {
        cells.put(Equipment.armorSlotIndex(ArmorPiece.HEAD), Cell.PLAIN);
        Plan plan = plan(preset(Map.of(ArmorPiece.HEAD, Loadout.Armor.NONE), Map.of()));
        assertTrue(plan.nothing(), "vanilla armor is never a wardrobe item");
    }

    @Test
    void hotbarTargetsAreNotUsedAsFreeSlots() {
        // Inventory 9-35 full, hotbar 0 has a plain item that must go; slot 1 is a target too, so 2 is the only place.
        for (int i = 9; i < 36; i++) {
            cells.put(i, Cell.PLAIN);
        }
        cells.put(0, Cell.PLAIN);
        chest.add(pick);
        chest.add(axe);
        Plan plan = plan(preset(Map.of(), Map.of(0, pick, 1, axe)));
        Map<Integer, Cell> after = run(plan);
        assertEquals(pick, after.get(0).id());
        assertEquals(axe, after.get(1).id());
        assertEquals(LoadoutPlanner.Kind.PLAIN, after.get(2).kind());
    }
}
