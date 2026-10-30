package net.skybornmc.skyblock.progression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Worth of one placed block for the island level (PHASE-5 M5.2, {@code block-values.yml}), by block id ({@code obsidian}).
 * Free of Bukkit (unit-tested); the runtime caches the worth per material. First match wins: exact {@code values} →
 * {@code zero} → {@code storage-blocks} (sell base price x amount x storage-percent) → shop price x shop-percent → 0.
 * Spawners are valued per spawner by tier ({@link #spawnerWorth}); the spawner block itself is worth 0 here.
 */
final class BlockValues {

    /** Spawner blocks are counted through their tile entities (stack size x tier worth), never as a plain block. */
    static final String SPAWNER = "spawner";

    private record Storage(String item, double amount) {
    }

    private final Map<String, Double> values;
    private final Set<String> zero;
    private final Map<String, Storage> storage;
    private final Map<Integer, Double> spawnerTiers;
    private final double shopShare;
    private final double storageShare;
    private final Function<String, OptionalLong> shopPrice;
    private final Function<String, OptionalDouble> sellBase;

    private BlockValues(Map<String, Double> values, Set<String> zero, Map<String, Storage> storage, Map<Integer, Double> spawnerTiers,
                        double shopShare, double storageShare, Function<String, OptionalLong> shopPrice,
                        Function<String, OptionalDouble> sellBase) {
        this.values = values;
        this.zero = zero;
        this.storage = storage;
        this.spawnerTiers = spawnerTiers;
        this.shopShare = shopShare;
        this.storageShare = storageShare;
        this.shopPrice = shopPrice;
        this.sellBase = sellBase;
    }

    /**
     * @param shopPrice /shop price per item id (empty = not sold)
     * @param sellBase  /sell base price per item id (empty = can't be sold)
     * @param problems  receives one line per broken entry (skipped)
     */
    static BlockValues load(BlockValuesConfig c, Function<String, OptionalLong> shopPrice, Function<String, OptionalDouble> sellBase,
                            Consumer<String> problems) {
        Map<String, Double> values = new HashMap<>();
        for (String entry : c.values) {
            String[] kv = split(entry);
            Double v = kv == null ? null : number(kv[1]);
            if (kv == null || v == null || v < 0) {
                problems.accept("block-values.yml → values: '" + entry + "' is not 'block: money ≥ 0' (skipped)");
                continue;
            }
            values.put(key(kv[0]), v);
        }
        Set<String> zero = new HashSet<>();
        for (String z : c.zero) {
            if (z != null && !z.isBlank()) {
                zero.add(key(z));
            }
        }
        Map<String, Storage> storage = new HashMap<>();
        for (String entry : c.storageBlocks) {
            String[] kv = split(entry);
            String[] parts = kv == null ? null : kv[1].trim().split("\\s+");
            Double amount = parts == null || parts.length != 2 ? null : number(parts[1]);
            if (kv == null || amount == null || amount <= 0) {
                problems.accept("block-values.yml → storage-blocks: '" + entry + "' is not 'block: item amount' (skipped)");
                continue;
            }
            storage.put(key(kv[0]), new Storage(key(parts[0]), amount));
        }
        Map<Integer, Double> tiers = new HashMap<>();
        for (String entry : c.spawnerTierValues) {
            String[] kv = split(entry);
            Double tier = kv == null ? null : number(kv[0]);
            Double v = kv == null ? null : number(kv[1]);
            if (tier == null || v == null || v < 0 || tier < 1 || tier != Math.floor(tier)) {
                problems.accept("block-values.yml → spawner-tier-values: '" + entry + "' is not 'tier: money' (skipped)");
                continue;
            }
            tiers.put(tier.intValue(), v);
        }
        return new BlockValues(values, zero, storage, tiers, Math.max(0, c.shopPercent) / 100.0,
                Math.max(0, c.storagePercent) / 100.0, shopPrice, sellBase);
    }

    /** Worth of one block of this id (0 for unknown blocks, spawners and blocks nobody sells). */
    double worth(String blockId) {
        String id = key(blockId);
        if (id.isEmpty() || id.equals(SPAWNER)) {
            return 0;
        }
        Double exact = values.get(id);
        if (exact != null) {
            return exact;
        }
        if (zero.contains(id)) {
            return 0;
        }
        Storage s = storage.get(id);
        if (s != null) {
            OptionalDouble base = sellBase.apply(s.item());
            return base.isPresent() && base.getAsDouble() > 0 ? base.getAsDouble() * s.amount() * storageShare : 0;
        }
        OptionalLong price = shopPrice.apply(id);
        return price.isPresent() && price.getAsLong() > 0 ? price.getAsLong() * shopShare : 0;
    }

    /** Worth of a spawner stack: spawners x the tier's worth (0 for unknown tiers). */
    double spawnerWorth(int tier, int spawners) {
        if (spawners <= 0) {
            return 0;
        }
        return spawnerTiers.getOrDefault(tier, 0.0) * spawners;
    }

    static String key(String id) {
        String k = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return k.startsWith("minecraft:") ? k.substring("minecraft:".length()) : k;
    }

    private static String[] split(String entry) {
        if (entry == null) {
            return null;
        }
        int at = entry.lastIndexOf(": ");
        if (at < 0) {
            at = entry.lastIndexOf(':');
        }
        if (at <= 0 || at >= entry.length() - 1) {
            return null;
        }
        return new String[]{entry.substring(0, at).trim(), entry.substring(at + 1).trim()};
    }

    private static Double number(String s) {
        try {
            double d = Double.parseDouble(s.trim());
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /** For tests and the staff info: entries that were read. */
    int exactCount() {
        return values.size();
    }

    List<Integer> spawnerTiers() {
        return spawnerTiers.keySet().stream().sorted().toList();
    }
}
