package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Plans a loadout swap (WARDROBE §7.4 step 1, LD4) from a snapshot of the player's inventory, their worn rows, the
 * Wardrobe Chest and the preset. Pure and unit-tested: it decides every move before anything happens, by simulating
 * them on a copy of the inventory, so applying the operations in order on the (locked) real inventory gives exactly the
 * planned result.
 *
 * <p>Rules: wardrobe pieces are worn/taken off by rows only (virtual). A hotbar slot gets its unique item from the
 * inventory (a move) or from the chest (take). The unique item that was in that slot goes into the chest (L3); a plain
 * item (or a unique item that can't be stored, or with the chest full) moves to a free inventory slot. Vanilla armor
 * under a piece moves to a free slot. Nothing is ever dropped: when something has nowhere to go, that slot is skipped
 * and reported; a preset item or set that is gone is skipped and reported too.</p>
 *
 * <p>Cell indices: 0-8 hotbar, 9-35 inventory, 36-39 armor (feet, legs, chest, head: {@link Equipment#armorSlotIndex}),
 * 40 off-hand.</p>
 */
final class LoadoutPlanner {

    static final int OFFHAND = 40;
    static final int SIZE = 41;

    private LoadoutPlanner() {
    }

    // ------------------------------------------------------------------ input

    enum Kind { EMPTY, PLAIN, UNIQUE, PIECE }

    /**
     * What one inventory cell holds.
     *
     * @param id       the unique item id (UNIQUE only)
     * @param storable the item may go in the Wardrobe Chest
     */
    record Cell(Kind kind, UUID id, boolean storable) {
        static final Cell EMPTY = new Cell(Kind.EMPTY, null, false);
        static final Cell PLAIN = new Cell(Kind.PLAIN, null, false);
        static final Cell PIECE = new Cell(Kind.PIECE, null, false);

        static Cell unique(UUID id, boolean storable) {
            return new Cell(Kind.UNIQUE, id, storable);
        }

        boolean empty() {
            return kind == Kind.EMPTY;
        }
    }

    /**
     * @param cells       inventory cells by index (missing = empty)
     * @param equipped    worn rows: armor slot → entry id
     * @param owned       active entries of the profile
     * @param chest       item ids stored in the Wardrobe Chest
     * @param chestUsed   chest places in use now (stored + being handed out)
     * @param chestSize   chest capacity
     */
    record Input(Map<Integer, Cell> cells, Map<ArmorPiece, UUID> equipped, Set<UUID> owned, Set<UUID> chest, int chestUsed,
                 int chestSize, Loadout preset) {
    }

    // ------------------------------------------------------------------ output

    sealed interface Op permits Move, Store, Take {
    }

    /** Move the item from one cell to another (empty at that point). */
    record Move(int from, int to) implements Op {
    }

    /** The unique item in {@code slot} goes into the chest. */
    record Store(int slot, UUID id) implements Op {
    }

    /** The chest item {@code id} goes into {@code to}. */
    record Take(UUID id, int to) implements Op {
    }

    /** Why a part of the preset was skipped (message {@code loadout.skip.<key>}). */
    enum Reason {
        SET_GONE("set-gone"), ITEM_MISSING("item-missing"), NO_ROOM("no-room");

        final String key;

        Reason(String key) {
            this.key = key;
        }
    }

    /**
     * A skipped part.
     *
     * @param piece the armor slot, or null for a hotbar slot
     * @param slot  the hotbar slot (0-8), or -1 for armor
     */
    record Skip(ArmorPiece piece, int slot, Reason reason) {
    }

    /**
     * The plan.
     *
     * @param ops   inventory operations in order (run after the database committed)
     * @param wear  armor slots → entry to wear (new rows)
     * @param off   armor slots whose worn piece comes off
     * @param store item ids going into the chest (in the swap's transaction)
     * @param take  item ids coming out of the chest
     * @param tools hotbar slots that get their preset item
     */
    record Plan(List<Op> ops, Map<ArmorPiece, UUID> wear, List<ArmorPiece> off, List<UUID> store, List<UUID> take, List<Skip> skips,
                int tools) {

        boolean nothing() {
            return ops.isEmpty() && wear.isEmpty() && off.isEmpty();
        }

        int pieces() {
            return wear.size() + off.size();
        }

        /** Chest places used after the swap. */
        int chestAfter(int chestUsed) {
            return chestUsed - take.size() + store.size();
        }
    }

    // ------------------------------------------------------------------ planning

    private enum Source { INVENTORY, CHEST }

    private static final class Target {
        final int slot;
        final UUID id;
        final Source source;

        Target(int slot, UUID id, Source source) {
            this.slot = slot;
            this.id = id;
            this.source = source;
        }
    }

    static Plan plan(Input in) {
        Cell[] cells = new Cell[SIZE];
        for (int i = 0; i < SIZE; i++) {
            Cell c = in.cells().get(i);
            cells[i] = c == null ? Cell.EMPTY : c;
        }
        List<Op> ops = new ArrayList<>();
        List<Skip> skips = new ArrayList<>();
        Loadout preset = in.preset();

        // A. Hotbar targets: where each preset item is now.
        Map<Integer, Target> targets = new LinkedHashMap<>();
        Set<UUID> inPlace = new HashSet<>();
        for (Map.Entry<Integer, Loadout.Tool> e : preset.hotbar().entrySet()) {
            int h = e.getKey();
            UUID id = e.getValue().itemId();
            if (h < 0 || h >= Loadout.HOTBAR) {
                continue;
            }
            if (cells[h].kind() == Kind.UNIQUE && id.equals(cells[h].id())) {
                inPlace.add(id);
                continue;
            }
            if (find(cells, id) >= 0) {
                targets.put(h, new Target(h, id, Source.INVENTORY));
            } else if (in.chest().contains(id)) {
                targets.put(h, new Target(h, id, Source.CHEST));
            } else {
                skips.add(new Skip(null, h, Reason.ITEM_MISSING));
            }
        }

        // B. Armor (rows only; vanilla armor under a new piece moves to a free slot).
        Map<ArmorPiece, UUID> wear = new LinkedHashMap<>();
        List<ArmorPiece> off = new ArrayList<>();
        for (ArmorPiece piece : ArmorPiece.values()) {
            Loadout.Armor choice = preset.armor().get(piece);
            if (choice == null) {
                continue; // keep
            }
            UUID current = in.equipped().get(piece);
            if (choice.empty()) {
                if (current != null) {
                    off.add(piece);
                }
                continue;
            }
            if (!in.owned().contains(choice.entryId())) {
                skips.add(new Skip(piece, -1, Reason.SET_GONE));
                continue;
            }
            if (choice.entryId().equals(current)) {
                continue;
            }
            int idx = Equipment.armorSlotIndex(piece);
            Cell there = cells[idx];
            if (there.kind() == Kind.PLAIN || there.kind() == Kind.UNIQUE) {
                int free = free(cells, targets.keySet());
                if (free < 0) {
                    skips.add(new Skip(piece, -1, Reason.NO_ROOM));
                    continue;
                }
                move(cells, ops, idx, free);
            }
            wear.put(piece, choice.entryId());
        }

        // C. Clear the target slots of what doesn't belong there.
        List<UUID> store = new ArrayList<>();
        for (Target t : List.copyOf(targets.values())) {
            Cell c = cells[t.slot];
            if (c.empty() || (c.kind() == Kind.UNIQUE && wanted(targets, c.id()))) {
                continue; // empty, or a preset item that moves on in step D
            }
            int incoming = (int) targets.values().stream().filter(x -> x.source == Source.CHEST).count();
            boolean chestRoom = in.chestUsed() - incoming + store.size() + 1 <= in.chestSize();
            if (c.kind() == Kind.UNIQUE && c.storable() && !inPlace.contains(c.id()) && chestRoom) {
                ops.add(new Store(t.slot, c.id()));
                store.add(c.id());
                cells[t.slot] = Cell.EMPTY;
                continue;
            }
            int free = free(cells, targets.keySet());
            if (free < 0) {
                targets.remove(t.slot);
                skips.add(new Skip(null, t.slot, Reason.NO_ROOM));
                continue;
            }
            move(cells, ops, t.slot, free);
        }

        // D. Fill: whenever a target slot is empty its item can come; cycles (A↔B) use one free slot.
        List<UUID> take = new ArrayList<>();
        int tools = 0;
        while (!targets.isEmpty()) {
            boolean progress = false;
            for (Target t : List.copyOf(targets.values())) {
                if (!cells[t.slot].empty()) {
                    continue;
                }
                if (t.source == Source.CHEST) {
                    ops.add(new Take(t.id, t.slot));
                    take.add(t.id);
                    cells[t.slot] = Cell.unique(t.id, true);
                } else {
                    int from = find(cells, t.id);
                    if (from < 0) { // can't happen (the item was found in step A and only moves)
                        targets.remove(t.slot);
                        skips.add(new Skip(null, t.slot, Reason.ITEM_MISSING));
                        continue;
                    }
                    move(cells, ops, from, t.slot);
                }
                targets.remove(t.slot);
                tools++;
                progress = true;
            }
            if (progress || targets.isEmpty()) {
                continue;
            }
            // Every remaining target slot holds an item another target still waits for: lift one out.
            Target first = targets.values().iterator().next();
            int free = free(cells, targets.keySet());
            if (free < 0) {
                for (Target t : targets.values()) {
                    skips.add(new Skip(null, t.slot, Reason.NO_ROOM));
                }
                targets.clear();
                break;
            }
            move(cells, ops, first.slot, free);
        }
        // An item lifted out for a target that was skipped later simply stays in the inventory (never lost).
        skips.sort(java.util.Comparator.comparingInt((Skip s) -> s.piece() == null ? 1 : 0)
                .thenComparingInt(s -> s.piece() == null ? s.slot() : s.piece().ordinal()));
        return new Plan(List.copyOf(ops), wear, List.copyOf(off), List.copyOf(store), List.copyOf(take), List.copyOf(skips), tools);
    }

    private static boolean wanted(Map<Integer, Target> targets, UUID id) {
        for (Target t : targets.values()) {
            if (t.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void move(Cell[] cells, List<Op> ops, int from, int to) {
        ops.add(new Move(from, to));
        cells[to] = cells[from];
        cells[from] = Cell.EMPTY;
    }

    /** Where a unique item is (inventory, hotbar or off-hand; never armor), or -1. */
    static int find(Cell[] cells, UUID id) {
        for (int i = 0; i < SIZE; i++) {
            if (i >= 36 && i <= 39) {
                continue;
            }
            if (cells[i].kind() == Kind.UNIQUE && id.equals(cells[i].id())) {
                return i;
            }
        }
        return -1;
    }

    /** A free slot for something moved away: inventory 9-35 first, then hotbar slots no preset item waits for. */
    static int free(Cell[] cells, Collection<Integer> reserved) {
        for (int i = 9; i < 36; i++) {
            if (cells[i].empty()) {
                return i;
            }
        }
        for (int i = 0; i < Loadout.HOTBAR; i++) {
            if (cells[i].empty() && !reserved.contains(i)) {
                return i;
            }
        }
        return -1;
    }
}
