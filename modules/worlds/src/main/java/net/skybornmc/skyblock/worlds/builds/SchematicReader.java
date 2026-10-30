package net.skybornmc.skyblock.worlds.builds;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Reads the header of a Sponge schematic (gzip NBT, versions 2 and 3) and counts its non-air blocks, without
 * WorldEdit, so builds can be validated (bounds, origin, block count) before FAWE pastes them. Off the main thread:
 * the biggest build (spawn base, ~7 MB of block data) takes well under a second.
 */
public final class SchematicReader {

    /** Biggest block data accepted (protects the heap from a broken or hostile file). */
    static final int MAX_ARRAY = 64 * 1024 * 1024;
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private SchematicReader() {
    }

    public static SchematicInfo read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return read(in);
        }
    }

    /** Reads a gzip-compressed schematic stream. */
    public static SchematicInfo read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)));
        int type = in.readUnsignedByte();
        if (type != 10) {
            throw new IOException("not an NBT compound (tag " + type + ")");
        }
        in.readUTF();
        Map<String, Object> root = compound(in, 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> schem = root.get("Schematic") instanceof Map<?, ?> m ? (Map<String, Object>) m : root;
        int version = intOf(schem, "Version", 1);
        int dataVersion = intOf(schem, "DataVersion", 0);
        int width = intOf(schem, "Width", -1);
        int height = intOf(schem, "Height", -1);
        int length = intOf(schem, "Length", -1);
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("missing Width/Height/Length");
        }
        int[] offset = schem.get("Offset") instanceof int[] o && o.length == 3 ? o : new int[3];
        int[] weOffset = new int[3];
        if (schem.get("Metadata") instanceof Map<?, ?> meta) {
            weOffset[0] = meta.get("WEOffsetX") instanceof Integer i ? i : 0;
            weOffset[1] = meta.get("WEOffsetY") instanceof Integer i ? i : 0;
            weOffset[2] = meta.get("WEOffsetZ") instanceof Integer i ? i : 0;
        }
        Map<?, ?> palette;
        byte[] data;
        Object entitiesTag;
        Object blockEntitiesTag;
        if (version >= 3 && schem.get("Blocks") instanceof Map<?, ?> blocks) {
            palette = blocks.get("Palette") instanceof Map<?, ?> p ? p : Map.of();
            data = blocks.get("Data") instanceof byte[] d ? d : null;
            blockEntitiesTag = blocks.get("BlockEntities");
        } else {
            palette = schem.get("Palette") instanceof Map<?, ?> p ? p : Map.of();
            data = schem.get("BlockData") instanceof byte[] d ? d : null;
            blockEntitiesTag = schem.get("BlockEntities");
        }
        entitiesTag = schem.get("Entities");
        long nonAir = data == null ? -1 : countNonAir(palette, data, (long) width * height * length);
        return new SchematicInfo(version, dataVersion, width, height, length, offset[0], offset[1], offset[2],
                offset[0] - weOffset[0], offset[1] - weOffset[1], offset[2] - weOffset[2], nonAir,
                blockEntitiesTag instanceof List<?> l ? l.size() : 0, entitiesTag instanceof List<?> e ? e.size() : 0);
    }

    /** Counts block data entries (var-ints into the palette) that are not air. */
    static long countNonAir(Map<?, ?> palette, byte[] data, long expected) throws IOException {
        int max = 0;
        for (Object v : palette.values()) {
            if (v instanceof Integer i) {
                max = Math.max(max, i);
            }
        }
        boolean[] air = new boolean[max + 1];
        palette.forEach((name, id) -> {
            if (id instanceof Integer i && i >= 0 && AIR.contains(baseName(String.valueOf(name)))) {
                air[i] = true;
            }
        });
        long count = 0;
        long seen = 0;
        int i = 0;
        while (i < data.length) {
            int value = 0;
            int shift = 0;
            int b;
            do {
                if (i >= data.length) {
                    throw new IOException("block data ends inside a number");
                }
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
                if (shift > 35) {
                    throw new IOException("block data number too long");
                }
            } while ((b & 0x80) != 0);
            seen++;
            if (value < 0 || value >= air.length || !air[value]) {
                count++;
            }
        }
        if (seen != expected) {
            throw new IOException("block data has " + seen + " entries, expected " + expected);
        }
        return count;
    }

    private static String baseName(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    private static int intOf(Map<String, Object> map, String key, int fallback) {
        Object v = map.get(key);
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Short s) {
            return s;
        }
        if (v instanceof Byte b) {
            return b;
        }
        return fallback;
    }

    // ------------------------------------------------------------------ minimal NBT

    private static Map<String, Object> compound(DataInputStream in, int depth) throws IOException {
        if (depth > 32) {
            throw new IOException("NBT nested too deep");
        }
        Map<String, Object> map = new HashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) {
                return map;
            }
            String name = in.readUTF();
            map.put(name, payload(in, type, depth + 1));
        }
    }

    private static Object payload(DataInputStream in, int type, int depth) throws IOException {
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] bytes = new byte[length(in)];
                in.readFully(bytes);
                yield bytes;
            }
            case 8 -> {
                byte[] bytes = new byte[in.readUnsignedShort()];
                in.readFully(bytes);
                yield new String(bytes, StandardCharsets.UTF_8);
            }
            case 9 -> {
                int element = in.readUnsignedByte();
                int n = length(in);
                List<Object> list = new ArrayList<>(Math.min(n, 1024));
                for (int i = 0; i < n; i++) {
                    list.add(payload(in, element, depth + 1));
                }
                yield list;
            }
            case 10 -> compound(in, depth);
            case 11 -> {
                int n = length(in);
                int[] ints = new int[n];
                for (int i = 0; i < n; i++) {
                    ints[i] = in.readInt();
                }
                yield ints;
            }
            case 12 -> {
                int n = length(in);
                in.skipNBytes(8L * n);
                yield n;
            }
            default -> throw new IOException("unknown NBT tag " + type);
        };
    }

    private static int length(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > MAX_ARRAY) {
            throw new EOFException("NBT array length " + n + " out of range");
        }
        return n;
    }
}
