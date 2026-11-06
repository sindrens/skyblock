package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One-time upgrade of a live {@code crates.yml} to {@code catalog-version: 3} (batch 9, 7 Nov 2026; pure, no file access).
 * The coming-soon rows (Sell Wand, Auto-Sell Chest, Chunk Collector, Tool Skin, Tome, Blueprint, Hologram, kill effect,
 * pillar XP) are items that are not made yet (Q-71), so a crate never shows a placeholder again:
 * <ul>
 *   <li>every {@code type: coming-soon} row is removed;</li>
 *   <li>its chance goes to what it already paid: crystals. Plain rows add to the crate's {@code crystals} row, top rows
 *       to a top {@code crystal_jackpot} row. Each is added when the crate has none. The value players get stays the same,
 *       and the top band (pity) keeps its share.</li>
 * </ul>
 * A second run changes nothing.
 */
final class ComingSoonMigration {

    static final int VERSION = 3;

    /** Crystal base of the new jackpot row per crate: the weighted fallback of the top rows it replaces. */
    static final Map<String, Integer> JACKPOT_BASE = Map.of("ember", 75, "cinder", 130, "blaze", 260, "inferno", 600);
    static final Map<String, String> JACKPOT_RARITY = Map.of("ember", "rare", "cinder", "rare", "blaze", "epic", "inferno", "legendary");

    record Result(boolean changed, List<String> log) {
    }

    private ComingSoonMigration() {
    }

    static boolean needed(YamlConfiguration yaml) {
        return yaml.getInt(CatalogMigration.VERSION_KEY, 1) < VERSION;
    }

    static Result migrate(YamlConfiguration yaml) {
        List<String> log = new ArrayList<>();
        if (!needed(yaml)) {
            return new Result(false, log);
        }
        ConfigurationSection root = yaml.getConfigurationSection(CrateCatalog.ROOT);
        if (root != null) {
            for (String crate : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(crate);
                if (section != null) {
                    migrateCrate(crate, section, log);
                }
            }
        }
        yaml.set(CatalogMigration.VERSION_KEY, VERSION);
        log.add("catalog-version set to " + VERSION);
        return new Result(true, log);
    }

    private static void migrateCrate(String crate, ConfigurationSection section, List<String> log) {
        List<Map<?, ?>> rewards = section.getMapList("rewards");
        List<Map<String, Object>> out = new ArrayList<>(rewards.size());
        double plain = 0;
        double top = 0;
        int jackpotAt = -1;
        for (Map<?, ?> reward : rewards) {
            Map<String, Object> copy = new LinkedHashMap<>();
            reward.forEach((k, v) -> copy.put(String.valueOf(k), v));
            String type = String.valueOf(copy.get("type")).strip().toLowerCase(Locale.ROOT);
            if (!type.equals("coming-soon")) {
                out.add(copy);
                continue;
            }
            double chance = copy.get("chance") instanceof Number n ? n.doubleValue() : 0;
            if (Boolean.TRUE.equals(copy.get("top")) || "true".equalsIgnoreCase(String.valueOf(copy.get("top")))) {
                top += chance;
                if (jackpotAt < 0) {
                    jackpotAt = out.size();
                }
            } else {
                plain += chance;
            }
            log.add(crate + ": removed coming-soon reward '" + copy.get("id") + "' (" + chance + "%, not made yet)");
        }
        if (plain == 0 && top == 0) {
            return;
        }
        int fallback = section.getInt("fallback-crystals", 15);
        if (plain > 0) {
            double share = plain;
            add(out, "crystals", share, -1, () -> row("crystals", share, fallback, false, null), crate, log);
        }
        if (top > 0) {
            double share = top;
            add(out, "crystal_jackpot", share, jackpotAt, () -> row("crystal_jackpot", share,
                    JACKPOT_BASE.getOrDefault(crate, fallback * 3), true, JACKPOT_RARITY.getOrDefault(crate, "rare")), crate, log);
        }
        section.set("rewards", out);
    }

    /** Adds {@code chance} to the row with this id, or inserts a new row (at {@code at}, or at the end). */
    private static void add(List<Map<String, Object>> out, String id, double chance, int at,
                            java.util.function.Supplier<Map<String, Object>> fresh, String crate, List<String> log) {
        for (Map<String, Object> row : out) {
            if (id.equals(String.valueOf(row.get("id"))) && row.get("chance") instanceof Number n) {
                row.put("chance", number(n.doubleValue() + chance));
                log.add(crate + ": reward '" + id + "' chance " + n + " -> " + row.get("chance"));
                return;
            }
        }
        Map<String, Object> row = fresh.get();
        if (at >= 0 && at <= out.size()) {
            out.add(at, row);
        } else {
            out.add(row);
        }
        log.add(crate + ": added reward '" + id + "' " + row);
    }

    private static Map<String, Object> row(String id, double chance, int base, boolean top, String rarity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", "crystals");
        m.put("chance", number(chance));
        m.put("base", base);
        if (top) {
            m.put("top", true);
        }
        if (rarity != null) {
            m.put("rarity", rarity);
        }
        return m;
    }

    private static Object number(double v) {
        double r = Math.round(v * 1000) / 1000.0;
        return r == Math.rint(r) ? (Object) (int) r : (Object) r;
    }
}
