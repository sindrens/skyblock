package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Shared test data: the bundled crates.yml and messages, small hand-made crates. */
final class Fixtures {

    private Fixtures() {
    }

    static YamlConfiguration resource(String name) {
        try (var in = Fixtures.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static CrateCatalog bundled() {
        List<String> problems = new ArrayList<>();
        CrateCatalog catalog = CrateCatalog.load(resource(CrateCatalog.FILE), problems::add);
        assertEquals(List.of(), problems, "bundled crates.yml must load without problems");
        return catalog;
    }

    static CrateCatalog parse(String yaml, List<String> problems) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return CrateCatalog.load(config, problems::add);
    }

    static CrateCatalog.Reward reward(String id, RewardType type, double chance, boolean top) {
        return new CrateCatalog.Reward(id, type, chance, top, "common", null, 3, 15, 1, "", "common", 1, List.of("delver"), 3,
                "elytra", List.of(), 10);
    }

    static CrateCatalog.Reward cosmetic(String id, RewardType type, double chance, boolean top, List<String> ids) {
        return new CrateCatalog.Reward(id, type, chance, top, "common", null, 0, 0, 1, "", "common", 1, List.of(), 0, "", ids, 10);
    }

    static CrateCatalog.Crate crate(int pity, CrateCatalog.Reward... rewards) {
        return new CrateCatalog.Crate("test", "common", "chest", 1, pity, false, 10, List.of(rewards));
    }

    /** A plan context at tier 1 where everything is live and nothing is owned. */
    static OpenPlan.Context context(int tier, Predicate<String> owns) {
        return new OpenPlan.Context(tier, new long[]{1_000, 2_000, 4_000}, 1.3, owns, r -> true,
                pool -> List.of("vein_rend", "swiftness"), t -> List.of("zombie"));
    }
}
