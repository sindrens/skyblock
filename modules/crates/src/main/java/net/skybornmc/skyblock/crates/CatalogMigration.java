package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One-time upgrade of a live {@code crates.yml} to {@code catalog-version: 2} (ECON-SHARDS step 16, ECON-SPAWNERS S10;
 * pure, no file access). Shards are only found in shard blocks now and premium (tier 5) spawners are sold by the Black
 * Market, so:
 * <ul>
 *   <li>every {@code type: shards} row is removed (it could never pay again anyway);</li>
 *   <li>the old {@code spawner_t5} rows (inferno, phoenix, eclipse) are removed <b>only</b> while they still equal the old
 *       default (an edited row stays, and the log tells staff);</li>
 *   <li>a {@code shard_block} row is added to blaze, inferno, phoenix, eclipse and conqueror when the crate has none.</li>
 * </ul>
 * A second run changes nothing.
 */
final class CatalogMigration {

    static final int VERSION = 2;
    static final String VERSION_KEY = "catalog-version";

    /** What the run did: changed or not, plus one English line per step for the log file. */
    record Result(boolean changed, List<String> log) {
    }

    private static final Map<String, Map<String, Object>> OLD_T5 = Map.of(
            "inferno", row("spawner_t5", "spawner", 2, Map.of("tier", 5, "amount", 1, "top", true, "rarity", "legendary")),
            "phoenix", row("spawner_t5", "spawner", 6, Map.of("tier", 5, "amount", 1, "top", true)),
            "eclipse", row("spawner_t5", "spawner", 6, Map.of("tier", 5, "amount", 2)));

    /** The new shard block rows per crate (same as the bundled file). */
    static final Map<String, Map<String, Object>> SHARD_BLOCK_ROWS = Map.of(
            "blaze", shardRow(0.5, null),
            "inferno", shardRow(1, "rare"),
            "phoenix", shardRow(2, "rare"),
            "eclipse", shardRow(3, "epic"),
            "conqueror", shardRow(2, "rare"));

    /**
     * The row that takes up the chance the removed rows leave (or gives back the shard block's share), so an untouched table
     * still adds up to 100: the most common plain reward of each crate (same numbers as the bundled file).
     */
    static final Map<String, String> FILLER = Map.of(
            "blaze", "money", "inferno", "money", "phoenix", "book", "eclipse", "book", "conqueror", "money");

    private CatalogMigration() {
    }

    private static double total(List<? extends Map<?, ?>> rows) {
        double sum = 0;
        for (Map<?, ?> r : rows) {
            Object c = r.get("chance");
            if (c instanceof Number n) {
                sum += n.doubleValue();
            }
        }
        return sum;
    }

    private static Map<String, Object> row(String id, String type, double chance, Map<String, Object> rest) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("chance", chance);
        m.putAll(rest);
        return m;
    }

    private static Map<String, Object> shardRow(double chance, String rarity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "shard_block");
        m.put("type", "item");
        m.put("material", "custom:shard_block");
        m.put("icon", "infested_stone");
        m.put("chance", chance);
        m.put("amount", 1);
        if (rarity != null) {
            m.put("top", true);
            m.put("rarity", rarity);
        }
        return m;
    }

    /** True when the file still needs the upgrade. */
    static boolean needed(YamlConfiguration yaml) {
        return yaml.getInt(VERSION_KEY, 1) < VERSION;
    }

    /** Upgrades {@code yaml} in place and sets {@code catalog-version: 2}. */
    static Result migrate(YamlConfiguration yaml) {
        List<String> log = new ArrayList<>();
        if (!needed(yaml)) {
            return new Result(false, log);
        }
        ConfigurationSection root = yaml.getConfigurationSection(CrateCatalog.ROOT);
        if (root != null) {
            for (String crate : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(crate);
                if (section == null) {
                    continue;
                }
                List<Map<?, ?>> rewards = section.getMapList("rewards");
                List<Map<String, Object>> out = new ArrayList<>(rewards.size() + 1);
                boolean changed = false;
                boolean hasShardBlock = false;
                for (Map<?, ?> reward : rewards) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    reward.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    String id = String.valueOf(copy.get("id"));
                    String type = String.valueOf(copy.get("type")).trim().toLowerCase(java.util.Locale.ROOT);
                    if (type.equals("shards")) {
                        log.add(crate + ": removed reward '" + id + "' " + copy + " (shards are only found in shard blocks)");
                        changed = true;
                        continue;
                    }
                    Map<String, Object> old = OLD_T5.get(crate);
                    if (old != null && same(copy, old)) {
                        log.add(crate + ": removed reward 'spawner_t5' (premium spawners are sold by the Black Market now)");
                        changed = true;
                        continue;
                    }
                    if (old != null && id.equals("spawner_t5")) {
                        log.add(crate + ": kept reward 'spawner_t5' because it was edited " + copy
                                + "; remove it by hand if premium spawners should stay Black Market only");
                    }
                    if (id.equals("shard_block")) {
                        hasShardBlock = true;
                    }
                    out.add(copy);
                }
                Map<String, Object> add = SHARD_BLOCK_ROWS.get(crate);
                if (add != null && !hasShardBlock) {
                    out.add(new LinkedHashMap<>(add));
                    log.add(crate + ": added reward 'shard_block' " + add);
                    changed = true;
                }
                // A table that added up to 100 before still does: the filler row takes the difference.
                String filler = FILLER.get(crate);
                double diff = Math.round((100 - total(out)) * 1000) / 1000.0;
                if (changed && filler != null && Math.abs(total(rewards) - 100) < 1e-9 && Math.abs(diff) > 1e-9) {
                    for (Map<String, Object> row : out) {
                        if (filler.equals(String.valueOf(row.get("id"))) && row.get("chance") instanceof Number n
                                && n.doubleValue() + diff >= 0) {
                            double now = Math.round((n.doubleValue() + diff) * 1000) / 1000.0;
                            row.put("chance", now == Math.rint(now) ? (Object) (int) now : (Object) now);
                            log.add(crate + ": reward '" + filler + "' chance " + n + " -> " + row.get("chance")
                                    + " so the table still adds up to 100");
                            break;
                        }
                    }
                }
                if (changed) {
                    section.set("rewards", out);
                }
            }
        }
        yaml.set(VERSION_KEY, VERSION);
        log.add("catalog-version set to " + VERSION);
        return new Result(true, log);
    }

    /** Same keys and values (numbers compared by value: 2 equals 2.0). */
    static boolean same(Map<String, Object> a, Map<String, Object> b) {
        if (!a.keySet().equals(b.keySet())) {
            return false;
        }
        for (Map.Entry<String, Object> e : a.entrySet()) {
            Object x = e.getValue();
            Object y = b.get(e.getKey());
            if (x instanceof Number n && y instanceof Number m) {
                if (Double.compare(n.doubleValue(), m.doubleValue()) != 0) {
                    return false;
                }
            } else if (!Objects.equals(String.valueOf(x), String.valueOf(y))) {
                return false;
            }
        }
        return true;
    }
}
