package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECON-SHARDS step 16: the one-time crates.yml v2 migration. {@code crates-v1.yml} is the bundled file before it (git
 * c81df6e5): migrating it must give exactly the chances of today's bundled file, every table adding up to 100.
 */
class CratesMigrationTest {

    private static Map<String, Map<String, Double>> chances(YamlConfiguration yaml) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection(CrateCatalog.ROOT);
        for (String crate : root.getKeys(false)) {
            Map<String, Double> rows = new LinkedHashMap<>();
            for (Map<?, ?> r : root.getConfigurationSection(crate).getMapList("rewards")) {
                rows.put(String.valueOf(r.get("id")), ((Number) r.get("chance")).doubleValue());
            }
            out.put(crate, rows);
        }
        return out;
    }

    @Test
    void anUntouchedOldFileBecomesTheBundledTables() {
        YamlConfiguration old = Fixtures.resource("crates-v1.yml");
        assertTrue(CatalogMigration.needed(old));
        CatalogMigration.Result r = CatalogMigration.migrate(old);
        assertTrue(r.changed());
        YamlConfiguration bundledYaml = Fixtures.resource("crates.yml");
        // Same order as CratesModule: the v2 catalog migration, then the Eclipse switch to the tiered pool.
        assertTrue(EclipseMigration.migrate(old, bundledYaml).changed());
        Map<String, Map<String, Double>> migrated = chances(old);
        Map<String, Map<String, Double>> bundled = chances(bundledYaml);
        for (String crate : bundled.keySet()) {
            assertEquals(bundled.get(crate), migrated.get(crate), crate);
            boolean tiered = bundledYaml.getConfigurationSection(CrateCatalog.ROOT + "." + crate).getMapList("rewards").stream()
                    .anyMatch(row -> row.containsKey("band"));
            if (!tiered) { // a tiered crate (Eclipse) weighs rewards inside each band, not to 100
                assertEquals(100, migrated.get(crate).values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9, crate + " adds up to 100");
            }
        }
        assertFalse(migrated.get("conqueror").containsKey("shards"), "no crate pays shards");
        assertFalse(migrated.get("phoenix").containsKey("spawner_t5"));
    }

    @Test
    void aSecondRunChangesNothing() {
        YamlConfiguration old = Fixtures.resource("crates-v1.yml");
        CatalogMigration.migrate(old);
        Map<String, Map<String, Double>> once = chances(old);
        assertFalse(CatalogMigration.migrate(old).changed());
        assertEquals(once, chances(old));
    }

    @Test
    void anEditedTableIsNotRebalanced() {
        YamlConfiguration old = Fixtures.resource("crates-v1.yml");
        ConfigurationSection inferno = old.getConfigurationSection(CrateCatalog.ROOT + ".inferno");
        List<Map<?, ?>> rows = inferno.getMapList("rewards");
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("id", "owner_row");
        extra.put("type", "money");
        extra.put("chance", 7);
        extra.put("minutes", 1);
        List<Map<?, ?>> edited = new java.util.ArrayList<>(rows);
        edited.add(extra);
        inferno.set("rewards", edited);
        CatalogMigration.migrate(old);
        assertEquals(15.0, chances(old).get("inferno").get("money"), "the owner's table (107) is left as weights");
    }
}
