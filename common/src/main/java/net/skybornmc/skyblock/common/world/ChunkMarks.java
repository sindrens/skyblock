package net.skybornmc.skyblock.common.world;

import net.skybornmc.skyblock.common.item.ItemKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * FX-17 (7 Nov 2026): blocks a player placed, saved in the chunk itself (chunk persistent data), so the mark survives a
 * restart and has no global cap. Placing an ore and breaking it after a restart gave free tool XP and shards with the
 * RAM-only {@link PlacedBlocks}. Each chunk keeps a sorted {@code int[]} of packed positions (binary search), at most
 * {@link #CAP} per chunk (the oldest-by-position is never dropped: at the cap a new mark is simply not stored, which only
 * matters for a chunk filled with thousands of placed ores). Main thread. The set maths is pure and unit-tested.
 */
public final class ChunkMarks {

    /** Most marks one chunk keeps (an island chunk full of placed ore blocks still fits). */
    public static final int CAP = 8_192;
    private static final NamespacedKey KEY = ItemKeys.key("placed_marks");

    private ChunkMarks() {
    }

    /** Chunk-relative position as one int: x and z 4 bits each, y + 2048 in 12 bits. */
    static int pack(int x, int y, int z) {
        return ((x & 15) << 16) | ((z & 15) << 12) | ((y + 2048) & 0xFFF);
    }

    static boolean contains(int[] set, int v) {
        return set != null && Arrays.binarySearch(set, v) >= 0;
    }

    /** The set with {@code v} (same array when it is already in or the set is full). */
    static int[] add(int[] set, int v, int cap) {
        int[] s = set == null ? new int[0] : set;
        int i = Arrays.binarySearch(s, v);
        if (i >= 0 || s.length >= cap) {
            return s;
        }
        int at = -i - 1;
        int[] out = new int[s.length + 1];
        System.arraycopy(s, 0, out, 0, at);
        out[at] = v;
        System.arraycopy(s, at, out, at + 1, s.length - at);
        return out;
    }

    /** The set without {@code v} (same array when it was not in). */
    static int[] remove(int[] set, int v) {
        if (set == null) {
            return new int[0];
        }
        int i = Arrays.binarySearch(set, v);
        if (i < 0) {
            return set;
        }
        int[] out = new int[set.length - 1];
        System.arraycopy(set, 0, out, 0, i);
        System.arraycopy(set, i + 1, out, i, set.length - i - 1);
        return out;
    }

    private static PersistentDataContainer pdc(Block b) {
        return b.getChunk().getPersistentDataContainer();
    }

    /** True when a player placed this block (and it was not broken or moved since). */
    public static boolean marked(Block b) {
        return contains(pdc(b).get(KEY, PersistentDataType.INTEGER_ARRAY), pack(b.getX(), b.getY(), b.getZ()));
    }

    /**
     * A reader for many blocks in one go (area enchants): each chunk's marks are read once. Use it for one scan only, then
     * drop it (marks change when blocks are broken).
     */
    public static java.util.function.Predicate<Block> reader() {
        java.util.Map<Long, int[]> chunks = new java.util.HashMap<>();
        return b -> {
            long key = ((long) (b.getX() >> 4) << 32) ^ ((b.getZ() >> 4) & 0xFFFFFFFFL);
            int[] set = chunks.computeIfAbsent(key, k -> {
                int[] v = pdc(b).get(KEY, PersistentDataType.INTEGER_ARRAY);
                return v == null ? new int[0] : v;
            });
            return contains(set, pack(b.getX(), b.getY(), b.getZ()));
        };
    }

    public static void mark(Block b) {
        PersistentDataContainer pdc = pdc(b);
        int[] set = pdc.get(KEY, PersistentDataType.INTEGER_ARRAY);
        int[] next = add(set, pack(b.getX(), b.getY(), b.getZ()), CAP);
        if (next != set) {
            pdc.set(KEY, PersistentDataType.INTEGER_ARRAY, next);
        }
    }

    public static void unmark(Block b) {
        PersistentDataContainer pdc = pdc(b);
        int[] set = pdc.get(KEY, PersistentDataType.INTEGER_ARRAY);
        if (set == null) {
            return;
        }
        int[] next = remove(set, pack(b.getX(), b.getY(), b.getZ()));
        if (next == set) {
            return;
        }
        if (next.length == 0) {
            pdc.remove(KEY);
        } else {
            pdc.set(KEY, PersistentDataType.INTEGER_ARRAY, next);
        }
    }
}
