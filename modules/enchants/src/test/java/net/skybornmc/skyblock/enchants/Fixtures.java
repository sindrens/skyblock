package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolSlots;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Shared test data: the bundled registry and messages, tools at a level. */
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

    static EnchantRegistry registry() {
        List<String> problems = new ArrayList<>();
        EnchantRegistry registry = EnchantRegistry.load(resource("enchants.yml"), problems::add);
        assertEquals(List.of(), problems, "bundled enchants.yml must load without problems");
        return registry;
    }

    static EnchantDef def(String id) {
        return registry().get(id).orElseThrow(() -> new AssertionError("no enchant " + id));
    }

    static ToolInfo tool(ToolKind kind, int level, int prestige, ToolPerk... perks) {
        int slots = ToolSlots.unlocked(level, prestige, ToolSlots.DEFAULT_SLOT_LEVELS);
        return new ToolInfo(UUID.randomUUID(), kind, 3, level, 0, prestige, slots, List.of(perks), null, null, false, Map.of());
    }

    static EnchantRules rules() {
        return new EnchantRules(ToolSlots.DEFAULT_SLOT_LEVELS, ToolSlots.DEFAULT_MAX_LEVEL, false);
    }
}
