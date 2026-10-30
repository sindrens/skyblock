package net.skybornmc.skyblock.stacking;

import java.util.Arrays;

/**
 * A set of block positions inside one chunk, packed into ints ({@code x | z << 4 | (y - minY) << 8}) so it can live in
 * the chunk's persistent data as an int array. Not thread-safe (main thread only).
 */
final class CropPositions {

    private int[] values;
    private int size;
    private boolean dirty;

    CropPositions(int[] stored) {
        values = stored == null ? new int[8] : Arrays.copyOf(stored, Math.max(8, stored.length));
        size = stored == null ? 0 : stored.length;
        // Drop duplicates from older data so add/remove stay consistent.
        if (size > 1) {
            int[] sorted = Arrays.copyOf(values, size);
            Arrays.sort(sorted);
            int n = 0;
            for (int i = 0; i < sorted.length; i++) {
                if (i == 0 || sorted[i] != sorted[i - 1]) {
                    sorted[n++] = sorted[i];
                }
            }
            if (n != size) {
                values = Arrays.copyOf(sorted, Math.max(8, n));
                size = n;
                dirty = true;
            }
        }
    }

    static int pack(int localX, int y, int localZ, int minY) {
        return (localX & 15) | (localZ & 15) << 4 | (y - minY) << 8;
    }

    static int x(int packed) {
        return packed & 15;
    }

    static int z(int packed) {
        return (packed >> 4) & 15;
    }

    static int y(int packed, int minY) {
        return (packed >>> 8) + minY;
    }

    boolean add(int packed) {
        if (indexOf(packed) >= 0) {
            return false;
        }
        if (size == values.length) {
            values = Arrays.copyOf(values, size * 2);
        }
        values[size++] = packed;
        dirty = true;
        return true;
    }

    boolean remove(int packed) {
        int i = indexOf(packed);
        if (i < 0) {
            return false;
        }
        values[i] = values[--size];
        dirty = true;
        return true;
    }

    private int indexOf(int packed) {
        for (int i = 0; i < size; i++) {
            if (values[i] == packed) {
                return i;
            }
        }
        return -1;
    }

    int size() {
        return size;
    }

    int get(int index) {
        return values[index];
    }

    boolean isEmpty() {
        return size == 0;
    }

    boolean dirty() {
        return dirty;
    }

    /** The values to store; clears the dirty flag. */
    int[] snapshot() {
        dirty = false;
        return Arrays.copyOf(values, size);
    }
}
