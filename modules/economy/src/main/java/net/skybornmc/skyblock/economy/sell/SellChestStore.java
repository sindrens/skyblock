package net.skybornmc.skyblock.economy.sell;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The crash copy of an open sell chest (SELL2). While the chest is open its contents are written into the player's own
 * data ({@link Holder}: the player's persistent data container), which vanilla saves in the <b>same file and at the same
 * moment</b> as the inventory. So a crash rolls both back together: an item is either in the inventory or in this copy,
 * never in both (no dupe) and never in neither (no loss). On the next join the copy is handed back and cleared.
 *
 * <p>Pure (no Bukkit): {@code T} is the stack type, turned into bytes by the given functions.</p>
 *
 * <p>Format (version 1): magic {@code SCT1}, version byte, profile key (2 longs), saved-at millis, count, then per stack
 * its chest slot, byte length and bytes.</p>
 */
final class SellChestStore<T> {

    static final int MAGIC = 0x53435431; // "SCT1"
    static final byte VERSION = 1;
    /** Most stacks a copy may hold (a 6-row chest); anything above is treated as broken data. */
    static final int MAX_STACKS = 54;
    /** Largest serialized stack accepted (1 MiB; real stacks are a few hundred bytes). */
    static final int MAX_STACK_BYTES = 1 << 20;

    /** Where the copy lives (the player's persistent data). */
    interface Holder {
        byte[] read();

        void write(byte[] data);

        void clear();
    }

    /**
     * A restored copy.
     *
     * @param profile the profile key the items belong to (the active profile when they were put in)
     * @param savedAt when the copy was written (millis)
     * @param items   chest slot → stack, in slot order
     * @param broken  stacks whose bytes could not be turned back into an item (logged with their raw data)
     */
    record Restored<T>(UUID profile, long savedAt, Map<Integer, T> items, List<byte[]> broken) {
    }

    private final Holder holder;
    private final Function<T, byte[]> serialize;
    private final Function<byte[], T> deserialize;

    SellChestStore(Holder holder, Function<T, byte[]> serialize, Function<byte[], T> deserialize) {
        this.holder = holder;
        this.serialize = serialize;
        this.deserialize = deserialize;
    }

    /** Writes the chest contents (slot → stack; null/absent = empty). An empty chest clears the copy. */
    void save(UUID profile, Map<Integer, T> items, long now) {
        Map<Integer, byte[]> bytes = new LinkedHashMap<>();
        for (Map.Entry<Integer, T> e : items.entrySet()) {
            if (e.getValue() != null) {
                bytes.put(e.getKey(), serialize.apply(e.getValue()));
            }
        }
        if (bytes.isEmpty()) {
            holder.clear();
            return;
        }
        holder.write(encode(profile, now, bytes));
    }

    /** Removes the copy (chest closed and everything handed back, or sold). */
    void clear() {
        holder.clear();
    }

    /** Whether a copy exists (a crash left items behind). */
    boolean present() {
        byte[] raw = holder.read();
        return raw != null && raw.length > 0;
    }

    /**
     * Reads the copy without clearing it. Empty if there is none. Throws {@link IllegalArgumentException} if the data is
     * not a copy at all (the caller logs the raw bytes before clearing).
     */
    Optional<Restored<T>> read() {
        byte[] raw = holder.read();
        if (raw == null || raw.length == 0) {
            return Optional.empty();
        }
        Decoded decoded = decode(raw);
        Map<Integer, T> items = new LinkedHashMap<>();
        List<byte[]> broken = new ArrayList<>();
        for (Map.Entry<Integer, byte[]> e : decoded.items().entrySet()) {
            T item;
            try {
                item = deserialize.apply(e.getValue());
            } catch (RuntimeException ex) {
                item = null;
            }
            if (item == null) {
                broken.add(e.getValue());
            } else {
                items.put(e.getKey(), item);
            }
        }
        return Optional.of(new Restored<>(decoded.profile(), decoded.savedAt(), items, broken));
    }

    /** The raw copy (for logging broken data). */
    byte[] raw() {
        return holder.read();
    }

    // ------------------------------------------------------------------ format

    record Decoded(UUID profile, long savedAt, Map<Integer, byte[]> items) {
    }

    static byte[] encode(UUID profile, long savedAt, Map<Integer, byte[]> items) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeLong(profile.getMostSignificantBits());
            out.writeLong(profile.getLeastSignificantBits());
            out.writeLong(savedAt);
            out.writeInt(items.size());
            for (Map.Entry<Integer, byte[]> e : items.entrySet()) {
                out.writeInt(e.getKey());
                out.writeInt(e.getValue().length);
                out.write(e.getValue());
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e); // in-memory streams don't fail
        }
    }

    static Decoded decode(byte[] raw) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("not a sell chest copy");
            }
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("unknown sell chest copy version " + version);
            }
            UUID profile = new UUID(in.readLong(), in.readLong());
            long savedAt = in.readLong();
            int count = in.readInt();
            if (count < 0 || count > MAX_STACKS) {
                throw new IllegalArgumentException("bad stack count " + count);
            }
            Map<Integer, byte[]> items = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int slot = in.readInt();
                int length = in.readInt();
                if (length < 0 || length > MAX_STACK_BYTES) {
                    throw new IllegalArgumentException("bad stack length " + length);
                }
                byte[] data = in.readNBytes(length);
                if (data.length != length) {
                    throw new IllegalArgumentException("copy is cut off");
                }
                items.put(slot, data);
            }
            return new Decoded(profile, savedAt, items);
        } catch (IOException e) {
            throw new IllegalArgumentException("copy is cut off", e);
        }
    }
}
