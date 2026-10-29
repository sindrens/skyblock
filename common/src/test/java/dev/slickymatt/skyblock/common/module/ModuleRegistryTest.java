package dev.slickymatt.skyblock.common.module;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleRegistryTest {

    private final List<String> events = new ArrayList<>();
    private final ModuleRegistry registry = new ModuleRegistry(Logger.getLogger("test"));

    private SkyblockModule module(String id, Set<String> deps, boolean failOnEnable) {
        return new SkyblockModule() {
            public String id() { return id; }
            public Set<String> dependencies() { return deps; }
            public void enable() throws Exception {
                if (failOnEnable) throw new Exception("boom");
                events.add("enable:" + id);
            }
            public void disable() { events.add("disable:" + id); }
        };
    }

    @Test
    void enablesDependenciesFirstAndDisablesInReverse() {
        registry.register(module("islands", Set.of("economy"), false));
        registry.register(module("economy", Set.of(), false));

        registry.enableAll();
        registry.disableAll();

        assertEquals(List.of("enable:economy", "enable:islands", "disable:islands", "disable:economy"), events);
    }

    @Test
    void failedModuleSkipsDependentsButNotOthers() {
        registry.register(module("economy", Set.of(), true));
        registry.register(module("islands", Set.of("economy"), false));
        registry.register(module("chat", Set.of(), false));

        registry.enableAll();

        assertEquals(ModuleRegistry.State.FAILED, registry.state("economy"));
        assertEquals(ModuleRegistry.State.FAILED, registry.state("islands"));
        assertEquals(ModuleRegistry.State.ENABLED, registry.state("chat"));
    }

    @Test
    void rejectsDuplicateAndInvalidIds() {
        registry.register(module("economy", Set.of(), false));
        assertThrows(IllegalArgumentException.class, () -> registry.register(module("economy", Set.of(), false)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(module("Bad Id", Set.of(), false)));
    }

    @Test
    void detectsCyclesAndUnknownDependencies() {
        registry.register(module("a", Set.of("b"), false));
        registry.register(module("b", Set.of("a"), false));
        assertThrows(IllegalStateException.class, registry::resolveOrder);

        ModuleRegistry other = new ModuleRegistry(Logger.getLogger("test"));
        other.register(module("a", Set.of("missing"), false));
        assertThrows(IllegalStateException.class, other::resolveOrder);
    }
}
