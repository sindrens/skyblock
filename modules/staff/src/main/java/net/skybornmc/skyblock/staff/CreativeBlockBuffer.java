package net.skybornmc.skyblock.staff;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F15: blocks placed and broken in creative on islands during staff mode, collected between two audit writes so a big
 * build is a handful of audit rows instead of one per block. One batch per staff member, island and action; a batch is
 * split after {@code maxPerRow} blocks. Main thread only. Pure (no Bukkit), unit-tested.
 */
final class CreativeBlockBuffer {

    enum Action {
        PLACE("island_creative_place"), BREAK("island_creative_break");

        final String audit;

        Action(String audit) {
            this.audit = audit;
        }
    }

    /** What one audit row is about. {@code island} is the island id, or {@code none} outside every island slot. */
    record Key(UUID staff, String staffName, String island, String world, Action action) {
    }

    /** One block: coordinates and the material key (e.g. {@code stone}). */
    record Block(int x, int y, int z, String material) {

        /** {@code x,y,z material}, the form stored in the audit details. */
        String describe() {
            return x + "," + y + "," + z + " " + material;
        }
    }

    /** One audit row to write. */
    record Batch(Key key, List<Block> blocks) {

        List<String> described() {
            return blocks.stream().map(Block::describe).toList();
        }
    }

    private final Map<Key, List<Block>> pending = new LinkedHashMap<>();
    private int size;

    void add(Key key, Block block) {
        pending.computeIfAbsent(key, k -> new ArrayList<>()).add(block);
        size++;
    }

    int size() {
        return size;
    }

    /** Takes everything collected, as audit rows of at most {@code maxPerRow} blocks. */
    List<Batch> drain(int maxPerRow) {
        return drain(null, maxPerRow);
    }

    /** Takes what {@code staff} collected ({@code null} = everyone), as audit rows of at most {@code maxPerRow} blocks. */
    List<Batch> drain(UUID staff, int maxPerRow) {
        int max = Math.max(1, maxPerRow);
        List<Batch> out = new ArrayList<>();
        Iterator<Map.Entry<Key, List<Block>>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, List<Block>> e = it.next();
            if (staff != null && !staff.equals(e.getKey().staff())) {
                continue;
            }
            List<Block> blocks = e.getValue();
            for (int from = 0; from < blocks.size(); from += max) {
                out.add(new Batch(e.getKey(), List.copyOf(blocks.subList(from, Math.min(blocks.size(), from + max)))));
            }
            size -= blocks.size();
            it.remove();
        }
        return out;
    }
}
